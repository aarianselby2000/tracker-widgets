package com.tracker.widgets;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * The widgets' ⟳ Sync button, a small card at the bottom of the screen (taps elsewhere go through to the home screen).
 * 1. Reads steps (Health Connect allows it while this is open), sends what's waiting, fetches the Tracker's latest.
 * 2. If the Tracker hasn't taken in everything yet, opens it here out of sight (signed in to Claude in this app) and waits
 *    until the sheet's queue is empty and it has written fresh data for the widgets, then closes.
 */
public class SyncActivity extends Activity {
    private static final long TIMEOUT = 100_000;
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean started, finished;
    private long start;
    private WebView web;
    private TextView status, action;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        overridePendingTransition(0, 0);
        Window w = getWindow();
        w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        w.setGravity(Gravity.BOTTOM);
        w.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
        w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        setContentView(R.layout.activity_sync);
        status = findViewById(R.id.sync_status);
        action = findViewById(R.id.sync_action);
        findViewById(R.id.sync_close).setOnClickListener(v -> done(null, 0));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (started) return;
        started = true;
        start = System.currentTimeMillis();
        Context app = getApplicationContext();
        WidgetData.prefs(app).edit().putLong("syncing_at", start).apply();
        Widgets.updateAll(app);
        status.setText("Sending what's waiting…");
        new Thread(() -> {
            try { if (StepsHC.granted(app)) StepsHC.refresh(app); } catch (Throwable ignored) {}
            String err;
            try {
                if (Phone.unsent(app) > 0) Phone.flush(app);
                err = WidgetData.refresh(app);
                android.appwidget.AppWidgetManager m = android.appwidget.AppWidgetManager.getInstance(app);
                if (m.getAppWidgetIds(new android.content.ComponentName(app, WeatherWidget.class)).length > 0) Extras.refreshWeather(app, true);
            } catch (Throwable t) { err = "Couldn't sync (" + t.getClass().getSimpleName() + ")"; }
            final String e = err;
            // The Tracker needs to run when entries are still in the sheet's queue, or its data here is over 20 minutes old.
            final int waiting = WidgetData.waiting(app).size();
            final boolean need = e == null && (waiting > 0 || System.currentTimeMillis() - Phone.updatedAt(app) > 20 * 60_000L);
            h.post(() -> {
                if (finished) return;
                if (e != null) done(e, 3500);
                else if (!need) done("Synced ✓  The Tracker already has everything.", 1400);
                else openTracker(waiting);
            });
        }).start();
    }

    private void openTracker(int waiting) {
        if (!TrackerWeb.signedIn()) { needSignIn(); return; }
        status.setText(waiting > 0 ? "Opening the Tracker to add " + waiting + " waiting…" : "Getting the Tracker's latest…");
        web = TrackerWeb.make(this);
        web.setAlpha(0.01f);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (TrackerWeb.isLogin(url)) needSignIn();
            }
        });
        FrameLayout box = findViewById(R.id.sync_web);
        box.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1400));
        web.loadUrl(WidgetData.TRACKER_URL);
        poll();
    }

    private void needSignIn() {
        if (finished) return;
        if (web != null) web.stopLoading();
        status.setText("To add what the widgets sent, the Tracker has to open. Sign in to Claude once in Tracker Widgets so Sync can do that here.");
        action.setText("Sign in");
        action.setVisibility(View.VISIBLE);
        action.setOnClickListener(v -> {
            startActivity(new Intent(this, TrackerWebActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            done(null, 0);
        });
        WidgetData.prefs(getApplicationContext()).edit().putLong("syncing_at", 0).apply();
        Widgets.updateAll(getApplicationContext());
    }

    /** Every 4 seconds: is the queue empty and has the Tracker written fresh data? */
    private void poll() {
        h.postDelayed(() -> {
            if (finished || action.getVisibility() == View.VISIBLE) return;
            Context app = getApplicationContext();
            new Thread(() -> {
                String err = WidgetData.refresh(app);
                int n = WidgetData.waiting(app).size();
                boolean ok = err == null && n == 0 && Phone.updatedAt(app) >= start - 5000;
                long took = System.currentTimeMillis() - start;
                h.post(() -> {
                    if (finished) return;
                    if (ok) done("Synced ✓  The Tracker has everything.", 1500);
                    else if (took > TIMEOUT) done("The Tracker didn't finish in time. It'll catch up the next time it's open.", 4000);
                    else {
                        status.setText(n > 0 ? "Tracker open · " + n + " waiting…" : "Tracker open · saving for the widgets…");
                        Widgets.updateAll(app);
                        poll();
                    }
                });
            }).start();
        }, 4000);
    }

    private void done(String msg, long delay) {
        if (finished) return;
        finished = true;
        Context app = getApplicationContext();
        WidgetData.prefs(app).edit().putLong("syncing_at", 0).apply();
        Widgets.updateAll(app);
        if (msg != null) status.setText(msg);
        h.postDelayed(() -> { finish(); overridePendingTransition(0, 0); }, delay);
    }

    @Override
    protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        if (web != null) { web.stopLoading(); web.destroy(); web = null; }
        WidgetData.prefs(getApplicationContext()).edit().putLong("syncing_at", 0).apply();
        super.onDestroy();
    }
}
