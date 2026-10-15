package com.stecaklab.padlab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** Flat rounded button / toggle with optional LED or vector icon. */
final class Btn extends View {
    static final int NONE = 0, PLAY = 1, STOP = 2, REC = 3, MIC = 4;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path path = new Path();
    private String text;
    private int icon, accent = Ui.ACCENT;
    private boolean active, led, pressed;
    private float sp = 12f;

    Btn(Context c, String text) {
        super(c);
        this.text = text;
        setClickable(true);
    }

    Btn text(String t) { if (!t.equals(text)) { text = t; requestLayout(); invalidate(); } return this; }
    Btn icon(int i) { icon = i; invalidate(); return this; }
    Btn accent(int c) { accent = c; invalidate(); return this; }
    Btn led(boolean l) { led = l; invalidate(); return this; }
    Btn size(float sp) { this.sp = sp; requestLayout(); return this; }

    Btn active(boolean a) { if (active != a) { active = a; invalidate(); } return this; }
    boolean isOn() { return active; }

    @Override protected void onMeasure(int w, int h) {
        p.setTextSize(Ui.dpf(sp));
        p.setTypeface(Ui.BOLD);
        int want = (int) p.measureText(text) + Ui.dp(26) + (icon != NONE ? Ui.dp(22) : 0) + (led ? Ui.dp(14) : 0);
        setMeasuredDimension(resolveSize(want, w), resolveSize(Ui.dp(44), h));
    }

    @Override protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight(), rad = Ui.dpf(9);
        r.set(Ui.dpf(1), Ui.dpf(1), w - Ui.dpf(1), h - Ui.dpf(1));
        int fill = active ? accent : (pressed ? 0xFF2A2E37 : Ui.PANEL2);
        if (active && pressed) fill = Ui.mix(accent, 0xFFFFFFFF, 0.25f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(fill);
        cv.drawRoundRect(r, rad, rad, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Ui.dpf(1));
        p.setColor(active ? Ui.mix(accent, 0xFFFFFFFF, 0.3f) : Ui.STROKE);
        cv.drawRoundRect(r, rad, rad, p);

        int fg = active ? 0xFF111111 : Ui.TEXT;
        p.setStyle(Paint.Style.FILL);
        p.setTextSize(Ui.dpf(sp));
        p.setTypeface(Ui.BOLD);
        float tw = text.length() == 0 ? 0 : p.measureText(text);
        float iw = icon != NONE ? Ui.dpf(14) : 0, gap = (iw > 0 && tw > 0) ? Ui.dpf(7) : 0;
        float ledW = led ? Ui.dpf(14) : 0;
        float x = (w - (tw + iw + gap + ledW)) / 2f, cy = h / 2f;
        if (led) {
            p.setColor(active ? 0xFF111111 : 0xFF3A3F49);
            cv.drawCircle(x + Ui.dpf(4), cy, Ui.dpf(3.5f), p);
            x += ledW;
        }
        if (icon != NONE) {
            drawIcon(cv, x, cy, iw, active ? 0xFF111111 : (icon == REC ? Ui.REC : fg));
            x += iw + gap;
        }
        p.setColor(fg);
        if (tw > 0) cv.drawText(text, x, cy + Ui.dpf(sp) * 0.35f, p);
    }

    private void drawIcon(Canvas cv, float x, float cy, float s, int color) {
        p.setColor(color);
        p.setStyle(Paint.Style.FILL);
        path.reset();
        switch (icon) {
            case PLAY:
                path.moveTo(x, cy - s / 2);
                path.lineTo(x + s, cy);
                path.lineTo(x, cy + s / 2);
                path.close();
                cv.drawPath(path, p);
                break;
            case STOP:
                cv.drawRoundRect(x + s * 0.05f, cy - s * 0.45f, x + s * 0.95f, cy + s * 0.45f, 3, 3, p);
                break;
            case REC:
                cv.drawCircle(x + s / 2, cy, s * 0.46f, p);
                break;
            case MIC:
                cv.drawRoundRect(x + s * 0.3f, cy - s * 0.55f, x + s * 0.7f, cy + s * 0.15f, s * 0.2f, s * 0.2f, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Ui.dpf(1.5f));
                r.set(x + s * 0.12f, cy - s * 0.35f, x + s * 0.88f, cy + s * 0.4f);
                cv.drawArc(r, 0, 180, false, p);
                cv.drawLine(x + s / 2, cy + s * 0.4f, x + s / 2, cy + s * 0.65f, p);
                p.setStyle(Paint.Style.FILL);
                break;
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressed = true;
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                boolean in = e.getX() >= 0 && e.getX() <= getWidth() && e.getY() >= 0 && e.getY() <= getHeight();
                if (in != pressed) { pressed = in; invalidate(); }
                return true;
            case MotionEvent.ACTION_UP:
                boolean fire = pressed;
                pressed = false;
                invalidate();
                if (fire) performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                pressed = false;
                invalidate();
                return true;
        }
        return true;
    }
}
