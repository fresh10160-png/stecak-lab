package com.stecaklab.padlab;

import java.util.Random;

/** Master-bus effect rack inspired by the Roland SP-303 / SP-202 (not exact emulations). */
abstract class Fx {
    static float SR = Engine.SR;
    static final float TWO_PI = (float) (2 * Math.PI);

    final String name;
    final String[] labels;
    final float[] p, def;
    volatile boolean on;
    Engine eng;

    Fx(String name, String[] labels, float... def) {
        this.name = name;
        this.labels = labels;
        this.def = def;
        this.p = def.clone();
    }

    abstract void process(float[] l, float[] r, int n);

    /** Allocate delay lines etc. Called off the audio thread before the effect is switched on. */
    void alloc() { }

    final void enable(boolean b) {
        if (b) alloc();
        on = b;
    }

    String text(int i) { return Math.round(p[i] * 100) + "%"; }

    void reset() { System.arraycopy(def, 0, p, 0, p.length); }

    // ---- shared helpers ------------------------------------------------------------------
    static float tanh(float x) {
        if (x > 3f) return 1f;
        if (x < -3f) return -1f;
        float x2 = x * x;
        return x * (27f + x2) / (27f + 9f * x2);
    }

    static float softClip(float x) {
        if (x > 0.9f) return 0.9f + 0.1f * tanh((x - 0.9f) * 10f);
        if (x < -0.9f) return -0.9f + 0.1f * tanh((x + 0.9f) * 10f);
        return x;
    }

    static float onePole(float fc) { return 1f - (float) Math.exp(-TWO_PI * fc / SR); }

    /** Order = signal order in the rack. */
    static Fx[] rack(Engine e) {
        Fx[] a = {new Isolator(), new FilterDrive(), new LoFi(), new RingMod(), new Resonator(),
                new Flanger(), new Slicer(), new SyncDelay(), new Reverb(), new Compressor(), new Vinyl()};
        for (Fx f : a) f.eng = e;
        return a;
    }

    static final int ISOLATOR = 0, FILTER = 1, LOFI = 2, RING = 3, RESO = 4, FLANGER = 5, SLICER = 6,
            DELAY = 7, REVERB = 8, COMP = 9, VINYL = 10;

    // ---- SP-303 ISOLATOR: 3-band kill ----------------------------------------------------
    static final class Isolator extends Fx {
        private final float[][] s = new float[2][4];
        private final float[] g = {1, 1, 1};

        Isolator() { super("ISOLATOR", new String[]{"Low", "Mid", "High"}, 1f, 1f, 1f); }

        void process(float[] l, float[] r, int n) {
            float k1 = onePole(250f), k2 = onePole(2800f);
            float[] t = {p[0] * p[0], p[1] * p[1], p[2] * p[2]};
            for (int i = 0; i < n; i++) {
                for (int b = 0; b < 3; b++) g[b] += (t[b] - g[b]) * 0.002f;
                for (int ch = 0; ch < 2; ch++) {
                    float x = ch == 0 ? l[i] : r[i];
                    float[] z = s[ch];
                    z[0] += k1 * (x - z[0]);
                    z[1] += k1 * (z[0] - z[1]);
                    z[2] += k2 * (x - z[2]);
                    z[3] += k2 * (z[2] - z[3]);
                    float low = z[1], midb = z[3] - z[1], high = x - z[3];
                    float y = low * g[0] + midb * g[1] + high * g[2];
                    if (ch == 0) l[i] = y; else r[i] = y;
                }
            }
        }
    }

    // ---- FILTER + DRIVE: resonant SVF low-pass into a soft clipper -----------------------
    static final class FilterDrive extends Fx {
        private final float[][] ic = new float[2][2];
        private float fcS = 16000f;

        FilterDrive() { super("FILTER+DRIVE", new String[]{"Cutoff", "Reso", "Drive"}, 1f, 0.3f, 0.2f); }

