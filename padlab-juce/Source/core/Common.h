// PadLab core — plain C++17, no JUCE, so it can be unit-tested anywhere.
#pragma once
#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstdint>
#include <memory>
#include <string>
#include <vector>

namespace padlab {

inline int SR = 44100;                   // set once, before the engine is created
constexpr int MAXB = 2048, PADS = 64, STEPS = 16, VOICES = 32;
constexpr float PI_F = 3.14159265358979f, TWO_PI_F = 6.28318530717959f;

/** std::shared_ptr with atomic get/set so the UI thread can swap what the audio thread reads. */
template <class T>
class AtomicPtr {
public:
    std::shared_ptr<T> get() const { return std::atomic_load(&p); }
    void set(std::shared_ptr<T> v) { std::atomic_store(&p, std::move(v)); }
private:
    std::shared_ptr<T> p;
};

inline float fastTanh(float x) {
    if (x > 3.f) return 1.f;
    if (x < -3.f) return -1.f;
    const float x2 = x * x;
    return x * (27.f + x2) / (27.f + 9.f * x2);
}

inline float softClip(float x) {
    if (x > 0.9f) return 0.9f + 0.1f * fastTanh((x - 0.9f) * 10.f);
    if (x < -0.9f) return -0.9f + 0.1f * fastTanh((x + 0.9f) * 10.f);
    return x;
}

inline float onePole(float fc) { return 1.f - std::exp(-TWO_PI_F * fc / (float) SR); }

} // namespace padlab
