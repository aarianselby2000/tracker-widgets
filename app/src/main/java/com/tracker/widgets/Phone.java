package com.tracker.widgets;

import android.content.Context;
import android.net.Uri;

import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Entries made on the phone (a widget button, the add-food or money form, a quick add, a text message).
 * Each one is saved here first, sent to the sheet's phone script (which queues it on the 'Phone Queue' tab),
 * and applied by the Tracker the next time it's open. Until the Tracker's widget data shows it applied,
 * the widgets draw it as pending so a tap shows up right away.
 */
final class Phone {
    private Phone() {}

    private static final Random RND = new Random();

    /** Saves an entry and sends it as soon as there's a connection. */
    static synchronized void send(Context c, JSONObject e) {
        try {
            long now = System.currentTimeMillis();
            e.put("id", "ph" + Long.toString(now, 36) + Integer.toString(RND.nextInt(1 << 20), 36));
            e.put("ts", now);
            e.put("hm", new SimpleDateFormat("HH:mm", Locale.US).format(new Date(now)));
            JSONArray box = outbox(c);
            box.put(e);
            save(c, box);
        } catch (Exception ignored) {}
        flushSoon(c);
    }

    static void flushSoon(Context c) {
        Constraints net = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        OneTimeWorkRequest r = new OneTimeWorkRequest.Builder(SendWorker.class).setConstraints(net)
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build();
        WorkManager.getInstance(c).enqueueUniqueWork("tracker-send", ExistingWorkPolicy.APPEND_OR_REPLACE, r);
    }

    static JSONArray outbox(Context c) {
        try { return new JSONArray(WidgetData.prefs(c).getString("outbox", "[]")); } catch (Exception e) { return new JSONArray(); }
    }

    private static void save(Context c, JSONArray box) {
        WidgetData.prefs(c).edit().putString("outbox", box.toString()).apply();
    }

