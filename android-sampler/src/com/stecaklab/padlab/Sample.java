package com.stecaklab.padlab;

/** Stereo float sample at the engine rate. Mono samples share one array for both channels. */
final class Sample {
    final String name;
    final float[] l, r;
    final int n;

    Sample(String name, float[] l, float[] r) {
        this.name = name;
        this.l = l;
        this.r = r;
        this.n = l.length;
    }

    static Sample mono(String name, float[] d) {
        return new Sample(name, d, d);
    }

    /** Linear resample to the engine rate and apply a short fade-out so one-shots never click. */
    static Sample prepare(String name, float[] l, float[] r, int srcRate) {
        if (srcRate != Engine.SR) {
            l = resample(l, srcRate);
            r = (r == l) ? l : resample(r, srcRate);
        }
        int fade = Math.min(64, l.length / 2);
        for (int i = 0; i < fade; i++) {
            float g = i / (float) fade;
            int k = l.length - 1 - i;
            l[k] *= g;
            if (r != l) r[k] *= g;
        }
        return new Sample(name, l, r);
    }

    static float[] resample(float[] src, int srcRate) {
        double ratio = srcRate / (double) Engine.SR;
        int n = (int) (src.length / ratio);
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            double p = i * ratio;
            int i0 = (int) p;
            int i1 = Math.min(i0 + 1, src.length - 1);
            float f = (float) (p - i0);
            out[i] = src[i0] + (src[i1] - src[i0]) * f;
        }
        return out;
    }
}
