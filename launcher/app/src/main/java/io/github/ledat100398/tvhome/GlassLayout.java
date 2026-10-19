package io.github.ledat100398.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.widget.FrameLayout;

/**
 * Frosted glass container without a border. It paints the pre-blurred wallpaper that sits behind it
 * on screen, a faint tint, and a soft sheen of light from the top left, then its children on top.
 * Every shape is an anti-aliased round rect (no clipPath), so edges stay smooth on a large panel.
 */
public class GlassLayout extends FrameLayout {

    private final Bitmap blur;
    private final Paint glass = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final BitmapShader shader;
    private final Matrix matrix = new Matrix();
    private final RectF rect = new RectF();
    private final int[] loc = new int[2];
    private float radius;

    public GlassLayout(Context c) {
        super(c);
        setWillNotDraw(false);
        blur = Backdrop.blur(c);
        shader = new BitmapShader(blur, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        glass.setShader(shader);
        tint.setColor(0x14FFFFFF);
    }

    void setRadius(float px) {
        radius = px;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        // Light pooling at the top left and fading out: depth without drawing an edge.
        sheen.setShader(new RadialGradient(w * 0.3f, -h * 0.3f, Math.max(1f, Math.max(w, h * 2f) * 1.1f),
                new int[]{0x29FFFFFF, 0x0AFFFFFF, 0x00FFFFFF}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        // Map the blurred wallpaper so the slice under this view lines up with the real wallpaper.
        getLocationInWindow(loc);
        float bw = Backdrop.width > 0 ? Backdrop.width : getRootView().getWidth();
        float bh = Backdrop.height > 0 ? Backdrop.height : getRootView().getHeight();
        matrix.setScale(bw / blur.getWidth(), bh / blur.getHeight());
        float x = loc[0] - getTranslationX(), y = loc[1] - getTranslationY();
        matrix.postTranslate(Backdrop.left - x, Backdrop.top - y);
        shader.setLocalMatrix(matrix);

        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, radius, radius, glass);
        canvas.drawRoundRect(rect, radius, radius, tint);
        canvas.drawRoundRect(rect, radius, radius, sheen);
    }
}
