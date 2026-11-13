package com.sza.streamsplayer.data.exchange.drive

import android.content.Context
import com.sza.streamsplayer.data.exchange.DeviceIdGenerator
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Drive channel's two local facts. Neither is a secret: the sign-in flag only records that the person
 * agreed, and the device id names this phone in the records it writes. No token and no account address is kept.
 */
@Singleton
class SharedPrefsDriveChannelStore @Inject constructor(
    @ApplicationContext context: Context,
) : DriveChannelStore {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override var signedIn: Boolean
        get() = prefs.getBoolean(KEY_SIGNED_IN, false)
        set(value) = prefs.edit().putBoolean(KEY_SIGNED_IN, value).apply()

    override var noticeShown: Boolean
        get() = prefs.getBoolean(KEY_NOTICE_SHOWN, false)
        set(value) = prefs.edit().putBoolean(KEY_NOTICE_SHOWN, value).apply()

    @get:Synchronized
    override val deviceId: String
        get() {
            val held = prefs.getString(KEY_DEVICE_ID, null)
            if (DeviceIdGenerator.isValid(held)) return checkNotNull(held)
            return DeviceIdGenerator.generate().also { prefs.edit().putString(KEY_DEVICE_ID, it).apply() }
        }

    private companion object {
        const val FILE = "drive_channel"
        const val KEY_SIGNED_IN = "signed_in"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_NOTICE_SHOWN = "notice_shown"
    }
}
