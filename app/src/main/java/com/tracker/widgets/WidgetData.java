package com.tracker.widgets;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Tracker writes the widgets' numbers to the "Widget" tab of its Google Sheet while it's open.
 * The app reads that tab through the sheet's phone script (a link ending in /exec?k=…, which can also take
 * entries, see Phone), or, for read-only use, through the tab's published CSV link.
 * The rows are key/value pairs; json1, json2, … hold one JSON object (W) with everything the widgets draw.
 */
final class WidgetData {
    static final String PREFS = "tracker_widgets";
    static final String KEY_URL = "csv_url";   // the link the user pasted (the name is from version 1.0)
    static final String TRACKER_URL = "https://claude.ai/artifact/KY8XdEXq4jdQxJ3p2F8DsJ";

    private WidgetData() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String url(Context c) {
        return prefs(c).getString(KEY_URL, "").trim();
    }

    /** The phone script's link can send entries; a published CSV link can only read. */
    static boolean canSend(Context c) {
        return isScript(url(c));
    }

    static boolean isScript(String u) {
        return u.contains("script.google.com/macros/") && Uri.parse(u).getQueryParameter("k") != null;
    }

    /** The last key/value rows downloaded (empty until the first good download). */
    static Map<String, String> load(Context c) {
        SharedPreferences p = prefs(c);
        String rows = p.getString("rows", "");
        if (!rows.isEmpty()) {
            Map<String, String> m = new LinkedHashMap<>();
            try {
                JSONArray a = new JSONArray(rows);
                for (int i = 0; i < a.length(); i++) {
                    JSONArray r = a.optJSONArray(i);
                    if (r != null && r.length() > 0 && !r.optString(0).isEmpty()) m.put(r.optString(0), r.optString(1, ""));
                }
            } catch (Exception ignored) {}
            return m;
        }
        return parse(p.getString("data", ""));
    }

    /** Everything the widgets draw, from the json1… rows (an empty object before Tracker version 67 writes it). */
    static JSONObject w(Context c) {
        try { return new JSONObject(prefs(c).getString("w", "{}")); } catch (Exception e) { return new JSONObject(); }
    }

    static long fetchedAt(Context c) {
        return prefs(c).getLong("fetched_at", 0);
    }

