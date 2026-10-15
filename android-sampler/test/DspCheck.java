package com.stecaklab.padlab;

/** Headless sanity check: renders the demo beat through each effect and reports peak/RMS/NaN. */
public class DspCheck {
    static double[] run(Engine e, int blocks) {
        short[] out = new short[Engine.BLOCK * 2];
        double peak = 0, sum = 0; long n = 0; boolean bad = false;
        for (int b = 0; b < blocks; b++) {
            e.render(out);
            for (short s : out) { peak = Math.max(peak, Math.abs(s) / 32768.0); sum += (s / 32768.0) * (s / 32768.0); n++; }
        }
        return new double[]{peak, Math.sqrt(sum / n)};
    }

    public static void main(String[] a) {
        int fail = 0;
        for (int fxIdx = -1; fxIdx < 11; fxIdx++) {
            Engine e = new Engine();
            DemoKit.load(e);
            e.master = 0.8f;
            if (fxIdx >= 0) e.fx[fxIdx].on = true;
            e.play();
            double[] r = run(e, 44100 * 6 / Engine.BLOCK);
            String name = fxIdx < 0 ? "(dry)" : e.fx[fxIdx].name;
            boolean ok = !Double.isNaN(r[0]) && r[0] <= 1.0 && r[1] > 0.005;
            if (!ok) fail++;
            System.out.printf("%-14s peak=%.3f rms=%.4f %s%n", name, r[0], r[1], ok ? "ok" : "FAIL");
        }
        // everything at once, maxed params, must stay finite
        Engine e = new Engine();
        DemoKit.load(e);
        for (Fx f : e.fx) { f.on = true; for (int i = 0; i < f.p.length; i++) f.p[i] = 1f; }
        e.play();
        double[] r = run(e, 44100 * 8 / Engine.BLOCK);
        boolean ok = !Double.isNaN(r[0]) && r[0] <= 1.0;
        if (!ok) fail++;
        System.out.printf("%-14s peak=%.3f rms=%.4f %s%n", "ALL MAX", r[0], r[1], ok ? "ok" : "FAIL");
        // all zero
        e = new Engine();
        DemoKit.load(e);
        for (Fx f : e.fx) { f.on = true; for (int i = 0; i < f.p.length; i++) f.p[i] = 0f; }
        e.play();
        r = run(e, 44100 * 4 / Engine.BLOCK);
        ok = !Double.isNaN(r[0]) && r[0] <= 1.0;
        if (!ok) fail++;
        System.out.printf("%-14s peak=%.3f rms=%.4f %s%n", "ALL ZERO", r[0], r[1], ok ? "ok" : "FAIL");
        // pad behaviours: loop latch, gate release, reverse, choke
        e = new Engine();
        DemoKit.load(e);
        e.pads[9].mode = Pad.LOOP;
        e.trigger(9, 1f);
        r = run(e, 200);
        double loopOn = r[1];
        e.trigger(9, 1f); // latch off
        run(e, 20);
        r = run(e, 50);
        System.out.printf("loop latch: on rms=%.4f, after 2nd tap rms=%.5f %s%n", loopOn, r[1], (loopOn > 0.05 && r[1] < 0.001) ? "ok" : "FAIL");
        if (!(loopOn > 0.05 && r[1] < 0.001)) fail++;
        e.pads[0].reverse = true;
        e.trigger(0, 1f);
        r = run(e, 40);
        System.out.printf("reverse kick peak=%.3f %s%n", r[0], r[0] > 0.1 ? "ok" : "FAIL");
        if (r[0] <= 0.1) fail++;
        System.out.println(fail == 0 ? "ALL OK" : fail + " FAILED");
        System.exit(fail == 0 ? 0 : 1);
    }
}
