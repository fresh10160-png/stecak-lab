// Headless checks for the pure-C++ core: engine, effects, stretch, pitch tracking, instruments.
#include <chrono>
#include <cstdio>
#include <random>
#include <thread>
#include "core/DemoKit.h"
#include "core/Stretch.h"
#include "core/Synth.h"

using namespace padlab;

static int failures = 0;
static const int B = 256;

static void check(const std::string& what, bool ok, const std::string& detail = "") {
    if (!ok) ++failures;
    std::printf("%-30s %s %s\n", what.c_str(), detail.c_str(), ok ? "ok" : "FAIL");
}

static std::string fmt(const char* f, double a = 0, double b = 0, double c = 0) {
    char buf[200];
    std::snprintf(buf, sizeof buf, f, a, b, c);
    return buf;
}

struct Stats { double peak = 0, rms = 0; bool nan = false; };

static Stats run(Engine& e, int blocks, std::vector<float>* capL = nullptr) {
    std::vector<float> l(B), r(B);
    Stats s;
    double sum = 0;
    long n = 0;
    for (int b = 0; b < blocks; ++b) {
        e.render(l.data(), r.data(), B, nullptr, 0);
        for (int i = 0; i < B; ++i) {
            if (std::isnan(l[(size_t) i]) || std::isnan(r[(size_t) i])) s.nan = true;
            s.peak = std::max({s.peak, (double) std::abs(l[(size_t) i]), (double) std::abs(r[(size_t) i])});
            sum += l[(size_t) i] * l[(size_t) i] + r[(size_t) i] * r[(size_t) i];
            n += 2;
            if (capL) capL->push_back(l[(size_t) i]);
        }
    }
    s.rms = std::sqrt(sum / (double) n);
    return s;
}

static void waitWarp(Engine& e) {
    for (int i = 0; i < 400; ++i) {
        if (e.warpBusy.load() == 0) {
            std::this_thread::sleep_for(std::chrono::milliseconds(40)); // let the worker pick up the request
            if (e.warpBusy.load() == 0) return;
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(20));
    }
}

static std::vector<float> sing(int sr, const std::vector<std::vector<double>>& spec, double vibCents, double noiseAmp, int harmonics, unsigned seed) {
    std::vector<float> x((size_t) (sr * (spec.back()[1] + 0.4)), 0.f);
    std::mt19937 rng(seed);
    std::uniform_real_distribution<double> u(-1, 1);
    for (const auto& n : spec) {
        double ph = 0;
        for (int i = (int) (n[0] * sr); i < (int) (n[1] * sr); ++i) {
            const double t = i / (double) sr, tn = t - n[0];
            const double f = 440 * std::pow(2.0, (n[2] - 69) / 12.0 + vibCents / 1200.0 * std::sin(2 * M_PI * 5.5 * t));
            ph += 2 * M_PI * f / sr;
            const double env = std::min(1.0, tn / 0.03) * std::min(1.0, (n[1] - t) / 0.03);
            double s = 0;
            for (int h = 1; h <= harmonics; ++h) s += std::sin(h * ph) / h;
            x[(size_t) i] += (float) (0.4 * s * env + noiseAmp * u(rng));
        }
    }
    return x;
}

