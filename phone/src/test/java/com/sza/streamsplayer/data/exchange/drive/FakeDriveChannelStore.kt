package com.sza.streamsplayer.data.exchange.drive

class FakeDriveChannelStore(
    override var signedIn: Boolean = true,
    override val deviceId: String = OWN_DEVICE_ID,
    override var noticeShown: Boolean = false,
) : DriveChannelStore {
    companion object {
        /** 22 characters of the share id form, like a real minted id. */
        const val OWN_DEVICE_ID = "OWNOWNOWNOWNOWNOWNOWNO"
    }
}

/** A token source that hands out one canary token; the canary must never reach a log. */
class CanaryTokenSource(var token: String = TOKEN) : DriveTokenSource {
    val invalidated = mutableListOf<String>()

    override suspend fun token(): String = token

    override suspend fun invalidate(token: String) {
        invalidated += token
    }

    companion object {
        const val TOKEN = "ya29.CANARY-ACCESS-TOKEN-must-not-be-logged"
    }
}
