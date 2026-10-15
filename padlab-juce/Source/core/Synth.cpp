#include "Synth.h"
#include <random>

namespace padlab {

const char* const Synth::NAMES[NUM] = {"ORIGINAL", "SINE", "SAW LEAD", "SQUARE", "PLUCK", "BASS", "PAD",
                                       "E-PIANO", "BELL", "ORGAN", "STRINGS", "FLUTE", "CHOIR", "SAMPLE"};

namespace {

float sr() { return (float) SR; }

struct Svf { // ZDF state-variable filter
    float ic1 = 0, ic2 = 0, lp = 0, bp = 0;
    void tick(float x, float fc, float q) {
        const float g = std::tan(PI_F * std::min(fc, 0.45f * sr()) / sr());
        const float k = 1.f / q;
        const float a1 = 1.f / (1.f + g * (g + k)), a2 = g * a1, a3 = g * a2;
        const float v3 = x - ic2, v1 = a1 * ic1 + a2 * v3, v2 = ic2 + a2 * ic1 + a3 * v3;
        ic1 = 2 * v1 - ic1;
        ic2 = 2 * v2 - ic2;
        bp = v1;
        lp = v2;
    }
};

float blep(float t, float dt) {
    if (t < dt) { t /= dt; return t + t - t * t - 1.f; }
    if (t > 1.f - dt) { t = (t - 1.f) / dt; return t * t + t + t + 1.f; }
    return 0.f;
}
float saw(float ph, float dt) { return 2.f * ph - 1.f - blep(ph, dt); }
float square(float ph, float dt, float duty) {
    float v = ph < duty ? 1.f : -1.f;
    v += blep(ph, dt);
    float p2 = ph - duty;
    if (p2 < 0) p2 += 1.f;
    return v - blep(p2, dt);
}
float wrap1(float x) { return x - std::floor(x); }

float adsr(int i, int len, float a, float d, float s, float r) {
    const float t = i / sr();
    auto level = [&](float tt) { return tt < a ? tt / a : (tt < a + d ? 1.f - (1.f - s) * (tt - a) / std::max(1e-4f, d) : s); };
    if (i < len) return level(t);
    const float tOff = (i - len) / sr();
    return std::max(0.f, level(len / sr()) * (1.f - tOff / r));
}

float releaseTime(int inst) {
    switch (inst) {
        case 4: return 1.6f;
        case 7: return 0.5f;
        case 8: return 2.2f;
        case 6: case 10: case 12: return 0.6f;
        default: return 0.18f;
    }
}

/** Adds one note to out. side = -1/+1 detunes the left/right copy of the wide instruments. */
void voice(std::vector<float>& out, int s0, int nl, double base, float vel, int inst, int side, std::mt19937& rng) {
    const float rel = releaseTime(inst);
    const int total = nl + (int) (rel * sr());
    float ph1 = 0, ph2 = 0, ph3 = 0, ph4 = 0, nz = 0;
    double ph64 = 0;
    Svf f1, f2;
    std::uniform_real_distribution<float> uni(-1.f, 1.f);
    const double fsr = SR;
    for (int i = 0; i < total && s0 + i < (int) out.size(); ++i) {
        const float t = i / sr();
        float y = 0, env;
        const float vib = std::sin(2.f * PI_F * 5.2f * t);
        switch (inst) {
            case 1: {
                const double fr = base * (1 + 0.002 * vib * std::min(1.f, t / 0.25f));
                ph64 += 2 * M_PI * fr / fsr;
                env = adsr(i, nl, 0.01f, 0.f, 1.f, 0.15f);
                y = (float) std::sin(ph64) * 0.7f * env;
                break;
            }
            case 2: {
                const float dt1 = (float) (base * 1.0035 / fsr), dt2 = (float) (base * 0.9965 / fsr);
                ph1 = wrap1(ph1 + dt1);
                ph2 = wrap1(ph2 + dt2);
                f1.tick(0.5f * (saw(ph1, dt1) + saw(ph2, dt2)), 900.f + 4500.f * std::exp(-t * 7.f), 1.8f);
                env = adsr(i, nl, 0.005f, 0.05f, 0.8f, 0.15f);
                y = f1.lp * 0.8f * env;
                break;
            }
            case 3: {
                const float dt = (float) (base / fsr);
                ph1 = wrap1(ph1 + dt);
                const float duty = 0.5f + 0.08f * std::sin(2.f * PI_F * 0.4f * t);
                f1.tick(square(ph1, dt, duty), 7000.f, 0.8f);
                env = adsr(i, nl, 0.003f, 0.f, 1.f, 0.07f);
                y = f1.lp * 0.45f * env;
                break;
            }
            case 4: {
                float s = 0;
                for (int k = 1; k <= 14 && base * k < 0.45 * fsr; ++k) {
                    const double ph = 2 * M_PI * base * k * i / fsr;
                    s += (float) (std::sin(ph) / k * std::exp(-t * (1.6 + k * 1.3)));
                }
                env = i < nl ? 1.f : std::max(0.f, 1.f - (i - nl) / (rel * sr() * 0.9f));
                y = s * 0.9f * env * std::min(1.f, i / 24.f);
                break;
            }
            case 5: {
                const double fb = base * 0.5;
                const float dt = (float) (fb / fsr);
                ph1 = wrap1(ph1 + dt);
                ph64 += 2 * M_PI * fb / fsr;
                f1.tick(saw(ph1, dt), 220.f + 1400.f * std::exp(-t * 9.f), 2.2f);
                env = adsr(i, nl, 0.004f, 0.08f, 0.85f, 0.1f);
                y = (f1.lp * 0.6f + (float) std::sin(ph64) * 0.7f) * env;
                break;
            }
            case 6: {
                const float dts[3] = {(float) (base * std::pow(2.0, (-9 + side * 3) / 1200.0) / fsr), (float) (base / fsr),
                                      (float) (base * std::pow(2.0, (9 + side * 3) / 1200.0) / fsr)};
                ph1 = wrap1(ph1 + dts[0]);
                ph2 = wrap1(ph2 + dts[1]);
                ph3 = wrap1(ph3 + dts[2]);
                const float x = (saw(ph1, dts[0]) + saw(ph2, dts[1]) + saw(ph3, dts[2])) / 3.f;
                f1.tick(x, 1300.f + 700.f * std::sin(2.f * PI_F * 0.25f * t), 0.9f);
                env = adsr(i, nl, 0.18f, 0.f, 1.f, 0.5f);
                y = f1.lp * 0.7f * env;
                break;
            }
            case 7: {
                const double ph = 2 * M_PI * base * t;
                const float idx = 2.4f * std::exp(-t * 4.5f) + 0.35f;
                float s = (float) std::sin(ph + idx * std::sin(ph));
                s += 0.12f * (float) (std::sin(2 * M_PI * base * 14 * t) * std::exp(-t * 22.f));
                const float dec = std::exp(-t * 1.1f);
                env = i < nl ? 1.f : std::max(0.f, 1.f - (i - nl) / (rel * sr()));
                y = s * dec * env * 0.7f * std::min(1.f, i / 24.f);
                break;
            }
            case 8: {
                const double ph = 2 * M_PI * base * t;
                const float idx = 3.6f * std::exp(-t * 1.6f);
                float s = (float) std::sin(ph + idx * std::sin(ph * 3.5));
                s += 0.35f * (float) (std::sin(ph * 2.0) * std::exp(-t * 2.4f));
                env = i < nl ? 1.f : std::max(0.f, 1.f - (i - nl) / (rel * sr()));
                y = s * std::exp(-t * 1.4f) * env * 0.6f * std::min(1.f, i / 24.f);
                break;
            }
            case 9: {
                const double ph = 2 * M_PI * base * t;
                const float s = (float) (std::sin(ph) + 0.8 * std::sin(2 * ph) + 0.6 * std::sin(3 * ph) + 0.5 * std::sin(4 * ph) +
                                         0.3 * std::sin(6 * ph) + 0.2 * std::sin(8 * ph)) / 3.4f;
                env = adsr(i, nl, 0.01f, 0.f, 1.f, 0.06f);
                y = s * env * 0.8f;
                break;
            }
            case 10: {
                const float v = 1 + 0.003f * vib * std::min(1.f, t / 0.3f);
                const float det[4] = {-10, -3, 3, 10};
                float phs[4] = {ph1, ph2, ph3, ph4};
                float x = 0;
                for (int k = 0; k < 4; ++k) {
                    const float dt = (float) (base * v * std::pow(2.0, (det[k] + side * 2) / 1200.0) / fsr);
                    phs[k] = wrap1(phs[k] + dt);
                    x += saw(phs[k], dt) * 0.25f;
                }
                ph1 = phs[0]; ph2 = phs[1]; ph3 = phs[2]; ph4 = phs[3];
                f1.tick(x, 2800.f, 0.8f);
                env = adsr(i, nl, 0.14f, 0.f, 1.f, 0.35f);
                y = f1.lp * 0.9f * env;
                break;
            }
            case 11: {
                const double fr = base * (1 + 0.004 * vib * std::min(1.f, std::max(0.f, (t - 0.2f) / 0.3f)));
                ph64 += 2 * M_PI * fr / fsr;
                nz += 0.35f * (uni(rng) - nz);
                const float s = (float) (std::sin(ph64) + 0.22 * std::sin(2 * ph64) + 0.08 * std::sin(3 * ph64));
                env = adsr(i, nl, 0.07f, 0.f, 0.92f, 0.12f);
                y = (s * 0.7f + nz * 0.07f * (1.f + 2.f * std::exp(-t * 12.f))) * env;
                break;
            }
            case 12: {
                const float v = 1 + 0.004f * vib * std::min(1.f, t / 0.35f);
                const float dt1 = (float) (base * v * std::pow(2.0, (-6 + side * 2) / 1200.0) / fsr);
                const float dt2 = (float) (base * v * std::pow(2.0, (6 + side * 2) / 1200.0) / fsr);
                ph1 = wrap1(ph1 + dt1);
                ph2 = wrap1(ph2 + dt2);
                const float x = 0.5f * (saw(ph1, dt1) + saw(ph2, dt2));
                f1.tick(x, 720.f, 6.f);
                f2.tick(x, 1250.f, 7.f);
                env = adsr(i, nl, 0.14f, 0.f, 1.f, 0.3f);
                y = (f1.bp + f2.bp * 0.6f) * 0.9f * env;
                break;
            }
            default: break;
        }
        out[(size_t) (s0 + i)] += y * vel;
    }
}

void playSample(std::vector<float>& l, std::vector<float>& r, int s0, int nl, const Sample& src, double rate, float vel) {
    const int relS = (int) (0.03 * sr());
    double pos = 0;
    for (int i = 0; i < nl + relS && s0 + i < (int) l.size(); ++i) {
        if (pos >= src.n() - 1) break;
        float env = i < nl ? 1.f : 1.f - (i - nl) / (float) relS;
        if (i < 48) env *= i / 48.f;
        const int i0 = (int) pos;
        const float fr = (float) (pos - i0);
        l[(size_t) (s0 + i)] += (src.L()[i0] + (src.L()[i0 + 1] - src.L()[i0]) * fr) * env * vel;
        r[(size_t) (s0 + i)] += (src.R()[i0] + (src.R()[i0 + 1] - src.R()[i0]) * fr) * env * vel;
        pos += rate;
    }
}

} // namespace

std::shared_ptr<const Sample> Synth::render(const std::vector<Melody::Out>& notes, std::shared_ptr<const Sample> recording,
                                            int inst, std::shared_ptr<const Sample> sampleSrc, int sampleRoot,
                                            bool snap, double bpm) {
    if (inst == ORIGINAL) return recording;
    if (notes.empty()) return nullptr;
    if (inst == SAMPLE && !sampleSrc) return nullptr;
    const auto& last = notes.back();
    const double end = last.start + last.dur;
    double total = end + releaseTime(inst) + 0.05;
    if (snap) {
        const double bar = 4 * 60.0 / bpm;
        total = std::max(std::ceil(end / bar - 1e-6) * bar, bar);
    }
    const int len = (int) (total * SR);
    std::vector<float> l((size_t) len, 0.f), r((size_t) len, 0.f), tmp;
    std::mt19937 rng(42);
    for (const auto& o : notes) {
        const int s0 = (int) (o.start * SR), nl = std::max(32, (int) (o.dur * SR));
        if (s0 >= len) continue;
        const double f = 440.0 * std::pow(2.0, (o.midi - 69) / 12.0);
        if (inst == SAMPLE) {
            playSample(l, r, s0, nl, *sampleSrc, std::pow(2.0, (o.midi - sampleRoot) / 12.0), o.vel);
        } else if (inst == 6 || inst == 10 || inst == 12) {
            voice(l, s0, nl, f, o.vel, inst, -1, rng);
            voice(r, s0, nl, f, o.vel, inst, 1, rng);
        } else {
            tmp.assign((size_t) len, 0.f);
            voice(tmp, s0, nl, f, o.vel, inst, 0, rng);
            for (int i = s0; i < len; ++i) { l[(size_t) i] += tmp[(size_t) i]; r[(size_t) i] += tmp[(size_t) i]; }
        }
    }
    float pk = 1e-6f;
    for (int i = 0; i < len; ++i) pk = std::max(pk, std::max(std::abs(l[(size_t) i]), std::abs(r[(size_t) i])));
    const float g = 0.85f / pk;
    for (int i = 0; i < len; ++i) { l[(size_t) i] *= g; r[(size_t) i] *= g; }
    const int fade = std::min(256, len / 2);
    for (int i = 0; i < fade; ++i) {
        const float k = i / (float) fade;
        l[(size_t) (len - 1 - i)] *= k;
        r[(size_t) (len - 1 - i)] *= k;
    }
    return Sample::make(NAMES[inst], std::move(l), std::move(r));
}

} // namespace padlab
