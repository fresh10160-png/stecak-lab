#include "Engine.h"
#include <chrono>
#include "Stretch.h"

namespace padlab {

namespace { enum { EV_ON, EV_OFF, EV_PLAY, EV_STOP, EV_KILL }; }

int64_t Engine::nowNs() {
    return std::chrono::duration_cast<std::chrono::nanoseconds>(std::chrono::steady_clock::now().time_since_epoch()).count();
}

Engine::Engine() : bl(MAXB), br(MAXB) {
    for (int i = 0; i < PADS; ++i) {
        stepBits[i] = 0;
        lastHit[i] = 0;
        busL[i].assign(MAXB, 0.f);
        busR[i].assign(MAXB, 0.f);
    }
    master = makeRack(&bpm);
    warper = std::thread([this] { warpLoop(); });
}

Engine::~Engine() {
    quit = true;
    if (warper.joinable()) warper.join();
}

// ---- event queue (single producer = UI thread, single consumer = audio thread) -------------
void Engine::push(int type, int pad, float vel) {
    const unsigned h = qHead.load(std::memory_order_relaxed);
    if (h - qTail.load(std::memory_order_acquire) >= (unsigned) QSIZE) return; // queue full: drop
    queue[h % QSIZE] = Ev{type, pad, vel};
    qHead.store(h + 1, std::memory_order_release);
}

void Engine::trigger(int pad, float vel) { push(EV_ON, pad, vel); }
void Engine::release(int pad) { push(EV_OFF, pad, 0.f); }
void Engine::play() { playing = true; push(EV_PLAY, 0, 0.f); }
void Engine::stop() { playing = false; push(EV_STOP, 0, 0.f); }
void Engine::killAll() { push(EV_KILL, 0, 0.f); }

std::shared_ptr<Chain> Engine::chainFor(int pad) {
    auto c = pads[(size_t) pad].chain.get();
    if (!c) {
        c = makeRack(&bpm);
        pads[(size_t) pad].chain.set(c);
    }
    return c;
}

// ---- mic capture ---------------------------------------------------------------------------
void Engine::startRecording() {
    if (recording) return;
    recBuf.assign((size_t) SR * 60, 0.f);
    recLen = 0;
    micPeak = 0.f;
    recording = true;
}

std::shared_ptr<Sample> Engine::stopRecording() {
    recording = false;
    const int n = recLen.load();
    if (n < 1000) return nullptr;
    int from = 0;
    while (from < n - 1 && std::abs(recBuf[(size_t) from]) < 600.f / 32768.f) ++from; // trim leading silence
    std::vector<float> d(recBuf.begin() + from, recBuf.begin() + n);
    micPeak = 0.f;
    return Sample::makeMono("REC", std::move(d));
}

// ---- time-stretch --------------------------------------------------------------------------
static uint64_t mix64(uint64_t h, uint64_t v) {
    h ^= v + 0x9e3779b97f4a7c15ULL + (h << 6) + (h >> 2);
    return h;
}

Engine::Warp Engine::warpSpec(const Pad& p, const Sample& s) const {
    Warp w;
    const int n = s.n();
    const int si = std::max(0, std::min(n - 2, (int) (p.start * n)));
    const int ei = std::max(si + 2, std::min(n, (int) (p.end * n)));
    const double q = std::pow(2.0, p.pitch / 12.0);
    double t = p.tempo;
    if (p.fitBeats > 0) t = (ei - si) / (double) SR / (p.fitBeats * 60.0 / bpm.load());
    t = std::max(0.25, std::min(4.0, t));
    const bool kl = p.keyLock || p.fitBeats > 0;
    const double f = std::max(0.25, std::min(4.0, (kl ? q : 1.0) / t));
    if (std::abs(f - 1.0) <= 0.005) return w;
    w.needed = true;
    w.si = si;
    w.ei = ei;
    w.f = f;
    uint64_t h = (uint64_t) (uintptr_t) &s;
    h = mix64(h, (uint64_t) si);
    h = mix64(h, (uint64_t) ei);
    h = mix64(h, (uint64_t) std::llround(f * 1000));
    h = mix64(h, (uint64_t) SR);
    w.key = h | 1;
    return w;
}

void Engine::requestWarp(int pad) { pads[(size_t) pad].warpWanted = true; }

void Engine::rewarpAll() {
    for (int i = 0; i < PADS; ++i) if (pads[(size_t) i].sample.get() && pads[(size_t) i].warped()) requestWarp(i);
}

void Engine::warpLoop() {
    while (!quit) {
        bool did = false;
        for (int i = 0; i < PADS && !quit; ++i) {
            Pad& p = pads[(size_t) i];
            if (!p.warpWanted.exchange(false)) continue;
            did = true;
            auto s = p.sample.get();
            if (!s) continue;
            const Warp w = warpSpec(p, *s);
            if (!w.needed) { p.rendered.set(nullptr); p.renderedKey = 0; p.pendingKey = 0; continue; }
            if (w.key == p.renderedKey.load() || w.key == p.pendingKey.load()) continue;
            p.pendingKey = w.key;
            ++warpBusy;
            auto r = wsola(*s, w.si, w.ei, w.f, [&p, &w, this] { return quit.load() || p.pendingKey.load() != w.key; });
            if (r && p.pendingKey.load() == w.key) {
                p.rendered.set(r);
                p.renderedKey = w.key;
            }
            --warpBusy;
        }
        if (!did) std::this_thread::sleep_for(std::chrono::milliseconds(15));
    }
}

// ---- audio thread --------------------------------------------------------------------------
void Engine::render(float* outL, float* outR, int frames, const float* in, int inFrames) {
    frames = std::min(frames, MAXB);

    if (recording.load(std::memory_order_relaxed) && in) {
        int len = recLen.load(std::memory_order_relaxed);
        const int cap = (int) recBuf.size();
        float pk = 0.f;
        for (int i = 0; i < std::min(frames, inFrames) && len < cap; ++i, ++len) {
            recBuf[(size_t) len] = in[i];
            pk = std::max(pk, std::abs(in[i]));
        }
        recLen.store(len, std::memory_order_release);
        micPeak = std::max(pk, micPeak.load() * 0.85f);
    }

    const unsigned head = qHead.load(std::memory_order_acquire);
    unsigned tail = qTail.load(std::memory_order_relaxed);
    while (tail != head) { handle(queue[tail % QSIZE]); ++tail; }
    qTail.store(tail, std::memory_order_release);

    std::fill(bl.begin(), bl.begin() + frames, 0.f);
    std::fill(br.begin(), br.begin() + frames, 0.f);
    for (int i = 0; i < PADS; ++i) {
        Pad& p = pads[(size_t) i];
        p.fxActive = p.hasFx();
        if (p.fxActive) {
            std::fill(busL[i].begin(), busL[i].begin() + frames, 0.f);
            std::fill(busR[i].begin(), busR[i].begin() + frames, 0.f);
        }
    }

    int pos = 0;
    while (pos < frames) {
        int seg = frames - pos;
        if (seqRunning) {
            while (seqCounter <= 0) fireStep();
            seg = (int) std::min<double>(seg, std::max(1.0, std::ceil(seqCounter)));
        }
        for (auto& v : voices) {
            if (!v.active) continue;
            if (pads[(size_t) v.pad].fxActive) v.mix(busL[(size_t) v.pad].data(), busR[(size_t) v.pad].data(), pos, seg);
            else v.mix(bl.data(), br.data(), pos, seg);
        }
        if (seqRunning) seqCounter -= seg;
        pos += seg;
    }

    for (int i = 0; i < PADS; ++i) {
        Pad& p = pads[(size_t) i];
        if (!p.fxActive) continue;
        auto chain = p.chain.get();
        if (chain) for (auto& f : *chain) if (f->on) f->process(busL[i].data(), busR[i].data(), frames);
        for (int k = 0; k < frames; ++k) { bl[(size_t) k] += busL[i][(size_t) k]; br[(size_t) k] += busR[i][(size_t) k]; }
    }

    for (auto& f : *master) if (f->on) f->process(bl.data(), br.data(), frames);

    const float m = masterVol;
    float pl = 0, pr = 0;
    for (int i = 0; i < frames; ++i) {
        const float a = softClip(bl[(size_t) i] * m), b = softClip(br[(size_t) i] * m);
        pl = std::max(pl, std::abs(a));
        pr = std::max(pr, std::abs(b));
        outL[i] = a;
        outR[i] = b;
    }
    const float decay = std::pow(0.5f, frames / ((float) SR * 0.25f));
    vuL = std::max(pl, vuL.load() * decay);
    vuR = std::max(pr, vuR.load() * decay);
}

void Engine::handle(const Ev& e) {
    switch (e.type) {
        case EV_ON:
            if (overdub && seqRunning) {
                int st = seqStep;
                if (st < 0) st = 0;
                else if (seqCounter < interval(seqStep) / 2) st = (st + 1) % STEPS;
                stepBits[e.pad].fetch_or((uint16_t) (1u << st));
            }
            startVoice(e.pad, e.vel, false);
            break;
        case EV_OFF:
            for (auto& v : voices) if (v.active && v.pad == e.pad && v.gate && !v.releasing) v.release(0.01f);
            break;
        case EV_PLAY: seqRunning = true; seqStep = -1; seqCounter = 0; break;
        case EV_STOP: seqRunning = false; seqStep = -1; curStep = -1; break;
        case EV_KILL: for (auto& v : voices) if (v.active) v.release(0.02f); break;
    }
}

double Engine::interval(int step) const {
    const double base = SR * 60.0 / bpm.load() / 4.0;
    const float sw = swing.load() * 0.33f;
    return (step & 1) == 0 ? base * (1 + sw) : base * (1 - sw);
}

void Engine::fireStep() {
    seqStep = (seqStep + 1) % STEPS;
    curStep = seqStep;
    for (int p = 0; p < PADS; ++p) if (stepBits[p].load() & (1u << seqStep)) startVoice(p, 0.9f, true);
    seqCounter += interval(seqStep);
}

void Engine::startVoice(int padId, float vel, bool fromSeq) {
    Pad& p = pads[(size_t) padId];
    auto s = p.sample.get();
    if (!s) return;
    lastHit[padId] = nowNs();
    const int mode = p.mode;

    if (!fromSeq && mode == Pad::LOOP) { // LOOP pads latch: second tap stops them
        bool stopped = false;
        for (auto& v : voices) if (v.active && v.pad == padId && v.loop && !v.releasing) { v.release(0.015f); stopped = true; }
        if (stopped) return;
    }
    const int choke = p.choke;
    if (choke > 0) for (auto& v : voices) if (v.active && v.pad != padId && v.choke == choke && !v.releasing) v.release(0.006f);

    Voice* freeV = nullptr;
    for (auto& v : voices) if (!v.active) { freeV = &v; break; }
    if (!freeV) {
        freeV = &voices[0];
        for (auto& v : voices) if (v.age < freeV->age) freeV = &v;
    }

    const int n = s->n();
    int si = std::max(0, std::min(n - 2, (int) (p.start * n)));
    int ei = std::max(si + 2, std::min(n, (int) (p.end * n)));
    std::shared_ptr<const Sample> use = s;
    if (p.warped()) {
        const Warp w = warpSpec(p, *s);
        if (w.needed) {
            auto r = p.rendered.get();
            if (r && w.key == p.renderedKey.load()) { use = r; si = 0; ei = r->n(); }
            else p.warpWanted = true;
        }
    }
    const float pan = p.pan, gl = pan > 0 ? 1.f - pan : 1.f, gr = pan < 0 ? 1.f + pan : 1.f;
    const float g = p.vol * vel * 0.8f;
    Voice& v = *freeV;
    v.pad = padId; v.s = use; v.si = si; v.ei = ei; v.rev = p.reverse;
    v.loop = !fromSeq && mode == Pad::LOOP;
    v.gate = !fromSeq && mode == Pad::GATE;
    v.gainL = g * gl; v.gainR = g * gr; v.choke = choke; v.age = ++voiceClock;
    const double rate = std::pow(2.0, p.pitch / 12.0);
    v.step = v.rev ? -rate : rate;
    v.pos = v.rev ? ei - 1 : si;
    v.env = 0.f;
    v.releasing = false;
    v.active = true;
}

void Engine::Voice::mix(float* l, float* r, int from, int len) {
    const float* sl = s->L();
    const float* sr = s->R();
    const int n = s->n();
    for (int i = from; i < from + len; ++i) {
        if (rev ? pos <= si : pos >= ei - 1) {
            if (loop && !releasing) {
                pos = rev ? (ei - 1) - (si - pos) : si + (pos - (ei - 1));
                if (pos < si || pos > ei - 1) pos = rev ? ei - 1 : si;
            } else { active = false; s.reset(); return; }
        }
        if (releasing) {
            env -= relStep;
            if (env <= 0.f) { active = false; s.reset(); return; }
        } else if (env < 1.f) {
            env = std::min(1.f, env + 1.f / 48.f);
        }
        const int i0 = (int) pos;
        const float f = (float) (pos - i0);
        const int i1 = std::min(i0 + 1, n - 1);
        l[i] += (sl[i0] + (sl[i1] - sl[i0]) * f) * gainL * env;
        r[i] += (sr[i0] + (sr[i1] - sr[i0]) * f) * gainR * env;
        pos += step;
    }
}

} // namespace padlab
