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
        findViewById(R.id.perm_steps_b).setOnClickListener(v -> {
            try {
                if (StepsHC.available(this)) startActivityForResult(StepsHC.permissionIntent(this), 13);
                else startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata")));
            } catch (Throwable t) {
                ((TextView) findViewById(R.id.perm_steps_t)).setText("Couldn't open Health Connect (" + t.getClass().getSimpleName() + ").");
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        afterExtras(req == 11);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 13) afterExtras(false);
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
                    ? "Connect, then turn on Steps (and reading in the background)" : "Install or update Health Connect first";
        } catch (Throwable t) { hs = "Health Connect isn't available here"; }
        String err = WidgetData.prefs(this).getString("steps_err", "");
        ((TextView) findViewById(R.id.perm_steps_t)).setText(hs + (hc && !err.isEmpty() ? '\n' + err : ""));
        ((TextView) findViewById(R.id.perm_steps_b)).setText(hc ? "✓" : "Connect");
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
