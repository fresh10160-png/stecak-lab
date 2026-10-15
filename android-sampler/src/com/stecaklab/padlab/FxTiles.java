package com.stecaklab.padlab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** Grid of effect slots (signal order, left→right, top→bottom) + an ALL OFF slot. */
final class FxTiles extends View {
    interface Listener { void onTap(int index); }

    static final int COLS = 4;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private Fx[] chain = new Fx[0];
    private int selected;
    private Listener listener;

    FxTiles(Context c) { super(c); }

    FxTiles listener(Listener l) { listener = l; return this; }

    void bind(Fx[] chain, int selected) {
        this.chain = chain;
        this.selected = selected;
        invalidate();
    }

    private int rows() { return (chain.length + 1 + COLS - 1) / COLS; }

    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension(resolveSize(Ui.dp(300), w), Ui.dp(54) * Math.max(1, rows()));
    }

    @Override protected void onDraw(Canvas cv) {
        float tw = getWidth() / (float) COLS, th = getHeight() / (float) rows();
        float gap = Ui.dpf(3), rad = Ui.dpf(10);
        for (int i = 0; i <= chain.length; i++) {
            float x = (i % COLS) * tw, y = (i / COLS) * th;
            r.set(x + gap, y + gap, x + tw - gap, y + th - gap);
            boolean all = i == chain.length;
            boolean on = !all && chain[i].on;
            boolean sel = !all && i == selected;
            p.setStyle(Paint.Style.FILL);
            p.setColor(on ? Ui.mix(Ui.PANEL2, Ui.CYAN, 0.22f) : (all ? Ui.PANEL : Ui.PANEL2));
            cv.drawRoundRect(r, rad, rad, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Ui.dpf(sel ? 2f : 1f));
            p.setColor(sel ? 0xFFFFFFFF : (on ? Ui.CYAN : Ui.STROKE));
            cv.drawRoundRect(r, rad, rad, p);

            p.setStyle(Paint.Style.FILL);
            if (!all) {
                p.setColor(on ? Ui.CYAN : 0xFF343842);
                cv.drawCircle(r.left + Ui.dpf(11), r.top + Ui.dpf(11), Ui.dpf(3.5f), p);
                if (on) {
                    p.setColor(Ui.alpha(Ui.CYAN, 0.25f));
                    cv.drawCircle(r.left + Ui.dpf(11), r.top + Ui.dpf(11), Ui.dpf(7), p);
                }
            }
            p.setTypeface(Ui.BOLD);
            p.setTextSize(Ui.dpf(10));
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(on ? Ui.TEXT : (all ? Ui.DIM : 0xFFB5BAC4));
            String name = all ? "ALL OFF" : chain[i].name;
            int sp = name.indexOf(' ');
            float cy = (r.top + r.bottom) / 2f;
            if (sp > 0 && !all) {
                cv.drawText(name.substring(0, sp), r.centerX(), cy + Ui.dpf(1), p);
                cv.drawText(name.substring(sp + 1), r.centerX(), cy + Ui.dpf(13), p);
            } else {
                cv.drawText(name, r.centerX(), cy + Ui.dpf(8), p);
            }
            p.setTextAlign(Paint.Align.LEFT);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) return true;
        if (e.getActionMasked() == MotionEvent.ACTION_UP) {
            int col = (int) (e.getX() / (getWidth() / (float) COLS));
            int row = (int) (e.getY() / (getHeight() / (float) rows()));
            int i = row * COLS + col;
            if (i >= 0 && i <= chain.length && listener != null) listener.onTap(i);
        }
        return true;
    }
}
