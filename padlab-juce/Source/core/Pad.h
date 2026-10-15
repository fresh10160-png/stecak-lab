#pragma once
#include "Fx.h"
#include "Sample.h"

namespace padlab {

struct Pad {
    enum Mode { ONE_SHOT, GATE, LOOP };
    static constexpr const char* MODES[3] = {"ONE-SHOT", "GATE", "LOOP"};
    static constexpr int FIT[6] = {0, 1, 2, 4, 8, 16};

    AtomicPtr<const Sample> sample, rendered;   // rendered = time-stretched copy of the trimmed region
    AtomicPtr<Chain> chain;                     // per-pad insert FX, created on first use
    std::string name;                           // UI thread only

    std::atomic<float> vol{0.9f}, pan{0.f}, start{0.f}, end{1.f}, tempo{1.f};
    std::atomic<int> pitch{0}, mode{ONE_SHOT}, choke{0}, fitBeats{0};
    std::atomic<bool> reverse{false}, keyLock{false};
    std::atomic<uint64_t> renderedKey{0}, pendingKey{0};
    std::atomic<bool> warpWanted{false};
    bool fxActive = false;                      // audio thread only

    std::string label() const {
        if (!name.empty()) return name;
        auto s = sample.get();
        return s ? s->name : std::string();
    }

    bool hasFx() const {
        auto c = chain.get();
        if (!c) return false;
        for (auto& f : *c) if (f->on) return true;
        return false;
    }

    bool warped() const {
        return fitBeats > 0 || std::abs(tempo.load() - 1.f) > 0.005f || (keyLock && pitch != 0);
    }
};

} // namespace padlab