int main() {
    for (int rate : {44100, 48000}) {
        SR = rate;
        std::printf("== sample rate %d\n", rate);

        // ---- master effects -----------------------------------------------------------------
        for (int fx = -1; fx < Fx::COUNT; ++fx) {
            Engine e;
            loadDemoKit(e);
            if (fx >= 0) (*e.master)[(size_t) fx]->enable(true);
            e.play();
            const Stats s = run(e, rate * 6 / B);
            check(std::string("master ") + (fx < 0 ? "(dry)" : (*e.master)[(size_t) fx]->name),
                  !s.nan && s.peak <= 1.0 && s.rms > 0.005, fmt("peak=%.3f rms=%.4f", s.peak, s.rms));
        }

        // ---- per-pad FX ---------------------------------------------------------------------
        {
            Engine e, d;
            loadDemoKit(e);
            loadDemoKit(d);
            (*e.chainFor(1))[Fx::REVERB]->p[2] = 0.8f;
            (*e.chainFor(1))[Fx::REVERB]->enable(true);
            e.trigger(1, 1.f);
            d.trigger(1, 1.f);
            run(e, rate / 2 / B);
            run(d, rate / 2 / B);
            const Stats wet = run(e, 40), dry = run(d, 40);
            check("pad FX reverb tail", wet.rms > 0.003 && dry.rms < 0.0005, fmt("wet=%.4f dry=%.5f", wet.rms, dry.rms));

            Engine o, ref;
            loadDemoKit(o);
            loadDemoKit(ref);
            (*o.chainFor(1))[Fx::LOFI]->p[0] = 0.f;
            (*o.chainFor(1))[Fx::LOFI]->enable(true);
            o.trigger(0, 1.f);
            ref.trigger(0, 1.f);
            const Stats x = run(o, 60), y = run(ref, 60);
            check("pad FX isolation", std::abs(x.rms - y.rms) < 1e-6, fmt("%.5f vs %.5f", x.rms, y.rms));
        }

        // ---- extremes -----------------------------------------------------------------------
        for (float val : {1.f, 0.f}) {
            Engine m;
            loadDemoKit(m);
            for (auto& f : *m.master) { f->enable(true); for (int i = 0; i < 3; ++i) f->p[(size_t) i] = val; }
            for (auto& f : *m.chainFor(2)) { f->enable(true); for (int i = 0; i < 3; ++i) f->p[(size_t) i] = val; }
            m.play();
            const Stats s = run(m, rate * 8 / B);
            check(fmt("all fx @%.0f", val), !s.nan && s.peak <= 1.0, fmt("peak=%.3f rms=%.4f", s.peak, s.rms));
        }

        // ---- WSOLA --------------------------------------------------------------------------
        {
            std::vector<float> sine((size_t) rate * 2);
            for (size_t i = 0; i < sine.size(); ++i) sine[i] = std::sin(2.f * PI_F * 440.f * (float) i / (float) rate);
            auto src = Sample::makeMono("sine", sine);
            for (double f : {0.6, 1.0, 1.5, 2.0}) {
                auto t0 = std::chrono::steady_clock::now();
                auto out = wsola(*src, 0, (int) sine.size(), f, nullptr);
                const double ms = std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - t0).count();
                int z = 0;
                const int a = rate / 10, b = out->n() - rate / 10;
                double rms = 0;
                for (int i = a + 1; i < b; ++i) {
                    if (out->l[(size_t) i - 1] < 0 && out->l[(size_t) i] >= 0) ++z;
                    rms += out->l[(size_t) i] * out->l[(size_t) i];
                }
                rms = std::sqrt(rms / (b - a));
                const double fr = z / ((b - a) / (double) rate);
                check(fmt("wsola x%.1f", f), std::abs(out->n() - sine.size() * f) < 2 && std::abs(fr - 440) < 6 && std::abs(rms - 0.707) < 0.06,
                      fmt("len=%.0f freq=%.1f rms=%.3f", out->n(), fr, rms) + fmt(" (%.0f ms)", ms));
            }
        }
        {
            Engine w;
            loadDemoKit(w);
            w.bpm = 120;
            w.pads[9].fitBeats = 4;
            w.requestWarp(9);
            waitWarp(w);
            auto rs = w.pads[9].rendered.get();
            check("fit 4 beats @120", rs && std::abs(rs->n() / (double) rate - 2.0) < 0.02, rs ? fmt("%.3fs", rs->n() / (double) rate) : "no render");

            Engine k;
            loadDemoKit(k);
            k.pads[9].pitch = 12;
            k.pads[9].keyLock = true;
            k.requestWarp(9);
            waitWarp(k);
            auto ks = k.pads[9].rendered.get();
            const double base = k.pads[9].sample.get()->n();
            check("keylock +12st", ks && std::abs(ks->n() / base - 2.0) < 0.02, ks ? fmt("stretched x%.2f", ks->n() / base) : "no render");
            k.trigger(9, 1.f);
            const Stats kr = run(k, rate / B);
            check("warped pad plays", kr.rms > 0.01 && kr.peak <= 1.0, fmt("rms=%.4f", kr.rms));
        }

        // ---- pad modes / pan / record -------------------------------------------------------
        {
            Engine m;
            loadDemoKit(m);
            m.pads[9].mode = Pad::LOOP;
            m.trigger(9, 1.f);
            const double on = run(m, 200).rms;
            m.trigger(9, 1.f);
            run(m, 20);
            const double off = run(m, 50).rms;
            check("loop latch", on > 0.05 && off < 0.001, fmt("on=%.4f off=%.5f", on, off));

            m.pads[0].pan = -1.f;
            m.trigger(0, 1.f);
            std::vector<float> l(B), r(B);
            m.render(l.data(), r.data(), B, nullptr, 0);
            double sl = 0, sr = 0;
            for (int i = 0; i < B; ++i) { sl += std::abs(l[(size_t) i]); sr += std::abs(r[(size_t) i]); }
            check("pan hard left", sl > 0 && sr == 0, fmt("L=%.2f R=%.2f", sl, sr));

            Engine rec;
            rec.startRecording();
            std::vector<float> in(B), l2(B), r2(B);
            for (int b = 0; b < 100; ++b) {
                for (int i = 0; i < B; ++i) in[(size_t) i] = b < 10 ? 0.f : 0.3f * std::sin(0.05f * (float) (b * B + i));
                rec.render(l2.data(), r2.data(), B, in.data(), B);
            }
            auto got = rec.stopRecording();
            check("mic capture", got && got->n() > 85 * B && got->n() < 92 * B, got ? fmt("%.0f frames", got->n()) : "none");
        }

        // ---- pitch tracking -----------------------------------------------------------------
        {
            const std::vector<std::vector<double>> spec = {{0.20, 0.80, 60}, {1.00, 1.60, 64}, {1.80, 2.50, 67}, {2.70, 3.40, 57}, {3.60, 4.20, 62}};
            const int want[5] = {60, 64, 67, 57, 62};
            struct Case { const char* name; double vib, noise, amp; int harm; };
            const Case cases[] = {{"sing clean", 0, 0, 1, 1}, {"sing vibrato h6", 30, 0.01, 1, 6}, {"sing vibrato h12", 40, 0.02, 1, 12},
                                  {"quiet mic -40 dB", 25, 0.0005, 0.01, 5}, {"breathy voice", 25, 0.12, 1, 5}};
            for (const auto& c : cases) {
                auto x = sing(rate, spec, c.vib, c.noise, c.harm, 5);
                for (auto& v : x) v *= (float) c.amp;
                Melody m = Melody::analyze(Sample::makeMono("sung", x));
                bool ok = m.notes.size() == 5;
                std::string desc = std::to_string(m.notes.size()) + " notes ";
                for (size_t i = 0; ok && i < 5; ++i) {
                    ok &= (int) std::lround(m.notes[i].pitch) == want[i] && std::abs(m.notes[i].start - (spec[i][0] - 0.2)) < 0.06 &&
                          std::abs(m.notes[i].dur - (spec[i][1] - spec[i][0])) < 0.1;
                }
                check(c.name, ok, desc);
            }
            std::vector<float> nz((size_t) rate * 2);
            std::mt19937 rng(1);
            std::normal_distribution<float> g(0.f, 0.004f);
            for (auto& v : nz) v = g(rng);
            check("no notes in noise", Melody::analyze(Sample::makeMono("n", nz)).notes.empty());
            check("scale snap", Melody::snapToScale(61.4f, 0, 1) == 62 && Melody::snapToScale(61.4f, 0, 0) == 61 && Melody::snapToScale(61.4f, 1, 1) == 61);

            auto rec = Sample::makeMono("sung", sing(rate, spec, 20, 0.005, 6, 5));
            Melody m = Melody::analyze(rec);
            auto src = Sample::makeMono("src", sing(rate, {{0, 1, 60}}, 0, 0, 4, 1));
            for (int inst = 1; inst < Synth::NUM; ++inst) {
                auto t0 = std::chrono::steady_clock::now();
                auto s = Synth::render(m.build(0, 0, 0, false, 100), rec, inst, inst == Synth::SAMPLE ? src : nullptr, 60, false, 100);
                const double ms = std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - t0).count();
                bool ok = s != nullptr;
                double pk = 0, rms = 0;
                if (ok) {
                    for (int i = 0; i < s->n(); ++i) { pk = std::max(pk, (double) std::abs(s->l[(size_t) i])); rms += s->l[(size_t) i] * s->l[(size_t) i]; if (std::isnan(s->l[(size_t) i])) ok = false; }
                    rms = std::sqrt(rms / s->n());
                    ok &= pk <= 0.9 && rms > 0.02;
                }
                check(std::string("render ") + Synth::NAMES[inst], ok, fmt("len=%.2fs peak=%.2f rms=%.3f", s ? s->n() / (double) rate : 0, pk, rms) + fmt(" (%.0f ms)", ms));
            }
            auto s = Synth::render(m.build(0, 0, 0, false, 100), rec, 1, nullptr, 60, false, 100);
            int z = 0;
            const int a0 = (int) (0.1 * rate), a1 = (int) (0.45 * rate);
            for (int i = a0 + 1; i < a1; ++i) if (s->l[(size_t) i - 1] < 0 && s->l[(size_t) i] >= 0) ++z;
            const double f = z / ((a1 - a0) / (double) rate);
            check("SINE pitch C4", std::abs(f - 261.6) < 6, fmt("%.1f Hz", f));
            auto o = Synth::render(m.build(0, 0, 1, true, 120), rec, 1, nullptr, 60, true, 120);
            const double bars = o->n() / (double) rate / (4 * 60.0 / 120);
            check("snap length = whole bars", std::abs(bars - std::round(bars)) < 0.01, fmt("%.2f bars", bars));
        }
    }
    std::printf(failures == 0 ? "ALL OK\n" : "%d FAILED\n", failures);
    return failures == 0 ? 0 : 1;
}
