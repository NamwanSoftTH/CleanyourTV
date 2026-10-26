package com.launcher.clean;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Quick settings: a glass card on the right, over whatever is on screen. Opened by holding Home
 * (KeyService). Up/Down move, OK opens, Back or Left closes.
 */
public class QuickPanelActivity extends Activity {
    private static final class Item {
        final int icon, label;
        final String value;
        final Intent intent;

        Item(int icon, int label, String value, Intent intent) {
            this.icon = icon;
            this.label = label;
            this.value = value;
            this.intent = intent;
        }
    }

    private final List<Item> items = new ArrayList<>();
    private final List<LinearLayout> rows = new ArrayList<>();
    private LinearLayout card;
    private Typeface fRegular, fMedium;
    private int sel, h;
    private boolean okDown, closing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        h = dm.heightPixels;
        fRegular = Typeface.createFromAsset(getAssets(), "fonts/IBMPlexSansThai-Regular.ttf");
        fMedium = Typeface.createFromAsset(getAssets(), "fonts/IBMPlexSansThai-Medium.ttf");

        String tvSettings = "com.android.tv.settings";
        items.add(new Item(R.drawable.ic_wifi, R.string.qs_network, networkName(),
                new Intent(Settings.ACTION_WIFI_SETTINGS)));
        items.add(new Item(R.drawable.ic_sound, R.string.qs_display_sound, null,
                new Intent(Settings.ACTION_SOUND_SETTINGS)));
        // The Remotes & accessories page (paired devices, "Pair accessory" on top). TV Settings' slice
        // screen only accepts callers holding WRITE_SECURE_SETTINGS (adb-granted, optional); without
        // it, main Settings, where Remotes & accessories is one row away.
        Intent accessories = checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")
                == PackageManager.PERMISSION_GRANTED
                ? new Intent("android.settings.SLICE_SETTINGS").setPackage(tvSettings)
                        .putExtra("slice_uri", "content://com.android.tv.settings.accessories.sliceprovider/general")
                : new Intent(Settings.ACTION_SETTINGS);
        items.add(new Item(R.drawable.ic_bluetooth, R.string.qs_accessories, null, accessories));
        // Explicit: two TV Settings screens answer APPLICATION_SETTINGS, which shows a chooser.
        items.add(new Item(R.drawable.ic_apps, R.string.qs_apps, null,
                new Intent().setClassName(tvSettings, tvSettings + ".device.apps.AppsActivity")));
        items.add(new Item(R.drawable.ic_settings, R.string.qs_all_settings, null,
                new Intent(Settings.ACTION_SETTINGS)));
        items.add(new Item(R.drawable.ic_restart, R.string.qs_restart, null, null)); // power menu

