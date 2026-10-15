package com.stecaklab.padlab;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;

import java.util.ArrayList;
import java.util.List;

/** Preset chips + one card per effect (on/off + up to three knobs). */
final class FxPanel extends ScrollView {
    private static final class Preset {
        final String name;
        final int[] fx;
        final float[][] vals;
        Preset(String name, int[] fx, float[][] vals) { this.name = name; this.fx = fx; this.vals = vals; }
    }

    private final Engine engine;
    private final List<Runnable> refreshers = new ArrayList<Runnable>();

    FxPanel(Context c, Engine e) {
        super(c);
        engine = e;
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(8), Ui.dp(4), Ui.dp(8), Ui.dp(12));
        addView(col, new LayoutParams(-1, -2));

        col.addView(Ui.label(c, "PRESETS  (SP-303 / SP-202 inspired)", 11, Ui.DIM));
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(c);
        hs.addView(chips);
        col.addView(hs);
        for (final Preset p : presets()) {
            Button b = Ui.button(c, p.name);
            b.setOnClickListener(new OnClickListener() { public void onClick(View v) { apply(p); } });
            chips.addView(b, Ui.margin(new LinearLayout.LayoutParams(-2, -2), 0, 4, 6, 6));
        }

        for (Fx f : engine.fx) col.addView(card(c, f), Ui.margin(new LinearLayout.LayoutParams(-1, -2), 0, 4, 0, 4));
    }

    private View card(final Context c, final Fx f) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.rounded(Ui.PANEL, 10));
        card.setPadding(Ui.dp(10), Ui.dp(6), Ui.dp(10), Ui.dp(6));

        final Button toggle = Ui.button(c, f.name);
        toggle.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        toggle.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                f.on = !f.on;
                Ui.active(toggle, f.on);
            }
        });
        card.addView(toggle, new LinearLayout.LayoutParams(-1, -2));

        final Ui.Row[] rows = new Ui.Row[f.labels.length];
        for (int i = 0; i < rows.length; i++) {
            final int idx = i;
            final Ui.Row row = new Ui.Row(c, f.labels[i], 100);
            rows[i] = row;
            row.bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar s, int v, boolean user) {
                    if (user) f.p[idx] = v / 100f;
                    row.value.setText(f.text(idx));
                }
                public void onStartTrackingTouch(SeekBar s) { }
                public void onStopTrackingTouch(SeekBar s) { }
            });
            card.addView(row.view);
        }
        refreshers.add(new Runnable() {
            public void run() {
                Ui.active(toggle, f.on);
                for (int i = 0; i < rows.length; i++) {
                    rows[i].bar.setProgress(Math.round(f.p[i] * 100));
                    rows[i].value.setText(f.text(i));
                }
            }
        });
        return card;
    }

    void refresh() { for (Runnable r : refreshers) r.run(); }

    private void apply(Preset p) {
        for (Fx f : engine.fx) { f.on = false; f.reset(); }
        for (int i = 0; i < p.fx.length; i++) {
            Fx f = engine.fx[p.fx[i]];
            System.arraycopy(p.vals[i], 0, f.p, 0, p.vals[i].length);
            f.on = true;
        }
        refresh();
    }

    private static Preset pr(String n, int[] fx, float[]... v) { return new Preset(n, fx, v); }

    private static float[] v(float... a) { return a; }

    private static Preset[] presets() {
        return new Preset[]{
                pr("CLEAN", new int[]{}),
                pr("303 VINYL", new int[]{Fx.COMP, Fx.FILTER, Fx.VINYL},
                        v(0.5f, 0.35f, 0.55f), v(0.82f, 0.15f, 0.1f), v(0.4f, 0.5f, 0.5f)),
                pr("DUSTY LO-FI", new int[]{Fx.LOFI, Fx.FILTER, Fx.VINYL},
                        v(0.35f, 0.4f, 0.35f), v(0.68f, 0.2f, 0.25f), v(0.55f, 0.65f, 0.4f)),
                pr("TAPE ECHO", new int[]{Fx.FILTER, Fx.DELAY, Fx.REVERB},
                        v(0.75f, 0.1f, 0.1f), v(0.5f, 0.65f, 0.4f), v(0.45f, 0.5f, 0.2f)),
                pr("ISO: NO LOW", new int[]{Fx.ISOLATOR}, v(0f, 1f, 1f)),
                pr("ISO: MID ONLY", new int[]{Fx.ISOLATOR}, v(0f, 1f, 0f)),
                pr("RING DRONE", new int[]{Fx.RING, Fx.RESO},
                        v(0.35f, 0.5f), v(0.3f, 0.85f, 0.6f)),
                pr("202 SPACE", new int[]{Fx.FLANGER, Fx.DELAY, Fx.REVERB},
                        v(0.3f, 0.55f, 0.5f), v(0.6f, 0.4f, 0.3f), v(0.8f, 0.4f, 0.4f)),
                pr("GATE CHOP", new int[]{Fx.SLICER, Fx.COMP},
                        v(0.5f, 1f, 0.5f), v(0.7f, 0.3f, 0.6f)),
                pr("FILTER SWEEP", new int[]{Fx.FILTER}, v(0.3f, 0.75f, 0.35f)),
        };
    }
}
