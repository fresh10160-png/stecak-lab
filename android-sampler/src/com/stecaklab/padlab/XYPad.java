package com.stecaklab.padlab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** Two-parameter performance surface (x right, y up), values 0..1. */
final class XYPad extends View {
    interface Listener { void onMove(float x, float y); }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float x = 0.5f, y = 0.5f;
    private boolean touching;
    private String xl = "", yl = "";
    private Listener listener;
    private int accent = Ui.CYAN;

    XYPad(Context c) { super(c); }

    XYPad listener(Listener l) { listener = l; return this; }
    XYPad accent(int c) { accent = c; return this; }

    void labels(String xl, String yl) { this.xl = xl; this.yl = yl; invalidate(); }

    void set(float x, float y) { this.x = x; this.y = y; invalidate(); }

    @Override protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight(), rad = Ui.dpf(12);
        r.set(0, 0, w, h);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Ui.LCD);
        cv.drawRoundRect(r, rad, rad, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Ui.dpf(1));
        p.setColor(Ui.alpha(Ui.STROKE, 0.8f));
        for (int i = 1; i < 4; i++) {
            cv.drawLine(w * i / 4f, 0, w * i / 4f, h, p);
            cv.drawLine(0, h * i / 4f, w, h * i / 4f, p);
        }
        p.setColor(Ui.STROKE);
        cv.drawRoundRect(r, rad, rad, p);

        float px = x * w, py = (1f - y) * h;
        p.setColor(Ui.alpha(accent, 0.55f));
        cv.drawLine(px, 0, px, h, p);
        cv.drawLine(0, py, w, py, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Ui.alpha(accent, touching ? 0.35f : 0.18f));
        cv.drawCircle(px, py, Ui.dpf(touching ? 26 : 20), p);
        p.setColor(accent);
        cv.drawCircle(px, py, Ui.dpf(8), p);

        p.setTypeface(Ui.BOLD);
        p.setTextSize(Ui.dpf(10));
        p.setColor(Ui.DIM);
        cv.drawText(xl.toUpperCase() + " →", Ui.dpf(10), h - Ui.dpf(8), p);
        cv.save();
        cv.rotate(-90, Ui.dpf(14), h - Ui.dpf(14) - 0);
        cv.drawText(yl.toUpperCase() + " →", Ui.dpf(14), h - Ui.dpf(14) + Ui.dpf(0), p);
        cv.restore();
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                touching = true;
            case MotionEvent.ACTION_MOVE:
                x = Math.max(0f, Math.min(1f, e.getX() / getWidth()));
                y = Math.max(0f, Math.min(1f, 1f - e.getY() / getHeight()));
                invalidate();
                if (listener != null) listener.onMove(x, y);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                touching = false;
                invalidate();
                return true;
        }
        return true;
    }
}
