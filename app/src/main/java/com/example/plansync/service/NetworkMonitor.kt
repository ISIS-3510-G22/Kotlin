package com.example.plansync.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Service layer — network connectivity monitoring.
 *
 * Singleton that wraps ConnectivityManager and exposes a [Flow<Boolean>]
 * so any ViewModel can observe online/offline state reactively.
 *
 * Design patterns:
 *  - Singleton: one instance per app process, obtained via [NetworkMonitor.get]
 *  - Observer pattern: callbackFlow bridges ConnectivityManager callbacks to
 *    Kotlin Flow, collected by ViewModels via collectAsStateWithLifecycle()
 */
class NetworkMonitor private constructor(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /** True when the device has a validated internet connection. */
    val isOnline: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(true) }
            override fun onLost(network: Network)      { trySend(false) }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        // Emit initial state so collectors don't wait for a change event.
        trySend(isCurrentlyOnline())

        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    /** Synchronous snapshot — use [isOnline] Flow for reactive observation. */
    fun isCurrentlyOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    companion object {
        @Volatile private var instance: NetworkMonitor? = null

        fun get(context: Context): NetworkMonitor =
            instance ?: synchronized(this) {
                instance ?: NetworkMonitor(context.applicationContext).also { instance = it }
            }
    }
}
