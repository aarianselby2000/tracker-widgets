package com.tracker.widgets;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

/** Health's progress rings (calories, protein, water), drawn as one picture since widgets can't draw shapes themselves. */
final class Rings {
    private Rings() {}

    static Bitmap draw(Context c, Double[][] vals, String[] labels, String[] units, int[] colors) {
        float d = c.getResources().getDisplayMetrics().density;
        int n = vals.length, cell = Math.round(100 * d), w = cell * n, h = Math.round(104 * d);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Typeface cond = Typeface.create("sans-serif-condensed", Typeface.BOLD);
        Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), arc = new Paint(Paint.ANTI_ALIAS_FLAG), txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        float stroke = 8 * d, r = 33 * d;
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(stroke);
        track.setColor(0xFF2A313B);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(stroke);
        arc.setStrokeCap(Paint.Cap.ROUND);
        txt.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < n; i++) {
            float cx = cell * i + cell / 2f, cy = r + stroke / 2 + 2 * d;
            RectF box = new RectF(cx - r, cy - r, cx + r, cy + r);
            cv.drawArc(box, 0, 360, false, track);
            Double v = vals[i][0], t = vals[i][1];
            double frac = v != null && t != null && t > 0 ? v / t : 0;
            arc.setColor(colors[i]);
            if (frac > 0) cv.drawArc(box, -90, (float) Math.min(1, frac) * 360, false, arc);
            if (frac > 1) {   // past the goal: a second, brighter lap
                arc.setColor(lighten(colors[i]));
                cv.drawArc(box, -90, (float) Math.min(1, frac - 1) * 360, false, arc);
            }
            txt.setTypeface(cond);
            txt.setColor(0xFFE9EDF2);
            txt.setTextSize(18 * d);
            cv.drawText(v == null ? "–" : Widgets.fmt(v), cx, cy + 3 * d, txt);
            txt.setTypeface(Typeface.DEFAULT);
            txt.setColor(0xFF9AA4B1);
            txt.setTextSize(9.5f * d);
            cv.drawText(t == null ? "" : "/ " + Widgets.fmt(t) + units[i], cx, cy + 15 * d, txt);
            txt.setTypeface(cond);
            txt.setColor(colors[i]);
            txt.setTextSize(11 * d);
            txt.setLetterSpacing(0.08f);
            cv.drawText(labels[i] + (frac >= 1 ? " ✓" : ""), cx, h - 3 * d, txt);
            txt.setLetterSpacing(0);
        }
        return b;
    }

    private static int lighten(int c) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        return 0xFF000000 | (Math.min(255, r + (255 - r) / 2) << 16) | (Math.min(255, g + (255 - g) / 2) << 8) | Math.min(255, b + (255 - b) / 2);
    }
}
