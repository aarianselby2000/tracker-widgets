package com.tracker.widgets;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/**
 * Today for Aarian and Miranda side by side, drawn like the Tracker's Schedule grid: hour lines, each event a block
 * tinted with its category's color and a solid left edge, shared plans outlined with a ♥, and the red line at the
 * current time across both columns. Widgets can't draw shapes, so this is one picture.
 */
final class Timeline {
    private Timeline() {}

    static final int SURFACE = 0xFF181D24, GRID = 0xFF222830, MUTED = 0xFF9AA4B1, INK = 0xFFE9EDF2, NOW = 0xFFFF5A5F, MIRANDA = 0xFFF07D9C;

    static class Ev {
        int s, e, color;
        String title, place, cat;
        boolean both;
        int lane, lanes = 1;
    }

    /** w and h in pixels; d is the screen density; now is minutes since midnight. */
    static Bitmap draw(JSONArray me, JSONArray mi, int now, int w, int h, float d, float k) {
        Bitmap b = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        List<Ev> a = parse(me), m = parse(mi);

        // The hours shown: from an hour before now to the end of the day's last event (at least 8 hours, at most 15).
        // A tall widget (about 34 dp per hour) shows more of the day: up to 18 hours.
        int hours = Math.max(8, Math.min(18, Math.round(h / d / (34 * k))));
        int start = Math.max(0, Math.min(now - 60, 24 * 60 - hours * 60) / 60 * 60);
        int last = start + hours * 60;
        for (List<Ev> l : java.util.Arrays.asList(a, m)) for (Ev e : l) if (e.s >= start - 30 || e.e > start) last = Math.max(last, Math.min(24 * 60, e.e));
        int end = Math.min(Math.min(24 * 60, start + Math.max(15, hours) * 60), (last + 59) / 60 * 60);
        if (end - start < 6 * 60) end = Math.min(24 * 60, start + 6 * 60);
        float gutter = 30 * d * k, top = 6 * d, bottom = h - 4 * d, perMin = (bottom - top) / (end - start);
        float colGap = 6 * d, colW = (w - gutter - colGap) / 2f;

        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        TextPaint txt = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        // hour lines and labels (every 2 hours when they're tight)
        int step = perMin * 60 < 16 * d ? 120 : 60;
        txt.setTextSize(9 * d * k);
        txt.setColor(MUTED);
        txt.setTextAlign(Paint.Align.RIGHT);
        for (int t = start; t <= end; t += 60) {
            float y = top + (t - start) * perMin;
            line.setColor(GRID);
            line.setStrokeWidth(1);
            cv.drawLine(gutter, y, w, y, line);
            if ((t - start) % step == 0 && t < end && Math.abs(t - now) > 14) cv.drawText(hour(t), gutter - 4 * d, y + 3.5f * d, txt);
        }
        // a faint divider between the two people
        line.setColor(0x332A313B);
        cv.drawLine(gutter + colW + colGap / 2, top, gutter + colW + colGap / 2, bottom, line);

        column(cv, a, gutter, colW, top, perMin, start, end, now, d * k, d);
        column(cv, m, gutter + colW + colGap, colW, top, perMin, start, end, now, d * k, d);

        // now: the Tracker's red line with a dot, across both columns, and the time in the hour column
        if (now >= start && now <= end) {
            float y = top + (now - start) * perMin;
            line.setColor(NOW);
            line.setStrokeWidth(2 * d);
            cv.drawLine(gutter, y, w, y, line);
            line.setStyle(Paint.Style.FILL);
            cv.drawCircle(gutter, y, 4.5f * d, line);
            txt.setColor(NOW);
            txt.setTypeface(Typeface.DEFAULT_BOLD);
            txt.setTextSize(8.5f * d * k);
            cv.drawText(Widgets.clock(now).replace(" AM", "").replace(" PM", ""), gutter - 7 * d, y + 3 * d, txt);
        }
        return b;
    }