    /** Entry ids still in the sheet's queue at the last download (the Tracker hasn't taken them yet). */
    static List<String> waiting(Context c) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("waiting", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.optString(i));
        } catch (Exception ignored) {}
        return out;
    }

    /** Downloads the Widget tab. Returns null when it worked, or a short reason when it didn't. */
    static String refresh(Context c) {
        String url = url(c);
        if (url.isEmpty()) return "No link yet: open the Tracker Widgets app and paste it.";
        try {
            String body = download(url);
            Map<String, String> m = new LinkedHashMap<>();
            SharedPreferences.Editor ed = prefs(c).edit();
            if (isScript(url)) {
                JSONObject o;
                try { o = new JSONObject(body); } catch (Exception e) { return "That link didn't answer like the phone script. Check that you pasted all of it."; }
                if (!o.optBoolean("ok")) return "The phone script turned the link down (" + o.optString("error", "no reason") + "). Paste the link again.";
                JSONArray rows = o.optJSONArray("rows");
                if (rows == null) rows = new JSONArray();
                for (int i = 0; i < rows.length(); i++) {
                    JSONArray r = rows.optJSONArray(i);
                    if (r != null && r.length() > 0 && !r.optString(0).isEmpty()) m.put(r.optString(0), r.optString(1, ""));
                }
                ed.putString("rows", rows.toString()).remove("data");
                JSONArray wait = o.optJSONArray("waiting");
                ed.putString("waiting", wait == null ? "[]" : wait.toString());
            } else {
                if (body.trim().isEmpty()) return "The link works, but the Widget tab is still empty. Open the Tracker in Claude for a minute so it fills in, then tap ↻.";
                m = parse(body);
                ed.putString("data", body).remove("rows").putString("waiting", "[]");
            }
            if (!m.containsKey("updated"))
                return "The link works, but the Widget tab is still empty. Open the Tracker in Claude for a minute so it fills in, then tap ↻.";
            StringBuilder js = new StringBuilder();
            for (int n = 1; m.containsKey("json" + n); n++) js.append(m.get("json" + n));
            if (js.length() > 0) {
                try { new JSONObject(js.toString()); ed.putString("w", js.toString()); } catch (Exception ignored) {}
            } else ed.putString("w", "{}");
            ed.putLong("fetched_at", System.currentTimeMillis()).apply();
            Phone.prune(c);
            return null;
        } catch (IOException e) {
            return "Couldn't reach Google (" + e.getClass().getSimpleName() + ").";
        }
    }

    static String download(String url) throws IOException {
        for (int hop = 0; hop < 6; hop++) {
            HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(20000);
            con.setReadTimeout(30000);
            con.setInstanceFollowRedirects(false);
            con.setRequestProperty("User-Agent", "TrackerWidgets/1.3");
            int code = con.getResponseCode();
            if (code >= 300 && code < 400) {
                String next = con.getHeaderField("Location");
                con.disconnect();
                if (next == null) throw new IOException("redirect without location");
                url = new URL(new URL(url), next).toString();
                continue;
            }
            if (code != 200) throw new IOException("HTTP " + code);
            try (InputStream in = con.getInputStream()) {
                return readAll(in);
            } finally {
                con.disconnect();
            }
        }
        throw new IOException("too many redirects");
    }

    /** POSTs JSON to the phone script. Apps Script answers with a redirect to the result, which is then fetched. */
    static String post(String url, String json) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(20000);
        con.setReadTimeout(30000);
        con.setInstanceFollowRedirects(false);
        con.setDoOutput(true);
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        con.setRequestProperty("User-Agent", "TrackerWidgets/1.3");
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        con.setFixedLengthStreamingMode(b.length);
        try (OutputStream o = con.getOutputStream()) { o.write(b); }
        int code = con.getResponseCode();
        try {
            if (code >= 300 && code < 400) {
                String next = con.getHeaderField("Location");
                if (next == null) throw new IOException("redirect without location");
                return download(new URL(new URL(url), next).toString());
            }
            if (code != 200) throw new IOException("HTTP " + code);
            try (InputStream in = con.getInputStream()) { return readAll(in); }
        } finally {
            con.disconnect();
        }
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toString(StandardCharsets.UTF_8.name());
    }

    /** Two-column CSV (key,value) to a map, in order. Handles quoted values with commas, quotes and new lines. */
    static Map<String, String> parse(String csv) {
        Map<String, String> m = new LinkedHashMap<>();
        if (csv == null || csv.isEmpty()) return m;
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < csv.length(); i++) {
            char ch = csv.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < csv.length() && csv.charAt(i + 1) == '"') { cell.append('"'); i++; }
                    else quoted = false;
                } else cell.append(ch);
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                row.add(cell.toString()); cell.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n') i++;
                row.add(cell.toString()); cell.setLength(0);
                addRow(m, row); row.clear();
            } else cell.append(ch);
        }
        row.add(cell.toString());
        addRow(m, row);
        return m;
    }

    private static void addRow(Map<String, String> m, List<String> row) {
        if (row.isEmpty()) return;
        String k = row.get(0).trim();
        if (!k.isEmpty()) m.put(k, row.size() > 1 ? row.get(1).trim() : "");
    }

    /** Downloads a file to the given place (used for app updates). */
    static void downloadTo(String url, java.io.File out) throws IOException {
        for (int hop = 0; hop < 6; hop++) {
            HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(20000);
            con.setReadTimeout(60000);
            con.setInstanceFollowRedirects(false);
            con.setRequestProperty("User-Agent", "TrackerWidgets/1.3");
            int code = con.getResponseCode();
            if (code >= 300 && code < 400) {
                String next = con.getHeaderField("Location");
                con.disconnect();
                if (next == null) throw new IOException("redirect without location");
                url = new URL(new URL(url), next).toString();
                continue;
            }
            if (code != 200) throw new IOException("HTTP " + code);
            String type = con.getContentType();
            if (type != null && type.startsWith("text/html")) throw new IOException("got a web page instead of the app file");
            try (InputStream in = con.getInputStream(); java.io.FileOutputStream fo = new java.io.FileOutputStream(out)) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) fo.write(buf, 0, n);
            } finally {
                con.disconnect();
            }
            return;
        }
        throw new IOException("too many redirects");
    }
}
