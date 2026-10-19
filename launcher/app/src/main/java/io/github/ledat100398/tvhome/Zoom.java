package io.github.ledat100398.tvhome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Picture;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;

import androidx.dynamicanimation.animation.DynamicAnimation;

/**
 * Apple TV style app opening, drawn by the launcher itself so it looks the same for every app.
 *
 * 1. Press: the tile gives a little (1.12 to 1.07), so the hand feels the click.
 * 2. Grow: a plate in the tile's own colour grows from exactly where the tile is to the full screen.
 *    Its corners straighten out and it darkens as it goes. The tile's picture rides along only a
 *    little (at most 1.25x) and fades out early, so nothing is ever blown up into a blurry image.
 * 3. The app fades in on top of the dark plate. Most TV apps start on a dark screen, so there is
 *    no white flash between the plate and the app.
 * 4. Back to Home: the same plate shrinks back into the tile and the picture fades back in.
 *
 * Everything is drawn by one view with anti-aliased round rects, recomputed every frame at the real
 * size: no scaled-up layers, so the corners stay smooth at every size.
 */
final class Zoom {

    private static final long PRESS_MS = 90, GROW_MS = 380, SHRINK_MS = 340, FADE_MS = 220;
    private static final long LOGO_OUT_MS = 170, LOGO_IN_DELAY_MS = 140, LOGO_IN_MS = 200;
    private static final float PRESS_SCALE = 1.07f, LOGO_GROW = 0.25f, DIM = 0.85f;
    /** The picture is captured a bit larger than the tile so it stays sharp while it rides along. */
    private static final float SNAP_RES = 1.4f;

    private static final Interpolator GROW = new PathInterpolator(0.2f, 0.8f, 0.2f, 1f);
    private static final Interpolator SHRINK = new PathInterpolator(0.3f, 0f, 0.1f, 1f);
    private static final Interpolator EASE = new PathInterpolator(0.25f, 0.1f, 0.25f, 1f);

    private static final int IDLE = 0, PRESS = 1, GROWING = 2, OPEN = 3, CLOSING = 4;

    private final Plate plate;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ValueAnimator anim;
    private int phase = IDLE;
    private AppAdapter.Holder held;
    private String key;
    private Runnable pendingGrow;

    Zoom(FrameLayout root, int sw, int sh) {
        plate = new Plate(root.getContext(), sw, sh);
        plate.setVisibility(View.GONE);
        root.addView(plate, new FrameLayout.LayoutParams(-1, -1));
    }

    /** True from the press until Home is fully back; keys on tiles are ignored meanwhile. */
    boolean isBusy() {
        return phase != IDLE;
    }

    /** True while the plate fills the screen behind an app (Home should shrink it back on resume). */
    boolean isOpen() {
        return phase == OPEN;
    }

    /** Key of the app that was opened, to find its tile again when Home comes back. */
    String key() {
        return key;
    }

    void open(AppAdapter.Holder h, String appKey, Runnable then) {
        if (phase != IDLE) return;
        phase = PRESS;
        held = h;
        key = appKey;
        plate.capture(h.card);                  // now, while the press hides the few ms it takes

        springScale(h.card, PRESS_SCALE, 1500f);
        springScale(h.shadowView, PRESS_SCALE, 1500f);
        pendingGrow = () -> grow(then);
        handler.postDelayed(pendingGrow, PRESS_MS);
    }

    private void grow(Runnable then) {
        pendingGrow = null;
        if (phase != PRESS || held == null) return;
        phase = GROWING;
        View card = held.card;
        tileRect(card, plate.from);              // where the pressed tile is right now: no jump
        plate.set(0f, 0f, 1f, 0f, 1f);
        plate.setVisibility(View.VISIBLE);
        card.setAlpha(0f);                       // same frame as the plate's first draw
        held.shadowView.animate().alpha(0f).setDuration(GROW_MS).start();

        run(GROW_MS, f -> {
            float logo = 1f - EASE.getInterpolation(Math.min(1f, f * GROW_MS / LOGO_OUT_MS));
            float g = GROW.getInterpolation(f);
            plate.set(g, darkFor(g), logo, EASE.getInterpolation(f), 1f);
        }, () -> {
            phase = OPEN;
            if (then != null) then.run();
        });
    }

