package com.example.plansync.data

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object LocalStorageProvider {

    private lateinit var dir: File
    private val mutex = Mutex()

    fun init(context: Context) {
        dir = context.applicationContext.filesDir
    }

    suspend fun read(name: String): String? = withContext(Dispatchers.IO) {
        mutex.withLock { File(dir, name).takeIf { it.exists() }?.readText() }
    }

    suspend fun write(name: String, content: String) = withContext(Dispatchers.IO) {
        mutex.withLock { File(dir, name).writeText(content) }
    }

    suspend fun update(name: String, transform: (String?) -> String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val file = File(dir, name)
            file.writeText(transform(file.takeIf { it.exists() }?.readText()))
        }
    }
}
