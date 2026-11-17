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

    @Override
    public void onReceive(Context c, Intent i) {
        String op = i.getStringExtra("op");
        if (op == null) return;
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
                case "prog":
                    e.put("id", i.getStringExtra("id")).put("v", i.getStringExtra("v"));
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
