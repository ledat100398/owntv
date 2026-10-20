package io.github.ledat100398.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

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
    private Set<String> stock;
    private long lastJump;

    /** Opening the stock home on purpose (Settings › Stock home screen): leave it alone for a minute. */
    static void allowStockHome(Context c) {
        c.getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putLong(STOCK_UNTIL, System.currentTimeMillis() + 60_000).apply();
    }

    @Override
    protected void onServiceConnected() {
        // Every other home screen on this TV (Google's, or the maker's own), not just the ones we know by name.
        stock = new HashSet<>(Arrays.asList(getResources().getStringArray(R.array.stock_launchers)));
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        for (android.content.pm.ResolveInfo ri : getPackageManager().queryIntentActivities(home, 0)) {
            if (ri.activityInfo == null) continue;
            String p = ri.activityInfo.packageName;
            if (p.equals(getPackageName()) || p.contains("settings") || p.contains("setup")) continue;
            stock.add(p);
        }
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
        if (stock == null || !stock.contains(e.getPackageName().toString())) return;
        long until = getSharedPreferences(PREFS, MODE_PRIVATE).getLong(STOCK_UNTIL, 0);
        if (System.currentTimeMillis() < until) return;
        goHome();
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
