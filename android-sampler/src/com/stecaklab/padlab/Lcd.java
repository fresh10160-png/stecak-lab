package com.stecaklab.padlab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Top display: BPM, selected pad, stereo VU and the sequencer position LEDs. */
final class Lcd extends View {
    private final MainActivity act;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Runnable tick = new Runnable() {
        public void run() {
            invalidate();
            if (isAttachedToWindow()) postOnAnimation(this);
        }
    };

    Lcd(Context c, MainActivity act) {
        super(c);
        this.act = act;
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        postOnAnimation(tick);
    }

    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension(resolveSize(Ui.dp(300), w), resolveSize(Ui.dp(88), h));
    }

    @Override protected void onDraw(Canvas cv) {
        Engine e = act.engine;
        float w = getWidth(), h = getHeight(), rad = Ui.dpf(12);
        r.set(0, 0, w, h);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Ui.LCD);
        cv.drawRoundRect(r, rad, rad, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Ui.dpf(1));
        p.setColor(Ui.STROKE);
        cv.drawRoundRect(r, rad, rad, p);
        p.setStyle(Paint.Style.FILL);

        int bankCol = Ui.BANK[act.selected / 16];
        // BPM
        p.setTypeface(Ui.BOLD);
        p.setTextSize(Ui.dpf(9));
        p.setColor(Ui.DIM);
        cv.drawText("BPM", Ui.dpf(14), Ui.dpf(18), p);
        p.setTypeface(Ui.MONO);
        p.setTextSize(Ui.dpf(34));
        p.setColor(Ui.ACCENT);
        String bpm = String.valueOf(Math.round(e.bpm));
        cv.drawText(bpm, Ui.dpf(12), Ui.dpf(54), p);
        p.setTextSize(Ui.dpf(10));
        p.setColor(e.playing ? Ui.GREEN : Ui.DIM);
        cv.drawText(e.playing ? "PLAYING" : "STOPPED", Ui.dpf(14), Ui.dpf(68), p);

        // pad
        float mx = w * 0.36f;
        Pad pad = e.pads[act.selected];
        p.setTypeface(Ui.BOLD);
        p.setTextSize(Ui.dpf(9));
        p.setColor(Ui.DIM);
        cv.drawText("PAD", mx, Ui.dpf(18), p);
        p.setTypeface(Ui.MONO);
        p.setTextSize(Ui.dpf(26));
        p.setColor(bankCol);
        cv.drawText((char) ('A' + act.selected / 16) + String.format("%02d", act.selected % 16 + 1), mx, Ui.dpf(46), p);
        p.setTypeface(Ui.BOLD);
        p.setTextSize(Ui.dpf(11));
        p.setColor(Ui.TEXT);
        String name = pad.sample == null ? "— empty —" : pad.label();
        if (name.length() > 12) name = name.substring(0, 12);
        cv.drawText(name, mx, Ui.dpf(62), p);
        p.setTextSize(Ui.dpf(9.5f));
        p.setColor(Ui.DIM);
        String info = pad.sample == null ? "" : String.format("%.2fs  %s", pad.sample.n / (float) Engine.SR, Pad.MODES[pad.mode]);
        cv.drawText(info, mx, Ui.dpf(74), p);

        // VU
        float vx = w - Ui.dpf(44);
        vu(cv, vx, e.vuL);
        vu(cv, vx + Ui.dpf(16), e.vuR);

        // step LEDs
        int cur = e.curStep;
        float gx = Ui.dpf(14), gw = w - Ui.dpf(28), cell = gw / 16f;
        for (int i = 0; i < 16; i++) {
            boolean on = i == cur;
            p.setColor(on ? Ui.ACCENT : (i % 4 == 0 ? 0xFF2A2E37 : 0xFF1B1E24));
            cv.drawRoundRect(gx + i * cell + 1, h - Ui.dpf(9), gx + (i + 1) * cell - 1, h - Ui.dpf(5), 2, 2, p);
        }
    }

    private void vu(Canvas cv, float x, float level) {
        int segs = 14;
        float h = Ui.dpf(52), top = Ui.dpf(12), seg = h / segs;
        float lv = (float) Math.min(1.0, Math.sqrt(level));
        for (int i = 0; i < segs; i++) {
            boolean lit = lv > (i + 0.5f) / segs;
            int col = i >= 12 ? Ui.REC : i >= 9 ? 0xFFFFC83D : Ui.GREEN;
            p.setColor(lit ? col : 0xFF1B1E24);
            float y1 = top + h - i * seg;
            cv.drawRoundRect(x, y1 - seg + 1.5f, x + Ui.dpf(11), y1 - 1.5f, 2, 2, p);
        }
    }
}
