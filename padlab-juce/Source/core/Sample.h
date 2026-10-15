#pragma once
#include "Common.h"

namespace padlab {

/** Stereo float audio at the engine rate. Mono samples keep an empty `r` and alias it to `l`. */
struct Sample {
    std::string name;
    std::vector<float> l, r;

    int n() const { return (int) l.size(); }
    bool mono() const { return r.empty(); }
    const float* L() const { return l.data(); }
    const float* R() const { return r.empty() ? l.data() : r.data(); }

    static std::shared_ptr<Sample> makeMono(std::string name, std::vector<float> d) {
        auto s = std::make_shared<Sample>();
        s->name = std::move(name);
        s->l = std::move(d);
        return s;
    }

    static std::shared_ptr<Sample> make(std::string name, std::vector<float> l, std::vector<float> r) {
        auto s = std::make_shared<Sample>();
        s->name = std::move(name);
        s->l = std::move(l);
        s->r = std::move(r);
        return s;
    }

    /** Linear resample to the engine rate and a short fade-out so one-shots never click. */
    static std::shared_ptr<Sample> prepare(std::string name, std::vector<float> l, std::vector<float> r, int srcRate) {
        const bool mono = r.empty();
        if (srcRate != SR) {
            l = resample(l, srcRate);
            if (!mono) r = resample(r, srcRate);
        }
        const int fade = std::min(64, (int) l.size() / 2);
        for (int i = 0; i < fade; ++i) {
            const float g = i / (float) fade;
            const int k = (int) l.size() - 1 - i;
            l[k] *= g;
            if (!mono) r[k] *= g;
        }
        return make(std::move(name), std::move(l), std::move(r));
    }

    static std::vector<float> resample(const std::vector<float>& src, int srcRate) {
        if (src.size() < 2) return src;
        const double ratio = srcRate / (double) SR;
        const int n = (int) (src.size() / ratio);
        std::vector<float> out((size_t) std::max(0, n));
        for (int i = 0; i < n; ++i) {
            const double p = i * ratio;
            const int i0 = (int) p, i1 = std::min(i0 + 1, (int) src.size() - 1);
            const float f = (float) (p - i0);
            out[(size_t) i] = src[(size_t) i0] + (src[(size_t) i1] - src[(size_t) i0]) * f;
        }
        return out;
    }
};

using SamplePtr = std::shared_ptr<const Sample>;

} // namespace padlab
