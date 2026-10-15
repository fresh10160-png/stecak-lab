#include "Fx.h"
#include <cstdio>

namespace padlab {

std::string Fx::text(int i) const {
    return std::to_string((int) std::lround(p[(size_t) i].load() * 100.f)) + "%";
}

namespace {

std::string fmt(const char* f, double v) {
    char buf[32];
    std::snprintf(buf, sizeof buf, f, v);
    return buf;
}

float P(const Fx& f, int i) { return f.p[(size_t) i].load(); }

// ---- ISOLATOR: 3-band kill ------------------------------------------------------------
class Isolator : public Fx {
public:
    Isolator() : Fx("ISOLATOR", {"Low", "Mid", "High"}, 1.f, 1.f, 1.f) {}
    void process(float* l, float* r, int n) override {
        const float k1 = onePole(250.f), k2 = onePole(2800.f);
        const float t[3] = {P(*this, 0) * P(*this, 0), P(*this, 1) * P(*this, 1), P(*this, 2) * P(*this, 2)};
        for (int i = 0; i < n; ++i) {
            for (int b = 0; b < 3; ++b) g[b] += (t[b] - g[b]) * 0.002f;
            for (int ch = 0; ch < 2; ++ch) {
                const float x = ch == 0 ? l[i] : r[i];
                float* z = s[ch];
                z[0] += k1 * (x - z[0]);
                z[1] += k1 * (z[0] - z[1]);
                z[2] += k2 * (x - z[2]);
                z[3] += k2 * (z[2] - z[3]);
                const float low = z[1], mid = z[3] - z[1], high = x - z[3];
                const float y = low * g[0] + mid * g[1] + high * g[2];
                (ch == 0 ? l[i] : r[i]) = y;
            }
        }
    }
private:
    float s[2][4] = {}, g[3] = {1, 1, 1};
};

// ---- FILTER + DRIVE ----------------------------------------------------------------------
class FilterDrive : public Fx {
public:
    FilterDrive() : Fx("FILTER+DRIVE", {"Cutoff", "Reso", "Drive"}, 1.f, 0.3f, 0.2f) {}
    std::string text(int i) const override {
        if (i == 0) return std::to_string((int) std::lround(40.f * std::pow(400.f, P(*this, 0)))) + " Hz";
        return Fx::text(i);
    }
    void process(float* l, float* r, int n) override {
        const float target = 40.f * std::pow(400.f, P(*this, 0));
        fcS += (target - fcS) * 0.35f;
        const float g = std::tan(PI_F * std::min(fcS, 18000.f) / (float) SR);
        const float k = 2.f - 2.f * std::min(0.97f, P(*this, 1));
        const float a1 = 1.f / (1.f + g * (g + k)), a2 = g * a1, a3 = g * a2;
        const float pre = 1.f + P(*this, 2) * 10.f, post = 1.f / (1.f + P(*this, 2) * 3.5f);
        for (int i = 0; i < n; ++i) {
            for (int ch = 0; ch < 2; ++ch) {
                const float x = fastTanh((ch == 0 ? l[i] : r[i]) * pre);
                float* z = ic[ch];
                const float v3 = x - z[1];
                const float v1 = a1 * z[0] + a2 * v3;
                const float v2 = z[1] + a2 * z[0] + a3 * v3;
                z[0] = 2.f * v1 - z[0];
                z[1] = 2.f * v2 - z[1];
                (ch == 0 ? l[i] : r[i]) = v2 * post;
            }
        }
    }
private:
    float ic[2][2] = {};
    float fcS = 16000.f;
};

// ---- LO-FI -------------------------------------------------------------------------------
class LoFi : public Fx {
public:
    LoFi() : Fx("LO-FI", {"Rate", "Bits", "Tone"}, 0.6f, 0.6f, 0.8f) {}
    std::string text(int i) const override {
        if (i == 0) return std::to_string((int) std::lround(rate())) + " Hz";
        if (i == 1) return fmt("%.1f bit", bits());
        return Fx::text(i);
    }
    void process(float* l, float* r, int n) override {
        const float step = std::min(1.f, rate() / (float) SR);
        const float lv = std::pow(2.f, bits() - 1.f);
        const float k = onePole(1500.f * std::pow(12.f, P(*this, 2)));
        for (int i = 0; i < n; ++i) {
            for (int ch = 0; ch < 2; ++ch) {
                acc[ch] += step;
                if (acc[ch] >= 1.f) {
                    acc[ch] -= 1.f;
                    hold[ch] = std::round((ch == 0 ? l[i] : r[i]) * lv) / lv;
                }
                lp[ch] += k * (hold[ch] - lp[ch]);
                (ch == 0 ? l[i] : r[i]) = lp[ch];
            }
        }
    }
private:
    float rate() const { return 1500.f * std::pow(29.4f, P(*this, 0)); }
    float bits() const { return 3.f + 13.f * P(*this, 1); }
    float acc[2] = {}, hold[2] = {}, lp[2] = {};
};

// ---- RING MOD ----------------------------------------------------------------------------
class RingMod : public Fx {
public:
    RingMod() : Fx("RING MOD", {"Freq", "Mix"}, 0.4f, 0.5f) {}
    std::string text(int i) const override {
        return i == 0 ? std::to_string((int) std::lround(freq())) + " Hz" : Fx::text(i);
    }
    void process(float* l, float* r, int n) override {
        const float inc = TWO_PI_F * freq() / (float) SR, mix = P(*this, 1);
        for (int i = 0; i < n; ++i) {
            const float c = std::sin(ph);
            ph += inc;
            if (ph > TWO_PI_F) ph -= TWO_PI_F;
            l[i] = l[i] * (1 - mix) + l[i] * c * mix;
            r[i] = r[i] * (1 - mix) + r[i] * c * mix;
        }
    }
private:
    float freq() const { return 30.f * std::pow(150.f, P(*this, 0)); }
    float ph = 0;
};

// ---- RESONATOR ---------------------------------------------------------------------------
class Resonator : public Fx {
public:
    Resonator() : Fx("RESONATOR", {"Pitch", "Feedback", "Mix"}, 0.4f, 0.8f, 0.5f) {}
    void alloc() override { if (buf[0].empty()) { buf[0].assign(4096, 0.f); buf[1].assign(4096, 0.f); } }
    std::string text(int i) const override {
        return i == 0 ? std::to_string((int) std::lround(freq())) + " Hz" : Fx::text(i);
    }
    void process(float* l, float* r, int n) override {
        const float target = (float) SR / freq();
        const float fb = P(*this, 1) * 0.96f, mix = P(*this, 2), wetGain = 1.f - 0.6f * fb;
        for (int i = 0; i < n; ++i) {
            dS += (target - dS) * 0.002f;
            float rp = (float) w - dS;
            if (rp < 0) rp += 4096;
            const int i0 = (int) rp & 4095, i1 = (i0 + 1) & 4095;
            const float fr = rp - std::floor(rp);
            for (int ch = 0; ch < 2; ++ch) {
                const float x = ch == 0 ? l[i] : r[i];
                auto& b = buf[ch];
                const float d = b[(size_t) i0] + (b[(size_t) i1] - b[(size_t) i0]) * fr;
                damp[ch] += 0.55f * (d - damp[ch]);
                const float y = x + fb * damp[ch];
                b[(size_t) w] = fastTanh(y);
                (ch == 0 ? l[i] : r[i]) = x * (1 - mix) + y * wetGain * mix;
            }
            w = (w + 1) & 4095;
        }
    }
private:
    float freq() const { return 55.f * std::pow(2.f, P(*this, 0) * 5.f); }
    std::vector<float> buf[2];
    float damp[2] = {};
    int w = 0;
    float dS = 200.f;
};

// ---- FLANGER -----------------------------------------------------------------------------
class Flanger : public Fx {
public:
    Flanger() : Fx("FLANGER", {"Rate", "Depth", "Feedback"}, 0.3f, 0.6f, 0.5f) {}
    void alloc() override { if (buf[0].empty()) { buf[0].assign(1024, 0.f); buf[1].assign(1024, 0.f); } }
    std::string text(int i) const override { return i == 0 ? fmt("%.2f Hz", rate()) : Fx::text(i); }
    void process(float* l, float* r, int n) override {
        const float inc = TWO_PI_F * rate() / (float) SR, fb = P(*this, 2) * 0.85f, depth = P(*this, 1) * 300.f;
        for (int i = 0; i < n; ++i) {
            for (int ch = 0; ch < 2; ++ch) {
                const float lfo = 0.5f + 0.5f * std::sin(ph + (float) ch * 1.2f);
                const float d = 24.f + depth * lfo;
                float rp = (float) w - d;
                if (rp < 0) rp += 1024;
                const int i0 = (int) rp & 1023, i1 = (i0 + 1) & 1023;
                const float fr = rp - std::floor(rp);
                auto& b = buf[ch];
                const float wet = b[(size_t) i0] + (b[(size_t) i1] - b[(size_t) i0]) * fr;
                const float x = ch == 0 ? l[i] : r[i];
                b[(size_t) w] = fastTanh(x + wet * fb);
                (ch == 0 ? l[i] : r[i]) = 0.7f * x + 0.7f * wet;
            }
            w = (w + 1) & 1023;
            ph += inc;
            if (ph > TWO_PI_F) ph -= TWO_PI_F;
        }
    }
private:
    float rate() const { return 0.05f * std::pow(100.f, P(*this, 0)); }
    std::vector<float> buf[2];
    int w = 0;
    float ph = 0;
};

// ---- SLICER ------------------------------------------------------------------------------
class Slicer : public Fx {
public:
    Slicer() : Fx("SLICER", {"Rate", "Depth", "Duty"}, 0.5f, 0.9f, 0.5f) {}
    std::string text(int i) const override { return i == 0 ? DIV[idx()] : Fx::text(i); }
    void process(float* l, float* r, int n) override {
        const double period = SR * 60.0 / bpmNow() * BEATS[idx()];
        const float low = 1.f - P(*this, 1), duty = 0.2f + 0.6f * P(*this, 2);
        for (int i = 0; i < n; ++i) {
            const float tgt = phase / period < duty ? 1.f : low;
            gS += (tgt - gS) * 0.02f;
            l[i] *= gS;
            r[i] *= gS;
            phase += 1.0;
            if (phase >= period) phase -= period;
        }
    }
private:
    static constexpr const char* DIV[4] = {"1/4", "1/8", "1/16", "1/32"};
    static constexpr float BEATS[4] = {1.f, 0.5f, 0.25f, 0.125f};
    int idx() const { return std::min(3, (int) (P(*this, 0) * 4.f)); }
    double phase = 0;
    float gS = 1.f;
};

// ---- SYNC DELAY --------------------------------------------------------------------------
class SyncDelay : public Fx {
public:
    SyncDelay() : Fx("SYNC DELAY", {"Time", "Feedback", "Mix"}, 0.5f, 0.5f, 0.35f) {}
    void alloc() override { if (buf[0].empty()) { buf[0].assign(SIZE, 0.f); buf[1].assign(SIZE, 0.f); } }
    std::string text(int i) const override { return i == 0 ? DIV[idx()] : Fx::text(i); }
    void process(float* l, float* r, int n) override {
        const double target = std::min((double) (SIZE - 8), SR * 60.0 / bpmNow() * BEATS[idx()]);
        const float fb = P(*this, 1) * 0.92f, mix = P(*this, 2), k = onePole(3500.f);
        for (int i = 0; i < n; ++i) {
            dS += (target - dS) * 0.0004;
            double rp = w - dS;
            if (rp < 0) rp += SIZE;
            const int i0 = (int) rp & MASK, i1 = (i0 + 1) & MASK;
            const float fr = (float) (rp - std::floor(rp));
            for (int ch = 0; ch < 2; ++ch) {
                auto& b = buf[ch];
                const float wet = b[(size_t) i0] + (b[(size_t) i1] - b[(size_t) i0]) * fr;
                lp[ch] += k * (wet - lp[ch]);
                const float x = ch == 0 ? l[i] : r[i];
                b[(size_t) w] = x + fastTanh(lp[ch] * fb);
                (ch == 0 ? l[i] : r[i]) = x + wet * mix;
            }
            w = (w + 1) & MASK;
        }
    }
private:
    static constexpr int SIZE = 131072, MASK = SIZE - 1;
    static constexpr const char* DIV[5] = {"1/4", "d1/8", "1/8", "1/8T", "1/16"};
    static constexpr float BEATS[5] = {1.f, 0.75f, 0.5f, 1.f / 3.f, 0.25f};
    int idx() const { return std::min(4, (int) (P(*this, 0) * 5.f)); }
    std::vector<float> buf[2];
    float lp[2] = {};
    int w = 0;
    double dS = 20000;
};

// ---- REVERB (Freeverb topology) ----------------------------------------------------------
class Reverb : public Fx {
public:
    Reverb() : Fx("REVERB", {"Size", "Damp", "Mix"}, 0.6f, 0.4f, 0.25f) {}
    void alloc() override {
        if (!cb[0][0].empty()) return;
        for (int ch = 0; ch < 2; ++ch) {
            for (int c = 0; c < 8; ++c) cb[ch][c].assign((size_t) (CT[c] + ch * 23), 0.f);
            for (int a = 0; a < 4; ++a) ab[ch][a].assign((size_t) (AT[a] + ch * 23), 0.f);
        }
    }
    void process(float* l, float* r, int n) override {
        const float fb = 0.7f + 0.28f * P(*this, 0), damp = 0.1f + 0.7f * P(*this, 1), mix = P(*this, 2);
        for (int i = 0; i < n; ++i) {
            const float in = (l[i] + r[i]) * 0.015f;
            for (int ch = 0; ch < 2; ++ch) {
                float sum = 0;
                for (int c = 0; c < 8; ++c) {
                    auto& b = cb[ch][c];
                    int ix = ci[ch][c];
                    const float o = b[(size_t) ix];
                    cs[ch][c] = o * (1 - damp) + cs[ch][c] * damp;
                    b[(size_t) ix] = in + cs[ch][c] * fb;
                    ci[ch][c] = ++ix >= (int) b.size() ? 0 : ix;
                    sum += o;
                }
                for (int a = 0; a < 4; ++a) {
                    auto& b = ab[ch][a];
                    int ix = ai[ch][a];
                    const float bo = b[(size_t) ix];
                    const float o = -sum + bo;
                    b[(size_t) ix] = sum + bo * 0.5f;
                    ai[ch][a] = ++ix >= (int) b.size() ? 0 : ix;
                    sum = o;
                }
                const float x = ch == 0 ? l[i] : r[i];
                (ch == 0 ? l[i] : r[i]) = x * (1 - mix * 0.5f) + sum * 3.f * mix;
            }
        }
    }
private:
    static constexpr int CT[8] = {1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617};
    static constexpr int AT[4] = {556, 441, 341, 225};
    std::vector<float> cb[2][8], ab[2][4];
    int ci[2][8] = {}, ai[2][4] = {};
    float cs[2][8] = {};
};

// ---- COMPRESSOR --------------------------------------------------------------------------
class Compressor : public Fx {
public:
    Compressor() : Fx("COMPRESSOR", {"Sustain", "Attack", "Level"}, 0.5f, 0.3f, 0.5f) {}
    void process(float* l, float* r, int n) override {
        const float thr = -6.f - 34.f * P(*this, 0), ratio = 2.f + 10.f * P(*this, 0);
        const float atk = std::exp(-1.f / ((float) SR * (0.001f + 0.04f * P(*this, 1))));
        const float rel = std::exp(-1.f / ((float) SR * 0.18f));
        const float makeup = 0.3f * (-thr) * (1.f - 1.f / ratio) + (P(*this, 2) - 0.5f) * 24.f;
        for (int i = 0; i < n; ++i) {
            const float det = std::max(std::abs(l[i]), std::abs(r[i]));
            env = det > env ? atk * env + (1 - atk) * det : rel * env + (1 - rel) * det;
            const float db = 20.f * std::log10(std::max(env, 1e-6f));
            const float over = db - thr;
            const float gr = over > 0 ? over * (1.f - 1.f / ratio) : 0.f;
            const float g = std::pow(10.f, (makeup - gr) / 20.f);
            l[i] *= g;
            r[i] *= g;
        }
    }
private:
    float env = 0;
};

// ---- VINYL SIM ---------------------------------------------------------------------------
class Vinyl : public Fx {
public:
    Vinyl() : Fx("VINYL SIM", {"Wow/Flutter", "Crackle", "Tone"}, 0.3f, 0.4f, 0.7f), rng(7) {}
    void alloc() override { if (buf[0].empty()) { buf[0].assign(2048, 0.f); buf[1].assign(2048, 0.f); } }
    void process(float* l, float* r, int n) override {
        const float wow = P(*this, 0);
        const float k = onePole(2500.f * std::pow(7.f, P(*this, 2)));
        const float crackP = P(*this, 1) * P(*this, 1) * 0.0004f, hiss = P(*this, 1) * 0.0025f;
        std::uniform_real_distribution<float> u(0.f, 1.f);
        for (int i = 0; i < n; ++i) {
            const double mod = std::sin(2 * M_PI * 0.55 * t) * 10 + std::sin(2 * M_PI * 6.3 * t) * 2.5 +
                               std::sin(2 * M_PI * 0.13 * t) * 6;
            t += 1.0 / SR;
            float rp = (float) (w - (600 + mod * wow));
            if (rp < 0) rp += 2048;
            const int i0 = (int) rp & 2047, i1 = (i0 + 1) & 2047;
            const float fr = rp - std::floor(rp);
            for (int ch = 0; ch < 2; ++ch) {
                auto& b = buf[ch];
                b[(size_t) w] = ch == 0 ? l[i] : r[i];
                float y = b[(size_t) i0] + (b[(size_t) i1] - b[(size_t) i0]) * fr;
                if (u(rng) < crackP) crk[ch] = (u(rng) * 2.f - 1.f) * (0.1f + 0.5f * u(rng));
                y += crk[ch] + (u(rng) - 0.5f) * 2.f * hiss;
                crk[ch] *= 0.8f;
                y = fastTanh(y * 1.3f) * 0.85f;
                lp[ch] += k * (y - lp[ch]);
                hp[ch] += 0.004f * (lp[ch] - hp[ch]);
                (ch == 0 ? l[i] : r[i]) = lp[ch] - hp[ch];
            }
            w = (w + 1) & 2047;
        }
    }
private:
    std::vector<float> buf[2];
    float lp[2] = {}, hp[2] = {}, crk[2] = {};
    std::mt19937 rng;
    int w = 0;
    double t = 0;
};

} // namespace

std::shared_ptr<Chain> makeRack(const std::atomic<float>* bpm) {
    auto c = std::make_shared<Chain>();
    c->emplace_back(new Isolator);
    c->emplace_back(new FilterDrive);
    c->emplace_back(new LoFi);
    c->emplace_back(new RingMod);
    c->emplace_back(new Resonator);
    c->emplace_back(new Flanger);
    c->emplace_back(new Slicer);
    c->emplace_back(new SyncDelay);
    c->emplace_back(new Reverb);
    c->emplace_back(new Compressor);
    c->emplace_back(new Vinyl);
    for (auto& f : *c) f->bpm = bpm;
    return c;
}

} // namespace padlab
