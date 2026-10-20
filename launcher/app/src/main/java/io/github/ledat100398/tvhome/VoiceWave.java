package io.github.ledat100398.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

/**
 * Five thin bars at the end of the listening bar. They breathe softly while waiting, follow the voice
 * while someone speaks (smoothed, so a cough does not make them jump), collapse into dots that light up
 * in turn while the words are being understood, and lie flat and dim after a miss.
 * Drawn every frame with anti-aliased round rects only while the view is on screen.
 */
final class VoiceWave extends View {

    static final int IDLE = 0, SPEECH = 1, THINK = 2, REST = 3;
    private static final int BARS = 5;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float[] bar = new float[BARS];      // smoothed height of each bar, 0..1
    private final float[] alpha = new float[BARS];
    private final long born = SystemClock.uptimeMillis();
    private int state = IDLE;
    private float target, level;                      // raw voice level and its smoothed value, 0..1

    VoiceWave(Context c) {
        super(c);
        paint.setColor(Ui.INK);
        for (int i = 0; i < BARS; i++) alpha[i] = 1f;
    }

    void setState(int s) {
        state = s;
        if (s != SPEECH) target = 0f;
        postInvalidateOnAnimation();
    }

    /** @param db the recogniser's RMS reading, roughly -2 (silence) to 10 (loud). */
    void setLevel(float db) {
        target = Math.max(0f, Math.min(1f, (db + 2f) / 11f));
    }

    @Override
    protected void onDraw(Canvas c) {
        float t = (SystemClock.uptimeMillis() - born) / 1000f;
        level += (target - level) * 0.16f;
        float w = Ui.f(6), gap = Ui.f(7), full = getHeight() * 0.9f, cy = getHeight() / 2f;
        float x = (getWidth() - (BARS * w + (BARS - 1) * gap)) / 2f;
        for (int i = 0; i < BARS; i++) {
            float goal, a;
            switch (state) {
                case SPEECH: {
                    float wobble = (float) Math.abs(Math.sin(t * (5.5f + i * 0.9f) + i * 1.7f));
                    goal = 0.17f + level * (0.3f + 0.7f * wobble) * 0.8f;
                    a = 1f;
                    break;
                }
                case THINK: {
                    goal = 0f;                    // dots
                    a = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(t * 6f - i * 0.8f));
                    break;
                }
                case REST:
                    goal = 0f;
                    a = 0.35f;
                    break;
                default:   // IDLE: a slow ripple running across the bars
                    goal = 0.14f + 0.07f * (0.5f + 0.5f * (float) Math.sin(t * 3.1f + i * 1.2f));
                    a = 0.9f;
            }
            bar[i] += (goal - bar[i]) * 0.22f;
            alpha[i] += (a - alpha[i]) * 0.25f;
            float h = Math.max(w, bar[i] * full);
            r.set(x, cy - h / 2f, x + w, cy + h / 2f);
            paint.setAlpha(Math.round(255 * alpha[i]));
            c.drawRoundRect(r, w / 2f, w / 2f, paint);
            x += w + gap;
        }
        if (isAttachedToWindow() && getVisibility() == VISIBLE) postInvalidateOnAnimation();
    }
}
