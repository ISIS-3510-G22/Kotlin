package com.example.plansync.data

import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/**
 * Data layer utility — converts the UTC-midnight millis that the Material3
 * DatePicker emits, plus a local hour and minute, into a [Date] for Firestore.
 *
 * Without this helper, code that does:
 *   Calendar.getInstance().apply { timeInMillis = pickedMillis; set(HOUR_OF_DAY, hour) }
 * interprets the UTC date components in the device's local timezone, landing
 * one day early in negative-offset zones like America/Bogotá (UTC-5).
 *
 * Fix: extract year/month/day in UTC (matching the picker's own frame of
 * reference), then rebuild those components in local time with the chosen hour.
 *
 * Used by both [com.example.plansync.viewmodel.CreatePlanViewModel] (online path)
 * and [com.example.plansync.service.OfflineSyncService] (queued sync path).
 */
fun planDateTime(pickedMillis: Long, hour: Int, minute: Int): Date {
    // Step 1 — decode the picker millis in UTC (that is the picker's own timezone)
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    utc.timeInMillis = pickedMillis

    // Step 2 — rebuild in the device's local timezone so the wall-clock date matches
    val local = Calendar.getInstance()
    local.clear()
    local.set(
        utc.get(Calendar.YEAR),
        utc.get(Calendar.MONTH),
        utc.get(Calendar.DAY_OF_MONTH),
        hour,
        minute,
        0
    )
    return local.time
}
