package com.stecaklab.padlab;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_PICK = 1, REQ_MIC = 2;

    final Engine engine = new Engine();
    private AudioIO audio;
    private final Handler ui = new Handler(Looper.getMainLooper());

    int bank, selected;
    boolean fullLevel = true;
    private int curTab;
    private boolean fxOnPad;

    private Lcd lcd;
    private final Btn[] bankBtns = new Btn[4], tabBtns = new Btn[5], modeBtns = new Btn[3];
    private final View[] tabs = new View[5];
    private final PadView[] padViews = new PadView[16];
    private StepGrid grid;
    private FxRack rack;
    private VoicePanel voice;
    private Btn fxMasterBtn, fxPadBtn;

    // edit tab
    private WaveView wave;
    private KnobView kVol, kPan, kPitch, kTempo, kBpm, kSwing;
    private Btn revBtn, chokeBtn, keyLockBtn, fitBtn, playBtn, dubBtn, micBtn, fullBtn;
    private TextView warpStatus;

    private volatile boolean micRecording;
    volatile float micPeak;
    private final long[] taps = new long[4];
    private int tapCount;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.density = getResources().getDisplayMetrics().density;
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        w.setStatusBarColor(Ui.BG);
        w.setNavigationBarColor(Ui.BG);
        Engine.setRate(AudioIO.nativeRate(this));
        DemoKit.load(engine);
        audio = new AudioIO(engine, AudioIO.nativeBurst(this));
        setContentView(buildUi());
        showTab(0);
        setBank(0);
    }

    private final Runnable ticker = new Runnable() {
        public void run() {
            if (curTab == 1) refreshWarpStatus();
            if (curTab == 4) voice.tick();
            ui.postDelayed(this, 90);
        }
    };

    @Override protected void onResume() {
        super.onResume();
        audio.start();
        ui.post(ticker);
    }

    @Override protected void onPause() {
        super.onPause();
        ui.removeCallbacks(ticker);
        micRecording = false;
        engine.stop();
        engine.killAll();
        updateTransport();
        audio.stop();
    }

    // ---- layout helpers ------------------------------------------------------------------
    private static LinearLayout.LayoutParams lp(int w, int h, float wt, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h, wt);
        p.setMargins(Ui.dp(l), Ui.dp(t), Ui.dp(r), Ui.dp(b));
        return p;
    }

    private static int px(int dp) { return Ui.dp(dp); }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    private TextView caption(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(Ui.DIM);
        v.setTextSize(11);
        v.setTypeface(Ui.BOLD);
        v.setPadding(0, px(10), 0, px(4));
        return v;
    }

    // ---- UI construction -----------------------------------------------------------------
    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(px(10), px(8), px(10), px(8));

        lcd = new Lcd(this, this);
        root.addView(lcd, lp(-1, px(88), 0, 0, 0, 0, 0));

        LinearLayout banks = row();
        for (int i = 0; i < 4; i++) {
            final int bk = i;
            Btn bb = new Btn(this, "BANK " + (char) ('A' + i)).size(11).accent(Ui.BANK[i]);
            bb.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { setBank(bk); } });
            bankBtns[i] = bb;
            banks.addView(bb, lp(0, px(36), 1f, i == 0 ? 0 : 3, 0, i == 3 ? 0 : 3, 0));
        }
        root.addView(banks, lp(-1, -2, 0, 0, 8, 0, 0));

        LinearLayout bar = row();
        String[] names = {"PADS", "EDIT", "FX", "SEQ", "VOICE"};
        for (int i = 0; i < 5; i++) {
            final int t = i;
            Btn tb = new Btn(this, names[i]).size(11.5f).accent(Ui.TEXT);
            tb.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showTab(t); } });
            tabBtns[i] = tb;
            bar.addView(tb, lp(0, px(42), 1f, i == 0 ? 0 : 2, 0, i == 4 ? 0 : 2, 0));
        }
        root.addView(bar, lp(-1, -2, 0, 0, 6, 0, 0));

        FrameLayout content = new FrameLayout(this);
        tabs[0] = buildPads();
        tabs[1] = buildEdit();
        tabs[2] = buildFx();
        tabs[3] = buildSeq();
        voice = new VoicePanel(this, this);
        tabs[4] = voice;
        for (View v : tabs) content.addView(v, new FrameLayout.LayoutParams(-1, -1));
        root.addView(content, lp(-1, 0, 1f, 0, 8, 0, 0));

        root.addView(buildTransport(), lp(-1, px(80), 0, 0, 6, 0, 0));
        return root;
    }

    private View buildPads() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        for (int r = 0; r < 4; r++) {
            LinearLayout line = new LinearLayout(this);
            for (int c = 0; c < 4; c++) {
                int idx = (3 - r) * 4 + c; // pad 1 bottom-left like an MPC
                PadView pv = new PadView(this, this, idx);
                padViews[idx] = pv;
                line.addView(pv, lp(0, -1, 1f, 0, 0, 0, 0));
            }
            col.addView(line, lp(-1, 0, 1f, 0, 0, 0, 0));
        }
        fullBtn = new Btn(this, "FULL LEVEL").size(11).led(true);
        fullBtn.active(true);
        fullBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                fullLevel = !fullLevel;
                fullBtn.active(fullLevel).text(fullLevel ? "FULL LEVEL" : "VELOCITY: TOUCH HEIGHT");
            }
        });
        col.addView(fullBtn, lp(-1, px(36), 0, 0, 6, 0, 0));
        return col;
    }

    private View buildEdit() {
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        sv.addView(col, new FrameLayout.LayoutParams(-1, -2));

        wave = new WaveView(this).listener(new WaveView.Listener() {
            public void onTrim(float s, float e, boolean finished) {
                Pad p = engine.pads[selected];
                p.start = s;
                p.end = e;
                if (finished) { engine.requestWarp(selected); refreshEdit(); }
            }
        });
        col.addView(wave, lp(-1, px(140), 0, 0, 0, 0, 0));

        col.addView(caption("PLAY MODE"));
        LinearLayout modes = row();
        String[] mn = Pad.MODES;
        for (int i = 0; i < 3; i++) {
            final int m = i;
            modeBtns[i] = new Btn(this, mn[i]).size(11.5f);
            modeBtns[i].setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { engine.pads[selected].mode = m; refreshEdit(); }
            });
            modes.addView(modeBtns[i], lp(0, px(38), 1f, i == 0 ? 0 : 3, 0, i == 2 ? 0 : 3, 0));
        }
        col.addView(modes);

        LinearLayout opts = row();
        revBtn = new Btn(this, "REVERSE").size(11.5f).led(true);
        chokeBtn = new Btn(this, "CHOKE OFF").size(11.5f);
        Btn padFx = new Btn(this, "PAD FX ›").size(11.5f).accent(Ui.CYAN);
        revBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { Pad p = engine.pads[selected]; p.reverse = !p.reverse; refreshEdit(); }
        });
        chokeBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { Pad p = engine.pads[selected]; p.choke = (p.choke + 1) % 5; refreshEdit(); }
        });
        padFx.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { fxOnPad = true; showTab(2); }
        });
        opts.addView(revBtn, lp(0, px(38), 1f, 0, 0, 3, 0));
        opts.addView(chokeBtn, lp(0, px(38), 1f, 3, 0, 3, 0));
        opts.addView(padFx, lp(0, px(38), 1f, 3, 0, 0, 0));
        col.addView(opts, lp(-1, -2, 0, 0, 6, 0, 0));

        col.addView(caption("LEVEL & PITCH"));
        LinearLayout knobs = row();
        kVol = new KnobView(this, "Volume", 0f, 1.5f, 0.9f).listener(new KnobView.Listener() {
            void onChange(KnobView k, float v) { engine.pads[selected].vol = v; }
        });
        kPan = new KnobView(this, "Pan", -1f, 1f, 0f).bipolar(true).fmt(new KnobView.Fmt() {
            public String fmt(float v) {
                int n = Math.round(Math.abs(v) * 100);
                return n < 3 ? "C" : (v < 0 ? "L" : "R") + n;
            }
        }).listener(new KnobView.Listener() {
            void onChange(KnobView k, float v) { engine.pads[selected].pan = v; }
        });
        kPitch = new KnobView(this, "Pitch", -24f, 24f, 0f).step(1f).bipolar(true).fmt(new KnobView.Fmt() {
            public String fmt(float v) { return (v > 0 ? "+" : "") + Math.round(v) + "st"; }
        }).listener(new KnobView.Listener() {
            void onChange(KnobView k, float v) { engine.pads[selected].pitch = Math.round(v); }
            void onEnd(KnobView k) { engine.requestWarp(selected); refreshEdit(); }
        });
        kTempo = new KnobView(this, "Tempo", 0.5f, 2f, 1f).accent(Ui.CYAN).fmt(new KnobView.Fmt() {
            public String fmt(float v) { return Math.round(v * 100) + "%"; }
        }).listener(new KnobView.Listener() {
            void onChange(KnobView k, float v) { engine.pads[selected].tempo = v; }
            void onEnd(KnobView k) { engine.requestWarp(selected); refreshEdit(); }
        });
        knobs.addView(kVol, lp(0, px(88), 1f, 0, 0, 0, 0));
        knobs.addView(kPan, lp(0, px(88), 1f, 0, 0, 0, 0));
        knobs.addView(kPitch, lp(0, px(88), 1f, 0, 0, 0, 0));
        knobs.addView(kTempo, lp(0, px(88), 1f, 0, 0, 0, 0));
        col.addView(knobs);

        col.addView(caption("TIME-STRETCH  (pitch-preserving)"));
        LinearLayout warp = row();
        keyLockBtn = new Btn(this, "KEY LOCK").size(11.5f).led(true).accent(Ui.CYAN);
        fitBtn = new Btn(this, "FIT: OFF").size(11.5f).accent(Ui.CYAN);
        keyLockBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Pad p = engine.pads[selected];
                p.keyLock = !p.keyLock;
                engine.requestWarp(selected);
                refreshEdit();
            }
        });
        fitBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Pad p = engine.pads[selected];
                int i = 0;
                for (int k = 0; k < Pad.FIT.length; k++) if (Pad.FIT[k] == p.fitBeats) i = k;
                p.fitBeats = Pad.FIT[(i + 1) % Pad.FIT.length];
                engine.requestWarp(selected);
                refreshEdit();
            }
        });
        warpStatus = new TextView(this);
        warpStatus.setTextColor(Ui.DIM);
        warpStatus.setTextSize(11);
        warpStatus.setTypeface(Ui.MONO);
        warpStatus.setGravity(Gravity.CENTER);
        warp.addView(keyLockBtn, lp(0, px(38), 1f, 0, 0, 3, 0));
        warp.addView(fitBtn, lp(0, px(38), 1.2f, 3, 0, 3, 0));
        warp.addView(warpStatus, lp(0, -2, 1f, 3, 0, 0, 0));
        col.addView(warp);

        LinearLayout acts = row();
        Btn load = new Btn(this, "LOAD FILE").size(11.5f);
        Btn clear = new Btn(this, "CLEAR PAD").size(11.5f);
        Btn prev = new Btn(this, "PREVIEW").size(11.5f).icon(Btn.PLAY).accent(Ui.GREEN);
        load.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickFile(); } });
        clear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Pad p = engine.pads[selected];
                p.sample = null; p.name = ""; p.start = 0; p.end = 1; p.pitch = 0; p.tempo = 1f;
                p.fitBeats = 0; p.pan = 0; p.vol = 0.9f; p.rendered = null; p.renderedKey = null; p.pendingKey = null;
                refreshAll();
            }
        });
        prev.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent e) {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN) engine.trigger(selected, 1f);
                else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) engine.release(selected);
                return false;
            }
        });
        acts.addView(load, lp(0, px(40), 1f, 0, 0, 3, 0));
        acts.addView(clear, lp(0, px(40), 1f, 3, 0, 3, 0));
        acts.addView(prev, lp(0, px(40), 1f, 3, 0, 0, 0));
        col.addView(acts, lp(-1, -2, 0, 0, 12, 0, 12));
        return sv;
    }

    private View buildFx() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout target = row();
        fxMasterBtn = new Btn(this, "MASTER BUS").size(11.5f).accent(Ui.CYAN);
        fxPadBtn = new Btn(this, "SELECTED PAD").size(11.5f).accent(Ui.CYAN);
        fxMasterBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { fxOnPad = false; rebindRack(); }
        });
        fxPadBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { fxOnPad = true; rebindRack(); }
        });
        target.addView(fxMasterBtn, lp(0, px(38), 1f, 0, 0, 3, 0));
        target.addView(fxPadBtn, lp(0, px(38), 1f, 3, 0, 0, 0));
        col.addView(target);

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        rack = new FxRack(this);
        sv.addView(rack, new FrameLayout.LayoutParams(-1, -2));
        col.addView(sv, lp(-1, 0, 1f, 0, 6, 0, 0));
        return col;
    }

    private View buildSeq() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        grid = new StepGrid(this, this);
        col.addView(grid, lp(-1, 0, 1f, 0, 0, 0, 0));

        LinearLayout r = row();
        kBpm = new KnobView(this, "BPM", 40f, 220f, 90f).step(1f).fmt(new KnobView.Fmt() {
            public String fmt(float v) { return String.valueOf(Math.round(v)); }
        }).listener(new KnobView.Listener() {
            void onChange(KnobView k, float v) { engine.bpm = v; }
            void onEnd(KnobView k) { engine.rewarpAll(); }
        });
        kSwing = new KnobView(this, "Swing", 0f, 1f, 0.3f).listener(new KnobView.Listener() {
            void onChange(KnobView k, float v) { engine.swing = v; }
        });
        kBpm.set(engine.bpm);
        kSwing.set(engine.swing);
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.VERTICAL);
        Btn tap = new Btn(this, "TAP TEMPO").size(11);
        Btn clrRow = new Btn(this, "CLEAR PAD").size(11);
        Btn clrAll = new Btn(this, "CLEAR ALL").size(11);
        tap.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { tapTempo(); } });
        clrRow.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { java.util.Arrays.fill(engine.steps[selected], false); grid.invalidate(); }
        });
        clrAll.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { for (boolean[] s : engine.steps) java.util.Arrays.fill(s, false); grid.invalidate(); }
        });
        btns.addView(tap, lp(-1, px(30), 0, 0, 0, 0, 3));
        btns.addView(clrRow, lp(-1, px(30), 0, 0, 0, 0, 3));
        btns.addView(clrAll, lp(-1, px(30), 0, 0, 0, 0, 0));
        r.addView(kBpm, lp(0, px(96), 1f, 0, 0, 0, 0));
        r.addView(kSwing, lp(0, px(96), 1f, 0, 0, 0, 0));
        r.addView(btns, lp(0, -2, 1.5f, 6, 0, 0, 0));
        col.addView(r, lp(-1, -2, 0, 0, 6, 0, 0));
        return col;
    }

    private View buildTransport() {
        LinearLayout t = row();
        playBtn = new Btn(this, "PLAY").icon(Btn.PLAY).accent(Ui.GREEN);
        dubBtn = new Btn(this, "OVERDUB").size(11.5f).icon(Btn.REC).accent(Ui.REC);
        micBtn = new Btn(this, "MIC").icon(Btn.MIC).accent(Ui.REC);
        playBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (engine.playing) engine.stop(); else engine.play();
                updateTransport();
            }
        });
        dubBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { engine.overdub = !engine.overdub; updateTransport(); }
        });
        micBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { toggleMic(); } });
        KnobView master = new KnobView(this, "Master", 0f, 1f, 0.8f).listener(new KnobView.Listener() {
            void onChange(KnobView k, float v) { engine.master = v; }
        });
        master.set(engine.master);
        t.addView(playBtn, lp(0, px(56), 1.15f, 0, 0, 3, 0));
        t.addView(dubBtn, lp(0, px(56), 1.25f, 3, 0, 3, 0));
        t.addView(micBtn, lp(0, px(56), 0.95f, 3, 0, 0, 0));
        t.addView(master, lp(px(78), -1, 0, 4, 0, 0, 0));
        updateTransport();
        return t;
    }

    // ---- state ---------------------------------------------------------------------------
    private void showTab(int t) {
        curTab = t;
        for (int i = 0; i < 5; i++) {
            tabs[i].setVisibility(i == t ? View.VISIBLE : View.GONE);
            tabBtns[i].active(i == t);
        }
        if (t == 1) refreshEdit();
        if (t == 2) rebindRack();
        if (t == 3) grid.invalidate();
    }

    void setBank(int b) {
        bank = b;
        selected = b * 16 + selected % 16;
        for (int i = 0; i < 4; i++) bankBtns[i].active(i == b);
        refreshAll();
    }

    void select(int id) {
        if (selected == id) return;
        selected = id;
        if (curTab == 1) refreshEdit();
        else if (curTab == 2) rebindRack();
        for (PadView p : padViews) p.invalidate();
        if (grid != null) grid.invalidate();
    }

    private String padTag() { return padTag(selected); }

    String padTag(int id) {
        return "PAD " + (char) ('A' + id / 16) + String.format("%02d", id % 16 + 1);
    }

    /** Called when something outside the edit tab replaced a pad's sample. */
    void padChanged() { refreshAll(); }

    private void rebindRack() {
        fxMasterBtn.active(!fxOnPad);
        fxPadBtn.active(fxOnPad).text(fxOnPad ? padTag() : "SELECTED PAD");
        if (fxOnPad) rack.bind(engine.chainFor(selected), padTag());
        else rack.bind(engine.fx, "MASTER");
    }

    void refreshAll() {
        for (PadView p : padViews) p.invalidate();
        refreshEdit();
        if (curTab == 2) rebindRack();
        if (grid != null) grid.invalidate();
    }

    private void refreshEdit() {
        if (kVol == null) return;
        Pad p = engine.pads[selected];
        kVol.set(p.vol);
        kPan.set(p.pan);
        kPitch.set(p.pitch);
        kTempo.set(p.tempo);
        kTempo.dim(p.fitBeats > 0);
        for (int i = 0; i < 3; i++) modeBtns[i].active(p.mode == i);
        revBtn.active(p.reverse);
        chokeBtn.text(p.choke == 0 ? "CHOKE OFF" : "CHOKE " + p.choke).active(p.choke > 0);
        keyLockBtn.active(p.keyLock);
        fitBtn.text(p.fitBeats == 0 ? "FIT: OFF" : "FIT: " + p.fitBeats + (p.fitBeats == 1 ? " BEAT" : " BEATS"))
                .active(p.fitBeats > 0);
        refreshWarpStatus();
    }

    private void refreshWarpStatus() {
        if (wave == null) return;
        Pad p = engine.pads[selected];
        boolean busy = engine.warpBusy.get() > 0;
        wave.set(p.sample, p.start, p.end, Ui.BANK[selected / 16], busy);
        warpStatus.setText(busy ? "RENDERING…" : (p.sample != null && p.warped() && p.rendered != null ? "STRETCHED" : "NO STRETCH"));
        warpStatus.setTextColor(busy ? Ui.CYAN : Ui.DIM);
    }

    private void updateTransport() {
        playBtn.text(engine.playing ? "STOP" : "PLAY").icon(engine.playing ? Btn.STOP : Btn.PLAY).active(engine.playing);
        dubBtn.active(engine.overdub);
        micBtn.active(micRecording).text(micRecording ? "STOP" : "MIC");
        if (voice != null) voice.onMicState(micRecording);
    }

    private void tapTempo() {
        long now = System.currentTimeMillis();
        if (tapCount > 0 && now - taps[(tapCount - 1) % taps.length] > 2000) tapCount = 0;
        taps[tapCount % taps.length] = now;
        tapCount++;
        int n = Math.min(tapCount, taps.length);
        if (n >= 2) {
            long first = taps[(tapCount - n) % taps.length], last = taps[(tapCount - 1) % taps.length];
            float bpm = 60000f * (n - 1) / (last - first);
            bpm = Math.max(40, Math.min(220, bpm));
            engine.bpm = bpm;
            kBpm.set(bpm);
        }
    }

    // ---- sample loading ------------------------------------------------------------------
    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("audio/*");
        startActivityForResult(i, REQ_PICK);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        if (req != REQ_PICK || res != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        final int target = selected;
        Toast.makeText(this, "Loading…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            public void run() {
                try {
                    final Sample s = Loader.load(MainActivity.this, uri);
                    ui.post(new Runnable() { public void run() { assign(target, s); } });
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        public void run() {
                            Toast.makeText(MainActivity.this, "Load failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void assign(int pad, Sample s) {
        Pad p = engine.pads[pad];
        p.sample = s;
        p.name = s.name;
        p.start = 0f;
        p.end = 1f;
        p.rendered = null;
        p.renderedKey = null;
        engine.requestWarp(pad);
        refreshAll();
    }

    // ---- microphone sampling -------------------------------------------------------------
    interface MicDone { void done(Sample s); }

    private MicDone pendingMic;

    boolean isMicRecording() { return micRecording; }

    void stopMic() { micRecording = false; }

    private void toggleMic() {
        if (micRecording) { micRecording = false; return; }
        final int target = selected;
        recordMic(new MicDone() {
            public void done(Sample s) { if (s != null) assign(target, s); }
        });
    }

    /** Records from the mic until stopMic(); the result (leading silence trimmed) arrives on the UI thread. */
    void recordMic(MicDone done) {
        if (micRecording) return;
        pendingMic = done;
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        startMic();
    }

    @Override public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        if (req == REQ_MIC && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startMic();
        else if (req == REQ_MIC) Toast.makeText(this, "Microphone permission denied", Toast.LENGTH_SHORT).show();
    }

    private void startMic() {
        final MicDone done = pendingMic;
        micRecording = true;
        updateTransport();
        new Thread(new Runnable() {
            public void run() {
                int rate = Engine.SR;
                int min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                AudioRecord ar = new AudioRecord(MediaRecorder.AudioSource.MIC, rate,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min, 16384));
                short[] all = new short[rate * 10];
                int n = 0;
                final int max = rate * 60;
                if (ar.getState() == AudioRecord.STATE_INITIALIZED) {
                    short[] chunk = new short[2048];
                    ar.startRecording();
                    while (micRecording && n < max) {
                        int r = ar.read(chunk, 0, chunk.length);
                        if (r <= 0) break;
                        int pk = 0;
                        for (int q = 0; q < r; q++) pk = Math.max(pk, Math.abs(chunk[q]));
                        micPeak = Math.max(pk / 32768f, micPeak * 0.85f);
                        if (n + r > all.length) all = java.util.Arrays.copyOf(all, Math.min(max, all.length * 2) + r);
                        System.arraycopy(chunk, 0, all, n, r);
                        n += r;
                    }
                    ar.stop();
                }
                ar.release();
                micRecording = false;
                micPeak = 0f;
                final int len = n;
                final short[] data = all;
                ui.post(new Runnable() {
                    public void run() {
                        updateTransport();
                        if (len < 1000) {
                            Toast.makeText(MainActivity.this, "Nothing recorded — is the microphone allowed and free?", Toast.LENGTH_LONG).show();
                            if (done != null) done.done(null);
                            return;
                        }
                        int from = 0;
                        while (from < len - 1 && Math.abs(data[from]) < 600) from++; // trim leading silence
                        float[] f = new float[len - from];
                        for (int i = 0; i < f.length; i++) f[i] = data[from + i] / 32768f;
                        Sample s = new Sample("REC", f, f);
                        if (done != null) done.done(s);
                    }
                });
            }
        }).start();
    }
}