    /**
     * Home is back: shrink into {@code target} (the opened app's tile, focused), or simply fade out
     * when that tile is not on screen any more.
     */
    void close(AppAdapter.Holder target) {
        if (phase != OPEN) return;
        phase = CLOSING;
        if (held != null && held != target) restore(held);
        held = target;
        if (target == null || !target.card.isAttachedToWindow() || target.card.getWidth() == 0) {
            run(FADE_MS, f -> plate.set(1f, 1f, 0f, 1f, 1f - EASE.getInterpolation(f)), this::finish);
            return;
        }
        View card = target.card;
        target.applyState(card.isFocused(), false, false);   // final size, so the plate lands exactly on it
        card.setAlpha(0f);
        target.shadowView.setAlpha(0f);
        target.shadowView.animate().alpha(card.isFocused() ? 1f : 0.45f).setDuration(SHRINK_MS).start();
        tileRect(card, plate.from);

        run(SHRINK_MS, f -> {
            float ms = f * SHRINK_MS;
            float logo = EASE.getInterpolation(clamp((ms - LOGO_IN_DELAY_MS) / LOGO_IN_MS));
            float g = 1f - SHRINK.getInterpolation(f);
            plate.set(g, darkFor(g), logo, 1f - EASE.getInterpolation(f), 1f);
        }, this::finish);
    }

    /** Hides at once (e.g. the app failed to open). */
    void reset() {
        if (anim != null) anim.cancel();
        if (pendingGrow != null) handler.removeCallbacks(pendingGrow);
        pendingGrow = null;
        finish();
    }

    private void finish() {
        if (held != null) restore(held);
        held = null;
        plate.setVisibility(View.GONE);
        phase = IDLE;
    }

    private static void restore(AppAdapter.Holder h) {
        h.card.setAlpha(1f);
        h.shadowView.animate().cancel();
        h.applyState(h.card.isFocused(), false, false);
    }

    private interface Frame {
        void at(float f);
    }

