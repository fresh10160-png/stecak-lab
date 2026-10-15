#include "Stretch.h"

namespace padlab {

std::shared_ptr<Sample> wsola(const Sample& s, int si, int ei, double f, const std::function<bool()>& cancelled) {
    const int len = ei - si;
    const int outLen = std::max(64, (int) (len * f));
    int w = (int) std::lround(2048.0 * SR / 44100.0);
    w &= ~1;
    const int hs = w / 2, tol = w / 4, m = hs;
    std::vector<float> win((size_t) w);
    for (int i = 0; i < w; ++i) win[(size_t) i] = 0.5f - 0.5f * std::cos(2.f * PI_F * (float) i / (float) w);

    const bool mono = s.mono();
    const float* L = s.L();
    const float* R = s.R();
    std::vector<float> mix((size_t) len);
    for (int i = 0; i < len; ++i) mix[(size_t) i] = mono ? L[si + i] : 0.5f * (L[si + i] + R[si + i]);

    std::vector<float> ol((size_t) outLen + (size_t) w, 0.f), orr;
    if (!mono) orr.assign((size_t) outLen + (size_t) w, 0.f);
    int prev = 0;
    for (int k = 0; (int64_t) k * hs < outLen; ++k) {
        if ((k & 7) == 0 && cancelled && cancelled()) return nullptr;
        int pos;
        if (k == 0) {
            pos = 0;
        } else {
            const int nominal = (int) std::llround(k * (double) hs / f);
            const int ref = prev + hs;
            pos = std::max(0, std::min(len - 1, nominal));
            if (ref + m < len && nominal < len) {
                double best = -1e30;
                const int lo = std::max(0, nominal - tol), hi = std::min(len - 1, nominal + tol);
                for (int c = lo; c <= hi; c += 2) {
                    double dot = 0, en = 1e-9;
                    const int lim = std::min(m, len - c);
                    for (int j = 0; j < lim; j += 2) {
                        const float a = mix[(size_t) (c + j)];
                        dot += a * mix[(size_t) (ref + j)];
                        en += a * a;
                    }
                    const double sc = dot / std::sqrt(en);
                    if (sc > best) { best = sc; pos = c; }
                }
            }
        }
        const int base = k * hs;
        for (int j = 0; j < w; ++j) {
            const int src = pos + j;
            if (src >= len) break;
            const float g = (k == 0 && j < hs) ? 1.f : win[(size_t) j];
            ol[(size_t) (base + j)] += g * L[si + src];
            if (!mono) orr[(size_t) (base + j)] += g * R[si + src];
        }
        prev = pos;
    }
    ol.resize((size_t) outLen);
    if (!mono) orr.resize((size_t) outLen);
    const int fade = std::min(64, outLen / 2);
    for (int i = 0; i < fade; ++i) {
        const float g = i / (float) fade;
        ol[(size_t) (outLen - 1 - i)] *= g;
        if (!mono) orr[(size_t) (outLen - 1 - i)] *= g;
    }
    return mono ? Sample::makeMono(s.name, std::move(ol)) : Sample::make(s.name, std::move(ol), std::move(orr));
}

} // namespace padlab
