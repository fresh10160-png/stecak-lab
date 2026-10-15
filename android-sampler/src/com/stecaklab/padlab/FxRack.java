package com.stecaklab.padlab;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Presets + effect tiles + knobs + XY pad, bound to either the master chain or a pad's chain. */
final class FxRack extends LinearLayout {
    private static final class Preset {
        final String name;
        final int[] fx;
        final float[][] vals;
        Preset(String name, int[] fx, float[][] vals) { this.name = name; this.fx = fx; this.vals = vals; }
    }

    private Fx[] chain = new Fx[0];
    private int sel;
    private final FxTiles tiles;
    private final TextView title;
    private final Btn power;
    private final KnobView[] knobs = new KnobView[3];
    private final XYPad xy;
    private final TextView xyCaption;

    FxRack(Context c) {
        super(c);
        setOrientation(VERTICAL);

        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(c);
        hs.addView(chips);
        addView(hs, new LayoutParams(-1, Ui.dp(40)));
        for (final Preset p : presets()) {
            Btn b = new Btn(c, p.name).size(11);
            b.setOnClickListener(new OnClickListener() { public void onClick(View v) { apply(p); } });
            LayoutParams lp = new LayoutParams(-2, Ui.dp(34));
            lp.setMargins(0, Ui.dp(2), Ui.dp(6), 0);
            chips.addView(b, lp);
        }

        tiles = new FxTiles(c).listener(new FxTiles.Listener() {
            public void onTap(int i) {
                if (i == chain.length) {
                    for (Fx f : chain) f.enable(false);
                } else if (i == sel) {
                    chain[i].enable(!chain[i].on);
                } else {
                    sel = i;
                }
                refresh();
            }
        });
        LayoutParams tl = new LayoutParams(-1, -2);
        tl.topMargin = Ui.dp(6);
        addView(tiles, tl);

        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        title = new TextView(c);
        title.setTextColor(Ui.TEXT);
        title.setTextSize(16);
        title.setTypeface(Ui.BOLD);
        head.addView(title, new LayoutParams(0, -2, 1f));
        power = new Btn(c, "ON").led(true).accent(Ui.CYAN);
        power.setOnClickListener(new OnClickListener() {
            public void onClick(View v) { chain[sel].enable(!chain[sel].on); refresh(); }
        });
        head.addView(power, new LayoutParams(Ui.dp(84), Ui.dp(38)));
        LayoutParams hl = new LayoutParams(-1, -2);
        hl.topMargin = Ui.dp(10);
        addView(head, hl);

        LinearLayout kr = new LinearLayout(c);
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            knobs[i] = new KnobView(c, "", 0f, 1f, 0.5f).accent(Ui.CYAN).listener(new KnobView.Listener() {
                void onChange(KnobView k, float v) {
                    chain[sel].p[idx] = v;
                    syncXy();
                }
            }).fmt(new KnobView.Fmt() { public String fmt(float v) { return chain.length == 0 ? "" : chain[sel].text(idx); } });
            kr.addView(knobs[i], new LayoutParams(0, Ui.dp(88), 1f));
        }
        addView(kr, new LayoutParams(-1, -2));

        xy = new XYPad(c).listener(new XYPad.Listener() {
            public void onMove(float x, float y) {
                Fx f = chain[sel];
                f.p[0] = x;
                if (f.p.length > 1) f.p[1] = y;
                for (int i = 0; i < 3; i++) knobs[i].set(i < f.p.length ? f.p[i] : 0);
                if (!f.on) { f.enable(true); refresh(); }
            }
        });
        LayoutParams xl = new LayoutParams(-1, Ui.dp(170));
        xl.topMargin = Ui.dp(6);
        addView(xy, xl);
        xyCaption = new TextView(c);
        xyCaption.setTextColor(Ui.DIM);
        xyCaption.setTextSize(11);
        xyCaption.setGravity(Gravity.CENTER);
        addView(xyCaption, new LayoutParams(-1, -2));
    }

    void bind(Fx[] chain, String name) {
        this.chain = chain;
        if (sel >= chain.length) sel = 0;
        this.name = name;
        refresh();
    }

    private String name = "";

    private void syncXy() {
        Fx f = chain[sel];
        xy.set(f.p[0], f.p.length > 1 ? f.p[1] : 0.5f);
    }

    void refresh() {
        if (chain.length == 0) return;
        Fx f = chain[sel];
        tiles.bind(chain, sel);
        title.setText(name + "  ›  " + f.name);
        power.active(f.on).text(f.on ? "ON" : "OFF");
        for (int i = 0; i < 3; i++) {
            boolean has = i < f.labels.length;
            knobs[i].setVisibility(has ? VISIBLE : INVISIBLE);
            if (has) {
                knobs[i].label(f.labels[i]).dim(!f.on);
                knobs[i].set(f.p[i]);
            }
        }
        syncXy();
        xy.labels(f.labels[0], f.labels.length > 1 ? f.labels[1] : "");
        xyCaption.setText("Drag the pad to play " + f.labels[0] + (f.labels.length > 1 ? " × " + f.labels[1] : ""));
    }

    private void apply(Preset p) {
        for (Fx f : chain) { f.enable(false); f.reset(); }
        for (int i = 0; i < p.fx.length; i++) {
            Fx f = chain[p.fx[i]];
            System.arraycopy(p.vals[i], 0, f.p, 0, p.vals[i].length);
            f.enable(true);
        }
        if (p.fx.length > 0) sel = p.fx[0];
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
                pr("RING DRONE", new int[]{Fx.RING, Fx.RESO}, v(0.35f, 0.5f), v(0.3f, 0.85f, 0.6f)),
                pr("202 SPACE", new int[]{Fx.FLANGER, Fx.DELAY, Fx.REVERB},
                        v(0.3f, 0.55f, 0.5f), v(0.6f, 0.4f, 0.3f), v(0.8f, 0.4f, 0.4f)),
                pr("GATE CHOP", new int[]{Fx.SLICER, Fx.COMP}, v(0.5f, 1f, 0.5f), v(0.7f, 0.3f, 0.6f)),
                pr("FILTER SWEEP", new int[]{Fx.FILTER}, v(0.3f, 0.75f, 0.35f)),
        };
    }
}
