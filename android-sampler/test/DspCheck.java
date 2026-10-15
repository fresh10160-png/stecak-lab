package com.stecaklab.padlab;

/** Headless sanity check: renders the engine on a JVM and reports peak/RMS/NaN, stretch accuracy, pad FX. */
public class DspCheck {
    static final int B = 256;
    static int fail = 0;

    static double[] run(Engine e, int blocks) {
        short[] out = new short[B * 2];
        double peak = 0, sum = 0; long n = 0;
        for (int b = 0; b < blocks; b++) {
            e.render(out, B);
            for (short s : out) { peak = Math.max(peak, Math.abs(s) / 32768.0); sum += (s / 32768.0) * (s / 32768.0); n++; }
        }
        return new double[]{peak, Math.sqrt(sum / n)};
    }

    static void check(String what, boolean ok, String detail) {
        if (!ok) fail++;
        System.out.printf("%-26s %s %s%n", what, detail, ok ? "ok" : "FAIL");
    }

    static double freq(float[] d) { // via zero crossings
        int z = 0;
        for (int i = 1; i < d.length; i++) if (d[i - 1] < 0 && d[i] >= 0) z++;
        return z * (double) Engine.SR / d.length;
    }

    public static void main(String[] a) throws Exception {
        for (int rate : new int[]{44100, 48000}) {
            Engine.setRate(rate);
            System.out.println("== sample rate " + rate);
            for (int fxIdx = -1; fxIdx < 11; fxIdx++) {
                Engine e = new Engine();
                DemoKit.load(e);
                if (fxIdx >= 0) e.fx[fxIdx].enable(true);
                e.play();
                double[] r = run(e, rate * 6 / B);
                String name = fxIdx < 0 ? "(dry)" : e.fx[fxIdx].name;
                check("master " + name, !Double.isNaN(r[0]) && r[0] <= 1.0 && r[1] > 0.005,
                        String.format("peak=%.3f rms=%.4f", r[0], r[1]));
            }
            // per-pad FX: reverb on the snare only must add a tail after the hit
            Engine e = new Engine();
            DemoKit.load(e);
            e.chainFor(1)[Fx.REVERB].p[2] = 0.8f;
            e.chainFor(1)[Fx.REVERB].enable(true);
            e.trigger(1, 1f);
            run(e, rate / 2 / B);              // body of the snare
            double[] tail = run(e, 40);        // ~0.2s after the dry sample ended
            Engine d = new Engine();
            DemoKit.load(d);
            d.trigger(1, 1f);
            run(d, rate / 2 / B);
            double[] dryTail = run(d, 40);
            check("pad FX reverb tail", tail[1] > 0.003 && dryTail[1] < 0.0005, String.format("wet=%.4f dry=%.5f", tail[1], dryTail[1]));
            // pad FX must not touch other pads
            Engine o = new Engine();
            DemoKit.load(o);
            o.chainFor(1)[Fx.LOFI].p[0] = 0f;
            o.chainFor(1)[Fx.LOFI].enable(true);
            Engine ref = new Engine();
            DemoKit.load(ref);
            o.trigger(0, 1f); ref.trigger(0, 1f);
            double[] x = run(o, 60), y = run(ref, 60);
            check("pad FX isolation", Math.abs(x[1] - y[1]) < 1e-6, String.format("%.5f vs %.5f", x[1], y[1]));

            // everything at max / zero
            for (float val : new float[]{1f, 0f}) {
                Engine m = new Engine();
                DemoKit.load(m);
                for (Fx f : m.fx) { f.enable(true); for (int i = 0; i < f.p.length; i++) f.p[i] = val; }
                for (Fx f : m.chainFor(2)) { f.enable(true); for (int i = 0; i < f.p.length; i++) f.p[i] = val; }
                m.play();
                double[] r = run(m, rate * 8 / B);
                check("all fx @" + val, !Double.isNaN(r[0]) && r[0] <= 1.0, String.format("peak=%.3f rms=%.4f", r[0], r[1]));
            }

            // WSOLA: stretch a 440 Hz sine; length scales, pitch stays
            float[] sine = new float[rate * 2];
            for (int i = 0; i < sine.length; i++) sine[i] = (float) Math.sin(2 * Math.PI * 440 * i / rate);
            Sample src = Sample.mono("sine", sine);
            for (double f : new double[]{0.6, 1.0, 1.5, 2.0}) {
                long t0 = System.nanoTime();
                Sample out = Stretch.wsola(src, 0, sine.length, f, null);
                double ms = (System.nanoTime() - t0) / 1e6;
                double fr = freq(java.util.Arrays.copyOfRange(out.l, rate / 10, out.n - rate / 10));
                double rms = 0;
                for (int i = rate / 10; i < out.n - rate / 10; i++) rms += out.l[i] * out.l[i];
                rms = Math.sqrt(rms / (out.n - rate / 5));
                check("wsola x" + f, Math.abs(out.n - sine.length * f) < 2 && Math.abs(fr - 440) < 6 && Math.abs(rms - 0.707) < 0.06,
                        String.format("len=%d freq=%.1f rms=%.3f (%.0f ms)", out.n, fr, rms, ms));
            }
            // engine-level: fit 2 beats @120bpm on the kick-loop -> rendered length 1.0 s
            Engine w = new Engine();
            DemoKit.load(w);
            w.bpm = 120;
            w.pads[9].fitBeats = 4;
            w.requestWarp(9);
            for (int i = 0; i < 200 && w.warpBusy.get() > 0; i++) Thread.sleep(20);
            Sample rs = w.pads[9].rendered;
            check("fit 4 beats @120", rs != null && Math.abs(rs.n / (double) rate - 2.0) < 0.02,
                    rs == null ? "no render" : String.format("%.3fs", rs.n / (double) rate));
            // pitch +12 with key lock keeps duration, no key lock halves it
            Engine k = new Engine();
            DemoKit.load(k);
            k.pads[9].pitch = 12;
            k.pads[9].keyLock = true;
            k.requestWarp(9);
            for (int i = 0; i < 200 && k.warpBusy.get() > 0; i++) Thread.sleep(20);
            Sample ks = k.pads[9].rendered;
            double base = k.pads[9].sample.n;
            check("keylock +12st", ks != null && Math.abs(ks.n / base - 2.0) < 0.02, ks == null ? "no render" : "stretched x" + ks.n / base + " then played 2x faster");
            // render with warped pad plays and finishes
            k.trigger(9, 1f);
            double[] kr = run(k, rate / B);
            check("warped pad plays", kr[1] > 0.01 && kr[0] <= 1.0, String.format("rms=%.4f", kr[1]));

            // pad modes
            Engine m = new Engine();
            DemoKit.load(m);
            m.pads[9].mode = Pad.LOOP;
            m.trigger(9, 1f);
            double loopOn = run(m, 200)[1];
            m.trigger(9, 1f);
            run(m, 20);
            double off = run(m, 50)[1];
            check("loop latch", loopOn > 0.05 && off < 0.001, String.format("on=%.4f off=%.5f", loopOn, off));
            m.pads[0].pan = -1f;
            m.trigger(0, 1f);
            short[] o2 = new short[B * 2];
            m.render(o2, B);
            long sl = 0, sr = 0;
            for (int i = 0; i < B; i++) { sl += Math.abs(o2[2 * i]); sr += Math.abs(o2[2 * i + 1]); }
            check("pan hard left", sl > 0 && sr == 0, "L=" + sl + " R=" + sr);
        }
        System.out.println(fail == 0 ? "ALL OK" : fail + " FAILED");
        System.exit(fail == 0 ? 0 : 1);
    }
}
