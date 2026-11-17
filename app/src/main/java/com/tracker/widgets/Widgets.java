package com.tracker.widgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Builds each widget's view from the last downloaded data plus entries made on the phone that aren't applied yet. */
final class Widgets {
    private Widgets() {}

    static final String ACTION_REFRESH = "com.tracker.widgets.REFRESH";

    // Colors from the Tracker's dark theme.
    static final int INK = 0xFFE9EDF2, MUTED = 0xFF9AA4B1, SUNK = 0xFF2A313B, PRIMARY = 0xFFE6EAEF, ON_PRIMARY = 0xFF12161B;
    static final int OVERDUE_BG = 0xFF5C6662;

    /** Redraws every placed widget of every kind. */
    static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        Map<String, String> d = WidgetData.load(c);
        JSONObject w = WidgetData.w(c);
        for (int id : m.getAppWidgetIds(new ComponentName(c, FoodWidget.class))) {
            m.updateAppWidget(id, food(c, d, w, id));
        }
        for (int id : m.getAppWidgetIds(new ComponentName(c, MoneyWidget.class))) {
            m.updateAppWidget(id, money(c, d, w, id));
            m.notifyAppWidgetViewDataChanged(id, R.id.acct_stack);
        }
        for (int id : m.getAppWidgetIds(new ComponentName(c, ScheduleWidget.class))) {
            m.updateAppWidget(id, schedule(c, d, w, id));
        }
        for (int id : m.getAppWidgetIds(new ComponentName(c, DeadlinesWidget.class))) {
            m.updateAppWidget(id, school(c, d, id));
            m.notifyAppWidgetViewDataChanged(id, R.id.assign_list);
        }
        for (int id : m.getAppWidgetIds(new ComponentName(c, InboxWidget.class))) {
            m.updateAppWidget(id, inbox(c, d, id));
            m.notifyAppWidgetViewDataChanged(id, R.id.inbox_list);
        }
    }

    // ---------- Health ----------

    static RemoteViews food(Context c, Map<String, String> d, JSONObject w, int wid) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_food);
        common(c, v, d, "#health", FoodWidget.class);
        float k = scale(c, wid);
        title(v, k);
        JSONObject h = w.optJSONObject("h");
        // One screen with three pages: tap it (or a tab) to flip Today → Food log → This week.
        int page = Math.max(0, Math.min(2, WidgetData.prefs(c).getInt("health_page_" + wid, 0)));
        int[] tabs = {R.id.tab0, R.id.tab1, R.id.tab2}, pages = {R.id.page_today, R.id.page_log, R.id.page_week};
        for (int i = 0; i < 3; i++) {
            v.setViewVisibility(pages[i], i == page ? View.VISIBLE : View.GONE);
            v.setInt(tabs[i], "setBackgroundResource", i == page ? R.drawable.soft_health : R.drawable.soft_plain);
            v.setTextColor(tabs[i], i == page ? 0xFFEF6A6A : MUTED);
            ts(v, tabs[i], 12, k);
            v.setOnClickPendingIntent(tabs[i], flip(c, FoodWidget.class, wid, "page", i));
        }
        v.setOnClickPendingIntent(R.id.pages, flip(c, FoodWidget.class, wid, "page", (page + 1) % 3));
        int[] sz = sizeDp(c, wid);
        float pageH = Math.max(120, sz[1] - (24 + 34 + 90) * k - 30);   // minus header, tabs, buttons and padding
        if (page == 0) healthToday(c, v, d, h, k);
        else if (page == 1) healthLog(c, v, h, k, pageH);
        else healthWeek(c, v, h, k, pageH);
        boolean supps = "yes".equals(h != null ? h.optString("supps") : d.get("supplements"));
        Boolean ps = Phone.pendingSupps(c);
        if (ps != null) supps = ps;
        int bottle = h != null ? h.optInt("bottle", 40) : 40;
        v.setTextViewText(R.id.btn_w3, "+" + bottle + " oz");
        v.setOnClickPendingIntent(R.id.btn_w1, act(c, "water", "oz", "8"));
        v.setOnClickPendingIntent(R.id.btn_w2, act(c, "water", "oz", "16"));
        v.setOnClickPendingIntent(R.id.btn_w3, act(c, "water", "oz", String.valueOf(bottle)));
        v.setTextViewText(R.id.btn_supps, supps ? "✓ Supps" : "Supps");
        v.setTextColor(R.id.btn_supps, supps ? 0xFF55B981 : MUTED);
        v.setOnClickPendingIntent(R.id.btn_supps, act(c, "supps", "on", supps ? "false" : "true"));
        v.setOnClickPendingIntent(R.id.btn_food, entry(c, "food", null, false));
        v.setOnClickPendingIntent(R.id.btn_cam, entry(c, "food", null, true));
        for (int id : new int[]{R.id.btn_w1, R.id.btn_w2, R.id.btn_w3, R.id.btn_supps, R.id.btn_food}) ts(v, id, 12, k);
        return v;
    }

    /** Health page 1: the Tracker's food bars, what's left today, and caffeine. */
    private static void healthToday(Context c, RemoteViews v, Map<String, String> d, JSONObject h, float k) {
        Double kcal = h != null ? opt(h, "kcal") : num(d.get("kcal")), kcalT = h != null ? opt(h, "kcalT") : num(d.get("kcal_target"));
        Double p = h != null ? opt(h, "p") : num(d.get("protein")), pT = h != null ? opt(h, "pT") : num(d.get("protein_target"));
        Double water = h != null ? opt(h, "water") : num(d.get("water_oz")), waterG = h != null ? opt(h, "waterG") : num(d.get("water_goal_oz"));
        double addW = Phone.pendingWater(c);
        if (addW > 0) water = (water == null ? 0 : water) + addW;
        Double carbs = h != null ? opt(h, "c") : null, carbsT = h != null ? opt(h, "cT") : null;
        Double fat = h != null ? opt(h, "f") : null, fatT = h != null ? opt(h, "fT") : null;
        macro(v, R.id.bar_k_b, R.id.bar_k_g, R.id.bar_k_o, R.id.val_k, kcal, kcalT, "kcal", "kcal");
        macro(v, R.id.bar_p_b, R.id.bar_p_g, R.id.bar_p_o, R.id.val_p, p, pT, "g", "p");
        macro(v, R.id.bar_c_b, R.id.bar_c_g, R.id.bar_c_o, R.id.val_c, carbs, carbsT, "g", "c");
        macro(v, R.id.bar_f_b, R.id.bar_f_g, R.id.bar_f_o, R.id.val_f, fat, fatT, "g", "f");
        v.setProgressBar(R.id.bar_w_b, 100, water != null && waterG != null && waterG > 0 ? (int) Math.round(Math.min(1, water / waterG) * 100) : 0, false);
        v.setTextViewText(R.id.val_w, amount(water, waterG, "oz"));
        String left = "";
        if (kcal != null && kcalT != null && kcalT > 0)
            left = kcal < kcalT ? fmt(kcalT - kcal) + " kcal and " + fmt(Math.max(0, (pT == null ? 0 : pT) - (p == null ? 0 : p))) + " g protein left" : "Calorie target reached";
        v.setTextViewText(R.id.left_text, left);
        Double caf = h != null ? opt(h, "caf") : num(d.get("caffeine_mg"));
        v.setTextViewText(R.id.caf_text, "☕ " + (caf == null ? "–" : fmt(caf)) + " / 400 mg caffeine");
        for (int id : new int[]{R.id.lbl_k, R.id.lbl_p, R.id.lbl_c, R.id.lbl_f, R.id.lbl_w}) ts(v, id, 12.5f, k);
        for (int id : new int[]{R.id.val_k, R.id.val_p, R.id.val_c, R.id.val_f, R.id.val_w}) ts(v, id, 11.5f, k);
        ts(v, R.id.left_text, 11, k);
        ts(v, R.id.caf_text, 11, k);
    }

    /** Health page 2: today's foods, newest first, with ones added on the phone that aren't estimated yet. */
    private static void healthLog(Context c, RemoteViews v, JSONObject h, float k, float pageH) {
        v.removeAllViews(R.id.log_rows);
        List<CharSequence> lines = new java.util.ArrayList<>();
        for (int i = 0; i < Phone.pendingOf(c, "photo").size(); i++) lines.add(logLine("", "⏳ Photo, read when the Tracker opens", -1, -1));
        for (JSONObject e : Phone.pendingOf(c, "food")) lines.add(logLine(e.optString("hm"), "⏳ " + e.optString("text"), -1, -1));
        JSONArray f = h != null ? h.optJSONArray("foods") : null;
        int total = 0;
        for (int i = f == null ? -1 : f.length() - 1; i >= 0; i--) {
            JSONArray x = f.optJSONArray(i);
            if (x == null) continue;
            lines.add(logLine(x.optString(2), x.optString(0), x.optInt(1, -1), x.optInt(3, -1)));
            total += Math.max(0, x.optInt(1, 0));
        }
        int fit = Math.max(3, (int) ((pageH - 24) / (21 * k)));
        if (lines.isEmpty()) lines.add("Nothing logged today yet.");
        for (int i = 0; i < Math.min(fit, lines.size()); i++) {
            RemoteViews r = new RemoteViews(c.getPackageName(), R.layout.item_log_row);
            r.setTextViewText(R.id.log_line, lines.get(i));
            r.setTextViewTextSize(R.id.log_line, android.util.TypedValue.COMPLEX_UNIT_SP, 12.5f * k);
            v.addView(R.id.log_rows, r);
        }
        int n = f == null ? 0 : f.length();
        v.setTextViewText(R.id.log_total, (n == 0 ? "" : n + (n == 1 ? " food" : " foods") + " · " + fmt(total) + " kcal") + (lines.size() > fit ? "  ·  " + (lines.size() - fit) + " more in the Tracker" : ""));
        ts(v, R.id.log_total, 11, k);
    }

    /** "8:05a  2 eggs and toast  320 · 18 g P" */
    static CharSequence logLine(String at, String name, int kcal, int p) {
        SpannableStringBuilder b = new SpannableStringBuilder();
        String t = "";
        if (at != null && at.matches("\\d{1,2}:\\d{2}")) { String[] q = at.split(":"); t = clock(Integer.parseInt(q[0]) * 60 + Integer.parseInt(q[1])).replace(" AM", "a").replace(" PM", "p"); }
        if (!t.isEmpty()) { b.append(t).append("  "); b.setSpan(new ForegroundColorSpan(MUTED), 0, b.length(), 0); }
        b.append(name);
        if (kcal >= 0) {
            b.append("  ");
            int s1 = b.length();
            b.append(fmt(kcal));
            b.setSpan(new ForegroundColorSpan(0xFFF0A040), s1, b.length(), 0);
            b.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), s1, b.length(), 0);
        }
        if (p > 0) {
            int s2 = b.length();
            b.append(" · ").append(String.valueOf(p)).append(" g P");
            b.setSpan(new ForegroundColorSpan(0xFF6B9CEC), s2, b.length(), 0);
        }
        return b;
    }

    /** Health page 3: the last 7 days of calories, protein and water against each day's target (dashed line). */
    private static void healthWeek(Context c, RemoteViews v, JSONObject h, float k, float pageH) {
        JSONArray week = h != null ? h.optJSONArray("week") : null;
        int n = week == null ? 0 : week.length();
        int[] dayIds = {R.id.wd0, R.id.wd1, R.id.wd2, R.id.wd3, R.id.wd4, R.id.wd5, R.id.wd6};
        for (int i = 0; i < 7; i++) {
            String lab = "";
            JSONArray x = i < n ? week.optJSONArray(i) : null;
            if (x != null) try { lab = i == n - 1 ? "Today" : LocalDate.parse(x.optString(0)).getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, Locale.US); } catch (Exception ignored) {}
            v.setTextViewText(dayIds[i], lab);
            v.setTextColor(dayIds[i], i == n - 1 ? INK : MUTED);
            ts(v, dayIds[i], 10, k);
        }
        if (android.os.Build.VERSION.SDK_INT < 31) {   // bars need Android 12; before that, the numbers
            StringBuilder t = new StringBuilder();
            for (int i = 0; i < n; i++) { JSONArray x = week.optJSONArray(i); if (x != null) t.append(x.optString(0).substring(5)).append("  ").append(x.optInt(1)).append(" kcal · ").append(x.optInt(3)).append(" g P · ").append(x.optInt(5)).append(" oz\n"); }
            v.setTextViewText(R.id.week_text, t.toString().trim());
            v.setViewVisibility(R.id.week_text, View.VISIBLE);
            for (int id : new int[]{R.id.wk_k, R.id.wk_p, R.id.wk_w, R.id.wl_k, R.id.wl_p, R.id.wl_w}) v.setViewVisibility(id, View.GONE);
            return;
        }
        float area = Math.max(24, (pageH - 20 * k - 3 * 18 * k) / 3f - 12);
        int[] rows = {R.id.wk_k, R.id.wk_p, R.id.wk_w}, labels = {R.id.wl_k, R.id.wl_p, R.id.wl_w};
        int[][] idx = {{1, 2}, {3, 4}, {5, 6}};
        for (int r = 0; r < 3; r++) {
            v.removeAllViews(rows[r]);
            ts(v, labels[r], 11, k);
            for (int i = 0; i < 7; i++) {
                JSONArray x = i < n ? week.optJSONArray(i) : null;
                double val = x != null ? x.optDouble(idx[r][0], 0) : 0, t = x != null ? x.optDouble(idx[r][1], 0) : 0, fr = t > 0 ? val / t : 0;
                int color = r == 2 ? (fr >= 1 ? 0xFF55B981 : 0xFF5BB3F0) : r == 0 ? (fr * 100 > 105 ? 0xFFF0A040 : fr * 100 >= 90 ? 0xFF55B981 : 0xFF6B9CEC) : (fr >= 1 ? 0xFF55B981 : 0xFF6B9CEC);
                v.addView(rows[r], WeekBars.col(c, val, t, area, color, k));
            }
        }
    }

    // ---------- Money ----------

    static final List<String> SPEND = Arrays.asList("Food", "Supplies", "Gifts", "Fun", "Bill");

    static RemoteViews money(Context c, Map<String, String> d, JSONObject w, int wid) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_money);
        common(c, v, d, "#money", MoneyWidget.class);
        float k = scale(c, wid);
        title(v, k);
        ts(v, R.id.spent_text, 12, k);
        for (int id : new int[]{R.id.tab_0, R.id.tab_1, R.id.tab_2}) ts(v, id, 12, k);
        for (int id : new int[]{R.id.btn_out, R.id.btn_in, R.id.btn_move}) ts(v, id, 12, k);
        JSONObject m = w.optJSONObject("m");
        Double spent = m != null ? opt(m, "spent") : num(d.get("month_spent"));
        for (JSONObject e : Phone.pendingOf(c, "money"))
            if ("out".equals(e.optString("dir")) && SPEND.contains(e.optString("type"))) spent = (spent == null ? 0 : spent) + e.optDouble("amount", 0);
        v.setTextViewText(R.id.spent_text, spent == null ? "" : "Spent this month  " + dollars(spent));
        // Checking, Savings and Cash as a stack of cards: swipe through them, or tap a tab to jump to one.
        Intent svc = new Intent(c, ListService.class).putExtra("kind", "acct").putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, wid);
        svc.setData(Uri.parse("tracker://list/acct/" + wid));
        v.setRemoteAdapter(R.id.acct_stack, svc);
        v.setEmptyView(R.id.acct_stack, R.id.acct_empty);
        Intent open = new Intent(c, OpenActivity.class).putExtra("section", "#money");
        v.setPendingIntentTemplate(R.id.acct_stack, PendingIntent.getActivity(c, 7100, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE));
        int[] tabs = {R.id.tab_0, R.id.tab_1, R.id.tab_2};
        for (int i = 0; i < tabs.length; i++) {
            Intent t = new Intent(c, MoneyWidget.class).setAction(BaseWidget.ACTION_SHOW + "." + wid + "." + i)
                    .putExtra("idx", i).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, wid);
            v.setOnClickPendingIntent(tabs[i], PendingIntent.getBroadcast(c, 7200 + wid * 4 + i, t, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }
        v.setDisplayedChild(R.id.acct_stack, WidgetData.prefs(c).getInt("money_acct_" + wid, 0));
        v.setOnClickPendingIntent(R.id.btn_out, entry(c, "money", "out", false));
        v.setOnClickPendingIntent(R.id.btn_in, entry(c, "money", "in", false));
        v.setOnClickPendingIntent(R.id.btn_move, entry(c, "money", "move", false));
        v.setOnClickPendingIntent(R.id.btn_cam, entry(c, "money", "out", true));
        return v;
    }

    // ---------- Schedule: Aarian and Miranda side by side ----------

    static RemoteViews schedule(Context c, Map<String, String> d, JSONObject w, int wid) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_schedule);
        common(c, v, d, "#schedule", ScheduleWidget.class);
        float k = scale(c, wid);
        title(v, k);
        ts(v, R.id.name_me, 13, k); ts(v, R.id.name_mi, 13, k);
        ts(v, R.id.now_me, 12, k); ts(v, R.id.now_mi, 12, k);
        ts(v, R.id.next_me, 10, k); ts(v, R.id.next_mi, 10, k);
        ts(v, R.id.study_text, 11, k);
        LocalDate today = LocalDate.now();
        v.setTextViewText(R.id.title_text, "SCHEDULE");
        JSONObject s = w.optJSONObject("s");
        String note = "";
        if (s == null) note = "Open the Tracker once (version 67 or later) so your day fills in.";
        else if (!today.toString().equals(s.optString("date"))) note = "This is " + dayName(s.optString("date")) + "'s schedule. Open the Tracker to update it.";
        v.setTextViewText(R.id.note_text, note);
        v.setViewVisibility(R.id.note_text, note.isEmpty() ? View.GONE : View.VISIBLE);
        int nowMin = LocalTime.now().getHour() * 60 + LocalTime.now().getMinute();
        JSONArray study = s != null ? s.optJSONArray("study") : null;
        boolean studying = study != null && study.optInt(1) == 1;
        nowNext(v, s != null ? s.optJSONArray("me") : null, nowMin, R.id.now_me, R.id.next_me, "Miranda", studying ? s.optJSONArray("nowMe") : null);
        nowNext(v, s != null ? s.optJSONArray("mi") : null, nowMin, R.id.now_mi, R.id.next_mi, "Aarian", null);
        // The chosen day (today unless ‹ › were tapped), built as real blocks on a time grid.
        JSONArray days = s != null ? s.optJSONArray("days") : null;
        int count = 1 + (days == null ? 0 : days.length()), off = Math.min(count - 1, dayOffset(c, wid));
        JSONObject day = off == 0 || days == null ? s : days.optJSONObject(off - 1);
        LocalDate date;
        try { date = LocalDate.parse(day.optString("date")); } catch (Exception e) { date = today.plusDays(off); }
        String dn = off == 0 ? "TODAY" : off == 1 ? "TOMORROW" : date.format(DateTimeFormatter.ofPattern("EEEE", Locale.US)).toUpperCase(Locale.US);
        v.setTextViewText(R.id.day_title, dn + " · " + date.format(DateTimeFormatter.ofPattern("EEE M/d", Locale.US)).toUpperCase(Locale.US));
        ts(v, R.id.day_title, 15, k);
        v.setTextColor(R.id.day_prev, off > 0 ? INK : 0xFF4A525E);
        v.setTextColor(R.id.day_next, off < count - 1 ? INK : 0xFF4A525E);
        v.setOnClickPendingIntent(R.id.day_prev, flip(c, ScheduleWidget.class, wid, "day", Math.max(0, off - 1)));
        v.setOnClickPendingIntent(R.id.day_next, flip(c, ScheduleWidget.class, wid, "day", Math.min(count - 1, off + 1)));
        v.setOnClickPendingIntent(R.id.day_title, flip(c, ScheduleWidget.class, wid, "day", 0));
        v.setViewVisibility(R.id.cards, off == 0 ? View.VISIBLE : View.GONE);
        v.setViewVisibility(R.id.names, off == 0 ? View.GONE : View.VISIBLE);
        int[] sz = sizeDp(c, wid);
        float reserved = (24 + 32 + 20) * k + (off == 0 ? 74 * k : 22 * k) + 44 + (note.isEmpty() ? 0 : 14);
        if (day != null) Grid.build(c, v, day.optJSONArray("me"), day.optJSONArray("mi"), off == 0 ? nowMin : -1,
                Math.max(150, sz[0] - 26), Math.max(120, sz[1] - reserved), k);
        if (off != 0) { v.setTextViewText(R.id.study_text, ""); return v; }
        String st = study == null ? "" : "Studied today: " + hm(study.optInt(0)) + (studying ? " · studying now" : "");
        v.setTextViewText(R.id.study_text, st);
        return v;
    }

    private static void nowNext(RemoteViews v, JSONArray evs, int now, int nowId, int nextId, String other, JSONArray studyNow) {
        JSONArray cur = null, nxt = null;
        int curEnd = 0;
        for (int i = 0; evs != null && i < evs.length(); i++) {
            JSONArray e = evs.optJSONArray(i);
            if (e == null || "sleep".equals(e.optString(3))) continue;
            int s = e.optInt(0), en = e.optInt(1);
            if (en <= s) en += 1440;
            if (s <= now && now < en) { cur = e; curEnd = en; }
            else if (s > now && nxt == null) nxt = e;
        }
        String with = " with " + other;
        if (studyNow != null) v.setTextViewText(nowId, studyNow.optString(0) + (studyNow.optString(1).isEmpty() ? "" : " · " + studyNow.optString(1)));
        else if (cur != null) v.setTextViewText(nowId, cur.optString(2) + (cur.optInt(4) == 1 ? with : "") + " · until " + clock(curEnd));
        else v.setTextViewText(nowId, evs == null ? "–" : "Free now");
        v.setTextViewText(nextId, nxt == null ? (evs == null ? "" : "Nothing else today") : "Next: " + nxt.optString(2) + (nxt.optInt(4) == 1 ? with : "") + " · " + clock(nxt.optInt(0)));
    }

    // ---------- School ----------

    static RemoteViews school(Context c, Map<String, String> d, int wid) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_deadlines);
        common(c, v, d, "#school", DeadlinesWidget.class);
        title(v, scale(c, wid));
        list(c, v, wid, R.id.assign_list, R.id.assign_empty, "assign");
        return v;
    }

    // ---------- Inbox (email and texts) ----------

    static RemoteViews inbox(Context c, Map<String, String> d, int wid) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_inbox);
        common(c, v, d, "#inbox", InboxWidget.class);
        float k = scale(c, wid);
        title(v, k);
        ts(v, R.id.btn_quick, 12, k);
        list(c, v, wid, R.id.inbox_list, R.id.inbox_empty, "inbox");
        v.setOnClickPendingIntent(R.id.btn_quick, entry(c, "quick", null, false));
        return v;
    }

    // ---------- shared parts ----------

    /** This widget's size in dp: its narrowest width and tallest height (what it has in portrait). */
    static int[] sizeDp(Context c, int wid) {
        android.os.Bundle o = AppWidgetManager.getInstance(c).getAppWidgetOptions(wid);
        int w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0), h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
        return new int[]{w > 0 ? w : 300, h > 0 ? h : 260};
    }

    /** How much larger to draw than the standard 4×3 size: 1 normally, up to 1.35 when a widget gets the whole screen. */
    static float scale(Context c, int wid) {
        int[] s = sizeDp(c, wid);
        float k = Math.min(s[1] / 280f, s[0] / 290f);
        return Math.max(1f, Math.min(1.35f, k));
    }

    static void ts(RemoteViews v, int id, float sp, float k) {
        v.setTextViewTextSize(id, android.util.TypedValue.COMPLEX_UNIT_SP, sp * k);
    }

    private static void title(RemoteViews v, float k) {
        ts(v, R.id.title_text, 15, k);
        ts(v, R.id.updated_text, 10, Math.min(k, 1.15f));
    }

    /** Which day the Schedule widget shows: 0 today, 1 tomorrow… (back to today the next day). */
    static int dayOffset(Context c, int wid) {
        android.content.SharedPreferences p = WidgetData.prefs(c);
        return LocalDate.now().toString().equals(p.getString("sched_day_on_" + wid, "")) ? p.getInt("sched_day_" + wid, 0) : 0;
    }

    /** A tap that flips a widget to a page (Health) or a day (Schedule). */
    private static PendingIntent flip(Context c, Class<?> provider, int wid, String what, int to) {
        Intent i = new Intent(c, provider).setAction(BaseWidget.ACTION_FLIP + "." + what + "." + wid + "." + to)
                .putExtra("what", what).putExtra("to", to).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, wid);
        return PendingIntent.getBroadcast(c, (what + wid + "." + to).hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** A stack of pages filled by ListService; tapping a page opens that section of the Tracker. */
    private static void stack(Context c, RemoteViews v, int wid, int stackId, int emptyId, String kind, String section) {
        Intent svc = new Intent(c, ListService.class).putExtra("kind", kind).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, wid);
        svc.setData(Uri.parse("tracker://list/" + kind + "/" + wid));
        v.setRemoteAdapter(stackId, svc);
        v.setEmptyView(stackId, emptyId);
        Intent open = new Intent(c, OpenActivity.class).putExtra("section", section);
        v.setPendingIntentTemplate(stackId, PendingIntent.getActivity(c, 7400 + kind.hashCode() % 100, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE));
    }

    /** A scrolling list filled by ListService; its buttons all go to ActionReceiver. */
    private static void list(Context c, RemoteViews v, int wid, int listId, int emptyId, String kind) {
        Intent svc = new Intent(c, ListService.class).putExtra("kind", kind).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, wid);
        svc.setData(Uri.parse("tracker://list/" + kind + "/" + wid));
        v.setRemoteAdapter(listId, svc);
        v.setEmptyView(listId, emptyId);
        Intent t = new Intent(c, ActionReceiver.class).setAction(ActionReceiver.ACT_LIST);
        v.setPendingIntentTemplate(listId, PendingIntent.getBroadcast(c, 7000 + kind.hashCode() % 1000, t,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE));
    }

    /** The "as of" line (plus what's waiting), the refresh button, and tapping the widget to open the Tracker. */
    private static void common(Context c, RemoteViews v, Map<String, String> d, String section, Class<?> provider) {
        String wait = Phone.waitingNote(c), err = WidgetData.prefs(c).getString("send_error", "");
        String line = asOf(d.get("updated"), WidgetData.fetchedAt(c));
        if (!err.isEmpty() && Phone.unsent(c) > 0) line = err;
        else if (!wait.isEmpty()) line = wait + " · " + line;
        v.setTextViewText(R.id.updated_text, line);
        Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(WidgetData.TRACKER_URL + section));
        v.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(c, provider.hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        Intent refresh = new Intent(c, provider).setAction(ACTION_REFRESH);
        v.setOnClickPendingIntent(R.id.refresh, PendingIntent.getBroadcast(c, provider.hashCode() + 1, refresh,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
    }

    /** A button that sends one entry (water, supplements). */
    private static PendingIntent act(Context c, String op, String key, String val) {
        Intent i = new Intent(c, ActionReceiver.class).setAction(ActionReceiver.ACT + "." + op + "." + val)
                .putExtra("op", op).putExtra(key, val);
        return PendingIntent.getBroadcast(c, (op + key + val).hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** A button that opens a form (add food, a money entry, a quick add). */
    private static PendingIntent entry(Context c, String mode, String dir, boolean camera) {
        Intent i = new Intent(c, EntryActivity.class).putExtra("mode", mode).putExtra("dir", dir).putExtra("camera", camera)
                .setData(Uri.parse("tracker://entry/" + mode + "/" + dir + "/" + camera))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return PendingIntent.getActivity(c, (mode + dir + camera).hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** One food bar: which color shows (blue, green or orange, like the Tracker's macrosHTML) and "1,820 / 3,330 kcal". */
    static void macro(RemoteViews v, int blue, int green, int orange, int val, Double a, Double t, String unit, String kind) {
        double pct = a != null && t != null && t > 0 ? a / t * 100 : 0;
        String cls = t == null || t <= 0 ? "" : kind.equals("kcal") ? (pct > 105 ? "over" : pct >= 90 ? "ok" : "") : kind.equals("p") ? (pct >= 100 ? "ok" : "") : (pct > 115 ? "over" : pct >= 90 ? "ok" : "");
        int show = cls.equals("ok") ? green : cls.equals("over") ? orange : blue;
        for (int id : new int[]{blue, green, orange}) v.setViewVisibility(id, id == show ? View.VISIBLE : View.GONE);
        v.setProgressBar(show, 100, (int) Math.round(Math.min(100, pct)), false);
        v.setTextViewText(val, amount(a, t, unit));
    }

    /** "1,820 / 3,330 kcal" with the amount in bold and the target in gray. */
    static CharSequence amount(Double a, Double t, String unit) {
        SpannableStringBuilder b = new SpannableStringBuilder();
        b.append(a == null ? "–" : fmt(a));
        b.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, b.length(), 0);
        int s = b.length();
        b.append(t != null && t > 0 ? " / " + fmt(t) + " " + unit : " " + unit);
        b.setSpan(new ForegroundColorSpan(MUTED), s, b.length(), 0);
        return b;
    }

    /** One line of Money's recent list: name, the amount in red (out) or green (in), then the details in gray. */
    private static void line(SpannableStringBuilder b, String name, Double amt, String rest) {
        if (b.length() > 0) b.append('\n');
        int s0 = b.length();
        b.append(name);
        b.setSpan(new ForegroundColorSpan(INK), s0, b.length(), 0);
        if (amt != null) {
            b.append("  ");
            int s1 = b.length();
            b.append(signed(amt));
            b.setSpan(new ForegroundColorSpan(amt < 0 ? 0xFFF07070 : 0xFF55B981), s1, b.length(), 0);
            b.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), s1, b.length(), 0);
        }
        if (rest != null && !rest.isEmpty()) b.append("  ").append(rest);
    }

    private static void bar(RemoteViews v, int text, int bar, String label, Double a, Double t, String unit) {
        if (a == null) { v.setTextViewText(text, label + ": –"); v.setProgressBar(bar, 100, 0, false); return; }
        v.setTextViewText(text, label + ": " + fmt(a) + (t != null ? " / " + fmt(t) : "") + unit);
        int pct = t != null && t > 0 ? (int) Math.round(Math.min(1.0, a / t) * 100) : 0;
        v.setProgressBar(bar, 100, pct, false);
    }

    /** "Tracker 5 min ago" (when the Tracker last wrote the numbers), with a nudge when it's old. */
    static String asOf(String updated, long fetched) {
        if (updated == null || updated.isEmpty()) return fetched == 0 ? "Open the app to set up" : "No data yet";
        try {
            long mins = Duration.between(Instant.parse(updated), Instant.now()).toMinutes();
            String ago = mins < 2 ? "just now" : mins < 60 ? mins + " min ago" : mins < 36 * 60 ? (mins / 60) + " h ago" : (mins / 1440) + " days ago";
            return "Tracker " + ago + (mins > 180 ? " · open it to update" : "");
        } catch (Exception e) {
            return "";
        }
    }

    // ---------- School's due chips and status colors (same scale as the Tracker) ----------

    private static final long MIN = 60000L, HR = 60 * MIN, DAY = 24 * HR;
    private static final long[][] BANDS = {{30 * MIN, 350}, {HR, 356}, {2 * HR, 2}, {4 * HR, 8}, {8 * HR, 14}, {DAY, 20}, {2 * DAY, 28},
            {3 * DAY, 36}, {5 * DAY, 46}, {7 * DAY, 58}, {14 * DAY, 95}, {30 * DAY, 145}};

    static int bandHue(long ms) {
        for (long[] b : BANDS) if (ms <= b[0]) return (int) b[1];
        return 200;
    }

    static String fmtSpan(long ms) {
        long total = Math.round(Math.abs(ms) / (double) MIN), d = total / 1440, h = (total % 1440) / 60, m = total % 60;
        if (d > 0) return d + (d == 1 ? " day" : " days") + (h > 0 ? " " + h + "h" : "");
        if (h > 0) return h + (h == 1 ? " hr" : " hrs") + (m > 0 ? " " + m + "m" : "");
        return m + " min";
    }

    static int chipBg(int h) { return hsl(h, 0.42f, 0.23f); }
    static int chipFg(int h) { return hsl(h, 0.62f, 0.84f); }

    static int hsl(float h, float s, float l) {
        float[] hsl = {((h % 360) + 360) % 360, s, l};
        return androidx.core.graphics.ColorUtils.HSLToColor(hsl);
    }

    /** Colors a pill (an ImageView background with a TextView on top) and sets its text. */
    static void pill(RemoteViews v, int frame, int bg, int text, String label, int bgColor, int fgColor) {
        v.setViewVisibility(frame, View.VISIBLE);
        v.setInt(bg, "setColorFilter", bgColor);
        v.setTextViewText(text, label);
        v.setTextColor(text, fgColor);
    }

    // ---------- small helpers ----------

    static String clock(int mins) {
        mins = ((mins % 1440) + 1440) % 1440;
        int h = mins / 60, m = mins % 60;
        return (h % 12 == 0 ? 12 : h % 12) + (m > 0 ? ":" + (m < 10 ? "0" : "") + m : "") + (h < 12 ? " AM" : " PM");
    }

    static String dayName(String iso) {
        try { return LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("EEEE", Locale.US)); } catch (Exception e) { return "an earlier day"; }
    }

    static String dueLabel(String s) {
        try {
            LocalDateTime t = LocalDateTime.parse(s.trim().replace(' ', 'T'));
            LocalDate today = LocalDate.now();
            String day = t.toLocalDate().equals(today) ? "Today" : t.toLocalDate().equals(today.plusDays(1)) ? "Tomorrow"
                    : t.format(DateTimeFormatter.ofPattern("EEE M/d", Locale.US));
            return day + " " + clock(t.getHour() * 60 + t.getMinute());
        } catch (Exception e) {
            return s;
        }
    }

    /** "CHEMICAL ENGRNG THERMODYNAMICS" → "ChemE Thermo", shortened the same way the Tracker does. */
    static String shortClass(String n) {
        String s = n.trim().toLowerCase(Locale.US);
        StringBuilder b = new StringBuilder();
        for (String x : s.split("\\s+")) {
            if (x.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            if (x.matches("i|ii|iii|iv")) b.append(x.toUpperCase(Locale.US));
            else if (x.matches("of|and|to|in|for")) b.append(x);
            else b.append(Character.toUpperCase(x.charAt(0))).append(x.substring(1));
        }
        return b.toString().replace("Chemical Engrng", "ChemE").replace("Chemical Engineering", "ChemE").replace("Chemistry", "Chem")
                .replace("Thermodynamics", "Thermo").replace("Intro to ", "").replace("Majors ", "");
    }

    static Double opt(JSONObject o, String k) {
        Object x = o.opt(k);
        if (x instanceof Number) return ((Number) x).doubleValue();
        return x == null ? null : num(String.valueOf(x));
    }

    static Double num(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try { return Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    static String fmt(double x) {
        return Math.abs(x - Math.rint(x)) < 0.05 ? NumberFormat.getIntegerInstance(Locale.US).format(Math.round(x))
                : String.format(Locale.US, "%.1f", x);
    }

    static String dollars(double x) {
        return (x < 0 ? "-$" : "$") + String.format(Locale.US, "%,.2f", Math.abs(x));
    }

    static String signed(double x) {
        return (x < 0 ? "−$" : "+$") + String.format(Locale.US, "%,.2f", Math.abs(x));
    }

    static String hm(int mins) {
        return mins < 60 ? mins + " min" : (mins / 60) + "h " + (mins % 60) + "m";
    }

    static int parse(String hex) { return Color.parseColor(hex); }
}
