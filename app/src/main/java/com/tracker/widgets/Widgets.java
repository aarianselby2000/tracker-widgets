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
        for (int id : m.getAppWidgetIds(new ComponentName(c, FoodWidget.class))) m.updateAppWidget(id, food(c, d, w));
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

    static RemoteViews food(Context c, Map<String, String> d, JSONObject w) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_food);
        common(c, v, d, "#health", FoodWidget.class);
        JSONObject h = w.optJSONObject("h");
        Double kcal = h != null ? opt(h, "kcal") : num(d.get("kcal")), kcalT = h != null ? opt(h, "kcalT") : num(d.get("kcal_target"));
        Double p = h != null ? opt(h, "p") : num(d.get("protein")), pT = h != null ? opt(h, "pT") : num(d.get("protein_target"));
        Double water = h != null ? opt(h, "water") : num(d.get("water_oz")), waterG = h != null ? opt(h, "waterG") : num(d.get("water_goal_oz"));
        double addW = Phone.pendingWater(c);
        if (addW > 0) water = (water == null ? 0 : water) + addW;
        // The Tracker's food bars: blue on the way, green once on target, orange when over (same rules as its Food tab).
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
        boolean supps = "yes".equals(h != null ? h.optString("supps") : d.get("supplements"));
        Boolean ps = Phone.pendingSupps(c);
        if (ps != null) supps = ps;
        Double caf = h != null ? opt(h, "caf") : num(d.get("caffeine_mg"));
        v.setTextViewText(R.id.caf_text, "☕ " + (caf == null ? "–" : fmt(caf)) + " / 400 mg");
        // Today's last foods, newest first, with ones typed on the phone that the Tracker hasn't estimated yet.
        StringBuilder f = new StringBuilder();
        for (int i = 0; i < Phone.pendingOf(c, "photo").size(); i++) f.append(f.length() > 0 ? " · " : "").append("⏳ Photo");
        List<JSONObject> pf = Phone.pendingOf(c, "food");
        for (int i = pf.size() - 1; i >= 0; i--) f.append(f.length() > 0 ? " · " : "").append("⏳ ").append(pf.get(i).optString("text"));
        JSONArray foods = h != null ? h.optJSONArray("foods") : null;
        for (int i = foods == null ? -1 : foods.length() - 1; i >= 0 && f.length() < 160; i--) {
            JSONArray x = foods.optJSONArray(i);
            if (x != null) f.append(f.length() > 0 ? " · " : "").append(x.optString(0)).append(x.optInt(1) > 0 ? " " + x.optInt(1) : "");
        }
        v.setTextViewText(R.id.foods_text, f.length() == 0 ? "No food logged today" : f.toString());
        int bottle = h != null ? h.optInt("bottle", 40) : 40;
        v.setTextViewText(R.id.btn_w3, "+" + bottle + " oz");
        v.setOnClickPendingIntent(R.id.btn_w1, act(c, "water", "oz", "8"));
        v.setOnClickPendingIntent(R.id.btn_w2, act(c, "water", "oz", "16"));
        v.setOnClickPendingIntent(R.id.btn_w3, act(c, "water", "oz", String.valueOf(bottle)));
        v.setTextViewText(R.id.btn_supps, supps ? "✓ Supplements" : "○ Supplements");
        v.setTextColor(R.id.btn_supps, supps ? 0xFF55B981 : MUTED);
        v.setOnClickPendingIntent(R.id.btn_supps, act(c, "supps", "on", supps ? "false" : "true"));
        v.setOnClickPendingIntent(R.id.btn_food, entry(c, "food", null, false));
        v.setOnClickPendingIntent(R.id.btn_cam, entry(c, "food", null, true));
        return v;
    }

    // ---------- Money ----------

    static final List<String> SPEND = Arrays.asList("Food", "Supplies", "Gifts", "Fun", "Bill");

    static RemoteViews money(Context c, Map<String, String> d, JSONObject w, int wid) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_money);
        common(c, v, d, "#money", MoneyWidget.class);
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
        LocalDate today = LocalDate.now();
        v.setTextViewText(R.id.title_text, "TODAY · " + today.format(DateTimeFormatter.ofPattern("EEE M/d", Locale.US)).toUpperCase(Locale.US));
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
        // The day as the Tracker draws it, sized to this widget (its height minus the header and the two cards).
        android.os.Bundle o = AppWidgetManager.getInstance(c).getAppWidgetOptions(wid);
        float dens = Math.min(2.75f, c.getResources().getDisplayMetrics().density);
        int wDp = Math.max(160, o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 300)) - 26;
        int hDp = Math.max(80, o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 300) - 150 - (note.isEmpty() ? 0 : 14));
        v.setImageViewBitmap(R.id.timeline, Timeline.draw(s != null ? s.optJSONArray("me") : null, s != null ? s.optJSONArray("mi") : null,
                nowMin, Math.round(wDp * dens), Math.round(hDp * dens), dens));
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
        list(c, v, wid, R.id.assign_list, R.id.assign_empty, "assign");
        return v;
    }

    // ---------- Inbox (email and texts) ----------

    static RemoteViews inbox(Context c, Map<String, String> d, int wid) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_inbox);
        common(c, v, d, "#inbox", InboxWidget.class);
        list(c, v, wid, R.id.inbox_list, R.id.inbox_empty, "inbox");
        v.setOnClickPendingIntent(R.id.btn_quick, entry(c, "quick", null, false));
        return v;
    }

    // ---------- shared parts ----------

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
    private static void macro(RemoteViews v, int blue, int green, int orange, int val, Double a, Double t, String unit, String kind) {
        double pct = a != null && t != null && t > 0 ? a / t * 100 : 0;
        String cls = t == null || t <= 0 ? "" : kind.equals("kcal") ? (pct > 105 ? "over" : pct >= 90 ? "ok" : "") : kind.equals("p") ? (pct >= 100 ? "ok" : "") : (pct > 115 ? "over" : pct >= 90 ? "ok" : "");
        int show = cls.equals("ok") ? green : cls.equals("over") ? orange : blue;
        for (int id : new int[]{blue, green, orange}) v.setViewVisibility(id, id == show ? View.VISIBLE : View.GONE);
        v.setProgressBar(show, 100, (int) Math.round(Math.min(100, pct)), false);
        v.setTextViewText(val, amount(a, t, unit));
    }

    /** "1,820 / 3,330 kcal" with the amount in bold and the target in gray. */
    private static CharSequence amount(Double a, Double t, String unit) {
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
