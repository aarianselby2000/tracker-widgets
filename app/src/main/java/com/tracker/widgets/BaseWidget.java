package com.tracker.widgets;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/** Shared behaviour of the four widgets: draw from the saved values, then fetch fresh ones in the background. */
public abstract class BaseWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        Widgets.updateAll(c);
        schedule(c);
        refreshNow(c);
    }

    @Override
    public void onReceive(Context c, Intent i) {
        super.onReceive(c, i);
        if (Widgets.ACTION_REFRESH.equals(i.getAction())) refreshNow(c);
    }

    /** Every 30 minutes while online (Android may stretch this when the phone is idle). */
    static void schedule(Context c) {
        Constraints net = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest r = new PeriodicWorkRequest.Builder(RefreshWorker.class, 30, TimeUnit.MINUTES).setConstraints(net).build();
        WorkManager.getInstance(c).enqueueUniquePeriodicWork("tracker-refresh", ExistingPeriodicWorkPolicy.KEEP, r);
    }

    static void refreshNow(Context c) {
        Constraints net = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        OneTimeWorkRequest r = new OneTimeWorkRequest.Builder(RefreshWorker.class).setConstraints(net).build();
        WorkManager.getInstance(c).enqueueUniqueWork("tracker-refresh-now", ExistingWorkPolicy.REPLACE, r);
    }
}
