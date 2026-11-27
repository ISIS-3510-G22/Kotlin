package com.example.plansync.service

import android.os.Build
import android.os.Trace
import java.util.concurrent.atomic.AtomicInteger

object PerfTrace {
    private val cookies = AtomicInteger()

    suspend fun <T> traced(name: String, block: suspend () -> T): T {
        val cookie = cookies.incrementAndGet()
        if (Build.VERSION.SDK_INT >= 29) Trace.beginAsyncSection(name, cookie)
        return try {
            block()
        } finally {
            if (Build.VERSION.SDK_INT >= 29) Trace.endAsyncSection(name, cookie)
        }
    }
}
