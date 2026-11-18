package com.tracker.widgets;

import android.os.Build;
import android.view.View;
import android.view.WindowInsets;

/** Keeps a screen's content clear of the status bar and the gesture / navigation bar (Android 15 draws apps under them). */
final class Bars {
    private Bars() {}

    static void pad(View v, boolean top, boolean bottom) {
        final int l = v.getPaddingLeft(), t = v.getPaddingTop(), r = v.getPaddingRight(), b = v.getPaddingBottom();
        v.setOnApplyWindowInsetsListener((view, in) -> {
            int it, ib;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets s = in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                it = s.top;
                ib = s.bottom;
            } else {
                it = in.getSystemWindowInsetTop();
                ib = in.getSystemWindowInsetBottom();
            }
            view.setPadding(l, t + (top ? it : 0), r, b + (bottom ? ib : 0));
            return in;
        });
        v.requestApplyInsets();
    }
}
