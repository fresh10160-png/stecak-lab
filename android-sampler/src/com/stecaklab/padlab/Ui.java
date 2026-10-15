package com.stecaklab.padlab;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

final class Ui {
    static final int BG = 0xFF101114, PANEL = 0xFF1c1e23, PAD = 0xFF2a2d33, PAD_LOADED = 0xFF3a3e46,
            ACCENT = 0xFFFF6A00, TEXT = 0xFFE8E8E8, DIM = 0xFF8A8F98, REC = 0xFFE5252A;

    static float density;

    static int dp(float v) { return Math.round(v * density); }

    static GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    static Button button(Context c, String text) {
        Button b = new Button(c);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(TEXT);
        b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(rounded(PAD, 6));
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(dp(40));
        b.setMinimumHeight(dp(40));
        b.setPadding(dp(10), 0, dp(10), 0);
        return b;
    }

    static void active(Button b, boolean on) {
        b.setBackground(rounded(on ? ACCENT : PAD, 6));
        b.setTextColor(on ? Color.BLACK : TEXT);
    }

    static TextView label(Context c, String t, float sp, int color) {
        TextView v = new TextView(c);
        v.setText(t);
        v.setTextSize(sp);
        v.setTextColor(color);
        return v;
    }

    static LinearLayout.LayoutParams lp(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }

    static LinearLayout.LayoutParams margin(LinearLayout.LayoutParams p, int l, int t, int r, int b) {
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    /** One slider row: [label][seekbar][value]. */
    static final class Row {
        final LinearLayout view;
        final SeekBar bar;
        final TextView name, value;

        Row(Context c, String label, int max) {
            view = new LinearLayout(c);
            view.setOrientation(LinearLayout.HORIZONTAL);
            view.setGravity(Gravity.CENTER_VERTICAL);
            name = label(c, label, 12, DIM);
            bar = new SeekBar(c);
            bar.setMax(max);
            value = label(c, "", 12, TEXT);
            value.setGravity(Gravity.END);
            view.addView(name, new LinearLayout.LayoutParams(dp(88), -2));
            view.addView(bar, lp(0, dp(36), 1f));
            view.addView(value, new LinearLayout.LayoutParams(dp(70), -2));
        }
    }

    static void gone(View v, boolean gone) { v.setVisibility(gone ? View.GONE : View.VISIBLE); }
}