    private void run(long ms, Frame frame, Runnable end) {
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(ms);
        anim.setInterpolator(new LinearInterpolator());   // each property applies its own curve
        anim.addUpdateListener(v -> frame.at((float) v.getAnimatedValue()));
        anim.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!cancelled) {
                    frame.at(1f);
                    if (end != null) end.run();
                }
            }
        });
        anim.start();
    }

    private static void springScale(View v, float to, float stiffness) {
        Ui.spring(v, R.id.spring_sx, DynamicAnimation.SCALE_X, to, stiffness, 1f);
        Ui.spring(v, R.id.spring_sy, DynamicAnimation.SCALE_Y, to, stiffness, 1f);
    }

    /** The tile's rect on screen, its current scale and lift included. */
    private static void tileRect(View card, RectF out) {
        int[] at = new int[2];
        card.getLocationInWindow(at);
        out.set(at[0], at[1], at[0] + card.getWidth() * card.getScaleX(), at[1] + card.getHeight() * card.getScaleY());
    }

    /**
     * How dark the plate is for a given size. It darkens ahead of its growth, so a light tile never
     * becomes a big bright surface on the way (that would read as a flash): nearly dark by the time
     * it covers half the screen. The same rule on the way back, so it lightens only as it lands.
     */
    private static float darkFor(float g) {
        float x = 1f - clamp(g / 0.75f);
        return 1f - x * x;
    }

    private static float clamp(float x) {
        return x < 0f ? 0f : (x > 1f ? 1f : x);
    }

    /** The one view that draws the dim, the plate and the tile's picture. */
    private static final class Plate extends View {

        final RectF from = new RectF();
        private final RectF full, rect = new RectF(), logoRect = new RectF();
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pic = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix matrix = new Matrix();
        private Bitmap snap;
        private BitmapShader shader;
        private int edge = 0xFF2C2C33, deep = Ui.DARK;
        private float grow, dark, logo, dim, alpha = 1f;

        Plate(Context c, int sw, int sh) {
            super(c);
            full = new RectF(0, 0, sw, sh);
            setWillNotDraw(false);
        }

        /** Takes the tile's picture (banner or icon, rim and glare as they look now) and its edge colour. */
        void capture(View card) {
            int w = Math.max(1, Math.round(card.getWidth() * SNAP_RES));
            int h = Math.max(1, Math.round(card.getHeight() * SNAP_RES));
            if (snap == null || snap.getWidth() != w || snap.getHeight() != h) {
                if (snap != null) snap.recycle();
                snap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            }
            snap.eraseColor(0);
            try {
                Canvas c = new Canvas(snap);
                c.scale(SNAP_RES, SNAP_RES);
                card.draw(c);
            } catch (RuntimeException softwareRefused) {
                // A banner held as a hardware bitmap cannot be drawn in software: record it and let
                // the GPU render it, then copy it back into a normal bitmap.
                try {
                    if (android.os.Build.VERSION.SDK_INT < 28) throw softwareRefused;
                    Picture p = new Picture();
                    Canvas rc = p.beginRecording(w, h);
                    rc.scale(SNAP_RES, SNAP_RES);
                    card.draw(rc);
                    p.endRecording();
                    Bitmap hw = Bitmap.createBitmap(p, w, h, Bitmap.Config.ARGB_8888);
                    Bitmap sw = hw.copy(Bitmap.Config.ARGB_8888, true);   // mutable: reused next time
                    hw.recycle();
                    snap.recycle();
                    snap = sw;
                } catch (RuntimeException e) {
                    shader = null;
                    pic.setShader(null);
                    edge = 0xFF2C2C33;
                    deep = Ui.DARK;
                    return;
                }
            }
            shader = new BitmapShader(snap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            pic.setShader(shader);
            edge = edgeColour(snap);
            // Where the plate ends: almost black, keeping a trace of the app's colour.
            deep = 0xFF000000
                    | (mixDark((edge >> 16) & 0xFF, (Ui.DARK >> 16) & 0xFF) << 16)
                    | (mixDark((edge >> 8) & 0xFF, (Ui.DARK >> 8) & 0xFF) << 8)
                    | mixDark(edge & 0xFF, Ui.DARK & 0xFF);
        }

        private static int mixDark(int c, int d) {
            return Math.min(255, d + Math.round(c * 0.08f));
        }

        /** Average colour of a ring just inside the picture's border (skips the hairline rim). */
        private static int edgeColour(Bitmap b) {
            int w = b.getWidth(), h = b.getHeight();
            int ix = Math.max(2, w / 16), iy = Math.max(2, h / 16);
            long r = 0, g = 0, bl = 0, n = 0;
            int steps = 24;
            for (int i = 0; i <= steps; i++) {
                int x = ix + (w - 1 - 2 * ix) * i / steps;
                int y = iy + (h - 1 - 2 * iy) * i / steps;
                int[] px = {b.getPixel(x, iy), b.getPixel(x, h - 1 - iy), b.getPixel(ix, y), b.getPixel(w - 1 - ix, y)};
                for (int p : px) {
                    r += (p >> 16) & 0xFF;
                    g += (p >> 8) & 0xFF;
                    bl += p & 0xFF;
                    n++;
                }
            }
            return 0xFF000000 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (bl / n);
        }

        /**
         * @param grow  0 = the tile's rect, 1 = full screen
         * @param dark  0 = the tile's edge colour, 1 = near black
         * @param logo  opacity of the tile's picture
         * @param dim   how much Home behind is dimmed (0..1 of {@link #DIM})
         * @param alpha overall opacity (only for the simple fade-out)
         */
        void set(float grow, float dark, float logo, float dim, float alpha) {
            this.grow = grow;
            this.dark = dark;
            this.logo = logo;
            this.dim = dim;
            this.alpha = alpha;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            if (alpha <= 0f) return;
            int dimA = Math.round(255 * DIM * dim * alpha);
            if (dimA > 0) c.drawColor((dimA << 24) | (Ui.DARK & 0xFFFFFF));

            float g = grow;
            rect.set(lerp(from.left, full.left, g), lerp(from.top, full.top, g),
                    lerp(from.right, full.right, g), lerp(from.bottom, full.bottom, g));
            float scale = from.width() / Math.max(1f, Ui.f(AppAdapter.TILE_W));
            float r0 = Ui.f(AppAdapter.RADIUS) * scale;
            float r = r0 * (1f - g);
            fill.setColor(mix(edge, deep, dark));
            fill.setAlpha(Math.round(255 * alpha));
            c.drawRoundRect(rect, r, r, fill);

            if (logo > 0.003f && shader != null) {
                float k = 1f + LOGO_GROW * g;
                float lw = from.width() * k, lh = from.height() * k;
                float cx = rect.centerX(), cy = rect.centerY();
                logoRect.set(cx - lw / 2f, cy - lh / 2f, cx + lw / 2f, cy + lh / 2f);
                matrix.setScale(lw / snap.getWidth(), lh / snap.getHeight());
                matrix.postTranslate(logoRect.left, logoRect.top);
                shader.setLocalMatrix(matrix);
                float lr = r0 * k;
                // The picture darkens exactly like the plate, so where its edge meets the plate there
                // is no visible border while it fades.
                int save = c.saveLayerAlpha(logoRect, Math.round(255 * logo * alpha));
                c.drawRoundRect(logoRect, lr, lr, pic);
                if (dark > 0f) {
                    shade.setColor(deep);
                    shade.setAlpha(Math.round(255 * dark));
                    c.drawRoundRect(logoRect, lr, lr, shade);
                }
                c.restoreToCount(save);
            }
        }

        private static float lerp(float a, float b, float t) {
            return a + (b - a) * t;
        }

        private static int mix(int a, int b, float t) {
            int r = Math.round(lerp((a >> 16) & 0xFF, (b >> 16) & 0xFF, t));
            int g = Math.round(lerp((a >> 8) & 0xFF, (b >> 8) & 0xFF, t));
            int bl = Math.round(lerp(a & 0xFF, b & 0xFF, t));
            return 0xFF000000 | r << 16 | g << 8 | bl;
        }
    }
}
