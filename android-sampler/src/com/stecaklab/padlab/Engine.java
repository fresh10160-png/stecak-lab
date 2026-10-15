package com.stecaklab.padlab;

import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sampler + 16-step sequencer + per-pad and master FX racks. Pure Java (no android.*) so it can be
 * tested on a JVM. UI threads only enqueue events; everything else happens in {@link #render}.
 */
final class Engine {
    static int SR = 44100;
    static final int MAXB = 2048, PADS = 64, STEPS = 16, VOICES = 32;

    static void setRate(int rate) {
        SR = rate;
        Fx.SR = rate;
    }

    final Pad[] pads = new Pad[PADS];
    final boolean[][] steps = new boolean[PADS][STEPS];
    final Fx[] fx = Fx.rack(this);

    volatile float bpm = 90f, swing = 0.3f, master = 0.8f;
    volatile boolean playing, overdub;
    volatile int curStep = -1;
    volatile float vuL, vuR;
    final long[] lastHit = new long[PADS];
    final AtomicInteger warpBusy = new AtomicInteger();

    private static final class Ev {
        final int type, pad;
        final float vel;
        Ev(int type, int pad, float vel) { this.type = type; this.pad = pad; this.vel = vel; }
    }
    private static final int EV_ON = 0, EV_OFF = 1, EV_PLAY = 2, EV_STOP = 3, EV_KILL = 4;

    private final ConcurrentLinkedQueue<Ev> queue = new ConcurrentLinkedQueue<Ev>();
    private final Voice[] voices = new Voice[VOICES];
    private final float[] bl = new float[MAXB], br = new float[MAXB];
    private final float[][] busL = new float[PADS][], busR = new float[PADS][];
    private double seqCounter;
    private int seqStep = -1;
    private boolean seqRunning;
    private long voiceClock;
    private final ExecutorService warper = Executors.newSingleThreadExecutor(new ThreadFactory() {
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "padlab-warp");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY + 1);
            return t;
        }
    });

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

    /** The pad's FX chain, created (and its buffers allocated lazily per effect) on first request. */
    Fx[] chainFor(int pad) {
        Fx[] c = pads[pad].chain;
        if (c == null) {
            c = Fx.rack(this);
            pads[pad].chain = c;
        }
        return c;
    }

    void rewarpAll() { for (int i = 0; i < PADS; i++) if (pads[i].sample != null && pads[i].warped()) requestWarp(i); }

    // ---- time-stretch --------------------------------------------------------------------
    private static final class Warp {
        int si, ei;
        double f;
        String key;
    }

    private Warp warpSpec(Pad p, Sample s) {
        int n = s.n;
        int si = Math.max(0, Math.min(n - 2, (int) (p.start * n)));
        int ei = Math.max(si + 2, Math.min(n, (int) (p.end * n)));
        double q = Math.pow(2.0, p.pitch / 12.0);
        double t = p.tempo;
        if (p.fitBeats > 0) t = (ei - si) / (double) SR / (p.fitBeats * 60.0 / bpm);
        t = Math.max(0.25, Math.min(4.0, t));
        boolean kl = p.keyLock || p.fitBeats > 0;
        double f = Math.max(0.25, Math.min(4.0, (kl ? q : 1.0) / t));
        if (Math.abs(f - 1.0) <= 0.005) return null;
        Warp w = new Warp();
        w.si = si;
        w.ei = ei;
        w.f = f;
        w.key = System.identityHashCode(s) + ":" + si + ":" + ei + ":" + Math.round(f * 1000) + ":" + SR;
        return w;
    }

    /** Re-renders the pad's stretched copy in the background if its settings need one. */
    void requestWarp(final int padId) {
        final Pad p = pads[padId];
        final Sample s = p.sample;
        if (s == null) return;
        final Warp w = warpSpec(p, s);
        if (w == null) { p.rendered = null; p.renderedKey = null; p.pendingKey = null; return; }
        if (w.key.equals(p.renderedKey) || w.key.equals(p.pendingKey)) return;
        p.pendingKey = w.key;
        warpBusy.incrementAndGet();
        warper.submit(new Runnable() {
            public void run() {
                try {
                    Sample r = Stretch.wsola(s, w.si, w.ei, w.f, new Stretch.Cancel() {
                        public boolean cancelled() { return !w.key.equals(p.pendingKey); }
                    });
                    if (r != null && w.key.equals(p.pendingKey)) {
                        p.rendered = r;
                        p.renderedKey = w.key;
                    }
                } finally {
                    warpBusy.decrementAndGet();
                }
            }
        });
    }

    // ---- audio thread --------------------------------------------------------------------
    /** Renders `frames` stereo frames, interleaved, into out (length >= frames*2). */
    void render(short[] out, int frames) {
        Ev e;
        while ((e = queue.poll()) != null) handle(e);

        Arrays.fill(bl, 0, frames, 0f);
        Arrays.fill(br, 0, frames, 0f);
        for (int i = 0; i < PADS; i++) {
            Pad p = pads[i];
            p.fxActive = p.hasFx();
            if (p.fxActive) {
                if (busL[i] == null) { busL[i] = new float[MAXB]; busR[i] = new float[MAXB]; }
                Arrays.fill(busL[i], 0, frames, 0f);
                Arrays.fill(busR[i], 0, frames, 0f);
            }
        }

        int pos = 0;
        while (pos < frames) {
            int seg = frames - pos;
            if (seqRunning) {
                while (seqCounter <= 0) fireStep();
                seg = (int) Math.min(seg, Math.max(1, Math.ceil(seqCounter)));
            }
            for (Voice v : voices) {
                if (!v.active) continue;
                if (pads[v.pad].fxActive) v.mix(busL[v.pad], busR[v.pad], pos, seg);
                else v.mix(bl, br, pos, seg);
            }
            if (seqRunning) seqCounter -= seg;
            pos += seg;
        }

        for (int i = 0; i < PADS; i++) {
            Pad p = pads[i];
            if (!p.fxActive) continue;
            for (Fx f : p.chain) if (f.on) f.process(busL[i], busR[i], frames);
            float[] a = busL[i], b = busR[i];
            for (int k = 0; k < frames; k++) { bl[k] += a[k]; br[k] += b[k]; }
        }

        for (Fx f : fx) if (f.on) f.process(bl, br, frames);

        float m = master, pl = 0, pr = 0;
        for (int i = 0; i < frames; i++) {
            float a = Fx.softClip(bl[i] * m), b = Fx.softClip(br[i] * m);
            pl = Math.max(pl, Math.abs(a));
            pr = Math.max(pr, Math.abs(b));
            out[2 * i] = toShort(a);
            out[2 * i + 1] = toShort(b);
        }
        float decay = (float) Math.pow(0.5, frames / (SR * 0.25));
        vuL = Math.max(pl, vuL * decay);
        vuR = Math.max(pr, vuR * decay);
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
        Sample use = s;
        if (p.warped()) {
            Warp w = warpSpec(p, s);
            if (w != null) {
                Sample r = p.rendered;
                if (r != null && w.key.equals(p.renderedKey)) { use = r; si = 0; ei = r.n; }
                else requestWarp(padId);
            }
        }
        float pan = p.pan, gl = pan > 0 ? 1f - pan : 1f, gr = pan < 0 ? 1f + pan : 1f;
        boolean loop = !fromSeq && p.mode == Pad.LOOP;
        boolean gate = !fromSeq && p.mode == Pad.GATE;
        float g = p.vol * vel * 0.8f;
        free.start(padId, use, si, ei, Math.pow(2.0, p.pitch / 12.0), p.reverse, loop, gate,
                g * gl, g * gr, p.choke, ++voiceClock);
    }

    // ---- voice ---------------------------------------------------------------------------
    private static final class Voice {
        boolean active, releasing, rev, loop, gate;
        int pad, choke, si, ei;
        Sample s;
        double pos, step;
        float gainL, gainR, env, relStep;
        long age;

        void start(int pad, Sample s, int si, int ei, double rate, boolean rev, boolean loop,
                   boolean gate, float gl, float gr, int choke, long age) {
            this.pad = pad; this.s = s; this.si = si; this.ei = ei; this.rev = rev;
            this.loop = loop; this.gate = gate; this.gainL = gl; this.gainR = gr; this.choke = choke; this.age = age;
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
                l[i] += (sl[i0] + (sl[i1] - sl[i0]) * f) * gainL * env;
                r[i] += (sr[i0] + (sr[i1] - sr[i0]) * f) * gainR * env;
                pos += step;
            }
        }
    }
}
