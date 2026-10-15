#pragma once
#include <juce_audio_devices/juce_audio_devices.h>
#include "core/Engine.h"

/** Owns the audio device; pumps padlab::Engine from the device callback. */
class AudioHost : public juce::AudioIODeviceCallback {
public:
    AudioHost() = default;
    ~AudioHost() override { stop(); }

    /** Opens output-only (mic stays off). Sets padlab::SR to the device rate. Returns false if no device. */
    bool startOutput();
    void attach(padlab::Engine* e);               // call once the engine exists
    bool hasInput() const { return inputOn; }
    /** Reopen the device with / without a microphone channel. Returns true if the requested state holds. */
    bool setInput(bool on);
    void stop();
    juce::String describe();

    void audioDeviceIOCallbackWithContext(const float* const* in, int numIn, float* const* out, int numOut, int numSamples,
                                          const juce::AudioIODeviceCallbackContext&) override;
    void audioDeviceAboutToStart(juce::AudioIODevice*) override {}
    void audioDeviceStopped() override {}

private:
    bool open(int numIn);
    juce::AudioDeviceManager manager;
    std::atomic<padlab::Engine*> engine{nullptr};
    bool inputOn = false, running = false;
};
