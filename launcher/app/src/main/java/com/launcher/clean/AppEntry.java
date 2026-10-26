package com.launcher.clean;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** One launchable TV app: its banner (or icon), label, launch intent and an accent colour. */
final class AppEntry {
    final String pkg;
    final String label;
    final Intent launch;
    final Drawable banner; // null when the app has no banner
    final Drawable icon;
    final int color;       // sampled from the banner, used for the glow above the dock

    private AppEntry(String pkg, String label, Intent launch, Drawable banner, Drawable icon, int color) {
        this.pkg = pkg;
        this.label = label;
        this.launch = launch;
        this.banner = banner;
        this.icon = icon;
        this.color = color;
    }

    /** Result of {@link #load}: dock apps in configured order, then the rest by label. */
    static final class Apps {
        final List<AppEntry> all = new ArrayList<>();
        int dockCount;
    }

    /** @param useDock false = no dock at all (dockCount 0); pass the dock apps at the front of order. */
    static Apps load(Context ctx, String[] dock, String[] order, String[] hidden, int columns, boolean useDock) {
        PackageManager pm = ctx.getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        Set<String> skip = new HashSet<>(Arrays.asList(hidden));
        skip.add(ctx.getPackageName());

        Map<String, AppEntry> byPkg = new LinkedHashMap<>();
        for (ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
            ActivityInfo ai = ri.activityInfo;
            if (skip.contains(ai.packageName) || byPkg.containsKey(ai.packageName)) continue;
            Intent launch = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                    .setComponent(new ComponentName(ai.packageName, ai.name))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            Drawable banner = ai.loadBanner(pm);
            Drawable icon = banner == null ? ri.loadIcon(pm) : null;
            String label = String.valueOf(ri.loadLabel(pm)).trim();
            byPkg.put(ai.packageName, new AppEntry(ai.packageName, label, launch, banner, icon,
                    sampleColor(banner != null ? banner : icon)));
        }

        Apps apps = new Apps();
        for (String p : dock) {
            AppEntry e = byPkg.remove(p);
            if (e != null && apps.all.size() < columns) apps.all.add(e);
        }
        int dockSize = apps.all.size();
        // Grid: app_order first, in that order; everything else alphabetically after it.
        for (String p : order) {
            AppEntry e = byPkg.remove(p);
            if (e != null) apps.all.add(e);
        }
        List<AppEntry> rest = new ArrayList<>(byPkg.values());
        Collator c = Collator.getInstance(Locale.getDefault());
        rest.sort((a, b) -> c.compare(a.label, b.label));
        apps.all.addAll(rest);
        // No configured dock app installed: the first row becomes the dock.
        apps.dockCount = dockSize;
        if (apps.dockCount == 0 && useDock) apps.dockCount = Math.min(columns, apps.all.size());
        return apps;
    }

    /** Hue-weighted average of the saturated, bright pixels; grey when there are none. */
    private static int sampleColor(Drawable d) {
        if (d == null) return 0xFF8E8E93;
        Drawable copy = d.getConstantState() != null ? d.getConstantState().newDrawable().mutate() : d;
        Bitmap b = Bitmap.createBitmap(16, 9, Bitmap.Config.ARGB_8888);
        copy.setBounds(0, 0, 16, 9);
        copy.draw(new Canvas(b));
        float[] hsv = new float[3];
        double x = 0, y = 0, sat = 0, weight = 0;
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 9; j++) {
                int px = b.getPixel(i, j);
                if (Color.alpha(px) < 128) continue;
                Color.colorToHSV(px, hsv);
                double w = hsv[1] * hsv[2];
                if (w < 0.08) continue;
                double rad = Math.toRadians(hsv[0]);
                x += Math.cos(rad) * w;
                y += Math.sin(rad) * w;
                sat += hsv[1] * w;
                weight += w;
            }
        }
        b.recycle();
        if (weight < 1.5) return 0xFF8E8E93;
        float hue = (float) ((Math.toDegrees(Math.atan2(y, x)) + 360) % 360);
        float s = (float) Math.max(0.45, Math.min(0.9, sat / weight));
        return Color.HSVToColor(new float[]{hue, s, 0.95f});
    }
}
