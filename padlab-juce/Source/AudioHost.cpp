#include "AudioHost.h"

using namespace juce;

bool AudioHost::open(int numIn) {
    manager.removeAudioCallback(this);
    manager.closeAudioDevice();
    const String err = manager.initialise(numIn, 2, nullptr, true);
    if (err.isNotEmpty()) {
        DBG("audio init failed: " + err);
        running = false;
        return false;
    }
    auto* dev = manager.getCurrentAudioDevice();
    if (dev == nullptr) { running = false; return false; }
    inputOn = dev->getActiveInputChannels().countNumberOfSetBits() > 0;
    const int rate = (int) dev->getCurrentSampleRate();
    if (!engine.load()) padlab::SR = rate > 0 ? rate : 48000;
    manager.addAudioCallback(this);
    running = true;
    return true;
}

bool AudioHost::startOutput() { return open(0); }

void AudioHost::attach(padlab::Engine* e) { engine = e; }

bool AudioHost::setInput(bool on) {
    if (on == inputOn && running) return true;
    const bool ok = open(on ? 1 : 0);
    return ok && (inputOn == on);
}

void AudioHost::stop() {
    manager.removeAudioCallback(this);
    manager.closeAudioDevice();
    running = false;
}

String AudioHost::describe() {
    if (auto* d = manager.getCurrentAudioDevice())
        return d->getName() + " " + String((int) d->getCurrentSampleRate()) + " Hz, buffer " + String(d->getCurrentBufferSizeSamples()) +
               " (" + String(d->getCurrentBufferSizeSamples() * 1000.0 / d->getCurrentSampleRate(), 1) + " ms)";
    return "no audio device";
}

void AudioHost::audioDeviceIOCallbackWithContext(const float* const* in, int numIn, float* const* out, int numOut, int numSamples,
                                                 const AudioIODeviceCallbackContext&) {
    ScopedNoDenormals noDenormals;
    auto* e = engine.load();
    if (e == nullptr || numOut < 1) {
        for (int c = 0; c < numOut; ++c) if (out[c]) FloatVectorOperations::clear(out[c], numSamples);
        return;
    }
    int done = 0;
    while (done < numSamples) {
        const int n = std::min(numSamples - done, padlab::MAXB);
        const float* mic = (numIn > 0 && in[0]) ? in[0] + done : nullptr;
        float* l = out[0] + done;
        float* r = numOut > 1 ? out[1] + done : l;
        if (numOut > 1) {
            e->render(l, r, n, mic, n);
        } else { // mono device: render to scratch, fold down
            static thread_local std::vector<float> tmp;
            tmp.resize((size_t) n);
            e->render(l, tmp.data(), n, mic, n);
            for (int i = 0; i < n; ++i) l[i] = 0.5f * (l[i] + tmp[(size_t) i]);
        }
        for (int c = 2; c < numOut; ++c) if (out[c]) FloatVectorOperations::clear(out[c] + done, n);
        done += n;
    }
}