        String text(int i) {
            if (i == 0) return Math.round(40f * (float) Math.pow(400.0, p[0])) + " Hz";
            return super.text(i);
        }

        void process(float[] l, float[] r, int n) {
            float target = 40f * (float) Math.pow(400.0, p[0]);
            fcS += (target - fcS) * 0.35f;
            float g = (float) Math.tan(Math.PI * Math.min(fcS, 18000f) / SR);
            float k = 2f - 2f * Math.min(0.97f, p[1]);
            float a1 = 1f / (1f + g * (g + k)), a2 = g * a1, a3 = g * a2;
            float pre = 1f + p[2] * 10f, post = 1f / (1f + p[2] * 3.5f);
            for (int i = 0; i < n; i++) {
                for (int ch = 0; ch < 2; ch++) {
                    float x = tanh((ch == 0 ? l[i] : r[i]) * pre);
                    float[] z = ic[ch];
                    float v3 = x - z[1];
                    float v1 = a1 * z[0] + a2 * v3;
                    float v2 = z[1] + a2 * z[0] + a3 * v3;
                    z[0] = 2f * v1 - z[0];
                    z[1] = 2f * v2 - z[1];
                    float y = v2 * post;
                    if (ch == 0) l[i] = y; else r[i] = y;
                }
            }
        }
    }

    // ---- LO-FI: sample-rate + bit-depth reduction ----------------------------------------
    static final class LoFi extends Fx {
        private final float[] acc = new float[2], hold = new float[2], lp = new float[2];

        LoFi() { super("LO-FI", new String[]{"Rate", "Bits", "Tone"}, 0.6f, 0.6f, 0.8f); }

        String text(int i) {
            if (i == 0) return Math.round(rate()) + " Hz";
            if (i == 1) return String.format("%.1f bit", bits());
            return super.text(i);
        }

        private float rate() { return 1500f * (float) Math.pow(29.4, p[0]); }
        private float bits() { return 3f + 13f * p[1]; }

        void process(float[] l, float[] r, int n) {
            float step = Math.min(1f, rate() / SR);
            float lv = (float) Math.pow(2.0, bits() - 1f);
            float k = onePole(1500f * (float) Math.pow(12.0, p[2]));
            for (int i = 0; i < n; i++) {
                for (int ch = 0; ch < 2; ch++) {
                    acc[ch] += step;
                    if (acc[ch] >= 1f) {
                        acc[ch] -= 1f;
                        hold[ch] = Math.round((ch == 0 ? l[i] : r[i]) * lv) / lv;
                    }
                    lp[ch] += k * (hold[ch] - lp[ch]);
                    if (ch == 0) l[i] = lp[ch]; else r[i] = lp[ch];
                }
            }
        }
    }

    // ---- RING MOD ------------------------------------------------------------------------
    static final class RingMod extends Fx {
        private float ph;

        RingMod() { super("RING MOD", new String[]{"Freq", "Mix"}, 0.4f, 0.5f); }

        String text(int i) { return i == 0 ? Math.round(freq()) + " Hz" : super.text(i); }
        private float freq() { return 30f * (float) Math.pow(150.0, p[0]); }

        void process(float[] l, float[] r, int n) {
            float inc = TWO_PI * freq() / SR, mix = p[1];
            for (int i = 0; i < n; i++) {
                float c = (float) Math.sin(ph);
                ph += inc;
                if (ph > TWO_PI) ph -= TWO_PI;
                l[i] = l[i] * (1 - mix) + l[i] * c * mix;
                r[i] = r[i] * (1 - mix) + r[i] * c * mix;
            }
        }
    }

    // ---- RESONATOR: tuned feedback comb --------------------------------------------------
    static final class Resonator extends Fx {
        private float[][] buf;
        private final float[] damp = new float[2];
        private int w;
        private float dS = 200f;

        Resonator() { super("RESONATOR", new String[]{"Pitch", "Feedback", "Mix"}, 0.4f, 0.8f, 0.5f); }
        void alloc() { if (buf == null) buf = new float[2][4096]; }

