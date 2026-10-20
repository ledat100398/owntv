package io.github.ledat100398.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;


/**
 * Keeps the Home button on OwnTV for TVs whose own home screen cannot be removed (some Google TV sets
 * put it back, or refuse to let it go). The same approach as button-remapping apps: an accessibility
 * service that sees the Home key first, plus a guard that steps in if the stock home screen comes to the
 * front by itself, for example right after the TV starts.
 *
 * The install script switches it on over adb; it can be turned off in Settings › Accessibility.
 */
public class HomeKeyService extends AccessibilityService {

    private static final String PREFS = "home";
    private static final String STOCK_UNTIL = "stockUntil";
    private long lastJump;

    /** Opening the stock home on purpose (Settings › Stock home screen): leave it alone for a minute. */
    static void allowStockHome(Context c) {
        c.getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putLong(STOCK_UNTIL, System.currentTimeMillis() + 60_000).apply();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        if (e.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;
        if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) goHome();
        return true;          // the system never sees Home, so the stock home screen never opens
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || e.getPackageName() == null) return;
        String pkg = e.getPackageName().toString(), cls = e.getClassName() == null ? "" : e.getClassName().toString();
        if (pkg.equals(getPackageName()) || !isOtherHome(pkg, cls)) return;
        long until = getSharedPreferences(PREFS, MODE_PRIVATE).getLong(STOCK_UNTIL, 0);
        if (System.currentTimeMillis() < until) return;
        goHome();
    }

    /**
     * Only the home screen itself, not every window of its package: launcher packages often host input
     * pickers, profile choosers or panels that must keep working. Looked up each time, so a home screen put
     * back while the service runs is recognised too.
     */
    private boolean isOtherHome(String pkg, String cls) {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        for (ResolveInfo ri : getPackageManager().queryIntentActivities(home, 0)) {
            ActivityInfo a = ri.activityInfo;
            if (a == null || !a.packageName.equals(pkg)) continue;
            if (a.packageName.contains("settings") || a.packageName.contains("setup")) return false;
            if (cls.equals(a.name) || cls.equals(a.targetActivity)) return true;
        }
        return false;
    }

    private void goHome() {
        long now = SystemClock.uptimeMillis();
        if (now - lastJump < 400) return;    // one jump per press, even if events arrive twice
        lastJump = now;
        Intent i = new Intent(this, MainActivity.class)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException ignored) {
        }
    }

    @Override
    public void onInterrupt() {
    }
}
