package com.tracker.widgets;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

import org.json.JSONArray;

/**
 * Health's "This week" page: the last 7 days of calories, protein and water as bars against each day's target,
 * colored by the Tracker's rules (blue on the way, green on target, orange over), with the target as a dashed line.
 */
final class WeekChart {
    private WeekChart() {}

    static final int BG = 0xFF1F252D, GRID = 0xFF2A313B, MUTED = 0xFF9AA4B1, INK = 0xFFE9EDF2;
    static final int BLUE = 0xFF6B9CEC, GREEN = 0xFF55B981, ORANGE = 0xFFF0A040, WATER = 0xFF5BB3F0;

    /** week: [[date, kcal, kcalTarget, protein, proteinTarget, waterOz, waterGoal]] oldest first. RGB_565 keeps it small. */
    static Bitmap draw(JSONArray week, int w, int h, float d, float k) {
        Bitmap b = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.RGB_565);
        Canvas cv = new Canvas(b);
        cv.drawColor(BG);
        int n = week == null ? 0 : week.length();
        String[] labels = {"CALORIES", "PROTEIN", "WATER"};
        int[][] idx = {{1, 2}, {3, 4}, {5, 6}};
        float labelH = 13 * d * k, dayH = 14 * d * k, gap = 8 * d;
        float rowH = (h - dayH - gap * 2) / 3f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), dash = new Paint(Paint.ANTI_ALIAS_FLAG), txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        dash.setStyle(Paint.Style.STROKE);
        dash.setStrokeWidth(1.2f * d);
        dash.setColor(0x99E9EDF2);
        dash.setPathEffect(new DashPathEffect(new float[]{4 * d, 3 * d}, 0));
        Typeface cond = Typeface.create("sans-serif-condensed", Typeface.BOLD);
        float colW = n > 0 ? w / (float) n : w, barW = Math.min(colW * 0.56f, 26 * d);
        for (int r = 0; r < 3; r++) {
            float top = r * (rowH + gap), base = top + rowH;
            txt.setTypeface(cond);
            txt.setTextSize(10.5f * d * k);
            txt.setTextAlign(Paint.Align.LEFT);
            txt.setColor(r == 2 ? WATER : MUTED);
            cv.drawText(labels[r], 2 * d, top + labelH - 2 * d, txt);
            float area = rowH - labelH - 2 * d;
            p.setColor(GRID);
            cv.drawRect(0, base - 1, w, base, p);
            for (int i = 0; i < n; i++) {
                JSONArray x = week.optJSONArray(i);
                if (x == null) continue;
                double v = x.optDouble(idx[r][0], 0), t = x.optDouble(idx[r][1], 0);
                double frac = t > 0 ? v / t : 0;
                float bh = (float) Math.min(1.4, frac) / 1.4f * area;
                float cx = colW * i + colW / 2;
                int color;
                if (r == 2) color = frac >= 1 ? GREEN : WATER;
                else if (r == 0) color = frac * 100 > 105 ? ORANGE : frac * 100 >= 90 ? GREEN : BLUE;
                else color = frac >= 1 ? GREEN : BLUE;
                p.setColor(color);
                if (v > 0) cv.drawRoundRect(new RectF(cx - barW / 2, base - Math.max(bh, 2 * d), cx + barW / 2, base), 4 * d, 4 * d, p);
                if (t > 0) {   // the day's target
                    float ty = base - area / 1.4f;
                    cv.drawLine(cx - barW / 2 - 3 * d, ty, cx + barW / 2 + 3 * d, ty, dash);
                }
                if (v > 0 && bh > 12 * d * k) {
                    txt.setTypeface(Typeface.DEFAULT_BOLD);
                    txt.setTextSize(8.5f * d * k);
                    txt.setTextAlign(Paint.Align.CENTER);
                    txt.setColor(0xFF12161B);
                    String lab = v >= 1000 ? String.format(java.util.Locale.US, "%.1fk", v / 1000) : String.valueOf(Math.round(v));
                    cv.drawText(lab, cx, base - bh + 10 * d * k, txt);
                }
            }
        }
        // day letters, today's in white
        txt.setTypeface(Typeface.DEFAULT_BOLD);
        txt.setTextSize(9.5f * d * k);
        txt.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < n; i++) {
            JSONArray x = week.optJSONArray(i);
            String day = "";
            try { day = java.time.LocalDate.parse(x.optString(0)).getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.US).substring(0, 2); } catch (Exception ignored) {}
            txt.setColor(i == n - 1 ? INK : MUTED);
            cv.drawText(i == n - 1 ? "Today" : day, colW * i + colW / 2, h - 3 * d, txt);
        }
        return b;
    }
}
