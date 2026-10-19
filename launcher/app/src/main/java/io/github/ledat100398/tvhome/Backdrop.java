package io.github.ledat100398.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/**
 * The wallpaper, plus a blurred and darkened copy made once at a tiny size (240 x 135) and stretched.
 * Glass surfaces and the app-grid backdrop draw that copy instead of blurring in real time
 * (never blur the full screen live). Both live for the life of the process.
 */
final class Backdrop {

    private static Bitmap sharp;
    private static Bitmap blur;
    /** Where the wallpaper view sits in the window (it is larger than the screen, for the glide). */
    static int left, top, width = -1, height = -1;

    private Backdrop() {
    }

    static void place(int l, int t, int w, int h) {
        left = l;
        top = t;
        width = w;
        height = h;
    }

    static synchronized Bitmap sharp(Context c) {
        if (sharp == null) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;   // 565 would band the sky on a large panel
            o.inScaled = false;
            sharp = BitmapFactory.decodeResource(c.getResources(), R.drawable.wallpaper, o);
        }
        return sharp;
    }

    static synchronized Bitmap blur(Context c) {
        if (blur == null) {
            Bitmap small = Bitmap.createScaledBitmap(sharp(c), 240, 135, true);
            int w = small.getWidth(), h = small.getHeight();
            int[] px = new int[w * h];
            small.getPixels(px, 0, w, 0, 0, w, h);
            for (int pass = 0; pass < 3; pass++) {          // three box passes approximate a gaussian
                boxH(px, w, h, 4);
                boxV(px, w, h, 4);
            }
            for (int i = 0; i < px.length; i++) {           // a touch more colour, then darken for legibility
                int p = px[i];
                float r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
                float l = 0.299f * r + 0.587f * g + 0.114f * b;
                r = clamp((l + (r - l) * 1.2f) * 0.58f);
                g = clamp((l + (g - l) * 1.2f) * 0.58f);
                b = clamp((l + (b - l) * 1.2f) * 0.58f);
                px[i] = 0xFF000000 | ((int) r << 16) | ((int) g << 8) | (int) b;
            }
            blur = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
            small.recycle();
        }
        return blur;
    }

    private static float clamp(float v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    private static void boxH(int[] px, int w, int h, int r) {
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            int o = y * w;
            System.arraycopy(px, o, row, 0, w);
            for (int x = 0; x < w; x++) {
                int sr = 0, sg = 0, sb = 0, n = 0;
                for (int k = -r; k <= r; k++) {
                    int p = row[Math.max(0, Math.min(w - 1, x + k))];
                    sr += (p >> 16) & 0xFF;
                    sg += (p >> 8) & 0xFF;
                    sb += p & 0xFF;
                    n++;
                }
                px[o + x] = 0xFF000000 | ((sr / n) << 16) | ((sg / n) << 8) | (sb / n);
            }
        }
    }

    private static void boxV(int[] px, int w, int h, int r) {
        int[] col = new int[h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) col[y] = px[y * w + x];
            for (int y = 0; y < h; y++) {
                int sr = 0, sg = 0, sb = 0, n = 0;
                for (int k = -r; k <= r; k++) {
                    int p = col[Math.max(0, Math.min(h - 1, y + k))];
                    sr += (p >> 16) & 0xFF;
                    sg += (p >> 8) & 0xFF;
                    sb += p & 0xFF;
                    n++;
                }
                px[y * w + x] = 0xFF000000 | ((sr / n) << 16) | ((sg / n) << 8) | (sb / n);
            }
        }
    }
}
