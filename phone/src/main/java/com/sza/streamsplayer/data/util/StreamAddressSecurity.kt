package com.sza.streamsplayer.data.util

import android.net.Uri

/**
 * Checks stream URLs for embedded user credentials before sharing or copying.
 */
object StreamAddressSecurity {

    /**
     * Returns true if [url] carries credentials (e.g. `http://user:pass@host/path`).
     */
    fun hasCredentials(url: String): Boolean {
        return try {
            val uri = Uri.parse(url)
            val userInfo = uri.userInfo
            !userInfo.isNullOrBlank()
        } catch (_: Exception) {
            false
        }
    }
}
