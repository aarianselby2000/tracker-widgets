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
        @Override public int getViewTypeCount() { return 1; }
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
                // Typed or photographed on the phone and not estimated yet, then today's foods, newest first.
                for (int i = 0; i < Phone.pendingOf(c, "photo").size(); i++) rows.add(new JSONArray().put("⏳ Photo, read when the Tracker opens").put(-1).put("").put(-1));
                for (JSONObject e : Phone.pendingOf(c, "food")) rows.add(new JSONArray().put("⏳ " + e.optString("text")).put(-1).put(e.optString("hm")).put(-1));
                JSONObject h = w.optJSONObject("h");
                JSONArray f = h != null ? h.optJSONArray("foods") : null;
                for (int i = f == null ? -1 : f.length() - 1; i >= 0; i--) if (f.optJSONArray(i) != null) rows.add(f.optJSONArray(i));
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
            if (kind.equals("acct")) return account(r.optJSONObject(0));
            if (kind.equals("foods")) return food(r);
            return event(r);
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
        RemoteViews account(JSONObject a) {
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
