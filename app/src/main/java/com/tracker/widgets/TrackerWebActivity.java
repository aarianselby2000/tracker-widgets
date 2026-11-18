package com.tracker.widgets;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Sign in to Claude inside this app, once, so Sync can open the Tracker here. Shows the Tracker when you're in. */
public class TrackerWebActivity extends Activity {
    private WebView web;
    private TextView info;

    private int dp(float v) { return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics())); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0F1317);
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(12), dp(8), dp(8));
        TextView title = new TextView(this);
        title.setText("SIGN IN FOR SYNC");
        title.setTextColor(0xFFE9EDF2);
        title.setTextSize(16);
        title.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView done = new TextView(this);
        done.setText("Done");
        done.setTextColor(0xFF12161B);
        done.setTypeface(Typeface.DEFAULT_BOLD);
        done.setBackgroundResource(R.drawable.btn_primary);
        done.setPadding(dp(16), dp(8), dp(16), dp(8));
        done.setOnClickListener(v -> finish());
        bar.addView(done);
        root.addView(bar);
        info = new TextView(this);
        info.setTextColor(0xFF9AA4B1);
        info.setTextSize(13);
        info.setPadding(dp(16), 0, dp(16), dp(10));
        root.addView(info);
        web = TrackerWeb.make(this);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                CookieManager.getInstance().flush();
                showInfo(url);
            }
        });
        root.addView(web, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        Bars.pad(root, true, true);
        showInfo(null);
        web.loadUrl(WidgetData.TRACKER_URL);
    }

    private void showInfo(String url) {
        if (TrackerWeb.isLogin(url) || !TrackerWeb.signedIn())
            info.setText("Sign in to Claude here once. Use Continue with email and the code it sends (Google sign-in doesn't work inside apps). "
                    + "Then let the Tracker load and allow anything it asks for.");
        else info.setText("✓ Signed in. Leave the Tracker open here for a few seconds (and allow anything it asks for), then tap Done. "
                + "From now on ⟳ Sync opens it out of sight to add what the widgets sent.");
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        CookieManager.getInstance().flush();
        if (web != null) { web.stopLoading(); web.destroy(); web = null; }
        super.onDestroy();
    }
}
