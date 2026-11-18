package com.tracker.widgets;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Sends anything left unsent, downloads the Widget tab, and redraws the widgets. */
public class RefreshWorker extends Worker {
    public RefreshWorker(@NonNull Context c, @NonNull WorkerParameters p) { super(c, p); }

    @NonNull
    @Override
    public Result doWork() {
        Context c = getApplicationContext();
        if (Phone.unsent(c) > 0) Phone.flush(c);
        String err = WidgetData.refresh(c);
        // The phone's own widgets: the weather (every 30 minutes, or now after ↻) and steps.
        android.appwidget.AppWidgetManager m = android.appwidget.AppWidgetManager.getInstance(c);
        boolean force = WidgetData.prefs(c).getBoolean("force_extras", false);
        WidgetData.prefs(c).edit().putBoolean("force_extras", false).apply();
        if (m.getAppWidgetIds(new android.content.ComponentName(c, WeatherWidget.class)).length > 0) Extras.refreshWeather(c, force);
        if (m.getAppWidgetIds(new android.content.ComponentName(c, StepsWidget.class)).length > 0) try { StepsHC.refresh(c); } catch (Throwable ignored) {}
        WidgetData.prefs(c).edit().putString("last_error", err == null ? "" : err).apply();
        Widgets.updateAll(c);
        return Result.success();
    }
}
