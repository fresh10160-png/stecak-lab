package com.stecaklab.padlab;

import java.util.List;
import java.util.Random;

/** Sings synthetic melodies into the analyser and renders every instrument. */
public class VoiceCheck {
    static int fail = 0;
    static void check(String w, boolean ok, String d) { if (!ok) fail++; System.out.printf("%-28s %s %s%n", w, d, ok ? "ok" : "FAIL"); }

    static float[] sing(int sr, double[][] spec, double vibCents, double noise, int harmonics, long seed) {
        float[] x = new float[(int) (sr * (spec[spec.length - 1][1] + 0.4))];
        Random r = new Random(seed);
        for (double[] n : spec) {
            double ph = 0;
            for (int i = (int) (n[0] * sr); i < (int) (n[1] * sr); i++) {
                double t = i / (double) sr, tn = t - n[0];
                double f = 440 * Math.pow(2, (n[2] - 69) / 12.0 + vibCents / 1200.0 * Math.sin(2 * Math.PI * 5.5 * t));
                if (n.length > 3) f *= Math.pow(2, n[3] * Math.min(1, tn / (n[1] - n[0])) / 12.0); // glide
                ph += 2 * Math.PI * f / sr;
                double env = Math.min(1, tn / 0.03) * Math.min(1, (n[1] - t) / 0.03);
                double s = 0;
                for (int h = 1; h <= harmonics; h++) s += Math.sin(h * ph) / h;
                x[i] += (float) (0.4 * s * env + noise * (r.nextDouble() * 2 - 1));
            }
        }
        return x;
    }

    public static void main(String[] a) {
        for (int rate : new int[]{44100, 48000}) {
            Engine.setRate(rate);
            System.out.println("== " + rate);
            double[][] spec = {{0.20, 0.80, 60}, {1.00, 1.60, 64}, {1.80, 2.50, 67}, {2.70, 3.40, 57}, {3.60, 4.20, 62}};
            int[] want = {60, 64, 67, 57, 62};
            for (int hv = 0; hv < 3; hv++) {
                int harm = new int[]{1, 6, 12}[hv];
                double vib = new double[]{0, 30, 40}[hv], nz = new double[]{0, 0.01, 0.02}[hv];
                Melody m = Melody.analyze(Sample.mono("sung", sing(rate, spec, vib, nz, harm, hv)));
                boolean ok = m.notes.size() == want.length;
                StringBuilder sb = new StringBuilder();
                for (Melody.Note n : m.notes) sb.append(String.format("%d@%.2f+%.2f ", Math.round(n.pitch), n.start, n.dur));
                for (int i = 0; ok && i < want.length; i++) {
                    Melody.Note n = m.notes.get(i);
                    ok &= Math.round(n.pitch) == want[i] && Math.abs(n.start - (spec[i][0] - 0.2)) < 0.06 && Math.abs(n.dur - 0.6) < 0.12;
                }
                check("sing h=" + harm + " vib=" + vib, ok, sb.toString());
            }
            // glide up 3 semitones inside a long note is split into notes; pitch-snap into C major
            Melody g = Melody.analyze(Sample.mono("g", sing(rate, new double[][]{{0.1, 1.1, 61.4}}, 0, 0, 6, 9)));
            check("scale snap 61.4 -> D (C maj)", g.notes.size() == 1 && Melody.snapToScale(61.4f, 0, 1) == 62 && Melody.snapToScale(61.4f, 0, 0) == 61 && Melody.snapToScale(61.4f, 1, 1) == 61,
                    "snap(61.4)=" + Melody.snapToScale(61.4f, 0, 1) + " notes=" + g.notes.size());
            // silence / noise only -> no notes
            float[] nz = new float[rate * 2];
            Random r = new Random(1);
            for (int i = 0; i < nz.length; i++) nz[i] = (float) (0.004 * r.nextGaussian());
            check("no notes in noise", Melody.analyze(Sample.mono("n", nz)).notes.isEmpty(), "");

            // render every instrument, check sanity + pitch for the sine
            Melody m = Melody.analyze(Sample.mono("sung", sing(rate, spec, 20, 0.005, 6, 5)));
            for (int inst = 1; inst < Synth.NAMES.length; inst++) {
                Sample src = inst == Synth.SAMPLE ? Sample.mono("src", sing(rate, new double[][]{{0, 1, 60}}, 0, 0, 4, 1)) : null;
                long t0 = System.nanoTime();
                Sample s = Synth.render(m, inst, src, 60, 0, 0, 0, false, 100);
                double ms = (System.nanoTime() - t0) / 1e6;
                boolean ok = s != null;
                double pk = 0, rms = 0;
                if (ok) {
                    for (int i = 0; i < s.n; i++) { pk = Math.max(pk, Math.abs(s.l[i])); rms += s.l[i] * s.l[i]; if (Float.isNaN(s.l[i])) ok = false; }
                    rms = Math.sqrt(rms / s.n);
                    ok &= pk <= 0.9 && rms > 0.02;
                }
                check("render " + Synth.NAMES[inst], ok, String.format("len=%.2fs peak=%.2f rms=%.3f (%.0f ms)", s == null ? 0 : s.n / (double) rate, pk, rms, ms));
            }
            // sine instrument pitch: first note C4 -> ~261.6 Hz
            Sample s = Synth.render(m, 1, null, 60, 0, 0, 0, false, 100);
            int a0 = (int) (0.1 * rate), a1 = (int) (0.45 * rate), z = 0;
            for (int i = a0 + 1; i < a1; i++) if (s.l[i - 1] < 0 && s.l[i] >= 0) z++;
            double f = z / ((a1 - a0) / (double) rate);
            check("SINE pitch C4", Math.abs(f - 261.6) < 6, String.format("%.1f Hz", f));
            // octave shift and snap grid length
            Sample o = Synth.render(m, 1, null, 60, 0, 0, 1, true, 120);
            double bar = 4 * 60.0 / 120;
            check("snap length = whole bars", Math.abs(o.n / (double) rate / bar - Math.round(o.n / (double) rate / bar)) < 0.01, String.format("%.2f bars", o.n / (double) rate / bar));
        }
        System.out.println(fail == 0 ? "ALL OK" : fail + " FAILED");
        System.exit(fail == 0 ? 0 : 1);
    }
}
