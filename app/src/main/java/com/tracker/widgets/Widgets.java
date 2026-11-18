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
            String fs = foodSig(c, w, id), fk = "food_sig_" + id;   // reload the list only when it changed (keeps your scroll place)
            if (!fs.equals(WidgetData.prefs(c).getString(fk, ""))) {
                WidgetData.prefs(c).edit().putString(fk, fs).apply();
                m.notifyAppWidgetViewDataChanged(id, R.id.h_list);
            }
        }
        for (int id : m.getAppWidgetIds(new ComponentName(c, MoneyWidget.class))) {
            m.updateAppWidget(id, money(c, d, w, id));
            // Reload the cards only when what they show changed: a reload mid-swipe can jump or scatter the stack.
            String sig = moneySig(c, w, id), key = "acct_sig_" + id;
            if (!sig.equals(WidgetData.prefs(c).getString(key, ""))) {
                WidgetData.prefs(c).edit().putString(key, sig).apply();
                m.notifyAppWidgetViewDataChanged(id, R.id.acct_stack);
            }
        }
        for (int id : m.getAppWidgetIds(new ComponentName(c, ScheduleWidget.class))) {
            m.updateAppWidget(id, schedule(c, d, w, id));
        }
        for (int id : m.getAppWidgetIds(new ComponentName(c, DeadlinesWidget.class))) {
            m.updateAppWidget(id, school(c, d, id));
        }
        for (int id : m.getAppWidgetIds(new ComponentName(c, WeatherWidget.class))) m.updateAppWidget(id, Extras.weather(c, id));
        for (int id : m.getAppWidgetIds(new ComponentName(c, ScreenTimeWidget.class))) m.updateAppWidget(id, Extras.screen(c, id));
        for (int id : m.getAppWidgetIds(new ComponentName(c, CalendarWidget.class))) m.updateAppWidget(id, Extras.calendar(c, id));
        for (int id : m.getAppWidgetIds(new ComponentName(c, StepsWidget.class))) {
            m.updateAppWidget(id, Extras.steps(c, id));
            String ss = Integer.toHexString((WidgetData.prefs(c).getString("steps", "") + "|" + Extras.goal(c) + "|" + scale(c, id)).hashCode()), sk = "steps_sig_" + id;
            if (!ss.equals(WidgetData.prefs(c).getString(sk, ""))) {   // reload the past days only when they changed
                WidgetData.prefs(c).edit().putString(sk, ss).apply();
                m.notifyAppWidgetViewDataChanged(id, R.id.sp_days);
            }
        }
        for (int id : m.getAppWidgetIds(new ComponentName(c, InboxWidget.class))) {
            m.updateAppWidget(id, inbox(c, d, id));
            m.notifyAppWidgetViewDataChanged(id, R.id.inbox_list);
        }
    }

    /** What the Money cards show: the accounts, entries still waiting, and the widget's size. */
    private static String moneySig(Context c, JSONObject w, int id) {
        JSONObject mo = w.optJSONObject("m");
        StringBuilder s = new StringBuilder(mo == null ? "" : String.valueOf(mo.optJSONArray("acc")));
        for (JSONObject e : Phone.pendingOf(c, "money")) s.append('|').append(e.optString("id"));
        s.append('|').append(scale(c, id));
        return Integer.toHexString(s.toString().hashCode()) + ":" + s.length();
    }

    /** What the food list shows: today's foods, recent foods, entries still waiting, and the widget's size. */
    private static String foodSig(Context c, JSONObject w, int id) {
        JSONObject h = w.optJSONObject("h");
        StringBuilder s = new StringBuilder(h == null ? "" : String.valueOf(h.optJSONArray("foods")) + h.optJSONArray("recent"));
        for (JSONObject e : Phone.pendingOf(c, "food")) s.append('|').append(e.optString("id"));
        for (JSONObject e : Phone.pendingOf(c, "photo")) s.append('|').append(e.optString("id"));
        s.append('|').append(scale(c, id));
        return Integer.toHexString(s.toString().hashCode()) + ":" + s.length();
    }

    // ---------- Health ----------

    static RemoteViews food(Context c, Map<String, String> d, JSONObject w, int wid) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_food);
        common(c, v, d, "#health", FoodWidget.class);
        float k = scale(c, wid);
        title(v, k);
        JSONObject h = w.optJSONObject("h");
        // The Tracker's food bars (blue on the way, green on target, orange over) and water.
        Double kcal = h != null ? opt(h, "kcal") : num(d.get("kcal")), kcalT = h != null ? opt(h, "kcalT") : num(d.get("kcal_target"));
        Double p = h != null ? opt(h, "p") : num(d.get("protein")), pT = h != null ? opt(h, "pT") : num(d.get("protein_target"));
        Double cb = h != null ? opt(h, "c") : null, cT = h != null ? opt(h, "cT") : null, f = h != null ? opt(h, "f") : null, fT = h != null ? opt(h, "fT") : null;
        Double water = h != null ? opt(h, "water") : num(d.get("water_oz")), waterG = h != null ? opt(h, "waterG") : num(d.get("water_goal_oz"));
        double addW = Phone.pendingWater(c);
        if (addW > 0) water = (water == null ? 0 : water) + addW;
        // Foods added again from the widget count right away (their numbers are known).
        for (JSONObject e : Phone.pendingOf(c, "food")) if (e.has("kcal")) {
            kcal = (kcal == null ? 0 : kcal) + e.optDouble("kcal", 0);
            p = (p == null ? 0 : p) + e.optDouble("p", 0);
            cb = (cb == null ? 0 : cb) + e.optDouble("c", 0);
            f = (f == null ? 0 : f) + e.optDouble("f", 0);
        }
        v.removeAllViews(R.id.h_bars);
        v.addView(R.id.h_bars, Pages.row(c, hbar("Calories", kcal, kcalT, "kcal", "kcal"), k));
        v.addView(R.id.h_bars, Pages.row(c, hbar("Protein", p, pT, "g", "p"), k));
        v.addView(R.id.h_bars, Pages.row(c, hbar("Carbs", cb, cT, "g", "c"), k));
        v.addView(R.id.h_bars, Pages.row(c, hbar("Fat", f, fT, "g", "f"), k));
        v.addView(R.id.h_bars, Pages.row(c, hbar("Water", water, waterG, "oz", "w"), k));
        String left = "";
        if (kcal != null && kcalT != null && kcalT > 0)
            left = kcal < kcalT ? fmt(kcalT - kcal) + " kcal and " + fmt(Math.max(0, (pT == null ? 0 : pT) - (p == null ? 0 : p))) + " g protein left" : "Calorie target reached";
        Double caf = h != null ? opt(h, "caf") : num(d.get("caffeine_mg"));
        v.setTextViewText(R.id.h_left, left + (caf == null ? "" : (left.isEmpty() ? "" : "  ·  ") + "☕ " + fmt(caf) + " mg caffeine"));
        ts(v, R.id.h_left, 11.5f, k);
        ts(v, R.id.h_food_h, 12, k);
        // Today's food and, under it, recent foods, each with ＋ to add it again: a scrolling list (ListService kind "foods").
        list(c, v, wid, R.id.h_list, R.id.h_empty, "foods");
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
        return v;
    }

    /** A food bar row: ["bar", label, "1,820 / 3,330 kcal", pct, color] with the Food tab's color rules. */
    private static JSONArray hbar(String label, Double a, Double t, String unit, String kind) {
        double v = a == null ? 0 : a, pct = t != null && t > 0 ? v / t * 100 : 0;
        String col = kind.equals("w") ? (pct >= 100 ? "g" : "w") : t == null || t <= 0 ? "b"
                : kind.equals("kcal") ? (pct > 105 ? "o" : pct >= 90 ? "g" : "b") : kind.equals("p") ? (pct >= 100 ? "g" : "b") : (pct > 115 ? "o" : pct >= 90 ? "g" : "b");
        return new JSONArray().put("bar").put(label).put((a == null ? "–" : fmt(v)) + (t != null && t > 0 ? " / " + fmt(t) : "") + " " + unit)
                .put((int) Math.min(100, Math.round(pct))).put(col);
    }

    /** One food: [name, kcal, "HH:MM", protein, carbs, fat, serving, source]. */
    static RemoteViews foodCard(Context c, JSONArray x, float k) {
        RemoteViews r = new RemoteViews(c.getPackageName(), R.layout.item_food_card);
        String at = x.optString(2), t = "";
        if (at.matches("\\d{1,2}:\\d{2}")) { String[] q = at.split(":"); t = clock(Integer.parseInt(q[0]) * 60 + Integer.parseInt(q[1])).replace(" AM", "a").replace(" PM", "p"); }
        r.setTextViewText(R.id.fc_time, t);
        r.setViewVisibility(R.id.fc_time, t.isEmpty() ? View.GONE : View.VISIBLE);
        r.setTextViewText(R.id.fc_name, x.optString(0));
        int kc = x.optInt(1, -1);
        r.setTextViewText(R.id.fc_kcal, kc >= 0 ? fmt(kc) : "");
        String src = x.optString(7), badge = src.equals("photo") ? "📷 " : src.equals("receipt") ? "🧾 " : src.equals("label") ? "🏷 " : src.equals("email") ? "✉ " : "";
        r.setTextViewText(R.id.fc_serving, badge + x.optString(6));
        SpannableStringBuilder m = new SpannableStringBuilder();
        if (x.optInt(3, -1) >= 0) macros(m, x.optInt(3), x.optInt(4), x.optInt(5));
        r.setTextViewText(R.id.fc_macros, m);
        r.setTextViewTextSize(R.id.fc_time, android.util.TypedValue.COMPLEX_UNIT_SP, 11 * k);
        r.setTextViewTextSize(R.id.fc_name, android.util.TypedValue.COMPLEX_UNIT_SP, 12.5f * k);
        r.setTextViewTextSize(R.id.fc_kcal, android.util.TypedValue.COMPLEX_UNIT_SP, 15 * k);
        r.setTextViewTextSize(R.id.fc_serving, android.util.TypedValue.COMPLEX_UNIT_SP, 10.5f * k);
        r.setTextViewTextSize(R.id.fc_macros, android.util.TypedValue.COMPLEX_UNIT_SP, 10.5f * k);
        return r;
    }

    /** "P 20  C 55  F 22" in the Tracker's protein blue, carb yellow and fat pink. */
    private static void macros(SpannableStringBuilder b, int p, int cb, int f) {
        String[][] parts = {{"P " + p, "#6B9CEC"}, {"C " + cb, "#E8BD34"}, {"F " + f, "#EE77AE"}};
        for (int i = 0; i < 3; i++) {
            if (i > 0) b.append("  ");
            int s0 = b.length();
            b.append(parts[i][0]);
            b.setSpan(new ForegroundColorSpan(android.graphics.Color.parseColor(parts[i][1])), s0, b.length(), 0);
            b.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), s0, b.length(), 0);
        }
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

    // ---------- Money ----------

    static final List<String> SPEND = Arrays.asList("Food", "Supplies", "Gifts", "Fun", "Bill");

    static RemoteViews money(Context c, Map<String, String> d, JSONObject w, int wid) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_money);
        common(c, v, d, "#money", MoneyWidget.class);
        float k = scale(c, wid);
        title(v, k);
        JSONObject m = w.optJSONObject("m");
        // Checking, Savings and Cash as a stack of cards: swipe up for the next one. Each card's name and balance are at
        // its top, which is all that shows of the cards behind; its latest entries are lower down, on the front card.
        Intent svc = new Intent(c, ListService.class).putExtra("kind", "acct").putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, wid);
        svc.setData(Uri.parse("tracker://list/acct/" + wid));
        v.setRemoteAdapter(R.id.acct_stack, svc);
        v.setEmptyView(R.id.acct_stack, R.id.acct_empty);
        // Tapping a card does nothing (swipe for the next one): the template only exists so the cards' taps go nowhere outside.
        Intent none = new Intent(c, ActionReceiver.class).setAction(ActionReceiver.ACT_LIST);
        v.setPendingIntentTemplate(R.id.acct_stack, PendingIntent.getBroadcast(c, 7100, none, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE));
        Double spent = m != null ? opt(m, "spent") : num(d.get("month_spent"));
        for (JSONObject e : Phone.pendingOf(c, "money"))
            if ("out".equals(e.optString("dir")) && SPEND.contains(e.optString("type"))) spent = (spent == null ? 0 : spent) + e.optDouble("amount", 0);
        v.setTextViewText(R.id.spent_text, spent == null ? "" : "Spent this month  " + dollars(spent));
        ts(v, R.id.spent_text, 12.5f, k);
        v.setOnClickPendingIntent(R.id.btn_out, entry(c, "money", "out", false));
        v.setOnClickPendingIntent(R.id.btn_in, entry(c, "money", "in", false));
        v.setOnClickPendingIntent(R.id.btn_move, entry(c, "money", "move", false));
        v.setOnClickPendingIntent(R.id.btn_cam, entry(c, "money", "out", true));
        return v;
    }

    static int acctColor(String key) { return key.equals("savings") ? 0xFF6B9CEC : key.equals("cash") ? 0xFFE8BD34 : 0xFF55B981; }

    private static double pendingEffect(Context c, String key) {
        double sum = 0;
        for (JSONObject e : Phone.pendingOf(c, "money")) sum += ListService.Factory.effect(key, e.optString("dir"), e.optString("type"), e.optString("card"), e.optDouble("amount", 0));
        return sum;
    }

    // ---------- Schedule: Aarian and Miranda side by side ----------

    static RemoteViews schedule(Context c, Map<String, String> d, JSONObject w, int wid) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_schedule);
        common(c, v, d, "#schedule", ScheduleWidget.class);
        float k = scale(c, wid);
        title(v, k);
        JSONObject s = w.optJSONObject("s");
        LocalDate today = LocalDate.now();
        v.setTextViewText(R.id.title_text, s != null && !today.toString().equals(s.optString("date")) ? "SCHEDULE · OPEN THE TRACKER" : "SCHEDULE");
        // The day (‹ › for the rest of the week, tap the date for today), drawn on the time grid.
        JSONArray days = s != null ? s.optJSONArray("days") : null;
        int count = 1 + (days == null ? 0 : days.length()), off = Math.min(count - 1, dayOffset(c, wid));
        JSONObject day = off == 0 || days == null ? s : days.optJSONObject(off - 1);
        LocalDate date;
        try { date = LocalDate.parse(day.optString("date")); } catch (Exception e) { date = today.plusDays(off); }
        String dn = off == 0 ? "TODAY" : off == 1 ? "TOMORROW" : date.format(DateTimeFormatter.ofPattern("EEEE", Locale.US)).toUpperCase(Locale.US);
        v.setTextViewText(R.id.day_title, dn + " · " + date.format(DateTimeFormatter.ofPattern("EEE M/d", Locale.US)).toUpperCase(Locale.US));
        ts(v, R.id.day_title, 13.5f, k);
        String ws = String.valueOf(wid);
        Pages.click(c, v, R.id.day_prev, "op", "day", "wid", ws, "to", String.valueOf((off - 1 + count) % count));
        Pages.click(c, v, R.id.day_next, "op", "day", "wid", ws, "to", String.valueOf((off + 1) % count));
        Pages.click(c, v, R.id.day_title, "op", "day", "wid", ws, "to", "0");
        int nowMin = LocalTime.now().getHour() * 60 + LocalTime.now().getMinute();
        v.setViewVisibility(R.id.cards, off == 0 ? View.VISIBLE : View.GONE);
        if (off == 0 && s != null) {
            JSONArray study = s.optJSONArray("study");
            boolean studying = study != null && study.optInt(1) == 1;
            nowNext(v, s.optJSONArray("me"), nowMin, R.id.now_me, R.id.next_me, "Miranda", studying ? s.optJSONArray("nowMe") : null);
            nowNext(v, s.optJSONArray("mi"), nowMin, R.id.now_mi, R.id.next_mi, "Aarian", null);
        }
        int[] sz = sizeDp(c, wid);
        float h = Math.max(150, sz[1] - (26 + 28 + 18 + 84 + 16) * k - (off == 0 ? 70 * k : 0) - 34);
        int end = day != null ? Grid.build(c, v, day.optJSONArray("me"), day.optJSONArray("mi"), off == 0 ? nowMin : -1, Math.max(150, sz[0] - 26), h, k) : 24 * 60;
        // What's after the last hour that fits, in one line.
        StringBuilder later = new StringBuilder();
        for (String who : new String[]{"me", "mi"}) {
            JSONArray evs = day != null ? day.optJSONArray(who) : null;
            for (int i = 0; evs != null && i < evs.length(); i++) {
                JSONArray e = evs.optJSONArray(i);
                if (e == null || e.optInt(0) < end || "sleep".equals(e.optString(3))) continue;
                String item = e.optString(2) + " " + clock(e.optInt(0));
                if (later.indexOf(item) < 0) later.append(later.length() > 0 ? " · " : "").append(item);
            }
        }
        v.setTextViewText(R.id.grid_more, "Later: " + later);
        v.setViewVisibility(R.id.grid_more, later.length() > 0 ? View.VISIBLE : View.GONE);
        sleepStudy(c, v, s, k);
        return v;
    }

    /** Sleep and study under the grid: where things stand, and Going to bed / Woke up and Start / Stop studying. */
    private static void sleepStudy(Context c, RemoteViews v, JSONObject s, float k) {
        JSONObject sl = s != null ? s.optJSONObject("sl") : null, st = s != null ? s.optJSONObject("st") : null;
        List<JSONObject> ps = Phone.pendingOf(c, "sleep"), pt = Phone.pendingOf(c, "study");
        boolean inBed = !ps.isEmpty() ? "bed".equals(ps.get(ps.size() - 1).optString("do")) : sl != null && !sl.optString("open").isEmpty();
        JSONArray last = sl != null ? sl.optJSONArray("last") : null;
        if (inBed) {
            String since = !ps.isEmpty() ? hmLabel(ps.get(ps.size() - 1).optString("hm")) + " ⏳" : sl.optString("open");
            v.setTextViewText(R.id.sl_text, "😴 In bed since " + since);
            v.setTextViewText(R.id.sl_text_sub, "Tap Woke up when you get up");
        } else {
            v.setTextViewText(R.id.sl_text, last != null ? "😴 " + hm(last.optInt(0)) + " last night" : "😴 No sleep logged yet");
            v.setTextViewText(R.id.sl_text_sub, last != null ? last.optString(1) + " – " + last.optString(2) + (sl.isNull("avg") ? "" : " · avg " + hm(sl.optInt("avg"))) : "");
        }
        v.setTextViewText(R.id.btn_sleep, inBed ? "☀  Woke up" : "🌙  Going to bed");
        v.setOnClickPendingIntent(R.id.btn_sleep, act(c, "sleep", "do", inBed ? "wake" : "bed"));
        boolean studying = !pt.isEmpty() ? "start".equals(pt.get(pt.size() - 1).optString("do")) : st != null && !st.optString("open").isEmpty();
        if (studying) {
            String since = !pt.isEmpty() ? hmLabel(pt.get(pt.size() - 1).optString("hm")) + " ⏳" : st.optString("open");
            v.setTextViewText(R.id.st_text, "📚 Studying since " + since);
        } else v.setTextViewText(R.id.st_text, "📚 " + hm(st != null ? st.optInt("today") : 0) + " today");
        v.setTextViewText(R.id.st_text_sub, hm(st != null ? st.optInt("week") : 0) + " this week");
        v.setTextViewText(R.id.btn_study, studying ? "■  Stop studying" : "▶  Start studying");
        if (studying) v.setOnClickPendingIntent(R.id.btn_study, entry(c, "study", null, false));
        else v.setOnClickPendingIntent(R.id.btn_study, act(c, "study", "do", "start"));
        for (int id : new int[]{R.id.sl_text, R.id.st_text}) ts(v, id, 12, k);
        for (int id : new int[]{R.id.sl_text_sub, R.id.st_text_sub}) ts(v, id, 10.5f, k);
        for (int id : new int[]{R.id.btn_sleep, R.id.btn_study}) ts(v, id, 13, k);
    }

    private static String hmLabel(String hm) {
        try { String[] q = hm.split(":"); return clock(Integer.parseInt(q[0]) * 60 + Integer.parseInt(q[1])); } catch (Exception e) { return hm; }
    }

    static void nowNext(RemoteViews v, JSONArray evs, int now, int nowId, int nextId, String other, JSONArray studyNow) {
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
        float k = scale(c, wid);
        title(v, k);
        JSONObject w = WidgetData.w(c), g = w.optJSONObject("g");
        // Your GPA and each class's letter, like the Grades tab.
        if (g != null) {
            v.setTextViewText(R.id.gpa, g.isNull("gpa") ? "–" : String.format(Locale.US, "%.2f", g.optDouble("gpa")));
            v.setTextViewText(R.id.gpa_sub, "est. GPA" + (g.isNull("cgpa") ? "" : String.format(Locale.US, " · cum %.2f", g.optDouble("cgpa"))));
            SpannableStringBuilder b = new SpannableStringBuilder();
            JSONArray cl = g.optJSONArray("cls");
            for (int i = 0; cl != null && i < cl.length(); i++) {
                JSONArray x = cl.optJSONArray(i);
                if (x == null) continue;
                if (b.length() > 0) b.append("\n");
                b.append(x.optString(0)).append("  ");
                int s0 = b.length();
                double pc = x.optDouble(2, Double.NaN);
                b.append(x.optString(1));
                int col = Double.isNaN(pc) ? MUTED : pc >= 90 ? 0xFF55B981 : pc >= 80 ? 0xFF6B9CEC : pc >= 70 ? 0xFFF0A040 : 0xFFF07070;
                b.setSpan(new ForegroundColorSpan(col), s0, b.length(), 0);
                b.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), s0, b.length(), 0);
                if (!Double.isNaN(pc)) { int s1 = b.length(); b.append(String.format(Locale.US, " %.1f%%", pc)); b.setSpan(new ForegroundColorSpan(MUTED), s1, b.length(), 0); }
            }
            v.setTextViewText(R.id.gpa_cls, b);
            v.setInt(R.id.gpa_cls, "setMaxLines", Math.max(2, Math.min(cl == null ? 2 : cl.length(), 8)));
        } else v.setTextViewText(R.id.gpa_cls, "Open School → Grades in the Tracker once so your grades show here.");
        ts(v, R.id.gpa, 28, k);
        ts(v, R.id.gpa_cls, 11.5f, k);
        ts(v, R.id.due_h, 12, k);
        // What's due soon, with the same status buttons as School.
        v.removeAllViews(R.id.due_list);
        JSONArray a = w.optJSONArray("a");
        int n = a == null ? 0 : a.length();
        int[] sz = sizeDp(c, wid);
        int lines = g != null && g.optJSONArray("cls") != null ? Math.min(g.optJSONArray("cls").length(), 8) : 2;
        int fit = (int) Math.max(1, (sz[1] - (26 + 30 + lines * 17 + 26) * k - 40) / (84 * k));
        for (int i = 0; i < Math.min(fit, n); i++) {
            JSONArray x = a.optJSONArray(i);
            if (x == null) continue;
            JSONArray row = new JSONArray().put("prog");
            for (int j = 0; j < x.length(); j++) row.put(x.opt(j));
            v.addView(R.id.due_list, Pages.prog(c, row, k));
        }
        if (n == 0) v.addView(R.id.due_list, Pages.note(c, "Nothing due. Open the Tracker once so this fills in.", k));
        else if (n > fit) v.addView(R.id.due_list, Pages.note(c, "+" + (n - fit) + " more in the Tracker", k));
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

    /** A section's tabs as a stack of pages (ListService kind "pages:<section>"); buttons on them go to ActionReceiver. */
    private static void pagesStack(Context c, RemoteViews v, int wid, int stackId, int emptyId, String section) {
        Intent svc = new Intent(c, ListService.class).putExtra("kind", "pages:" + section).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, wid);
        svc.setData(Uri.parse("tracker://pages/" + section + "/" + wid));
        v.setRemoteAdapter(stackId, svc);
        v.setEmptyView(stackId, emptyId);
        Intent t = new Intent(c, ActionReceiver.class).setAction(ActionReceiver.ACT_LIST);
        v.setPendingIntentTemplate(stackId, PendingIntent.getBroadcast(c, 7600 + Math.abs(section.hashCode() % 100), t,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE));
    }

    /** A scrolling list filled by ListService; its buttons all go to ActionReceiver. */
    static void list(Context c, RemoteViews v, int wid, int listId, int emptyId, String kind) {
        Intent svc = new Intent(c, ListService.class).putExtra("kind", kind).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, wid);
        svc.setData(Uri.parse("tracker://list/" + kind + "/" + wid));
        v.setRemoteAdapter(listId, svc);
        v.setEmptyView(listId, emptyId);
        Intent t = new Intent(c, ActionReceiver.class).setAction(ActionReceiver.ACT_LIST);
        v.setPendingIntentTemplate(listId, PendingIntent.getBroadcast(c, 7000 + kind.hashCode() % 1000, t,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE));
    }

    /** Sync is running (the Sync button was tapped in the last 30 seconds and hasn't finished). */
    static boolean syncing(Context c) {
        long t = WidgetData.prefs(c).getLong("syncing_at", 0);
        return t > 0 && System.currentTimeMillis() - t < 30_000;
    }

    /** ⟳ Sync: sends what's waiting, fetches the Tracker's latest and rereads the phone's data, right away (SyncActivity). */
    static void syncButton(Context c, RemoteViews v) {
        Intent i = new Intent(c, SyncActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        v.setOnClickPendingIntent(R.id.refresh, PendingIntent.getActivity(c, 7777, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
    }

    /** The "as of" line (plus what's waiting) and the Sync button. Tapping the widget itself opens nothing. */
    private static void common(Context c, RemoteViews v, Map<String, String> d, String section, Class<?> provider) {
        String wait = Phone.waitingNote(c), err = WidgetData.prefs(c).getString("send_error", "");
        String line = asOf(d.get("updated"), WidgetData.fetchedAt(c));
        if (!err.isEmpty() && Phone.unsent(c) > 0) line = err;
        else if (!wait.isEmpty()) line = wait + " · " + line;
        v.setTextViewText(R.id.updated_text, syncing(c) ? "Syncing…" : line);
        syncButton(c, v);
        // A tap anywhere that isn't a button does nothing (otherwise the launcher may open an app).
        Pages.click(c, v, R.id.root, "op", "none");
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
