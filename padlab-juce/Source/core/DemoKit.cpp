#include "DemoKit.h"
#include <random>

namespace padlab {

namespace {

std::mt19937 rng;
float noise() { static std::uniform_real_distribution<float> u(-1.f, 1.f); return u(rng); }
double sr() { return (double) SR; }

std::vector<float> norm(std::vector<float> d) {
    float pk = 1e-6f;
    for (float v : d) pk = std::max(pk, std::abs(v));
    for (float& v : d) v *= 0.9f / pk;
    return d;
}

std::vector<float> kick() {
    std::vector<float> d((size_t) (sr() * 0.55));
    double ph = 0;
    for (size_t i = 0; i < d.size(); ++i) {
        const double t = (double) i / sr();
        ph += 2 * M_PI * (45 + 110 * std::exp(-t * 28)) / sr();
        double s = std::sin(ph) * std::exp(-t * 6.5);
        if (i < 100) s += noise() * 0.3 * (1 - (double) i / 100.0);
        d[i] = fastTanh((float) (s * 1.6));
    }
    return d;
}

std::vector<float> snare() {
    std::vector<float> d((size_t) (sr() * 0.35));
    float prev = 0;
    for (size_t i = 0; i < d.size(); ++i) {
        const double t = (double) i / sr();
        const float n = noise(), hp = n - prev * 0.6f;
        prev = n;
        d[i] = (float) (hp * std::exp(-t * 16) * 0.7 + std::sin(2 * M_PI * 185 * t) * std::exp(-t * 28) * 0.6 +
                        std::sin(2 * M_PI * 330 * t) * std::exp(-t * 35) * 0.3);
    }
    return d;
}

std::vector<float> hat(double len, double decay) {
    std::vector<float> d((size_t) (sr() * len));
    const double f[6] = {205.3, 304.4, 369.6, 522.7, 540, 800};
    float lp1 = 0, lp2 = 0;
    for (size_t i = 0; i < d.size(); ++i) {
        const double t = (double) i / sr();
        float s = 0;
        for (double fr : f) s += std::sin(2 * M_PI * fr * 2.6 * t) > 0 ? 1.f : -1.f;
        s = s / 6.f + noise() * 0.25f;
        lp1 += 0.35f * (s - lp1);
        const float h = s - lp1;
        lp2 += 0.35f * (h - lp2);
        d[i] = (float) ((h - lp2) * std::exp(-t * decay));
    }
    return d;
}

std::vector<float> clap() {
    std::vector<float> d((size_t) (sr() * 0.4));
    float lp = 0, bp = 0;
    for (size_t i = 0; i < d.size(); ++i) {
        const double t = (double) i / sr();
        lp += 0.25f * (noise() - lp);
        bp += 0.25f * (lp - bp);
        const float x = lp - bp;
        double env = 0;
        for (int b = 0; b < 3; ++b) if (t >= b * 0.011) env += std::exp(-(t - b * 0.011) * 150);
        env += std::exp(-std::max(0.0, t - 0.033) * 22) * 0.6;
        d[i] = (float) (x * env);
    }
    return d;
}

std::vector<float> rim() {
    std::vector<float> d((size_t) (sr() * 0.06));
    for (size_t i = 0; i < d.size(); ++i) {
        const double t = (double) i / sr();
        d[i] = (float) ((std::sin(2 * M_PI * 1700 * t) + std::sin(2 * M_PI * 480 * t) * 0.6 + noise() * 0.3) * std::exp(-t * 90));
    }
    return d;
}

std::vector<float> tom(double f0, double f1, double decay) {
    std::vector<float> d((size_t) (sr() * 0.5));
    double ph = 0;
    for (size_t i = 0; i < d.size(); ++i) {
        const double t = (double) i / sr();
        ph += 2 * M_PI * (f1 + (f0 - f1) * std::exp(-t * 18)) / sr();
        d[i] = (float) (std::sin(ph) * std::exp(-t * decay));
    }
    return d;
}

std::vector<float> cowbell() {
    std::vector<float> d((size_t) (sr() * 0.4));
    for (size_t i = 0; i < d.size(); ++i) {
        const double t = (double) i / sr();
        const float s = (std::sin(2 * M_PI * 540 * t) > 0 ? 1.f : -1.f) + (std::sin(2 * M_PI * 800 * t) > 0 ? 1.f : -1.f);
        d[i] = (float) (s * 0.5 * (std::exp(-t * 10) * 0.7 + std::exp(-t * 60) * 0.3));
    }
    return d;
}

std::vector<float> bass() {
    std::vector<float> d((size_t) sr());
    for (size_t i = 0; i < d.size(); ++i) {
        const double t = (double) i / sr();
        const double a = std::min(1.0, t / 0.005) * std::exp(-t * 2.2);
        d[i] = (float) ((std::sin(2 * M_PI * 55 * t) + 0.35 * std::sin(2 * M_PI * 110 * t)) * a);
    }
    return d;
}

std::vector<float> stab() {
    std::vector<float> d((size_t) (sr() * 0.8));
    const double f[4] = {220, 261.63, 329.63, 392};
    float lp = 0;
    for (size_t i = 0; i < d.size(); ++i) {
        const double t = (double) i / sr();
        double s = 0;
        for (double fr : f) s += 2 * std::fmod(fr * t, 1.0) - 1;
        const float k = (float) (0.04 + 0.5 * std::exp(-t * 9));
        lp += k * ((float) s - lp);
        d[i] = (float) (lp * std::min(1.0, t / 0.003) * std::exp(-t * 3.5));
    }
    return d;
}

std::vector<float> pluck() {
    const int n = (int) sr(), len = (int) (sr() / 330.0);
    std::vector<float> d((size_t) n), b((size_t) len);
    for (auto& v : b) v = noise();
    int ix = 0;
    for (int i = 0; i < n; ++i) {
        const int nx = (ix + 1) % len;
        const float y = 0.5f * (b[(size_t) ix] + b[(size_t) nx]) * 0.996f;
        d[(size_t) i] = b[(size_t) ix];
        b[(size_t) ix] = y;
        ix = nx;
    }
    return d;
}

std::vector<float> sweep() {
    std::vector<float> d((size_t) (sr() * 1.2));
    float lp = 0;
    for (size_t i = 0; i < d.size(); ++i) {
        const double t = (double) i / sr();
        const float k = (float) (0.005 + 0.6 * std::pow(t / 1.2, 2));
        lp += k * (noise() - lp);
        d[i] = (float) (lp * std::sin(M_PI * t / 1.2));
    }
    return d;
}

std::vector<float> zap() {
    std::vector<float> d((size_t) (sr() * 0.3));
    double ph = 0;
    for (size_t i = 0; i < d.size(); ++i) {
        const double t = (double) i / sr();
        ph += 2 * M_PI * (100 + 3000 * std::exp(-t * 20)) / sr();
        d[i] = (float) (std::sin(ph) * std::exp(-t * 8));
    }
    return d;
}

std::vector<float> crash() {
    std::vector<float> d((size_t) (sr() * 1.6));
    float lp = 0;
    for (size_t i = 0; i < d.size(); ++i) {
        const double t = (double) i / sr();
        const float s = noise();
        lp += 0.3f * (s - lp);
        d[i] = (float) ((s - lp) * std::exp(-t * 3));
    }
    return d;
}

std::vector<float> perc() {
    std::vector<float> d((size_t) (sr() * 0.25));
    double ph = 0;
    for (size_t i = 0; i < d.size(); ++i) {
        const double t = (double) i / sr();
        ph += 2 * M_PI * (300 + 500 * std::exp(-t * 40)) / sr();
        d[i] = (float) (std::sin(ph) * std::exp(-t * 16));
    }
    return d;
}

} // namespace

void loadDemoKit(Engine& e) {
    rng.seed(1234);
    const char* names[16] = {"KICK", "SNARE", "CL HAT", "OP HAT", "CLAP", "RIM", "TOM LO", "TOM HI",
                             "COWBELL", "BASS", "STAB", "PLUCK", "SWEEP", "ZAP", "CRASH", "PERC"};
    std::vector<float> d[16] = {kick(), snare(), hat(0.09, 70), hat(0.6, 9), clap(), rim(), tom(110, 70, 8), tom(190, 120, 9),
                                cowbell(), bass(), stab(), pluck(), sweep(), zap(), crash(), perc()};
    for (int i = 0; i < 16; ++i) {
        Pad& p = e.pads[(size_t) i];
        p.sample.set(Sample::makeMono(names[i], norm(std::move(d[i]))));
        p.name = names[i];
    }
    e.pads[2].choke = 1;
    e.pads[3].choke = 1;
    e.pads[9].mode = Pad::GATE;

    const std::vector<std::vector<int>> pattern = {{0, 7, 10}, {4, 12}, {0, 2, 4, 6, 8, 10, 12, 14}, {15}, {12}};
    for (size_t p = 0; p < pattern.size(); ++p)
        for (int s : pattern[p]) e.stepBits[p].fetch_or((uint16_t) (1u << s));
}

} // namespace padlab
