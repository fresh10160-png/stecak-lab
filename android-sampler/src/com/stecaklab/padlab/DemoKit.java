package com.stecaklab.padlab;

import java.util.Random;

/** Synthesised 808-ish kit so the app makes noise on first launch, no files needed. */
final class DemoKit {
    private static double SR = Engine.SR;
    private static final Random R = new Random(1234);

    static void load(Engine e) {
        SR = Engine.SR;
        R.setSeed(1234);
        String[] names = {"KICK", "SNARE", "CL HAT", "OP HAT", "CLAP", "RIM", "TOM LO", "TOM HI",
                "COWBELL", "BASS", "STAB", "PLUCK", "SWEEP", "ZAP", "CRASH", "PERC"};
        float[][] d = {kick(), snare(), hat(0.09, 70), hat(0.6, 9), clap(), rim(), tom(110, 70, 8),
                tom(190, 120, 9), cowbell(), bass(), stab(), pluck(), sweep(), zap(), crash(), perc()};
        for (int i = 0; i < 16; i++) {
            Pad p = e.pads[i];
            p.sample = Sample.mono(names[i], norm(d[i]));
            p.name = names[i];
        }
        e.pads[2].choke = 1;
        e.pads[3].choke = 1;
        e.pads[9].mode = Pad.GATE;
        e.pads[12].mode = Pad.ONE_SHOT;

        int[][] pattern = {{0, 7, 10}, {4, 12}, {0, 2, 4, 6, 8, 10, 12, 14}, {15}, {12}};
        for (int p = 0; p < pattern.length; p++) for (int s : pattern[p]) e.steps[p][s] = true;
    }

    private static float[] norm(float[] d) {
        float pk = 1e-6f;
        for (float v : d) pk = Math.max(pk, Math.abs(v));
        for (int i = 0; i < d.length; i++) d[i] *= 0.9f / pk;
        return d;
    }

    private static float noise() { return R.nextFloat() * 2f - 1f; }

    private static float[] kick() {
        float[] d = new float[(int) (SR * 0.55)];
        double ph = 0;
        for (int i = 0; i < d.length; i++) {
            double t = i / SR;
            ph += 2 * Math.PI * (45 + 110 * Math.exp(-t * 28)) / SR;
            double s = Math.sin(ph) * Math.exp(-t * 6.5);
            if (i < 100) s += noise() * 0.3 * (1 - i / 100.0);
            d[i] = Fx.tanh((float) (s * 1.6));
        }
        return d;
    }

    private static float[] snare() {
        float[] d = new float[(int) (SR * 0.35)];
        float prev = 0;
        for (int i = 0; i < d.length; i++) {
            double t = i / SR;
            float n = noise();
            float hp = n - prev * 0.6f;
            prev = n;
            d[i] = (float) (hp * Math.exp(-t * 16) * 0.7 + Math.sin(2 * Math.PI * 185 * t) * Math.exp(-t * 28) * 0.6
                    + Math.sin(2 * Math.PI * 330 * t) * Math.exp(-t * 35) * 0.3);
        }
        return d;
    }

    private static float[] hat(double len, double decay) {
        float[] d = new float[(int) (SR * len)];
        double[] f = {205.3, 304.4, 369.6, 522.7, 540, 800};
        float lp1 = 0, lp2 = 0;
        for (int i = 0; i < d.length; i++) {
            double t = i / SR;
            float s = 0;
            for (double fr : f) s += Math.sin(2 * Math.PI * fr * 2.6 * t) > 0 ? 1f : -1f;
            s = s / 6f + noise() * 0.25f;
            lp1 += 0.35f * (s - lp1);
            float h = s - lp1;
            lp2 += 0.35f * (h - lp2);
            d[i] = (float) ((h - lp2) * Math.exp(-t * decay));
        }
        return d;
    }

    private static float[] clap() {
        float[] d = new float[(int) (SR * 0.4)];
        float lp = 0, bp = 0;
        for (int i = 0; i < d.length; i++) {
            double t = i / SR;
            lp += 0.25f * (noise() - lp);
            bp += 0.25f * (lp - bp);
            float x = lp - bp;
            double env = 0;
            for (int b = 0; b < 3; b++) if (t >= b * 0.011) env += Math.exp(-(t - b * 0.011) * 150);
            env += Math.exp(-Math.max(0, t - 0.033) * 22) * 0.6;
            d[i] = (float) (x * env);
        }
        return d;
    }