        String text(int i) { return i == 0 ? Math.round(freq()) + " Hz" : super.text(i); }
        private float freq() { return 55f * (float) Math.pow(2.0, p[0] * 5f); }

        void process(float[] l, float[] r, int n) {
            float target = SR / freq();
            float fb = p[1] * 0.96f, mix = p[2], wetGain = 1f - 0.6f * fb;
            for (int i = 0; i < n; i++) {
                dS += (target - dS) * 0.002f;
                float rp = w - dS;
                if (rp < 0) rp += 4096;
                int i0 = (int) rp & 4095, i1 = (i0 + 1) & 4095;
                float fr = rp - (int) rp;
                for (int ch = 0; ch < 2; ch++) {
                    float x = ch == 0 ? l[i] : r[i];
                    float[] b = buf[ch];
                    float d = b[i0] + (b[i1] - b[i0]) * fr;
                    damp[ch] += 0.55f * (d - damp[ch]);
                    float y = x + fb * damp[ch];
                    b[w] = tanh(y);
                    float o = x * (1 - mix) + y * wetGain * mix;
                    if (ch == 0) l[i] = o; else r[i] = o;
                }
                w = (w + 1) & 4095;
            }
        }
    }

    // ---- FLANGER (SP-202 style) ----------------------------------------------------------
    static final class Flanger extends Fx {
        private float[][] buf;
        private int w;
        private float ph;

        Flanger() { super("FLANGER", new String[]{"Rate", "Depth", "Feedback"}, 0.3f, 0.6f, 0.5f); }
        void alloc() { if (buf == null) buf = new float[2][1024]; }

        String text(int i) { return i == 0 ? String.format("%.2f Hz", rate()) : super.text(i); }
        private float rate() { return 0.05f * (float) Math.pow(100.0, p[0]); }

        void process(float[] l, float[] r, int n) {
            float inc = TWO_PI * rate() / SR, fb = p[2] * 0.85f, depth = p[1] * 300f;
            for (int i = 0; i < n; i++) {
                for (int ch = 0; ch < 2; ch++) {
                    float lfo = 0.5f + 0.5f * (float) Math.sin(ph + ch * 1.2f);
                    float d = 24f + depth * lfo;
                    float rp = w - d;
                    if (rp < 0) rp += 1024;
                    int i0 = (int) rp & 1023, i1 = (i0 + 1) & 1023;
                    float fr = rp - (int) rp;
                    float[] b = buf[ch];
                    float wet = b[i0] + (b[i1] - b[i0]) * fr;
                    float x = ch == 0 ? l[i] : r[i];
                    b[w] = tanh(x + wet * fb);
                    float o = 0.7f * x + 0.7f * wet;
                    if (ch == 0) l[i] = o; else r[i] = o;
                }
                w = (w + 1) & 1023;
                ph += inc;
                if (ph > TWO_PI) ph -= TWO_PI;
            }
        }
    }

    // ---- SLICER: tempo-synced gate -------------------------------------------------------
    static final class Slicer extends Fx {
        static final String[] DIV = {"1/4", "1/8", "1/16", "1/32"};
        static final float[] BEATS = {1f, 0.5f, 0.25f, 0.125f};
        private double phase;
        private float gS = 1f;

        Slicer() { super("SLICER", new String[]{"Rate", "Depth", "Duty"}, 0.5f, 0.9f, 0.5f); }

        private int idx() { return Math.min(3, (int) (p[0] * 4f)); }
        String text(int i) { return i == 0 ? DIV[idx()] : super.text(i); }

        void process(float[] l, float[] r, int n) {
            double period = SR * 60.0 / eng.bpm * BEATS[idx()];
            float low = 1f - p[1], duty = 0.2f + 0.6f * p[2];
            for (int i = 0; i < n; i++) {
                float tgt = phase / period < duty ? 1f : low;
                gS += (tgt - gS) * 0.02f;
                l[i] *= gS;
                r[i] *= gS;
                phase += 1.0;
                if (phase >= period) phase -= period;
            }
        }
    }

