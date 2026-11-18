package com.tracker.widgets;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Map;

/** Setup, styled like the Tracker: the phone link, what each widget does, texts for the Inbox, and app updates. */
public class MainActivity extends Activity {
    private EditText url;
    private TextView status, textsNote, connText;
    private ImageView connDot;
    private Switch texts;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        url = findViewById(R.id.url);
        status = findViewById(R.id.status);
        texts = findViewById(R.id.texts);
        textsNote = findViewById(R.id.texts_note);
        connText = findViewById(R.id.conn_text);
        connDot = findViewById(R.id.conn_dot);
        url.setText(WidgetData.url(this));
        findViewById(R.id.save).setOnClickListener(v -> saveAndTest());
        findViewById(R.id.open).setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(WidgetData.TRACKER_URL))));
        TextView upd = findViewById(R.id.update_status);
        upd.setText("Version " + Updater.installedName(this) + " (build " + Updater.installedVersion(this) + ")");
        findViewById(R.id.update).setOnClickListener(v -> Updater.check(this, upd));
        TextView show = findViewById(R.id.show_data);
        show.setOnClickListener(v -> {
            boolean open = status.getVisibility() != View.VISIBLE;
            status.setVisibility(open ? View.VISIBLE : View.GONE);
            show.setText(open ? "WHAT THE WIDGETS SEE  ▴" : "WHAT THE WIDGETS SEE  ▾");
        });
        showValues(null);
        // The phone's own widgets: each asks for its access only when you tap it.
        findViewById(R.id.perm_weather_b).setOnClickListener(v -> requestPermissions(new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION}, 11));
        findViewById(R.id.perm_calendar_b).setOnClickListener(v -> requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR}, 12));
        findViewById(R.id.perm_screen_b).setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        findViewById(R.id.perm_steps_b).setOnClickListener(v -> connectSteps());
    }

    private static final String HC_PKG = "com.google.android.apps.healthdata";
    private long hcAsked;

    /** A message under Steps that stays put when you come back to the app (for 15 minutes), plus a quick note at the bottom of the screen. */
    private void stepsMsg(String m, boolean toast) {
        WidgetData.prefs(this).edit().putString("steps_msg", m).putLong("steps_msg_at", System.currentTimeMillis()).apply();
        showExtras();
        if (toast) Toast.makeText(this, m, Toast.LENGTH_LONG).show();
    }

    /** Ask Health Connect for steps; if it isn't on the phone, get it from the Play Store; if it closes without asking, open its permission page. */
    private void connectSteps() {
        int st;
        try { st = StepsHC.status(this); } catch (Throwable e) { st = -1; }
        if (st != androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE) {
            // Android 13 and older: Health Connect is an app from the Play Store (Android 14+ has it built in).
            stepsMsg("Health Connect isn't ready on this phone, so the Play Store is opening it. Install or update it, then in Samsung Health: "
                    + "⋮ → Settings → Health Connect → allow it to share Steps. Then tap Connect here again.", true);
            openPlayStore(HC_PKG);
            return;
        }
        if (StepsHC.granted(this)) { askBackground(); return; }
        Intent ask;
        try { ask = StepsHC.permissionIntent(this); } catch (Throwable e) { openHealthConnect("Couldn't build Health Connect's request (" + e.getClass().getSimpleName() + ")"); return; }
        hcAsked = android.os.SystemClock.elapsedRealtime();
        Toast.makeText(this, "Opening Health Connect…", Toast.LENGTH_SHORT).show();
        try { startActivityForResult(ask, 13); }
        catch (Throwable e) { openHealthConnect("Health Connect's permission screen wouldn't open (" + e.getClass().getSimpleName() + ")"); }
    }

    private void openPlayStore(String pkg) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return; } catch (Throwable ignored) {}
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Throwable e) { Toast.makeText(this, "Search the Play Store for Health Connect.", Toast.LENGTH_LONG).show(); }
    }

    private void askBackground() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 34 && !StepsHC.grantedBackground(this)) { startActivityForResult(StepsHC.backgroundIntent(this), 14); return; }
        } catch (Throwable ignored) {}
        afterExtras(false);
    }

    /** Health Connect's page for this app, else its home, else its app, else Settings: whichever this phone has. */
    private void openHealthConnect(String why) {
        java.util.List<Intent> tries = new java.util.ArrayList<>();
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            tries.add(new Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS").putExtra(Intent.EXTRA_PACKAGE_NAME, getPackageName()));
            tries.add(new Intent("android.health.connect.action.HEALTH_HOME_SETTINGS"));
        }
        tries.add(new Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"));
        Intent app = getPackageManager().getLaunchIntentForPackage(HC_PKG);
        if (app != null) tries.add(app);
        for (Intent i : tries) {
            try {
                startActivity(i);
                stepsMsg(why + ", so Health Connect's own page is open. Find App permissions → Tracker Widgets (or Steps) and allow it, then come back.", false);
                return;
            } catch (Throwable ignored) {}
        }
        stepsMsg(why + ", and Health Connect's settings wouldn't open either. Open Settings, search \"Health Connect\", then App permissions → Tracker Widgets → allow Steps.", true);
        try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Throwable ignored) {}
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        afterExtras(req == 11);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        // Steps allowed: then (optionally) reading in the background, so the widget updates on its own.
        if (req == 13) {
            long took = android.os.SystemClock.elapsedRealtime() - hcAsked;
            if (StepsHC.granted(this)) { stepsMsg("✓ Steps allowed", false); askBackground(); }
            // Back in under 1.5 s means Health Connect didn't show anything (it stops asking after "Don't allow" twice).
            else if (took < 1500) openHealthConnect("Health Connect closed without asking (after " + took + " ms)");
            else stepsMsg("Steps weren't allowed. Tap Connect to try again, or allow Steps for Tracker Widgets in Health Connect → App permissions.", false);
        }
        if (req == 14) afterExtras(false);
    }

    /** Refresh the phone's widgets right away (steps read fine while this screen is open), then redraw. */
    private void afterExtras(boolean weather) {
        showExtras();
        new Thread(() -> {
            if (weather) Extras.refreshWeather(getApplicationContext(), true);
            try { if (StepsHC.granted(getApplicationContext())) StepsHC.refresh(getApplicationContext()); } catch (Throwable ignored) {}
            new Handler(Looper.getMainLooper()).post(() -> { Widgets.updateAll(this); showExtras(); });
        }).start();
    }

    private void showExtras() {
        boolean loc = Extras.granted(this, android.Manifest.permission.ACCESS_COARSE_LOCATION);
        ((TextView) findViewById(R.id.perm_weather_t)).setText(loc ? "✓ Using your location" : "Newark, DE until you allow your location");
        ((TextView) findViewById(R.id.perm_weather_b)).setText(loc ? "✓" : "Use location");
        boolean us = Extras.usageAllowed(this);
        ((TextView) findViewById(R.id.perm_screen_t)).setText(us ? "✓ Usage access on (stays on your phone)" : "Needs usage access: find Tracker Widgets in the list and turn it on");
        ((TextView) findViewById(R.id.perm_screen_b)).setText(us ? "✓" : "Allow");
        boolean cal = Extras.granted(this, android.Manifest.permission.READ_CALENDAR);
        ((TextView) findViewById(R.id.perm_calendar_t)).setText(cal ? "✓ Reading your phone's calendars" : "Needs calendar access");
        ((TextView) findViewById(R.id.perm_calendar_b)).setText(cal ? "✓" : "Allow");
        boolean hc = false;
        String hs;
        try {
            int st = StepsHC.status(this);
            hc = StepsHC.granted(this);
            hs = hc ? "✓ Connected to Health Connect" : st == androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE
                    ? "Tap Connect and allow Steps (Samsung Health needs to share Steps with Health Connect too)"
                    : "Needs the Health Connect app from the Play Store (Android " + android.os.Build.VERSION.RELEASE + "): tap Get it";
        } catch (Throwable t) { hs = "Health Connect isn't available here"; }
        String err = WidgetData.prefs(this).getString("steps_err", "");
        if (hc && !err.isEmpty()) hs += '\n' + err;
        // The last thing Connect did, kept for 15 minutes so coming back to the app doesn't wipe it.
        String msg = WidgetData.prefs(this).getString("steps_msg", "");
        if (!hc && !msg.isEmpty() && System.currentTimeMillis() - WidgetData.prefs(this).getLong("steps_msg_at", 0) < 15 * 60_000L) hs = msg;
        if (!hc) hs += "\n(Android " + android.os.Build.VERSION.RELEASE + " · Health Connect " + hcState() + ")";
        ((TextView) findViewById(R.id.perm_steps_t)).setText(hs);
        boolean avail = false;
        try { avail = StepsHC.available(this); } catch (Throwable ignored) {}
        ((TextView) findViewById(R.id.perm_steps_b)).setText(hc ? "✓" : avail ? "Connect" : "Get it");
    }

    /** What the app sees of Health Connect, in words, for the line under Steps. */
    private String hcState() {
        int st;
        try { st = StepsHC.status(this); } catch (Throwable e) { return "unreadable"; }
        String v = "";
        try { v = " " + getPackageManager().getPackageInfo(HC_PKG, 0).versionName; } catch (Throwable ignored) {}
        if (st == androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE) return (android.os.Build.VERSION.SDK_INT >= 34 ? "built in" : "app" + v) + ", ready";
        if (st == androidx.health.connect.client.HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) return "app" + v + ", needs an update";
        return "not installed";
    }

    @Override
    protected void onResume() {
        super.onResume();
        afterExtras(false);
        // Show the saved state without treating it as a tap.
        texts.setOnCheckedChangeListener(null);
        texts.setChecked(WidgetData.prefs(this).getBoolean(TextListener.PREF_ON, false));
        texts.setOnCheckedChangeListener((s, on) -> {
            WidgetData.prefs(this).edit().putBoolean(TextListener.PREF_ON, on).apply();
            if (on && !hasAccess()) startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            showTexts();
        });
        showTexts();
        showValues(null);
    }

    private boolean hasAccess() {
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(new ComponentName(this, TextListener.class).flattenToString());
    }

    private void showTexts() {
        boolean on = WidgetData.prefs(this).getBoolean(TextListener.PREF_ON, false);
        int n = WidgetData.prefs(this).getInt("texts_sent", 0);
        String s;
        if (!on) s = "Off. Nothing from your texts leaves the phone.";
        else if (!hasAccess()) s = "Almost: allow notification access for Tracker Widgets on the screen that opened (or Settings → Notifications → Device & app notifications), then come back.";
        else s = "On. New texts go to the Tracker's Inbox" + (n > 0 ? " (" + n + " so far)" : "") + ". Sign-in codes are skipped.";
        if (on && !WidgetData.canSend(this)) s += " Paste the phone link above first.";
        textsNote.setText(s);
    }

    private void saveAndTest() {
        String u = url.getText().toString().trim();
        WidgetData.prefs(this).edit().putString(WidgetData.KEY_URL, u).apply();
        connText.setText("Checking the link…");
        new Thread(() -> {
            String err = WidgetData.refresh(getApplicationContext());
            if (err == null && Phone.unsent(getApplicationContext()) > 0) Phone.flush(getApplicationContext());
            new Handler(Looper.getMainLooper()).post(() -> {
                BaseWidget.schedule(this);
                Widgets.updateAll(this);
                showValues(err);
                showTexts();
            });
        }).start();
    }

    private void showValues(String err) {
        Map<String, String> d = WidgetData.load(this);
        int dot;
        String conn;
        if (err != null) { dot = 0xFFF07070; conn = err; }
        else if (d.isEmpty()) { dot = 0xFF9AA4B1; conn = WidgetData.url(this).isEmpty() ? "Not set up yet: paste your phone link below." : "Tap Save and test to connect."; }
        else {
            dot = WidgetData.canSend(this) ? 0xFF55B981 : 0xFFF0A040;
            conn = "Connected · " + Widgets.asOf(d.get("updated"), WidgetData.fetchedAt(this))
                    + (WidgetData.canSend(this) ? "" : "\nThis link can only read: paste the phone link to add things from widgets.");
        }
        String wait = Phone.waitingNote(this);
        if (!wait.isEmpty()) conn += "\n" + wait;
        connText.setText(conn);
        connDot.setColorFilter(dot);
        StringBuilder s = new StringBuilder();
        if (d.isEmpty()) s.append("No values yet.");
        else for (Map.Entry<String, String> e : d.entrySet()) {
            if (e.getKey().startsWith("json")) continue;
            s.append(e.getKey()).append(": ").append(e.getValue()).append('\n');
        }
        status.setText(s.toString());
    }
}