    private static float[] rim() {
        float[] d = new float[(int) (SR * 0.06)];
        for (int i = 0; i < d.length; i++) {
            double t = i / SR;
            d[i] = (float) ((Math.sin(2 * Math.PI * 1700 * t) + Math.sin(2 * Math.PI * 480 * t) * 0.6
                    + noise() * 0.3) * Math.exp(-t * 90));
        }
        return d;
    }

    private static float[] tom(double f0, double f1, double decay) {
        float[] d = new float[(int) (SR * 0.5)];
        double ph = 0;
        for (int i = 0; i < d.length; i++) {
            double t = i / SR;
            ph += 2 * Math.PI * (f1 + (f0 - f1) * Math.exp(-t * 18)) / SR;
            d[i] = (float) (Math.sin(ph) * Math.exp(-t * decay));
        }
        return d;
    }

    private static float[] cowbell() {
        float[] d = new float[(int) (SR * 0.4)];
        for (int i = 0; i < d.length; i++) {
            double t = i / SR;
            float s = (Math.sin(2 * Math.PI * 540 * t) > 0 ? 1f : -1f) + (Math.sin(2 * Math.PI * 800 * t) > 0 ? 1f : -1f);
            d[i] = (float) (s * 0.5 * (Math.exp(-t * 10) * 0.7 + Math.exp(-t * 60) * 0.3));
        }
        return d;
    }

    private static float[] bass() {
        float[] d = new float[(int) (SR * 1.0)];
        for (int i = 0; i < d.length; i++) {
            double t = i / SR;
            double a = Math.min(1, t / 0.005) * Math.exp(-t * 2.2);
            d[i] = (float) ((Math.sin(2 * Math.PI * 55 * t) + 0.35 * Math.sin(2 * Math.PI * 110 * t)) * a);
        }
        return d;
    }

    private static float[] stab() {
        float[] d = new float[(int) (SR * 0.8)];
        double[] f = {220, 261.63, 329.63, 392};
        float lp = 0;
        for (int i = 0; i < d.length; i++) {
            double t = i / SR;
            double s = 0;
            for (double fr : f) s += 2 * ((fr * t + 0.0) % 1.0) - 1;
            float k = (float) (0.04 + 0.5 * Math.exp(-t * 9));
            lp += k * ((float) s - lp);
            d[i] = (float) (lp * Math.min(1, t / 0.003) * Math.exp(-t * 3.5));
        }
        return d;
    }

    private static float[] pluck() {
        int n = (int) (SR * 1.0), len = (int) (SR / 330.0);
        float[] d = new float[n], b = new float[len];
        for (int i = 0; i < len; i++) b[i] = noise();
        int ix = 0;
        for (int i = 0; i < n; i++) {
            int nx = (ix + 1) % len;
            float y = 0.5f * (b[ix] + b[nx]) * 0.996f;
            d[i] = b[ix];
            b[ix] = y;
            ix = nx;
        }
        return d;
    }

    private static float[] sweep() {
        float[] d = new float[(int) (SR * 1.2)];
        float lp = 0;
        for (int i = 0; i < d.length; i++) {
            double t = i / SR;
            float k = (float) (0.005 + 0.6 * Math.pow(t / 1.2, 2));
            lp += k * (noise() - lp);
            d[i] = (float) (lp * Math.sin(Math.PI * t / 1.2));
        }
        return d;
    }

    private static float[] zap() {
        float[] d = new float[(int) (SR * 0.3)];
        double ph = 0;
        for (int i = 0; i < d.length; i++) {
            double t = i / SR;
            ph += 2 * Math.PI * (100 + 3000 * Math.exp(-t * 20)) / SR;
            d[i] = (float) (Math.sin(ph) * Math.exp(-t * 8));
        }
        return d;
    }

    private static float[] crash() {
        float[] d = new float[(int) (SR * 1.6)];
        float lp = 0;
        for (int i = 0; i < d.length; i++) {
            double t = i / SR;
            float s = noise();
            lp += 0.3f * (s - lp);
            d[i] = (float) ((s - lp) * Math.exp(-t * 3));
        }
        return d;
    }

    private static float[] perc() {
        float[] d = new float[(int) (SR * 0.25)];
        double ph = 0;
        for (int i = 0; i < d.length; i++) {
            double t = i / SR;
            ph += 2 * Math.PI * (300 + 500 * Math.exp(-t * 40)) / SR;
            d[i] = (float) (Math.sin(ph) * Math.exp(-t * 16));
        }
        return d;
    }
}
