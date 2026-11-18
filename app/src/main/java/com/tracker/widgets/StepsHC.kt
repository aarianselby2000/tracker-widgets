package com.tracker.widgets

import android.content.Context
import android.content.Intent
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Steps, calories and active time from Health Connect (Samsung Health, Google Fit and others sync into it), for the Steps widget.
 * Health Connect lets an app read only while it's open unless background reading is allowed, so the widget's ⟳ Sync
 * (SyncActivity) reads while briefly "open".
 */
object StepsHC {
    private const val BACKGROUND = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
    private val READ = HealthPermission.getReadPermission(StepsRecord::class)
    private val ACTIVE = HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
    private val TOTAL = HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)
    private val EXERCISE = HealthPermission.getReadPermission(ExerciseSessionRecord::class)

    // Background reading is asked for on its own: in the same request it can make Health Connect turn everything down.
    @JvmField val PERMISSIONS: Set<String> = setOf(READ, ACTIVE, TOTAL, EXERCISE)

    private val NAMES = mapOf(
        "com.sec.android.app.shealth" to "Samsung Health",
        "com.samsung.android.wear.shealth" to "Galaxy Watch",
        "com.google.android.apps.fitness" to "Google Fit",
        "com.fitbit.FitbitMobile" to "Fitbit",
    )

    @JvmStatic fun status(c: Context): Int = HealthConnectClient.getSdkStatus(c)

    @JvmStatic fun available(c: Context): Boolean = status(c) == HealthConnectClient.SDK_AVAILABLE

    @JvmStatic fun permissionIntent(c: Context): Intent =
        PermissionController.createRequestPermissionResultContract().createIntent(c, PERMISSIONS)

    @JvmStatic fun backgroundIntent(c: Context): Intent =
        PermissionController.createRequestPermissionResultContract().createIntent(c, setOf(BACKGROUND))

    private fun grantedSet(c: Context): Set<String> = try {
        if (available(c)) runBlocking { HealthConnectClient.getOrCreate(c).permissionController.getGrantedPermissions() } else emptySet()
    } catch (e: Exception) { emptySet() }

    @JvmStatic fun grantedBackground(c: Context): Boolean = grantedSet(c).contains(BACKGROUND)

    /** Steps can be read. */
    @JvmStatic fun granted(c: Context): Boolean = grantedSet(c).contains(READ)

    /** Steps, calories and exercise can all be read. */
    @JvmStatic fun grantedAll(c: Context): Boolean = grantedSet(c).containsAll(PERMISSIONS)

    /** Health Connect's page for this app's permissions (or its main settings on older phones). */
    @JvmStatic fun settingsIntent(c: Context): Intent =
        if (android.os.Build.VERSION.SDK_INT >= 34)
            Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS").putExtra(Intent.EXTRA_PACKAGE_NAME, c.packageName)
        else Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)

    /**
     * Reads the last 7 days (steps, active and total calories) and today's active time into the widget's saved data.
     * Returns null when it worked, or why it didn't ("BG" = refused because the app wasn't open).
     */
    @JvmStatic fun refresh(c: Context): String? {
        val p = WidgetData.prefs(c)
        if (!available(c)) { p.edit().putString("steps_err", "Health Connect isn't set up on this phone.").apply(); return "Health Connect isn't set up on this phone." }
        val err: String? = try {
            runBlocking {
                val client = HealthConnectClient.getOrCreate(c)
                val got = client.permissionController.getGrantedPermissions()
                if (!got.contains(READ)) {
                    "Open Tracker Widgets and tap Connect next to Steps."
                } else {
                    val z = ZoneId.systemDefault()
                    val today = LocalDate.now()
                    val out = JSONArray()
                    val sources = linkedSetOf<String>()
                    // The last 30 days, one bucket per day. A day with nothing in Health Connect has no count: kept as -1 ("no data").
                    val range = TimeRangeFilter.between(today.minusDays(29).atStartOfDay(), java.time.LocalDateTime.now())
                    val stepsBy = HashMap<String, Long>()
                    for (bk in client.aggregateGroupByPeriod(AggregateGroupByPeriodRequest(setOf(StepsRecord.COUNT_TOTAL), range, java.time.Period.ofDays(1)))) {
                        val n = bk.result[StepsRecord.COUNT_TOTAL] ?: continue
                        stepsBy[bk.startTime.toLocalDate().toString()] = n
                        for (o in bk.result.dataOrigins) sources.add(NAMES[o.packageName] ?: o.packageName.substringAfterLast('.'))
                    }
                    val kcalBy = HashMap<String, Long>()
                    if (got.contains(ACTIVE))
                        for (bk in client.aggregateGroupByPeriod(AggregateGroupByPeriodRequest(setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), range, java.time.Period.ofDays(1)))) {
                            val e = bk.result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL] ?: continue
                            kcalBy[bk.startTime.toLocalDate().toString()] = Math.round(e.inKilocalories)
                        }
                    // Active time per day, estimated from step entries and workouts. A past day doesn't change once it's over, so it's
                    // kept; today and yesterday are worked out again each time. Row: [date, steps, active kcal, -1, active minutes].
                    val amOld = try { org.json.JSONObject(p.getString("steps_am", "{}") ?: "{}") } catch (e: Exception) { org.json.JSONObject() }
                    val amNew = org.json.JSONObject()
                    for (i in 29 downTo 0) {
                        val day = today.minusDays(i.toLong())
                        val d = day.toString()
                        val am = when {
                            i >= 2 && amOld.optLong(d, -1L) >= 0 -> amOld.optLong(d)
                            !stepsBy.containsKey(d) -> -1L
                            else -> try {
                                activeMinutes(client, got, day.atStartOfDay(z).toInstant(), if (i == 0) Instant.now() else day.plusDays(1).atStartOfDay(z).toInstant())
                            } catch (e: Exception) { -1L }
                        }
                        amNew.put(d, am)
                        out.put(JSONArray().put(d).put(stepsBy[d] ?: -1L).put(if (got.contains(ACTIVE)) kcalBy[d] ?: 0L else -1L).put(-1L).put(am))
                    }
                    p.edit().putString("steps_am", amNew.toString()).apply()
                    p.edit().putBoolean("steps_all", got.containsAll(PERMISSIONS)).apply()
                    val dayStart = today.atStartOfDay(z).toInstant()
                    p.edit().putString("steps", out.toString()).putLong("steps_at", System.currentTimeMillis())
                        .putString("steps_src", sources.joinToString(" + ")).putLong("steps_active_min", activeMinutes(client, got, dayStart))
                        .putString("steps_diag", diag(client, got, dayStart, p)).apply()
                    ActivitySend.maybe(c, false)   // on to the Tracker's Health section when it changed
                    null
                }
            }
        } catch (e: SecurityException) {
            "BG"
        } catch (e: Exception) {
            "Couldn't read steps (" + e.javaClass.simpleName + ")."
        }
        p.edit().putString("steps_err", err ?: "").apply()
        return err
    }

    private fun who(pk: Collection<String>): String =
        if (pk.isEmpty()) "no app" else pk.toSet().joinToString(", ") { NAMES[it] ?: it }

    /**
     * What Health Connect holds for today, per app, to show when the numbers look wrong: how many steps in how many entries,
     * from when to when (Samsung Health may only share what it counted after it was connected), and how long entries are.
     */
    private suspend fun diag(client: HealthConnectClient, got: Set<String>, start: Instant, p: android.content.SharedPreferences): String {
        val range = TimeRangeFilter.between(start, Instant.now())
        val z = ZoneId.systemDefault()
        val fmt = java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.US)
        fun t(i: Instant): String = i.atZone(z).format(fmt)
        val parts = mutableListOf<String>()
        val st = client.readRecords(ReadRecordsRequest(recordType = StepsRecord::class, timeRangeFilter = range, pageSize = 5000)).records
        // When today's first step entry starts, for the widget's "only has steps since" note.
        p.edit().putLong("steps_first", if (st.isEmpty()) 0L else st.minOf { it.startTime }.toEpochMilli()).apply()
        if (st.isEmpty()) parts.add("no step entries")
        for ((pk, rs) in st.groupBy { it.metadata.dataOrigin.packageName }) {
            val mins = rs.map { (it.endTime.toEpochMilli() - it.startTime.toEpochMilli()) / 60000 }.sorted()
            parts.add((NAMES[pk] ?: pk) + ": " + String.format(java.util.Locale.US, "%,d", rs.sumOf { it.count }) + " steps in " + rs.size +
                (if (rs.size == 1) " entry, " else " entries, ") + t(rs.minOf { it.startTime }) + "–" + t(rs.maxOf { it.endTime }) +
                ", each about " + mins[mins.size / 2] + " min")
        }
        if (got.contains(ACTIVE)) {
            val ac = client.readRecords(ReadRecordsRequest(recordType = ActiveCaloriesBurnedRecord::class, timeRangeFilter = range, pageSize = 2000)).records
            if (ac.isEmpty()) parts.add("no active-calorie entries")
            for ((pk, rs) in ac.groupBy { it.metadata.dataOrigin.packageName })
                parts.add((NAMES[pk] ?: pk) + " active calories: " + Math.round(rs.sumOf { it.energy.inKilocalories }) + " kcal, " +
                    t(rs.minOf { it.startTime }) + "–" + t(rs.maxOf { it.endTime }))
        }
        return "Today in Health Connect · " + parts.joinToString(" · ")
    }

    /**
     * Today's active time, estimated (Health Connect has no "active time" of its own): logged workouts, plus walking outside them,
     * counted from the busiest step source's records at 60+ steps a minute.
     */
    private suspend fun activeMinutes(client: HealthConnectClient, got: Set<String>, start: Instant, end: Instant = Instant.now()): Long {
        val range = TimeRangeFilter.between(start, end)
        val ex = if (got.contains(EXERCISE))
            client.readRecords(ReadRecordsRequest(recordType = ExerciseSessionRecord::class, timeRangeFilter = range, pageSize = 200)).records
                .map { it.startTime.toEpochMilli() to it.endTime.toEpochMilli() }.sortedBy { it.first }
        else emptyList()
        var exMs = 0L
        var upTo = 0L
        for ((a, b) in ex) { val s = maxOf(a, upTo); if (b > s) exMs += b - s; upTo = maxOf(upTo, b) }
        val recs = client.readRecords(ReadRecordsRequest(recordType = StepsRecord::class, timeRangeFilter = range, pageSize = 5000)).records
        val main = recs.groupBy { it.metadata.dataOrigin.packageName }.maxByOrNull { e -> e.value.sumOf { it.count } }?.value ?: emptyList()
        var walk = 0L
        for (r in main) {
            val a = r.startTime.toEpochMilli()
            val b = r.endTime.toEpochMilli()
            val ms = b - a
            if (ms <= 0 || ex.any { a < it.second && b > it.first }) continue
            if (r.count * 60000.0 / ms >= 60) walk += if (ms <= 15 * 60000) ms else (r.count / 100.0 * 60000).toLong()
        }
        return (exMs + walk) / 60000
    }
}
