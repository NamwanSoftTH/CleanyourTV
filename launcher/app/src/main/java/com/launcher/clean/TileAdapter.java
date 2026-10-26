package com.launcher.clean;

import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Dock tiles (positions below dockCount) and grid tiles with a name that shows
 * when focused.
 */
final class TileAdapter extends RecyclerView.Adapter<TileAdapter.VH> {
    interface Host {
        /**
         * Adapter position of the focused tile, or -1 when focus is not on the tiles.
         */
        int focusedTile();
    }

    private static final int DOCK = 0, GRID = 1;

    private final Geo g;
    private final Host host;
    private final Typeface labelFace, fallbackFace;
    private List<AppEntry> items = new ArrayList<>();
    private int dockCount;

    TileAdapter(Geo g, Host host, Typeface labelFace, Typeface fallbackFace) {
        this.g = g;
        this.host = host;
        this.labelFace = labelFace;
        this.fallbackFace = fallbackFace;
    }

    void setItems(List<AppEntry> items, int dockCount) {
        this.items = items;
        this.dockCount = dockCount;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public int getItemViewType(int position) {
        return position < dockCount ? DOCK : GRID;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        FrameLayout cell = new FrameLayout(parent.getContext());
        FrameLayout tile = new FrameLayout(parent.getContext());
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1C1C1E);
        bg.setCornerRadius(g.radius);
        tile.setBackground(bg);
        tile.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), g.radius);
            }
        });
        tile.setClipToOutline(true);

        ImageView banner = new ImageView(parent.getContext());
        banner.setScaleType(ImageView.ScaleType.CENTER_CROP);
        tile.addView(banner, new FrameLayout.LayoutParams(-1, -1));

        // Apps without a banner: icon with the name under it.
        int iconSize = Math.round(g.tileH * 0.46f);
        ImageView icon = new ImageView(parent.getContext());
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(iconSize, iconSize,
                Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        ilp.topMargin = Math.round(g.tileH * 0.13f);
        tile.addView(icon, ilp);
        TextView name = new TextView(parent.getContext());
        name.setTypeface(fallbackFace);
        name.setTextColor(0xE6FFFFFF);
        name.setTextSize(TypedValue.COMPLEX_UNIT_PX, g.tileH * 0.14f);
        name.setSingleLine();
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        nlp.bottomMargin = Math.round(g.tileH * 0.08f);
        nlp.leftMargin = nlp.rightMargin = Math.round(g.tileH * 0.08f);
        tile.addView(name, nlp);

        TextView label = null;
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(g.tileW, g.tileH, Gravity.START | Gravity.TOP);
        tlp.leftMargin = g.gap / 2;
        if (viewType == DOCK) {
            cell.setLayoutParams(new RecyclerView.LayoutParams(-1, g.dockItemH));
            tlp.topMargin = g.bandPad;
            cell.addView(tile, tlp);
        } else {
            cell.setLayoutParams(new RecyclerView.LayoutParams(-1, g.gridItemH));
            tlp.topMargin = g.rowGap;
            cell.addView(tile, tlp);
            label = new TextView(parent.getContext());
            label.setTypeface(labelFace);
            label.setTextColor(0xF2FFFFFF);
            label.setTextSize(TypedValue.COMPLEX_UNIT_PX, g.h * 0.024f);
            label.setSingleLine();
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setIncludeFontPadding(false); // fits the tight row; Thai marks still have room
            label.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
            label.setAlpha(0f);
            FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(g.tileW + g.gap, g.labelH,
                    Gravity.START | Gravity.TOP);
            llp.topMargin = g.rowGap + g.tileH + g.labelGap;
            cell.addView(label, llp);
        }
        return new VH(cell, tile, banner, icon, name, label);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        AppEntry e = items.get(position);
        if (e.banner != null) {
            h.banner.setImageDrawable(e.banner);
            h.banner.setVisibility(View.VISIBLE);
            h.icon.setVisibility(View.GONE);
            h.name.setVisibility(View.GONE);
        } else {
            h.banner.setVisibility(View.GONE);
            h.icon.setImageDrawable(e.icon);
            h.icon.setVisibility(View.VISIBLE);
            h.name.setText(e.label);
            h.name.setVisibility(View.VISIBLE);
        }
        if (h.label != null)
            h.label.setText(e.label);
        h.setFocused(position == host.focusedTile(), false);
    }

    static final class VH extends RecyclerView.ViewHolder {
        final FrameLayout tile;
        final ImageView banner, icon;
        final TextView name, label;

        VH(View cell, FrameLayout tile, ImageView banner, ImageView icon, TextView name, TextView label) {
            super(cell);
            this.tile = tile;
            this.banner = banner;
            this.icon = icon;
            this.name = name;
            this.label = label;
        }

        void setFocused(boolean on, boolean animate) {
            float s = on ? Geo.SCALE : 1f;
            tile.animate().cancel();
            if (animate) {
                tile.animate().scaleX(s).scaleY(s).setDuration(200).start();
            } else {
                tile.setScaleX(s);
                tile.setScaleY(s);
            }
            if (label != null) {
                label.animate().cancel();
                if (animate)
                    label.animate().alpha(on ? 1f : 0f).setDuration(200).start();
                else
                    label.setAlpha(on ? 1f : 0f);
            }
        }
    }
}
