#pragma once
#include <array>
#include <thread>
#include "Pad.h"

namespace padlab {

/**
 * Sampler + 16-step sequencer + master and per-pad FX racks + mic capture.
 * The UI thread calls trigger()/release()/play()/stop(); everything else runs in render().
 * No locks, no allocation in render() (time-stretch runs on its own thread).
 */
class Engine {
public:
    Engine();
    ~Engine();

    std::array<Pad, PADS> pads;
    std::atomic<uint16_t> stepBits[PADS];                 // bit s = step s is on
    std::shared_ptr<Chain> master;
    std::atomic<float> bpm{90.f}, swing{0.3f}, masterVol{0.8f};
    std::atomic<bool> playing{false}, overdub{false};
    std::atomic<int> curStep{-1};
    std::atomic<float> vuL{0.f}, vuR{0.f}, micPeak{0.f};
    std::atomic<int64_t> lastHit[PADS];                   // steady-clock ns of the last trigger
    std::atomic<int> warpBusy{0};

    // ---- UI thread -------------------------------------------------------------------------
    void trigger(int pad, float vel);
    void release(int pad);
    void play();
    void stop();
    void killAll();
    std::shared_ptr<Chain> chainFor(int pad);
    void requestWarp(int pad);                            // re-render the stretched copy if needed
    void rewarpAll();

    void startRecording();                                // mic capture (needs an input channel)
    bool isRecording() const { return recording.load(); }
    std::shared_ptr<Sample> stopRecording();              // leading silence trimmed; null if empty
    int recordedFrames() const { return recLen.load(); }

    // ---- audio thread ----------------------------------------------------------------------
    void render(float* outL, float* outR, int frames, const float* in, int inFrames);

    static int64_t nowNs();

private:
    struct Ev { int type = 0, pad = 0; float vel = 0.f; };
    static constexpr int QSIZE = 1024;
    Ev queue[QSIZE];
    std::atomic<unsigned> qHead{0}, qTail{0};
    void push(int type, int pad, float vel);

    struct Voice {
        bool active = false, releasing = false, rev = false, loop = false, gate = false;
        int pad = 0, choke = 0, si = 0, ei = 0;
        std::shared_ptr<const Sample> s;
        double pos = 0, step = 0;
        float gainL = 0, gainR = 0, env = 0, relStep = 0;
        uint64_t age = 0;
        void release(float seconds) { releasing = true; relStep = 1.f / std::max(8.f, seconds * (float) SR); }
        void mix(float* l, float* r, int from, int len);
    };

    void handle(const Ev& e);
    void startVoice(int pad, float vel, bool fromSeq);
    void fireStep();
    double interval(int step) const;

    struct Warp { int si = 0, ei = 0; double f = 1.0; uint64_t key = 0; bool needed = false; };
    Warp warpSpec(const Pad& p, const Sample& s) const;
    void warpLoop();

    std::array<Voice, VOICES> voices;
    std::vector<float> bl, br;
    std::vector<float> busL[PADS], busR[PADS];
    double seqCounter = 0;
    int seqStep = -1;
    bool seqRunning = false;
    uint64_t voiceClock = 0;

    std::thread warper;
    std::atomic<bool> quit{false};

    std::vector<float> recBuf;
    std::atomic<int> recLen{0};
    std::atomic<bool> recording{false};
};

} // namespace padlab
