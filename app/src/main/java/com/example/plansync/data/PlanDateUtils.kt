package com.example.plansync.data

import java.util.Calendar
import java.util.Date
import java.util.TimeZone

fun planDateTime(pickedMillis: Long, hour: Int, minute: Int): Date {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    utc.timeInMillis = pickedMillis

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
