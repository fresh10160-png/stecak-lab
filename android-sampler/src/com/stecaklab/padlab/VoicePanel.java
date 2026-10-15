package com.stecaklab.padlab;

import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Sing -> notes -> pick an instrument. The rendered melody is written straight into a pad. */
final class VoicePanel extends ScrollView {
    private final MainActivity act;
    private final Btn singBtn, snapBtn, targetBtn;
    private final Btn[] chips = new Btn[Synth.NAMES.length];
    private final TextView status;
    private final RollView roll;
    private final KnobView kKey, kScale, kOct, kRoot;

    private Melody melody;
    private int inst = 2, root, scale, octave, sampleRoot = 60, selNote = -1, target;
    private boolean snap = true;
    private Sample sampleSrc, lastRender;
    private int gen;
    private long recStart;

    VoicePanel(Context c, MainActivity activity) {
        super(c);
        act = activity;
        setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        addView(col, new FrameLayout.LayoutParams(-1, -2));
        target = act.selected;

        LinearLayout top = new LinearLayout(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        singBtn = new Btn(c, "SING").icon(Btn.MIC).accent(Ui.REC);
        singBtn.setOnClickListener(new OnClickListener() { public void onClick(View v) { toggleSing(); } });
        status = new TextView(c);
        status.setTextColor(Ui.DIM);
        status.setTextSize(12);
        status.setTypeface(Ui.MONO);
        status.setText("Tap SING, hum or sing a melody, tap STOP.");
        top.addView(singBtn, lp(Ui.dp(120), Ui.dp(54), 0, 0, 0, 10, 0));
        top.addView(status, lp(0, -2, 1f, 0, 0, 0, 0));
        col.addView(top);

        HorizontalScrollView rs = new HorizontalScrollView(c);
        rs.setHorizontalScrollBarEnabled(false);
        roll = new RollView(c).listener(new RollView.Listener() {
            public void onSelect(int i) { selNote = i; refreshRoll(); }
        });
        rs.addView(roll);
        col.addView(rs, lp(-1, Ui.dp(160), 0, 0, 10, 0, 0));

        col.addView(caption("INSTRUMENT"));
        HorizontalScrollView cs = new HorizontalScrollView(c);
        cs.setHorizontalScrollBarEnabled(false);
        LinearLayout chipRow = new LinearLayout(c);
        cs.addView(chipRow);
        for (int i = 0; i < Synth.NAMES.length; i++) {
            final int idx = i;
            chips[i] = new Btn(c, Synth.NAMES[i]).size(11.5f).accent(Ui.CYAN);
            chips[i].setOnClickListener(new OnClickListener() { public void onClick(View v) { pick(idx); } });
            chipRow.addView(chips[i], lp(-2, Ui.dp(40), 0, 0, 0, 6, 0));
        }
        col.addView(cs);

        col.addView(caption("KEY, SCALE & RANGE"));
        LinearLayout knobs = new LinearLayout(c);
        kKey = new KnobView(c, "Key", 0f, 11f, 0f).step(1f).fmt(new KnobView.Fmt() {
            public String fmt(float v) { return Melody.KEYS[Math.round(v)]; }
        }).listener(new KnobView.Listener() {
            void onChange(KnobView k, float v) { root = Math.round(v); }
            void onEnd(KnobView k) { rerender(true); }
        });
        kScale = new KnobView(c, "Scale", 0f, Melody.SCALE_NAMES.length - 1, 0f).step(1f).fmt(new KnobView.Fmt() {
            public String fmt(float v) { return Melody.SCALE_NAMES[Math.round(v)].replace("CHROMATIC", "CHROM"); }
        }).listener(new KnobView.Listener() {
            void onChange(KnobView k, float v) { scale = Math.round(v); }
            void onEnd(KnobView k) { rerender(true); }
        });
        kOct = new KnobView(c, "Octave", -2f, 2f, 0f).step(1f).bipolar(true).fmt(new KnobView.Fmt() {
            public String fmt(float v) { int o = Math.round(v); return o > 0 ? "+" + o : String.valueOf(o); }
        }).listener(new KnobView.Listener() {
            void onChange(KnobView k, float v) { octave = Math.round(v); }
            void onEnd(KnobView k) { rerender(true); }
        });
        kRoot = new KnobView(c, "Smp root", 36f, 84f, 60f).step(1f).accent(Ui.CYAN).fmt(new KnobView.Fmt() {
            public String fmt(float v) { return Melody.noteName(Math.round(v)); }
        }).listener(new KnobView.Listener() {
            void onChange(KnobView k, float v) { sampleRoot = Math.round(v); }
            void onEnd(KnobView k) { if (inst == Synth.SAMPLE) rerender(true); }
        });
        for (KnobView k : new KnobView[]{kKey, kScale, kOct, kRoot}) knobs.addView(k, lp(0, Ui.dp(88), 1f, 0, 0, 0, 0));
        col.addView(knobs);

        LinearLayout edit = new LinearLayout(c);
        snapBtn = new Btn(c, "SNAP 1/16").size(11.5f).led(true);
        snapBtn.active(true);
        snapBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View v) { snap = !snap; snapBtn.active(snap); rerender(true); }
        });
        Btn down = new Btn(c, "NOTE −1").size(11.5f), up = new Btn(c, "NOTE +1").size(11.5f), del = new Btn(c, "DELETE").size(11.5f);
        down.setOnClickListener(new OnClickListener() { public void onClick(View v) { editNote(-1, false); } });
        up.setOnClickListener(new OnClickListener() { public void onClick(View v) { editNote(1, false); } });
        del.setOnClickListener(new OnClickListener() { public void onClick(View v) { editNote(0, true); } });
        edit.addView(snapBtn, lp(0, Ui.dp(38), 1.3f, 0, 0, 3, 0));
        edit.addView(down, lp(0, Ui.dp(38), 1f, 3, 0, 3, 0));
        edit.addView(up, lp(0, Ui.dp(38), 1f, 3, 0, 3, 0));
        edit.addView(del, lp(0, Ui.dp(38), 1f, 3, 0, 0, 0));
        col.addView(edit, lp(-1, -2, 0, 0, 6, 0, 0));

        LinearLayout out = new LinearLayout(c);
        targetBtn = new Btn(c, "").size(11.5f).accent(Ui.GREEN);
        targetBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View v) { target = act.selected; updateTarget(); rerender(false); }
        });
        Btn play = new Btn(c, "PLAY").icon(Btn.PLAY).accent(Ui.GREEN);
        play.setOnTouchListener(new OnTouchListener() {
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) act.engine.trigger(target, 1f);
                return false;
            }
        });
        out.addView(targetBtn, lp(0, Ui.dp(44), 2f, 0, 0, 3, 0));
        out.addView(play, lp(0, Ui.dp(44), 1f, 3, 0, 0, 0));
        col.addView(out, lp(-1, -2, 0, 0, 8, 0, 0));

        TextView hint = new TextView(c);
        hint.setTextColor(Ui.DIM);
        hint.setTextSize(11);
        hint.setText("The result is written into the target pad, so you can still trim it, stretch it, add pad FX and sequence it. "
                + "SAMPLE plays any other pad's sample as an instrument — select that pad first, then tap SAMPLE.");
        hint.setPadding(0, Ui.dp(10), 0, Ui.dp(16));
        col.addView(hint);

        updateChips();
        updateTarget();
    }

    private static LinearLayout.LayoutParams lp(int w, int h, float wt, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h, wt);
        p.setMargins(Ui.dp(l), Ui.dp(t), Ui.dp(r), Ui.dp(b));
        return p;
    }

    private TextView caption(String t) {
        TextView v = new TextView(getContext());
        v.setText(t);
        v.setTextColor(Ui.DIM);
        v.setTextSize(11);
        v.setTypeface(Ui.BOLD);
        v.setPadding(0, Ui.dp(12), 0, Ui.dp(4));
        return v;
    }

    // ---- recording & analysis ------------------------------------------------------------
    void onMicState(boolean recording) {
        singBtn.active(recording).text(recording ? "STOP" : "SING");
        if (recording) status.setText("REC … sing a melody");
    }

    void tick() { // called by the activity ticker while this tab is visible
        if (act.isMicRecording()) status.setText(String.format("REC  %.1fs", (System.currentTimeMillis() - recStart) / 1000.0));
        updateTarget();
    }

    private void toggleSing() {
        if (act.isMicRecording()) { act.stopMic(); return; }
        target = act.selected;
        updateTarget();
        recStart = System.currentTimeMillis();
        act.recordMic(new MainActivity.MicDone() {
            public void done(final Sample s) {
                if (s == null) return;
                status.setText("Analysing…");
                new Thread(new Runnable() {
                    public void run() {
                        final Melody m = Melody.analyze(s);
                        act.runOnUiThread(new Runnable() { public void run() { gotMelody(m); } });
                    }
                }).start();
            }
        });
    }

    private void gotMelody(Melody m) {
        if (m.notes.isEmpty()) {
            Toast.makeText(act, "No clear pitch found — sing louder or closer to the mic", Toast.LENGTH_LONG).show();
            status.setText("No notes detected. Try again.");
            return;
        }
        melody = m;
        selNote = -1;
        status.setText(m.notes.size() + " notes detected");
        rerender(true);
    }

    // ---- shaping & rendering -------------------------------------------------------------
    private void pick(int i) {
        if (i == Synth.SAMPLE) {
            Sample cand = act.engine.pads[act.selected].sample;
            if (cand != null && cand != lastRender) sampleSrc = cand;
            if (sampleSrc == null) {
                Toast.makeText(act, "Select a pad that holds a sample first, then tap SAMPLE", Toast.LENGTH_LONG).show();
                return;
            }
        }
        inst = i;
        updateChips();
        rerender(true);
    }

    private void updateChips() {
        for (int i = 0; i < chips.length; i++) chips[i].active(i == inst);
        kRoot.dim(inst != Synth.SAMPLE);
    }

    private void updateTarget() {
        targetBtn.text("→ " + act.padTag(target) + "   (tap: use selected pad)");
    }

    private void editNote(int dShift, boolean delete) {
        if (melody == null || selNote < 0 || selNote >= melody.notes.size()) {
            Toast.makeText(act, "Tap a note in the roll first", Toast.LENGTH_SHORT).show();
            return;
        }
        Melody.Note n = melody.notes.get(selNote);
        if (delete) { n.deleted = true; selNote = -1; } else n.shift += dShift;
        rerender(true);
    }

    private void refreshRoll() {
        if (melody == null) return;
        roll.set(melody.build(root, scale, octave, snap, act.engine.bpm), snap ? 60.0 / act.engine.bpm / 4 : 0, selNote);
    }

    /** Re-shape the notes, redraw the roll, and render the chosen instrument into the target pad. */
    void rerender(final boolean audition) {
        if (melody == null) return;
        final List<Melody.Out> notes = melody.build(root, scale, octave, snap, act.engine.bpm);
        roll.set(notes, snap ? 60.0 / act.engine.bpm / 4 : 0, selNote);
        if (notes.isEmpty()) { status.setText("All notes deleted"); return; }
        final int myGen = ++gen, ins = inst, smpRoot = sampleRoot;
        final boolean sn = snap;
        final double bpm = act.engine.bpm;
        final Sample rec = melody.recording, src = sampleSrc;
        status.setText("Rendering " + Synth.NAMES[ins] + "…");
        new Thread(new Runnable() {
            public void run() {
                final Sample out = Synth.render(new ArrayList<Melody.Out>(notes), rec, ins, src, smpRoot, sn, bpm);
                act.runOnUiThread(new Runnable() {
                    public void run() {
                        if (myGen != gen || out == null) return;
                        lastRender = out;
                        Pad p = act.engine.pads[target];
                        p.sample = out;
                        p.name = Synth.NAMES[ins];
                        p.start = 0f;
                        p.end = 1f;
                        p.mode = Pad.ONE_SHOT;
                        p.reverse = false;
                        p.pitch = 0;
                        p.tempo = 1f;
                        p.fitBeats = 0;
                        p.rendered = null;
                        p.renderedKey = null;
                        act.padChanged();
                        status.setText(notes.size() + " notes · " + Synth.NAMES[ins] + " → " + act.padTag(target));
                        if (audition) act.engine.trigger(target, 1f);
                    }
                });
            }
        }).start();
    }
}
