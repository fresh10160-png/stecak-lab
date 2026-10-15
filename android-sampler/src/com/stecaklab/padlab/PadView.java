package com.stecaklab.padlab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

final class PadView extends View {
    private final MainActivity act;
    private final int index; // 0..15 within bank
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private Shader sheen;
    private int held = -1;

    PadView(Context c, MainActivity act, int index) {
        super(c);
        this.act = act;
        this.index = index;
    }

    int id() { return act.bank * 16 + index; }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        sheen = new LinearGradient(0, 0, 0, h, 0x26FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
    }

    @Override protected void onDraw(Canvas cv) {
        int id = id();
        Pad p = act.engine.pads[id];
        int bank = Ui.BANK[act.bank];
        long since = (System.nanoTime() - act.engine.lastHit[id]) / 1000000L;
        float glow = held >= 0 ? 1f : Math.max(0f, 1f - since / 260f);
        float inset = Ui.dpf(3), rad = Ui.dpf(14);

        cv.save();
        if (held >= 0) cv.scale(0.975f, 0.975f, getWidth() / 2f, getHeight() / 2f);
        rect.set(inset, inset, getWidth() - inset, getHeight() - inset);

        if (glow > 0.02f) { // outer glow
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Ui.alpha(bank, 0.28f * glow));
            rect.inset(-Ui.dpf(2), -Ui.dpf(2));
            cv.drawRoundRect(rect, rad + 2, rad + 2, paint);
            rect.inset(Ui.dpf(2), Ui.dpf(2));
        }
        int base = p.sample == null ? 0xFF1A1C21 : 0xFF2B2F37;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Ui.mix(base, bank, glow * 0.92f));
        cv.drawRoundRect(rect, rad, rad, paint);
        paint.setShader(sheen);
        cv.drawRoundRect(rect, rad, rad, paint);
        paint.setShader(null);

        paint.setStyle(Paint.Style.STROKE);
        boolean sel = act.selected == id;
        paint.setStrokeWidth(Ui.dpf(sel ? 2f : 1f));
        paint.setColor(sel ? 0xFFFFFFFF : Ui.mix(Ui.STROKE, bank, glow));
        cv.drawRoundRect(rect, rad, rad, paint);

        paint.setStyle(Paint.Style.FILL);
        int fg = glow > 0.5f ? 0xFF111111 : Ui.TEXT;
        paint.setTypeface(Ui.MONO);
        paint.setTextSize(Ui.dpf(11));
        paint.setColor(glow > 0.5f ? 0xFF111111 : bank);
        cv.drawText(String.valueOf(index + 1), rect.left + Ui.dpf(9), rect.top + Ui.dpf(19), paint);

        // tags (top-right)
        paint.setTypeface(Ui.BOLD);
        paint.setTextSize(Ui.dpf(8.5f));
        paint.setTextAlign(Paint.Align.RIGHT);
        float tx = rect.right - Ui.dpf(8), ty = rect.top + Ui.dpf(18);
        if (p.mode != Pad.ONE_SHOT) {
            paint.setColor(fg);
            cv.drawText(p.mode == Pad.LOOP ? "LOOP" : "GATE", tx, ty, paint);
            tx -= Ui.dpf(30);
        }
        if (p.hasFx()) {
            paint.setColor(glow > 0.5f ? 0xFF111111 : Ui.CYAN);
            cv.drawText("FX", tx, ty, paint);
            tx -= Ui.dpf(18);
        }
        if (p.sample != null && p.warped()) {
            paint.setColor(glow > 0.5f ? 0xFF111111 : Ui.CYAN);
            cv.drawText("TS", tx, ty, paint);
        }

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(Ui.dpf(11.5f));
        paint.setColor(fg);
        String t = p.label();
        if (t.length() > 9) t = t.substring(0, 9);
        cv.drawText(t.toUpperCase(), getWidth() / 2f, getHeight() - Ui.dpf(13), paint);
        paint.setTextAlign(Paint.Align.LEFT);
        cv.restore();
        if (glow > 0 && held < 0) postInvalidateOnAnimation();
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
