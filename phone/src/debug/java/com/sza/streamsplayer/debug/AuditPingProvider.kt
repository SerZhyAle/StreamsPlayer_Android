package com.sza.streamsplayer.debug

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * S0025 decision 14.2: the main-Looper ping of the dynamic audit rig, the counterpart of a WM_NULL ping. Debug builds
 * only: this source set is not part of any release package, and it counts nothing (no instance or subscription
 * registries). The rig (`tools/audit/rig.py ping`) sends
 *
 *     adb shell am broadcast -a com.sza.streamsplayer.debug.AUDIT_PING -p <package> --ei seq <n> --es run <nonce>
 *
 * The broadcast lands on a background thread, which posts one message to the main Looper and stamps both ends on the
 * device clock; the difference is how long the main thread kept that message waiting:
 *
 *     SpAuditPing: pong run=<nonce> seq=<n> queue_ms=<ms> uptime_ms=<device uptime when the main thread ran it>
 *
 * Only a sender holding DUMP (the adb shell, the system) can trigger it. The receiver is registered from a provider
 * because a manifest receiver runs on the main thread, which is the thread being measured.
 */
class AuditPingProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        val context = context ?: return false
        val thread = HandlerThread(THREAD).apply { start() }
        ContextCompat.registerReceiver(
            context,
            PingReceiver(Handler(Looper.getMainLooper())),
            IntentFilter(ACTION),
            Manifest.permission.DUMP,
            Handler(thread.looper),
            ContextCompat.RECEIVER_EXPORTED,
        )
        return true
    }

    private class PingReceiver(private val main: Handler) : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val run = intent.getStringExtra(EXTRA_RUN) ?: "-"
            val seq = intent.getIntExtra(EXTRA_SEQ, -1)
            val posted = SystemClock.uptimeMillis()
            main.post {
                val ran = SystemClock.uptimeMillis()
                Log.i(TAG, "pong run=$run seq=$seq queue_ms=${ran - posted} uptime_ms=$ran")
            }
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        const val ACTION = "com.sza.streamsplayer.debug.AUDIT_PING"
        const val EXTRA_SEQ = "seq"
        const val EXTRA_RUN = "run"
        private const val TAG = "SpAuditPing"
        private const val THREAD = "SpAuditPing"
    }
}
