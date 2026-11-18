package com.tracker.widgets;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One swipeable page per Tracker tab. The Tracker sends each page as rows (see its widget code, version 75+):
 * headings, bars, rows, big numbers, chips, food cards, School items with their status buttons, notes, and the
 * Schedule's day grid. Entries made on the phone that the Tracker hasn't applied yet are shown here too.
 */
final class Pages {
    private Pages() {}

    static int accent(String section) {
        switch (section) {
            case "health": return 0xFFEF6A6A;
            case "money": return 0xFF55B981;
            case "school": return 0xFF6B9CEC;
            default: return 0xFFE8BD34;
        }
    }

    /** Space for a page's rows, in dp: the widget's height minus its header, buttons and the page title. */
    static float budget(Context c, int wid, String section, float k) {
        int[] sz = Widgets.sizeDp(c, wid);
        float reserved = section.equals("health") ? 150 : section.equals("money") ? 105 : 60;
        return Math.max(110, sz[1] - reserved * k - 60 * k);
    }

    /** The tab a widget shows (saved per widget), 0 at first. */
    static int current(Context c, String section, int wid, int count) {
        int i = WidgetData.prefs(c).getInt("page_" + section + "_" + wid, 0);
        return count <= 0 ? 0 : ((i % count) + count) % count;
    }

    /** A tap that sends ActionReceiver these extras (each distinct tap gets its own PendingIntent). */
    static void click(Context c, RemoteViews v, int viewId, String... kv) {
        Intent i = new Intent(c, ActionReceiver.class);
        StringBuilder key = new StringBuilder(ActionReceiver.ACT);
        for (int n = 0; n + 1 < kv.length; n += 2) { i.putExtra(kv[n], kv[n + 1]); key.append('|').append(kv[n]).append('=').append(kv[n + 1]); }
        i.setAction(key.toString());
        v.setOnClickPendingIntent(viewId, android.app.PendingIntent.getBroadcast(c, key.toString().hashCode(), i,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE));
    }

    /** ‹ title ›, the dots, and tapping the page itself for the next tab. */
    private static void nav(Context c, RemoteViews v, String section, JSONArray pages, int pos, int wid, float k) {
        int count = pages.length(), acc = accent(section);
        String w = String.valueOf(wid), prev = String.valueOf((pos - 1 + count) % count), next = String.valueOf((pos + 1) % count);
        JSONObject p = pages.optJSONObject(pos), np = pages.optJSONObject((pos + 1) % count);
        v.setTextViewText(R.id.page_title, p != null ? p.optString("n") : "");
        v.setTextColor(R.id.page_title, acc);
        v.setTextViewTextSize(R.id.page_title, TypedValue.COMPLEX_UNIT_SP, 15 * k);
        v.setTextViewText(R.id.page_hint, (pos + 1) + " of " + count + (np != null && count > 1 ? "  ·  next: " + cap(np.optString("n")) : ""));
        click(c, v, R.id.page_prev, "op", "page", "section", section, "wid", w, "to", prev);
        click(c, v, R.id.page_next, "op", "page", "section", section, "wid", w, "to", next);
        click(c, v, R.id.page_head, "op", "page", "section", section, "wid", w, "to", next);
        click(c, v, R.id.page, "op", "page", "section", section, "wid", w, "to", next);
        v.removeAllViews(R.id.page_dots);
        for (int i = 0; i < count; i++) {
            RemoteViews d = new RemoteViews(c.getPackageName(), R.layout.item_dot);
            d.setInt(R.id.dot, "setColorFilter", i == pos ? acc : 0xFF2A313B);
            click(c, d, R.id.dotf, "op", "page", "section", section, "wid", w, "to", String.valueOf(i));
            v.addView(R.id.page_dots, d);
        }
    }

