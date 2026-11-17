package com.tracker.widgets;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

/** Sends saved phone entries to the sheet, then checks back for the Tracker's result a little later. */
public class SendWorker extends Worker {
    public SendWorker(@NonNull Context c, @NonNull WorkerParameters p) { super(c, p); }

    @NonNull
    @Override
    public Result doWork() {
        Context c = getApplicationContext();
        String err = Phone.flush(c);
        WidgetData.prefs(c).edit().putString("send_error", err == null ? "" : err).apply();
        Widgets.updateAll(c);
        if (err != null && err.startsWith("Couldn't send") && getRunAttemptCount() < 8) return Result.retry();
        if (err == null) {
            // If the Tracker is open somewhere it applies entries within about 30 seconds.
            Constraints net = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
            WorkManager wm = WorkManager.getInstance(c);
            wm.enqueueUniqueWork("tracker-after-1", ExistingWorkPolicy.REPLACE,
                    new OneTimeWorkRequest.Builder(RefreshWorker.class).setConstraints(net).setInitialDelay(45, TimeUnit.SECONDS).build());
            wm.enqueueUniqueWork("tracker-after-2", ExistingWorkPolicy.REPLACE,
                    new OneTimeWorkRequest.Builder(RefreshWorker.class).setConstraints(net).setInitialDelay(3, TimeUnit.MINUTES).build());
        }
        return Result.success();
    }
}
