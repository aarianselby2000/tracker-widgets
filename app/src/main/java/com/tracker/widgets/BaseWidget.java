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
        tick(c);
        refreshNow(c);
    }

    @Override
    public void onReceive(Context c, Intent i) {
        super.onReceive(c, i);
        if (Widgets.ACTION_REFRESH.equals(i.getAction())) refreshNow(c);
        if (ACTION_TICK.equals(i.getAction())) Widgets.updateAll(c);   // moves Today's now line; no download
        if (i.getAction() != null && i.getAction().startsWith(ACTION_FLIP)) {   // Health page or Schedule day
            int id = i.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1), to = i.getIntExtra("to", 0);
            if (id == -1) return;
            if ("day".equals(i.getStringExtra("what")))
                WidgetData.prefs(c).edit().putInt("sched_day_" + id, to).putString("sched_day_on_" + id, java.time.LocalDate.now().toString()).apply();
            else WidgetData.prefs(c).edit().putInt("health_page_" + id, to).apply();
            Widgets.updateAll(c);
            return;
        }
        if (i.getAction() != null && i.getAction().startsWith(ACTION_SHOW)) {   // a Money tab: show that account's card
            int id = i.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1), idx = i.getIntExtra("idx", 0);
            if (id == -1) return;
            WidgetData.prefs(c).edit().putInt("money_acct_" + id, idx).apply();
            android.widget.RemoteViews rv = new android.widget.RemoteViews(c.getPackageName(), R.layout.widget_money);
            rv.setDisplayedChild(R.id.acct_stack, idx);
            AppWidgetManager.getInstance(c).partiallyUpdateAppWidget(id, rv);
        }
    }

    static final String ACTION_SHOW = "com.tracker.widgets.SHOW";
    static final String ACTION_FLIP = "com.tracker.widgets.FLIP";

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, android.os.Bundle o) {
        Widgets.updateAll(c);   // resized: redraw at the new size
    }

    static final String ACTION_TICK = "com.tracker.widgets.TICK";

    /** Redraws every 15 minutes while the phone is awake (it doesn't wake the phone), so the now line keeps up. */
    static void tick(Context c) {
        android.app.AlarmManager am = (android.app.AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(c, ScheduleWidget.class).setAction(ACTION_TICK);
        android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(c, 4242, i,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
        if (am != null) am.setInexactRepeating(android.app.AlarmManager.ELAPSED_REALTIME, android.os.SystemClock.elapsedRealtime() + 15 * 60000L,
                android.app.AlarmManager.INTERVAL_FIFTEEN_MINUTES, pi);
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
