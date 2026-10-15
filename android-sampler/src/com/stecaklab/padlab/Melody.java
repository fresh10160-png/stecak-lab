package com.stecaklab.padlab;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** A sung melody: YIN pitch tracking -> note list, plus key/scale/octave/grid shaping. */
final class Melody {
    static final String[] SCALE_NAMES = {"CHROMATIC", "MAJOR", "MINOR", "PENT MAJ", "PENT MIN", "BLUES", "DORIAN"};
    static final int[][] SCALES = {
            {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11}, {0, 2, 4, 5, 7, 9, 11}, {0, 2, 3, 5, 7, 8, 10},
            {0, 2, 4, 7, 9}, {0, 3, 5, 7, 10}, {0, 3, 5, 6, 7, 10}, {0, 2, 3, 5, 7, 9, 10}};
    static final String[] KEYS = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

    /** A detected note: pitch is fractional MIDI, times in seconds from the first note. */
    static final class Note {
        double start, dur;
        float pitch, vel;
        int shift;          // manual semitone edit
        boolean deleted;
    }

    /** A note after scale/grid/octave shaping, ready for the synth. */
    static final class Out {
        double start, dur;
        int midi, src;
        float vel;
    }

    final Sample recording;
    final List<Note> notes = new ArrayList<Note>();

    Melody(Sample rec) { recording = rec; }

    static String noteName(int midi) { return KEYS[((midi % 12) + 12) % 12] + (midi / 12 - 1); }

    // ---- analysis ------------------------------------------------------------------------
    static Melody analyze(Sample rec) {
        Melody m = new Melody(rec);
        final int sr = Engine.SR;
        final int dec = Math.max(1, sr / 16000), fs = sr / dec;
        int n = rec.l.length / dec;
        float[] y = new float[n];
        float peak = 1e-9f;
        for (int i = 0; i < n; i++) {
            float s = 0;
            for (int k = 0; k < dec; k++) s += rec.l[i * dec + k];
            y[i] = s / dec;
            peak = Math.max(peak, Math.abs(y[i]));
        }
        if (peak < 1e-4f) return m;           // silence
        for (int i = 0; i < n; i++) y[i] *= 0.8f / peak; // quiet mics still get analysed
        final int win = (int) (0.032 * fs), hop = (int) (0.010 * fs);
        final int tauMax = fs / 70, tauMin = Math.max(2, fs / 1000);
        int frames = (n - win - tauMax) / hop;
        if (frames < 10) return m;

        float[] pitch = new float[frames], rms = new float[frames];
        float[] d = new float[tauMax + 2], c = new float[tauMax + 2];
        for (int f = 0; f < frames; f++) {
            int t0 = f * hop;
            double e = 0;
            for (int j = 0; j < win; j++) e += y[t0 + j] * y[t0 + j];
            rms[f] = (float) Math.sqrt(e / win);
            pitch[f] = Float.NaN;
        }
        float[] sorted = rms.clone();
        Arrays.sort(sorted);
        float ref = sorted[(int) (frames * 0.95f)];
        float gate = Math.max(0.006f, ref * 0.12f);

        for (int f = 0; f < frames; f++) {
            if (rms[f] < gate) continue;
            int t0 = f * hop;
            for (int tau = 1; tau <= tauMax; tau++) {
                float s = 0;
                for (int j = 0; j < win; j++) {
                    float df = y[t0 + j] - y[t0 + j + tau];
                    s += df * df;
                }
                d[tau] = s;
            }
            float run = 0;
            c[0] = 1f;
            for (int tau = 1; tau <= tauMax; tau++) {
                run += d[tau];
                c[tau] = run > 0 ? d[tau] * tau / run : 1f;
            }
            int best = -1;
            for (int tau = tauMin; tau < tauMax; tau++) {
                if (c[tau] < 0.2f) {
                    while (tau + 1 < tauMax && c[tau + 1] < c[tau]) tau++;
                    best = tau;
                    break;
                }
            }
            if (best < 0) {
                int arg = tauMin;
                for (int tau = tauMin; tau < tauMax; tau++) if (c[tau] < c[arg]) arg = tau;
                if (c[arg] < 0.4f) best = arg;
            }
            if (best < 0) continue;
            float tau = best;
            if (best > 1 && best < tauMax) {
                float a = c[best - 1], b = c[best], g = c[best + 1], den = a - 2 * b + g;
                if (Math.abs(den) > 1e-9f) tau += 0.5f * (a - g) / den;
            }
            float f0 = fs / tau;
            pitch[f] = (float) (69 + 12 * Math.log(f0 / 440.0) / Math.log(2));
        }

        // 5-frame median smoothing over voiced neighbours
        float[] sm = pitch.clone();
        float[] w = new float[5];
        for (int f = 2; f < frames - 2; f++) {
            if (Float.isNaN(pitch[f])) continue;
            int k = 0;
            for (int j = -2; j <= 2; j++) if (!Float.isNaN(pitch[f + j])) w[k++] = pitch[f + j];
            if (k >= 3) { float[] t = Arrays.copyOf(w, k); Arrays.sort(t); sm[f] = t[k / 2]; }
        }
        m.segment(sm, rms, ref, hop, win, fs);
        return m;
    }

