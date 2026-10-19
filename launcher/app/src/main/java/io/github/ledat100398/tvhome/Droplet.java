package io.github.ledat100398.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * The focus shape used across Home (the bar's sliding platter and the focused row in a panel):
 * a drop of frosted water rather than a flat white pill. Brighter where light hits at the top left,
 * a slightly darker belly, a soft shadow underneath, and the glass behind faintly showing through.
 * Drawn with anti-aliased round rects only, so its edge stays clean at any size.
 */
final class Droplet extends Drawable {

    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint belly = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float radius;
    private int alpha = 255;

    /** @param radius corner radius in px; use half the height for a circle or capsule. */
    Droplet(float radius) {
        this.radius = radius;
        shadow(255);
    }

    /** Shadow drawn by the hardware renderer with the shape (API 28+); faded with the drop itself. */
    private void shadow(int a) {
        body.setShadowLayer(Ui.f(18), 0, Ui.f(7), (Math.round(0x3D * a / 255f) << 24));
    }

    @Override
    public int getAlpha() {
        return alpha;
    }

    @Override
    protected void onBoundsChange(Rect b) {
        super.onBoundsChange(b);
        r.set(b);
        float w = Math.max(1, b.width()), h = Math.max(1, b.height());
        body.setShader(new RadialGradient(b.left + w * 0.32f, b.top + h * 0.18f, Math.max(w, h) * 1.2f,
                new int[]{0xB8FFFFFF, 0x9EFAFBFD, 0x8CE2E7F0}, new float[]{0f, 0.46f, 1f}, Shader.TileMode.CLAMP));
        belly.setShader(new LinearGradient(0, b.top + h * 0.5f, 0, b.bottom,
                0x006E7A91, 0x386E7A91, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(Canvas c) {
        if (alpha == 0) return;
        float rad = Math.min(radius, Math.min(r.width(), r.height()) / 2f);
        body.setAlpha(alpha);
        belly.setAlpha(alpha);
        c.drawRoundRect(r, rad, rad, body);
        c.drawRoundRect(r, rad, rad, belly);
    }

    @Override
    public void setAlpha(int a) {
        if (a == alpha) return;
        alpha = a;
        shadow(a);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
