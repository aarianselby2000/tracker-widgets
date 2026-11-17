package com.tracker.widgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.RemoteViews;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/** Builds each widget's view from the last downloaded values. */
final class Widgets {
    private Widgets() {}

    static final String ACTION_REFRESH = "com.tracker.widgets.REFRESH";

    /** Redraws every placed widget of every kind. */
    static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        Map<String, String> d = WidgetData.load(c);
        for (int id : m.getAppWidgetIds(new ComponentName(c, FoodWidget.class))) m.updateAppWidget(id, food(c, d));
        for (int id : m.getAppWidgetIds(new ComponentName(c, DeadlinesWidget.class))) m.updateAppWidget(id, deadlines(c, d));
        for (int id : m.getAppWidgetIds(new ComponentName(c, MoneyWidget.class))) m.updateAppWidget(id, money(c, d));
        for (int id : m.getAppWidgetIds(new ComponentName(c, ScheduleWidget.class))) m.updateAppWidget(id, schedule(c, d));
    }

    // ---------- the four widgets ----------

    static RemoteViews food(Context c, Map<String, String> d) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_food);
        common(c, v, d, "#health", FoodWidget.class);
        bar(v, R.id.kcal_text, R.id.kcal_bar, "Calories", d.get("kcal"), d.get("kcal_target"), "");
        bar(v, R.id.protein_text, R.id.protein_bar, "Protein", d.get("protein"), d.get("protein_target"), " g");
        bar(v, R.id.water_text, R.id.water_bar, "Water", d.get("water_oz"), d.get("water_goal_oz"), " oz");
        String caf = d.get("caffeine_mg");
        String supps = "yes".equals(d.get("supplements")) ? "Supplements ✓" : "Supplements not yet";
        v.setTextViewText(R.id.extra_text, (caf == null || caf.isEmpty() ? "" : "Caffeine " + caf + " / 400 mg · ") + supps);
        return v;
    }

    static RemoteViews deadlines(Context c, Map<String, String> d) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_deadlines);
        common(c, v, d, "#school", DeadlinesWidget.class);
        int[] rows = {R.id.due1, R.id.due2, R.id.due3, R.id.due4};
        int shown = 0;
        for (int i = 0; i < rows.length; i++) {
            String raw = d.get("due" + (i + 1));
            if (raw == null || raw.isEmpty()) { v.setViewVisibility(rows[i], View.GONE); continue; }
            String[] p = raw.split("\\|", -1);
            String name = p.length > 0 ? p[0] : "";
            String cls = p.length > 1 ? shortClass(p[1]) : "";
            String when = p.length > 2 ? dueLabel(p[2]) : "";
            boolean exam = p.length > 3 && "exam".equals(p[3]);
            v.setTextViewText(rows[i], (exam ? "★ " : "• ") + name + "\n   " + when + (cls.isEmpty() ? "" : " · " + cls));
            v.setViewVisibility(rows[i], View.VISIBLE);
            shown++;
        }
        v.setViewVisibility(R.id.empty_text, shown == 0 ? View.VISIBLE : View.GONE);
        return v;
    }

    static RemoteViews money(Context c, Map<String, String> d) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_money);
        common(c, v, d, "#money", MoneyWidget.class);
        Double bal = num(d.get("checking")), min = num(d.get("checking_min")), spent = num(d.get("month_spent"));
        v.setTextViewText(R.id.balance_text, bal == null ? "–" : dollars(bal));
        String room = "";
        if (bal != null && min != null) {
            double r = bal - min;
            room = r < 0 ? "Below your minimum by " + dollars(-r) : dollars(r) + " above your minimum";
            v.setTextColor(R.id.balance_text, r < 0 ? 0xFFF07070 : 0xFFFFFFFF);
        }
        v.setTextViewText(R.id.room_text, room);
        v.setTextViewText(R.id.spent_text, spent == null ? "" : "Spent this month: " + dollars(spent));
        return v;
    }

    static RemoteViews schedule(Context c, Map<String, String> d) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_schedule);
        common(c, v, d, "#schedule", ScheduleWidget.class);
        String now = d.get("now"), next = d.get("next");
        v.setTextViewText(R.id.now_text, now == null || now.isEmpty() ? "–" : now.replace("|", " · "));
        v.setTextViewText(R.id.next_text, next == null || next.isEmpty() ? "Nothing else coming up" : "Next: " + next.replace("|", " · "));
        Double mins = num(d.get("study_min"));
        String study = mins == null ? "" : "Studied today: " + hm(mins.intValue());
        if ("yes".equals(d.get("studying"))) study += (study.isEmpty() ? "" : " · ") + "studying now";
        v.setTextViewText(R.id.study_text, study);
        return v;
    }

    // ---------- shared parts ----------

    /** The "as of" line, the refresh button, and tapping the widget to open the Tracker. */
    private static void common(Context c, RemoteViews v, Map<String, String> d, String section, Class<?> provider) {
        v.setTextViewText(R.id.updated_text, asOf(d.get("updated"), WidgetData.fetchedAt(c)));
        Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(WidgetData.TRACKER_URL + section));
        v.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(c, provider.hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        Intent refresh = new Intent(c, provider).setAction(ACTION_REFRESH);
        v.setOnClickPendingIntent(R.id.refresh, PendingIntent.getBroadcast(c, provider.hashCode() + 1, refresh,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
    }

    private static void bar(RemoteViews v, int text, int bar, String label, String val, String target, String unit) {
        Double a = num(val), t = num(target);
        if (a == null) { v.setTextViewText(text, label + ": –"); v.setProgressBar(bar, 100, 0, false); return; }
        String s = label + ": " + fmt(a) + (t != null ? " / " + fmt(t) : "") + unit;
        v.setTextViewText(text, s);
        int pct = t != null && t > 0 ? (int) Math.round(Math.min(1.0, a / t) * 100) : 0;
        v.setProgressBar(bar, 100, pct, false);
    }

    /** "Tracker as of 2:41 PM" (when the Tracker last wrote the numbers), or a note when it's old. */
    static String asOf(String updated, long fetched) {
        if (updated == null || updated.isEmpty()) return fetched == 0 ? "Open the app to set up" : "No data yet";
        try {
            Instant t = Instant.parse(updated);
            long mins = Duration.between(t, Instant.now()).toMinutes();
            String ago = mins < 2 ? "just now" : mins < 60 ? mins + " min ago" : mins < 36 * 60 ? (mins / 60) + " h ago" : (mins / 1440) + " days ago";
            return "Tracker " + ago + (mins > 180 ? " · open it to update" : "");
        } catch (Exception e) {
            return "";
        }
    }

    static String dueLabel(String s) {
        try {
            LocalDateTime t = LocalDateTime.parse(s.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            LocalDate today = LocalDate.now();
            String day = t.toLocalDate().equals(today) ? "Today" : t.toLocalDate().equals(today.plusDays(1)) ? "Tomorrow"
                    : t.format(DateTimeFormatter.ofPattern("EEE M/d", Locale.US));
            return day + " " + t.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US));
        } catch (Exception e) {
            return s;
        }
    }

    /** "CHEMICAL ENGRNG THERMODYNAMICS" → "Chem Engrng Thermodynamics", shortened the same way the Tracker does. */
    static String shortClass(String n) {
        String s = n.trim().toLowerCase(Locale.US);
        StringBuilder b = new StringBuilder();
        for (String w : s.split("\\s+")) {
            if (w.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            if (w.matches("i|ii|iii|iv")) b.append(w.toUpperCase(Locale.US));
            else if (w.matches("of|and|to|in|for")) b.append(w);
            else b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return b.toString().replace("Chemical Engrng", "ChemE").replace("Chemistry", "Chem").replace("Thermodynamics", "Thermo")
                .replace("Intro to ", "").replace("Majors ", "");
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

    static String hm(int mins) {
        return mins < 60 ? mins + " min" : (mins / 60) + "h " + (mins % 60) + "m";
    }
}
