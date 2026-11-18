package com.tracker.widgets;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.Toast;

/**
 * The widgets' ⟳ Sync button. An invisible screen that lets touches through: while it's up, Health Connect counts the app as
 * open, so steps can be read; then it closes and the rest (sending what's waiting, fetching the Tracker's latest, the weather)
 * finishes on its own, ending with "Synced ✓" or what went wrong.
 */
public class SyncActivity extends Activity {
    private boolean started;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (started) return;
        started = true;
        Context app = getApplicationContext();
        WidgetData.prefs(app).edit().putLong("syncing_at", System.currentTimeMillis()).apply();
        Widgets.updateAll(app);
        new Thread(() -> {
            try { if (StepsHC.granted(app)) StepsHC.refresh(app); } catch (Throwable ignored) {}
            runOnUiThread(() -> { finish(); overridePendingTransition(0, 0); });
            String err;
            try {
                if (Phone.unsent(app) > 0) Phone.flush(app);
                err = WidgetData.refresh(app);
                android.appwidget.AppWidgetManager m = android.appwidget.AppWidgetManager.getInstance(app);
                if (m.getAppWidgetIds(new android.content.ComponentName(app, WeatherWidget.class)).length > 0) Extras.refreshWeather(app, true);
            } catch (Throwable t) { err = "Couldn't sync (" + t.getClass().getSimpleName() + ")"; }
            WidgetData.prefs(app).edit().putLong("syncing_at", 0).putString("last_error", err == null ? "" : err).apply();
            final String msg = err == null ? "Synced ✓" : err;
            new Handler(Looper.getMainLooper()).post(() -> {
                Widgets.updateAll(app);
                Toast.makeText(app, msg, Toast.LENGTH_SHORT).show();
            });
        }).start();
    }
}
