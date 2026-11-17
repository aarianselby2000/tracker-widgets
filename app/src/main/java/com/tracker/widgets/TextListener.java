package com.tracker.widgets;

import android.app.Notification;
import android.content.Context;
import android.os.Bundle;
import android.provider.Telephony;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.core.app.NotificationCompat;
import androidx.core.app.Person;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Texts for the Tracker's Inbox. Only while "Send my texts to the Tracker" is on in the app (and Android's
 * notification access is granted): each incoming message notification from the phone's texting app becomes a
 * "text" entry (sender name and message), which the Tracker's Inbox sorts with Claude like an email.
 * Messages you send, group summaries and sign-in codes are skipped.
 */
public class TextListener extends NotificationListenerService {
    static final String PREF_ON = "texts_on";
    private static final Set<String> APPS = new HashSet<>(Arrays.asList(
            "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms", "com.motorola.messaging", "com.oneplus.mms"));
    private static final Pattern CODE = Pattern.compile("(?i)(code|verif|passcode|one[- ]time|otp|2fa|sign[- ]?in|log[- ]?in)");
    private static final Pattern DIGITS = Pattern.compile("\\b\\d{4,8}\\b");

    static boolean isTextApp(Context c, String pkg) {
        if (APPS.contains(pkg)) return true;
        try { return pkg.equals(Telephony.Sms.getDefaultSmsPackage(c)); } catch (Exception e) { return false; }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Context c = getApplicationContext();
        try {
            if (!WidgetData.prefs(c).getBoolean(PREF_ON, false) || !WidgetData.canSend(c)) return;
            if (!isTextApp(c, sbn.getPackageName())) return;
            Notification n = sbn.getNotification();
            if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
            long cutoff = System.currentTimeMillis() - 6 * 3600 * 1000L;
            NotificationCompat.MessagingStyle ms = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n);
            if (ms != null) {
                CharSequence convo = ms.getConversationTitle();
                List<NotificationCompat.MessagingStyle.Message> msgs = ms.getMessages();
                for (NotificationCompat.MessagingStyle.Message m : msgs) {
                    Person p = m.getPerson();
                    if (p == null || m.getText() == null) continue;   // a message you sent
                    if (m.getTimestamp() > 0 && m.getTimestamp() < cutoff) continue;
                    String from = String.valueOf(p.getName());
                    if (convo != null && ms.isGroupConversation()) from = from + " (in " + convo + ")";
                    take(c, sbn.getPackageName(), from, m.getText().toString(), m.getTimestamp());
                }
                return;
            }
            Bundle x = n.extras;
            CharSequence title = x.getCharSequence(Notification.EXTRA_TITLE), text = x.getCharSequence(Notification.EXTRA_BIG_TEXT);
            if (text == null) text = x.getCharSequence(Notification.EXTRA_TEXT);
            if (title == null || text == null) return;
            take(c, sbn.getPackageName(), title.toString(), text.toString(), sbn.getPostTime());
        } catch (Exception ignored) {}
    }

    private static synchronized void take(Context c, String app, String from, String body, long at) throws Exception {
        body = body.trim();
        if (body.isEmpty()) return;
        if (CODE.matcher(body).find() && DIGITS.matcher(body).find()) return;   // sign-in and verification codes stay on the phone
        String key = Integer.toHexString((from + "|" + body + "|" + (at / 60000)).hashCode());
        JSONArray seen = new JSONArray(WidgetData.prefs(c).getString("texts_seen", "[]"));
        for (int i = 0; i < seen.length(); i++) if (key.equals(seen.optString(i))) return;
        seen.put(key);
        JSONArray keep = new JSONArray();
        for (int i = Math.max(0, seen.length() - 400); i < seen.length(); i++) keep.put(seen.get(i));
        WidgetData.prefs(c).edit().putString("texts_seen", keep.toString())
                .putInt("texts_sent", WidgetData.prefs(c).getInt("texts_sent", 0) + 1).apply();
        JSONObject e = new JSONObject().put("op", "text").put("from", from.length() > 80 ? from.substring(0, 80) : from)
                .put("body", body.length() > 2000 ? body.substring(0, 2000) : body).put("app", app);
        Phone.send(c, e);
    }
}
