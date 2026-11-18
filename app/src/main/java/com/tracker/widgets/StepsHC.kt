package com.tracker.widgets

import android.content.Context
import android.content.Intent
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Steps from Health Connect (Samsung Health, Google Fit and others can sync into it), for the Steps widget.
 * Reading from a widget update counts as "in the background", which needs the background-read permission too;
 * without it, steps update whenever the Tracker Widgets app is opened.
 */
object StepsHC {
    private const val BACKGROUND = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
    private val READ = HealthPermission.getReadPermission(StepsRecord::class)

    @JvmField val PERMISSIONS: Set<String> = setOf(READ, BACKGROUND)

    @JvmStatic fun status(c: Context): Int = HealthConnectClient.getSdkStatus(c)

    @JvmStatic fun available(c: Context): Boolean = status(c) == HealthConnectClient.SDK_AVAILABLE

    @JvmStatic fun permissionIntent(c: Context): Intent =
        PermissionController.createRequestPermissionResultContract().createIntent(c, PERMISSIONS)

    @JvmStatic fun granted(c: Context): Boolean = try {
        available(c) && runBlocking { HealthConnectClient.getOrCreate(c).permissionController.getGrantedPermissions().contains(READ) }
    } catch (e: Exception) { false }

    /** Reads the last 7 days into the widget's saved data. Returns null when it worked, or why it didn't. */
    @JvmStatic fun refresh(c: Context): String? {
        if (!available(c)) return "Health Connect isn't set up on this phone."
        val err: String? = try {
            runBlocking {
                val client = HealthConnectClient.getOrCreate(c)
                if (!client.permissionController.getGrantedPermissions().contains(READ)) {
                    "Open Tracker Widgets and tap Connect steps."
                } else {
                    val z = ZoneId.systemDefault()
                    val today = LocalDate.now()
                    val out = JSONArray()
                    for (i in 6 downTo 0) {
                        val d = today.minusDays(i.toLong())
                        val end = if (i == 0) Instant.now() else d.plusDays(1).atStartOfDay(z).toInstant()
                        val res = client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(d.atStartOfDay(z).toInstant(), end)))
                        out.put(JSONArray().put(d.toString()).put(res[StepsRecord.COUNT_TOTAL] ?: 0L))
                    }
                    WidgetData.prefs(c).edit().putString("steps", out.toString()).putLong("steps_at", System.currentTimeMillis()).apply()
                    null
                }
            }
        } catch (e: SecurityException) {
            "Steps update when you open Tracker Widgets (allow background reading in Health Connect to update on their own)."
        } catch (e: Exception) {
            "Couldn't read steps (" + e.javaClass.simpleName + ")."
        }
        WidgetData.prefs(c).edit().putString("steps_err", err ?: "").apply()
        return err
    }
}
