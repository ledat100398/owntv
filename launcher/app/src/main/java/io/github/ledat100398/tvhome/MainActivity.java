package io.github.ledat100398.tvhome;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Home screen in the approved design: a wallpaper that glides with the focus, app banners in five
 * columns, one frosted capsule top-right (Inputs, network, Settings, clock) and a frosted side panel
 * for Inputs and for the hold-OK menu. Navigation is decided here, by grid index, so focus always moves
 * exactly one tile in the pressed direction and never gets lost.
 */
public class MainActivity extends Activity implements AppAdapter.Host {

    private static final int COLS = 5;
    private static final long REPEAT_MIN_MS = 85;      // held key: at most ~12 moves per second
    private static final Object HINT_TOKEN = new Object();
    private static final PathInterpolator EASE_OUT = new PathInterpolator(0.22f, 0.85f, 0.3f, 1f);
    /** Settles with a small overshoot, like a light spring. */
    private static final PathInterpolator SPRING_OUT = new PathInterpolator(0.3f, 1.32f, 0.5f, 1f);

    // Survive the activity being recreated while an app is in front; reset only when the process dies.
    private static String lastKey;
    private static int lastPos;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private Sounds sounds;

    private FrameLayout root;
    private ImageView wall, wallBlur;
    private RecyclerView grid;
    private AppAdapter adapter;
    private GlassLayout bar, panel, hint;
    private Zoom zoom;
    private LinearLayout panelRows, panelStatus;
    private TextView panelTitle;
    private final View[] pills = new View[3];
    private final ImageView[] pillIcons = new ImageView[3];
    private final ValueAnimator[] iconTint = new ValueAnimator[3];
    private final boolean[] iconDark = new boolean[3];
    private View platter, scrim, barSep, bigClock;
    private TextView caption, barClock;
    private boolean platterShown, bigStyle;
    private int captionSeq;

