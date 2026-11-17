package com.tracker.widgets;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/**
 * The Schedule widget's day, built from real views instead of a picture: hour lines, each event a block placed by its
 * time (tinted with its category's color, solid left edge, like the Tracker's grid), Aarian and Miranda side by side,
 * and the red line at the current time. Placing views by position needs Android 12; older phones get two plain lists.
 */
final class Grid {
    private Grid() {}

    static final int SURFACE = 0xFF181D24, MUTED = 0xFF9AA4B1, INK = 0xFFE9EDF2;

    static class Ev {
        int s, e, color, lane, lanes = 1;
        String title, place, cat;
        boolean both;
    }

    /** now is minutes since midnight, or -1 for a day other than today. Sizes are in dp. */
    static void build(Context c, RemoteViews v, JSONArray me, JSONArray mi, int now, float wDp, float hDp, float k) {
        v.removeAllViews(R.id.grid);
        List<Ev> a = parse(me), m = parse(mi);
        // The whole day: from 7 AM (or the first thing that isn't sleep, if earlier) to midnight.
        int start = 7 * 60, end = 24 * 60;
        for (List<Ev> l : java.util.Arrays.asList(a, m)) for (Ev e : l) if (!"sleep".equals(e.cat)) start = Math.min(start, e.s / 60 * 60);
        if (now >= 0 && now < start) start = Math.max(0, (now - 30) / 60 * 60);
        if (Build.VERSION.SDK_INT < 31) { lists(c, v, a, m, now, k); return; }
        String pkg = c.getPackageName();
        float gutter = 28, top = 6, bottom = hDp - 4, perMin = (bottom - top) / (end - start);
        float colGap = 5, colW = (wDp - gutter - colGap) / 2f;
        int step = perMin * 60 < 15 ? 120 : 60;
        for (int t = start; t <= end; t += 60) {
            RemoteViews hr = new RemoteViews(pkg, R.layout.item_hour);
            boolean label = (t - start) % step == 0 && t < end && (now < 0 || Math.abs(t - now) > 12);
            hr.setTextViewText(R.id.hour_t, label ? hour(t) : "");
            hr.setTextViewTextSize(R.id.hour_t, TypedValue.COMPLEX_UNIT_SP, 9 * Math.min(k, 1.2f));
            hr.setViewLayoutMargin(R.id.hour, RemoteViews.MARGIN_TOP, top + (t - start) * perMin - 6, TypedValue.COMPLEX_UNIT_DIP);
            v.addView(R.id.grid, hr);
        }
        column(c, v, a, gutter, colW, top, perMin, start, end, now, k);
        column(c, v, m, gutter + colW + colGap, colW, top, perMin, start, end, now, k);
        if (now >= start && now <= end) {
            RemoteViews nl = new RemoteViews(pkg, R.layout.item_nowline);
            nl.setTextViewText(R.id.now_t, Widgets.clock(now).replace(" AM", "").replace(" PM", ""));
            nl.setViewLayoutMargin(R.id.nowline, RemoteViews.MARGIN_TOP, top + (now - start) * perMin - 5, TypedValue.COMPLEX_UNIT_DIP);
            nl.setViewLayoutMargin(R.id.nowline, RemoteViews.MARGIN_LEFT, 4, TypedValue.COMPLEX_UNIT_DIP);
            v.addView(R.id.grid, nl);
        }
    }

    @androidx.annotation.RequiresApi(31)
    private static void column(Context c, RemoteViews v, List<Ev> evs, float x0, float colW, float top, float perMin, int start, int end, int now, float k) {
        lanes(evs);
        for (Ev e : evs) {
            int s = Math.max(e.s, start), en = Math.min(e.e, end);
            if (en <= s) continue;
            float laneW = colW / e.lanes;
            float x = x0 + e.lane * laneW + 1, y = top + (s - start) * perMin + 1, w = laneW - 2, h = Math.max(6, (en - s) * perMin - 2);
            boolean past = now >= 0 && e.e <= now, sleep = "sleep".equals(e.cat);
            RemoteViews b = new RemoteViews(c.getPackageName(), R.layout.item_block);
            b.setViewLayoutMargin(R.id.blk, RemoteViews.MARGIN_LEFT, x, TypedValue.COMPLEX_UNIT_DIP);
            b.setViewLayoutMargin(R.id.blk, RemoteViews.MARGIN_TOP, y, TypedValue.COMPLEX_UNIT_DIP);
            b.setViewLayoutWidth(R.id.blk, w, TypedValue.COMPLEX_UNIT_DIP);
            b.setViewLayoutHeight(R.id.blk, h, TypedValue.COMPLEX_UNIT_DIP);
            b.setInt(R.id.blk_bg, "setColorFilter", mix(e.color, sleep ? 0.14f : 0.26f));
            b.setInt(R.id.blk_bar, "setColorFilter", e.color);
            if (past) { b.setInt(R.id.blk_bg, "setImageAlpha", 150); b.setInt(R.id.blk_bar, "setImageAlpha", 130); }
            float tk = Math.min(k, 1.25f);
            b.setTextViewTextSize(R.id.blk_title, TypedValue.COMPLEX_UNIT_SP, 10.5f * tk);
            b.setTextViewTextSize(R.id.blk_time, TypedValue.COMPLEX_UNIT_SP, 9 * tk);
            b.setTextViewText(R.id.blk_title, (e.both ? "♥ " : "") + e.title);
            b.setTextColor(R.id.blk_title, sleep || past ? MUTED : INK);
            b.setInt(R.id.blk_title, "setMaxLines", h >= 40 * tk ? 2 : 1);
            if (h < 12 * tk) b.setViewVisibility(R.id.blk_title, View.GONE);
            if (h >= 27 * tk) b.setTextViewText(R.id.blk_time, Widgets.clock(e.s) + " – " + Widgets.clock(e.e) + (e.place.isEmpty() ? "" : " · " + e.place));
            else b.setViewVisibility(R.id.blk_time, View.GONE);
            v.addView(R.id.grid, b);
        }
    }

