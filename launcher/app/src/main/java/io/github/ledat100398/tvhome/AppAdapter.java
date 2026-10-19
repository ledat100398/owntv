package io.github.ledat100398.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * App tiles: the app's own TV banner, a hairline glass rim, a specular glare and a name that appears
 * only on the focused tile. All views are built in code at design-pixel sizes (see {@link Ui}).
 */
public class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {

    // Design pixels on the 1920 x 1080 frame, matching the approved mockup.
    static final float TILE_W = 313f, TILE_H = 176f, RADIUS = 20f, ROW_GAP = 74f, LABEL_TOP = 204f;
    static final float FOCUS_SCALE = 1.12f, MOVE_SCALE = 1.16f;

    interface Host {
        void onTileFocus(int pos, Holder h, boolean focused);

        /** D-pad and Menu keys on a tile. Return true when handled. */
        boolean onTileKey(int pos, Holder h, int keyCode, KeyEvent e);

        void onTileClick(int pos);

        void onTileHold(int pos);

        /** Whether this position is the focused tile and whether it is being moved, for rebinds. */
        boolean isFocusedPos(int pos);

        boolean isMoving();
    }

    static final class Entry {
        final String key, label, pkg;
        final ComponentName component;
        final Drawable banner, icon;

        Entry(String label, ComponentName component, Drawable banner, Drawable icon) {
            this.label = label;
            this.component = component;
            this.pkg = component.getPackageName();
            this.key = component.flattenToShortString();
            this.banner = banner;
            this.icon = icon;
        }
    }

    private final Host host;
    private final List<Entry> items = new ArrayList<>();
    private final java.util.Map<String, Long> ids = new java.util.HashMap<>();
    private final int cellW;
    private Bitmap shadow;

    AppAdapter(Host host, int cellW) {
        this.host = host;
        this.cellW = cellW;
        setHasStableIds(true);
    }

    void setItems(List<Entry> fresh) {
        items.clear();
        items.addAll(fresh);
        notifyDataSetChanged();
    }

    List<Entry> items() {
        return items;
    }

    Entry at(int pos) {
        return pos >= 0 && pos < items.size() ? items.get(pos) : null;
    }

    int positionOf(String key) {
        if (key == null) return -1;
        for (int i = 0; i < items.size(); i++) if (items.get(i).key.equals(key)) return i;
        return -1;
    }

    /** Lifts one app out and drops it in at another position; the grid reflows around it. */
    void move(int from, int to) {
        Entry e = items.remove(from);
        items.add(to, e);
        notifyItemMoved(from, to);
    }

