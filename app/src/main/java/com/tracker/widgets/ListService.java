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
        return new Factory(getApplicationContext(), intent.getStringExtra("kind"));
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

        Factory(Context c, String kind) { this.c = c; this.kind = kind == null ? "" : kind; }

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
            return event(r);
        }

        /** [id, name, class, due "YYYY-MM-DDTHH:MM", "e"|"h", status] like a row in School. */
        RemoteViews assign(JSONArray r) {
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.item_assign);
            String id = r.optString(0), due = r.optString(3), prog = r.optString(5, "todo");
            boolean exam = "e".equals(r.optString(4));
            v.setTextViewText(R.id.name_text, r.optString(1));
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
            v.setTextViewText(R.id.tag_text, tag[0]);
            v.setTextColor(R.id.tag_text, Widgets.parse(tag[1]));
            v.setTextViewText(R.id.what_text, r.optString(2));
            String why = r.optString(3), from = r.optString(4);
            v.setTextViewText(R.id.from_text, from + (why.isEmpty() ? "" : " · " + why));
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
                Widgets.pill(v, p[0], p[1], p[2], b[0], primary ? Widgets.PRIMARY : Widgets.SUNK, primary ? Widgets.ON_PRIMARY : Widgets.INK);
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
            v.setTextViewText(R.id.ev_time, (cur ? "NOW · " : "") + Widgets.clock(s) + " – " + Widgets.clock(e) + (r.optString(5).isEmpty() ? "" : " · " + r.optString(5)));
            v.setTextViewText(R.id.ev_title, (r.optInt(4) == 1 ? "♥ " : "") + r.optString(2));
            v.setTextColor(R.id.ev_title, past ? 0xFF6B7380 : Widgets.INK);
            v.setTextColor(R.id.ev_time, cur ? Widgets.parse(col) : past ? 0xFF5B6471 : Widgets.MUTED);
            return v;
        }
    }
}
