package com.tracker.widgets;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Rows for the scrolling lists: School's assignments, the Inbox's suggestions, and each person's day. */
public class ListService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new Factory(getApplicationContext(), intent.getStringExtra("kind"), intent.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, 0));
    }

    static final Map<String, String> CAT = new HashMap<>();
    static {
        String[] p = {"class", "#6E9FEA", "exam", "#FF6B74", "study", "#3EC2AC", "research", "#A78BF0", "work", "#E77E66", "fitness", "#74C35E",
                "club", "#EE77AE", "meal", "#EDB04E", "routine", "#C9AE7A", "sleep", "#8A94A8", "travel", "#8E97A5", "other", "#8B95A7", "due", "#9C93FF"};
        for (int i = 0; i < p.length; i += 2) CAT.put(p[i], p[i + 1]);
    }
    // Inbox tags, colored like the Tracker's.
    static final Map<String, String[]> TAG = new HashMap<>();
    static {
        TAG.put("money", new String[]{"MONEY", "#55B981"});
        TAG.put("health", new String[]{"HEALTH", "#EF6A6A"});
        TAG.put("school", new String[]{"SCHOOL", "#6B9CEC"});
        TAG.put("schedule", new String[]{"SCHEDULE", "#E8BD34"});
        TAG.put("school_update", new String[]{"SCHOOL CHANGE", "#6B9CEC"});
        TAG.put("schedule_update", new String[]{"SCHEDULE CHANGE", "#E8BD34"});
        TAG.put("note", new String[]{"NOTE", "#9AA4B1"});
    }

    static final int[][] PILLS4 = {{R.id.s0, R.id.s0_bg, R.id.s0_t}, {R.id.s1, R.id.s1_bg, R.id.s1_t}, {R.id.s2, R.id.s2_bg, R.id.s2_t}, {R.id.s3, R.id.s3_bg, R.id.s3_t}};
    static final int[][] PILLS5 = {{R.id.b0, R.id.b0_bg, R.id.b0_t}, {R.id.b1, R.id.b1_bg, R.id.b1_t}, {R.id.b2, R.id.b2_bg, R.id.b2_t}, {R.id.b3, R.id.b3_bg, R.id.b3_t}, {R.id.b4, R.id.b4_bg, R.id.b4_t}};
    // School's two status sets: [value, label, hue].
    static final Object[][] HW = {{"todo", "To do", 4}, {"doing", "Doing", 32}, {"done", "Done", 130}};
    static final Object[][] EXAM = {{"todo", "Not started", 4}, {"doing", "Studying", 32}, {"ready", "Ready", 200}, {"done", "Taken", 130}};

    static class Factory implements RemoteViewsFactory {
        final Context c;
        final String kind;
        final List<JSONArray> rows = new ArrayList<>();

        final int wid;
        float k = 1;

        Factory(Context c, String kind, int wid) { this.c = c; this.kind = kind == null ? "" : kind; this.wid = wid; }

        @Override public void onCreate() {}
        @Override public void onDestroy() {}
        @Override public int getCount() { return rows.size(); }
        @Override public RemoteViews getLoadingView() { return null; }
        @Override public int getViewTypeCount() { return 12; }
        @Override public long getItemId(int i) { return i; }
        @Override public boolean hasStableIds() { return false; }

        @Override
        public void onDataSetChanged() {
            rows.clear();
            k = wid != 0 ? Widgets.scale(c, wid) : 1;
            JSONObject w = WidgetData.w(c);
            if (kind.equals("assign")) {
                JSONArray a = w.optJSONArray("a");
                Map<String, String> set = Phone.pendingProg(c);
                for (int i = 0; a != null && i < a.length(); i++) {
                    JSONArray r = a.optJSONArray(i);
                    if (r == null) continue;
                    try { if (set.containsKey(r.optString(0))) r.put(5, set.get(r.optString(0))); } catch (Exception ignored) {}
                    rows.add(r);
                }
            } else if (kind.equals("foods")) {
                // Today's food (what's waiting from the phone first, then newest first), then RECENT: earlier foods to add again with ＋.
                JSONObject h = w.optJSONObject("h");
                try {
                    for (int i = 0; i < Phone.pendingOf(c, "photo").size(); i++) rows.add(new JSONArray("[\"⏳ Photo\",-1,\"\",-1,-1,-1,\"read when the Tracker opens\",\"\",\"pending\"]"));
                    for (JSONObject e : Phone.pendingOf(c, "food")) {
                        boolean known = e.has("kcal");
                        rows.add(new JSONArray().put("⏳ " + e.optString("text")).put(known ? e.optInt("kcal") : -1).put(e.optString("hm"))
                                .put(known ? e.optInt("p") : -1).put(known ? e.optInt("c") : -1).put(known ? e.optInt("f") : -1)
                                .put(known ? "adding when the Tracker syncs" : "estimated when the Tracker opens").put("").put("pending"));
                    }
                } catch (Exception ignored) {}
                Set<String> today = new HashSet<>();
                JSONArray f = h != null ? h.optJSONArray("foods") : null;
                for (int i = f == null ? -1 : f.length() - 1; i >= 0; i--) {
                    JSONArray x = f.optJSONArray(i);
                    if (x != null) { rows.add(x); today.add(x.optString(0).toLowerCase(java.util.Locale.US)); }
                }
                if (rows.isEmpty()) rows.add(new JSONArray().put("__head").put("Nothing logged yet today"));
                JSONArray rc = h != null ? h.optJSONArray("recent") : null;   // [name, kcal, p, c, f, serving], most recent first
                boolean head = false;
                for (int i = 0; rc != null && i < rc.length(); i++) {
                    JSONArray x = rc.optJSONArray(i);
                    if (x == null || today.contains(x.optString(0).toLowerCase(java.util.Locale.US))) continue;
                    if (!head) { rows.add(new JSONArray().put("__head").put("Recent  ·  ＋ adds it again")); head = true; }
                    rows.add(new JSONArray().put(x.optString(0)).put(x.optInt(1)).put("").put(x.optInt(2)).put(x.optInt(3)).put(x.optInt(4))
                            .put(x.optString(5)).put("").put("recent"));
                }
            } else if (kind.equals("cal")) {
                // The Calendar widget's list, as Extras.calendar laid it out.
                try {
                    JSONArray a = new JSONArray(WidgetData.prefs(c).getString("cal_rows_" + wid, "[]"));
                    for (int i = 0; i < a.length(); i++) if (a.optJSONArray(i) != null) rows.add(a.optJSONArray(i));
                } catch (Exception ignored) {}
            } else if (kind.equals("steps")) {
                // The Steps widget's past days, newest first (today is at the top of the widget, so it's left out).
                try {
                    JSONArray days = new JSONArray(WidgetData.prefs(c).getString("steps", "[]"));
                    int last = days.length() - 1;
                    if (last >= 0 && java.time.LocalDate.now().toString().equals(days.getJSONArray(last).optString(0))) last--;
                    for (int i = last; i >= 0; i--) rows.add(days.getJSONArray(i));
                } catch (Exception ignored) {}
            } else if (kind.startsWith("pages:")) {
                JSONObject pg = w.optJSONObject("pages");
                JSONArray a = pg != null ? pg.optJSONArray(kind.substring(6)) : null;
                for (int i = 0; a != null && i < a.length(); i++) if (a.optJSONObject(i) != null) rows.add(new JSONArray().put(a.optJSONObject(i)));
            } else if (kind.equals("health")) {
                for (String page : new String[]{"today", "log", "week"}) rows.add(new JSONArray().put(page));
            } else if (kind.equals("days")) {
                JSONObject sd = w.optJSONObject("s");
                if (sd != null) {
                    try { rows.add(new JSONArray().put(new JSONObject().put("date", sd.optString("date")).put("me", sd.optJSONArray("me")).put("mi", sd.optJSONArray("mi")))); } catch (Exception ignored) {}
                    JSONArray days = sd.optJSONArray("days");
                    for (int i = 0; days != null && i < days.length(); i++) if (days.optJSONObject(i) != null) rows.add(new JSONArray().put(days.optJSONObject(i)));
                }
            } else if (kind.equals("acct")) {
                JSONObject m = w.optJSONObject("m");
                JSONArray a = m != null ? m.optJSONArray("acc") : null;
                for (int i = 0; a != null && i < a.length(); i++) if (a.optJSONObject(i) != null) rows.add(new JSONArray().put(a.optJSONObject(i)));
            } else if (kind.equals("inbox")) {
                JSONArray a = w.optJSONArray("i");
                Set<String> done = Phone.pendingSugs(c);
                for (int i = 0; a != null && i < a.length(); i++) {
                    JSONArray r = a.optJSONArray(i);
                    if (r != null && !done.contains(r.optString(0))) rows.add(r);
                }
            } else {
                JSONObject s = w.optJSONObject("s");
                JSONArray a = s != null ? s.optJSONArray(kind) : null;
                for (int i = 0; a != null && i < a.length(); i++) if (a.optJSONArray(i) != null) rows.add(a.optJSONArray(i));
            }
        }

        @Override
        public RemoteViews getViewAt(int pos) {
            if (pos >= rows.size()) return null;
            JSONArray r = rows.get(pos);
            if (kind.equals("assign")) return assign(r);
            if (kind.equals("inbox")) return inbox(r);
            if (kind.equals("acct")) return account(r.optJSONObject(0), pos);
            if (kind.equals("foods")) return foodRow(r);
            if (kind.equals("steps")) return stepDay(r);
            if (kind.equals("cal")) return Pages.row(c, r, k);
            if (kind.equals("health")) return healthPage(r.optString(0));
            if (kind.equals("days")) return dayPage(r.optJSONObject(0), pos, rows.size());
            return event(r);
        }

        /** Space left for a page: the widget's size minus its header, buttons and the page's own title, in dp. */
        int[] pageDp(int reserved) {
            int[] sz = Widgets.sizeDp(c, wid);
            return new int[]{Math.max(150, sz[0] - 46), Math.max(90, sz[1] - Math.round(reserved * k))};
        }

        /** One day, drawn like the Tracker's Schedule grid (today with the now line). */
        RemoteViews dayPage(JSONObject day, int pos, int count) {
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.item_day);
            java.time.LocalDate date;
            try { date = java.time.LocalDate.parse(day.optString("date")); } catch (Exception e) { date = java.time.LocalDate.now(); }
            boolean today = date.equals(java.time.LocalDate.now());
            String name = today ? "TODAY" : date.equals(java.time.LocalDate.now().plusDays(1)) ? "TOMORROW" : date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE", java.util.Locale.US)).toUpperCase(java.util.Locale.US);
            v.setTextViewText(R.id.page_title, name + " · " + date.format(java.time.format.DateTimeFormatter.ofPattern("EEE M/d", java.util.Locale.US)).toUpperCase(java.util.Locale.US));
            v.setTextViewText(R.id.page_hint, (pos + 1) + "/" + count + "  swipe ↕");
            Widgets.ts(v, R.id.page_title, 14, k);
            int now = java.time.LocalTime.now().getHour() * 60 + java.time.LocalTime.now().getMinute();
            int[] p = pageDp(190);
            float dens = Math.min(1.5f, c.getResources().getDisplayMetrics().density);
            v.setImageViewBitmap(R.id.day_img, Timeline.draw(day.optJSONArray("me"), day.optJSONArray("mi"), today ? now : -1,
                    Math.round(p[0] * dens), Math.round(p[1] * dens), dens, k));
            v.setOnClickFillInIntent(R.id.page, new Intent());
            return v;
        }

        /** Health's pages: Today (food bars), Food log, This week. */
        RemoteViews healthPage(String page) {
            JSONObject w = WidgetData.w(c), h = w.optJSONObject("h");
            java.util.Map<String, String> d = WidgetData.load(c);
            RemoteViews v;
            if (page.equals("log")) {
                v = new RemoteViews(c.getPackageName(), R.layout.item_health_log);
                v.setTextViewText(R.id.page_title, "FOOD LOG");
                v.setTextViewText(R.id.page_hint, "2/3  swipe ↕");
                android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder();
                for (int i = 0; i < Phone.pendingOf(c, "photo").size(); i++) logLine(b, "", "⏳ Photo, read when the Tracker opens", -1, -1);
                for (JSONObject e : Phone.pendingOf(c, "food")) logLine(b, e.optString("hm"), "⏳ " + e.optString("text"), -1, -1);
                JSONArray f = h != null ? h.optJSONArray("foods") : null;
                int total = 0;
                for (int i = f == null ? -1 : f.length() - 1; i >= 0; i--) {
                    JSONArray x = f.optJSONArray(i);
                    if (x == null) continue;
                    logLine(b, x.optString(2), x.optString(0), x.optInt(1, -1), x.optInt(3, -1));
                    total += Math.max(0, x.optInt(1, 0));
                }
                v.setTextViewText(R.id.log_text, b.length() == 0 ? "Nothing logged today yet." : b);
                v.setTextViewText(R.id.log_total, f == null || f.length() == 0 ? "" : f.length() + (f.length() == 1 ? " food" : " foods") + " · " + Widgets.fmt(total) + " kcal");
                Widgets.ts(v, R.id.log_text, 12.5f, k);
                Widgets.ts(v, R.id.log_total, 11, k);
            } else if (page.equals("week")) {
                v = new RemoteViews(c.getPackageName(), R.layout.item_health_week);
                v.setTextViewText(R.id.page_title, "THIS WEEK");
                v.setTextViewText(R.id.page_hint, "3/3  swipe ↕");
                int[] p = pageDp(170);
                float dens = Math.min(1.5f, c.getResources().getDisplayMetrics().density);
                v.setImageViewBitmap(R.id.week_img, WeekChart.draw(h != null ? h.optJSONArray("week") : null, Math.round(p[0] * dens), Math.round(p[1] * dens), dens, k));
            } else {
                v = new RemoteViews(c.getPackageName(), R.layout.item_health_today);
                v.setTextViewText(R.id.page_title, "TODAY");
                v.setTextViewText(R.id.page_hint, "1/3  swipe ↕");
                Double kcal = h != null ? Widgets.opt(h, "kcal") : Widgets.num(d.get("kcal")), kcalT = h != null ? Widgets.opt(h, "kcalT") : Widgets.num(d.get("kcal_target"));
                Double p = h != null ? Widgets.opt(h, "p") : Widgets.num(d.get("protein")), pT = h != null ? Widgets.opt(h, "pT") : Widgets.num(d.get("protein_target"));
                Double water = h != null ? Widgets.opt(h, "water") : Widgets.num(d.get("water_oz")), waterG = h != null ? Widgets.opt(h, "waterG") : Widgets.num(d.get("water_goal_oz"));
                double addW = Phone.pendingWater(c);
                if (addW > 0) water = (water == null ? 0 : water) + addW;
                Double carbs = h != null ? Widgets.opt(h, "c") : null, carbsT = h != null ? Widgets.opt(h, "cT") : null;
                Double fat = h != null ? Widgets.opt(h, "f") : null, fatT = h != null ? Widgets.opt(h, "fT") : null;
                Widgets.macro(v, R.id.bar_k_b, R.id.bar_k_g, R.id.bar_k_o, R.id.val_k, kcal, kcalT, "kcal", "kcal");
                Widgets.macro(v, R.id.bar_p_b, R.id.bar_p_g, R.id.bar_p_o, R.id.val_p, p, pT, "g", "p");
                Widgets.macro(v, R.id.bar_c_b, R.id.bar_c_g, R.id.bar_c_o, R.id.val_c, carbs, carbsT, "g", "c");
                Widgets.macro(v, R.id.bar_f_b, R.id.bar_f_g, R.id.bar_f_o, R.id.val_f, fat, fatT, "g", "f");
                v.setProgressBar(R.id.bar_w_b, 100, water != null && waterG != null && waterG > 0 ? (int) Math.round(Math.min(1, water / waterG) * 100) : 0, false);
                v.setTextViewText(R.id.val_w, Widgets.amount(water, waterG, "oz"));
                String left = "";
                if (kcal != null && kcalT != null && kcalT > 0)
                    left = kcal < kcalT ? Widgets.fmt(kcalT - kcal) + " kcal and " + Widgets.fmt(Math.max(0, (pT == null ? 0 : pT) - (p == null ? 0 : p))) + " g protein left" : "Calorie target reached";
                v.setTextViewText(R.id.left_text, left);
                Double caf = h != null ? Widgets.opt(h, "caf") : Widgets.num(d.get("caffeine_mg"));
                v.setTextViewText(R.id.caf_text, "☕ " + (caf == null ? "–" : Widgets.fmt(caf)) + " / 400 mg caffeine");
                for (int id : new int[]{R.id.lbl_k, R.id.lbl_p, R.id.lbl_c, R.id.lbl_f, R.id.lbl_w}) Widgets.ts(v, id, 12.5f, k);
                for (int id : new int[]{R.id.val_k, R.id.val_p, R.id.val_c, R.id.val_f, R.id.val_w}) Widgets.ts(v, id, 11.5f, k);
                Widgets.ts(v, R.id.left_text, 11, k);
                Widgets.ts(v, R.id.caf_text, 11, k);
            }
            Widgets.ts(v, R.id.page_title, 14, k);
            v.setOnClickFillInIntent(R.id.page, new Intent());
            return v;
        }

        /** "8:05a  2 eggs and toast  320 kcal · 18 g P" */
        void logLine(android.text.SpannableStringBuilder b, String at, String name, int kcal, int p) {
            if (b.length() > 0) b.append('\n');
            String t = "";
            if (at != null && at.matches("\\d{1,2}:\\d{2}")) { String[] q = at.split(":"); t = Widgets.clock(Integer.parseInt(q[0]) * 60 + Integer.parseInt(q[1])).replace(" AM", "a").replace(" PM", "p"); }
            int s0 = b.length();
            b.append(t.isEmpty() ? "" : t + "  ");
            b.setSpan(new android.text.style.ForegroundColorSpan(Widgets.MUTED), s0, b.length(), 0);
            b.append(name);
            if (kcal >= 0) {
                b.append("  ");
                int s1 = b.length();
                b.append(Widgets.fmt(kcal));
                b.setSpan(new android.text.style.ForegroundColorSpan(0xFFF0A040), s1, b.length(), 0);
                b.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), s1, b.length(), 0);
            }
            if (p > 0) {
                int s2 = b.length();
                b.append(" · " + p + " g P");
                b.setSpan(new android.text.style.ForegroundColorSpan(0xFF6B9CEC), s2, b.length(), 0);
            }
        }

        /** One past day in the Steps widget: [date, steps (-1 = no data), …] as a bar toward the goal. */
        RemoteViews stepDay(JSONArray x) {
            int goal = Extras.goal(c);
            long s = x.optLong(1, -1);
            java.time.LocalDate dd = java.time.LocalDate.parse(x.optString(0));
            String lab = dd.equals(java.time.LocalDate.now().minusDays(1)) ? "Yesterday" : dd.format(java.time.format.DateTimeFormatter.ofPattern("EEE M/d", java.util.Locale.US));
            if (s < 0) return Pages.row(c, new JSONArray().put("bar").put(lab).put("no data").put(0).put("b"), k);
            return Pages.row(c, new JSONArray().put("bar").put(lab).put(String.format(java.util.Locale.US, "%,d", s))
                    .put((int) Math.min(100, s * 100 / goal)).put(s >= goal ? "g" : "b"), k);
        }

        /** A food card with ＋ (add it again), or a section heading. Pending entries have no ＋. */
        RemoteViews foodRow(JSONArray r) {
            if ("__head".equals(r.optString(0))) {
                RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.item_food_head);
                v.setTextViewText(R.id.fh_text, r.optString(1).toUpperCase(java.util.Locale.US));
                Widgets.ts(v, R.id.fh_text, 12, k);
                v.setOnClickFillInIntent(R.id.fh_text, new Intent());
                return v;
            }
            RemoteViews v = Widgets.foodCard(c, r, k);
            boolean pending = "pending".equals(r.optString(8));
            v.setOnClickFillInIntent(R.id.fc_root, new Intent());   // a tap on the card itself does nothing
            v.setViewVisibility(R.id.fc_add, pending ? View.GONE : View.VISIBLE);
            if (!pending) {
                v.setOnClickFillInIntent(R.id.fc_add, new Intent().putExtra("op", "food").putExtra("again", r.optString(0))
                        .putExtra("kcal", String.valueOf(Math.max(0, r.optInt(1)))).putExtra("p", String.valueOf(Math.max(0, r.optInt(3))))
                        .putExtra("c", String.valueOf(Math.max(0, r.optInt(4)))).putExtra("f", String.valueOf(Math.max(0, r.optInt(5)))));
                Widgets.ts(v, R.id.fc_add, 20, k);
            }
            return v;
        }

        /** [name, kcal, "HH:MM", protein g] like a row in the Tracker's food log. */
        RemoteViews food(JSONArray r) {
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.item_food);
            String at = r.optString(2);
            String t = "";
            if (at.matches("\\d{1,2}:\\d{2}")) { String[] p = at.split(":"); t = Widgets.clock(Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1])); }
            v.setTextViewText(R.id.food_time, t.replace(" AM", "a").replace(" PM", "p"));
            v.setTextViewText(R.id.food_name, r.optString(0));
            int kc = r.optInt(1, -1), pr = r.optInt(3, -1);
            v.setTextViewText(R.id.food_kcal, kc >= 0 ? Widgets.fmt(kc) : "");
            v.setTextViewText(R.id.food_p, pr > 0 ? pr + " g P" : "");
            Widgets.ts(v, R.id.food_time, 10.5f, k);
            Widgets.ts(v, R.id.food_name, 12, k);
            Widgets.ts(v, R.id.food_kcal, 12, k);
            Widgets.ts(v, R.id.food_p, 10.5f, k);
            v.setOnClickFillInIntent(R.id.food_row, new Intent());
            return v;
        }

        /** One account card (Checking, Savings or Cash), like its panel in the Tracker's Money section. */
        RemoteViews account(JSONObject a, int pos) {
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.item_account);
            String k = a.optString("k");
            int color = k.equals("savings") ? 0xFF6B9CEC : k.equals("cash") ? 0xFFE8BD34 : 0xFF55B981;
            v.setTextViewText(R.id.acct_name, a.optString("name").toUpperCase(java.util.Locale.US));
            Widgets.ts(v, R.id.acct_name, 14, this.k);
            Widgets.ts(v, R.id.acct_bal, 30, this.k);
            Widgets.ts(v, R.id.acct_note, 11, this.k);
            Widgets.ts(v, R.id.acct_recent, 11.5f, this.k);
            Widgets.ts(v, R.id.acct_pill, 11, this.k);
            v.setTextColor(R.id.acct_name, color);
            v.setOnClickFillInIntent(R.id.acct_card, new Intent());
            // Entries added on the phone that the Tracker hasn't applied yet move the balance here too.
            android.text.SpannableStringBuilder rec = new android.text.SpannableStringBuilder();
            double pend = 0;
            for (JSONObject e : Phone.pendingOf(c, "money")) {
                double amt = e.optDouble("amount", 0), eff = effect(k, e.optString("dir"), e.optString("type"), e.optString("card"), amt);
                if (eff == 0) continue;
                pend += eff;
                recLine(rec, "⏳ " + e.optString("source"), eff, e.optString("type"));
            }
            boolean has = !a.isNull("bal") && a.has("bal");
            double bal = a.optDouble("bal", 0) + pend;
            v.setTextViewText(R.id.acct_bal, has ? (pend != 0 ? "≈ " : "") + Widgets.dollars(bal) : "–");
            String note = a.optString("note");
            int bar = -1, pct = 0;
            if (k.equals("checking") && has) {
                double min = a.optDouble("min", 0), buf = a.optDouble("buffer", 0), room = bal - min;
                String st = pend == 0 ? a.optString("st") : room < 0 ? "under" : room <= buf ? "close" : "ok";
                bar = st.equals("under") ? R.id.acct_bar_r : st.equals("close") ? R.id.acct_bar_a : R.id.acct_bar_g;
                v.setTextColor(R.id.acct_bal, st.equals("under") ? 0xFFF07070 : st.equals("close") ? 0xFFF0A040 : 0xFF55B981);
                pct = room <= 0 ? 0 : (int) Math.round(Math.min(1, room / Math.max(min * 2, 200)) * 100);
                if (pend != 0) note = room < 0 ? Widgets.dollars(-room) + " below your minimum" : Widgets.dollars(room) + " of room above your minimum";
            } else if (k.equals("savings") && has) {
                double goal = a.optDouble("goal", 0), total = a.optDouble("total", 0);
                if (goal > 0) { bar = R.id.acct_bar_b; pct = (int) Math.round(Math.max(0, Math.min(1, bal / goal)) * 100); }
                if (total != 0) {
                    double p = a.optDouble("pct", Double.NaN);
                    v.setTextViewText(R.id.acct_pill, (total >= 0 ? "▲ " : "▼ ") + Widgets.dollars(Math.abs(total)) + (Double.isNaN(p) ? "" : String.format(java.util.Locale.US, " (%s%.1f%%)", total >= 0 ? "+" : "−", Math.abs(p))));
                    v.setTextColor(R.id.acct_pill, total >= 0 ? 0xFF55B981 : 0xFFF07070);
                    v.setViewVisibility(R.id.acct_pill, View.VISIBLE);
                }
            } else if (k.equals("cash") && has && bal < 0) v.setTextColor(R.id.acct_bal, 0xFFF07070);
            v.setTextViewText(R.id.acct_note, note);
            for (int id : new int[]{R.id.acct_bar_g, R.id.acct_bar_a, R.id.acct_bar_r, R.id.acct_bar_b}) v.setViewVisibility(id, id == bar ? View.VISIBLE : View.GONE);
            v.setViewVisibility(R.id.acct_bars, bar == -1 ? View.GONE : View.VISIBLE);
            if (bar != -1) v.setProgressBar(bar, 100, pct, false);
            JSONArray r = a.optJSONArray("recent");
            for (int i = 0; r != null && i < r.length(); i++) {
                JSONArray x = r.optJSONArray(i);
                if (x != null) recLine(rec, x.optString(1), x.optDouble(2, 0), x.optString(3) + " · " + x.optString(0));
            }
            v.setTextViewText(R.id.acct_recent, rec.length() == 0 ? "No entries yet" : rec);
            String asOf = a.optString("asOf");
            v.setTextViewText(R.id.acct_asof, asOf.length() == 10 ? (k.equals("cash") ? "Counted " : "From your bank ") + Integer.parseInt(asOf.substring(5, 7)) + "/" + Integer.parseInt(asOf.substring(8)) + ", plus entries since" : "");
            return v;
        }

        /** How a phone entry changes this account (the Tracker's ckEffect, svEffect and cashEffect). */
        static double effect(String k, String dir, String type, String card, double amt) {
            amt = Math.abs(amt);
            if (k.equals("savings")) return type.equals("To savings") ? amt : type.equals("From savings") ? -amt : 0;
            if (k.equals("cash")) {
                if (type.equals("ATM withdrawal")) return amt;
                if (type.equals("Cash deposit")) return -amt;
                if (dir.equals("move")) return 0;
                return card.equals("Cash") ? (dir.equals("in") ? amt : -amt) : 0;
            }
            if (type.equals("To savings") || type.equals("ATM withdrawal")) return -amt;
            if (type.equals("From savings") || type.equals("Cash deposit")) return amt;
            if (dir.equals("move")) return 0;
            if (dir.equals("in")) return card.equals("Cash") ? 0 : amt;
            return card.equals("Self") ? -amt : 0;
        }

        static void recLine(android.text.SpannableStringBuilder b, String name, double amt, String rest) {
            if (b.length() > 0) b.append('\n');
            int s0 = b.length();
            b.append(name);
            b.setSpan(new android.text.style.ForegroundColorSpan(Widgets.INK), s0, b.length(), 0);
            b.append("  ");
            int s1 = b.length();
            b.append(Widgets.signed(amt));
            b.setSpan(new android.text.style.ForegroundColorSpan(amt < 0 ? 0xFFF07070 : 0xFF55B981), s1, b.length(), 0);
            b.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), s1, b.length(), 0);
            if (rest != null && !rest.isEmpty()) b.append("  ").append(rest);
        }

        /** [id, name, class, due "YYYY-MM-DDTHH:MM", "e"|"h", status] like a row in School. */
        RemoteViews assign(JSONArray r) {
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.item_assign);
            String id = r.optString(0), due = r.optString(3), prog = r.optString(5, "todo");
            boolean exam = "e".equals(r.optString(4));
            v.setTextViewText(R.id.name_text, r.optString(1));
            Widgets.ts(v, R.id.name_text, 13, k);
            Widgets.ts(v, R.id.cls_text, 11, k);
            for (int tid : new int[]{R.id.chip_t, R.id.s0_t, R.id.s1_t, R.id.s2_t, R.id.s3_t}) Widgets.ts(v, tid, 11, k);
            String cls = Widgets.shortClass(r.optString(2));
            v.setTextViewText(R.id.cls_text, cls + (due.isEmpty() ? "" : (cls.isEmpty() ? "" : " · ") + Widgets.dueLabel(due)) + (exam ? " · exam" : ""));
            // The time-left chip, colored by how close it is.
            if (due.isEmpty()) Widgets.pill(v, R.id.chip, R.id.chip_bg, R.id.chip_t, exam ? "Date TBA" : "No date", Widgets.SUNK, Widgets.MUTED);
            else {
                long ms;
                try { ms = LocalDateTime.parse(due).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - System.currentTimeMillis(); }
                catch (Exception e) { ms = Long.MAX_VALUE / 2; }
                if (ms <= 0 && exam) Widgets.pill(v, R.id.chip, R.id.chip_bg, R.id.chip_t, "Happening now", Widgets.chipBg(280), Widgets.chipFg(280));
                else if (ms <= 0) Widgets.pill(v, R.id.chip, R.id.chip_bg, R.id.chip_t, "Overdue " + Widgets.fmtSpan(ms), Widgets.OVERDUE_BG, 0xFFFFFFFF);
                else { int h = Widgets.bandHue(ms); Widgets.pill(v, R.id.chip, R.id.chip_bg, R.id.chip_t, Widgets.fmtSpan(ms), Widgets.chipBg(h), Widgets.chipFg(h)); }
            }
            Object[][] opts = exam ? EXAM : HW;
            for (int i = 0; i < PILLS4.length; i++) {
                int[] p = PILLS4[i];
                if (i >= opts.length) { v.setViewVisibility(p[0], View.GONE); continue; }
                String val = (String) opts[i][0];
                int h = (Integer) opts[i][2];
                boolean on = val.equals(prog) || ("ready".equals(prog) && !exam && "doing".equals(val));
                Widgets.pill(v, p[0], p[1], p[2], (String) opts[i][1], on ? Widgets.chipBg(h) : Widgets.SUNK, on ? Widgets.chipFg(h) : Widgets.MUTED);
                v.setOnClickFillInIntent(p[0], new Intent().putExtra("op", "prog").putExtra("id", id).putExtra("v", val));
            }
            return v;
        }

        /** [key, type, text, why, from, isUpdate, needsCard] like a suggestion in the Inbox. */
        RemoteViews inbox(JSONArray r) {
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.item_inbox);
            String key = r.optString(0), type = r.optString(1);
            String[] tag = TAG.containsKey(type) ? TAG.get(type) : new String[]{type.toUpperCase(), "#9AA4B1"};
            int tc = Widgets.parse(tag[1]);
            Widgets.pill(v, R.id.tag, R.id.tag_bg, R.id.tag_t, tag[0], tc, tc);
            v.setInt(R.id.tag_bg, "setImageAlpha", 60);
            v.setTextViewText(R.id.what_text, r.optString(2));
            Widgets.ts(v, R.id.what_text, 12.5f, k);
            Widgets.ts(v, R.id.from_text, 10.5f, k);
            Widgets.ts(v, R.id.why_text, 10.5f, k);
            for (int id : new int[]{R.id.tag_t, R.id.b0_t, R.id.b1_t, R.id.b2_t, R.id.b3_t, R.id.b4_t}) Widgets.ts(v, id, 11, k);
            String why = r.optString(3), from = r.optString(4);
            v.setTextViewText(R.id.from_text, "  " + from);
            v.setTextViewText(R.id.why_text, why);
            v.setViewVisibility(R.id.why_text, why.isEmpty() ? View.GONE : View.VISIBLE);
            List<String[]> btns = new ArrayList<>();   // label, do, card
            if (r.optInt(6) == 1) {
                btns.add(new String[]{"My card", "add", "Self"});
                btns.add(new String[]{"Mom", "add", "Mom"});
                btns.add(new String[]{"Dad", "add", "Dad"});
                btns.add(new String[]{"Cash", "add", "Cash"});
                btns.add(new String[]{"Skip", "skip", ""});
            } else if ("note".equals(type)) btns.add(new String[]{"Got it", "skip", ""});
            else {
                btns.add(new String[]{r.optInt(5) == 1 ? "Apply" : "Add", "add", ""});
                btns.add(new String[]{"Skip", "skip", ""});
            }
            for (int i = 0; i < PILLS5.length; i++) {
                int[] p = PILLS5[i];
                if (i >= btns.size()) { v.setViewVisibility(p[0], View.GONE); continue; }
                String[] b = btns.get(i);
                boolean primary = "add".equals(b[1]) && (i == 0 || r.optInt(6) == 1);
                Widgets.pill(v, p[0], p[1], p[2], b[0], primary ? tc : 0xFF2E3640, primary ? Widgets.ON_PRIMARY : Widgets.INK);
                v.setOnClickFillInIntent(p[0], new Intent().putExtra("op", "sug").putExtra("key", key).putExtra("do", b[1]).putExtra("card", b[2])
                        .putExtra("label", r.optString(2)));
            }
            return v;
        }

        /** [start, end, title, category, shared, place] (minutes since midnight). */
        RemoteViews event(JSONArray r) {
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.item_event);
            int s = r.optInt(0), e = r.optInt(1), now = LocalTime.now().getHour() * 60 + LocalTime.now().getMinute();
            int end = e <= s ? e + 1440 : e;
            boolean past = end <= now, cur = s <= now && now < end;
            String col = CAT.containsKey(r.optString(3)) ? CAT.get(r.optString(3)) : "#8B95A7";
            v.setInt(R.id.ev_bar, "setColorFilter", Widgets.parse(col));
            v.setInt(R.id.ev_bg, "setColorFilter", Widgets.parse(col));
            v.setInt(R.id.ev_bg, "setImageAlpha", past ? 22 : cur ? 80 : 45);
            v.setInt(R.id.ev_bar, "setImageAlpha", past ? 110 : 255);
            v.setTextViewText(R.id.ev_time, (cur ? "NOW · " : "") + Widgets.clock(s) + " – " + Widgets.clock(e) + (r.optString(5).isEmpty() ? "" : " · " + r.optString(5)));
            v.setTextViewText(R.id.ev_title, (r.optInt(4) == 1 ? "♥ " : "") + r.optString(2));
            v.setTextColor(R.id.ev_title, past ? 0xFF6B7380 : Widgets.INK);
            v.setTextColor(R.id.ev_time, cur ? Widgets.parse(col) : past ? 0xFF5B6471 : 0xFFB4BCC8);
            return v;
        }
    }
}