    /** Phones before Android 12: each person's day as a plain list, with the now line between past and upcoming. */
    private static void lists(Context c, RemoteViews v, List<Ev> a, List<Ev> m, int now, float k) {
        RemoteViews cols = new RemoteViews(c.getPackageName(), R.layout.item_cols);
        int[] ids = {R.id.col_me, R.id.col_mi};
        List<List<Ev>> both = java.util.Arrays.asList(a, m);
        for (int i = 0; i < 2; i++) {
            boolean lineDone = now < 0;
            for (Ev e : both.get(i)) {
                if (!lineDone && e.s > now) { cols.addView(ids[i], new RemoteViews(c.getPackageName(), R.layout.item_nowline)); lineDone = true; }
                RemoteViews r = new RemoteViews(c.getPackageName(), R.layout.item_event);
                boolean past = now >= 0 && e.e <= now;
                r.setInt(R.id.ev_bar, "setColorFilter", e.color);
                r.setInt(R.id.ev_bg, "setColorFilter", mix(e.color, 0.26f));
                r.setTextViewText(R.id.ev_time, Widgets.clock(e.s) + " – " + Widgets.clock(e.e));
                r.setTextViewText(R.id.ev_title, (e.both ? "♥ " : "") + e.title);
                r.setTextColor(R.id.ev_title, past ? MUTED : INK);
                cols.addView(ids[i], r);
            }
            if (!lineDone) cols.addView(ids[i], new RemoteViews(c.getPackageName(), R.layout.item_nowline));
        }
        v.addView(R.id.grid, cols);
    }

    /** Side by side when events overlap, like the Tracker's layout(). */
    private static void lanes(List<Ev> evs) {
        List<Ev> group = new ArrayList<>();
        int groupEnd = -1;
        for (Ev e : evs) {
            if (e.s >= groupEnd && !group.isEmpty()) { finish(group); group.clear(); }
            group.add(e);
            groupEnd = Math.max(groupEnd, e.e);
        }
        finish(group);
    }

    private static void finish(List<Ev> group) {
        List<Integer> ends = new ArrayList<>();
        for (Ev e : group) {
            int i = 0;
            while (i < ends.size() && ends.get(i) > e.s) i++;
            if (i == ends.size()) ends.add(e.e); else ends.set(i, e.e);
            e.lane = i;
        }
        for (Ev e : group) e.lanes = Math.max(1, ends.size());
    }

    static List<Ev> parse(JSONArray arr) {
        List<Ev> out = new ArrayList<>();
        for (int i = 0; arr != null && i < arr.length(); i++) {
            JSONArray r = arr.optJSONArray(i);
            if (r == null) continue;
            Ev e = new Ev();
            e.s = r.optInt(0);
            e.e = r.optInt(1);
            if (e.e <= e.s) e.e = Math.min(24 * 60, e.e + 24 * 60);   // runs past midnight
            e.title = r.optString(2);
            e.cat = r.optString(3);
            e.both = r.optInt(4) == 1;
            e.place = r.optString(5);
            String col = ListService.CAT.containsKey(e.cat) ? ListService.CAT.get(e.cat) : "#8B95A7";
            e.color = Color.parseColor(col);
            out.add(e);
        }
        out.sort((x, y) -> x.s != y.s ? Integer.compare(x.s, y.s) : Integer.compare(y.e, x.e));
        return out;
    }

    /** color-mix(in srgb, color p, surface): how the Tracker tints its event blocks. */
    static int mix(int c, float p) {
        return Color.rgb(Math.round(Color.red(c) * p + Color.red(SURFACE) * (1 - p)), Math.round(Color.green(c) * p + Color.green(SURFACE) * (1 - p)),
                Math.round(Color.blue(c) * p + Color.blue(SURFACE) * (1 - p)));
    }

    private static String hour(int t) {
        int h = (t / 60) % 24;
        return h == 0 ? "12a" : h == 12 ? "12p" : h < 12 ? h + "a" : (h - 12) + "p";
    }
}