    // ---- SYNC DELAY with tape-style darkening feedback -----------------------------------
    static final class SyncDelay extends Fx {
        static final String[] DIV = {"1/4", "d1/8", "1/8", "1/8T", "1/16"};
        static final float[] BEATS = {1f, 0.75f, 0.5f, 1f / 3f, 0.25f};
        private static final int SIZE = 131072, MASK = SIZE - 1;
        private float[][] buf;
        private final float[] lp = new float[2];
        private int w;
        private double dS = 20000;

        SyncDelay() { super("SYNC DELAY", new String[]{"Time", "Feedback", "Mix"}, 0.5f, 0.5f, 0.35f); }
        void alloc() { if (buf == null) buf = new float[2][SIZE]; }

        private int idx() { return Math.min(4, (int) (p[0] * 5f)); }
        String text(int i) { return i == 0 ? DIV[idx()] : super.text(i); }

        void process(float[] l, float[] r, int n) {
            double target = Math.min(SIZE - 8, SR * 60.0 / eng.bpm * BEATS[idx()]);
            float fb = p[1] * 0.92f, mix = p[2], k = onePole(3500f);
            for (int i = 0; i < n; i++) {
                dS += (target - dS) * 0.0004;
                double rp = w - dS;
                if (rp < 0) rp += SIZE;
                int i0 = (int) rp & MASK, i1 = (i0 + 1) & MASK;
                float fr = (float) (rp - Math.floor(rp));
                for (int ch = 0; ch < 2; ch++) {
                    float[] b = buf[ch];
                    float wet = b[i0] + (b[i1] - b[i0]) * fr;
                    lp[ch] += k * (wet - lp[ch]);
                    float x = ch == 0 ? l[i] : r[i];
                    b[w] = x + tanh(lp[ch] * fb);
                    float o = x + wet * mix;
                    if (ch == 0) l[i] = o; else r[i] = o;
                }
                w = (w + 1) & MASK;
            }
        }
    }

    // ---- REVERB (Freeverb topology) ------------------------------------------------------
    static final class Reverb extends Fx {
        private static final int[] CT = {1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617};
        private static final int[] AT = {556, 441, 341, 225};
        private final float[][][] cb = new float[2][8][];
        private final int[][] ci = new int[2][8];
        private final float[][] cs = new float[2][8];
        private final float[][][] ab = new float[2][4][];
        private final int[][] ai = new int[2][4];

        Reverb() {
            super("REVERB", new String[]{"Size", "Damp", "Mix"}, 0.6f, 0.4f, 0.25f);
        }

        void alloc() {
            if (cb[0][0] != null) return;
            for (int ch = 0; ch < 2; ch++) {
                for (int c = 0; c < 8; c++) cb[ch][c] = new float[CT[c] + ch * 23];
                for (int a = 0; a < 4; a++) ab[ch][a] = new float[AT[a] + ch * 23];
            }
        }

        void process(float[] l, float[] r, int n) {
            float fb = 0.7f + 0.28f * p[0], damp = 0.1f + 0.7f * p[1], mix = p[2];
            for (int i = 0; i < n; i++) {
                float in = (l[i] + r[i]) * 0.015f;
                for (int ch = 0; ch < 2; ch++) {
                    float sum = 0;
                    for (int c = 0; c < 8; c++) {
                        float[] b = cb[ch][c];
                        int ix = ci[ch][c];
                        float o = b[ix];
                        cs[ch][c] = o * (1 - damp) + cs[ch][c] * damp;
                        b[ix] = in + cs[ch][c] * fb;
                        ci[ch][c] = ++ix >= b.length ? 0 : ix;
                        sum += o;
                    }
                    for (int a = 0; a < 4; a++) {
                        float[] b = ab[ch][a];
                        int ix = ai[ch][a];
                        float bo = b[ix];
                        float o = -sum + bo;
                        b[ix] = sum + bo * 0.5f;
                        ai[ch][a] = ++ix >= b.length ? 0 : ix;
                        sum = o;
                    }
                    float x = ch == 0 ? l[i] : r[i];
                    float o = x * (1 - mix * 0.5f) + sum * 3f * mix;
                    if (ch == 0) l[i] = o; else r[i] = o;
                }
            }
        }
    }

