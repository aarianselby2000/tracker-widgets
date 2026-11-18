package com.tracker.widgets;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import org.json.JSONObject;

/** Widget buttons: water, supplements, an assignment's status, and Add/Skip on an Inbox suggestion. */
public class ActionReceiver extends BroadcastReceiver {
    static final String ACT = "com.tracker.widgets.ACT";
    static final String ACT_LIST = "com.tracker.widgets.ACT_LIST";

    private static int num(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return -1; }
    }

    @Override
    public void onReceive(Context c, Intent i) {
        String op = i.getStringExtra("op");
        if (op == null || op.equals("none")) return;
        if (op.equals("acct")) {   // Money: bring an account's card to the front
            int wid = num(i.getStringExtra("wid"));
            if (wid != -1) WidgetData.prefs(c).edit().putInt("money_front_" + wid, num(i.getStringExtra("to"))).apply();
            Widgets.updateAll(c);
            return;
        }
        if (op.equals("stepday") || op.equals("steppage")) {   // Steps: which day shows big, which week the strip shows
            int wid = num(i.getStringExtra("wid"));
            String to = i.getStringExtra("to");
            if (wid != -1) {
                android.content.SharedPreferences.Editor ed = WidgetData.prefs(c).edit().putString("steps_sel_on_" + wid, java.time.LocalDate.now().toString());
                if (op.equals("stepday")) ed.putString("steps_sel_" + wid, to == null ? "" : to);
                else ed.putInt("steps_page_" + wid, Math.max(0, num(to)));
                ed.apply();
            }
            Widgets.updateAll(c);
            return;
        }
        if (op.equals("calday")) {   // Calendar: show a day's events (empty = back to coming up)
            int wid = num(i.getStringExtra("wid"));
            String to = i.getStringExtra("to");
            if (wid != -1) WidgetData.prefs(c).edit().putString("cal_day_" + wid, to == null ? "" : to).apply();
            Widgets.updateAll(c);
            return;
        }
        if (op.equals("day") || op.equals("page")) {   // which day or tab a widget shows: nothing to send
            int wid = num(i.getStringExtra("wid")), to = num(i.getStringExtra("to"));
            if (wid == -1) return;
            if (op.equals("day")) WidgetData.prefs(c).edit().putInt("sched_day_" + wid, to)
                    .putString("sched_day_on_" + wid, java.time.LocalDate.now().toString()).apply();
            else WidgetData.prefs(c).edit().putInt("page_" + i.getStringExtra("section") + "_" + wid, to).apply();
            Widgets.updateAll(c);
            return;
        }
        if (!WidgetData.canSend(c)) {
            Toast.makeText(c, "Open Tracker Widgets and paste the phone link first.", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            JSONObject e = new JSONObject().put("op", op);
            String msg;
            switch (op) {
                case "water": {
                    double oz = Double.parseDouble(i.getStringExtra("oz"));
                    e.put("oz", oz);
                    msg = "+" + Widgets.fmt(oz) + " oz water";
                    break;
                }
                case "supps": {
                    boolean on = !"false".equals(i.getStringExtra("on"));
                    e.put("on", on);
                    msg = on ? "Supplements checked" : "Supplements unchecked";
                    break;
                }
                case "sleep": {
                    String what = i.getStringExtra("do");
                    e.put("do", what);
                    msg = "bed".equals(what) ? "Good night 🌙" : "Good morning ☀";
                    break;
                }
                case "study":
                    e.put("do", i.getStringExtra("do"));
                    msg = "Study timer started";
                    break;
                case "food": {   // ＋ on a food in the Health widget: the Tracker logs the same food again with its saved numbers
                    String name = i.getStringExtra("again");
                    if (name == null || name.isEmpty()) return;
                    e.put("text", name).put("again", name);
                    for (String key : new String[]{"kcal", "p", "c", "f"}) {
                        try { e.put(key, Double.parseDouble(i.getStringExtra(key))); } catch (Exception ignored) {}
                    }
                    msg = "Adding " + name + " again";
                    break;
                }
                case "prog":
                    e.put("aid", i.getStringExtra("id")).put("v", i.getStringExtra("v"));
                    msg = null;
                    break;
                case "sug": {
                    String what = i.getStringExtra("do"), card = i.getStringExtra("card");
                    e.put("key", i.getStringExtra("key")).put("do", what);
                    if (card != null && !card.isEmpty()) e.put("card", card);
                    msg = "skip".equals(what) ? "Skipped" : "Adding: " + i.getStringExtra("label");
                    break;
                }
                default:
                    return;
            }
            Phone.send(c, e);
            Widgets.updateAll(c);
            if (msg != null) Toast.makeText(c, msg, Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {}
    }
}
