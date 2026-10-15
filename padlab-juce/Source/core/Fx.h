#pragma once
#include <array>
#include <random>
#include "Common.h"

namespace padlab {

/** Master/pad effect rack inspired by the Roland SP-303 / SP-202 (not exact emulations). */
class Fx {
public:
    enum Id { ISOLATOR, FILTER, LOFI, RING, RESO, FLANGER, SLICER, DELAY, REVERB, COMP, VINYL, COUNT };

    Fx(std::string name, std::vector<std::string> labels, float d0, float d1 = 0.f, float d2 = 0.f)
        : name(std::move(name)), labels(std::move(labels)), def{d0, d1, d2} {
        reset();
    }
    virtual ~Fx() = default;

    virtual void process(float* l, float* r, int n) = 0;
    virtual void alloc() {}
    virtual std::string text(int i) const;

    void reset() { for (int i = 0; i < 3; ++i) p[(size_t) i] = def[(size_t) i]; }
    void enable(bool b) { if (b) alloc(); on = b; }
    int params() const { return (int) labels.size(); }

    const std::string name;
    const std::vector<std::string> labels;
    const std::array<float, 3> def;
    std::array<std::atomic<float>, 3> p;
    std::atomic<bool> on{false};
    const std::atomic<float>* bpm = nullptr;

    float bpmNow() const { return bpm ? bpm->load() : 120.f; }
};

using Chain = std::vector<std::unique_ptr<Fx>>;

/** Fresh rack in signal order; `bpm` feeds the tempo-synced effects. */
std::shared_ptr<Chain> makeRack(const std::atomic<float>* bpm);

} // namespace padlab