    /** Sends everything not sent yet. Returns null when it worked (or there was nothing to send). */
    static synchronized String flush(Context c) {
        String url = WidgetData.url(c);
        if (!WidgetData.isScript(url)) return "This link can only read. Paste the phone link to send entries.";
        JSONArray box = outbox(c), send = new JSONArray();
        for (int i = 0; i < box.length(); i++) {
            JSONObject e = box.optJSONObject(i);
            if (e != null && !e.optBoolean("_sent")) send.put(strip(e));
        }
        if (send.length() == 0) return null;
        try {
            Uri u = Uri.parse(url);
            String base = u.buildUpon().clearQuery().build().toString();
            JSONObject body = new JSONObject().put("k", u.getQueryParameter("k")).put("entries", send);
            JSONObject res = new JSONObject(WidgetData.post(base, body.toString()));
            if (!res.optBoolean("ok")) return "The phone script turned it down (" + res.optString("error") + ").";
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < send.length(); i++) ids.add(send.getJSONObject(i).optString("id"));
            JSONArray now = outbox(c);   // entries added while sending stay unsent
            long t = System.currentTimeMillis();
            for (int i = 0; i < now.length(); i++) {
                JSONObject e = now.optJSONObject(i);
                if (e != null && ids.contains(e.optString("id"))) { e.put("_sent", true); e.put("_sentAt", t); e.remove("img"); }
            }
            save(c, now);
            return null;
        } catch (Exception ex) {
            return "Couldn't send (" + ex.getClass().getSimpleName() + ").";
        }
    }

    private static JSONObject strip(JSONObject e) {
        JSONObject o = new JSONObject();
        java.util.Iterator<String> k = e.keys();
        while (k.hasNext()) {
            String key = k.next();
            if (!key.startsWith("_")) try { o.put(key, e.opt(key)); } catch (Exception ignored) {}
        }
        return o;
    }

    /** Ids the Tracker reported as applied in its widget data. */
    private static Set<String> applied(Context c) {
        Set<String> s = new HashSet<>();
        JSONArray ph = WidgetData.w(c).optJSONArray("ph");
        for (int i = 0; ph != null && i < ph.length(); i++) {
            JSONArray r = ph.optJSONArray(i);
            if (r != null) s.add(r.optString(0));
        }
        return s;
    }

    /** When the Tracker last wrote the widget data, in ms (0 if unknown). */
    static long updatedAt(Context c) {
        try { return java.time.Instant.parse(WidgetData.load(c).get("updated")).toEpochMilli(); } catch (Exception e) { return 0; }
    }

    /**
     * Entries the widgets should still draw as pending: not applied by the Tracker yet. An entry counts as applied
     * when the Tracker lists it, or when it has left the sheet's queue and the Tracker has written new data since.
     */
    static List<JSONObject> pending(Context c) {
        List<JSONObject> out = new ArrayList<>();
        Set<String> done = applied(c);
        List<String> wait = WidgetData.waiting(c);
        long upd = updatedAt(c), fetched = WidgetData.fetchedAt(c), now = System.currentTimeMillis();
        JSONArray box = outbox(c);
        for (int i = 0; i < box.length(); i++) {
            JSONObject e = box.optJSONObject(i);
            if (e == null) continue;
            String id = e.optString("id");
            if (done.contains(id)) continue;
            long sentAt = e.optLong("_sentAt", 0);
            if (sentAt > 0 && fetched > sentAt && !wait.contains(id) && upd > sentAt) continue;
            if (now - e.optLong("ts", now) > 3 * 86400000L) continue;
            out.add(e);
        }
        return out;
    }

    /** Drops entries that are applied (or too old) from the saved list. */
    static synchronized void prune(Context c) {
        List<JSONObject> keep = pending(c);
        Set<String> ids = new HashSet<>();
        for (JSONObject e : keep) ids.add(e.optString("id"));
        JSONArray box = outbox(c), out = new JSONArray();
        for (int i = 0; i < box.length(); i++) {
            JSONObject e = box.optJSONObject(i);
            if (e != null && (ids.contains(e.optString("id")) || !e.optBoolean("_sent"))) out.put(e);
        }
        save(c, out);
    }

    static int unsent(Context c) {
        int n = 0;
        JSONArray box = outbox(c);
        for (int i = 0; i < box.length(); i++) { JSONObject e = box.optJSONObject(i); if (e != null && !e.optBoolean("_sent")) n++; }
        return n;
    }

    /** "2 waiting for the Tracker" / "1 not sent yet" for the widget headers, or "". */
    static String waitingNote(Context c) {
        int unsent = unsent(c), pend = 0;
        for (JSONObject e : pending(c)) if (!"text".equals(e.optString("op")) && !"activity".equals(e.optString("op"))) pend++;
        if (unsent > 0) return unsent + " not sent yet";
        if (pend > 0) return pend + " waiting for the Tracker";
        return "";
    }

    // ---------- what the pending entries change, for drawing ----------

    static double pendingWater(Context c) {
        double oz = 0;
        for (JSONObject e : pending(c)) if ("water".equals(e.optString("op"))) oz += e.optDouble("oz", 0);
        return oz;
    }

    /** The last supplements tap not applied yet: TRUE/FALSE, or null. */
    static Boolean pendingSupps(Context c) {
        Boolean b = null;
        for (JSONObject e : pending(c)) if ("supps".equals(e.optString("op"))) b = e.optBoolean("on", true);
        return b;
    }

    static List<JSONObject> pendingOf(Context c, String op) {
        List<JSONObject> out = new ArrayList<>();
        for (JSONObject e : pending(c)) if (op.equals(e.optString("op"))) out.add(e);
        return out;
    }

    /** School item id → status set on the phone and not applied yet. */
    static Map<String, String> pendingProg(Context c) {
        Map<String, String> m = new HashMap<>();
        for (JSONObject e : pendingOf(c, "prog")) m.put(e.optString("aid", e.optString("id")), e.optString("v"));
        return m;
    }

    /** Inbox suggestions already answered on the phone. */
    static Set<String> pendingSugs(Context c) {
        Set<String> s = new HashSet<>();
        for (JSONObject e : pendingOf(c, "sug")) s.add(e.optString("key"));
        return s;
    }
}