    private static String cap(String s) {
        String t = s.split(" · ")[0].toLowerCase(Locale.US);
        StringBuilder b = new StringBuilder();
        for (String x : t.split(" ")) if (!x.isEmpty()) b.append(b.length() > 0 ? " " : "").append(x.equals("&") ? "&" : Character.toUpperCase(x.charAt(0)) + x.substring(1));
        return b.toString();
    }

    /** The widget's current tab of this section, filling the widget. */
    static RemoteViews page(Context c, String section, JSONArray pages, int pos, int wid, float k) {
        JSONObject p = pages.optJSONObject(pos);
        if (p == null) p = new JSONObject();
        if ("today".equals(p.optString("t")) && section.equals("schedule")) return gridPage(c, section, pages, wid, pos, k);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.item_page);
        nav(c, v, section, pages, pos, wid, k);
        JSONArray rows = withPending(c, section, p.optString("t"), p.optJSONArray("r"));
        float left = budget(c, wid, section, k);
        int shown = 0, n = rows == null ? 0 : rows.length();
        v.removeAllViews(R.id.rows);
        for (int i = 0; i < n; i++) {
            JSONArray r = rows.optJSONArray(i);
            if (r == null) continue;
            float h = height(r) * k;
            if (h > left && shown > 0) {
                v.addView(R.id.rows, note(c, "+" + (n - i) + " more in the Tracker", k));
                break;
            }
            RemoteViews row = row(c, r, k);
            if (row == null) continue;
            v.addView(R.id.rows, row);
            left -= h;
            shown++;
        }
        if (n == 0) v.addView(R.id.rows, note(c, "Nothing here yet.", k));
        return v;
    }

    private static float height(JSONArray r) {
        switch (r.optString(0)) {
            case "h": return 24;
            case "bar": return 34;
            case "row": return r.optString(3).isEmpty() ? 34 : 46;
            case "big": return 58;
            case "food": return 56;
            case "chips": return 28;
            case "prog": return 84;
            default: return 20 + 14 * (r.optString(1).length() / 48);
        }
    }

    // ---------- rows ----------

    static RemoteViews row(Context c, JSONArray r, float k) {
        String pkg = c.getPackageName();
        switch (r.optString(0)) {
            case "h": {
                RemoteViews v = new RemoteViews(pkg, R.layout.row_h);
                v.setTextViewText(R.id.rh, r.optString(1).toUpperCase(Locale.US));
                v.setTextViewTextSize(R.id.rh, TypedValue.COMPLEX_UNIT_SP, 11.5f * k);
                return v;
            }
            case "note": return note(c, r.optString(1), k);
            case "chips": {
                RemoteViews v = new RemoteViews(pkg, R.layout.row_chips);
                SpannableStringBuilder b = new SpannableStringBuilder();
                JSONArray cs = r.optJSONArray(1);
                for (int i = 0; cs != null && i < cs.length(); i++) {
                    JSONArray ch = cs.optJSONArray(i);
                    if (ch == null) continue;
                    if (b.length() > 0) b.append("    ");
                    int s0 = b.length();
                    b.append(ch.optString(0));
                    b.setSpan(new ForegroundColorSpan(color(ch.optString(1), Widgets.INK)), s0, b.length(), 0);
                }
                v.setTextViewText(R.id.rc, b);
                v.setTextViewTextSize(R.id.rc, TypedValue.COMPLEX_UNIT_SP, 12 * k);
                return v;
            }
            case "big": {
                RemoteViews v = new RemoteViews(pkg, R.layout.row_big);
                v.setTextViewText(R.id.rbig_t, r.optString(1));
                v.setTextColor(R.id.rbig_t, color(r.optString(3), Widgets.INK));
                v.setTextViewText(R.id.rbig_s, r.optString(2));
                v.setTextViewTextSize(R.id.rbig_t, TypedValue.COMPLEX_UNIT_SP, 26 * k);
                v.setTextViewTextSize(R.id.rbig_s, TypedValue.COMPLEX_UNIT_SP, 11 * k);
                if (r.optString(2).isEmpty()) v.setViewVisibility(R.id.rbig_s, View.GONE);
                return v;
            }
            case "bar": {
                RemoteViews v = new RemoteViews(pkg, R.layout.row_bar);
                v.setTextViewText(R.id.rb_l, r.optString(1));
                String val = r.optString(2);
                SpannableStringBuilder b = new SpannableStringBuilder(val);
                int cut = val.indexOf(" / ");
                if (cut < 0) cut = val.indexOf(" · ");
                b.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, cut > 0 ? cut : val.length(), 0);
                if (cut > 0) b.setSpan(new ForegroundColorSpan(Widgets.MUTED), cut, val.length(), 0);
                v.setTextViewText(R.id.rb_v, b);
                int id = barId(r.optString(4));
                v.setViewVisibility(id, View.VISIBLE);
                v.setProgressBar(id, 100, Math.max(0, Math.min(100, r.optInt(3))), false);
                v.setTextViewTextSize(R.id.rb_l, TypedValue.COMPLEX_UNIT_SP, 12 * k);
                v.setTextViewTextSize(R.id.rb_v, TypedValue.COMPLEX_UNIT_SP, 11 * k);
                return v;
            }
            case "row": {
                RemoteViews v = new RemoteViews(pkg, R.layout.row_row);
                v.setTextViewText(R.id.rr_l, r.optString(1));
                v.setTextViewText(R.id.rr_r, r.optString(2));
                v.setTextColor(R.id.rr_r, color(r.optString(5), Widgets.INK));
                String sub = r.optString(3);
                v.setTextViewText(R.id.rr_s, sub);
                if (sub.isEmpty()) v.setViewVisibility(R.id.rr_s, View.GONE);
                v.setInt(R.id.rr_acc, "setColorFilter", color(r.optString(4), 0xFF2A313B));
                v.setTextViewTextSize(R.id.rr_l, TypedValue.COMPLEX_UNIT_SP, 12.5f * k);
                v.setTextViewTextSize(R.id.rr_r, TypedValue.COMPLEX_UNIT_SP, 13.5f * k);
                v.setTextViewTextSize(R.id.rr_s, TypedValue.COMPLEX_UNIT_SP, 10.5f * k);
                return v;
            }
            case "food": {
                JSONArray f = new JSONArray();
                for (int i = 1; i < r.length(); i++) f.put(r.opt(i));
                return Widgets.foodCard(c, f, k);
            }
            case "prog": return prog(c, r, k);
            default: return null;
        }
    }

    static RemoteViews note(Context c, String text, float k) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.row_note);
        v.setTextViewText(R.id.rn, text);
        v.setTextViewTextSize(R.id.rn, TypedValue.COMPLEX_UNIT_SP, 11 * k);
        return v;
    }

    private static int barId(String key) {
        switch (key) {
            case "g": return R.id.rb_g;
            case "o": return R.id.rb_o;
            case "w": return R.id.rb_w;
            case "r": return R.id.rb_r;
            case "p": return R.id.rb_p;
            case "y": return R.id.rb_y;
            default: return R.id.rb_b;
        }
    }

    /** "#RRGGBB", "cat:class" (a Schedule category) or "" (the fallback). */
    static int color(String s, int fallback) {
        if (s == null || s.isEmpty()) return fallback;
        if (s.startsWith("cat:")) { String h = ListService.CAT.get(s.substring(4)); return h != null ? Color.parseColor(h) : 0xFF8B95A7; }
        try { return Color.parseColor(s); } catch (Exception e) { return fallback; }
    }

    /** ["prog", id, name, class, due, "e"|"h", status]: a School item like a row on its Homework or Exams tab. */
    static RemoteViews prog(Context c, JSONArray r, float k) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.item_assign);
        String id = r.optString(1), due = r.optString(4), status = r.optString(6, "todo");
        String pend = Phone.pendingProg(c).get(id);
        if (pend != null) status = pend;
        boolean exam = "e".equals(r.optString(5));
        v.setTextViewText(R.id.name_text, r.optString(2));
        String cls = Widgets.shortClass(r.optString(3));
        v.setTextViewText(R.id.cls_text, cls + (due.isEmpty() ? "" : (cls.isEmpty() ? "" : " · ") + Widgets.dueLabel(due)) + (exam ? " · exam" : ""));
        if (due.isEmpty()) Widgets.pill(v, R.id.chip, R.id.chip_bg, R.id.chip_t, exam ? "Date TBA" : "No date", Widgets.SUNK, Widgets.MUTED);
        else {
            long ms;
            try { ms = LocalDateTime.parse(due).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - System.currentTimeMillis(); } catch (Exception e) { ms = Long.MAX_VALUE / 2; }
            if (ms <= 0 && exam) Widgets.pill(v, R.id.chip, R.id.chip_bg, R.id.chip_t, "Happening now", Widgets.chipBg(280), Widgets.chipFg(280));
            else if (ms <= 0) Widgets.pill(v, R.id.chip, R.id.chip_bg, R.id.chip_t, "Overdue " + Widgets.fmtSpan(ms), Widgets.OVERDUE_BG, 0xFFFFFFFF);
            else { int h = Widgets.bandHue(ms); Widgets.pill(v, R.id.chip, R.id.chip_bg, R.id.chip_t, Widgets.fmtSpan(ms), Widgets.chipBg(h), Widgets.chipFg(h)); }
        }
        Object[][] opts = exam ? ListService.EXAM : ListService.HW;
        for (int i = 0; i < ListService.PILLS4.length; i++) {
            int[] p = ListService.PILLS4[i];
            if (i >= opts.length) { v.setViewVisibility(p[0], View.GONE); continue; }
            String val = (String) opts[i][0];
            int h = (Integer) opts[i][2];
            boolean on = val.equals(status);
            Widgets.pill(v, p[0], p[1], p[2], (String) opts[i][1], on ? Widgets.chipBg(h) : Widgets.SUNK, on ? Widgets.chipFg(h) : Widgets.MUTED);
            click(c, v, p[0], "op", "prog", "id", id, "v", val);
            v.setTextViewTextSize(p[2], TypedValue.COMPLEX_UNIT_SP, 11 * k);
        }
        v.setTextViewTextSize(R.id.name_text, TypedValue.COMPLEX_UNIT_SP, 13 * k);
        v.setTextViewTextSize(R.id.cls_text, TypedValue.COMPLEX_UNIT_SP, 11 * k);
        v.setTextViewTextSize(R.id.chip_t, TypedValue.COMPLEX_UNIT_SP, 11 * k);
        return v;
    }

    // ---------- things added on the phone, shown before the Tracker applies them ----------

    private static JSONArray withPending(Context c, String section, String tab, JSONArray rows) {
        JSONArray out = new JSONArray();
        try {
            if (section.equals("health") && tab.equals("food")) {
                for (int i = 0; i < Phone.pendingOf(c, "photo").size(); i++) out.put(new JSONArray("[\"food\",\"⏳ Photo\",-1,\"\",-1,-1,-1,\"read when the Tracker opens\",\"pending\"]"));
                for (JSONObject e : Phone.pendingOf(c, "food"))
                    out.put(new JSONArray().put("food").put("⏳ " + e.optString("text")).put(-1).put(e.optString("hm")).put(-1).put(-1).put(-1).put("estimated when the Tracker opens").put("pending"));
            }
            if (section.equals("money") && tab.equals("entries"))
                for (JSONObject e : Phone.pendingOf(c, "money")) {
                    boolean in = "in".equals(e.optString("dir"));
                    String col = "move".equals(e.optString("dir")) ? "#9AA4B1" : in ? "#55B981" : "#F07070";
                    out.put(new JSONArray().put("row").put("⏳ " + e.optString("source")).put(Widgets.signed((in ? 1 : -1) * e.optDouble("amount", 0)))
                            .put(e.optString("type") + " · waiting for the Tracker").put(col).put(col));
                }
            double addW = section.equals("health") && tab.equals("today") || tab.equals("water") ? Phone.pendingWater(c) : 0;
            JSONObject h = WidgetData.w(c).optJSONObject("h");
            for (int i = 0; rows != null && i < rows.length(); i++) {
                JSONArray r = rows.optJSONArray(i);
                if (r != null && addW > 0 && h != null && "bar".equals(r.optString(0)) && "Water".equals(r.optString(1))) {
                    double oz = h.optDouble("water", 0) + addW, g = h.optDouble("waterG", 0);
                    r = new JSONArray().put("bar").put("Water").put(Widgets.fmt(oz) + (g > 0 ? " / " + Widgets.fmt(g) : "") + " oz ⏳")
                            .put(g > 0 ? (int) Math.min(100, Math.round(oz / g * 100)) : 0).put(oz >= g && g > 0 ? "g" : "w");
                }
                if (r != null) out.put(r);
            }
        } catch (Exception ignored) {}
        return out;
    }

    // ---------- the Schedule's Today page: ‹ day ›, now/next, the grid ----------

    static RemoteViews gridPage(Context c, String section, JSONArray pages, int wid, int pos, float k) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.item_grid_page);
        nav(c, v, section, pages, pos, wid, k);
        JSONObject w = WidgetData.w(c), s = w.optJSONObject("s");
        JSONArray days = s != null ? s.optJSONArray("days") : null;
        int n = 1 + (days == null ? 0 : days.length()), off = Math.min(n - 1, Widgets.dayOffset(c, wid));
        JSONObject day = off == 0 || days == null ? s : days.optJSONObject(off - 1);
        LocalDate today = LocalDate.now(), date;
        try { date = LocalDate.parse(day.optString("date")); } catch (Exception e) { date = today.plusDays(off); }
        String dn = off == 0 ? "TODAY" : off == 1 ? "TOMORROW" : date.format(DateTimeFormatter.ofPattern("EEEE", Locale.US)).toUpperCase(Locale.US);
        v.setTextViewText(R.id.day_title, dn + " · " + date.format(DateTimeFormatter.ofPattern("EEE M/d", Locale.US)).toUpperCase(Locale.US));
        v.setTextViewTextSize(R.id.day_title, TypedValue.COMPLEX_UNIT_SP, 14 * k);
        // On this page the grid is what you tap through days with, so a tap on the page doesn't change tabs.
        click(c, v, R.id.page, "op", "none");
        click(c, v, R.id.day_prev, "op", "day", "wid", String.valueOf(wid), "to", String.valueOf((off - 1 + n) % n));
        click(c, v, R.id.day_next, "op", "day", "wid", String.valueOf(wid), "to", String.valueOf((off + 1) % n));
        click(c, v, R.id.day_title, "op", "day", "wid", String.valueOf(wid), "to", "0");
        int nowMin = LocalTime.now().getHour() * 60 + LocalTime.now().getMinute();
        v.setViewVisibility(R.id.cards, off == 0 ? View.VISIBLE : View.GONE);
        if (off == 0 && s != null) {
            JSONArray study = s.optJSONArray("study");
            boolean studying = study != null && study.optInt(1) == 1;
            Widgets.nowNext(v, s.optJSONArray("me"), nowMin, R.id.now_me, R.id.next_me, "Miranda", studying ? s.optJSONArray("nowMe") : null);
            Widgets.nowNext(v, s.optJSONArray("mi"), nowMin, R.id.now_mi, R.id.next_mi, "Aarian", null);
        }
        int[] sz = Widgets.sizeDp(c, wid);
        float h = Math.max(120, sz[1] - 60 * k - 92 * k - (off == 0 ? 70 * k : 0) - 20);
        if (day != null) Grid.build(c, v, day.optJSONArray("me"), day.optJSONArray("mi"), off == 0 ? nowMin : -1, Math.max(150, sz[0] - 46), h, k);
        return v;
    }
}
