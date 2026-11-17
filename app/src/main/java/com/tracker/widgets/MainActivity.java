package com.tracker.widgets;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.util.Map;

/** Setup: paste the published link of the sheet's Widget tab, test it, and see what the widgets will show. */
public class MainActivity extends Activity {
    private EditText url;
    private TextView status;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        url = findViewById(R.id.url);
        status = findViewById(R.id.status);
        url.setText(WidgetData.prefs(this).getString(WidgetData.KEY_URL, ""));
        Button save = findViewById(R.id.save);
        save.setOnClickListener(v -> saveAndTest());
        findViewById(R.id.open).setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(WidgetData.TRACKER_URL))));
        TextView upd = findViewById(R.id.update_status);
        upd.setText("Version " + Updater.installedName(this) + " (build " + Updater.installedVersion(this) + ")");
        findViewById(R.id.update).setOnClickListener(v -> Updater.check(this, upd));
        showValues(null);
    }

    private void saveAndTest() {
        String u = url.getText().toString().trim();
        WidgetData.prefs(this).edit().putString(WidgetData.KEY_URL, u).apply();
        status.setText("Checking the link…");
        new Thread(() -> {
            String err = WidgetData.refresh(getApplicationContext());
            new Handler(Looper.getMainLooper()).post(() -> {
                BaseWidget.schedule(this);
                Widgets.updateAll(this);
                showValues(err);
            });
        }).start();
    }

    private void showValues(String err) {
        Map<String, String> d = WidgetData.load(this);
        StringBuilder s = new StringBuilder();
        if (err != null) s.append("⚠ ").append(err).append("\n\n");
        else if (!d.isEmpty()) s.append("✓ Connected. ").append(Widgets.asOf(d.get("updated"), WidgetData.fetchedAt(this))).append("\n\n");
        if (d.isEmpty()) s.append("No values yet.");
        else for (Map.Entry<String, String> e : d.entrySet()) s.append(e.getKey()).append(": ").append(e.getValue()).append('\n');
        status.setText(s.toString());
    }
}
