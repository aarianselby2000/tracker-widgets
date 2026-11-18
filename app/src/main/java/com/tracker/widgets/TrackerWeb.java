package com.tracker.widgets;

import android.annotation.SuppressLint;
import android.content.Context;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;

/**
 * The Tracker, opened inside this app. Sync runs it out of sight for a few seconds so it takes in what the widgets sent
 * (only the Tracker can write its data); the sign-in screen shows it once so you can sign in to Claude here.
 */
final class TrackerWeb {
    private TrackerWeb() {}

    @SuppressLint("SetJavaScriptEnabled")
    static WebView make(Context c) {
        WebView w = new WebView(c);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        // Look like Chrome on this phone rather than an embedded viewer.
        s.setUserAgentString(s.getUserAgentString().replace("; wv", "").replaceAll("Version/[0-9.]+ ", ""));
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(w, true);
        return w;
    }

    /** Signed in to Claude inside this app (its session cookie is here). */
    static boolean signedIn() {
        try {
            String ck = CookieManager.getInstance().getCookie("https://claude.ai");
            return ck != null && ck.contains("sessionKey");
        } catch (Throwable t) { return false; }
    }

    static boolean isLogin(String url) {
        return url != null && (url.contains("/login") || url.contains("/signup") || url.contains("/magic-link"));
    }
}