    // ---- COMPRESSOR (SP-303 "Sustain / Attack / Level") ----------------------------------
    static final class Compressor extends Fx {
        private float env;

        Compressor() { super("COMPRESSOR", new String[]{"Sustain", "Attack", "Level"}, 0.5f, 0.3f, 0.5f); }

        void process(float[] l, float[] r, int n) {
            float thr = -6f - 34f * p[0], ratio = 2f + 10f * p[0];
            float atk = (float) Math.exp(-1.0 / (SR * (0.001 + 0.04 * p[1])));
            float rel = (float) Math.exp(-1.0 / (SR * 0.18));
            float makeup = 0.3f * (-thr) * (1f - 1f / ratio) + (p[2] - 0.5f) * 24f;
            for (int i = 0; i < n; i++) {
                float det = Math.max(Math.abs(l[i]), Math.abs(r[i]));
                env = det > env ? atk * env + (1 - atk) * det : rel * env + (1 - rel) * det;
                float db = 20f * (float) Math.log10(Math.max(env, 1e-6f));
                float over = db - thr;
                float gr = over > 0 ? over * (1f - 1f / ratio) : 0f;
                float g = (float) Math.pow(10.0, (makeup - gr) / 20.0);
                l[i] *= g;
                r[i] *= g;
            }
        }
    }

    // ---- VINYL SIM (SP-303 signature): wow/flutter, crackle, hiss, band-limit ------------
    static final class Vinyl extends Fx {
        private float[][] buf;
        private final float[] lp = new float[2], hp = new float[2], crk = new float[2];
        private final Random rnd = new Random(7);
        private int w;
        private double t;

        Vinyl() { super("VINYL SIM", new String[]{"Wow/Flutter", "Crackle", "Tone"}, 0.3f, 0.4f, 0.7f); }
        void alloc() { if (buf == null) buf = new float[2][2048]; }

        void process(float[] l, float[] r, int n) {
            float wow = p[0];
            float k = onePole(2500f * (float) Math.pow(7.0, p[2]));
            float crackP = p[1] * p[1] * 0.0004f, hiss = p[1] * 0.0025f;
            for (int i = 0; i < n; i++) {
                double mod = Math.sin(2 * Math.PI * 0.55 * t) * 10 + Math.sin(2 * Math.PI * 6.3 * t) * 2.5
                        + Math.sin(2 * Math.PI * 0.13 * t) * 6;
                t += 1.0 / SR;
                float rp = (float) (w - (600 + mod * wow));
                if (rp < 0) rp += 2048;
                int i0 = (int) rp & 2047, i1 = (i0 + 1) & 2047;
                float fr = rp - (int) rp;
                for (int ch = 0; ch < 2; ch++) {
                    float[] b = buf[ch];
                    b[w] = ch == 0 ? l[i] : r[i];
                    float y = b[i0] + (b[i1] - b[i0]) * fr;
                    if (rnd.nextFloat() < crackP) crk[ch] = (rnd.nextFloat() * 2f - 1f) * (0.1f + 0.5f * rnd.nextFloat());
                    y += crk[ch] + (rnd.nextFloat() - 0.5f) * 2f * hiss;
                    crk[ch] *= 0.8f;
                    y = tanh(y * 1.3f) * 0.85f;
                    lp[ch] += k * (y - lp[ch]);
                    hp[ch] += 0.004f * (lp[ch] - hp[ch]);
                    float o = lp[ch] - hp[ch];
                    if (ch == 0) l[i] = o; else r[i] = o;
                }
                w = (w + 1) & 2047;
            }
        }
    }
}
