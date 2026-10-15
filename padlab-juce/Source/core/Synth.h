#pragma once
#include "Melody.h"

namespace padlab {

/** Offline note renderer: turns a melody into a stereo Sample using a chosen instrument. */
struct Synth {
    static constexpr int NUM = 14, ORIGINAL = 0, SAMPLE = 13;
    static const char* const NAMES[NUM];

    /** `recording` is returned as-is for ORIGINAL; `sampleSrc` feeds the SAMPLE instrument. */
    static std::shared_ptr<const Sample> render(const std::vector<Melody::Out>& notes, std::shared_ptr<const Sample> recording,
                                                int inst, std::shared_ptr<const Sample> sampleSrc, int sampleRoot,
                                                bool snap, double bpm);
};

} // namespace padlab
