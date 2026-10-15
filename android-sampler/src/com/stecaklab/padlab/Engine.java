package com.stecaklab.padlab;

import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Sampler + 16-step sequencer + FX rack. Pure Java (no android.*) so it can be tested on a JVM.
 * UI threads only enqueue events; everything else happens inside {@link #render(short[])}.
 */
final class Engine {
    static final int SR = 44100, BLOCK = 256, PADS = 64, STEPS = 16, VOICES = 32;

    final Pad[] pads = new Pad[PADS];
    final boolean[][] steps = new boolean[PADS][STEPS];
    final Fx[] fx = Fx.rack(this);

    volatile float bpm = 90f, swing = 0.3f, master = 0.8f;
    volatile boolean playing, overdub;
    volatile int curStep = -1;
    final long[] lastHit = new long[PADS];

    private static final class Ev {
        final int type, pad;
        final float vel;
        Ev(int type, int pad, float vel) { this.type = type; this.pad = pad; this.vel = vel; }
    }
    private static final int EV_ON = 0, EV_OFF = 1, EV_PLAY = 2, EV_STOP = 3, EV_KILL = 4;

    private final ConcurrentLinkedQueue<Ev> queue = new ConcurrentLinkedQueue<Ev>();
    private final Voice[] voices = new Voice[VOICES];
    private final float[] bl = new float[BLOCK], br = new float[BLOCK];
    private double seqCounter;
    private int seqStep = -1;
    private boolean seqRunning;
    private long voiceClock;

    Engine() {
        for (int i = 0; i < PADS; i++) pads[i] = new Pad();
        for (int i = 0; i < VOICES; i++) voices[i] = new Voice();
    }

    // ---- UI-thread API -------------------------------------------------------------------
    void trigger(int pad, float vel) { queue.add(new Ev(EV_ON, pad, vel)); }
    void release(int pad) { queue.add(new Ev(EV_OFF, pad, 0)); }
    void play() { playing = true; queue.add(new Ev(EV_PLAY, 0, 0)); }
    void stop() { playing = false; queue.add(new Ev(EV_STOP, 0, 0)); }
    void killAll() { queue.add(new Ev(EV_KILL, 0, 0)); }

    // ---- audio thread --------------------------------------------------------------------
    /** Renders BLOCK stereo frames, interleaved, into out (length >= BLOCK*2). */
    void render(short[] out) {
        Ev e;
        while ((e = queue.poll()) != null) handle(e);

        Arrays.fill(bl, 0f);
        Arrays.fill(br, 0f);
        int pos = 0;
        while (pos < BLOCK) {
            int seg = BLOCK - pos;
            if (seqRunning) {
                while (seqCounter <= 0) fireStep();
                seg = (int) Math.min(seg, Math.max(1, Math.ceil(seqCounter)));
            }
            for (Voice v : voices) if (v.active) v.mix(bl, br, pos, seg);
            if (seqRunning) seqCounter -= seg;
            pos += seg;
        }

        for (Fx f : fx) if (f.on) f.process(bl, br, BLOCK);

        float m = master;
        for (int i = 0; i < BLOCK; i++) {
            out[2 * i] = toShort(Fx.softClip(bl[i] * m));
            out[2 * i + 1] = toShort(Fx.softClip(br[i] * m));
        }
    }

    private static short toShort(float x) {
        int v = (int) (x * 32767f);
        return (short) (v > 32767 ? 32767 : v < -32768 ? -32768 : v);
    }

    private void handle(Ev e) {
        switch (e.type) {
            case EV_ON:
                if (overdub && seqRunning) {
                    int st = seqStep;
                    if (st < 0) st = 0;
                    else if (seqCounter < interval(seqStep) / 2) st = (st + 1) % STEPS;
                    steps[e.pad][st] = true;
                }
                startVoice(e.pad, e.vel, false);
                break;
            case EV_OFF:
                for (Voice v : voices)
                    if (v.active && v.pad == e.pad && v.gate && !v.releasing) v.release(0.01f);
                break;
            case EV_PLAY:
                seqRunning = true;
                seqStep = -1;
                seqCounter = 0;
                break;
            case EV_STOP:
                seqRunning = false;
                seqStep = -1;
                curStep = -1;
                break;
            case EV_KILL:
                for (Voice v : voices) if (v.active) v.release(0.02f);
                break;
        }
    }

    private double interval(int step) {
        double base = SR * 60.0 / bpm / 4.0;
        float sw = swing * 0.33f;
        return (step & 1) == 0 ? base * (1 + sw) : base * (1 - sw);
    }

    private void fireStep() {
        seqStep = (seqStep + 1) % STEPS;
        curStep = seqStep;
        for (int p = 0; p < PADS; p++) if (steps[p][seqStep]) startVoice(p, 0.9f, true);
        seqCounter += interval(seqStep);
    }

    private void startVoice(int padId, float vel, boolean fromSeq) {
        Pad p = pads[padId];
        Sample s = p.sample;
        if (s == null) return;
        lastHit[padId] = System.nanoTime();

        // LOOP pads latch: second tap stops them.
        if (!fromSeq && p.mode == Pad.LOOP) {
            boolean stopped = false;
            for (Voice v : voices)
                if (v.active && v.pad == padId && v.loop && !v.releasing) { v.release(0.015f); stopped = true; }
            if (stopped) return;
        }
        if (p.choke > 0) {
            for (Voice v : voices)
                if (v.active && v.pad != padId && v.choke == p.choke && !v.releasing) v.release(0.006f);
        }

        Voice free = null;
        for (Voice v : voices) if (!v.active) { free = v; break; }
        if (free == null) {
            free = voices[0];
            for (Voice v : voices) if (v.age < free.age) free = v;
        }
        int n = s.n;
        int si = Math.max(0, Math.min(n - 2, (int) (p.start * n)));
        int ei = Math.max(si + 2, Math.min(n, (int) (p.end * n)));
        boolean loop = !fromSeq && p.mode == Pad.LOOP;
        boolean gate = !fromSeq && p.mode == Pad.GATE;
        free.start(padId, s, si, ei, Math.pow(2.0, p.pitch / 12.0), p.reverse, loop, gate,
                p.vol * vel * 0.8f, p.choke, ++voiceClock);
    }

    // ---- voice ---------------------------------------------------------------------------
    private static final class Voice {
        boolean active, releasing, rev, loop, gate;
        int pad, choke, si, ei;
        Sample s;
        double pos, step;
        float gain, env, relStep;
        long age;

        void start(int pad, Sample s, int si, int ei, double rate, boolean rev, boolean loop,
                   boolean gate, float gain, int choke, long age) {
            this.pad = pad; this.s = s; this.si = si; this.ei = ei; this.rev = rev;
            this.loop = loop; this.gate = gate; this.gain = gain; this.choke = choke; this.age = age;
            step = rev ? -rate : rate;
            pos = rev ? ei - 1 : si;
            env = 0f;
            releasing = false;
            active = true;
        }

        void release(float seconds) {
            releasing = true;
            relStep = 1f / Math.max(8f, seconds * SR);
        }

        void mix(float[] l, float[] r, int from, int len) {
            final float[] sl = s.l, sr = s.r;
            for (int i = from; i < from + len; i++) {
                if (rev ? pos <= si : pos >= ei - 1) {
                    if (loop && !releasing) {
                        pos = rev ? (ei - 1) - (si - pos) : si + (pos - (ei - 1));
                        if (pos < si || pos > ei - 1) pos = rev ? ei - 1 : si;
                    } else { active = false; return; }
                }
                if (releasing) {
                    env -= relStep;
                    if (env <= 0f) { active = false; return; }
                } else if (env < 1f) {
                    env = Math.min(1f, env + 1f / 48f);
                }
                int i0 = (int) pos;
                float f = (float) (pos - i0);
                int i1 = Math.min(i0 + 1, s.n - 1);
                float g = gain * env;
                l[i] += (sl[i0] + (sl[i1] - sl[i0]) * f) * g;
                r[i] += (sr[i0] + (sr[i1] - sr[i0]) * f) * g;
                pos += step;
            }
        }
    }
}