    private int focusPos = -1;          // focused tile, or -1 when focus is in the bar or panel
    private int barIndex = 2;           // last pill used, so Up from the grid lands somewhere familiar
    private boolean inBar, deep, moving, panelOpen, dirty = true, firstFocus = true, resumed;
    private List<String> savedOrder;    // order before a move started, for Back to undo
    private int moveStart;
    private View panelOpener;
    private long lastMoveAt;
    private int scrollTarget;
    private int scrolled;              // grid scroll offset, tracked from onScrolled
    private int pendingFocus = -1;     // tile to focus as soon as its view is attached

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            dirty = true;
            if (hasWindowFocus()) reload();
        }
    };

    // ---- lifecycle -------------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.init(this);
        prefs = getSharedPreferences("home", MODE_PRIVATE);
        sounds = new Sounds(this);
        buildViews();
        applyHomeStyle(false);
        setContentView(root);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(packages, f, 4 /* Context.RECEIVER_NOT_EXPORTED */);
        else registerReceiver(packages, f);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        boolean reloading = dirty;
        if (dirty) reload();
        else restoreFocus();
        // Back from an app: the plate that filled the screen shrinks back into its tile. If the app
        // list is being reloaded (apps changed meanwhile) tiles may move, so it just fades instead.
        if (zoom.isOpen()) handler.postDelayed(() -> zoom.close(reloading ? null : holderFor(zoom.key())), 80);
    }

    /** Home pressed while already home: put away a panel or an unfinished move. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (panelOpen) closePanel();
        if (moving) endMove(false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(packages);
        io.shutdownNow();
        sounds.release();
    }

    // ---- views -----------------------------------------------------------------------------------

    private void buildViews() {
        root = new FrameLayout(this);
        root.setClipChildren(false);
        int sw = getResources().getDisplayMetrics().widthPixels;
        int sh = getResources().getDisplayMetrics().heightPixels;

        // Wallpaper, a little larger than the screen so it can glide with the focus.
        float over = Ui.f(44);
        int ww = Math.round(sw + 2 * over), wh = Math.round(sh + 2 * over * 0.5625f);
        int wl = Math.round(-over), wt = Math.round(-over * 0.5625f);
        Backdrop.place(wl, wt, ww, wh);
        wall = new ImageView(this);
        wall.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wall.setImageBitmap(Backdrop.sharp(this));
        FrameLayout.LayoutParams wlp = new FrameLayout.LayoutParams(ww, wh);
        wlp.leftMargin = wl;
        wlp.topMargin = wt;
        root.addView(wall, wlp);

        wallBlur = new ImageView(this);
        wallBlur.setScaleType(ImageView.ScaleType.FIT_XY);
        wallBlur.setImageBitmap(Backdrop.blur(this));
        wallBlur.setAlpha(0f);
        FrameLayout.LayoutParams blp0 = new FrameLayout.LayoutParams(ww, wh);
        blp0.leftMargin = wl;
        blp0.topMargin = wt;
        root.addView(wallBlur, blp0);

        // Gentle top and bottom shading over the wallpaper, so the bar and the tiles always read.
        View shade = new View(this) {
            private final Paint p = new Paint();

            @Override
            protected void onSizeChanged(int w, int h, int ow, int oh) {
                p.setShader(new LinearGradient(0, 0, 0, h,
                        new int[]{0x400B0B0F, 0x000B0B0F, 0x000B0B0F, 0x800B0B0F},
                        new float[]{0f, 0.22f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            }

            @Override
            protected void onDraw(Canvas c) {
                c.drawRect(0, 0, getWidth(), getHeight(), p);
            }
        };
        root.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        // App grid: five columns, first row at y = 612, tiles 313 x 176 with 42 between them.
        int side = Ui.px(96 - 21);
        int cellW = (sw - 2 * side) / COLS;
        grid = new RecyclerView(this);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        grid.setPadding(side, Ui.px(612), side, sh);
        grid.setHasFixedSize(true);
        androidx.recyclerview.widget.DefaultItemAnimator anim = new androidx.recyclerview.widget.DefaultItemAnimator();
        anim.setSupportsChangeAnimations(false);
        anim.setMoveDuration(220);
        anim.setAddDuration(0);
        anim.setRemoveDuration(120);
        grid.setItemAnimator(anim);
        grid.setItemViewCacheSize(48);
        grid.setOverScrollMode(View.OVER_SCROLL_NEVER);
        grid.setVerticalScrollBarEnabled(false);
        grid.setFocusable(false);
        grid.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        grid.setLayoutManager(new GridLayoutManager(this, COLS) {
            // We scroll by row ourselves; stop RecyclerView from chasing the focused child.
            @Override
            public boolean onRequestChildFocus(@NonNull RecyclerView parent, @NonNull RecyclerView.State st,
                                               @NonNull View child, View focused) {
                return true;
            }

            @Override
            public boolean requestChildRectangleOnScreen(@NonNull RecyclerView parent, @NonNull View child,
                                                         @NonNull android.graphics.Rect rect, boolean immediate,
                                                         boolean focusedChildVisible) {
                return false;
            }
        });
        adapter = new AppAdapter(this, cellW);
        grid.setAdapter(adapter);
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                scrolled += dy;
            }
        });
        // A row that was off screen gets its focus the moment its tile is attached.
        grid.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(@NonNull View view) {
                if (pendingFocus >= 0 && grid.getChildAdapterPosition(view) == pendingFocus) {
                    pendingFocus = -1;
                    RecyclerView.ViewHolder h = grid.getChildViewHolder(view);
                    if (h instanceof AppAdapter.Holder) ((AppAdapter.Holder) h).card.requestFocus();
                }
            }

            @Override
            public void onChildViewDetachedFromWindow(@NonNull View view) {
            }
        });
        root.addView(grid, new FrameLayout.LayoutParams(-1, -1));

        buildBigClock();
        // While a panel is open the rest of Home steps back, so the panel is the only thing that reads.
        scrim = new View(this);
        scrim.setBackgroundColor(0x800B0B0F);
        scrim.setAlpha(0f);
        scrim.setVisibility(View.GONE);
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        buildBar();
        buildPanel();
        buildHint();
        zoom = new Zoom(root, sw, sh);
    }

    private void buildBar() {
        bar = new GlassLayout(this);
        bar.setRadius(Ui.f(38));
        bar.setClipChildren(false);
        FrameLayout inner = new FrameLayout(this);
        inner.setClipChildren(false);

        // One droplet glides between the buttons; the buttons themselves never change size, so the bar
        // never jumps. It sits behind the row and is moved with a spring (a few % overshoot, then still).
        platter = new View(this);
        platter.setBackground(new Droplet(Ui.f(32)));
        platter.setAlpha(0f);
        platter.setScaleX(0.82f);
        platter.setScaleY(0.82f);
        FrameLayout.LayoutParams plp0 = new FrameLayout.LayoutParams(Ui.px(64), Ui.px(64));
        plp0.leftMargin = Ui.px(6);
        plp0.topMargin = Ui.px(6);
        inner.addView(platter, plp0);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.px(6), Ui.px(6), Ui.px(6), Ui.px(6));
        int[] icons = {R.drawable.ic_input, R.drawable.ic_wifi, R.drawable.ic_settings};
        String[] labels = {getString(R.string.inputs), getString(R.string.wifi), getString(R.string.settings)};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            FrameLayout pill = new FrameLayout(this);
            pill.setFocusable(true);
            pill.setClickable(true);
            pill.setDefaultFocusHighlightEnabled(false);
            pill.setContentDescription(labels[i]);
            ImageView ic = new ImageView(this);
            ic.setImageResource(icons[i]);
            ic.setColorFilter(Ui.INK);
            pill.addView(ic, new FrameLayout.LayoutParams(Ui.px(30), Ui.px(30), Gravity.CENTER));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(64), Ui.px(64));
            if (i > 0) lp.leftMargin = Ui.px(4);
            row.addView(pill, lp);
            pills[i] = pill;
            pillIcons[i] = ic;
            pill.setOnFocusChangeListener((v, has) -> onPillFocus(idx, has));
            pill.setOnKeyListener((v, code, e) -> onPillKey(idx, code, e));
            pill.setOnClickListener(v -> onPillClick(idx));
        }
        barSep = new View(this);
        barSep.setBackgroundColor(0x38F2F2F5);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(Math.max(1, Ui.px(1)), Ui.px(30));
        slp.leftMargin = Ui.px(10);
        slp.rightMargin = Ui.px(6);
        row.addView(barSep, slp);
        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("H:mm");
        clock.setFormat12Hour("H:mm");
        clock.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.f(28));
        clock.setTypeface(Ui.semibold);
        clock.setTextColor(Ui.INK);
        clock.setIncludeFontPadding(false);
        clock.setFontFeatureSettings("tnum");
        clock.setPadding(Ui.px(6), 0, Ui.px(20), 0);
        row.addView(clock, new LinearLayout.LayoutParams(-2, -2));
        barClock = clock;
        inner.addView(row, new FrameLayout.LayoutParams(-2, -2));

        // The focused button's name, centred just under it, travelling with the droplet. Placed with
        // translation (not margins) so it never changes the bar's size.
        caption = Ui.text(this, 22, true, 0xE6F2F2F5);
        caption.setShadowLayer(Ui.f(8), 0, Ui.f(1), 0x66000000);
        caption.setAlpha(0f);
        caption.setTranslationY(Ui.f(88));
        inner.addView(caption, new FrameLayout.LayoutParams(-2, -2));

        bar.addView(inner, new FrameLayout.LayoutParams(-2, -2));
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        blp.topMargin = Ui.px(72);
        blp.rightMargin = Ui.px(96);
        root.addView(bar, blp);
    }

    /** Optional large date and time at the top left (Settings > Home screen). */
    private void buildBigClock() {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        TextClock date = new TextClock(this);
        date.setFormat24Hour(getString(R.string.date_format));
        date.setFormat12Hour(getString(R.string.date_format));
        date.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.f(30));
        date.setTypeface(Ui.semibold);
        date.setTextColor(0xCCF2F2F5);
        date.setIncludeFontPadding(false);
        block.addView(date, new LinearLayout.LayoutParams(-2, -2));
        TextClock time = new TextClock(this);
        time.setFormat24Hour("H:mm");
        time.setFormat12Hour("H:mm");
        time.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.f(156));
        time.setTypeface(Ui.display);
        time.setTextColor(0xF2F2F2F5);
        time.setLetterSpacing(-0.038f);
        time.setFontFeatureSettings("tnum");
        time.setIncludeFontPadding(false);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.topMargin = Ui.px(8);
        tl.leftMargin = -Ui.px(6);
        block.addView(time, tl);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        lp.leftMargin = Ui.px(96);
        lp.topMargin = Ui.px(80);
        block.setAlpha(0f);
        block.setVisibility(View.GONE);
        root.addView(block, lp);
        bigClock = block;
    }

    private void applyHomeStyle(boolean animate) {
        bigStyle = prefs.getBoolean("bigClock", false);
        boolean show = bigStyle && !deep;
        if (animate) {
            if (show) bigClock.setVisibility(View.VISIBLE);
            bigClock.animate().alpha(show ? 1f : 0f).translationY(show ? 0f : -Ui.f(10)).setDuration(show ? 260 : 180)
                    .setInterpolator(EASE_OUT).withEndAction(() -> {
                        if (!bigStyle || deep) bigClock.setVisibility(View.GONE);
                    }).start();
        } else {
            bigClock.setVisibility(show ? View.VISIBLE : View.GONE);
            bigClock.setAlpha(show ? 1f : 0f);
            bigClock.setTranslationY(0f);
        }
        // The small clock in the bar steps aside when the large one is on.
        int v = bigStyle ? View.GONE : View.VISIBLE;
        barSep.setVisibility(v);
        barClock.setVisibility(v);
    }

    private void buildPanel() {
        panel = new GlassLayout(this);
        panel.setRadius(Ui.f(40));
        panel.setVisibility(View.GONE);
        panel.setClipChildren(false);
        panel.setFocusable(true);   // holds focus when a panel has no rows (status only)
        panel.setDefaultFocusHighlightEnabled(false);
        panel.setOnKeyListener((v, code, e) -> {
            if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_UP) {
                if (e.getAction() == KeyEvent.ACTION_UP) closePanel();
                return true;
            }
            return isDpad(code);
        });
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setPadding(Ui.px(12), Ui.px(12), Ui.px(12), Ui.px(12));
        panelTitle = Ui.text(this, 24, true, 0x99F2F2F5);
        panelTitle.setPadding(Ui.px(22), Ui.px(10), Ui.px(22), Ui.px(8));
        col.addView(panelTitle, new LinearLayout.LayoutParams(-1, -2));
        panelStatus = new LinearLayout(this);
        panelStatus.setOrientation(LinearLayout.VERTICAL);
        col.addView(panelStatus, new LinearLayout.LayoutParams(-1, -2));
        panelRows = new LinearLayout(this);
        panelRows.setOrientation(LinearLayout.VERTICAL);
        panelRows.setClipChildren(false);
        col.addView(panelRows, new LinearLayout.LayoutParams(-1, -2));
        panel.addView(col, new FrameLayout.LayoutParams(-1, -2));
        // Under the bar, aligned to its right edge: the bar and its selected button stay in view.
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.px(480), -2, Gravity.TOP | Gravity.END);
        lp.topMargin = Ui.px(162);
        lp.rightMargin = Ui.px(96);
        root.addView(panel, lp);
    }

    private void buildHint() {
        hint = new GlassLayout(this);
        hint.setRadius(Ui.f(32));
        hint.setAlpha(0f);
        hint.setVisibility(View.GONE);
        TextView t = Ui.text(this, 28, false, Ui.INK);
        t.setText(R.string.hint_hold);
        t.setPadding(Ui.px(32), Ui.px(18), Ui.px(32), Ui.px(18));
        hint.addView(t, new FrameLayout.LayoutParams(-2, -2));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = Ui.px(60);
        root.addView(hint, lp);
    }

    private static GradientDrawable capsule(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(Ui.f(32));
        d.setColor(color);
        return d;
    }

    // ---- apps ------------------------------------------------------------------------------------

    private void reload() {
        dirty = false;
        final Context app = getApplicationContext();
        io.execute(() -> {
            final List<AppAdapter.Entry> list = loadApps(app);
            handler.post(() -> {
                if (isFinishing()) return;
                if (adapter.getItemCount() == 0) inBar = false;
                adapter.setItems(list);
                grid.post(this::restoreFocus);
            });
        });
    }

    private List<AppAdapter.Entry> loadApps(Context c) {
        PackageManager pm = c.getPackageManager();
        Set<String> hiddenPkgs = new HashSet<>(Arrays.asList(getResources().getStringArray(R.array.hidden_apps)));
        hiddenPkgs.addAll(Arrays.asList(getResources().getStringArray(R.array.live_tv_apps)));   // already under Inputs
        Set<String> hiddenKeys = prefs.getStringSet("hidden", Collections.emptySet());
        List<String> dock = Arrays.asList(getResources().getStringArray(R.array.dock_apps));
        List<String> order = savedOrder();
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<AppAdapter.Entry> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : pm.queryIntentActivities(probe, 0)) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || hiddenPkgs.contains(pkg)) continue;
            if (!seen.add(pkg)) continue;      // one tile per app (Sony's Timers & Clock lists itself twice)
            ComponentName cn = new ComponentName(pkg, ri.activityInfo.name);
            if (hiddenKeys.contains(cn.flattenToShortString())) continue;
            Drawable banner = ri.activityInfo.loadBanner(pm);
            if (banner == null) banner = ri.activityInfo.applicationInfo.loadBanner(pm);
            out.add(new AppAdapter.Entry(String.valueOf(ri.loadLabel(pm)), cn, banner, ri.loadIcon(pm)));
        }
        Intent phone = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(phone, 0)) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || hiddenPkgs.contains(pkg)) continue;
            if ((ri.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;  // only apps you installed
            if (!seen.add(pkg)) continue;
            ComponentName cn = new ComponentName(pkg, ri.activityInfo.name);
            if (hiddenKeys.contains(cn.flattenToShortString())) continue;
            out.add(new AppAdapter.Entry(String.valueOf(ri.loadLabel(pm)), cn, ri.activityInfo.applicationInfo.loadBanner(pm), ri.loadIcon(pm)));
        }
        Map<String, Integer> rank = new HashMap<>();
        for (int i = 0; i < order.size(); i++) rank.put(order.get(i), i);
        Collections.sort(out, (a, b) -> {
            Integer ra = rank.get(a.key), rb = rank.get(b.key);
            if (ra != null || rb != null) {
                if (ra == null) return 1;
                if (rb == null) return -1;
                return Integer.compare(ra, rb);
            }
            int da = dock.indexOf(a.pkg), db = dock.indexOf(b.pkg);
            if (da >= 0 || db >= 0) {
                if (da < 0) return 1;
                if (db < 0) return -1;
                return Integer.compare(da, db);
            }
            return a.label.compareToIgnoreCase(b.label);
        });
        return out;
    }

    private List<String> savedOrder() {
        String s = prefs.getString("order", "");
        return s.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(s.split("\n")));
    }

    private void saveOrder() {
        List<String> keys = new ArrayList<>();
        for (AppAdapter.Entry e : adapter.items()) keys.add(e.key);
        prefs.edit().putString("order", TextUtils.join("\n", keys)).apply();
    }

    private void restoreFocus() {
        if (panelOpen) return;
        int n = adapter.getItemCount();
        if (n == 0) return;          // apps still loading: focus lands on the first tile when they arrive
        int p = adapter.positionOf(lastKey);
        if (p < 0) p = Math.min(lastPos, n - 1);
        if (inBar) focusPill(barIndex, false);
        else focusTile(p, false);
    }

    // ---- AppAdapter.Host -------------------------------------------------------------------------

    @Override
    public boolean isFocusedPos(int pos) {
        return !inBar && !panelOpen && pos == focusPos;
    }

    @Override
    public boolean isMoving() {
        return moving;
    }

    @Override
    public void onTileFocus(int pos, AppAdapter.Holder h, boolean focused) {
        if (!focused) {
            // Opening the side panel takes focus away, but the tile it is about stays lifted.
            if (!panelOpen) h.applyState(false, false, true);
            return;
        }
        h.applyState(true, moving, true);
        boolean changed = pos != focusPos || inBar;
        focusPos = pos;
        inBar = false;
        lastPos = pos;
        AppAdapter.Entry e = adapter.at(pos);
        lastKey = e != null ? e.key : null;
        if (changed && !firstFocus) sounds.tick();
        firstFocus = false;
        scrollToRow(pos / COLS);
        glide(pos % COLS, false);
        setDeep(pos / COLS > 1);
        maybeShowHint();
    }

    @Override
    public boolean onTileKey(int pos, AppAdapter.Holder h, int code, KeyEvent e) {
        if (zoom.isBusy()) return true;          // an app is opening or Home is coming back: ignore keys
        if (code == KeyEvent.KEYCODE_MENU) {
            if (e.getAction() == KeyEvent.ACTION_UP && !moving) onTileHold(pos);
            return true;
        }
        if (!isDpad(code)) return false;
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        if (throttled(e)) return true;
        int n = adapter.getItemCount(), col = pos % COLS, row = pos / COLS, target = -1;
        // Left and right read the grid like a line of text: past the end of a row they carry on at the
        // start of the next (or the end of the one above). Right on the last app comes back to the
        // first; left on the first app stays put with the usual nudge.
        if (code == KeyEvent.KEYCODE_DPAD_LEFT && pos > 0) target = pos - 1;
        else if (code == KeyEvent.KEYCODE_DPAD_RIGHT && pos + 1 < n) target = pos + 1;
        else if (code == KeyEvent.KEYCODE_DPAD_RIGHT && !moving && n > 1 && e.getRepeatCount() == 0) target = 0;
        else if (code == KeyEvent.KEYCODE_DPAD_DOWN && (row + 1) * COLS < n) target = Math.min(pos + COLS, n - 1);
        else if (code == KeyEvent.KEYCODE_DPAD_UP) {
            if (row > 0) target = pos - COLS;
            else if (!moving) {
                focusPill(barIndex, true);
                return true;
            }
        }
        if (target < 0) {
            bump(h.itemView, code);
            return true;
        }
        if (moving) moveTo(pos, target);
        else focusTile(target, true);
        return true;
    }

    @Override
    public void onTileClick(int pos) {
        if (zoom.isBusy()) return;
        if (moving) {
            endMove(true);
            return;
        }
        AppAdapter.Entry e = adapter.at(pos);
        if (e == null) return;
        Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(e.pkg);
        if (i == null) i = getPackageManager().getLaunchIntentForPackage(e.pkg);
        if (i == null) i = new Intent(Intent.ACTION_MAIN).setComponent(e.component);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        final Intent launch = i;
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
        if (vh == null) {
            startApp(launch, e, null);
            return;
        }
        zoom.open((AppAdapter.Holder) vh, e.key, () -> startApp(launch, e,
                android.app.ActivityOptions.makeCustomAnimation(this, R.anim.app_in, R.anim.hold).toBundle()));
    }

    /** The tile now showing this app, if it is on screen. */
    private AppAdapter.Holder holderFor(String key) {
        int pos = adapter.positionOf(key);
        if (pos < 0) return null;
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
        return vh instanceof AppAdapter.Holder ? (AppAdapter.Holder) vh : null;
    }

    private void startApp(Intent i, AppAdapter.Entry e, android.os.Bundle opts) {
        try {
            startActivity(i, opts);
            // Safety net: if Home never actually went to the background (an app that opens nothing
            // on screen), do not leave the plate up and the tiles locked.
            handler.postDelayed(() -> {
                if (resumed && zoom.isOpen()) zoom.close(holderFor(zoom.key()));
            }, 2500);
        } catch (Exception ex) {
            zoom.reset();
            Toast.makeText(this, getString(R.string.cant_open, e.label), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onTileHold(int pos) {
        if (moving) return;
        final AppAdapter.Entry e = adapter.at(pos);
        if (e == null) return;
        prefs.edit().putBoolean("hinted", true).apply();
        hideHint();
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(getString(R.string.move), null, 0, () -> startMove(pos)));
        rows.add(new Row(getString(R.string.hide), null, 0, () -> hideApp(e)));
        rows.add(new Row(getString(R.string.app_info), null, 0, () -> open(new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + e.pkg)))));
        openPanel(e.label, rows);
    }

    // ---- focus and motion ------------------------------------------------------------------------

    private static boolean isDpad(int code) {
        return code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
                || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN;
    }

    /** Held keys repeat at the remote's rate; cap it so releasing the key stops focus at once. */
    private boolean throttled(KeyEvent e) {
        long now = SystemClock.uptimeMillis();
        if (e.getRepeatCount() > 0 && now - lastMoveAt < REPEAT_MIN_MS) return true;
        lastMoveAt = now;
        return false;
    }

    private void focusTile(int pos, boolean sound) {
        int n = adapter.getItemCount();
        if (n == 0) return;
        final int p = Math.max(0, Math.min(pos, n - 1));
        if (!sound) firstFocus = true;
        scrollToRow(p / COLS);
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(p);
        if (vh != null) {
            pendingFocus = -1;
            View card = ((AppAdapter.Holder) vh).card;
            if (card.isFocused()) onTileFocus(p, (AppAdapter.Holder) vh, true);
            else card.requestFocus();
        } else {
            pendingFocus = p;   // focused by the attach listener once the scroll brings it in
        }
    }

    /** Row 0 sits at y = 612 with the wallpaper in full view; deeper rows slide up under a blurred backdrop. */
    private void scrollToRow(int row) {
        int rowH = Ui.px(AppAdapter.TILE_H + AppAdapter.ROW_GAP);
        // Rows 0 and 1 are both on screen from the start; only a third row and beyond need the grid to move.
        int target = row <= 1 ? 0 : Ui.px(470) + (row - 2) * rowH;
        if (target == scrollTarget && scrolled == target) return;
        scrollTarget = target;
        int dy = target - scrolled;
        if (dy != 0) grid.smoothScrollBy(0, dy, EASE_OUT, 320);
    }

    /** The wallpaper glides a little against the focus: near layers seem to move, the scene gains depth. */
    private void glide(int col, boolean bar) {
        float x = bar ? -2 * Ui.f(14) : (2 - col) * Ui.f(14);
        float y = bar ? Ui.f(8) : 0f;
        Ui.spring(wall, R.id.spring_tx, DynamicAnimation.TRANSLATION_X, x, 120f, 1f);
        Ui.spring(wall, R.id.spring_ty, DynamicAnimation.TRANSLATION_Y, y, 120f, 1f);
    }

    private void setDeep(boolean d) {
        if (d == deep) return;
        deep = d;
        wall.animate().alpha(d ? 0.45f : 1f).setDuration(300).start();
        bar.animate().alpha(d ? 0f : 1f).setDuration(250).start();
        applyHomeStyle(true);
    }

    /** At an edge the focused item nudges 6 px toward the press and comes back: "nothing more this way". */
    private void bump(View v, int code) {
        // Always from and back to rest, cancelling a nudge still in flight, so held keys never leave a view off-place.
        Object old = v.getTag(R.id.bump);
        if (old instanceof ObjectAnimator) ((ObjectAnimator) old).cancel();
        float d = Ui.f(6);
        boolean horizontal = code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT;
        String prop = horizontal ? "translationX" : "translationY";
        float s = (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_UP) ? -d : d;
        ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder(v, PropertyValuesHolder.ofFloat(prop, 0f, s, 0f));
        a.setDuration(150);
        a.setInterpolator(EASE_OUT);
        v.setTag(R.id.bump, a);
        a.start();
        sounds.knock();
    }

    // ---- top bar ---------------------------------------------------------------------------------

    private void focusPill(int i, boolean sound) {
        if (!sound) firstFocus = true;
        pills[i].requestFocus();
    }

    private void onPillFocus(int i, boolean has) {
        if (has) {
            boolean changed = !inBar || barIndex != i;
            inBar = true;
            barIndex = i;
            focusPos = -1;
            if (changed && !firstFocus) sounds.tick();
            firstFocus = false;
            glide(0, true);
        }
        handler.post(this::updateBarFocus);   // after focus has fully moved (leaving one pill, entering the next)
    }

    /** Droplet, icon colours and caption follow the bar's state: focused in the bar, or its panel open. */
    private void updateBarFocus() {
        boolean fromBar = panelOpen && panelOpener != null && Arrays.asList(pills).contains(panelOpener);
        boolean show = (inBar && !panelOpen && pills[barIndex].isFocused()) || fromBar;
        int idx = fromBar ? Arrays.asList(pills).indexOf(panelOpener) : barIndex;
        float x = idx * Ui.f(68);
        if (show && !platterShown) {
            Ui.set(platter, R.id.spring_tx, DynamicAnimation.TRANSLATION_X, x);   // appear where it is needed
        } else {
            Ui.spring(platter, R.id.spring_tx, DynamicAnimation.TRANSLATION_X, x, 520f, 0.72f);
        }
        if (show != platterShown) {
            platterShown = show;
            platter.animate().alpha(show ? 1f : 0f).scaleX(show ? 1.06f : 0.82f).scaleY(show ? 1.06f : 0.82f)
                    .setDuration(show ? 240 : 140).setInterpolator(show ? SPRING_OUT : EASE_OUT).start();
        }
        for (int k = 0; k < 3; k++) tintIcon(k, show && k == idx);

        updateCaption(show && !panelOpen, pills[idx].getContentDescription().toString(), Ui.f(6) + x + Ui.f(32));
    }

    /**
     * The name under the focused button. Moving to another button: the old name fades out as it starts
     * to travel, the new one fades in as it arrives, centred under its button.
     */
    private void updateCaption(boolean on, String text, float centre) {
        final int seq = ++captionSeq;
        if (!on) {
            caption.animate().cancel();
            caption.animate().alpha(0f).translationY(Ui.f(84)).setStartDelay(0).setDuration(110)
                    .setInterpolator(EASE_OUT).start();
            return;
        }
        boolean hidden = caption.getAlpha() < 0.01f;
        if (hidden || text.contentEquals(caption.getText())) {
            if (!text.contentEquals(caption.getText())) {
                caption.setText(text);
                caption.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            }
            float tx = centre - caption.getMeasuredWidth() / 2f;
            if (hidden) Ui.set(caption, R.id.caption_tx, DynamicAnimation.TRANSLATION_X, tx);
            else Ui.spring(caption, R.id.caption_tx, DynamicAnimation.TRANSLATION_X, tx, 520f, 0.72f);
            caption.animate().cancel();
            caption.animate().alpha(1f).translationY(Ui.f(88)).setStartDelay(hidden ? 70 : 0).setDuration(180)
                    .setInterpolator(EASE_OUT).start();
            return;
        }
        Ui.spring(caption, R.id.caption_tx, DynamicAnimation.TRANSLATION_X,
                centre - caption.getMeasuredWidth() / 2f, 520f, 0.72f);
        caption.animate().cancel();
        caption.animate().alpha(0f).setStartDelay(0).setDuration(90).setInterpolator(EASE_OUT).withEndAction(() -> {
            if (seq != captionSeq) return;
            caption.setText(text);
            caption.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            Ui.spring(caption, R.id.caption_tx, DynamicAnimation.TRANSLATION_X,
                    centre - caption.getMeasuredWidth() / 2f, 520f, 0.72f);
            caption.animate().alpha(1f).translationY(Ui.f(88)).setDuration(160).setInterpolator(EASE_OUT).start();
        }).start();
    }

    private void tintIcon(int k, boolean dark) {
        if (iconDark[k] == dark) return;
        iconDark[k] = dark;
        if (iconTint[k] != null) iconTint[k].cancel();
        int from = dark ? Ui.INK : Ui.DARK, to = dark ? Ui.DARK : Ui.INK;
        ValueAnimator a = ValueAnimator.ofObject(new ArgbEvaluator(), from, to);
        a.setDuration(140);
        a.setStartDelay(dark ? 60 : 0);
        a.addUpdateListener(v -> pillIcons[k].setColorFilter((int) v.getAnimatedValue()));
        a.start();
        iconTint[k] = a;
    }

    private boolean onPillKey(int i, int code, KeyEvent e) {
        if (!isDpad(code)) return false;
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        if (throttled(e)) return true;
        if (code == KeyEvent.KEYCODE_DPAD_LEFT && i > 0) focusPill(i - 1, true);
        else if (code == KeyEvent.KEYCODE_DPAD_RIGHT && i < 2) focusPill(i + 1, true);
        else if (code == KeyEvent.KEYCODE_DPAD_DOWN && adapter.getItemCount() > 0) {
            inBar = false;
            focusTile(Math.min(lastPos, COLS - 1), true);
        } else bump(bar, code);
        return true;
    }

    private void onPillClick(int i) {
        if (i == 0) openInputs();
        else if (i == 1) openNetwork();
        else openSettings();
    }

    /** What the network is doing right now, for the Wi-Fi panel. */
    private Row networkStatus() {
        String title = getString(R.string.offline), sub = getString(R.string.not_connected);
        int kind = 0;   // 0 offline, 1 ethernet, 2 wifi
        boolean validated = false;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            for (Network net : cm != null ? cm.getAllNetworks() : new Network[0]) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(net);
                if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;
                boolean v = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    kind = 1;
                    validated |= v;
                } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && kind != 1) {
                    kind = 2;
                    validated |= v;
                }
            }
            if (kind == 1) {
                title = getString(R.string.ethernet);
            } else if (kind == 2) {
                title = getString(R.string.wifi);
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
                if (info != null) {
                    String ssid = info.getSSID();
                    if (ssid != null) {
                        ssid = ssid.replace("\"", "").trim();
                        if (!ssid.isEmpty() && !ssid.contains("unknown") && !ssid.equals("0x")) title = ssid;
                    }
                }
            }
            if (kind != 0) sub = getString(validated ? R.string.connected_internet : R.string.no_internet);
        } catch (Exception ignored) {
        }
        return new Row(title, sub, kind, null);
    }

    private void openNetwork() {
        List<Row> rows = new ArrayList<>();
        Intent net = firstResolvable(new Intent(Settings.ACTION_WIFI_SETTINGS), new Intent(Settings.ACTION_WIRELESS_SETTINGS));
        if (net != null) rows.add(Row.ext(getString(R.string.network_settings), () -> open(net)));
        openPanel(getString(R.string.wifi), networkStatus(), rows);
    }

    /** Settings people reach for every day; anything this TV cannot open is simply left out. */
    private void openSettings() {
        List<Row> rows = new ArrayList<>();
        Intent picture = firstResolvable(new Intent(Settings.ACTION_DISPLAY_SETTINGS));
        if (picture != null) rows.add(Row.ext(getString(R.string.picture), () -> open(picture)));
        Intent sound = firstResolvable(new Intent(Settings.ACTION_SOUND_SETTINGS));
        if (sound != null) rows.add(Row.ext(getString(R.string.sound), () -> open(sound)));
        for (String pkg : getResources().getStringArray(R.array.sleep_timer_apps)) {
            Intent t = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
            if (t == null) t = getPackageManager().getLaunchIntentForPackage(pkg);
            if (t != null) {
                final Intent ti = t;
                rows.add(Row.ext(getString(R.string.sleep_timer), () -> open(ti)));
                break;
            }
        }
        final Row style = new Row(getString(R.string.home_style),
                getString(bigStyle ? R.string.style_clock : R.string.style_compact), 0, null);
        style.stay = true;
        style.action = () -> {
            prefs.edit().putBoolean("bigClock", !bigStyle).apply();
            applyHomeStyle(true);
            style.sub = getString(bigStyle ? R.string.style_clock : R.string.style_compact);
            if (style.subView != null) style.subView.setText(style.sub);
        };
        rows.add(style);
        int hidden = prefs.getStringSet("hidden", Collections.emptySet()).size();
        if (hidden > 0) {
            rows.add(new Row(getString(R.string.show_hidden), String.valueOf(hidden), 0, () -> {
                prefs.edit().remove("hidden").apply();
                reload();
            }));
        }
        for (String pkg : getResources().getStringArray(R.array.stock_launchers)) {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(pkg);
            if (getPackageManager().resolveActivity(home, 0) != null) {
                rows.add(Row.ext(getString(R.string.stock_home), () -> open(home)));
                break;
            }
        }
        rows.add(Row.ext(getString(R.string.all_settings), () -> open(new Intent(Settings.ACTION_SETTINGS))));
        openPanel(getString(R.string.settings), null, rows);
    }

    private Intent firstResolvable(Intent... candidates) {
        for (Intent i : candidates) if (getPackageManager().resolveActivity(i, 0) != null) return i;
        return null;
    }

    // ---- panel -----------------------------------------------------------------------------------

    private static final class Row {
        final String title;
        String sub;
        final int dot;          // input rows: 0 none, 1 connected. Status row: 0 offline, 1 wired, 2 Wi-Fi
        Runnable action;
        boolean ext;            // opens a screen of the TV's own Settings: shows a chevron
        boolean stay;           // acts in place; the panel stays open
        TextView subView;

        Row(String title, String sub, int dot, Runnable action) {
            this.title = title;
            this.sub = sub;
            this.dot = dot;
            this.action = action;
        }

        static Row ext(String title, Runnable action) {
            Row r = new Row(title, null, 0, action);
            r.ext = true;
            return r;
        }
    }

    private void openPanel(String title, List<Row> rows) {
        openPanel(title, null, rows);
    }

    /** A frosted panel that drops from under the bar; Home behind it dims. */
    private void openPanel(String title, Row status, List<Row> rows) {
        if (rows.isEmpty() && status == null) return;
        panelOpener = getCurrentFocus();
        panelRows.removeAllViews();
        panelTitle.setText(title);
        panelTitle.setVisibility(title == null ? View.GONE : View.VISIBLE);
        panelStatus.removeAllViews();
        panelStatus.setVisibility(status == null ? View.GONE : View.VISIBLE);
        if (status != null) panelStatus.addView(statusView(status));
        boolean dots = false;
        for (Row r : rows) dots |= r.dot != 0;
        for (int i = 0; i < rows.size(); i++) panelRows.addView(rowView(rows.get(i), i, rows.size(), dots));
        panelOpen = true;
        panel.setVisibility(View.VISIBLE);
        panel.setPivotX(Ui.f(480));
        panel.setPivotY(0f);
        panel.setAlpha(0f);
        panel.setScaleX(0.96f);
        panel.setScaleY(0.96f);
        panel.setTranslationY(-Ui.f(12));
        panel.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).setStartDelay(40).setDuration(320)
                .setInterpolator(SPRING_OUT).start();
        scrim.setVisibility(View.VISIBLE);
        scrim.animate().alpha(1f).setDuration(220).setInterpolator(EASE_OUT).start();
        bar.animate().alpha(1f).setDuration(200).start();   // the panel hangs from the bar, so show it even deep in the grid
        firstFocus = true;
        if (panelRows.getChildCount() > 0) panelRows.getChildAt(0).requestFocus();
        else panel.requestFocus();
        updateBarFocus();
    }

    private void closePanel() {
        if (!panelOpen) return;
        panelOpen = false;
        panel.animate().alpha(0f).scaleX(0.97f).scaleY(0.97f).translationY(-Ui.f(8)).setStartDelay(0).setDuration(160)
                .setInterpolator(EASE_OUT).withEndAction(() -> {
                    if (!panelOpen) panel.setVisibility(View.GONE);
                }).start();
        scrim.animate().alpha(0f).setDuration(160).withEndAction(() -> {
            if (!panelOpen) scrim.setVisibility(View.GONE);
        }).start();
        bar.animate().alpha(deep ? 0f : 1f).setDuration(200).start();
        firstFocus = true;
        if (panelOpener != null && panelOpener.isAttachedToWindow()) panelOpener.requestFocus();
        else restoreFocus();
        handler.postDelayed(this::updateBarFocus, 120);   // the caption returns once the panel has gone
    }

    private View statusView(Row r) {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(Ui.px(20), Ui.px(18), Ui.px(20), Ui.px(18));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Ui.f(24));
        bg.setColor(0x12FFFFFF);
        v.setBackground(bg);
        int tint = r.dot == 0 ? 0xFFFF453A : Ui.OK_GREEN;
        FrameLayout badge = new FrameLayout(this);
        GradientDrawable bd = new GradientDrawable();
        bd.setShape(GradientDrawable.OVAL);
        bd.setColor((tint & 0x00FFFFFF) | 0x2E000000);
        badge.setBackground(bd);
        ImageView ic = new ImageView(this);
        ic.setImageResource(r.dot == 2 ? R.drawable.ic_wifi : (r.dot == 1 ? R.drawable.ic_ethernet : R.drawable.ic_offline));
        ic.setColorFilter(tint);
        badge.addView(ic, new FrameLayout.LayoutParams(Ui.px(28), Ui.px(28), Gravity.CENTER));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Ui.px(52), Ui.px(52));
        blp.rightMargin = Ui.px(18);
        v.addView(badge, blp);
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.text(this, 27, true, Ui.INK);
        t.setText(r.title);
        t.setEllipsize(TextUtils.TruncateAt.END);
        text.addView(t);
        TextView s = Ui.text(this, 22, false, 0xA8F2F2F5);
        s.setText(r.sub);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.topMargin = Ui.px(6);
        text.addView(s, sl);
        v.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.leftMargin = Ui.px(8);
        lp.rightMargin = Ui.px(8);
        lp.topMargin = Ui.px(4);
        lp.bottomMargin = Ui.px(8);
        v.setLayoutParams(lp);
        return v;
    }

    private View rowView(Row r, int index, int count, boolean dots) {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(Ui.px(22), 0, Ui.px(22), 0);
        v.setFocusable(true);
        v.setClickable(true);
        v.setDefaultFocusHighlightEnabled(false);
        final Droplet drop = new Droplet(Ui.f(24));
        drop.setAlpha(0);
        v.setBackground(drop);
        View dot = null;
        if (dots) {
            dot = new View(this);
            GradientDrawable dd = new GradientDrawable();
            dd.setShape(GradientDrawable.OVAL);
            dd.setColor(r.dot == 1 ? Ui.OK_GREEN : 0x42F2F2F5);
            dot.setBackground(dd);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(Ui.px(12), Ui.px(12));
            dlp.rightMargin = Ui.px(18);
            v.addView(dot, dlp);
        }
        final View dotView = dot;
        final TextView t = Ui.text(this, 27, true, Ui.INK);
        t.setText(r.title);
        t.setEllipsize(TextUtils.TruncateAt.END);
        v.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        final TextView s = Ui.text(this, 25, false, 0x9EF2F2F5);
        if (r.sub != null) {
            s.setText(r.sub);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
            sl.leftMargin = Ui.px(16);
            v.addView(s, sl);
            r.subView = s;
        }
        final ImageView chev = new ImageView(this);
        if (r.ext) {
            chev.setImageResource(R.drawable.ic_chevron);
            chev.setColorFilter(0x66F2F2F5);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(Ui.px(22), Ui.px(22));
            cl.leftMargin = Ui.px(14);
            v.addView(chev, cl);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Ui.px(64));
        if (index > 0) lp.topMargin = Ui.px(4);
        v.setLayoutParams(lp);
        v.setOnFocusChangeListener((view, has) -> {
            Object old = view.getTag(R.id.bump_fade);
            if (old instanceof ValueAnimator) ((ValueAnimator) old).cancel();
            ValueAnimator a = ValueAnimator.ofInt(drop.getAlpha(), has ? 255 : 0);
            a.setDuration(has ? 140 : 100);
            a.addUpdateListener(an -> drop.setAlpha((int) an.getAnimatedValue()));
            view.setTag(R.id.bump_fade, a);
            a.start();
            t.setTextColor(has ? Ui.DARK : Ui.INK);
            s.setTextColor(has ? 0x8C0B0B0F : 0x9EF2F2F5);
            chev.setColorFilter(has ? 0x730B0B0F : 0x66F2F2F5);
            if (dotView != null && r.dot != 1) ((GradientDrawable) dotView.getBackground()).setColor(has ? 0x380B0B0F : 0x42F2F2F5);
            if (has && !firstFocus) sounds.tick();
            if (has) firstFocus = false;
        });
        v.setOnKeyListener((view, code, e) -> {
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (e.getAction() == KeyEvent.ACTION_UP) closePanel();
                return true;
            }
            if (!isDpad(code)) return false;
            if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
            if (throttled(e)) return true;
            if (code == KeyEvent.KEYCODE_DPAD_UP && index > 0) panelRows.getChildAt(index - 1).requestFocus();
            else if (code == KeyEvent.KEYCODE_DPAD_DOWN && index < count - 1) panelRows.getChildAt(index + 1).requestFocus();
            else bump(view, code);
            return true;
        });
        v.setOnClickListener(view -> {
            if (r.stay) {
                sounds.tick();
                r.action.run();
                return;
            }
            closePanel();
            if (r.action != null) r.action.run();
        });
        return v;
    }

    private void openInputs() {
        List<Row> rows = new ArrayList<>();
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim != null) {
            List<TvInputInfo> all = new ArrayList<>(tim.getTvInputList());
            // HDMI-CEC devices show up as child inputs of a port: use their name as the port's subtitle.
            Map<String, String> deviceName = new HashMap<>();
            for (TvInputInfo i : all) {
                if (i.getParentId() != null && !i.isHidden(this)) {
                    CharSequence l = i.loadLabel(this);
                    if (l != null) deviceName.put(i.getParentId(), l.toString());
                }
            }
            Collections.sort(all, (a, b) -> {
                boolean ha = a.getType() == TvInputInfo.TYPE_HDMI, hb = b.getType() == TvInputInfo.TYPE_HDMI;
                if (ha != hb) return ha ? -1 : 1;
                return String.valueOf(a.loadLabel(this)).compareToIgnoreCase(String.valueOf(b.loadLabel(this)));
            });
            // Some TVs (Sony among them) report every HDMI port as connected whether or not anything is
            // plugged in: then show no dots rather than wrong ones.
            int ports = 0, connected = 0;
            for (TvInputInfo i : all) {
                if (!i.isPassthroughInput() || i.getType() != TvInputInfo.TYPE_HDMI || i.getParentId() != null) continue;
                ports++;
                if (tim.getInputState(i.getId()) == TvInputManager.INPUT_STATE_CONNECTED) connected++;
            }
            boolean bogus = ports > 1 && connected == ports;
            for (TvInputInfo i : all) {
                if (!i.isPassthroughInput() || i.getParentId() != null || i.isHidden(this)) continue;
                CharSequence l = i.loadLabel(this);
                String label = l == null ? i.getId() : l.toString();
                boolean hdmi = i.getType() == TvInputInfo.TYPE_HDMI;
                if (hdmi && !label.toUpperCase().contains("HDMI")) label = "HDMI " + label;
                int dot = !bogus && tim.getInputState(i.getId()) == TvInputManager.INPUT_STATE_CONNECTED ? 1 : 0;
                final String id = i.getId();
                rows.add(new Row(label, deviceName.get(id), dot, () -> open(new Intent(Intent.ACTION_VIEW)
                        .setDataAndType(TvContract.buildChannelUriForPassthroughInput(id), "vnd.android.cursor.item/channel"))));
            }
        }
        Intent liveTv = liveTvIntent();
        if (liveTv != null) rows.add(new Row(getString(R.string.live_tv), null, 0, () -> open(liveTv)));
        if (rows.isEmpty()) {
            Toast.makeText(this, R.string.no_inputs, Toast.LENGTH_SHORT).show();
            return;
        }
        openPanel(getString(R.string.inputs), rows);
    }

    /** The TV's own Live TV app: the first known one installed (Sony, Google, AOSP, MediaTek, TCL...). */
    private Intent liveTvIntent() {
        PackageManager pm = getPackageManager();
        for (String pkg : getResources().getStringArray(R.array.live_tv_apps)) {
            Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (i == null) i = pm.getLaunchIntentForPackage(pkg);
            if (i != null) return i;
        }
        return null;
    }

    // ---- move and hide ---------------------------------------------------------------------------

    private void startMove(int pos) {
        savedOrder = new ArrayList<>();
        for (AppAdapter.Entry e : adapter.items()) savedOrder.add(e.key);
        moveStart = pos;
        moving = true;
        showMoveHint(true);
        handler.post(() -> {
            RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
            if (vh != null) ((AppAdapter.Holder) vh).applyState(true, true, true);
        });
    }

    private void moveTo(int from, int to) {
        // The lifted tile keeps its view (and focus); RecyclerView slides the neighbours into place.
        adapter.move(from, to);
        focusPos = to;
        lastPos = to;
        scrollToRow(to / COLS);
        glide(to % COLS, false);
        setDeep(to / COLS > 1);
        sounds.tick();
    }

    private void endMove(boolean keep) {
        moving = false;
        showMoveHint(false);
        if (keep) {
            saveOrder();
            AppAdapter.Entry e = adapter.at(focusPos);
            lastKey = e != null ? e.key : null;
            RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(focusPos);
            if (vh != null) {
                // Drop: the tile settles back to its focused size with a soft knock, like setting it down.
                ((AppAdapter.Holder) vh).applyState(true, false, true);
            }
            sounds.knock();
        } else {
            Map<String, AppAdapter.Entry> byKey = new HashMap<>();
            for (AppAdapter.Entry e : adapter.items()) byKey.put(e.key, e);
            List<AppAdapter.Entry> back = new ArrayList<>();
            for (String k : savedOrder) if (byKey.containsKey(k)) back.add(byKey.get(k));
            adapter.setItems(back);
            final int p = moveStart;
            grid.post(() -> focusTile(p, false));
        }
        savedOrder = null;
    }

    private void showMoveHint(boolean on) {
        TextView t = (TextView) hint.getChildAt(0);
        if (on) {
            handler.removeCallbacksAndMessages(HINT_TOKEN);
            t.setText(R.string.hint_move);
            hint.setVisibility(View.VISIBLE);
            hint.animate().alpha(1f).setDuration(200).start();
        } else {
            hint.animate().alpha(0f).setDuration(200).withEndAction(() -> {
                hint.setVisibility(View.GONE);
                t.setText(R.string.hint_hold);
            }).start();
        }
    }

    private void hideApp(AppAdapter.Entry e) {
        Set<String> hidden = new HashSet<>(prefs.getStringSet("hidden", Collections.emptySet()));
        hidden.add(e.key);
        prefs.edit().putStringSet("hidden", hidden).apply();
        int pos = adapter.positionOf(e.key);
        if (pos >= 0) adapter.remove(pos);
        final int p = Math.min(Math.max(0, pos), adapter.getItemCount() - 1);
        grid.post(() -> focusTile(p, false));
    }

    // ---- hint, keys, helpers ---------------------------------------------------------------------

    /** Tell people once that holding OK does more. */
    private void maybeShowHint() {
        if (prefs.getBoolean("hinted", false) || hint.getVisibility() == View.VISIBLE) return;
        prefs.edit().putBoolean("hinted", true).apply();
        handler.postDelayed(() -> {
            hint.setVisibility(View.VISIBLE);
            hint.animate().alpha(1f).setDuration(300).start();
            handler.postDelayed(this::hideHint, 5000);
        }, 1200);
    }

    private void hideHint() {
        if (moving || hint.getVisibility() != View.VISIBLE) return;
        hint.animate().alpha(0f).setDuration(300).withEndAction(() -> hint.setVisibility(View.GONE)).start();
    }

    /** Back steps back exactly once: close the panel, cancel a move, leave the bar, return to row one. */
    @Override
    public void onBackPressed() {
        if (panelOpen) {
            closePanel();
        } else if (moving) {
            endMove(false);
        } else if (inBar) {
            focusTile(Math.min(lastPos, COLS - 1), true);
        } else if (focusPos >= COLS) {
            focusTile(focusPos % COLS, true);
        }
        // On row one already: a home screen has nowhere to go back to.
    }

    private void open(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }
}
