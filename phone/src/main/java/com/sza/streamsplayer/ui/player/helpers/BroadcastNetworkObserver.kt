package com.sza.streamsplayer.ui.player.helpers

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import java.io.Closeable

/** Restarts a broadcast after the default network changes, excluding the initial registration. */
class BroadcastNetworkObserver(context: Context, private val onChanged: () -> Unit) : Closeable {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var closed = false
    private var lastNetwork = manager.activeNetwork
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            handler.post {
                if (!closed && lastNetwork != network) {
                    lastNetwork = network
                    onChanged()
                }
            }
        }
        override fun onLost(network: Network) {
            handler.post { if (lastNetwork == network) lastNetwork = null }
        }
    }

    init { manager.registerDefaultNetworkCallback(callback) }

    override fun close() {
        closed = true
        manager.unregisterNetworkCallback(callback)
        handler.removeCallbacksAndMessages(null)
    }
}
