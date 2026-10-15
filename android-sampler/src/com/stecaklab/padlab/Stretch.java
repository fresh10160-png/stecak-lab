package com.stecaklab.padlab;

/** WSOLA time-stretch: changes duration by factor f (output = input * f) while keeping pitch. */
final class Stretch {
    interface Cancel { boolean cancelled(); }

    static Sample wsola(Sample s, int si, int ei, double f, Cancel cancel) {
        final int len = ei - si;
        final int outLen = Math.max(64, (int) (len * f));
        int w = (int) Math.round(2048.0 * Engine.SR / 44100.0);
        w &= ~1;
        final int hs = w / 2, tol = w / 4, m = hs; // correlation length
        final float[] win = new float[w];
        for (int i = 0; i < w; i++) win[i] = 0.5f - 0.5f * (float) Math.cos(2 * Math.PI * i / w);

        final float[] l = s.l, r = s.r;
        final boolean mono = l == r;
        final float[] mix = new float[len];
        for (int i = 0; i < len; i++) mix[i] = mono ? l[si + i] : 0.5f * (l[si + i] + r[si + i]);

        float[] ol = new float[outLen + w], or = mono ? ol : new float[outLen + w];
        int prev = 0;
        for (int k = 0; (long) k * hs < outLen; k++) {
            if (cancel != null && (k & 7) == 0 && cancel.cancelled()) return null;
            int pos;
            if (k == 0) {
                pos = 0;
            } else {
                int nominal = (int) Math.round(k * hs / f);
                int ref = prev + hs;
                pos = Math.max(0, Math.min(len - 1, nominal));
                if (ref + m < len && nominal < len) {
                    double best = -1e30;
                    int lo = Math.max(0, nominal - tol), hi = Math.min(len - 1, nominal + tol);
                    for (int c = lo; c <= hi; c += 2) {
                        double dot = 0, en = 1e-9;
                        int lim = Math.min(m, len - c);
                        for (int j = 0; j < lim; j += 2) {
                            float a = mix[c + j];
                            dot += a * mix[ref + j];
                            en += a * a;
                        }
                        double sc = dot / Math.sqrt(en);
                        if (sc > best) { best = sc; pos = c; }
                    }
                }
            }
            int base = k * hs;
            for (int j = 0; j < w; j++) {
                int src = pos + j;
                if (src >= len) break;
                float g = (k == 0 && j < hs) ? 1f : win[j];
                ol[base + j] += g * l[si + src];
                if (!mono) or[base + j] += g * r[si + src];
            }
            prev = pos;
        }
        float[] rl = java.util.Arrays.copyOf(ol, outLen);
        float[] rr = mono ? rl : java.util.Arrays.copyOf(or, outLen);
        int fade = Math.min(64, outLen / 2);
        for (int i = 0; i < fade; i++) {
            float g = i / (float) fade;
            rl[outLen - 1 - i] *= g;
            if (!mono) rr[outLen - 1 - i] *= g;
        }
        return new Sample(s.name, rl, rr);
    }
}
