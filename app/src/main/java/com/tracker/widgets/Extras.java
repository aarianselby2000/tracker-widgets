package com.tracker.widgets;

import android.Manifest;
import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.provider.CalendarContract;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** The Weather, Screen time, Calendar and Steps widgets, drawn in the Tracker's style from the phone's own data. */
final class Extras {
    private Extras() {}

    static boolean granted(Context c, String perm) {
        return c.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }

    /** The header's "updated" line (or Syncing…) and its Sync button. Tapping the widget itself opens nothing. */
    private static void header(Context c, RemoteViews v, Class<?> provider, String sub) {
        v.setTextViewText(R.id.updated_text, Widgets.syncing(c) ? "Syncing…" : sub);
        Widgets.syncButton(c, v);
        Pages.click(c, v, R.id.root, "op", "none");   // taps that aren't on a button do nothing
    }

    private static void note(RemoteViews v, int id, String text) {
        v.setTextViewText(id, text);
        v.setViewVisibility(id, text == null || text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private static String ago(long t) {
        if (t == 0) return "";
        long m = (System.currentTimeMillis() - t) / 60000;
        return m < 2 ? "just now" : m < 60 ? m + " min ago" : (m / 60) + " h ago";
    }

    // ================= Weather (Open-Meteo: free, no account; your location if allowed, else Newark, DE) =================

    static final double DEF_LAT = 39.6837, DEF_LON = -75.7497;

    /** Downloads the forecast when it's over 30 minutes old (or forced). Returns null when it worked. */
    static String refreshWeather(Context c, boolean force) {
        android.content.SharedPreferences p = WidgetData.prefs(c);
        if (!force && System.currentTimeMillis() - p.getLong("wx_at", 0) < 30 * 60000L) return null;
        double lat = DEF_LAT, lon = DEF_LON;
        String place = "Newark, DE";
        if (granted(c, Manifest.permission.ACCESS_COARSE_LOCATION)) {
            try {
                LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
                Location best = null;
                for (String prov : lm.getProviders(true)) {
                    Location l = lm.getLastKnownLocation(prov);
                    if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
                }
                if (best != null) {
                    lat = Math.round(best.getLatitude() * 100) / 100.0;   // about 1 km: plenty for weather
                    lon = Math.round(best.getLongitude() * 100) / 100.0;
                    place = "";
                    try {
                        @SuppressWarnings("deprecation") List<android.location.Address> a = new Geocoder(c, Locale.US).getFromLocation(lat, lon, 1);
                        if (a != null && !a.isEmpty()) place = (a.get(0).getLocality() != null ? a.get(0).getLocality() : "") + (a.get(0).getAdminArea() != null ? ", " + a.get(0).getAdminArea() : "");
                    } catch (Exception ignored) {}
                    if (place.isEmpty()) place = "Your location";
                }
            } catch (SecurityException ignored) {}
        }
        try {
            String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                    + "&current=temperature_2m,apparent_temperature,weather_code,is_day,wind_speed_10m,relative_humidity_2m"
                    + "&hourly=temperature_2m,weather_code,precipitation_probability,is_day"
                    + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                    + "&temperature_unit=fahrenheit&wind_speed_unit=mph&timezone=auto&forecast_days=7&forecast_hours=12";
            String body = WidgetData.download(url);
            new JSONObject(body);
            p.edit().putString("wx", body).putString("wx_place", place).putLong("wx_at", System.currentTimeMillis()).apply();
            return null;
        } catch (Exception e) {
            return "Couldn't get the weather (" + e.getClass().getSimpleName() + ").";
        }
    }

    static String[] wmo(int code, boolean day) {
        if (code == 0) return new String[]{day ? "☀️" : "🌙", "Clear"};
        if (code == 1) return new String[]{day ? "🌤️" : "🌙", "Mostly clear"};
        if (code == 2) return new String[]{"⛅", "Partly cloudy"};
        if (code == 3) return new String[]{"☁️", "Cloudy"};
        if (code == 45 || code == 48) return new String[]{"🌫️", "Fog"};
        if (code >= 51 && code <= 57) return new String[]{"🌦️", "Drizzle"};
        if (code == 66 || code == 67) return new String[]{"🌧️", "Freezing rain"};
        if (code >= 61 && code <= 65) return new String[]{"🌧️", code == 65 ? "Heavy rain" : "Rain"};
        if (code >= 71 && code <= 77) return new String[]{"🌨️", "Snow"};
        if (code >= 80 && code <= 82) return new String[]{"🌧️", "Showers"};
        if (code == 85 || code == 86) return new String[]{"🌨️", "Snow showers"};
        if (code >= 95) return new String[]{"⛈️", "Thunderstorms"};
        return new String[]{"🌡️", "—"};
    }

    static RemoteViews weather(Context c, int wid) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_weather);
        android.content.SharedPreferences p = WidgetData.prefs(c);
        float k = Widgets.scale(c, wid);
        header(c, v, WeatherWidget.class, ago(p.getLong("wx_at", 0)));
        String raw = p.getString("wx", "");
        if (raw.isEmpty()) { note(v, R.id.w_note, "Getting the weather… tap ↻ if it doesn't show up."); return v; }
        note(v, R.id.w_note, "");
        try {
            JSONObject o = new JSONObject(raw), cur = o.getJSONObject("current"), d = o.getJSONObject("daily"), h = o.getJSONObject("hourly");
            String[] wx = wmo(cur.optInt("weather_code"), cur.optInt("is_day", 1) == 1);
            v.setTextViewText(R.id.w_icon, wx[0]);
            v.setTextViewText(R.id.w_temp, Math.round(cur.optDouble("temperature_2m")) + "°");
            v.setTextViewText(R.id.w_cond, wx[1]);
            v.setTextViewText(R.id.w_feels, "Feels " + Math.round(cur.optDouble("apparent_temperature")) + "° · wind " + Math.round(cur.optDouble("wind_speed_10m")) + " mph · " + cur.optInt("relative_humidity_2m") + "% humid");
            v.setTextViewText(R.id.w_hilo, "H " + Math.round(d.getJSONArray("temperature_2m_max").optDouble(0)) + "°  L " + Math.round(d.getJSONArray("temperature_2m_min").optDouble(0)) + "°"
                    + (d.getJSONArray("precipitation_probability_max").optInt(0) > 0 ? "  ·  💧 " + d.getJSONArray("precipitation_probability_max").optInt(0) + "%" : ""));
            v.setTextViewText(R.id.w_place, "📍 " + p.getString("wx_place", "Newark, DE"));
            Widgets.ts(v, R.id.w_temp, 48, k);
            Widgets.ts(v, R.id.w_cond, 15, k);
            // the next hours
            v.removeAllViews(R.id.w_hours);
            JSONArray ht = h.getJSONArray("time"), htemp = h.getJSONArray("temperature_2m"), hc = h.getJSONArray("weather_code"), hp = h.getJSONArray("precipitation_probability"), hd = h.getJSONArray("is_day");
            int[] sz = Widgets.sizeDp(c, wid);
            int cells = Math.max(4, Math.min(8, (sz[0] - 26) / 48));
            for (int i = 0; i < Math.min(cells, ht.length()); i++) {
                RemoteViews cell = new RemoteViews(c.getPackageName(), R.layout.item_wx_hour);
                LocalDateTime t = LocalDateTime.parse(ht.optString(i));
                cell.setTextViewText(R.id.wh_t, i == 0 ? "Now" : Widgets.clock(t.getHour() * 60).replace(" AM", "a").replace(" PM", "p"));
                cell.setTextViewText(R.id.wh_i, wmo(hc.optInt(i), hd.optInt(i, 1) == 1)[0]);
                cell.setTextViewText(R.id.wh_v, Math.round(htemp.optDouble(i)) + "°");
                cell.setTextViewText(R.id.wh_p, hp.optInt(i) > 0 ? hp.optInt(i) + "%" : "");
                v.addView(R.id.w_hours, cell);
            }
            // the next days, as many as fit
            v.removeAllViews(R.id.w_days);
            JSONArray dt = d.getJSONArray("time"), dc = d.getJSONArray("weather_code"), dmax = d.getJSONArray("temperature_2m_max"), dmin = d.getJSONArray("temperature_2m_min"), dp = d.getJSONArray("precipitation_probability_max");
            int fit = (int) Math.max(2, (sz[1] - (26 + 70 + 20 + 30 + 80 + 30) * k - 26) / (30 * k));
            for (int i = 1; i < Math.min(dt.length(), fit + 1); i++) {
                RemoteViews row = new RemoteViews(c.getPackageName(), R.layout.item_wx_day);
                LocalDate day = LocalDate.parse(dt.optString(i));
                String[] w = wmo(dc.optInt(i), true);
                row.setTextViewText(R.id.wd_n, i == 1 ? "Tmrw" : day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.US));
                row.setTextViewText(R.id.wd_i, w[0]);
                row.setTextViewText(R.id.wd_p, dp.optInt(i) > 0 ? "💧" + dp.optInt(i) + "%" : "");
                row.setTextViewText(R.id.wd_c, w[1]);
                android.text.SpannableStringBuilder r = new android.text.SpannableStringBuilder();
                r.append(Math.round(dmin.optDouble(i)) + "°  ");
                r.setSpan(new android.text.style.ForegroundColorSpan(Widgets.MUTED), 0, r.length(), 0);
                int s0 = r.length();
                r.append(Math.round(dmax.optDouble(i)) + "°");
                r.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), s0, r.length(), 0);
                row.setTextViewText(R.id.wd_r, r);
                row.setTextViewTextSize(R.id.wd_n, TypedValue.COMPLEX_UNIT_SP, 12.5f * k);
                row.setTextViewTextSize(R.id.wd_r, TypedValue.COMPLEX_UNIT_SP, 14 * k);
                v.addView(R.id.w_days, row);
            }
        } catch (Exception e) {
            note(v, R.id.w_note, "Couldn't read the forecast. Tap ↻.");
        }
        return v;
    }

    // ================= Screen time (Android's usage access; nothing leaves the phone) =================

    static boolean usageAllowed(Context c) {
        AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
        int mode = Build.VERSION.SDK_INT >= 29 ? ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName())
                : ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    /** Foreground time per app between two moments, from the activity resume/pause events. */
    static Map<String, Long> usage(Context c, long from, long to) {
        Map<String, Long> fg = new HashMap<>(), open = new HashMap<>();
        UsageStatsManager um = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
        UsageEvents ev = um.queryEvents(from, to);
        UsageEvents.Event e = new UsageEvents.Event();
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e);
            String pkg = e.getPackageName();
            int t = e.getEventType();
            if (t == UsageEvents.Event.ACTIVITY_RESUMED) open.put(pkg, e.getTimeStamp());
            else if (t == UsageEvents.Event.ACTIVITY_PAUSED || t == UsageEvents.Event.ACTIVITY_STOPPED) {
                Long s = open.remove(pkg);
                if (s != null) fg.put(pkg, (fg.containsKey(pkg) ? fg.get(pkg) : 0L) + (e.getTimeStamp() - s));
            }
        }
        for (Map.Entry<String, Long> o : open.entrySet()) fg.put(o.getKey(), (fg.containsKey(o.getKey()) ? fg.get(o.getKey()) : 0L) + (to - o.getValue()));
        // Not counted: the home screen, the system UI and this app.
        Set<String> skip = new HashSet<>();
        skip.add(c.getPackageName());
        skip.add("com.android.systemui");
        try {
            android.content.pm.ResolveInfo home = c.getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
            if (home != null) skip.add(home.activityInfo.packageName);
        } catch (Exception ignored) {}
        for (String sk : skip) fg.remove(sk);
        return fg;
    }

    static RemoteViews screen(Context c, int wid) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_screen);
        float k = Widgets.scale(c, wid);
        header(c, v, ScreenTimeWidget.class, "today");
        if (!usageAllowed(c)) {
            v.setTextViewText(R.id.sc_total, "–");
            note(v, R.id.sc_note, "Open Tracker Widgets and tap Allow screen time (usage access). It stays on your phone.");
            return v;
        }
        note(v, R.id.sc_note, "");
        ZoneId z = ZoneId.systemDefault();
        long now = System.currentTimeMillis(), midnight = LocalDate.now().atStartOfDay(z).toInstant().toEpochMilli();
        Map<String, Long> today = usage(c, midnight, now);
        long total = 0;
        for (long t : today.values()) total += t;
        // Yesterday up to the same time, for a fair comparison.
        long yStart = midnight - 86400000L;
        long yTotal = 0;
        for (long t : usage(c, yStart, now - 86400000L).values()) yTotal += t;
        v.setTextViewText(R.id.sc_total, dur(total));
        long diff = total - yTotal;
        v.setTextViewText(R.id.sc_sub, yTotal == 0 ? "so far today" : (Math.abs(diff) < 60000 ? "about the same as" : dur(Math.abs(diff)) + (diff > 0 ? " more than" : " less than")) + " yesterday by now");
        v.setTextColor(R.id.sc_sub, yTotal == 0 || Math.abs(diff) < 60000 ? Widgets.MUTED : diff > 0 ? 0xFFF0A040 : 0xFF55B981);
        Widgets.ts(v, R.id.sc_total, 40, k);
        List<Map.Entry<String, Long>> apps = new ArrayList<>(today.entrySet());
        apps.removeIf(x -> x.getValue() < 60000);
        apps.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        v.removeAllViews(R.id.sc_apps);
        int[] sz = Widgets.sizeDp(c, wid);
        int fit = (int) Math.max(2, (sz[1] - (26 + 60 + 20 + 30) * k - 26) / (40 * k));
        long top = apps.isEmpty() ? 1 : apps.get(0).getValue();
        PackageManager pm = c.getPackageManager();
        for (int i = 0; i < Math.min(fit, apps.size()); i++) {
            String pkg = apps.get(i).getKey();
            RemoteViews row = new RemoteViews(c.getPackageName(), R.layout.item_app_row);
            String name = pkg;
            try {
                android.content.pm.ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                name = String.valueOf(pm.getApplicationLabel(ai));
                row.setImageViewBitmap(R.id.app_icon, bitmap(pm.getApplicationIcon(ai), Math.round(28 * c.getResources().getDisplayMetrics().density)));
            } catch (Exception ignored) {}
            row.setTextViewText(R.id.app_name, name);
            row.setTextViewText(R.id.app_time, dur(apps.get(i).getValue()));
            row.setProgressBar(R.id.app_bar, 100, (int) Math.round(apps.get(i).getValue() * 100.0 / top), false);
            row.setTextViewTextSize(R.id.app_name, TypedValue.COMPLEX_UNIT_SP, 12.5f * k);
            row.setTextViewTextSize(R.id.app_time, TypedValue.COMPLEX_UNIT_SP, 13.5f * k);
            v.addView(R.id.sc_apps, row);
        }
        if (apps.isEmpty()) v.addView(R.id.sc_apps, Pages.note(c, "Nothing over a minute yet today.", k));
        return v;
    }

    private static Bitmap bitmap(Drawable d, int px) {
        Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        d.setBounds(0, 0, px, px);
        d.draw(cv);
        return b;
    }

    static String dur(long ms) {
        long m = Math.round(ms / 60000.0);
        return m < 60 ? m + "m" : (m / 60) + "h " + (m % 60) + "m";
    }

    // ================= Calendar (your phone's calendars) =================

    static RemoteViews calendar(Context c, int wid) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_calendar);
        float k = Widgets.scale(c, wid);
        header(c, v, CalendarWidget.class, "");
        LocalDate today = LocalDate.now(), first = today.withDayOfMonth(1);
        v.setTextViewText(R.id.cal_month, today.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)).toUpperCase(Locale.US));
        Widgets.ts(v, R.id.cal_month, 17, k);
        boolean ok = granted(c, Manifest.permission.READ_CALENDAR);
        note(v, R.id.cal_note, ok ? "" : "Open Tracker Widgets and tap Allow calendar to see your events.");
        ZoneId z = ZoneId.systemDefault(), utc = ZoneId.of("UTC");
        LocalDate gridStart = first.minusDays(first.getDayOfWeek().getValue() % 7), gridEnd = gridStart.plusWeeks(6);
        // The day you tapped stays chosen until you tap it again (or ✕), or the month changes.
        String w = String.valueOf(wid);
        LocalDate sel = null;
        try { String x = WidgetData.prefs(c).getString("cal_day_" + wid, ""); if (!x.isEmpty()) sel = LocalDate.parse(x); } catch (Exception ignored) {}
        if (sel != null && (sel.isBefore(gridStart) || !sel.isBefore(gridEnd))) sel = null;
        LocalDate until = today.plusDays(15).isAfter(gridEnd) ? today.plusDays(15) : gridEnd;
        List<long[]> times = new ArrayList<>();
        List<String[]> info = new ArrayList<>();   // title, location, first day, day after the last
        Set<LocalDate> busy = new HashSet<>();
        if (ok) {
            Uri.Builder ub = CalendarContract.Instances.CONTENT_URI.buildUpon();
            ContentUris.appendId(ub, gridStart.atStartOfDay(z).toInstant().toEpochMilli());
            ContentUris.appendId(ub, until.atStartOfDay(z).toInstant().toEpochMilli());
            String[] proj = {CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY,
                    CalendarContract.Instances.DISPLAY_COLOR, CalendarContract.Instances.EVENT_LOCATION};
            try (Cursor cur = c.getContentResolver().query(ub.build(), proj, CalendarContract.Instances.VISIBLE + "=1", null, CalendarContract.Instances.BEGIN + " ASC")) {
                while (cur != null && cur.moveToNext()) {
                    long bg = cur.getLong(1), en = Math.max(cur.getLong(2), cur.getLong(1));
                    boolean allDay = cur.getInt(3) == 1;
                    LocalDate d0, d1;
                    if (allDay) { d0 = Instant.ofEpochMilli(bg).atZone(utc).toLocalDate(); d1 = Instant.ofEpochMilli(en).atZone(utc).toLocalDate(); }
                    else { d0 = Instant.ofEpochMilli(bg).atZone(z).toLocalDate(); d1 = Instant.ofEpochMilli(Math.max(bg, en - 1)).atZone(z).toLocalDate().plusDays(1); }
                    if (!d1.isAfter(d0)) d1 = d0.plusDays(1);
                    for (LocalDate x = d0; x.isBefore(d1) && x.isBefore(d0.plusDays(40)); x = x.plusDays(1)) busy.add(x);
                    times.add(new long[]{bg, en, allDay ? 1 : 0, cur.getInt(4)});
                    info.add(new String[]{cur.getString(0) == null ? "(no title)" : cur.getString(0), cur.getString(5) == null ? "" : cur.getString(5), d0.toString(), d1.toString()});
                }
            } catch (Exception ignored) {}
        }
        // the month grid: tap a day to see it below
        v.removeAllViews(R.id.cal_grid);
        int[][] cell = {{R.id.c0, R.id.c0_bg, R.id.c0_t, R.id.c0_d}, {R.id.c1, R.id.c1_bg, R.id.c1_t, R.id.c1_d}, {R.id.c2, R.id.c2_bg, R.id.c2_t, R.id.c2_d},
                {R.id.c3, R.id.c3_bg, R.id.c3_t, R.id.c3_d}, {R.id.c4, R.id.c4_bg, R.id.c4_t, R.id.c4_d}, {R.id.c5, R.id.c5_bg, R.id.c5_t, R.id.c5_d}, {R.id.c6, R.id.c6_bg, R.id.c6_t, R.id.c6_d}};
        LocalDate d = gridStart;
        int weeks = 0;
        for (int wk = 0; wk < 6 && (wk == 0 || d.getMonth() == first.getMonth()); wk++, weeks++) {
            RemoteViews row = new RemoteViews(c.getPackageName(), R.layout.item_cal_week);
            for (int i = 0; i < 7; i++, d = d.plusDays(1)) {
                boolean inMonth = d.getMonth() == first.getMonth(), isToday = d.equals(today), isSel = d.equals(sel), filled = isToday || isSel;
                row.setTextViewText(cell[i][2], String.valueOf(d.getDayOfMonth()));
                row.setTextColor(cell[i][2], filled ? 0xFF12161B : !inMonth ? 0xFF4A525E : d.isBefore(today) ? Widgets.MUTED : Widgets.INK);
                row.setViewVisibility(cell[i][1], filled ? View.VISIBLE : View.INVISIBLE);
                row.setInt(cell[i][1], "setColorFilter", isToday ? 0xFFF0AC55 : 0xFF6B9CEC);
                row.setTextViewText(cell[i][3], busy.contains(d) && !filled ? "•" : "");
                row.setTextViewTextSize(cell[i][2], TypedValue.COMPLEX_UNIT_SP, 12.5f * k);
                Pages.click(c, row, cell[i][0], "op", "calday", "wid", w, "to", isSel ? "" : d.toString());
            }
            v.addView(R.id.cal_grid, row);
        }
        JSONArray items = new JSONArray();   // the list's rows (Pages.row format), drawn by ListService kind "cal"
        int[] sz = Widgets.sizeDp(c, wid);
        int shown = 0, more = 0;
        if (sel != null) {
            // the chosen day: everything on it, morning to night
            v.setTextViewText(R.id.cal_list_h, (sel.equals(today) ? "TODAY" : sel.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US)).toUpperCase(Locale.US)) + "   ✕");
            Pages.click(c, v, R.id.cal_list_h, "op", "calday", "wid", w, "to", "");
            for (int i = 0; i < times.size(); i++) {
                long[] t = times.get(i);
                String[] in = info.get(i);
                LocalDate d0 = LocalDate.parse(in[2]), d1 = LocalDate.parse(in[3]);
                if (sel.isBefore(d0) || !sel.isBefore(d1)) continue;
                String when;
                if (t[2] == 1) when = "all day";
                else {
                    LocalDateTime st = Instant.ofEpochMilli(t[0]).atZone(z).toLocalDateTime(), en = Instant.ofEpochMilli(t[1]).atZone(z).toLocalDateTime();
                    when = (d0.equals(sel) ? Widgets.clock(st.getHour() * 60 + st.getMinute()) : "…") + " – "
                            + (en.toLocalDate().equals(sel) ? Widgets.clock(en.getHour() * 60 + en.getMinute()) : "…");
                }
                String col = String.format("#%06X", 0xFFFFFF & (int) t[3]);
                items.put(new JSONArray().put("row").put(in[0]).put(when).put(in[1]).put(col).put(""));
                shown++;
            }
            if (ok && shown == 0) items.put(new JSONArray().put("note").put("Nothing on this day."));
            calList(c, v, wid, items);
            return v;
        }
        // coming up: from now, all of it (the list scrolls)
        v.setTextViewText(R.id.cal_list_h, "COMING UP  ·  TAP A DAY");
        long now = System.currentTimeMillis();
        for (int i = 0; i < times.size(); i++) {
            long[] t = times.get(i);
            if (t[1] < now) continue;
            String[] in = info.get(i);
            LocalDate day = LocalDate.parse(in[2]);
            if (day.isBefore(today)) day = today;
            String dayLabel = day.equals(today) ? "Today" : day.equals(today.plusDays(1)) ? "Tomorrow" : day.format(DateTimeFormatter.ofPattern("EEE M/d", Locale.US));
            LocalDateTime st = Instant.ofEpochMilli(t[0]).atZone(z).toLocalDateTime();
            String when = t[2] == 1 ? "all day" : Widgets.clock(st.getHour() * 60 + st.getMinute());
            String col = String.format("#%06X", 0xFFFFFF & (int) t[3]);
            items.put(new JSONArray().put("row").put(in[0]).put(when).put(dayLabel + (in[1].isEmpty() ? "" : " · " + in[1])).put(col).put(""));
            shown++;
        }
        if (ok && shown == 0) items.put(new JSONArray().put("note").put("Nothing in the next two weeks."));
        calList(c, v, wid, items);
        return v;
    }

    /** The calendar's list scrolls (all of it, no "+N more"): its rows are kept for ListService kind "cal". */
    private static void calList(Context c, RemoteViews v, int wid, JSONArray items) {
        WidgetData.prefs(c).edit().putString("cal_rows_" + wid, items.toString()).apply();
        Widgets.list(c, v, wid, R.id.cal_list, R.id.cal_empty, "cal");
    }

    // ================= Steps (Health Connect; Samsung Health or Google Fit can sync into it) =================

    /** Daily goals, set in the app: steps (10,000), active minutes (90) and active calories (500), Samsung Health's defaults for the last two. */
    static int goal(Context c) { return Math.max(500, WidgetData.prefs(c).getInt("steps_goal", 10000)); }
    static int goalMin(Context c) { return Math.max(5, WidgetData.prefs(c).getInt("steps_goal_min", 90)); }
    static int goalKcal(Context c) { return Math.max(50, WidgetData.prefs(c).getInt("steps_goal_kcal", 500)); }

    private static int pctOf(long v, int g) { return v <= 0 ? 0 : (int) Math.min(100, v * 100 / g); }

    /** A day's three rings: outer steps, middle active time, center active calories, each filling toward its goal. */
    private static void rings(Context c, RemoteViews v, int r1, int r2, int r3, long steps, long min, long kcal) {
        v.setProgressBar(r1, 100, pctOf(steps, goal(c)), false);
        v.setProgressBar(r2, 100, pctOf(min, goalMin(c)), false);
        v.setProgressBar(r3, 100, pctOf(kcal, goalKcal(c)), false);
    }

    /** The last n days as [date, steps, active minutes, active kcal] (Health Connect and the import combined), skipping days with nothing. */
    static JSONArray activityDays(Context c, int n) {
        android.content.SharedPreferences p = WidgetData.prefs(c);
        Map<String, JSONArray> byDay = new HashMap<>();
        JSONObject imp = null;
        try {
            JSONArray days = new JSONArray(p.getString("steps", "[]"));
            for (int i = 0; i < days.length(); i++) byDay.put(days.getJSONArray(i).optString(0), days.getJSONArray(i));
        } catch (Exception ignored) {}
        try { String si = p.getString("steps_import", ""); if (!si.isEmpty()) imp = new JSONObject(si); } catch (Exception ignored) {}
        JSONArray out = new JSONArray();
        LocalDate today = LocalDate.now();
        for (int i = n - 1; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            long[] v = dayVals(p, byDay, imp, d, i == 0);
            if (v[0] < 0 && v[1] < 0 && v[2] < 0) continue;
            out.put(new JSONArray().put(d.toString()).put(v[0]).put(v[1]).put(v[2]));
        }
        return out;
    }

    /** A day's steps, active minutes and active kcal: Health Connect's, or the Samsung Health import's where that's higher (-1 = none). */
    private static long[] dayVals(android.content.SharedPreferences p, Map<String, JSONArray> byDay, JSONObject imp, LocalDate d, boolean today) {
        JSONArray y = byDay.get(d.toString());
        long s = y == null ? -1 : y.optLong(1, -1), k = y == null ? -1 : y.optLong(2, -1), m = y == null ? -1 : y.optLong(4, -1);
        if (today && m < 0 && s >= 0) m = p.getLong("steps_active_min", -1);
        JSONArray q = imp == null ? null : imp.optJSONArray(d.toString());
        if (q != null) { s = Math.max(s, q.optLong(0, -1)); m = Math.max(m, q.optLong(1, -1)); k = Math.max(k, q.optLong(2, -1)); }
        return new long[]{s, m, k};
    }

    static RemoteViews steps(Context c, int wid) {
        // Tall widget: big rings in the middle with the numbers in a row under them; otherwise the rings sit beside the numbers.
        int[] sz = Widgets.sizeDp(c, wid);
        boolean tall = sz[1] >= 300 && sz[1] > sz[0] * 0.8f;
        RemoteViews v = new RemoteViews(c.getPackageName(), tall ? R.layout.widget_steps_tall : R.layout.widget_steps);
        float k = Widgets.scale(c, wid);
        android.content.SharedPreferences p = WidgetData.prefs(c);
        long at = p.getLong("steps_at", 0);
        header(c, v, StepsWidget.class, ago(at));
        String raw = p.getString("steps", ""), err = p.getString("steps_err", "");
        // "BG": Health Connect wouldn't let the 30-minute refresh read (only open apps may, unless you allow background reading).
        boolean stale = System.currentTimeMillis() - at > 2 * 3600_000L;
        if (err.equals("BG")) err = stale ? "Tap ⟳ Sync to update your steps (Health Connect lets apps read them only while open unless you allow background reading)." : "";
        Map<String, JSONArray> byDay = new HashMap<>();
        boolean none = true;
        try {
            JSONArray days = new JSONArray(raw.isEmpty() ? "[]" : raw);   // [date, steps, active kcal, -1, active minutes]; -1 = no data
            for (int i = 0; i < days.length(); i++) {
                JSONArray x = days.getJSONArray(i);
                byDay.put(x.optString(0), x);
                if (x.optLong(1) > 0) none = false;
            }
        } catch (Exception ignored) {}
        JSONObject imp = null;   // the Samsung Health import, {date: [steps, active minutes, kcal]}
        try {
            String si = p.getString("steps_import", "");
            if (!si.isEmpty()) { imp = new JSONObject(si); if (imp.length() > 0) none = false; }
        } catch (Exception ignored) {}
        LocalDate today = LocalDate.now(), first = today.minusDays(29);
        String w = String.valueOf(wid);
        // The day shown big (today unless you tapped another) and which week the strip shows; both go back to today on a new day.
        LocalDate sel = today;
        int page = 0;
        if (today.toString().equals(p.getString("steps_sel_on_" + wid, ""))) {
            try { String x = p.getString("steps_sel_" + wid, ""); if (!x.isEmpty()) sel = LocalDate.parse(x); } catch (Exception ignored) {}
            page = Math.max(0, Math.min(4, p.getInt("steps_page_" + wid, 0)));
        }
        if (sel.isAfter(today) || sel.isBefore(first)) sel = today;
        long[] sv = dayVals(p, byDay, imp, sel, sel.equals(today));
        long steps = sv[0], min = sv[1], kcal = sv[2];
        rings(c, v, R.id.sp_r1, R.id.sp_r2, R.id.sp_r3, steps, min, kcal);
        v.setTextViewText(R.id.sp_day, sel.equals(today) ? "TODAY" : sel.equals(today.minusDays(1)) ? "YESTERDAY"
                : sel.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US)).toUpperCase(Locale.US));
        v.setTextViewText(R.id.sp_steps, steps < 0 ? "–" : String.format(Locale.US, "%,d", steps));
        v.setTextViewText(R.id.sp_steps_l, String.format(Locale.US, tall ? "of %,d steps" : "/ %,d steps", goal(c)));
        v.setTextViewText(R.id.sp_active, min < 0 ? "–" : "≈ " + min);
        v.setTextViewText(R.id.sp_active_l, (tall ? "of " : "/ ") + goalMin(c) + " min active");
        v.setTextViewText(R.id.sp_kcal, kcal < 0 ? "–" : String.format(Locale.US, "%,d", kcal));
        v.setTextViewText(R.id.sp_kcal_l, String.format(Locale.US, tall ? "of %,d active kcal" : "/ %,d active kcal", goalKcal(c)));
        for (int id : new int[]{R.id.sp_steps, R.id.sp_active, R.id.sp_kcal}) Widgets.ts(v, id, tall ? 24 : 20, k);
        for (int id : new int[]{R.id.sp_steps_l, R.id.sp_active_l, R.id.sp_kcal_l}) Widgets.ts(v, id, tall ? 10.5f : 11, k);
        Widgets.ts(v, R.id.sp_day, tall ? 13 : 12, k);
        // The small rings in the strip: as big as a seventh of the width allows.
        float cellW = (sz[0] - 26 - 60) / 7f, mini = Math.max(30, Math.min(cellW - 6, tall ? 56 : 44));
        // The week strip: a small set of rings per day; tap one to show it big, ‹ › for the weeks before (30 days are kept).
        v.removeAllViews(R.id.sp_strip);
        LocalDate end = today.minusDays(7L * page);
        for (LocalDate d = end.minusDays(6); !d.isAfter(end); d = d.plusDays(1)) {
            RemoteViews cell = new RemoteViews(c.getPackageName(), R.layout.item_ring_day);
            if (Build.VERSION.SDK_INT >= 31) sizeRings(cell, R.id.rd_rings, R.id.rd_2, R.id.rd_3, mini, 36, 5, 10);
            Widgets.ts(cell, R.id.rd_lab, 10, k);
            Widgets.ts(cell, R.id.rd_num, 11, k);
            long[] dv = dayVals(p, byDay, imp, d, d.equals(today));
            rings(c, cell, R.id.rd_1, R.id.rd_2, R.id.rd_3, dv[0], dv[1], dv[2]);
            cell.setTextViewText(R.id.rd_lab, d.equals(today) ? "TODAY" : d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.US).toUpperCase(Locale.US));
            cell.setTextViewText(R.id.rd_num, String.valueOf(d.getDayOfMonth()));
            boolean inRange = !d.isBefore(first);
            cell.setTextColor(R.id.rd_num, !inRange ? 0xFF4A525E : d.equals(sel) ? 0xFF74C35E : Widgets.INK);
            if (d.equals(sel)) cell.setInt(R.id.rd_cell, "setBackgroundResource", R.drawable.soft_plain);
            if (inRange) Pages.click(c, cell, R.id.rd_cell, "op", "stepday", "wid", w, "to", d.equals(today) ? "" : d.toString());
            v.addView(R.id.sp_strip, cell);
        }
        v.setTextColor(R.id.sp_prev, page < 4 ? Widgets.INK : 0xFF4A525E);
        v.setTextColor(R.id.sp_next, page > 0 ? Widgets.INK : 0xFF4A525E);
        if (page < 4) Pages.click(c, v, R.id.sp_prev, "op", "steppage", "wid", w, "to", String.valueOf(page + 1));
        if (page > 0) Pages.click(c, v, R.id.sp_next, "op", "steppage", "wid", w, "to", String.valueOf(page - 1));
        // A note when something's missing.
        String nt = err;
        if (nt.isEmpty() && raw.isEmpty() && imp == null) nt = "Open Tracker Widgets and tap Connect next to Steps (Health Connect), then ⟳ Sync.";
        else if (nt.isEmpty() && none)
            nt = "Health Connect has no steps from Samsung Health yet. In Tracker Widgets, tap Check Samsung Health's access, allow Samsung Health to share "
                    + "Steps and Active calories, then open Samsung Health once so it sends them, and tap ⟳ Sync.";
        else if (nt.isEmpty() && !p.getBoolean("steps_all", true))
            nt = "For calories and active time, open Tracker Widgets and tap More next to Steps.";
        long firstEntry = p.getLong("steps_first", 0);
        ZoneId zz = ZoneId.systemDefault();
        if (nt.isEmpty() && sel.equals(today) && firstEntry > today.atStartOfDay(zz).plusHours(5).toInstant().toEpochMilli())
            nt = "Health Connect only has today's steps from " + Instant.ofEpochMilli(firstEntry).atZone(zz).format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
                    + " on, so today is short of Samsung Health's count.";
        note(v, R.id.sp_note, nt);
        // The big rings fill what's left: the width (tall) or half of it (beside the numbers), and the height after everything else.
        if (Build.VERSION.SDK_INT >= 31) {
            float rest = (nt.isEmpty() ? 0 : 64) + (mini + 40) + 40 * k;   // note, strip, header and padding
            float big = tall ? Math.min(sz[0] - 40, sz[1] - rest - (24 + 62) * k) : Math.min(sz[0] * 0.48f, sz[1] - rest - 12);
            sizeRings(v, R.id.sp_rings, R.id.sp_r2, R.id.sp_r3, Math.max(96, Math.min(big, 340)), 128, 15, 30);
        }
        return v;
    }

    /** Sets a set of rings to a size; the middle and inner rings' insets grow with it (the drawables are drawn for `base` dp). */
    @androidx.annotation.RequiresApi(31)
    private static void sizeRings(RemoteViews v, int frame, int mid, int in, float size, float base, float midInset, float inInset) {
        v.setViewLayoutWidth(frame, size, TypedValue.COMPLEX_UNIT_DIP);
        v.setViewLayoutHeight(frame, size, TypedValue.COMPLEX_UNIT_DIP);
        float a = size * midInset / base, b = size * inInset / base;
        for (int t : new int[]{RemoteViews.MARGIN_LEFT, RemoteViews.MARGIN_TOP, RemoteViews.MARGIN_RIGHT, RemoteViews.MARGIN_BOTTOM}) {
            v.setViewLayoutMargin(mid, t, a, TypedValue.COMPLEX_UNIT_DIP);
            v.setViewLayoutMargin(in, t, b, TypedValue.COMPLEX_UNIT_DIP);
        }
    }
}