        FrameLayout root = new FrameLayout(this);
        // Soft shade towards the card so it reads over a bright picture.
        GradientDrawable shade = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x00000000, 0x99000000});
        root.setBackground(shade);
        root.addView(buildCard());
        setContentView(root);
        setSel(0);

        card.setTranslationX(h * 0.08f);
        card.setAlpha(0f);
        card.animate().translationX(0).alpha(1f).setDuration(220)
                .setInterpolator(new DecelerateInterpolator(1.6f)).start();
    }

    private View buildCard() {
        card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFC181A22);
        bg.setCornerRadius(h * 0.03f);
        bg.setStroke(1, 0x33FFFFFF);
        card.setBackground(bg);
        int pad = Math.round(h * 0.022f);
        card.setPadding(pad, pad, pad, pad);

        TextView title = text(fMedium, h * 0.03f, 0xFFFFFFFF);
        title.setText(R.string.qs_title);
        int tp = Math.round(h * 0.014f);
        title.setPadding(tp, 0, tp, Math.round(h * 0.016f));
        card.addView(title);

        for (Item it : items) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(tp, 0, tp, 0);
            ImageView iv = new ImageView(this);
            iv.setImageResource(it.icon);
            int is = Math.round(h * 0.03f);
            row.addView(iv, new LinearLayout.LayoutParams(is, is));
            TextView label = text(fRegular, h * 0.025f, 0xF2FFFFFF);
            label.setText(it.label);
            label.setSingleLine();
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(0, -2, 1f);
            ll.leftMargin = Math.round(h * 0.018f);
            row.addView(label, ll);
            TextView value = text(fRegular, h * 0.021f, 0x99FFFFFF);
            value.setSingleLine();
            value.setEllipsize(TextUtils.TruncateAt.END);
            value.setMaxWidth(Math.round(h * 0.2f));
            if (it.value != null) value.setText(it.value);
            row.addView(value);
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, Math.round(h * 0.068f));
            rl.topMargin = Math.round(h * 0.004f);
            card.addView(row, rl);
            rows.add(row);
        }

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.round(h * 0.56f), -2,
                Gravity.END | Gravity.TOP);
        lp.topMargin = Math.round(h * 0.06f);
        lp.rightMargin = Math.round(h * 0.06f);
        card.setLayoutParams(lp);
        return card;
    }

    private TextView text(Typeface face, float px, int color) {
        TextView t = new TextView(this);
        t.setTypeface(face);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        t.setTextColor(color);
        return t;
    }

    /** Focused row: white pill with black content, like the pills on Home. */
    private void setSel(int s) {
        sel = s;
        for (int i = 0; i < rows.size(); i++) {
            LinearLayout row = rows.get(i);
            boolean on = i == s;
            if (on) {
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(0xF2FFFFFF);
                bg.setCornerRadius(h * 0.018f);
                row.setBackground(bg);
            } else {
                row.setBackground(null);
            }
            ((ImageView) row.getChildAt(0)).setColorFilter(on ? 0xFF000000 : 0xE6FFFFFF);
            ((TextView) row.getChildAt(1)).setTextColor(on ? 0xFF000000 : 0xF2FFFFFF);
            ((TextView) row.getChildAt(2)).setTextColor(on ? 0x99000000 : 0x99FFFFFF);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean ok = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
                || k == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (k == KeyEvent.KEYCODE_DPAD_UP) {
                if (sel > 0) setSel(sel - 1);
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (sel < rows.size() - 1) setSel(sel + 1);
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_RIGHT) return true;
            if (ok) {
                if (e.getRepeatCount() == 0) okDown = true;
                return true;
            }
            if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_DPAD_LEFT) return true;
        } else if (e.getAction() == KeyEvent.ACTION_UP) {
            if (ok) {
                if (okDown) open(items.get(sel));
                okDown = false;
                return true;
            }
            if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_DPAD_LEFT) {
                close();
                return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    private void open(Item it) {
        if (it.intent == null) {
            // Restart: the system power menu asks for confirmation itself.
            if (!KeyService.showPowerMenu()) {
                Toast.makeText(this, getString(R.string.cannot_open, getString(it.label)), Toast.LENGTH_SHORT).show();
                return;
            }
            finish();
            overridePendingTransition(0, 0);
            return;
        }
        try {
            // Fresh task: otherwise Back lands on whatever Settings screen was left open earlier.
            startActivity(new Intent(it.intent)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        } catch (ActivityNotFoundException | SecurityException ex) {
            Toast.makeText(this, getString(R.string.cannot_open, getString(it.label)), Toast.LENGTH_SHORT).show();
            return;
        }
        finish();
        overridePendingTransition(0, 0);
    }

    private void close() {
        if (closing) return;
        closing = true;
        card.animate().translationX(h * 0.08f).alpha(0f).setDuration(160)
                .withEndAction(() -> {
                    finish();
                    overridePendingTransition(0, 0);
                }).start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Something else came to the front (Home, an app): don't leave the panel behind.
        if (!isFinishing()) finish();
    }

    @SuppressWarnings("deprecation")
    private String networkName() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
        if (caps == null) return getString(R.string.offline);
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return getString(R.string.ethernet);
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null;
        WifiInfo wi = getApplicationContext().getSystemService(WifiManager.class).getConnectionInfo();
        String ssid = wi == null ? null : wi.getSSID();
        if (ssid != null && ssid.length() > 1 && ssid.startsWith("\"") && ssid.endsWith("\"")) {
            ssid = ssid.substring(1, ssid.length() - 1);
        }
        return TextUtils.isEmpty(ssid) || "<unknown ssid>".equals(ssid) ? getString(R.string.wifi) : ssid;
    }
}
