package io.github.ledat100398.tvhome;

import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.view.Gravity;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.Random;

/**
 * Screensaver: date above a large clock on a near-black ground, the same type as the home screen.
 * Once a minute the pair glides to a new spot over six seconds, so nothing stays in one place.
 */
public class ClockDream extends DreamService {

    private static final long MOVE_MS = 60_000L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private FrameLayout root;
    private LinearLayout block;

    private final Runnable move = new Runnable() {
        @Override
        public void run() {
            glide();
            handler.postDelayed(this, MOVE_MS);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // any key wakes the TV back to where it was
        setFullscreen(true);
        setScreenBright(false);
        Ui.init(this);

        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B0B0F);
        block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);

        TextClock date = new TextClock(this);
        date.setFormat24Hour(getString(R.string.date_format));
        date.setFormat12Hour(getString(R.string.date_format));
        style(date, 36, 0x80F2F2F5);
        block.addView(date);

        TextClock time = new TextClock(this);
        time.setFormat24Hour("H:mm");
        time.setFormat12Hour("H:mm");
        style(time, 220, 0xB8F2F2F5);
        time.setTypeface(Ui.display);
        time.setLetterSpacing(-0.02f);
        time.setFontFeatureSettings("tnum");
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.topMargin = -Ui.px(8);
        block.addView(time, tl);

        root.addView(block, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START));
        setContentView(root);
        root.post(() -> {
            block.setTranslationX(Ui.f(620));
            block.setTranslationY(Ui.f(380));
        });
    }

    private void style(TextView t, float size, int color) {
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.f(size));
        t.setTypeface(Ui.semibold);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
    }

    private void glide() {
        int maxX = Math.max(0, root.getWidth() - block.getWidth() - Ui.px(96) * 2);
        int maxY = Math.max(0, root.getHeight() - block.getHeight() - Ui.px(60) * 2);
        block.animate()
                .translationX(Ui.px(96) + random.nextInt(maxX + 1))
                .translationY(Ui.px(60) + random.nextInt(maxY + 1))
                .setDuration(6000)
                .setInterpolator(new PathInterpolator(0.45f, 0f, 0.2f, 1f))
                .start();
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        handler.postDelayed(move, MOVE_MS);
    }

    @Override
    public void onDreamingStopped() {
        super.onDreamingStopped();
        handler.removeCallbacks(move);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        handler.removeCallbacksAndMessages(null);
        if (block != null) block.animate().cancel();
    }
}
