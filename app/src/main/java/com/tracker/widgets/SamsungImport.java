package com.tracker.widgets;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Primes the Steps rings from a Samsung Health export (Samsung Health → Settings → Download personal data), read on the phone:
 * the day summary file (com.samsung.shealth.activity.day_summary…csv: steps, active time, activity calories) or the daily
 * steps file (com.samsung.shealth.step_daily_trend…csv). Keeps the last 30 days as {date: [steps, active minutes, kcal]};
 * nothing leaves the phone.
 */
final class SamsungImport {
    private SamsungImport() {}

    static String run(Context c, Uri uri) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getContentResolver().openInputStream(uri), StandardCharsets.UTF_8))) {
            String l;
            while ((l = r.readLine()) != null && lines.size() < 300000) lines.add(l);
        } catch (Exception e) {
            return "Couldn't open that file (" + e.getClass().getSimpleName() + ").";
        }
        // Samsung's files start with a line naming the data type, then the column names.
        int hi = -1;
        for (int i = 0; i < Math.min(6, lines.size()); i++) if (lines.get(i).contains("day_time")) { hi = i; break; }
        if (hi < 0) return "That doesn't look like Samsung Health's day summary. In the export folder, pick the file that starts with com.samsung.shealth.activity.day_summary.";
        List<String> head = split(lines.get(hi));
        Map<String, Integer> col = new HashMap<>();
        for (int i = 0; i < head.size(); i++) {
            String h = head.get(i).trim().toLowerCase(Locale.US);
            h = h.substring(h.lastIndexOf('.') + 1);   // some columns are written as com.samsung.health.xxx.name
            if (!col.containsKey(h)) col.put(h, i);
        }
        Integer cDay = col.get("day_time"), cSteps = col.containsKey("step_count") ? col.get("step_count") : col.get("count");
        Integer cAct = col.get("active_time"), cKcal = col.get("calorie"), cSrc = col.get("source_type");
        if (cDay == null || cSteps == null) return "That file has no daily steps. Pick the one that starts with com.samsung.shealth.activity.day_summary.";
        // The daily steps file has a row per device plus one for all of them together (source_type -2): use that one.
        boolean merged = false;
        if (cSrc != null) for (int i = hi + 1; i < lines.size() && !merged; i++) merged = "-2".equals(get(split(lines.get(i)), cSrc));
        LocalDate today = LocalDate.now(), from = today.minusDays(29);
        Map<String, long[]> days = new TreeMap<>();
        for (int i = hi + 1; i < lines.size(); i++) {
            List<String> f = split(lines.get(i));
            if (merged && !"-2".equals(get(f, cSrc))) continue;
            LocalDate d = day(get(f, cDay));
            if (d == null || d.isBefore(from) || d.isAfter(today)) continue;
            long s = num(get(f, cSteps)), m = cAct == null ? -1 : minutes(num(get(f, cAct))), k = cKcal == null ? -1 : num(get(f, cKcal));
            long[] o = days.get(d.toString());
            if (o == null) o = new long[]{-1, -1, -1};
            o[0] = Math.max(o[0], s);
            o[1] = Math.max(o[1], m);
            o[2] = Math.max(o[2], k);
            days.put(d.toString(), o);
        }
        if (days.isEmpty()) return "That file has nothing from the last 30 days. Export again from Samsung Health and pick the newest file.";
        JSONObject out = new JSONObject();
        try { for (Map.Entry<String, long[]> e : days.entrySet()) out.put(e.getKey(), new JSONArray().put(e.getValue()[0]).put(e.getValue()[1]).put(e.getValue()[2])); }
        catch (Exception e) { return "Couldn't save the import."; }
        WidgetData.prefs(c).edit().putString("steps_import", out.toString()).putLong("steps_import_at", System.currentTimeMillis())
                .putInt("steps_import_n", days.size()).apply();
        return "✓ Primed with " + days.size() + " days from Samsung Health" + (cAct == null ? " (steps and calories: this file has no active time)" : "") + ".";
    }

    private static String get(List<String> f, Integer i) { return i == null || i >= f.size() ? "" : f.get(i).trim(); }

    private static long num(String v) {
        try { return v.isEmpty() ? -1 : Math.round(Double.parseDouble(v)); } catch (Exception e) { return -1; }
    }

    /** Samsung keeps active time in milliseconds; a small number is already minutes. */
    private static long minutes(long v) { return v < 0 ? -1 : v > 1440 ? Math.round(v / 60000.0) : v; }

    /** day_time is the day's start in UTC milliseconds (or, in some exports, "2026-11-10 00:00:00.000"). */
    private static LocalDate day(String v) {
        try {
            if (v.matches("\\d{10,}")) return Instant.ofEpochMilli(Long.parseLong(v)).atZone(ZoneOffset.UTC).toLocalDate();
            if (v.length() >= 10) return LocalDate.parse(v.substring(0, 10));
        } catch (Exception ignored) {}
        return null;
    }

    /** One CSV line into fields (quotes allowed). */
    private static List<String> split(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder b = new StringBuilder();
        boolean q = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') { if (q && i + 1 < line.length() && line.charAt(i + 1) == '"') { b.append('"'); i++; } else q = !q; }
            else if (ch == ',' && !q) { out.add(b.toString()); b.setLength(0); }
            else b.append(ch);
        }
        out.add(b.toString());
        return out;
    }
}
