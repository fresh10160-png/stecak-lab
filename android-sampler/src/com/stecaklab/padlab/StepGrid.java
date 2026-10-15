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
    private int paintRow = -1, paintCol = -1;
    private boolean paintValue;

    StepGrid(Context c, MainActivity act) {
        super(c);
        this.act = act;
    }

    private float labelW() { return Ui.dp(64); }
    private float cellW() { return (getWidth() - labelW()) / Engine.STEPS; }
    private float cellH() { return getHeight() / 16f; }

    @Override protected void onDraw(Canvas cv) {
        Engine e = act.engine;
        int cur = e.curStep;
        float cw = cellW(), ch = cellH(), lw = labelW();
        paint.setTextSize(Math.min(Ui.dp(11), ch * 0.6f));
        for (int row = 0; row < 16; row++) {
            int pad = act.bank * 16 + (15 - row);
            float top = row * ch;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(act.selected == pad ? Ui.ACCENT : Ui.DIM);
            paint.setFakeBoldText(true);
            String t = (16 - row) + " " + e.pads[pad].label();
            if (t.length() > 9) t = t.substring(0, 9);
            cv.drawText(t, Ui.dp(4), top + ch * 0.72f, paint);
            for (int s = 0; s < Engine.STEPS; s++) {
                boolean on = e.steps[pad][s];
                int col = on ? Ui.ACCENT : ((s / 4) % 2 == 0 ? 0xFF2a2d33 : 0xFF202328);
                if (s == cur) col = on ? 0xFFFFD2A8 : 0xFF4a4f58;
                paint.setColor(col);
                r.set(lw + s * cw + 1.5f, top + 1.5f, lw + (s + 1) * cw - 1.5f, top + ch - 1.5f);
                cv.drawRoundRect(r, 3, 3, paint);
            }
        }
        if (e.playing || act.engine.curStep >= 0) postInvalidateDelayed(40);
    }

    @Override public boolean onTouchEvent(MotionEvent ev) {
        int row = (int) (ev.getY() / cellH());
        int col = (int) ((ev.getX() - labelW()) / cellW());
        if (row < 0 || row > 15) return true;
        int pad = act.bank * 16 + (15 - row);
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (ev.getX() < labelW()) {
                    act.select(pad);
                    act.engine.trigger(pad, 1f);
                    final int auditioned = pad;
                    postDelayed(new Runnable() { public void run() { act.engine.release(auditioned); } }, 150);
                    invalidate();
                    return true;
                }
                if (col < 0 || col >= Engine.STEPS) return true;
                paintValue = !act.engine.steps[pad][col];
                act.engine.steps[pad][col] = paintValue;
                paintRow = row;
                paintCol = col;
                act.select(pad);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (paintRow < 0 || col < 0 || col >= Engine.STEPS) return true;
                if (row != paintRow || col != paintCol) {
                    act.engine.steps[pad][col] = paintValue;
                    paintRow = row;
                    paintCol = col;
                    invalidate();
                }
                return true;
            default:
                paintRow = -1;
                return true;
        }
    }
}
