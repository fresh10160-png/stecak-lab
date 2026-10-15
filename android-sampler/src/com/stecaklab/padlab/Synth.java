package com.stecaklab.padlab;

import java.util.List;
import java.util.Random;

/** Offline note renderer: turns a melody into a stereo Sample using a chosen instrument. */
final class Synth {
    static final String[] NAMES = {"ORIGINAL", "SINE", "SAW LEAD", "SQUARE", "PLUCK", "BASS", "PAD",
            "E-PIANO", "BELL", "ORGAN", "STRINGS", "FLUTE", "CHOIR", "SAMPLE"};
    static final int ORIGINAL = 0, SAMPLE = 13;

    private static float sr() { return Engine.SR; }

    /** ZDF state-variable filter. */
    private static final class Svf {
        float ic1, ic2, lp, bp;
        void tick(float x, float fc, float q) {
            float g = (float) Math.tan(Math.PI * Math.min(fc, 0.45f * sr()) / sr());
            float k = 1f / q;
            float a1 = 1f / (1f + g * (g + k)), a2 = g * a1, a3 = g * a2;
            float v3 = x - ic2, v1 = a1 * ic1 + a2 * v3, v2 = ic2 + a2 * ic1 + a3 * v3;
            ic1 = 2 * v1 - ic1;
            ic2 = 2 * v2 - ic2;
            bp = v1;
            lp = v2;
        }
    }

    private static float blep(float t, float dt) {
        if (t < dt) { t /= dt; return t + t - t * t - 1f; }
        if (t > 1f - dt) { t = (t - 1f) / dt; return t * t + t + t + 1f; }
        return 0f;
    }

    private static float saw(float ph, float dt) { return 2f * ph - 1f - blep(ph, dt); }

    private static float square(float ph, float dt, float duty) {
        float v = ph < duty ? 1f : -1f;
        v += blep(ph, dt);
        float p2 = ph - duty;
        if (p2 < 0) p2 += 1f;
        return v - blep(p2, dt);
    }

    /** Gate-based ADSR in samples; i = sample index, len = note-on length. */
    private static float adsr(int i, int len, float a, float d, float s, float r) {
        float t = i / sr();
        float lvl;
        if (i < len) {
            lvl = t < a ? t / a : (t < a + d ? 1f - (1f - s) * (t - a) / Math.max(1e-4f, d) : s);
            return lvl;
        }
        float tOff = (i - len) / sr();
        float at = len / sr();
        lvl = at < a ? at / a : (at < a + d ? 1f - (1f - s) * (at - a) / Math.max(1e-4f, d) : s);
        return Math.max(0f, lvl * (1f - tOff / r));
    }

    private static float release(int inst) {
        switch (inst) {
            case 4: return 1.6f;   // pluck rings
            case 7: return 0.5f;
            case 8: return 2.2f;
            case 6: case 10: case 12: return 0.6f;
            default: return 0.18f;
        }
    }

    static Sample render(Melody m, int inst, Sample sampleSrc, int sampleRoot, int root, int scale,
                         int octave, boolean snap, double bpm) {
        return render(m.build(root, scale, octave, snap, bpm), m.recording, inst, sampleSrc, sampleRoot, snap, bpm);
    }

    static Sample render(List<Melody.Out> notes, Sample recording, int inst, Sample sampleSrc, int sampleRoot,
                         boolean snap, double bpm) {
        if (inst == ORIGINAL) return recording;
        if (notes.isEmpty()) return null;
        if (inst == SAMPLE && sampleSrc == null) return null;
        Melody.Out last = notes.get(notes.size() - 1);
        double end = last.start + last.dur;
        double total = end + release(inst) + 0.05;
        if (snap) {
            double bar = 4 * 60.0 / bpm;
            total = Math.max(Math.ceil(end / bar - 1e-6) * bar, bar);
        }
        int len = (int) (total * sr());
        float[] l = new float[len], r = new float[len];
        Random rnd = new Random(42);
        for (Melody.Out o : notes) {
            int s0 = (int) (o.start * sr()), nl = Math.max(32, (int) (o.dur * sr()));
            double f = 440.0 * Math.pow(2.0, (o.midi - 69) / 12.0);
            if (inst == SAMPLE) {
                playSample(l, r, s0, nl, sampleSrc, Math.pow(2.0, (o.midi - sampleRoot) / 12.0), o.vel);
            } else if (inst == 6 || inst == 10 || inst == 12) {
                voice(l, s0, nl, f, o.vel, inst, -1, rnd);
                voice(r, s0, nl, f, o.vel, inst, 1, rnd);
            } else {
                float[] tmp = new float[len];
                voice(tmp, s0, nl, f, o.vel, inst, 0, rnd);
                for (int i = s0; i < len; i++) { l[i] += tmp[i]; r[i] += tmp[i]; }
            }
        }
        float pk = 1e-6f;
        for (int i = 0; i < len; i++) pk = Math.max(pk, Math.max(Math.abs(l[i]), Math.abs(r[i])));
        float g = 0.85f / pk;
        for (int i = 0; i < len; i++) { l[i] *= g; r[i] *= g; }
        int fade = Math.min(256, len / 2);
        for (int i = 0; i < fade; i++) { float k = i / (float) fade; l[len - 1 - i] *= k; r[len - 1 - i] *= k; }
        return new Sample(NAMES[inst], l, r);
    }

