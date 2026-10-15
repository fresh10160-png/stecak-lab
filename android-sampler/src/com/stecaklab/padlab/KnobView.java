package com.stecaklab.padlab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** Rotary knob: drag vertically, double-tap to reset. */
final class KnobView extends View {
    interface Fmt { String fmt(float v); }

    abstract static class Listener {
        abstract void onChange(KnobView k, float v);
        void onEnd(KnobView k) { }
    }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private String label;
    private float min, max, def, value, raw, step;
    private boolean bipolar, dim;
    private int accent = Ui.ACCENT;
    private Fmt fmt;
    private Listener listener;
    private float lastY, downY;
    private long lastUp;

    KnobView(Context c, String label, float min, float max, float def) {
        super(c);
        this.label = label;
        this.min = min;
        this.max = max;
        this.def = def;
        this.value = this.raw = def;
        this.fmt = new Fmt() { public String fmt(float v) { return Math.round(v * 100) + "%"; } };
        setClickable(true);
    }

    KnobView fmt(Fmt f) { fmt = f; return this; }
    KnobView listener(Listener l) { listener = l; return this; }
    KnobView step(float s) { step = s; return this; }
    KnobView bipolar(boolean b) { bipolar = b; return this; }
    KnobView accent(int c) { accent = c; invalidate(); return this; }
    KnobView label(String l) { label = l; invalidate(); return this; }
    KnobView dim(boolean d) { if (dim != d) { dim = d; invalidate(); } return this; }

    float value() { return value; }

    void set(float v) {
        value = raw = Math.max(min, Math.min(max, v));
        invalidate();
    }

    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension(resolveSize(Ui.dp(72), w), resolveSize(Ui.dp(88), h));
    }

    @Override protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        float labelH = Ui.dpf(16);
        float d = Math.min(w, h - labelH) - Ui.dpf(8);
        float cx = w / 2f, cy = (h - labelH) / 2f + Ui.dpf(2);
        float rad = d / 2f;
        float t = (value - min) / (max - min);
        int col = dim ? Ui.DIM : accent;

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(Ui.dpf(4));
        r.set(cx - rad, cy - rad, cx + rad, cy + rad);
        p.setColor(0xFF2A2E37);
        cv.drawArc(r, 135, 270, false, p);
        p.setColor(col);
        if (bipolar) {
            float mid = (0f - min) / (max - min);
            cv.drawArc(r, 135 + 270 * Math.min(mid, t), 270 * Math.abs(t - mid), false, p);
        } else if (t > 0.002f) {
            cv.drawArc(r, 135, 270 * t, false, p);
        }

        float cap = rad - Ui.dpf(7);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Ui.PANEL2);
        cv.drawCircle(cx, cy, cap, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Ui.dpf(1));
        p.setColor(Ui.STROKE);
        cv.drawCircle(cx, cy, cap, p);

        double ang = Math.toRadians(135 + 270 * t);
        p.setStrokeWidth(Ui.dpf(2.5f));
        p.setColor(col);
        cv.drawLine(cx + (float) Math.cos(ang) * cap * 0.45f, cy + (float) Math.sin(ang) * cap * 0.45f,
                cx + (float) Math.cos(ang) * cap * 0.92f, cy + (float) Math.sin(ang) * cap * 0.92f, p);

        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Ui.MONO);
        p.setTextAlign(Paint.Align.CENTER);
        String vs = fmt.fmt(value);
        float ts = Ui.dpf(vs.length() > 6 ? 8f : 9.5f);
        p.setTextSize(ts);
        p.setColor(dim ? Ui.DIM : Ui.TEXT);
        cv.drawText(vs, cx, cy + ts * 0.36f, p);
        p.setTypeface(Ui.BOLD);
        p.setTextSize(Ui.dpf(10));
        p.setColor(Ui.DIM);
        cv.drawText(label.toUpperCase(), cx, h - Ui.dpf(3), p);
        p.setTextAlign(Paint.Align.LEFT);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                lastY = downY = e.getY();
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dv = (lastY - e.getY()) / Ui.dpf(170) * (max - min);
                lastY = e.getY();
                raw = Math.max(min, Math.min(max, raw + dv));
                float nv = step > 0 ? Math.round(raw / step) * step : raw;
                nv = Math.max(min, Math.min(max, nv));
                if (nv != value) {
                    value = nv;
                    invalidate();
                    if (listener != null) listener.onChange(this, value);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                long now = System.currentTimeMillis();
                boolean tap = Math.abs(e.getY() - downY) < Ui.dpf(6);
                if (tap && now - lastUp < 320) {
                    value = raw = def;
                    invalidate();
                    if (listener != null) listener.onChange(this, value);
                    lastUp = 0;
                } else if (tap) {
                    lastUp = now;
                }
                if (listener != null) listener.onEnd(this);
                return true;
            }
        }
        return true;
    }
}
