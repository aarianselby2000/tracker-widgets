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
        WidgetData.prefs(c).edit().putString("last_error", err == null ? "" : err).apply();
        Widgets.updateAll(c);
        return Result.success();
    }
}
