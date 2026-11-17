package com.tracker.widgets;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * "Check for update": each new version of the app is published by Claude as a GitHub release of the
 * tracker-widgets repository, tagged b&lt;build number&gt; (b3, b4, …) with the .apk attached. This asks GitHub for the
 * latest release, downloads it when its build number is higher than the installed one, and opens Android's installer.
 */
final class Updater {
    private Updater() {}

    static final String REPO = BuildConfig.UPDATE_REPO;

    static long installedVersion(Activity a) {
        try {
            PackageInfo p = a.getPackageManager().getPackageInfo(a.getPackageName(), 0);
            return p.getLongVersionCode();
        } catch (Exception e) {
            return 0;
        }
    }

    static String installedName(Activity a) {
        try {
            return a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    static void check(Activity a, TextView out) {
        out.setText("Checking for an update…");
        new Thread(() -> {
            long have = installedVersion(a);
            try {
                JSONObject rel = new JSONObject(get("https://api.github.com/repos/" + REPO + "/releases/latest"));
                long latest = parseLong(rel.optString("tag_name", "").replaceAll("[^0-9]", ""));
                String name = rel.optString("name", ""), notes = rel.optString("body", "").trim();
                String url = null;
                JSONArray assets = rel.optJSONArray("assets");
                for (int i = 0; assets != null && i < assets.length(); i++) {
                    JSONObject as = assets.getJSONObject(i);
                    if (as.optString("name", "").endsWith(".apk")) { url = as.optString("browser_download_url", null); break; }
                }
                if (latest <= have || url == null) {
                    post(out, "You have the latest version (" + installedName(a) + ", build " + have + ").");
                    return;
                }
                post(out, "Downloading " + (name.isEmpty() ? "build " + latest : name) + "…");
                File dir = new File(a.getCacheDir(), "updates");
                dir.mkdirs();
                File apk = new File(dir, "TrackerWidgets-" + latest + ".apk");
                WidgetData.downloadTo(url, apk);
                final String msg = (name.isEmpty() ? "Build " + latest : name) + " downloaded" + (notes.isEmpty() ? "." : ":\n" + notes) + "\n\nTap Install on the next screen.";
                new Handler(Looper.getMainLooper()).post(() -> {
                    out.setText(msg);
                    install(a, apk, out);
                });
            } catch (java.io.FileNotFoundException e) {
                post(out, "No versions have been published yet.");
            } catch (Exception e) {
                post(out, "Couldn't check for an update (" + e.getMessage() + "). Check your connection and try again.");
            }
        }).start();
    }

    private static void install(Activity a, File apk, TextView out) {
        if (!a.getPackageManager().canRequestPackageInstalls()) {
            // Android asks once whether this app may install its own updates.
            out.append("\n\nFirst allow \"Install unknown apps\" for Tracker Widgets, then come back and tap Check for update again.");
            a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.getPackageName())));
            return;
        }
        Uri uri = FileProvider.getUriForFile(a, a.getPackageName() + ".files", apk);
        Intent i = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        a.startActivity(i);
    }

    private static String get(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(15000);
        con.setReadTimeout(15000);
        con.setRequestProperty("Accept", "application/vnd.github+json");
        con.setRequestProperty("User-Agent", "TrackerWidgets");
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

    private static void post(TextView out, String s) {
        new Handler(Looper.getMainLooper()).post(() -> out.setText(s));
    }

    private static long parseLong(String s) {
        try { return s == null || s.isEmpty() ? 0 : Long.parseLong(s.trim()); } catch (NumberFormatException e) { return 0; }
    }
}