    void remove(int pos) {
        items.remove(pos);
        notifyItemRemoved(pos);
        notifyItemRangeChanged(pos, items.size() - pos);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public long getItemId(int position) {
        String k = items.get(position).key;
        Long id = ids.get(k);
        if (id == null) {
            id = (long) ids.size();
            ids.put(k, id);
        }
        return id;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (shadow == null) shadow = makeShadow();
        return new Holder(parent.getContext(), cellW, shadow, host);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Entry e = items.get(position);
        h.label.setText(e.label);
        h.card.setContentDescription(e.label);
        h.card.setAlpha(1f);   // a tile hidden under the opening animation never stays hidden after a rebind
        if (e.banner != null) {
            h.banner.setImageDrawable(e.banner);
            h.banner.setVisibility(View.VISIBLE);
            h.icon.setVisibility(View.GONE);
        } else {
            h.banner.setImageDrawable(null);
            h.banner.setVisibility(View.GONE);
            h.icon.setImageDrawable(e.icon);
            h.icon.setVisibility(View.VISIBLE);
        }
        h.applyState(host.isFocusedPos(position), host.isFocusedPos(position) && host.isMoving(), false);
    }

    /** One soft shadow, drawn once and reused by every tile (no live shadow radius). */
    private static Bitmap makeShadow() {
        float pad = Ui.f(70f);
        int w = Math.round(Ui.f(TILE_W) + 2 * pad), h = Math.round(Ui.f(TILE_H) + 2 * pad);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xB8000000);
        p.setMaskFilter(new BlurMaskFilter(Ui.f(34f), BlurMaskFilter.Blur.NORMAL));
        RectF r = new RectF(pad + Ui.f(10f), pad + Ui.f(22f), w - pad - Ui.f(10f), h - pad + Ui.f(18f));
        c.drawRoundRect(r, Ui.f(RADIUS), Ui.f(RADIUS), p);
        return b;
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final FrameLayout card;
        final ImageView banner, icon, shadowView;
        final View glare, ring;
        final TextView label;

        Holder(Context c, int cellW, Bitmap shadow, Host host) {
            super(new FrameLayout(c));
            FrameLayout item = (FrameLayout) itemView;
            int tw = Ui.px(TILE_W), th = Ui.px(TILE_H);
            item.setLayoutParams(new RecyclerView.LayoutParams(cellW, th + Ui.px(ROW_GAP)));
            item.setClipChildren(false);
            item.setClipToPadding(false);

            shadowView = new ImageView(c);
            shadowView.setImageBitmap(shadow);
            FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(shadow.getWidth(), shadow.getHeight());
            slp.leftMargin = (cellW - shadow.getWidth()) / 2;
            slp.topMargin = (th - shadow.getHeight()) / 2;
            item.addView(shadowView, slp);
            shadowView.setAlpha(0.45f);

            card = new FrameLayout(c);
            FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(tw, th);
            clp.leftMargin = (cellW - tw) / 2;
            item.addView(card, clp);
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF2C2C33, 0xFF141418});
            card.setBackground(bg);
            final float r = Ui.f(RADIUS);
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
                }
            });
            card.setClipToOutline(true);
            card.setFocusable(true);
            card.setClickable(true);
            card.setDefaultFocusHighlightEnabled(false);
            card.setLongClickable(true);
            card.setPivotX(tw / 2f);
            card.setPivotY(th / 2f);

            banner = new ImageView(c);
            banner.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(banner, new FrameLayout.LayoutParams(-1, -1));

            icon = new ImageView(c);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            card.addView(icon, new FrameLayout.LayoutParams(Ui.px(76), Ui.px(76), Gravity.CENTER));

            // Hairline glass rim with a brighter top edge.
            View rim = new View(c);
            GradientDrawable rimD = new GradientDrawable();
            rimD.setCornerRadius(r);
            rimD.setColor(0x00000000);
            rimD.setStroke(Math.max(1, Ui.px(1.2f)), 0x2EFFFFFF);
            rim.setBackground(rimD);
            card.addView(rim, new FrameLayout.LayoutParams(-1, -1));

            glare = new View(c);
            GradientDrawable g = new GradientDrawable();
            g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
            g.setGradientCenter(0.26f, 0f);
            g.setGradientRadius(tw * 0.85f);
            g.setColors(new int[]{0x66FFFFFF, 0x1AFFFFFF, 0x00FFFFFF});
            glare.setBackground(g);
            glare.setAlpha(0f);
            card.addView(glare, new FrameLayout.LayoutParams(-1, -1));

            ring = new View(c);
            GradientDrawable rg = new GradientDrawable();
            rg.setCornerRadius(r);
            rg.setColor(0x00000000);
            rg.setStroke(Ui.px(4), 0xEBFFFFFF);
            ring.setBackground(rg);
            ring.setAlpha(0f);
            card.addView(ring, new FrameLayout.LayoutParams(-1, -1));

            label = Ui.text(c, 28, true, Ui.INK);
            label.setGravity(Gravity.CENTER_HORIZONTAL);
            label.setShadowLayer(Ui.f(12), 0, Ui.f(2), 0x8C000000);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            label.setAlpha(0f);
            FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(cellW, -2);
            llp.topMargin = Ui.px(LABEL_TOP);
            // No names under the tiles: the app's own banner says what it is (no extra text).
            label.setVisibility(View.GONE);
            item.addView(label, llp);

            card.setOnFocusChangeListener((v, focused) -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) host.onTileFocus(pos, this, focused);
            });
            card.setOnKeyListener((v, keyCode, event) -> {
                int pos = getBindingAdapterPosition();
                return pos != RecyclerView.NO_POSITION && host.onTileKey(pos, this, keyCode, event);
            });
            card.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) host.onTileClick(pos);
            });
            card.setOnLongClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) host.onTileHold(pos);
                return true;
            });
        }

        /** Focused / moving look. Animated with springs, or set at once when a view is rebound. */
        void applyState(boolean focused, boolean moving, boolean animate) {
            float scale = moving ? MOVE_SCALE : (focused ? FOCUS_SCALE : 1f);
            float lift = moving ? -Ui.f(14) : (focused ? -Ui.f(6) : 0f);
            float shadowA = focused ? 1f : 0.45f;
            if (animate) {
                Ui.focusSpring(card, R.id.spring_sx, androidx.dynamicanimation.animation.DynamicAnimation.SCALE_X, scale);
                Ui.focusSpring(card, R.id.spring_sy, androidx.dynamicanimation.animation.DynamicAnimation.SCALE_Y, scale);
                Ui.focusSpring(card, R.id.spring_ty, androidx.dynamicanimation.animation.DynamicAnimation.TRANSLATION_Y, lift);
                Ui.focusSpring(shadowView, R.id.spring_sx, androidx.dynamicanimation.animation.DynamicAnimation.SCALE_X, scale);
                Ui.focusSpring(shadowView, R.id.spring_sy, androidx.dynamicanimation.animation.DynamicAnimation.SCALE_Y, scale);
                Ui.focusSpring(shadowView, R.id.spring_ty, androidx.dynamicanimation.animation.DynamicAnimation.TRANSLATION_Y, lift * 0.5f);
                shadowView.animate().alpha(shadowA).setDuration(180).start();
                glare.animate().alpha(focused ? 1f : 0f).setDuration(180).start();
                ring.animate().alpha(moving ? 1f : 0f).setDuration(180).start();
                label.animate().alpha(focused && !moving ? 1f : 0f).translationY(focused ? Ui.f(4) : -Ui.f(4))
                        .setDuration(180).start();
            } else {
                Ui.set(card, R.id.spring_sx, androidx.dynamicanimation.animation.DynamicAnimation.SCALE_X, scale);
                Ui.set(card, R.id.spring_sy, androidx.dynamicanimation.animation.DynamicAnimation.SCALE_Y, scale);
                Ui.set(card, R.id.spring_ty, androidx.dynamicanimation.animation.DynamicAnimation.TRANSLATION_Y, lift);
                Ui.set(shadowView, R.id.spring_sx, androidx.dynamicanimation.animation.DynamicAnimation.SCALE_X, scale);
                Ui.set(shadowView, R.id.spring_sy, androidx.dynamicanimation.animation.DynamicAnimation.SCALE_Y, scale);
                Ui.set(shadowView, R.id.spring_ty, androidx.dynamicanimation.animation.DynamicAnimation.TRANSLATION_Y, lift * 0.5f);
                shadowView.setAlpha(shadowA);
                glare.setAlpha(focused ? 1f : 0f);
                ring.setAlpha(moving ? 1f : 0f);
                label.setAlpha(focused && !moving ? 1f : 0f);
                label.setTranslationY(focused ? Ui.f(4) : -Ui.f(4));
            }
            itemView.setZ(focused ? 1f : 0f);   // the lifted tile draws over its neighbours
        }
    }
}
