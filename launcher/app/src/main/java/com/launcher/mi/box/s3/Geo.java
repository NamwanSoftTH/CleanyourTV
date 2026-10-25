package com.launcher.mi.box.s3;

/**
 * Screen geometry in pixels. Everything is derived from the screen size and column count, so tile
 * positions can be computed without asking the RecyclerView (rows below the screen are not laid out).
 *
 * Content (scroll offset 0): the dock row sits inside a band at dock_position; grid rows follow
 * below it. HomeActivity scrolls only as far as needed to keep the focused row on screen.
 */
final class Geo {
    static final float SCALE = 1.10f;

    final int w, h, cols;
    final int gap, tileW, tileH, padL, padR;
    final int bandPad, dockItemH, dockTop;
    final int rowGap, dockGap, labelGap, labelH, gridItemH;
    final int radius, hlPad, hlW, hlH;

    /** @param dockPos dock height at scroll offset 0: 50 = centre of the screen, 100 = bottom. */
    Geo(int w, int h, int cols, int dockPos) {
        this.w = w;
        this.h = h;
        this.cols = cols;
        int side = Math.round(w * 0.055f);
        gap = Math.round(w * 0.014f);
        tileW = (w - 2 * side - (cols - 1) * gap) / cols;
        tileH = Math.round(tileW * 9f / 16f);
        // Each span is exactly tileW + gap wide, the tile sits gap/2 into it.
        padL = (w - cols * (tileW + gap)) / 2;
        padR = w - padL - cols * (tileW + gap);
        bandPad = Math.round(tileH * 0.22f);
        dockItemH = tileH + 2 * bandPad;
        // 0 = highest (room left above for the header and the big app name), 50 = centre, 100 = bottom.
        int bottom = h - Math.round(h * 0.065f) - dockItemH;
        int centre = (h - dockItemH) / 2;
        int top = Math.round(h * 0.30f);
        int pos = Math.max(0, Math.min(100, dockPos));
        dockTop = pos >= 50
                ? Math.round(centre + (bottom - centre) * (pos - 50) / 50f)
                : Math.round(centre - (centre - top) * (50 - pos) / 50f);
        // Grid rows: just room for the focused tile's grown edge (SCALE adds 5% each side) and
        // the one-line name under it.
        rowGap = Math.round(tileH * 0.08f);
        dockGap = Math.round(tileH * 0.16f); // extra space between the dock band and the first grid row
        labelGap = Math.round(tileH * 0.07f);
        labelH = Math.round(h * 0.036f);
        gridItemH = rowGap + tileH + labelGap + labelH;
        radius = Math.round(tileH * 0.11f);
        hlPad = Math.max(4, Math.round(tileH * 0.035f));
        hlW = Math.round(tileW * SCALE) + 2 * hlPad;
        hlH = Math.round(tileH * SCALE) + 2 * hlPad;
    }

    int tileX(int col) {
        return padL + gap / 2 + col * (tileW + gap);
    }

    /** Screen y of a tile's top edge in row {@code row} (0 = dock) at scroll offset {@code off}. */
    int tileY(int row, int off) {
        if (row == 0) return dockTop + bandPad - off;
        return rowTop(row) + rowGap - off;
    }

    /** Top of a row's cell in content coordinates (scroll offset 0). */
    int rowTop(int row) {
        if (gridOnlyTop >= 0) return gridOnlyTop + (row - 1) * gridItemH; // no dock: row 1 is the first
        return row == 0 ? dockTop : dockTop + dockItemH + dockGap + (row - 1) * gridItemH;
    }

    /** Set when dock_enabled is false: grid row 1 starts here (under the header), there is no row 0. */
    int gridOnlyTop = -1;
}
