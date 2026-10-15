package com.stecaklab.padlab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

final class WaveView extends View {
    private final Paint paint = new Paint();
    private Sample sample;
    private float start, end = 1f;
    private float[] peaks;
    private int peaksW = -1;
    private Sample peaksFor;

    WaveView(Context c) {
        super(c);
        setBackground(Ui.rounded(0xFF0b0c0e, 8));
    }

    void set(Sample s, float start, float end) {
        this.sample = s;
        this.start = start;
        this.end = end;
        invalidate();
    }

    @Override protected void onDraw(Canvas cv) {
        int w = getWidth(), h = getHeight();
        if (sample == null) {
            paint.setColor(Ui.DIM);
            paint.setTextSize(Ui.dp(13));
            cv.drawText("EMPTY — LOAD a file or record with MIC", Ui.dp(12), h / 2f, paint);
            return;
        }
        if (peaks == null || peaksW != w || peaksFor != sample) {
            peaks = new float[w];
            peaksW = w;
            peaksFor = sample;
            int n = sample.n;
            for (int x = 0; x < w; x++) {
                int a = (int) ((long) x * n / w), b = Math.max(a + 1, (int) ((long) (x + 1) * n / w));
                float pk = 0;
                int stride = Math.max(1, (b - a) / 64);
                for (int i = a; i < b && i < n; i += stride) pk = Math.max(pk, Math.abs(sample.l[i]));
                peaks[x] = pk;
            }
        }
        paint.setColor(Ui.DIM);
        for (int x = 0; x < w; x++) {
            float a = peaks[x] * h * 0.45f;
            cv.drawLine(x, h / 2f - a, x, h / 2f + a + 1, paint);
        }
        paint.setColor(0xAA000000);
        cv.drawRect(0, 0, start * w, h, paint);
        cv.drawRect(end * w, 0, w, h, paint);
        paint.setColor(Ui.ACCENT);
        paint.setStrokeWidth(Ui.dp(2));
        cv.drawLine(start * w, 0, start * w, h, paint);
        cv.drawLine(end * w, 0, end * w, h, paint);
        paint.setStrokeWidth(0);
    }
}
