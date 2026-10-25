package com.launcher.mi.box.s3;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
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
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeActivity extends Activity implements TileAdapter.Host {
    private static final int ZONE_TILES = 0, ZONE_TOP = 1, ZONE_MENU = 2, ZONE_MOVE = 3;
    private static final int MENU_INPUTS = 0, MENU_ACTIONS = 1;
    private static final int ACTION_MOVE = 0, ACTION_INFO = 1, ACTION_RESET = 2;
    private static final int MOVE_MS = 300;
    private static final Interpolator SCROLL = new DecelerateInterpolator(1.6f);
    private static final Interpolator SPRING = new OvershootInterpolator(1.15f);

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Geo g;
    private SharedPreferences prefs;
    private Typeface fThin, fLight, fRegular, fMedium;

    private RecyclerView rv;
    private GridLayoutManager glm;
    private TileAdapter adapter;
    private List<AppEntry> apps = new ArrayList<>();
    private int dockCount;

    private View header, glow, highlight;
    private TextView greeting, clock, date, title;
    private LinearLayout pillRow, menu;
    private final List<View> pills = new ArrayList<>();
    private TextView wifiText, usbText;
    private View inputsPill, usbPill, wifiPill, settingsPill;
    private String usbLabel; // mounted USB drive's name, null when none
    private final List<TvInputInfo> inputs = new ArrayList<>();
    private GradientDrawable glowDrawable;
    private ValueAnimator glowAnim;
    private int glowColor = 0xFF8E8E93;

    private boolean hasWallpaper;
    private boolean dockEnabled = true; // config.xml dock_enabled; false = app list only
    private GradientDrawable hlNormal, hlMoving;
    private TextView moveHint;
    private int zone = ZONE_TILES;
    private int menuKind;
    private final List<Integer> menuActions = new ArrayList<>();
    private int sel, lastDockCol, topSel, menuSel;
    private int scrollY;
    private boolean resumed, longPressed, okDown;
    private ConnectivityManager.NetworkCallback netCallback;

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("home", MODE_PRIVATE);
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        int cols = Math.max(4, Math.min(7, getResources().getInteger(R.integer.columns)));
        g = new Geo(dm.widthPixels, dm.heightPixels, cols, getResources().getInteger(R.integer.dock_position));
        fThin = font("ExtraLight");
        fLight = font("Light");
        fRegular = font("Regular");
        fMedium = font("Medium");
        loadUsb();
        setContentView(buildUi());
        loadInputs();
        loadApps();
        registerPackageReceiver();
        registerUsbReceiver();
        registerNetwork();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(timeReceiver, f);
        updateClock();
        usbReceiver.onReceive(this, null); // a drive may have come or gone while an app was open
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        unregisterReceiver(timeReceiver);
        ui.removeCallbacks(okLongPress);
        if (zone == ZONE_MOVE) endMove();
        if (sel < apps.size()) prefs.edit().putString("sel", apps.get(sel).pkg).apply();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while already on Home: back to the dock. Otherwise keep the last tile.
        if (resumed && !apps.isEmpty()) {
            if (zone == ZONE_MOVE) endMove();
            closeMenu();
            zone = ZONE_TILES;
            refreshPills();
            select(dockCount > 0 ? Math.min(lastDockCol, dockCount - 1) : 0, true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(packageReceiver);
        unregisterReceiver(usbReceiver);
        if (netCallback != null) getSystemService(ConnectivityManager.class).unregisterNetworkCallback(netCallback);
        worker.shutdownNow();
    }

    /** config.xml clock_weight; anything unknown falls back to ExtraLight. */
    private Typeface clockFont() {
        switch (getString(R.string.clock_weight).trim()) {
            case "Light": return fLight;
            case "Regular": return fRegular;
            case "Medium": return fMedium;
            default: return fThin;
        }
    }

    private Typeface font(String weight) {
        return Typeface.createFromAsset(getAssets(), "fonts/IBMPlexSansThai-" + weight + ".ttf");
    }

    // ---------------------------------------------------------------- layout

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(getColor(R.color.background));
        root.setClipChildren(false);

        String wp = getString(R.string.wallpaper);
        int wpId = TextUtils.isEmpty(wp) ? 0 : getResources().getIdentifier(wp, "drawable", getPackageName());
        hasWallpaper = wpId != 0;
        if (hasWallpaper) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageResource(wpId);
            root.addView(iv, new FrameLayout.LayoutParams(-1, -1));
            int scrimPct = Math.max(0, Math.min(100, getResources().getInteger(R.integer.wallpaper_scrim)));
            if (scrimPct > 0) {
                View scrim = new View(this);
                scrim.setBackgroundColor(Math.round(scrimPct * 2.55f) << 24);
                root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
            }
        }

        // Ambient glow behind the dock app's name: a radial gradient squashed into an ellipse.
        int glowSize = Math.round(g.w * 0.5f);
        glowDrawable = new GradientDrawable();
        glowDrawable.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glowDrawable.setGradientRadius(glowSize / 2f);
        glow = new View(this);
        glow.setBackground(glowDrawable);
        glow.setScaleY(0.32f);
        setGlowColor(glowColor);
        int titleSize = Math.round(g.h * 0.064f);
        int titleH = Math.round(titleSize * 1.7f);
        int titleTop = g.dockTop - titleH - Math.round(g.h * 0.005f);
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(glowSize, glowSize, Gravity.TOP | Gravity.START);
        glp.leftMargin = g.tileX(0) + Math.round(g.w * 0.12f) - glowSize / 2;
        glp.topMargin = titleTop + titleH / 2 - glowSize / 2;
        root.addView(glow, glp);

        View headerView = buildHeader(); // measured first: the app area starts under it (rvTop)

        // The app list starts under the fixed header; tiles scrolling up are cut off at its top
        // edge (clipBounds, since root doesn't clip its children) instead of covering the clock.
        rv = new RecyclerView(this) {
            @Override
            protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                super.onSizeChanged(w, h, oldw, oldh);
                setClipBounds(new android.graphics.Rect(0, 0, w, h));
            }
        };
        dockEnabled = getResources().getBoolean(R.bool.dock_enabled);
        if (!dockEnabled) g.gridOnlyTop = rvTop + Math.round(g.h * 0.01f); // app list right under the header
        rv.setPadding(g.padL, (dockEnabled ? g.dockTop : g.gridOnlyTop) - rvTop, g.padR, g.h);
        rv.setClipToPadding(false);
        rv.setFocusable(false);
        rv.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        rv.setItemAnimator(null);
        rv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rv.setVerticalScrollBarEnabled(false);
        rv.setItemViewCacheSize(40);
        glm = new GridLayoutManager(this, g.cols) {
            @Override
            public boolean requestChildRectangleOnScreen(@NonNull RecyclerView parent, @NonNull View child,
                    @NonNull android.graphics.Rect rect, boolean immediate) {
                return false; // scrolling is ours, see select()
            }

            @Override
            public boolean requestChildRectangleOnScreen(@NonNull RecyclerView parent, @NonNull View child,
                    @NonNull android.graphics.Rect rect, boolean immediate, boolean focusedChildVisible) {
                return false;
            }
        };
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                // A short dock still ends its row, so the grid starts on a row of its own.
                return position == dockCount - 1 ? g.cols - dockCount + 1 : 1;
            }
        });
        rv.setLayoutManager(glm);
        rv.addItemDecoration(new DockBand());
        adapter = new TileAdapter(g, this, fRegular, fRegular);
        rv.setAdapter(adapter);
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView r, int dx, int dy) {
                syncScroll();
            }
        });
        // A data change re-lays the list out without onScrolled; read the offset again after every layout.
        rv.getViewTreeObserver().addOnGlobalLayoutListener(this::syncScroll);
        FrameLayout.LayoutParams rvl = new FrameLayout.LayoutParams(-1, -1);
        rvl.topMargin = rvTop;
        root.addView(rv, rvl);

        title = new TextView(this);
        title.setTypeface(fLight);
        title.setTextColor(0xF7FFFFFF);
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, titleSize);
        title.setSingleLine();
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setGravity(Gravity.BOTTOM | Gravity.START);
        title.setIncludeFontPadding(false);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(g.w - 2 * g.tileX(0), titleH, Gravity.TOP | Gravity.START);
        tlp.leftMargin = g.tileX(0);
        tlp.topMargin = titleTop;
        root.addView(title, tlp);

        root.addView(headerView);
        root.addView(buildPills());

        highlight = new View(this);
        hlNormal = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x38FFFFFF, 0x0AFFFFFF});
        hlNormal.setCornerRadius(g.radius * Geo.SCALE + g.hlPad);
        hlNormal.setStroke(Math.max(2, Math.round(g.h * 0.0022f)), 0x99FFFFFF);
        // Move mode: the picked-up tile gets a bright, thick edge.
        hlMoving = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x26FFFFFF, 0x26FFFFFF});
        hlMoving.setCornerRadius(g.radius * Geo.SCALE + g.hlPad);
        hlMoving.setStroke(Math.max(4, Math.round(g.h * 0.005f)), 0xFFFFFFFF);
        highlight.setBackground(hlNormal);
        highlight.setAlpha(0f);
        root.addView(highlight, new FrameLayout.LayoutParams(g.hlW, g.hlH, Gravity.TOP | Gravity.START));

        moveHint = text(fMedium, g.h * 0.024f, 0xFF000000);
        moveHint.setText(R.string.move_hint);
        moveHint.setGravity(Gravity.CENTER);
        int hp = Math.round(g.h * 0.028f);
        moveHint.setPadding(hp, 0, hp, 0);
        GradientDrawable hbg = new GradientDrawable();
        hbg.setColor(0xF2FFFFFF);
        hbg.setCornerRadius(g.h);
        moveHint.setBackground(hbg);
        moveHint.setVisibility(View.GONE);
        FrameLayout.LayoutParams mhl = new FrameLayout.LayoutParams(-2, Math.round(g.h * 0.058f),
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        mhl.topMargin = Math.round(g.h * 0.068f);
        root.addView(moveHint, mhl);

        menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable mbg = new GradientDrawable();
        mbg.setColor(0xF0202024);
        mbg.setCornerRadius(g.h * 0.022f);
        mbg.setStroke(1, 0x33FFFFFF);
        menu.setBackground(mbg);
        int mp = Math.round(g.h * 0.01f);
        menu.setPadding(mp, mp, mp, mp);
        menu.setVisibility(View.GONE);
        root.addView(menu, new FrameLayout.LayoutParams(Math.round(g.w * 0.17f), -2, Gravity.TOP | Gravity.START));
        return root;
    }

    /**
     * Compact header that stays on screen: greeting, then "12:34 | weekday d month" on one line.
     * Measured here so the app area (rvTop) and the pills can be placed against it.
     */
    private View buildHeader() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.VERTICAL);
        greeting = text(fRegular, g.h * 0.026f, 0xB3FFFFFF);
        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setGravity(Gravity.CENTER_VERTICAL);
        clock = text(clockFont(), g.h * 0.085f, 0xFFFFFFFF);
        clock.setIncludeFontPadding(false);
        View divider = new View(this);
        divider.setBackgroundColor(0x59FFFFFF);
        date = text(fRegular, g.h * 0.028f, 0xD9FFFFFF);
        // WRAP_CONTENT widths: with MATCH_PARENT a wider new time never re-measures the
        // wrap_content header and the last digit gets cut off.
        timeRow.addView(clock, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams dv = new LinearLayout.LayoutParams(Math.max(2, Math.round(g.h * 0.002f)),
                Math.round(g.h * 0.05f));
        dv.leftMargin = dv.rightMargin = Math.round(g.h * 0.02f);
        timeRow.addView(divider, dv);
        timeRow.addView(date, new LinearLayout.LayoutParams(-2, -2));
        // Tight lines: Plex Thai's line box reserves room for Thai marks above and below.
        greeting.setIncludeFontPadding(false);
        date.setIncludeFontPadding(false);
        h.addView(greeting, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams tr = new LinearLayout.LayoutParams(-2, -2);
        tr.topMargin = -Math.round(g.h * 0.024f); // the big digits' line box still has headroom
        h.addView(timeRow, tr);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        lp.leftMargin = g.tileX(0);
        lp.topMargin = Math.round(g.h * 0.03f);
        header = h;
        h.setLayoutParams(lp);

        updateClock(); // real text, so the measurement below is the real height
        h.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int pillH = Math.round(g.h * 0.054f);
        int timeCenter = lp.topMargin + greeting.getMeasuredHeight() + tr.topMargin + timeRow.getMeasuredHeight() / 2;
        pillTop = timeCenter - pillH / 2;
        rvTop = lp.topMargin + h.getMeasuredHeight() + Math.round(g.h * 0.006f); // just under the header
        return h;
    }

    private int pillTop, rvTop; // set by buildHeader()

    private View buildPills() {
        pillRow = new LinearLayout(this);
        pillRow.setOrientation(LinearLayout.HORIZONTAL);
        pillRow.setClipChildren(false); // the focused pill scales past the row's edge
        inputsPill = pill(R.drawable.ic_input, getString(R.string.inputs));
        usbPill = pill(R.drawable.ic_usb, getString(R.string.usb));
        usbText = (TextView) ((ViewGroup) usbPill).getChildAt(1);
        usbText.setMaxWidth(Math.round(g.w * 0.12f));
        wifiPill = pill(R.drawable.ic_wifi, getString(R.string.wifi));
        wifiText = (TextView) ((ViewGroup) wifiPill).getChildAt(1);
        wifiText.setMaxWidth(Math.round(g.w * 0.16f));
        settingsPill = pill(R.drawable.ic_settings, getString(R.string.settings));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        lp.rightMargin = g.w - (g.tileX(g.cols - 1) + g.tileW);
        lp.topMargin = pillTop; // centred on the time line
        pillRow.setLayoutParams(lp);
        rebuildPillRow();
        return pillRow;
    }

    private void rebuildPillRow() {
        pillRow.removeAllViews();
        pills.clear();
        if (!inputs.isEmpty()) pills.add(inputsPill);
        if (usbLabel != null) {
            usbText.setText(usbLabel);
            pills.add(usbPill);
        }
        pills.add(wifiPill);
        pills.add(settingsPill);
        int space = Math.round(g.gap * 0.6f);
        for (int i = 0; i < pills.size(); i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, Math.round(g.h * 0.054f));
            if (i > 0) lp.leftMargin = space;
            pillRow.addView(pills.get(i), lp);
        }
        topSel = Math.min(topSel, pills.size() - 1);
        refreshPills();
    }

    private View pill(int icon, String label) {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.HORIZONTAL);
        p.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Math.round(g.h * 0.022f);
        p.setPadding(pad, 0, pad, 0);
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        int is = Math.round(g.h * 0.026f);
        p.addView(iv, new LinearLayout.LayoutParams(is, is));
        TextView t = text(fMedium, g.h * 0.022f, 0xE6FFFFFF);
        t.setSingleLine();
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setText(label);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.leftMargin = Math.round(g.h * 0.01f);
        p.addView(t, tl);
        return p;
    }

    private TextView text(Typeface face, float px, int color) {
        TextView t = new TextView(this);
        t.setTypeface(face);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        t.setTextColor(color);
        return t;
    }

    /** The lighter rounded band behind the dock row; it scrolls with the tiles. */
    private final class DockBand extends RecyclerView.ItemDecoration {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        DockBand() {
            // Glass over a wallpaper (dock_opacity), solid on a plain background.
            int alpha = hasWallpaper
                    ? Math.round(Math.max(0, Math.min(100, getResources().getInteger(R.integer.dock_opacity))) * 2.55f)
                    : 255;
            fill.setColor((alpha << 24) | 0x161618);
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(1);
            edge.setColor(0x14FFFFFF);
        }

        /** The first grid row sits dockGap lower, away from the band. */
        @Override
        public void getItemOffsets(@NonNull android.graphics.Rect out, @NonNull View view,
                @NonNull RecyclerView parent, @NonNull RecyclerView.State s) {
            int p = parent.getChildAdapterPosition(view);
            boolean belowDock = dockCount > 0 && p != RecyclerView.NO_POSITION && p < apps.size() && row(p) == 1;
            out.set(0, belowDock ? g.dockGap : 0, 0, 0);
        }

        @Override
        public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State s) {
            if (dockCount == 0) return;
            View first = glm.findViewByPosition(0);
            if (first == null) return;
            float top = first.getTop();
            r.set(g.tileX(0) - g.gap, top, g.tileX(g.cols - 1) + g.tileW + g.gap, top + g.dockItemH);
            float rad = g.radius * 2.2f;
            c.drawRoundRect(r, rad, rad, fill);
            c.drawRoundRect(r, rad, rad, edge);
        }
    }

    // ---------------------------------------------------------------- apps

    private void loadApps() {
        String[] dock = getResources().getStringArray(R.array.dock_apps);
        String[] order = getResources().getStringArray(R.array.app_order);
        // An order arranged on the TV (move mode) overrides config.xml until "Reset app order".
        String saved = prefs.getString("order", null);
        if (!TextUtils.isEmpty(saved)) {
            String[] all = saved.split(",");
            int n = Math.max(0, Math.min(prefs.getInt("dock_n", g.cols), all.length));
            dock = java.util.Arrays.copyOfRange(all, 0, n);
            order = java.util.Arrays.copyOfRange(all, n, all.length);
        }
        String[] hidden = getResources().getStringArray(R.array.hidden_apps);
        if (!dockEnabled) {
            // No dock: the dock apps lead the list, so favourites stay at the top.
            List<String> all = new ArrayList<>(java.util.Arrays.asList(dock));
            all.addAll(java.util.Arrays.asList(order));
            order = all.toArray(new String[0]);
            dock = new String[0];
        }
        String[] dockApps = dock, orderApps = order;
        worker.execute(() -> {
            AppEntry.Apps loaded = AppEntry.load(this, dockApps, orderApps, hidden, g.cols, dockEnabled);
            ui.post(() -> showApps(loaded));
        });
    }

    private void showApps(AppEntry.Apps loaded) {
        String keep = apps.isEmpty() ? prefs.getString("sel", null)
                : (sel < apps.size() ? apps.get(sel).pkg : null);
        apps = loaded.all;
        dockCount = loaded.dockCount;
        sel = 0;
        for (int i = 0; i < apps.size(); i++) if (apps.get(i).pkg.equals(keep)) sel = i;
        if (row(sel) == 0) lastDockCol = sel;
        rv.stopScroll();
        adapter.setItems(apps, dockCount);
        if (apps.isEmpty()) return;
        rv.post(() -> select(sel, false));
    }

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            loadApps();
        }
    };

    private void registerPackageReceiver() {
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        registerReceiver(packageReceiver, f);
    }

    // ---------------------------------------------------------------- grid model

    private int row(int i) {
        return i < dockCount ? 0 : 1 + (i - dockCount) / g.cols;
    }

    private int col(int i) {
        return i < dockCount ? i : (i - dockCount) % g.cols;
    }

    private int rowStart(int r) {
        return r == 0 ? 0 : dockCount + (r - 1) * g.cols;
    }

    private int rowLen(int r) {
        return r == 0 ? dockCount : Math.min(g.cols, apps.size() - rowStart(r));
    }

    /** The top row of tiles: the dock (0), or grid row 1 when there is no dock. */
    private int firstRow() {
        return dockCount > 0 ? 0 : 1;
    }

    private int rowCount() {
        int grid = apps.size() - dockCount;
        return 1 + (grid + g.cols - 1) / g.cols;
    }

    @Override
    public int focusedTile() {
        return onTiles() ? sel : -1;
    }

    /** Focus is on a tile: browsing, or carrying it in move mode. */
    private boolean onTiles() {
        return zone == ZONE_TILES || zone == ZONE_MOVE;
    }

    // ---------------------------------------------------------------- focus and scrolling

    /**
     * The real scroll offset, read from any laid-out tile: its row's top in content coordinates
     * minus where it is now. (Adding up onScrolled deltas drifts: a re-layout moves the list
     * without reporting it, and the highlight then floats where no tile is.)
     */
    private int currentOffset() {
        for (int i = 0; i < rv.getChildCount(); i++) {
            int p = rv.getChildAdapterPosition(rv.getChildAt(i));
            if (p == RecyclerView.NO_POSITION || p >= apps.size()) continue;
            return g.rowTop(row(p)) - (rvTop + rv.getChildAt(i).getTop()); // child tops are relative to rv
        }
        return scrollY;
    }

    /**
     * Scroll offset that shows row {@code r}, moving as little as possible from {@code now}: nothing
     * while the focused tile (grown, with its glass edge) and its name are already between the
     * header and the bottom of the screen; otherwise just enough. The dock row goes back to 0.
     */
    private int targetFor(int r, int now) {
        if (r == 0) return 0;
        int y = g.tileY(r, now);
        int top = y - Math.round(g.tileH * (Geo.SCALE - 1f) / 2f) - g.hlPad;
        int bottom = y + g.tileH + g.labelGap + g.labelH;
        int minTop = rvTop + Math.round(g.h * 0.015f);
        int maxBottom = g.h - Math.round(g.h * 0.03f);
        if (top < minTop) return Math.max(0, now - (minTop - top));
        if (bottom > maxBottom) return now + (bottom - maxBottom);
        return now;
    }

    private void syncScroll() {
        int now = currentOffset();
        if (now != scrollY) {
            scrollY = now;
            applyScroll();
        }
        // Safety net: a select() that ran before the list was laid out could not scroll, while the
        // highlight already went to the target. When idle and off target, scroll there once.
        if (apps.isEmpty() || rv.getChildCount() == 0 || rv.getScrollState() != RecyclerView.SCROLL_STATE_IDLE) return;
        int target = targetFor(row(sel), now);
        if (Math.abs(target - now) <= 1) {
            lastFix = Integer.MIN_VALUE;
            return;
        }
        if (target - now == lastFix) return; // tried this already; don't loop
        lastFix = target - now;
        rv.scrollBy(0, target - now);
        moveHighlight(target, false);
    }

    private int lastFix = Integer.MIN_VALUE;

    private void select(int idx, boolean animate) {
        if (apps.isEmpty()) return;
        idx = Math.max(0, Math.min(idx, apps.size() - 1));
        int old = sel;
        sel = idx;
        if (old != idx) setTileFocus(old, false, animate);
        setTileFocus(idx, onTiles(), animate);
        int r = row(idx);
        if (r == 0) lastDockCol = idx;

        scrollY = currentOffset();
        int target = targetFor(r, scrollY);
        int dy = target - scrollY;
        if (dy != 0) {
            if (animate) rv.smoothScrollBy(0, dy, SCROLL, MOVE_MS);
            else rv.scrollBy(0, dy);
        }
        moveHighlight(target, animate);
        updateTitle(animate);
    }

    private void setTileFocus(int idx, boolean on, boolean animate) {
        View v = glm.findViewByPosition(idx);
        if (v == null) return; // bound later; onBind reads focusedTile()
        ((TileAdapter.VH) rv.getChildViewHolder(v)).setFocused(on, animate);
    }

    private void moveHighlight(int offset, boolean animate) {
        highlight.animate().cancel();
        highlight.setBackground(zone == ZONE_MOVE ? hlMoving : hlNormal);
        if (!onTiles()) {
            highlight.animate().alpha(0f).setDuration(150).start();
            return;
        }
        float x = g.tileX(col(sel)) + g.tileW / 2f - g.hlW / 2f;
        float y = g.tileY(row(sel), offset) + g.tileH / 2f - g.hlH / 2f;
        if (!animate || highlight.getAlpha() == 0f) {
            highlight.setTranslationX(x);
            highlight.setTranslationY(y);
            highlight.animate().alpha(1f).setDuration(animate ? 150 : 0).start();
        } else {
            highlight.animate().translationX(x).translationY(y).alpha(1f)
                    .setInterpolator(SPRING).setDuration(MOVE_MS).start();
        }
    }

    /** The big app name above the dock, only while a dock tile is focused. */
    private void updateTitle(boolean animate) {
        boolean show = onTiles() && row(sel) == 0 && sel < apps.size();
        if (show) {
            AppEntry e = apps.get(sel);
            title.setText(e.label);
            title.setShadowLayer(g.h * 0.03f, 0, 0, (e.color & 0x00FFFFFF) | 0x99000000);
            animateGlow(e.color, animate);
        }
        float a = show ? 1f : 0f;
        title.animate().alpha(a).setDuration(animate ? 200 : 0).start();
        glow.animate().alpha(a).setDuration(animate ? 250 : 0).start();
    }

    private void animateGlow(int to, boolean animate) {
        if (glowAnim != null) glowAnim.cancel();
        if (!animate) {
            setGlowColor(to);
            return;
        }
        glowAnim = ValueAnimator.ofObject(new ArgbEvaluator(), glowColor, to);
        glowAnim.setDuration(350);
        glowAnim.addUpdateListener(a -> setGlowColor((int) a.getAnimatedValue()));
        glowAnim.start();
    }

    private void setGlowColor(int c) {
        glowColor = c;
        glowDrawable.setColors(new int[]{(c & 0x00FFFFFF) | 0x59000000, (c & 0x00FFFFFF) | 0x1A000000, 0});
    }

    /** The header and pills stay put; only the dock app's name and glow travel with the list. */
    private void applyScroll() {
        title.setTranslationY(-scrollY);
        glow.setTranslationY(-scrollY);
    }

    // ---------------------------------------------------------------- keys

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean ok = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
                || k == KeyEvent.KEYCODE_NUMPAD_ENTER;
        boolean dir = k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN
                || k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT;
        boolean back = k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_ESCAPE;
        if (!ok && !dir && !back) return super.dispatchKeyEvent(e);
        if (apps.isEmpty()) return true;

        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (ok) {
                // Hold is timed, not counted in key repeats: the remote-button helper
                // (a key-filtering accessibility service) stops keys from repeating.
                if (e.getRepeatCount() == 0) {
                    okDown = true;
                    longPressed = false;
                    if (zone == ZONE_TILES) ui.postDelayed(okLongPress, getResources().getInteger(R.integer.long_press_ms));
                }
            } else if (dir) {
                move(k);
            }
        } else if (e.getAction() == KeyEvent.ACTION_UP) {
            if (ok) {
                ui.removeCallbacks(okLongPress);
                // Ignore an OK release whose press started in another app.
                if (okDown && !longPressed) activate();
                okDown = false;
                longPressed = false;
            } else if (back) {
                goBack();
            }
        }
        return true;
    }

    private final Runnable okLongPress = () -> {
        if (!okDown || zone != ZONE_TILES) return;
        longPressed = true;
        openActions();
    };

    private void move(int k) {
        if (zone == ZONE_MENU) {
            if (k == KeyEvent.KEYCODE_DPAD_UP && menuSel > 0) setMenuSel(menuSel - 1);
            else if (k == KeyEvent.KEYCODE_DPAD_DOWN && menuSel < menu.getChildCount() - 1) setMenuSel(menuSel + 1);
            else if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) closeMenu();
            return;
        }
        if (zone == ZONE_MOVE) {
            moveCarried(k);
            return;
        }
        if (zone == ZONE_TOP) {
            // The ends of the pill row stop; only Down leaves it, back to the remembered tile.
            if (k == KeyEvent.KEYCODE_DPAD_LEFT && topSel > 0) topSel--;
            else if (k == KeyEvent.KEYCODE_DPAD_RIGHT && topSel < pills.size() - 1) topSel++;
            else if (k == KeyEvent.KEYCODE_DPAD_DOWN) {
                zone = ZONE_TILES;
                select(sel, true);
            }
            refreshPills();
            return;
        }
        int r = row(sel), c = col(sel);
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (c > 0) select(sel - 1, true);
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (c < rowLen(r) - 1) select(sel + 1, true);
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (r == firstRow()) {
                    zone = ZONE_TOP;
                    select(sel, true);
                    refreshPills();
                } else {
                    select(rowStart(r - 1) + Math.min(c, rowLen(r - 1) - 1), true);
                }
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (r < rowCount() - 1) select(rowStart(r + 1) + Math.min(c, rowLen(r + 1) - 1), true);
                break;
        }
    }

    // ---------------------------------------------------------------- arranging apps on the TV

    /** Hold OK on a tile: Move / App info / Reset app order, under (or above) the tile. */
    private void openActions() {
        menuActions.clear();
        menuActions.add(ACTION_MOVE);
        menuActions.add(ACTION_INFO);
        if (prefs.contains("order")) menuActions.add(ACTION_RESET);
        List<String> labels = new ArrayList<>();
        for (int a : menuActions) {
            labels.add(getString(a == ACTION_MOVE ? R.string.action_move
                    : a == ACTION_INFO ? R.string.action_info : R.string.action_reset));
        }
        fillMenu(labels);
        int menuW = menu.getLayoutParams().width;
        menu.measure(View.MeasureSpec.makeMeasureSpec(menuW, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int mh = menu.getMeasuredHeight();
        int top = g.tileY(row(sel), scrollY);
        int gapY = Math.round(g.h * 0.03f);
        float y = row(sel) == 0 || top + g.tileH + gapY + mh > g.h ? top - gapY - mh : top + g.tileH + gapY;
        float x = Math.min(g.tileX(col(sel)), g.w - g.tileX(0) - menuW);
        showMenu(MENU_ACTIONS, x, y);
    }

    private void runAction(int action) {
        AppEntry e = apps.get(sel);
        closeMenu();
        if (action == ACTION_MOVE) {
            zone = ZONE_MOVE;
            moveHint.setVisibility(View.VISIBLE);
            moveHint.setAlpha(0f);
            moveHint.animate().alpha(1f).setDuration(150).start();
            select(sel, true);
        } else if (action == ACTION_INFO) {
            openAppInfo(e);
        } else {
            prefs.edit().remove("order").remove("dock_n").apply();
            loadApps(); // back to config.xml; keeps focus on the same app
        }
    }

    /**
     * Move mode: the arrows carry the tile. Left/Right step through the whole list (so a tile can
     * cross from the dock into the grid and back), Up/Down a row. The dock keeps its size: a tile
     * moved into it pushes the last dock app into the grid.
     */
    private void moveCarried(int k) {
        int r = row(sel), c = col(sel), to = -1;
        if (k == KeyEvent.KEYCODE_DPAD_LEFT && sel > 0) to = sel - 1;
        else if (k == KeyEvent.KEYCODE_DPAD_RIGHT && sel < apps.size() - 1) to = sel + 1;
        else if (k == KeyEvent.KEYCODE_DPAD_UP && r > firstRow()) to = rowStart(r - 1) + Math.min(c, rowLen(r - 1) - 1);
        else if (k == KeyEvent.KEYCODE_DPAD_DOWN && r < rowCount() - 1)
            to = Math.min(apps.size() - 1, rowStart(r + 1) + c);
        if (to < 0 || to == sel) return;
        rv.stopScroll(); // don't re-lay the list out under a running smooth scroll
        apps.add(to, apps.remove(sel));
        adapter.notifyDataSetChanged();
        sel = to;
        // Scroll and place the highlight after the new layout, from the offset it really has.
        rv.post(() -> select(sel, true));
    }

    /** OK or Back in move mode: drop the tile and remember the whole order on the box. */
    private void endMove() {
        zone = ZONE_TILES;
        moveHint.setVisibility(View.GONE);
        List<String> pkgs = new ArrayList<>();
        for (AppEntry a : apps) pkgs.add(a.pkg);
        prefs.edit().putString("order", TextUtils.join(",", pkgs)).putInt("dock_n", dockCount).apply();
        select(sel, true);
    }

    private void goBack() {
        if (zone == ZONE_MOVE) {
            endMove();
        } else if (zone == ZONE_MENU) {
            closeMenu();
        } else if (zone == ZONE_TOP) {
            zone = ZONE_TILES;
            select(sel, true);
            refreshPills();
        } else if (row(sel) > firstRow()) {
            // Back to the dock (last dock tile), or to the first app when there is no dock.
            select(dockCount > 0 ? Math.min(lastDockCol, dockCount - 1) : 0, true);
        }
    }

    private void activate() {
        if (zone == ZONE_MOVE) {
            endMove();
        } else if (zone == ZONE_MENU) {
            if (menuKind == MENU_INPUTS) switchInput(inputs.get(menuSel));
            else runAction(menuActions.get(menuSel));
        } else if (zone == ZONE_TOP) {
            View p = pills.get(topSel);
            if (p == inputsPill) openMenu();
            else if (p == usbPill) openUsb();
            else if (p == wifiPill) start(new Intent(Settings.ACTION_WIFI_SETTINGS), getString(R.string.wifi));
            else start(new Intent(Settings.ACTION_SETTINGS), getString(R.string.settings));
        } else {
            AppEntry e = apps.get(sel);
            start(e.launch, e.label);
        }
    }

    private void openAppInfo(AppEntry e) {
        start(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", e.pkg, null)), e.label);
    }

    private void start(Intent i, String what) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException ex) {
            Toast.makeText(this, getString(R.string.cannot_open, what), Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshPills() {
        boolean onTop = zone == ZONE_TOP || (zone == ZONE_MENU && menuKind == MENU_INPUTS);
        for (int i = 0; i < pills.size(); i++) {
            boolean on = onTop && i == topSel;
            LinearLayout p = (LinearLayout) pills.get(i);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(g.h);
            if (on) {
                bg.setColor(0xF2FFFFFF);
            } else {
                bg.setColor(0x24FFFFFF);
                bg.setStroke(1, 0x33FFFFFF);
            }
            p.setBackground(bg);
            int fg = on ? 0xFF000000 : 0xE6FFFFFF;
            ((ImageView) p.getChildAt(0)).setColorFilter(fg);
            ((TextView) p.getChildAt(1)).setTextColor(fg);
            float s = on ? 1.06f : 1f;
            p.animate().scaleX(s).scaleY(s).setDuration(150).start();
        }
    }

    // ---------------------------------------------------------------- inputs

    private void loadInputs() {
        inputs.clear();
        TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return;
        for (TvInputInfo info : tim.getTvInputList()) {
            int t = info.getType();
            if (info.getParentId() != null) continue; // CEC devices behind an HDMI port
            if (t == TvInputInfo.TYPE_HDMI || t == TvInputInfo.TYPE_COMPOSITE || t == TvInputInfo.TYPE_TUNER) {
                inputs.add(info);
            }
        }
        inputs.sort((a, b) -> Integer.compare(order(a), order(b)));
    }

    private static int order(TvInputInfo i) {
        int t = i.getType();
        return t == TvInputInfo.TYPE_HDMI ? 0 : t == TvInputInfo.TYPE_COMPOSITE ? 1 : 2;
    }

    private String inputLabel(TvInputInfo i) {
        if (i.getType() == TvInputInfo.TYPE_TUNER) return getString(R.string.live_tv);
        CharSequence l = i.loadLabel(this);
        if (!TextUtils.isEmpty(l)) return l.toString();
        return i.getType() == TvInputInfo.TYPE_COMPOSITE ? getString(R.string.av) : "HDMI";
    }

    /** Inputs dropdown under the Inputs pill. */
    private void openMenu() {
        List<String> labels = new ArrayList<>();
        for (TvInputInfo i : inputs) labels.add(inputLabel(i));
        fillMenu(labels);
        int[] loc = new int[2];
        inputsPill.getLocationInWindow(loc);
        showMenu(MENU_INPUTS, loc[0], loc[1] + inputsPill.getHeight() + g.h * 0.012f);
    }

    private void fillMenu(List<String> labels) {
        menu.removeAllViews();
        for (String l : labels) {
            TextView t = text(fRegular, g.h * 0.024f, 0xF2FFFFFF);
            t.setText(l);
            t.setSingleLine();
            t.setGravity(Gravity.CENTER_VERTICAL);
            int p = Math.round(g.h * 0.016f);
            t.setPadding(p, 0, p, 0);
            menu.addView(t, new LinearLayout.LayoutParams(-1, Math.round(g.h * 0.052f)));
        }
    }

    private void showMenu(int kind, float x, float y) {
        menuKind = kind;
        menu.setTranslationX(x);
        menu.setTranslationY(y);
        menu.setVisibility(View.VISIBLE);
        menu.setAlpha(0f);
        menu.animate().alpha(1f).setDuration(150).start();
        zone = ZONE_MENU;
        if (kind == MENU_ACTIONS) moveHighlight(scrollY, true); // fade the glass while the menu is up
        setMenuSel(0);
    }

    private void setMenuSel(int s) {
        menuSel = s;
        for (int i = 0; i < menu.getChildCount(); i++) {
            View v = menu.getChildAt(i);
            if (i == s) {
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(0x33FFFFFF);
                bg.setCornerRadius(g.h * 0.014f);
                v.setBackground(bg);
            } else {
                v.setBackground(null);
            }
        }
    }

    private void closeMenu() {
        if (menu.getVisibility() != View.VISIBLE) return;
        menu.setVisibility(View.GONE);
        if (menuKind == MENU_ACTIONS) {
            zone = ZONE_TILES;
            select(sel, true);
        } else {
            zone = ZONE_TOP;
            refreshPills();
        }
    }

    private void switchInput(TvInputInfo info) {
        Uri uri = info.isPassthroughInput()
                ? TvContract.buildChannelUriForPassthroughInput(info.getId())
                : TvContract.buildChannelsUriForInput(info.getId());
        closeMenu();
        start(new Intent(Intent.ACTION_VIEW, uri), inputLabel(info));
    }

    // ---------------------------------------------------------------- USB drive

    /** Name of the first mounted removable volume, or null. */
    private void loadUsb() {
        usbLabel = null;
        StorageManager sm = getSystemService(StorageManager.class);
        for (StorageVolume v : sm.getStorageVolumes()) {
            if (v.isRemovable() && Environment.MEDIA_MOUNTED.equals(v.getState())) {
                String d = v.getDescription(this);
                usbLabel = TextUtils.isEmpty(d) ? getString(R.string.usb) : d;
                return;
            }
        }
    }

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            String before = usbLabel;
            loadUsb();
            if (TextUtils.equals(before, usbLabel)) return;
            View focused = zone == ZONE_TOP && topSel < pills.size() ? pills.get(topSel) : null;
            rebuildPillRow();
            // Keep focus on the same pill when one appears or disappears before it.
            int idx = pills.indexOf(focused);
            if (idx >= 0) topSel = idx;
            if (zone == ZONE_MENU && menuKind == MENU_INPUTS) closeMenu();
            refreshPills();
        }
    };

    private void registerUsbReceiver() {
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_MEDIA_MOUNTED);
        f.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        f.addAction(Intent.ACTION_MEDIA_REMOVED);
        f.addAction(Intent.ACTION_MEDIA_BAD_REMOVAL);
        f.addAction(Intent.ACTION_MEDIA_EJECT);
        f.addDataScheme("file");
        registerReceiver(usbReceiver, f);
    }

    private void openUsb() {
        String pkg = getString(R.string.usb_app);
        PackageManager pm = getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) {
            Toast.makeText(this, getString(R.string.cannot_open, usbLabel), Toast.LENGTH_SHORT).show();
            return;
        }
        start(i, usbLabel);
    }

    // ---------------------------------------------------------------- clock

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            updateClock();
        }
    };

    private void updateClock() {
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        greeting.setText(hour >= 5 && hour < 12 ? R.string.morning
                : hour >= 12 && hour < 17 ? R.string.afternoon : R.string.evening);
        clock.setText(DateFormat.format(DateFormat.is24HourFormat(this) ? "HH:mm" : "h:mm", now));
        Locale loc = Locale.getDefault();
        date.setText(DateFormat.format(DateFormat.getBestDateTimePattern(loc, "EEEEdMMMM"), now));
    }

    // ---------------------------------------------------------------- network name

    private void registerNetwork() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        netCallback = Build.VERSION.SDK_INT >= 31
                ? new ConnectivityManager.NetworkCallback(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO) {
                    @Override
                    public void onCapabilitiesChanged(@NonNull Network n, @NonNull NetworkCapabilities caps) {
                        showNetwork(caps);
                    }

                    @Override
                    public void onLost(@NonNull Network n) {
                        showNetwork(null);
                    }
                }
                : new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onCapabilitiesChanged(@NonNull Network n, @NonNull NetworkCapabilities caps) {
                        showNetwork(caps);
                    }

                    @Override
                    public void onLost(@NonNull Network n) {
                        showNetwork(null);
                    }
                };
        cm.registerDefaultNetworkCallback(netCallback, ui);
    }

    @SuppressWarnings("deprecation")
    private void showNetwork(NetworkCapabilities caps) {
        String name;
        if (caps == null) {
            name = getString(R.string.offline);
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            name = getString(R.string.ethernet);
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            WifiInfo wi = null;
            if (Build.VERSION.SDK_INT >= 29 && caps.getTransportInfo() instanceof WifiInfo) {
                wi = (WifiInfo) caps.getTransportInfo();
            }
            if (wi == null) wi = getApplicationContext().getSystemService(WifiManager.class).getConnectionInfo();
            String ssid = wi == null ? null : wi.getSSID();
            if (ssid != null && ssid.startsWith("\"") && ssid.endsWith("\"") && ssid.length() > 1) {
                ssid = ssid.substring(1, ssid.length() - 1);
            }
            name = TextUtils.isEmpty(ssid) || "<unknown ssid>".equals(ssid) ? getString(R.string.wifi) : ssid;
        } else {
            name = getString(R.string.wifi);
        }
        wifiText.setText(name);
    }
}
