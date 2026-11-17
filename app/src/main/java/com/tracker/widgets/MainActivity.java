package com.tracker.widgets;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import java.util.Map;

/** Setup: paste the phone link, test it, turn on texts for the Inbox, and check for app updates. */
public class MainActivity extends Activity {
    private EditText url;
    private TextView status, textsNote;
    private Switch texts;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        url = findViewById(R.id.url);
        status = findViewById(R.id.status);
        texts = findViewById(R.id.texts);
        textsNote = findViewById(R.id.texts_note);
        url.setText(WidgetData.url(this));
        Button save = findViewById(R.id.save);
        save.setOnClickListener(v -> saveAndTest());
        findViewById(R.id.open).setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(WidgetData.TRACKER_URL))));
        TextView upd = findViewById(R.id.update_status);
        upd.setText("Version " + Updater.installedName(this) + " (build " + Updater.installedVersion(this) + ")");
        findViewById(R.id.update).setOnClickListener(v -> Updater.check(this, upd));
        showValues(null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Show the saved state without treating it as a tap.
        texts.setOnCheckedChangeListener(null);
        texts.setChecked(WidgetData.prefs(this).getBoolean(TextListener.PREF_ON, false));
        texts.setOnCheckedChangeListener((s, on) -> {
            WidgetData.prefs(this).edit().putBoolean(TextListener.PREF_ON, on).apply();
            if (on && !hasAccess()) startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            showTexts();
        });
        showTexts();
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
        else if (!hasAccess()) s = "Almost: allow notification access for Tracker Widgets (Settings → Notifications → Device & app notifications, or the screen that just opened), then come back.";
        else s = "On. New texts go to the Tracker's Inbox" + (n > 0 ? " (" + n + " so far)" : "") + ". Sign-in codes are skipped.";
        if (on && !WidgetData.canSend(this)) s += " Paste the phone link above first.";
        textsNote.setText(s);
    }

    private void saveAndTest() {
        String u = url.getText().toString().trim();
        WidgetData.prefs(this).edit().putString(WidgetData.KEY_URL, u).apply();
        status.setText("Checking the link…");
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
        StringBuilder s = new StringBuilder();
        if (err != null) s.append("⚠ ").append(err).append("\n\n");
        else if (!d.isEmpty()) {
            s.append("✓ Connected. ").append(Widgets.asOf(d.get("updated"), WidgetData.fetchedAt(this))).append('\n');
            s.append(WidgetData.canSend(this) ? "Widgets can add things (the Tracker applies them when it's open)." : "This link can only read: paste the phone link to add things from widgets.").append("\n\n");
        }
        String wait = Phone.waitingNote(this);
        if (!wait.isEmpty()) s.append(wait).append("\n\n");
        if (d.isEmpty()) s.append("No values yet.");
        else for (Map.Entry<String, String> e : d.entrySet()) {
            if (e.getKey().startsWith("json")) continue;
            s.append(e.getKey()).append(": ").append(e.getValue()).append('\n');
        }
        status.setText(s.toString());
    }
}