    private static float median(List<Float> v) {
        float[] a = new float[v.size()];
        for (int i = 0; i < a.length; i++) a[i] = v.get(i);
        Arrays.sort(a);
        return a[a.length / 2];
    }

    private void segment(float[] p, float[] rms, float ref, int hop, int win, int fs) {
        final int minFrames = 7;
        List<Float> cur = new ArrayList<Float>(), dev = new ArrayList<Float>();
        int startF = -1, unvoiced = 0;
        float rsum = 0;
        int frames = p.length;
        for (int f = 0; f <= frames; f++) {
            float v = f < frames ? p[f] : Float.NaN;
            if (Float.isNaN(v)) {
                unvoiced++;
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
                cur.add(v); rsum += rms[f];
                continue;
            }
            List<Float> tail = cur.subList(Math.max(0, cur.size() - 8), cur.size());
            float cm = median(tail);
            if (Math.abs(v - cm) > 0.75f) {
                dev.add(v);
                if (dev.size() >= 4) {
                    int split = f - dev.size() + 1;
                    close(cur, startF, split, rsum, ref, hop, win, fs);
                    startF = split;
                    cur = new ArrayList<Float>(dev);
                    dev.clear();
                    rsum = 0;
                    for (int q = split; q <= f; q++) rsum += rms[q];
                }
            } else {
                for (float q : dev) cur.add(q);
                dev.clear();
                cur.add(v);
                rsum += rms[f];
            }
        }
        if (notes.isEmpty()) return;
        // merge same-pitch neighbours separated by a tiny gap, then shift so the first note starts at 0
        for (int i = notes.size() - 2; i >= 0; i--) {
            Note a = notes.get(i), b = notes.get(i + 1);
            if (Math.abs(a.pitch - b.pitch) < 0.5f && b.start - (a.start + a.dur) < 0.05) {
                a.dur = b.start + b.dur - a.start;
                a.pitch = (a.pitch + b.pitch) / 2;
                notes.remove(i + 1);
            }
        }
        double t0 = notes.get(0).start;
        for (Note nt : notes) nt.start -= t0;
    }

    private void close(List<Float> cur, int sf, int ef, float rsum, float ref, int hop, int win, int fs) {
        if (ef - sf < 6 || cur.isEmpty()) return;
        Note n = new Note();
        n.start = (sf * (double) hop + win / 2.0) / fs;
        n.dur = (ef - sf) * (double) hop / fs;
        n.pitch = median(cur);
        float avg = rsum / Math.max(1, ef - sf);
        n.vel = Math.max(0.35f, Math.min(1f, 0.4f + 0.6f * avg / Math.max(1e-6f, ref)));
        notes.add(n);
    }

    // ---- shaping -------------------------------------------------------------------------
    static int snapToScale(float p, int root, int scale) {
        int[] sc = SCALES[scale];
        int best = Math.round(p);
        float bd = 1e9f;
        for (int k = Math.round(p) - 7; k <= Math.round(p) + 7; k++) {
            int pc = (((k - root) % 12) + 12) % 12;
            boolean in = false;
            for (int s : sc) if (s == pc) { in = true; break; }
            if (in && Math.abs(k - p) < bd) { bd = Math.abs(k - p); best = k; }
        }
        return best;
    }

    List<Out> build(int root, int scale, int octave, boolean snap, double bpm) {
        List<Out> out = new ArrayList<Out>();
        double step = 60.0 / bpm / 4.0;
        for (int idx = 0; idx < notes.size(); idx++) {
            Note n = notes.get(idx);
            if (n.deleted) continue;
            Out o = new Out();
            o.src = idx;
            o.midi = Math.max(12, Math.min(120, snapToScale(n.pitch, root, scale) + n.shift + 12 * octave));
            o.vel = n.vel;
            if (snap) {
                double s = Math.round(n.start / step) * step, e = Math.round((n.start + n.dur) / step) * step;
                if (e <= s) e = s + step;
                o.start = s;
                o.dur = e - s;
            } else {
                o.start = n.start;
                o.dur = n.dur;
            }
            out.add(o);
        }
        for (int i = 0; i + 1 < out.size(); i++) { // keep it monophonic
            Out a = out.get(i), b = out.get(i + 1);
            if (a.start + a.dur > b.start) a.dur = Math.max(0.03, b.start - a.start);
        }
        return out;
    }
}
