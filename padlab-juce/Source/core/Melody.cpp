#include "Melody.h"
#include <limits>

namespace padlab {

const char* const Melody::SCALE_NAMES[NUM_SCALES] = {"CHROMATIC", "MAJOR", "MINOR", "PENT MAJ", "PENT MIN", "BLUES", "DORIAN"};
const char* const Melody::KEYS[12] = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

namespace {
const std::vector<std::vector<int>> SCALES = {
    {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11}, {0, 2, 4, 5, 7, 9, 11}, {0, 2, 3, 5, 7, 8, 10},
    {0, 2, 4, 7, 9}, {0, 3, 5, 7, 10}, {0, 3, 5, 6, 7, 10}, {0, 2, 3, 5, 7, 9, 10}};

float median(std::vector<float> v) {
    std::sort(v.begin(), v.end());
    return v[v.size() / 2];
}
const float NaN = std::numeric_limits<float>::quiet_NaN();
}

std::string Melody::noteName(int midi) {
    return std::string(KEYS[((midi % 12) + 12) % 12]) + std::to_string(midi / 12 - 1);
}

Melody Melody::analyze(std::shared_ptr<const Sample> rec) {
    Melody m;
    m.recording = rec;
    const int sr = SR;
    const int dec = std::max(1, sr / 16000), fs = sr / dec;
    const int n = rec->n() / dec;
    std::vector<float> y((size_t) std::max(0, n));
    float peak = 1e-9f;
    for (int i = 0; i < n; ++i) {
        float s = 0;
        for (int k = 0; k < dec; ++k) s += rec->l[(size_t) (i * dec + k)];
        y[(size_t) i] = s / dec;
        peak = std::max(peak, std::abs(y[(size_t) i]));
    }
    if (peak < 1e-4f) return m;                      // silence
    for (auto& v : y) v *= 0.8f / peak;              // quiet mics still get analysed

    const int win = (int) (0.032 * fs), hop = (int) (0.010 * fs);
    const int tauMax = fs / 70, tauMin = std::max(2, fs / 1000);
    const int frames = (n - win - tauMax) / hop;
    if (frames < 10) return m;

    std::vector<float> pitch((size_t) frames, NaN), rms((size_t) frames);
    std::vector<float> d((size_t) tauMax + 2), c((size_t) tauMax + 2);
    for (int f = 0; f < frames; ++f) {
        const int t0 = f * hop;
        double e = 0;
        for (int j = 0; j < win; ++j) e += y[(size_t) (t0 + j)] * y[(size_t) (t0 + j)];
        rms[(size_t) f] = (float) std::sqrt(e / win);
    }
    std::vector<float> sorted = rms;
    std::sort(sorted.begin(), sorted.end());
    const float ref = sorted[(size_t) (frames * 0.95f)];
    const float gate = std::max(0.006f, ref * 0.12f);

    for (int f = 0; f < frames; ++f) {
        if (rms[(size_t) f] < gate) continue;
        const int t0 = f * hop;
        for (int tau = 1; tau <= tauMax; ++tau) {
            float s = 0;
            for (int j = 0; j < win; ++j) {
                const float df = y[(size_t) (t0 + j)] - y[(size_t) (t0 + j + tau)];
                s += df * df;
            }
            d[(size_t) tau] = s;
        }
        float run = 0;
        c[0] = 1.f;
        for (int tau = 1; tau <= tauMax; ++tau) {
            run += d[(size_t) tau];
            c[(size_t) tau] = run > 0 ? d[(size_t) tau] * tau / run : 1.f;
        }
        int best = -1;
        for (int tau = tauMin; tau < tauMax; ++tau) {
            if (c[(size_t) tau] < 0.2f) {
                while (tau + 1 < tauMax && c[(size_t) tau + 1] < c[(size_t) tau]) ++tau;
                best = tau;
                break;
            }
        }
        if (best < 0) {
            int arg = tauMin;
            for (int tau = tauMin; tau < tauMax; ++tau) if (c[(size_t) tau] < c[(size_t) arg]) arg = tau;
            if (c[(size_t) arg] < 0.4f) best = arg;
        }
        if (best < 0) continue;
        float tau = (float) best;
        if (best > 1 && best < tauMax) {
            const float a = c[(size_t) best - 1], b = c[(size_t) best], g = c[(size_t) best + 1], den = a - 2 * b + g;
            if (std::abs(den) > 1e-9f) tau += 0.5f * (a - g) / den;
        }
        const float f0 = (float) fs / tau;
        pitch[(size_t) f] = (float) (69 + 12 * std::log(f0 / 440.0) / std::log(2.0));
    }

    std::vector<float> sm = pitch;                    // 5-frame median over voiced neighbours
    for (int f = 2; f < frames - 2; ++f) {
        if (std::isnan(pitch[(size_t) f])) continue;
        std::vector<float> w;
        for (int j = -2; j <= 2; ++j) if (!std::isnan(pitch[(size_t) (f + j)])) w.push_back(pitch[(size_t) (f + j)]);
        if (w.size() >= 3) sm[(size_t) f] = median(w);
    }
    m.segment(sm, rms, ref, hop, win, fs);
    return m;
}

void Melody::segment(const std::vector<float>& p, const std::vector<float>& rms, float ref, int hop, int win, int fs) {
    std::vector<float> cur, dev;
    int startF = -1, unvoiced = 0;
    float rsum = 0;
    const int frames = (int) p.size();
    for (int f = 0; f <= frames; ++f) {
        const float v = f < frames ? p[(size_t) f] : NaN;
        if (std::isnan(v)) {
            ++unvoiced;
            if (startF >= 0 && (unvoiced >= 3 || f == frames)) {
                close(cur, startF, f - unvoiced + 1, rsum, ref, hop, win, fs);
                startF = -1;
                cur.clear();
                dev.clear();
            }
            continue;
        }
        unvoiced = 0;
        if (startF < 0) {
            startF = f; cur.clear(); dev.clear(); rsum = 0;
            cur.push_back(v); rsum += rms[(size_t) f];
            continue;
        }
        const size_t from = cur.size() > 8 ? cur.size() - 8 : 0;
        const float cm = median(std::vector<float>(cur.begin() + (long) from, cur.end()));
        if (std::abs(v - cm) > 0.75f) {
            dev.push_back(v);
            if (dev.size() >= 4) {
                const int split = f - (int) dev.size() + 1;
                close(cur, startF, split, rsum, ref, hop, win, fs);
                startF = split;
                cur = dev;
                dev.clear();
                rsum = 0;
                for (int q = split; q <= f; ++q) rsum += rms[(size_t) q];
            }
        } else {
            for (float q : dev) cur.push_back(q);
            dev.clear();
            cur.push_back(v);
            rsum += rms[(size_t) f];
        }
    }
    if (notes.empty()) return;
    for (int i = (int) notes.size() - 2; i >= 0; --i) { // merge same-pitch neighbours with a tiny gap
        Note& a = notes[(size_t) i];
        const Note& b = notes[(size_t) i + 1];
        if (std::abs(a.pitch - b.pitch) < 0.5f && b.start - (a.start + a.dur) < 0.05) {
            a.dur = b.start + b.dur - a.start;
            a.pitch = (a.pitch + b.pitch) / 2;
            notes.erase(notes.begin() + i + 1);
        }
    }
    const double t0 = notes[0].start;
    for (auto& nt : notes) nt.start -= t0;
}

void Melody::close(const std::vector<float>& cur, int sf, int ef, float rsum, float ref, int hop, int win, int fs) {
    if (ef - sf < 6 || cur.empty()) return;
    Note n;
    n.start = (sf * (double) hop + win / 2.0) / fs;
    n.dur = (ef - sf) * (double) hop / fs;
    n.pitch = median(cur);
    const float avg = rsum / (float) std::max(1, ef - sf);
    n.vel = std::max(0.35f, std::min(1.f, 0.4f + 0.6f * avg / std::max(1e-6f, ref)));
    notes.push_back(n);
}

int Melody::snapToScale(float p, int root, int scale) {
    const auto& sc = SCALES[(size_t) scale];
    int best = (int) std::lround(p);
    float bd = 1e9f;
    for (int k = (int) std::lround(p) - 7; k <= (int) std::lround(p) + 7; ++k) {
        const int pc = (((k - root) % 12) + 12) % 12;
        const bool in = std::find(sc.begin(), sc.end(), pc) != sc.end();
        if (in && std::abs((float) k - p) < bd) { bd = std::abs((float) k - p); best = k; }
    }
    return best;
}

std::vector<Melody::Out> Melody::build(int root, int scale, int octave, bool snap, double bpm) const {
    std::vector<Out> out;
    const double step = 60.0 / bpm / 4.0;
    for (size_t idx = 0; idx < notes.size(); ++idx) {
        const Note& n = notes[idx];
        if (n.deleted) continue;
        Out o;
        o.src = (int) idx;
        o.midi = std::max(12, std::min(120, snapToScale(n.pitch, root, scale) + n.shift + 12 * octave));
        o.vel = n.vel;
        if (snap) {
            const double s = std::round(n.start / step) * step;
            double e = std::round((n.start + n.dur) / step) * step;
            if (e <= s) e = s + step;
            o.start = s;
            o.dur = e - s;
        } else {
            o.start = n.start;
            o.dur = n.dur;
        }
        out.push_back(o);
    }
    for (size_t i = 0; i + 1 < out.size(); ++i) {     // keep it monophonic
        Out& a = out[i];
        const Out& b = out[i + 1];
        if (a.start + a.dur > b.start) a.dur = std::max(0.03, b.start - a.start);
    }
    return out;
}

} // namespace padlab
