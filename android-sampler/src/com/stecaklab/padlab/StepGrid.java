package com.stecaklab.padlab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** 16 pads (current bank) x 16 steps. Tap or drag to paint steps; tap a label to audition. */
final class StepGrid extends View {
    private final MainActivity act;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private int paintRow = -1, paintCol = -1, auditioned = -1;
    private boolean paintValue;

    StepGrid(Context c, MainActivity act) {
        super(c);
        this.act = act;
    }

    private float labelW() { return Ui.dpf(70); }
    private float headH() { return Ui.dpf(16); }
    private float cellW() { return (getWidth() - labelW()) / Engine.STEPS; }
    private float cellH() { return (getHeight() - headH()) / 16f; }

    @Override protected void onDraw(Canvas cv) {
        Engine e = act.engine;
        int cur = e.curStep;
        int bank = Ui.BANK[act.bank];
        float cw = cellW(), ch = cellH(), lw = labelW(), hh = headH();
        paint.setTypeface(Ui.MONO);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(Ui.dpf(9));
        for (int s = 0; s < Engine.STEPS; s++) {
            paint.setColor(s == cur ? Ui.ACCENT : (s % 4 == 0 ? Ui.TEXT : Ui.DIM));
            cv.drawText(String.valueOf(s + 1), lw + (s + 0.5f) * cw, hh - Ui.dpf(4), paint);
        }
        paint.setTextAlign(Paint.Align.LEFT);
        float pad = Ui.dpf(1.5f), rad = Ui.dpf(5);
        for (int row = 0; row < 16; row++) {
            int id = act.bank * 16 + (15 - row);
            Pad pd = e.pads[id];
            float top = hh + row * ch;
            boolean sel = act.selected == id;
            paint.setStyle(Paint.Style.FILL);
            paint.setTypeface(Ui.BOLD);
            paint.setTextSize(Math.min(Ui.dpf(10.5f), ch * 0.55f));
            paint.setColor(sel ? bank : Ui.DIM);
            String t = String.format("%02d ", 16 - row) + pd.label().toUpperCase();
            if (t.length() > 10) t = t.substring(0, 10);
            cv.drawText(t, Ui.dpf(2), top + ch * 0.68f, paint);
            for (int s = 0; s < Engine.STEPS; s++) {
                boolean on = e.steps[id][s];
                int col = on ? bank : ((s / 4) % 2 == 0 ? 0xFF22252C : 0xFF191B21);
                if (on && s == cur) col = Ui.mix(bank, 0xFFFFFFFF, 0.6f);
                paint.setColor(col);
                r.set(lw + s * cw + pad, top + pad, lw + (s + 1) * cw - pad, top + ch - pad);
                cv.drawRoundRect(r, rad, rad, paint);
            }
        }
        if (cur >= 0) { // playhead
            paint.setColor(0x22FFFFFF);
            cv.drawRect(lw + cur * cw, hh, lw + (cur + 1) * cw, getHeight(), paint);
            postInvalidateDelayed(33);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent ev) {
        int row = (int) ((ev.getY() - headH()) / cellH());
        int col = (int) ((ev.getX() - labelW()) / cellW());
        if (row < 0 || row > 15) return true;
        int id = act.bank * 16 + (15 - row);
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                if (ev.getX() < labelW()) {
                    act.select(id);
                    act.engine.trigger(id, 1f);
                    auditioned = id;
                    invalidate();
                    return true;
                }
                if (col < 0 || col >= Engine.STEPS) return true;
                paintValue = !act.engine.steps[id][col];
                act.engine.steps[id][col] = paintValue;
                paintRow = row;
                paintCol = col;
                act.select(id);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (paintRow < 0 || col < 0 || col >= Engine.STEPS) return true;
                if (row != paintRow || col != paintCol) {
                    act.engine.steps[id][col] = paintValue;
                    paintRow = row;
                    paintCol = col;
                    invalidate();
                }
                return true;
            default:
                if (auditioned >= 0) act.engine.release(auditioned);
                auditioned = -1;
                paintRow = -1;
                return true;
        }
    }
}
