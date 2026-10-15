package com.stecaklab.padlab;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_PICK = 1, REQ_MIC = 2;

    final Engine engine = new Engine();
    private AudioIO audio;
    private final Handler ui = new Handler(Looper.getMainLooper());

    int bank, selected;
    boolean fullLevel = true;

    private final Button[] tabButtons = new Button[4], bankButtons = new Button[4];
    private final View[] tabs = new View[4];
    private final PadView[] padViews = new PadView[16];
    private StepGrid grid;
    private FxPanel fxPanel;
    private TextView padInfo, bpmText;
    private WaveView wave;
    private Ui.Row rowVol, rowPitch, rowStart, rowEnd, rowBpm, rowSwing;
    private Button modeBtn, revBtn, chokeBtn, playBtn, dubBtn, micBtn, fullBtn;
    private boolean updating;

    private volatile boolean micRecording;
    private Thread micThread;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.density = getResources().getDisplayMetrics().density;
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        w.setStatusBarColor(Ui.BG);
        w.setNavigationBarColor(Ui.BG);
        DemoKit.load(engine);
        audio = new AudioIO(engine);
        setContentView(buildUi());
        showTab(0);
        setBank(0);
    }

    @Override protected void onResume() { super.onResume(); audio.start(); }

    @Override protected void onPause() {
        super.onPause();
        if (micRecording) micRecording = false;
        engine.stop();
        engine.killAll();
        updatePlayButton();
        audio.stop();
    }

    // ---- UI construction -----------------------------------------------------------------
    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(Ui.dp(8), Ui.dp(8), Ui.dp(8), Ui.dp(8));

        // header: title, bpm, banks
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.label(this, "PADLAB", 20, Ui.ACCENT);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        head.addView(title, Ui.lp(0, -2, 1f));
        bpmText = Ui.label(this, "", 13, Ui.DIM);
        head.addView(bpmText, Ui.margin(new LinearLayout.LayoutParams(-2, -2), 0, 0, 12, 0));
        for (int i = 0; i < 4; i++) {
            final int bk = i;
            Button bb = Ui.button(this, String.valueOf((char) ('A' + i)));
            bb.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { setBank(bk); } });
            bankButtons[i] = bb;
            head.addView(bb, Ui.margin(new LinearLayout.LayoutParams(Ui.dp(44), -2), 3, 0, 0, 0));
        }
        root.addView(head);

        // tab bar
        LinearLayout bar = new LinearLayout(this);
        String[] names = {"PADS", "EDIT", "FX", "SEQ"};
        for (int i = 0; i < 4; i++) {
            final int t = i;
            Button tb = Ui.button(this, names[i]);
            tb.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showTab(t); } });
            tabButtons[i] = tb;
            bar.addView(tb, Ui.margin(Ui.lp(0, -2, 1f), 2, 6, 2, 6));
        }
        root.addView(bar);

        FrameLayout content = new FrameLayout(this);
        tabs[0] = buildPads();
        tabs[1] = buildEdit();
        fxPanel = new FxPanel(this, engine);
        tabs[2] = fxPanel;
        tabs[3] = buildSeq();
        for (View v : tabs) content.addView(v, new FrameLayout.LayoutParams(-1, -1));
        root.addView(content, Ui.lp(-1, 0, 1f));
        fxPanel.refresh();

        root.addView(buildTransport());
        return root;
    }

    private View buildPads() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        for (int row = 0; row < 4; row++) {
            LinearLayout r = new LinearLayout(this);
            for (int c = 0; c < 4; c++) {
                int idx = (3 - row) * 4 + c; // pad 1 bottom-left like an MPC
                PadView pv = new PadView(this, this, idx);
                padViews[idx] = pv;
                r.addView(pv, Ui.lp(0, -1, 1f));
            }
            col.addView(r, Ui.lp(-1, 0, 1f));
        }
        fullBtn = Ui.button(this, "FULL LEVEL: ON");
        Ui.active(fullBtn, true);
        fullBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                fullLevel = !fullLevel;
                fullBtn.setText(fullLevel ? "FULL LEVEL: ON" : "VELOCITY: TOUCH HEIGHT");
                Ui.active(fullBtn, fullLevel);
            }
        });
        col.addView(fullBtn, Ui.margin(new LinearLayout.LayoutParams(-1, -2), 0, 6, 0, 0));
        return col;
    }

    private View buildEdit() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        padInfo = Ui.label(this, "", 14, Ui.TEXT);
        col.addView(padInfo);
        wave = new WaveView(this);
        col.addView(wave, Ui.margin(new LinearLayout.LayoutParams(-1, Ui.dp(110)), 0, 6, 0, 6));

        rowVol = slider(col, "Volume", 150);
        rowPitch = slider(col, "Pitch", 48);
        rowStart = slider(col, "Start", 1000);
        rowEnd = slider(col, "End", 1000);
        SeekBar.OnSeekBarChangeListener l = new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean user) { if (user && !updating) editChanged(); }
            public void onStartTrackingTouch(SeekBar s) { }
            public void onStopTrackingTouch(SeekBar s) { }
        };
        rowVol.bar.setOnSeekBarChangeListener(l);
        rowPitch.bar.setOnSeekBarChangeListener(l);
        rowStart.bar.setOnSeekBarChangeListener(l);
        rowEnd.bar.setOnSeekBarChangeListener(l);

        LinearLayout b1 = new LinearLayout(this);
        modeBtn = Ui.button(this, "");
        revBtn = Ui.button(this, "REVERSE");
        chokeBtn = Ui.button(this, "");
        b1.addView(modeBtn, Ui.margin(Ui.lp(0, -2, 1f), 0, 8, 3, 0));
        b1.addView(revBtn, Ui.margin(Ui.lp(0, -2, 1f), 3, 8, 3, 0));
        b1.addView(chokeBtn, Ui.margin(Ui.lp(0, -2, 1f), 3, 8, 0, 0));
        col.addView(b1);
        modeBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { Pad p = engine.pads[selected]; p.mode = (p.mode + 1) % 3; refreshEdit(); }
        });
        revBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { Pad p = engine.pads[selected]; p.reverse = !p.reverse; refreshEdit(); }
        });
        chokeBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { Pad p = engine.pads[selected]; p.choke = (p.choke + 1) % 5; refreshEdit(); }
        });

        LinearLayout b2 = new LinearLayout(this);
        Button load = Ui.button(this, "LOAD FILE");
        Button clear = Ui.button(this, "CLEAR PAD");
        Button prev = Ui.button(this, "PREVIEW");
        b2.addView(load, Ui.margin(Ui.lp(0, -2, 1f), 0, 6, 3, 0));
        b2.addView(clear, Ui.margin(Ui.lp(0, -2, 1f), 3, 6, 3, 0));
        b2.addView(prev, Ui.margin(Ui.lp(0, -2, 1f), 3, 6, 0, 0));
        col.addView(b2);
        load.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickFile(); } });
        clear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Pad p = engine.pads[selected];
                p.sample = null; p.name = ""; p.start = 0; p.end = 1; p.pitch = 0;
                refreshAll();
            }
        });
        prev.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, android.view.MotionEvent e) {
                if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) engine.trigger(selected, 1f);
                else if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP
                        || e.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL) engine.release(selected);
                return false;
            }
        });
        return col;
    }

    private Ui.Row slider(LinearLayout parent, String label, int max) {
        Ui.Row r = new Ui.Row(this, label, max);
        parent.addView(r.view);
        return r;
    }

    private View buildSeq() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        grid = new StepGrid(this, this);
        col.addView(grid, Ui.lp(-1, 0, 1f));

        rowBpm = slider(col, "BPM", 180);
        rowSwing = slider(col, "Swing", 100);
        rowBpm.bar.setProgress((int) engine.bpm - 40);
        rowSwing.bar.setProgress((int) (engine.swing * 100));
        SeekBar.OnSeekBarChangeListener l = new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean user) {
                engine.bpm = rowBpm.bar.getProgress() + 40;
                engine.swing = rowSwing.bar.getProgress() / 100f;
                rowBpm.value.setText((int) engine.bpm + " bpm");
                rowSwing.value.setText(rowSwing.bar.getProgress() + "%");
                bpmText.setText((int) engine.bpm + " BPM");
            }
            public void onStartTrackingTouch(SeekBar s) { }
            public void onStopTrackingTouch(SeekBar s) { }
        };
        rowBpm.bar.setOnSeekBarChangeListener(l);
        rowSwing.bar.setOnSeekBarChangeListener(l);
        l.onProgressChanged(rowBpm.bar, 0, false);

        LinearLayout b = new LinearLayout(this);
        Button clrPad = Ui.button(this, "CLEAR PAD");
        Button clrAll = Ui.button(this, "CLEAR ALL");
        b.addView(clrPad, Ui.margin(Ui.lp(0, -2, 1f), 0, 4, 3, 0));
        b.addView(clrAll, Ui.margin(Ui.lp(0, -2, 1f), 3, 4, 0, 0));
        col.addView(b);
        clrPad.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { java.util.Arrays.fill(engine.steps[selected], false); grid.invalidate(); }
        });
        clrAll.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { for (boolean[] s : engine.steps) java.util.Arrays.fill(s, false); grid.invalidate(); }
        });
        return col;
    }

    private View buildTransport() {
        LinearLayout t = new LinearLayout(this);
        t.setGravity(Gravity.CENTER_VERTICAL);
        playBtn = Ui.button(this, "▶ PLAY");
        dubBtn = Ui.button(this, "● OVERDUB");
        micBtn = Ui.button(this, "🎤 MIC");
        t.addView(playBtn, Ui.margin(Ui.lp(0, -2, 1.1f), 0, 6, 3, 0));
        t.addView(dubBtn, Ui.margin(Ui.lp(0, -2, 1.3f), 3, 6, 3, 0));
        t.addView(micBtn, Ui.margin(Ui.lp(0, -2, 1f), 3, 6, 0, 0));
        playBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (engine.playing) engine.stop(); else engine.play();
                updatePlayButton();
            }
        });
        dubBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { engine.overdub = !engine.overdub; updatePlayButton(); }
        });
        micBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { toggleMic(); } });

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.addView(t);
        Ui.Row vol = new Ui.Row(this, "Master", 100);
        vol.bar.setProgress((int) (engine.master * 100));
        vol.value.setText((int) (engine.master * 100) + "%");
        vol.bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean user) { engine.master = v / 100f; }
            public void onStartTrackingTouch(SeekBar s) { }
            public void onStopTrackingTouch(SeekBar s) { }
        });
        vol.value.setVisibility(View.GONE);
        outer.addView(vol.view);
        updatePlayButton();
        return outer;
    }

    // ---- state ---------------------------------------------------------------------------
    private void showTab(int t) {
        for (int i = 0; i < 4; i++) {
            tabs[i].setVisibility(i == t ? View.VISIBLE : View.GONE);
            Ui.active(tabButtons[i], i == t);
        }
        if (t == 1) refreshEdit();
        if (t == 3) grid.invalidate();
    }

    void setBank(int b) {
        bank = b;
        selected = b * 16 + selected % 16;
        for (int i = 0; i < 4; i++) Ui.active(bankButtons[i], i == b);
        refreshAll();
    }

    void select(int id) {
        if (selected == id) return;
        selected = id;
        refreshEdit();
        for (PadView p : padViews) p.invalidate();
        if (grid != null) grid.invalidate();
    }

    private void refreshAll() {
        for (PadView p : padViews) p.invalidate();
        refreshEdit();
        if (grid != null) grid.invalidate();
    }

    private void refreshEdit() {
        if (rowVol == null) return;
        Pad p = engine.pads[selected];
        updating = true;
        padInfo.setText("PAD " + (char) ('A' + selected / 16) + (selected % 16 + 1) + "   "
                + (p.sample == null ? "(empty)" : p.label() + "  " + String.format("%.2fs", p.sample.n / (float) Engine.SR)));
        rowVol.bar.setProgress(Math.round(p.vol * 100));
        rowPitch.bar.setProgress(p.pitch + 24);
        rowStart.bar.setProgress(Math.round(p.start * 1000));
        rowEnd.bar.setProgress(Math.round(p.end * 1000));
        updating = false;
        rowVol.value.setText(Math.round(p.vol * 100) + "%");
        rowPitch.value.setText((p.pitch > 0 ? "+" : "") + p.pitch + " st");
        rowStart.value.setText(String.format("%.1f%%", p.start * 100));
        rowEnd.value.setText(String.format("%.1f%%", p.end * 100));
        modeBtn.setText(Pad.MODES[p.mode]);
        revBtn.setText(p.reverse ? "REVERSE ✓" : "REVERSE");
        Ui.active(revBtn, p.reverse);
        chokeBtn.setText(p.choke == 0 ? "CHOKE: OFF" : "CHOKE: " + p.choke);
        wave.set(p.sample, p.start, p.end);
    }

    private void editChanged() {
        Pad p = engine.pads[selected];
        p.vol = rowVol.bar.getProgress() / 100f;
        p.pitch = rowPitch.bar.getProgress() - 24;
        float s = rowStart.bar.getProgress() / 1000f, e = rowEnd.bar.getProgress() / 1000f;
        if (s > e - 0.01f) { if (rowStart.bar.isPressed()) s = Math.max(0, e - 0.01f); else e = Math.min(1, s + 0.01f); }
        p.start = s;
        p.end = e;
        refreshEdit();
    }

    private void updatePlayButton() {
        playBtn.setText(engine.playing ? "■ STOP" : "▶ PLAY");
        Ui.active(playBtn, engine.playing);
        Ui.active(dubBtn, engine.overdub);
        if (engine.overdub) dubBtn.setBackground(Ui.rounded(Ui.REC, 6));
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
        refreshAll();
    }

    // ---- microphone sampling -------------------------------------------------------------
    private void toggleMic() {
        if (micRecording) { micRecording = false; return; }
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
        final int target = selected;
        micRecording = true;
        micBtn.setText("■ STOP REC");
        micBtn.setBackground(Ui.rounded(Ui.REC, 6));
        micThread = new Thread(new Runnable() {
            public void run() {
                int min = AudioRecord.getMinBufferSize(Engine.SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                AudioRecord ar = new AudioRecord(MediaRecorder.AudioSource.MIC, Engine.SR,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min, 16384));
                short[] all = new short[Engine.SR * 10];
                int n = 0;
                final int max = Engine.SR * 60;
                if (ar.getState() == AudioRecord.STATE_INITIALIZED) {
                    short[] chunk = new short[2048];
                    ar.startRecording();
                    while (micRecording && n < max) {
                        int r = ar.read(chunk, 0, chunk.length);
                        if (r <= 0) break;
                        if (n + r > all.length) all = java.util.Arrays.copyOf(all, Math.min(max, all.length * 2) + r);
                        System.arraycopy(chunk, 0, all, n, r);
                        n += r;
                    }
                    ar.stop();
                }
                ar.release();
                micRecording = false;
                final int len = n;
                final short[] data = all;
                ui.post(new Runnable() {
                    public void run() {
                        micBtn.setText("🎤 MIC");
                        Ui.active(micBtn, false);
                        if (len < 1000) {
                            Toast.makeText(MainActivity.this, "Nothing recorded", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        int from = 0;
                        while (from < len - 1 && Math.abs(data[from]) < 600) from++; // trim leading silence
                        float[] f = new float[len - from];
                        for (int i = 0; i < f.length; i++) f[i] = data[from + i] / 32768f;
                        assign(target, Sample.prepare("REC", f, f, Engine.SR));
                    }
                });
            }
        });
        micThread.start();
    }
}
