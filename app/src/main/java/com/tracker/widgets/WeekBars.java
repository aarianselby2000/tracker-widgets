package com.tracker.widgets;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

/** One day's bar on Health's "This week" page, sized against that day's target (a dashed line at 100%). */
final class WeekBars {
    private WeekBars() {}

    @androidx.annotation.RequiresApi(31)
    static RemoteViews col(Context c, double val, double target, float area, int color, float k) {
        RemoteViews r = new RemoteViews(c.getPackageName(), R.layout.item_week_col);
        double fr = target > 0 ? val / target : 0;
        float bh = val > 0 ? Math.max(3, (float) Math.min(1.4, fr) / 1.4f * area) : 0;
        r.setViewLayoutHeight(R.id.wbar, bh, TypedValue.COMPLEX_UNIT_DIP);
        r.setInt(R.id.wbar, "setColorFilter", color);
        if (bh == 0) r.setViewVisibility(R.id.wbar, View.GONE);
        if (target > 0) r.setViewLayoutMargin(R.id.wtarget, RemoteViews.MARGIN_BOTTOM, area / 1.4f, TypedValue.COMPLEX_UNIT_DIP);
        else r.setViewVisibility(R.id.wtarget, View.GONE);
        String lab = val <= 0 ? "" : val >= 1000 ? String.format(java.util.Locale.US, "%.1fk", val / 1000) : String.valueOf(Math.round(val));
        r.setTextViewText(R.id.wval, lab);
        r.setTextViewTextSize(R.id.wval, TypedValue.COMPLEX_UNIT_SP, 8.5f * Math.min(k, 1.2f));
        r.setViewLayoutMargin(R.id.wval, RemoteViews.MARGIN_BOTTOM, bh + 1, TypedValue.COMPLEX_UNIT_DIP);
        return r;
    }
}
