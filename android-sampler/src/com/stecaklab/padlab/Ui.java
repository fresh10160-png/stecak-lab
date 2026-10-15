package com.stecaklab.padlab;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;

/** Theme tokens and tiny helpers shared by the custom views. */
final class Ui {
    static final int BG = 0xFF0B0C0F, PANEL = 0xFF15171C, PANEL2 = 0xFF1D2027, STROKE = 0xFF2B2F38,
            TEXT = 0xFFEDEFF3, DIM = 0xFF7B828E, ACCENT = 0xFFFF7A1A, CYAN = 0xFF2FD6C8,
            REC = 0xFFFF3B3B, GREEN = 0xFF5BE37D, LCD = 0xFF07090B;
    static final int[] BANK = {0xFFFF7A1A, 0xFF2FD6C8, 0xFFD64BE0, 0xFF7BE35B};
    static final Typeface MONO = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);
    static final Typeface BOLD = Typeface.create(Typeface.DEFAULT, Typeface.BOLD);

    static float density = 1f;

    static int dp(float v) { return Math.round(v * density); }

    static float dpf(float v) { return v * density; }

    static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    static int alpha(int c, float a) { return Color.argb((int) (255 * a), (c >> 16) & 255, (c >> 8) & 255, c & 255); }

    static GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }
}
