package io.github.ledat100398.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;


/**
 * Keeps the Home button on OwnTV for TVs whose own home screen cannot be removed (some Google TV sets
 * put it back, or refuse to let it go). The same approach as button-remapping apps: an accessibility
 * service that sees the Home key first, plus a guard that steps in if the stock home screen comes to the
 * front by itself, for example right after the TV starts.
 *
 * The guard never fights the TV going to standby: many sets bring their own home screen up while the
 * power button turns them off, and jumping back to OwnTV at that moment cancelled the standby. So it
 * waits a moment, stays out of the way while the screen is off or a power key was just pressed, and only
 * steps in once the TV is clearly on.
 *
 * The install script switches it on over adb; it can be turned off in Settings › Accessibility.
 */
public class HomeKeyService extends AccessibilityService {

    private static final String PREFS = "home";
    private static final String STOCK_UNTIL = "stockUntil";
    private static final long SETTLE_MS = 1500;     // how long the stock home must stay before stepping in
    private static final long POWER_QUIET_MS = 20_000;

    private final Handler main = new Handler(Looper.getMainLooper());
    private long lastJump, quietUntil;
    private boolean screenOff, stockInFront;

    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) {
                screenOff = true;
                main.removeCallbacks(check);
            } else {
                screenOff = false;
                quietUntil = 0;
                main.removeCallbacks(check);
                main.postDelayed(check, SETTLE_MS);   // woke up on the stock home: bring OwnTV up
            }
        }
    };

    private final Runnable check = this::stepInIfStockHome;

    /** Opening the stock home on purpose (Settings › Stock home screen): leave it alone for a minute. */
    static void allowStockHome(Context c) {
        c.getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putLong(STOCK_UNTIL, System.currentTimeMillis() + 60_000).apply();
    }

    @Override
    protected void onServiceConnected() {
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screen, f);
    }

    @Override
    public void onDestroy() {
        main.removeCallbacks(check);
        try {
            unregisterReceiver(screen);
        } catch (IllegalArgumentException ignored) {
        }
        super.onDestroy();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.KEYCODE_POWER || k == KeyEvent.KEYCODE_TV_POWER || k == KeyEvent.KEYCODE_SLEEP
                || k == KeyEvent.KEYCODE_SOFT_SLEEP) {
            // Pressed while on: the TV is about to go to standby, leave whatever it shows alone.
            if (e.getAction() == KeyEvent.ACTION_DOWN && isOn()) {
                quietUntil = SystemClock.uptimeMillis() + POWER_QUIET_MS;
                main.removeCallbacks(check);
            }
            return false;
        }
        if (k != KeyEvent.KEYCODE_HOME) return false;
        if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) goHome();
        return true;          // the system never sees Home, so the stock home screen never opens
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || e.getPackageName() == null) return;
        String pkg = e.getPackageName().toString(), cls = e.getClassName() == null ? "" : e.getClassName().toString();
        main.removeCallbacks(check);
        stockInFront = !pkg.equals(getPackageName()) && isOtherHome(pkg, cls);
        if (stockInFront) main.postDelayed(check, SETTLE_MS);
    }

    /** Runs a moment after the stock home showed up: only if it is still in front and the TV is on. */
    private void stepInIfStockHome() {
        if (!isOn() || SystemClock.uptimeMillis() < quietUntil) return;
        long until = getSharedPreferences(PREFS, MODE_PRIVATE).getLong(STOCK_UNTIL, 0);
        if (System.currentTimeMillis() < until) return;
        if (!stockInFront) return;                   // left it in the meantime
        goHome();
    }

    private boolean isOn() {
        if (screenOff) return false;
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        return pm == null || pm.isInteractive();
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
