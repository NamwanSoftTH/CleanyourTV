package com.launcher.clean;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Optional: opens apps from remote buttons that only the stock Google TV home handled
 * (config.xml app_keys). Off by default - with any key-filtering service bound, held keys
 * stop repeating.
 */
public class KeyService extends AccessibilityService {
    private final SparseArray<String> keys = new SparseArray<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private static KeyService instance;
    private String homeLong = "";
    private boolean homeHeld;

    /** System power menu (Restart / Power off, with its own confirmation). False if the helper is off. */
    static boolean showPowerMenu() {
        return instance != null && instance.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    protected void onServiceConnected() {
        instance = this;
        homeLong = getString(R.string.home_long_press).trim();
        keys.clear();
        for (String item : getResources().getStringArray(R.array.app_keys)) {
            String[] kv = item.split(":", 2);
            if (kv.length != 2) continue;
            try {
                keys.put(Integer.parseInt(kv[0].trim()), kv[1].trim());
            } catch (NumberFormatException ignored) {
            }
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_HOME && !homeLong.isEmpty()) return onHome(event);
        String pkg = keys.get(event.getKeyCode());
        if (pkg == null) return false;
        if (event.getAction() == KeyEvent.ACTION_UP) {
            PackageManager pm = getPackageManager();
            Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (i == null) i = pm.getLaunchIntentForPackage(pkg);
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            }
        }
        return true;
    }

    /** Short press goes Home; holding it opens quick settings or Settings (home_long_press). */
    private boolean onHome(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                homeHeld = true;
                handler.postDelayed(onLongPress, getResources().getInteger(R.integer.long_press_ms));
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP && homeHeld) {
            homeHeld = false;
            handler.removeCallbacks(onLongPress);
            performGlobalAction(GLOBAL_ACTION_HOME);
        }
        return true;
    }

    private final Runnable onLongPress = () -> {
        homeHeld = false;
        Intent i = "settings".equals(homeLong)
                ? new Intent(Settings.ACTION_SETTINGS)
                : new Intent(this, QuickPanelActivity.class);
        startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    };

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
