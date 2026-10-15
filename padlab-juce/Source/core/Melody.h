#pragma once
#include "Sample.h"

namespace padlab {

/** A sung melody: YIN pitch tracking -> note list, plus key/scale/octave/grid shaping. */
struct Melody {
    static constexpr int NUM_SCALES = 7;
    static const char* const SCALE_NAMES[NUM_SCALES];
    static const char* const KEYS[12];

    /** Detected note: fractional MIDI pitch, seconds relative to the first note. */
    struct Note {
        double start = 0, dur = 0;
        float pitch = 0, vel = 0;
        int shift = 0;          // manual semitone edit
        bool deleted = false;
    };

    /** A note after scale/grid/octave shaping, ready for the synth. */
    struct Out {
        double start = 0, dur = 0;
        int midi = 60, src = 0;
        float vel = 1;
    };

    std::shared_ptr<const Sample> recording;
    std::vector<Note> notes;

    static std::string noteName(int midi);
    static int snapToScale(float p, int root, int scale);
    static Melody analyze(std::shared_ptr<const Sample> rec);
    std::vector<Out> build(int root, int scale, int octave, bool snap, double bpm) const;

private:
    void segment(const std::vector<float>& pitch, const std::vector<float>& rms, float ref, int hop, int win, int fs);
    void close(const std::vector<float>& cur, int sf, int ef, float rsum, float ref, int hop, int win, int fs);
};

} // namespace padlab
