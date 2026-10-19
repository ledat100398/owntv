package io.github.ledat100398.tvhome;

import android.content.Context;
import android.graphics.Typeface;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/**
 * Layout is specified in design pixels on a 1920 x 1080 frame (the same numbers as the approved mockup),
 * then scaled to whatever density the TV reports. One design pixel = {@link #u} real pixels.
 */
final class Ui {

    static float u = 1f;
    static Typeface medium, semibold, display;

    // Palette (no pure black or white over large areas).
    static final int INK = 0xFFF2F2F5;        // primary text
    static final int INK_DIM = 0xB3F2F2F5;    // secondary text, 70%
    static final int DARK = 0xFF0B0B0F;       // text on a focused (white) control
    static final int DARK_DIM = 0x8C0B0B0F;
    static final int OK_GREEN = 0xFF30D158;

    // Motion (focus 150-200 ms, ease-out, interruptible, no long bounce).
    static final float FOCUS_STIFFNESS = 800f;
    static final float FOCUS_DAMPING = 0.9f;

    private Ui() {
    }

    static void init(Context c) {
        DisplayMetrics m = c.getResources().getDisplayMetrics();
        u = Math.max(m.widthPixels, m.heightPixels) / 1920f;
        if (medium == null) {
            // Inter: the closest open typeface to Apple's SF Pro, with full Vietnamese. Text cut for UI
            // sizes, Display cut (tighter, finer) for the large clock.
            medium = c.getResources().getFont(R.font.inter_medium);
            semibold = c.getResources().getFont(R.font.inter_semibold);
            display = c.getResources().getFont(R.font.inter_display_semibold);
        }
    }

    static int px(float design) {
        return Math.round(design * u);
    }

    static float f(float design) {
        return design * u;
    }

    static TextView text(Context c, float size, boolean bold, int color) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, f(size));
        t.setTypeface(bold ? semibold : medium);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        t.setLetterSpacing(-0.01f);   // SF-style tracking at 28 px
        return t;
    }

    /** Springs one property of a view toward a target. Calling again mid-flight retargets smoothly. */
    static void spring(View v, int tag, DynamicAnimation.ViewProperty prop, float to, float stiffness, float damping) {
        Object t = v.getTag(tag);
        SpringAnimation s;
        if (t instanceof SpringAnimation) {
            s = (SpringAnimation) t;
        } else {
            s = new SpringAnimation(v, prop);
            s.setSpring(new SpringForce());
            v.setTag(tag, s);
        }
        s.getSpring().setStiffness(stiffness).setDampingRatio(damping);
        s.animateToFinalPosition(to);
    }

    static void focusSpring(View v, int tag, DynamicAnimation.ViewProperty prop, float to) {
        spring(v, tag, prop, to, FOCUS_STIFFNESS, FOCUS_DAMPING);
    }

    /** Stops any spring on this property and jumps to a value (used when a recycled view is rebound). */
    static void set(View v, int tag, DynamicAnimation.ViewProperty prop, float value) {
        Object t = v.getTag(tag);
        if (t instanceof SpringAnimation) ((SpringAnimation) t).cancel();
        prop.setValue(v, value);
    }
}