    private static void playSample(float[] l, float[] r, int s0, int nl, Sample src, double rate, float vel) {
        int relS = (int) (0.03 * sr());
        double pos = 0;
        for (int i = 0; i < nl + relS && s0 + i < l.length; i++) {
            if (pos >= src.n - 1) break;
            float env = i < nl ? 1f : 1f - (i - nl) / (float) relS;
            if (i < 48) env *= i / 48f;
            int i0 = (int) pos;
            float fr = (float) (pos - i0);
            l[s0 + i] += (src.l[i0] + (src.l[i0 + 1] - src.l[i0]) * fr) * env * vel;
            r[s0 + i] += (src.r[i0] + (src.r[i0 + 1] - src.r[i0]) * fr) * env * vel;
            pos += rate;
        }
    }

    /** Adds one note to out. side = -1/+1 detune the left/right copy for wide instruments. */
    private static void voice(float[] out, int s0, int nl, double freq, float vel, int inst, int side, Random rnd) {
        final float sr = sr();
        float rel = release(inst);
        int total = nl + (int) (rel * sr);
        float ph1 = 0, ph2 = 0, ph3 = 0, ph4 = 0, lfoPh = 0;
        Svf f1 = new Svf(), f2 = new Svf();
        float nz = 0;
        double base = freq;
        for (int i = 0; i < total && s0 + i < out.length; i++) {
            float t = i / sr, y = 0, env;
            float vib = (float) Math.sin(2 * Math.PI * 5.2 * t);
            switch (inst) {
                case 1: { // SINE
                    double fr = base * (1 + 0.002 * vib * Math.min(1f, t / 0.25f));
                    ph1 += 2 * Math.PI * fr / sr;
                    env = adsr(i, nl, 0.01f, 0f, 1f, 0.15f);
                    y = (float) Math.sin(ph1) * 0.7f * env;
                    break;
                }
                case 2: { // SAW LEAD
                    float dt1 = (float) (base * 1.0035 / sr), dt2 = (float) (base * 0.9965 / sr);
                    ph1 = (ph1 + dt1) % 1f;
                    ph2 = (ph2 + dt2) % 1f;
                    float x = 0.5f * (saw(ph1, dt1) + saw(ph2, dt2));
                    f1.tick(x, 900f + 4500f * (float) Math.exp(-t * 7), 1.8f);
                    env = adsr(i, nl, 0.005f, 0.05f, 0.8f, 0.15f);
                    y = f1.lp * 0.8f * env;
                    break;
                }
                case 3: { // SQUARE
                    float dt = (float) (base / sr);
                    ph1 = (ph1 + dt) % 1f;
                    float duty = 0.5f + 0.08f * (float) Math.sin(2 * Math.PI * 0.4 * t);
                    f1.tick(square(ph1, dt, duty), 7000f, 0.8f);
                    env = adsr(i, nl, 0.003f, 0f, 1f, 0.07f);
                    y = f1.lp * 0.45f * env;
                    break;
                }
                case 4: { // PLUCK: additive, exact pitch, high partials die first
                    float s = 0;
                    for (int k = 1; k <= 14 && base * k < 0.45 * sr; k++) {
                        double ph = 2 * Math.PI * base * k * i / sr;
                        s += (float) (Math.sin(ph) / k * Math.exp(-t * (1.6 + k * 1.3)));
                    }
                    env = i < nl ? 1f : Math.max(0f, 1f - (i - nl) / (rel * sr * 0.9f));
                    y = s * 0.9f * env * Math.min(1f, i / 24f);
                    break;
                }
                case 5: { // BASS (octave down)
                    double fb = base * 0.5;
                    float dt = (float) (fb / sr);
                    ph1 = (ph1 + dt) % 1f;
                    ph2 += 2 * Math.PI * fb / sr;
                    f1.tick(saw(ph1, dt), 220f + 1400f * (float) Math.exp(-t * 9), 2.2f);
                    env = adsr(i, nl, 0.004f, 0.08f, 0.85f, 0.1f);
                    y = (f1.lp * 0.6f + (float) Math.sin(ph2) * 0.7f) * env;
                    break;
                }
                case 6: { // PAD
                    float[] dts = {(float) (base * Math.pow(2, (-9 + side * 3) / 1200.0) / sr),
                            (float) (base / sr), (float) (base * Math.pow(2, (9 + side * 3) / 1200.0) / sr)};
                    ph1 = (ph1 + dts[0]) % 1f;
                    ph2 = (ph2 + dts[1]) % 1f;
                    ph3 = (ph3 + dts[2]) % 1f;
                    float x = (saw(ph1, dts[0]) + saw(ph2, dts[1]) + saw(ph3, dts[2])) / 3f;
                    f1.tick(x, 1300f + 700f * (float) Math.sin(2 * Math.PI * 0.25 * t), 0.9f);
                    env = adsr(i, nl, 0.18f, 0f, 1f, 0.5f);
                    y = f1.lp * 0.7f * env;
                    break;
                }
                case 7: { // E-PIANO (FM)
                    double ph = 2 * Math.PI * base * t;
                    float idx = 2.4f * (float) Math.exp(-t * 4.5) + 0.35f;
                    float s = (float) Math.sin(ph + idx * Math.sin(ph));
                    s += 0.12f * (float) (Math.sin(2 * Math.PI * base * 14 * t) * Math.exp(-t * 22));
                    float dec = (float) Math.exp(-t * 1.1);
                    env = i < nl ? 1f : Math.max(0f, 1f - (i - nl) / (rel * sr));
                    y = s * dec * env * 0.7f * Math.min(1f, i / 24f);
                    break;
                }
                case 8: { // BELL (inharmonic FM)
                    double ph = 2 * Math.PI * base * t;
                    float idx = 3.6f * (float) Math.exp(-t * 1.6);
                    float s = (float) Math.sin(ph + idx * Math.sin(ph * 3.5));
                    s += 0.35f * (float) (Math.sin(ph * 2.0) * Math.exp(-t * 2.4));
                    env = i < nl ? 1f : Math.max(0f, 1f - (i - nl) / (rel * sr));
                    y = s * (float) Math.exp(-t * 1.4) * env * 0.6f * Math.min(1f, i / 24f);
                    break;
                }
                case 9: { // ORGAN
                    double ph = 2 * Math.PI * base * t;
                    float s = (float) (Math.sin(ph) + 0.8 * Math.sin(2 * ph) + 0.6 * Math.sin(3 * ph)
                            + 0.5 * Math.sin(4 * ph) + 0.3 * Math.sin(6 * ph) + 0.2 * Math.sin(8 * ph)) / 3.4f;
                    env = adsr(i, nl, 0.01f, 0f, 1f, 0.06f);
                    y = s * env * 0.8f;
                    break;
                }
                case 10: { // STRINGS
                    float v = 1 + 0.003f * vib * Math.min(1f, t / 0.3f);
                    float[] dts = new float[4];
                    float[] det = {-10, -3, 3, 10};
                    float x = 0;
                    float[] phs = {ph1, ph2, ph3, ph4};
                    for (int k = 0; k < 4; k++) {
                        dts[k] = (float) (base * v * Math.pow(2, (det[k] + side * 2) / 1200.0) / sr);
                        phs[k] = (phs[k] + dts[k]) % 1f;
                        x += saw(phs[k], dts[k]) * 0.25f;
                    }
                    ph1 = phs[0]; ph2 = phs[1]; ph3 = phs[2]; ph4 = phs[3];
                    f1.tick(x, 2800f, 0.8f);
                    env = adsr(i, nl, 0.14f, 0f, 1f, 0.35f);
                    y = f1.lp * 0.9f * env;
                    break;
                }
                case 11: { // FLUTE
                    double fr = base * (1 + 0.004 * vib * Math.min(1f, Math.max(0f, (t - 0.2f) / 0.3f)));
                    ph1 += 2 * Math.PI * fr / sr;
                    nz += 0.35f * ((rnd.nextFloat() * 2 - 1) - nz);
                    float s = (float) (Math.sin(ph1) + 0.22 * Math.sin(2 * ph1) + 0.08 * Math.sin(3 * ph1));
                    env = adsr(i, nl, 0.07f, 0f, 0.92f, 0.12f);
                    y = (s * 0.7f + nz * 0.07f * (1f + 2f * (float) Math.exp(-t * 12))) * env;
                    break;
                }
                case 12: { // CHOIR ("ah" formants)
                    float v = 1 + 0.004f * vib * Math.min(1f, t / 0.35f);
                    float dt1 = (float) (base * v * Math.pow(2, (-6 + side * 2) / 1200.0) / sr);
                    float dt2 = (float) (base * v * Math.pow(2, (6 + side * 2) / 1200.0) / sr);
                    ph1 = (ph1 + dt1) % 1f;
                    ph2 = (ph2 + dt2) % 1f;
                    float x = 0.5f * (saw(ph1, dt1) + saw(ph2, dt2));
                    f1.tick(x, 720f, 6f);
                    f2.tick(x, 1250f, 7f);
                    env = adsr(i, nl, 0.14f, 0f, 1f, 0.3f);
                    y = (f1.bp + f2.bp * 0.6f) * 0.9f * env;
                    break;
                }
            }
            out[s0 + i] += y * vel;
        }
    }
}
