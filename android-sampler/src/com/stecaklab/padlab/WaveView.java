package com.stecaklab.padlab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** Waveform with draggable start/end handles. */
final class WaveView extends View {
    interface Listener { void onTrim(float start, float end, boolean finished); }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private Sample sample, peaksFor;
    private float start, end = 1f;
    private float[] peaks;
    private int peaksW = -1, drag = -1; // 0 = start, 1 = end
    private boolean busy;
    private int accent = Ui.ACCENT;
    private Listener listener;
    private boolean readOnly;

    WaveView(Context c) { super(c); }

    WaveView listener(Listener l) { listener = l; return this; }

    WaveView readOnly(boolean r) { readOnly = r; return this; }

    void set(Sample s, float start, float end, int accent, boolean busy) {
        this.sample = s;
        this.start = start;
        this.end = end;
        this.accent = accent;
        this.busy = busy;
        invalidate();
    }

    @Override protected void onDraw(Canvas cv) {
        int w = getWidth(), h = getHeight();
        float rad = Ui.dpf(12);
        r.set(0, 0, w, h);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Ui.LCD);
        cv.drawRoundRect(r, rad, rad, p);
        cv.save();
        cv.clipRect(0, 0, w, h);
        p.setStrokeWidth(Ui.dpf(1));
        p.setColor(0xFF1B1E24);
        for (int i = 1; i < 8; i++) cv.drawLine(w * i / 8f, 0, w * i / 8f, h, p);
        cv.drawLine(0, h / 2f, w, h / 2f, p);

        if (sample == null) {
            p.setColor(Ui.DIM);
            p.setTypeface(Ui.BOLD);
            p.setTextSize(Ui.dpf(12));
            p.setTextAlign(Paint.Align.CENTER);
            cv.drawText("EMPTY  —  LOAD A FILE OR HOLD MIC", w / 2f, h / 2f + Ui.dpf(4), p);
            p.setTextAlign(Paint.Align.LEFT);
            cv.restore();
            return;
        }
        if (peaks == null || peaksW != w || peaksFor != sample) {
            peaks = new float[w];
            peaksW = w;
            peaksFor = sample;
            int n = sample.n;
            for (int x = 0; x < w; x++) {
                int a = (int) ((long) x * n / w), b = Math.max(a + 1, (int) ((long) (x + 1) * n / w));
                float pk = 0;
                int stride = Math.max(1, (b - a) / 48);
                for (int i = a; i < b && i < n; i += stride) pk = Math.max(pk, Math.abs(sample.l[i]));
                peaks[x] = pk;
            }
        }
        float sx = start * w, ex = end * w;
        for (int x = 0; x < w; x++) {
            boolean in = x >= sx && x <= ex;
            p.setColor(in ? accent : 0xFF3A3F49);
            float a = Math.max(1f, peaks[x] * h * 0.46f);
            cv.drawLine(x + 0.5f, h / 2f - a, x + 0.5f, h / 2f + a, p);
        }
        p.setColor(0x99000000);
        cv.drawRect(0, 0, sx, h, p);
        cv.drawRect(ex, 0, w, h, p);
        if (!readOnly) {
            handle(cv, sx, h, "S", true);
            handle(cv, ex, h, "E", false);
        }

        p.setTypeface(Ui.MONO);
        p.setTextSize(Ui.dpf(10));
        p.setColor(Ui.DIM);
        cv.drawText(String.format("%.2fs", (end - start) * sample.n / Engine.SR), Ui.dpf(10), h - Ui.dpf(8), p);
        if (busy) {
            p.setColor(Ui.CYAN);
            p.setTextAlign(Paint.Align.RIGHT);
            cv.drawText("STRETCHING…", w - Ui.dpf(10), h - Ui.dpf(8), p);
            p.setTextAlign(Paint.Align.LEFT);
        }
        cv.restore();
    }

    private void handle(Canvas cv, float x, float h, String t, boolean left) {
        p.setColor(Ui.TEXT);
        p.setStrokeWidth(Ui.dpf(2));
        cv.drawLine(x, 0, x, h, p);
        float fw = Ui.dpf(18), fh = Ui.dpf(18);
        p.setStyle(Paint.Style.FILL);
        float x0 = left ? x : x - fw;
        cv.drawRect(x0, 0, x0 + fw, fh, p);
        p.setColor(0xFF111111);
        p.setTypeface(Ui.BOLD);
        p.setTextSize(Ui.dpf(11));
        p.setTextAlign(Paint.Align.CENTER);
        cv.drawText(t, x0 + fw / 2, fh - Ui.dpf(5), p);
        p.setTextAlign(Paint.Align.LEFT);
        p.setStrokeWidth(Ui.dpf(1));
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (sample == null || readOnly) return true;
        float w = getWidth(), x = e.getX() / w;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                float ds = Math.abs(x - start), de = Math.abs(x - end);
                drag = ds <= de ? 0 : 1;
                // same spot (both handles close): pick by side
                if (Math.abs(start - end) < 0.02f) drag = x < start ? 0 : 1;
            case MotionEvent.ACTION_MOVE:
                if (drag == 0) start = Math.max(0f, Math.min(end - 0.01f, x));
                else if (drag == 1) end = Math.min(1f, Math.max(start + 0.01f, x));
                invalidate();
                if (listener != null) listener.onTrim(start, end, false);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (listener != null) listener.onTrim(start, end, true);
                drag = -1;
                return true;
        }
        return true;
    }
}