    private static void column(Canvas cv, List<Ev> evs, float x0, float colW, float top, float perMin, int start, int end, int now, float d, float d0) {
        lanes(evs);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        TextPaint title = new TextPaint(Paint.ANTI_ALIAS_FLAG), meta = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(10.5f * d);
        meta.setTextSize(9 * d);
        for (Ev e : evs) {
            int s = Math.max(e.s, start), en = Math.min(e.e, end);
            if (en <= s) continue;
            float laneW = colW / e.lanes;
            float l = x0 + e.lane * laneW + 1 * d, r = x0 + (e.lane + 1) * laneW - 1 * d;
            float t = top + (s - start) * perMin + 1, btm = top + (en - start) * perMin - 1;
            if (btm - t < 4 * d) btm = t + 4 * d;
            boolean past = e.e <= now, sleep = "sleep".equals(e.cat);
            int alpha = past ? 140 : 255;
            RectF box = new RectF(l, t, r, btm);
            fill.setColor(mix(e.color, sleep ? 0.14f : 0.26f));
            fill.setAlpha(alpha);
            cv.drawRoundRect(box, 5 * d, 5 * d, fill);
            if (sleep) {   // the Tracker hatches sleep
                edge.setColor(e.color);
                edge.setAlpha(past ? 30 : 50);
                edge.setStrokeWidth(2 * d);
                cv.save();
                cv.clipRect(box);
                for (float k = l - (btm - t); k < r; k += 8 * d) cv.drawLine(k, btm, k + (btm - t), t, edge);
                cv.restore();
            }
            if (e.both) {
                edge.setStyle(Paint.Style.STROKE);
                edge.setStrokeWidth(1.5f * d);
                edge.setColor(e.color);
                edge.setAlpha(past ? 80 : 140);
                cv.drawRoundRect(box, 5 * d, 5 * d, edge);
                edge.setStyle(Paint.Style.FILL);
            }
            edge.setColor(e.color);
            edge.setAlpha(alpha);
            cv.save();
            cv.clipRect(l, t, l + 3 * d, btm);
            cv.drawRoundRect(box, 5 * d, 5 * d, edge);
            cv.restore();
            // title, then time and place when there's room
            float hgt = btm - t, pad = 5 * d, tx = l + 3 * d + pad, avail = r - tx - 2 * d;
            if (hgt < 9 * d || avail < 12 * d) continue;
            title.setColor(sleep ? MUTED : INK);
            title.setAlpha(past ? 150 : 255);
            String name = (e.both ? "♥ " : "") + e.title;
            CharSequence tt = TextUtils.ellipsize(name, title, avail, TextUtils.TruncateAt.END);
            float ty = t + Math.min(hgt / 2 + 4 * d, 12 * d);
            if (e.both && tt.length() > 1) {
                title.setColor(MIRANDA);
                cv.drawText("♥", tx, ty, title);
                float hw = title.measureText("♥ ");
                title.setColor(sleep ? MUTED : INK);
                title.setAlpha(past ? 150 : 255);
                cv.drawText(TextUtils.ellipsize(e.title, title, avail - hw, TextUtils.TruncateAt.END).toString(), tx + hw, ty, title);
            } else cv.drawText(tt.toString(), tx, ty, title);
            if (hgt >= 24 * d) {
                meta.setColor(MUTED);
                meta.setAlpha(past ? 150 : 255);
                String m = Widgets.clock(e.s) + " – " + Widgets.clock(e.e) + (e.place.isEmpty() ? "" : " · " + e.place);
                cv.drawText(TextUtils.ellipsize(m, meta, avail, TextUtils.TruncateAt.END).toString(), tx, ty + 11 * d, meta);
            }
        }
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
        List<Integer> laneEnds = new ArrayList<>();
        for (Ev e : group) {
            int k = 0;
            while (k < laneEnds.size() && laneEnds.get(k) > e.s) k++;
            if (k == laneEnds.size()) laneEnds.add(e.e); else laneEnds.set(k, e.e);
            e.lane = k;
        }
        for (Ev e : group) e.lanes = Math.max(1, laneEnds.size());
    }

    private static List<Ev> parse(JSONArray a) {
        List<Ev> out = new ArrayList<>();
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONArray r = a.optJSONArray(i);
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
        int r = Math.round(Color.red(c) * p + Color.red(SURFACE) * (1 - p));
        int g = Math.round(Color.green(c) * p + Color.green(SURFACE) * (1 - p));
        int b = Math.round(Color.blue(c) * p + Color.blue(SURFACE) * (1 - p));
        return Color.rgb(r, g, b);
    }

    private static String hour(int t) {
        int h = (t / 60) % 24;
        return h == 0 ? "12a" : h == 12 ? "12p" : h < 12 ? h + "a" : (h - 12) + "p";
    }
}
