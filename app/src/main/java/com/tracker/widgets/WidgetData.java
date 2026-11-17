package com.tracker.widgets;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Tracker writes key/value rows to the "Widget" tab of its Google Sheet while it's open.
 * That tab is published to the web as CSV; this class downloads it, keeps the last good copy,
 * and hands the values to the widgets.
 */
final class WidgetData {
    static final String PREFS = "tracker_widgets";
    static final String KEY_URL = "csv_url";
    static final String TRACKER_URL = "https://claude.ai/artifact/KY8XdEXq4jdQxJ3p2F8DsJ";

    private WidgetData() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The last values downloaded (empty until the first good download). */
    static Map<String, String> load(Context c) {
        return parse(prefs(c).getString("data", ""));
    }

    static long fetchedAt(Context c) {
        return prefs(c).getLong("fetched_at", 0);
    }

    /** Downloads the published tab. Returns null when it worked, or a short reason when it didn't. */
    static String refresh(Context c) {
        String url = prefs(c).getString(KEY_URL, "").trim();
        if (url.isEmpty()) return "No sheet link yet: open the Tracker Widgets app and paste it.";
        try {
            String csv = download(url);
            if (csv.trim().isEmpty())
                return "The link works, but the Widget tab is still empty. Open the Tracker in Claude for a minute (phone or laptop) so it fills in, then tap ↻.";
            Map<String, String> m = parse(csv);
            if (!m.containsKey("updated")) {
                return "That link doesn't show the Widget tab as CSV. Publish the Widget tab as CSV and paste that link.";
            }
            prefs(c).edit().putString("data", csv).putLong("fetched_at", System.currentTimeMillis()).apply();
            return null;
        } catch (IOException e) {
            return "Couldn't reach Google Sheets (" + e.getClass().getSimpleName() + ").";
        }
    }

    private static String download(String url) throws IOException {
        for (int hop = 0; hop < 5; hop++) {
            HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(15000);
            con.setReadTimeout(15000);
            con.setInstanceFollowRedirects(false);
            con.setRequestProperty("User-Agent", "TrackerWidgets/1.0");
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
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return out.toString(StandardCharsets.UTF_8.name());
            } finally {
                con.disconnect();
            }
        }
        throw new IOException("too many redirects");
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
        // Columns D and E hold the app's own release info (app_version, app_url, app_notes), written by Claude.
        if (row.size() > 3) {
            String k2 = row.get(3).trim();
            if (!k2.isEmpty()) m.put(k2, row.size() > 4 ? row.get(4).trim() : "");
        }
    }

    /** Downloads a file to the given place (used for app updates). */
    static void downloadTo(String url, java.io.File out) throws IOException {
        for (int hop = 0; hop < 6; hop++) {
            HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(20000);
            con.setReadTimeout(60000);
            con.setInstanceFollowRedirects(false);
            con.setRequestProperty("User-Agent", "TrackerWidgets/1.0");
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
