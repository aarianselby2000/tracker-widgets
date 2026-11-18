package com.tracker.widgets;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;

/**
 * Sends your steps, active time and active calories to the Tracker's Health section (Tracker v79+: shown on Today and used for
 * the day's Extra activity), as one "activity" entry. The last 3 days normally, all 30 after a Samsung Health import; only when
 * the numbers or goals changed, and at most every 45 minutes unless it's a new day or an import.
 */
final class ActivitySend {
    private ActivitySend() {}

    static void maybe(Context c, boolean all) {
        try {
            if (!WidgetData.canSend(c)) return;
            SharedPreferences p = WidgetData.prefs(c);
            JSONArray days = Extras.activityDays(c, all ? 30 : 3);
            if (days.length() == 0) return;
            JSONArray goals = new JSONArray().put(Extras.goal(c)).put(Extras.goalMin(c)).put(Extras.goalKcal(c));
            String sig = days.toString() + goals, today = LocalDate.now().toString();
            if (sig.equals(p.getString("act_sent_sig", ""))) return;
            boolean newDay = !today.equals(p.getString("act_sent_on", ""));
            if (!all && !newDay && System.currentTimeMillis() - p.getLong("act_sent_at", 0) < 45 * 60_000L) return;
            String src = p.getString("steps_src", "");
            Phone.send(c, new JSONObject().put("op", "activity").put("days", days).put("goals", goals).put("src", src.isEmpty() ? "phone" : src));
            p.edit().putString("act_sent_sig", sig).putLong("act_sent_at", System.currentTimeMillis()).putString("act_sent_on", today).apply();
        } catch (Exception ignored) {}
    }
}
