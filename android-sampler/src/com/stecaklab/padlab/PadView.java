package com.stecaklab.padlab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

final class PadView extends View {
    private final MainActivity act;
    private final int index; // 0..15 within bank
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int held = -1;

    PadView(Context c, MainActivity act, int index) {
        super(c);
        this.act = act;
        this.index = index;
    }

    int id() { return act.bank * 16 + index; }

    @Override protected void onDraw(Canvas cv) {
        Pad p = act.engine.pads[id()];
        long since = (System.nanoTime() - act.engine.lastHit[id()]) / 1000000L;
        float glow = held >= 0 ? 1f : Math.max(0f, 1f - since / 220f);
        int base = p.sample == null ? Ui.PAD : Ui.PAD_LOADED;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(blend(base, Ui.ACCENT, glow));
        float m = Ui.dp(3);
        rect.set(m, m, getWidth() - m, getHeight() - m);
        cv.drawRoundRect(rect, Ui.dp(10), Ui.dp(10), paint);
        if (act.selected == id()) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Ui.dp(2));
            paint.setColor(0xFFFFFFFF);
            cv.drawRoundRect(rect, Ui.dp(10), Ui.dp(10), paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(glow > 0.5f ? 0xFF000000 : Ui.TEXT);
        paint.setTextSize(Ui.dp(12));
        paint.setFakeBoldText(true);
        cv.drawText(String.valueOf(index + 1), rect.left + Ui.dp(8), rect.top + Ui.dp(18), paint);
        paint.setTextSize(Ui.dp(11));
        String t = p.label();
        if (t.length() > 9) t = t.substring(0, 9);
        paint.setTextAlign(Paint.Align.CENTER);
        cv.drawText(t, getWidth() / 2f, getHeight() - Ui.dp(12), paint);
        paint.setTextAlign(Paint.Align.LEFT);
        if (glow > 0 && held < 0) postInvalidateOnAnimation();
    }

    private static int blend(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                held = id();
                float vel = act.fullLevel ? 1f : Math.max(0.2f, Math.min(1f, 0.25f + 0.75f * e.getY() / getHeight()));
                act.engine.trigger(held, vel);
                act.select(held);
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (held >= 0) act.engine.release(held);
                held = -1;
                invalidate();
                return true;
        }
        return true;
    }
}
