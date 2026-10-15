package com.stecaklab.padlab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** Piano-roll view of the detected melody (after key/scale/grid shaping). Tap a note to select it. */
final class RollView extends View {
    interface Listener { void onSelect(int srcIndex); }

    private static final boolean[] BLACK = {false, true, false, true, false, false, true, false, true, false, true, false};

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private List<Melody.Out> notes = new ArrayList<Melody.Out>();
    private double total = 2, gridStep;
    private int lo = 55, hi = 72, selected = -1;
    private Listener listener;

    RollView(Context c) { super(c); }

    RollView listener(Listener l) { listener = l; return this; }

    void set(List<Melody.Out> notes, double gridStep, int selected) {
        this.notes = notes;
        this.gridStep = gridStep;
        this.selected = selected;
        double end = 2;
        int mn = 127, mx = 0;
        for (Melody.Out o : notes) {
            end = Math.max(end, o.start + o.dur + 0.2);
            mn = Math.min(mn, o.midi);
            mx = Math.max(mx, o.midi);
        }
        total = end;
        if (!notes.isEmpty()) {
            lo = mn - 2;
            hi = mx + 2;
            while (hi - lo < 12) { lo--; hi++; }
        }
        requestLayout();
        invalidate();
    }

    private float gutter() { return Ui.dpf(30); }

    @Override protected void onMeasure(int w, int h) {
        int want = (int) (gutter() + total * Ui.dpf(90));
        int mode = MeasureSpec.getMode(w), size = MeasureSpec.getSize(w);
        int width = mode == MeasureSpec.UNSPECIFIED ? Math.max(want, Ui.dp(320)) : Math.max(want, size);
        setMeasuredDimension(width, resolveSize(Ui.dp(160), h));
    }

    @Override protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight(), g = gutter();
        int rows = hi - lo + 1;
        float rh = h / rows;
        p.setStyle(Paint.Style.FILL);
        p.setColor(Ui.LCD);
        cv.drawRect(0, 0, w, h, p);
        for (int m = lo; m <= hi; m++) {
            float y = h - (m - lo + 1) * rh;
            boolean blk = BLACK[((m % 12) + 12) % 12];
            p.setColor(blk ? 0xFF0E1013 : 0xFF14171B);
            cv.drawRect(g, y, w, y + rh, p);
            p.setColor(0xFF1C2026);
            cv.drawRect(g, y + rh - 1, w, y + rh, p);
        }
        float pxs = (w - g) / (float) total;
        // time grid
        p.setStrokeWidth(Ui.dpf(1));
        if (gridStep > 0) {
            int i = 0;
            for (double t = 0; t < total; t += gridStep, i++) {
                p.setColor(i % 4 == 0 ? 0xFF2D323B : 0xFF1B1F25);
                cv.drawLine(g + (float) t * pxs, 0, g + (float) t * pxs, h, p);
            }
        } else {
            p.setColor(0xFF22262D);
            for (int s = 0; s < total; s++) cv.drawLine(g + s * pxs, 0, g + s * pxs, h, p);
        }
        // gutter with note names on every C and on the lowest/highest row
        p.setColor(0xFF101216);
        cv.drawRect(0, 0, g, h, p);
        p.setTypeface(Ui.MONO);
        p.setTextSize(Ui.dpf(9));
        p.setColor(Ui.DIM);
        for (int m = lo; m <= hi; m++) {
            if (m % 12 == 0 || m % 12 == 7) cv.drawText(Melody.noteName(m), Ui.dpf(3), h - (m - lo) * rh - rh * 0.3f, p);
        }
        if (notes.isEmpty()) {
            p.setTypeface(Ui.BOLD);
            p.setTextSize(Ui.dpf(12));
            p.setTextAlign(Paint.Align.CENTER);
            cv.drawText("SING INTO THE MIC  —  NOTES APPEAR HERE", w / 2f, h / 2f, p);
            p.setTextAlign(Paint.Align.LEFT);
            return;
        }
        for (Melody.Out o : notes) {
            float x0 = g + (float) o.start * pxs, x1 = g + (float) (o.start + o.dur) * pxs - Ui.dpf(1.5f);
            float y1 = h - (o.midi - lo) * rh;
            r.set(x0, y1 - rh + Ui.dpf(1), Math.max(x1, x0 + Ui.dpf(4)), y1 - Ui.dpf(1));
            boolean sel = o.src == selected;
            p.setStyle(Paint.Style.FILL);
            p.setColor(Ui.mix(0xFF5A2D0A, Ui.ACCENT, o.vel));
            cv.drawRoundRect(r, Ui.dpf(4), Ui.dpf(4), p);
            if (sel) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Ui.dpf(2));
                p.setColor(0xFFFFFFFF);
                cv.drawRoundRect(r, Ui.dpf(4), Ui.dpf(4), p);
                p.setStyle(Paint.Style.FILL);
            }
            if (r.width() > Ui.dpf(26) && rh > Ui.dpf(11)) {
                p.setTypeface(Ui.MONO);
                p.setTextSize(Math.min(Ui.dpf(10), rh * 0.8f));
                p.setColor(0xFF111111);
                cv.drawText(Melody.noteName(o.midi), r.left + Ui.dpf(4), r.bottom - (rh - Ui.dpf(2) - p.getTextSize()) / 2f - Ui.dpf(1), p);
            }
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() != MotionEvent.ACTION_UP) return true;
        float g = gutter(), pxs = (getWidth() - g) / (float) total;
        double t = (e.getX() - g) / pxs;
        int best = -1;
        double bd = 1e9;
        for (Melody.Out o : notes) {
            double c = o.start + o.dur / 2;
            double d = t >= o.start && t <= o.start + o.dur ? 0 : Math.min(Math.abs(t - o.start), Math.abs(t - o.start - o.dur));
            if (d < bd) { bd = d; best = o.src; }
        }
        if (best >= 0 && bd < 0.25 && listener != null) listener.onSelect(best);
        return true;
    }
}
