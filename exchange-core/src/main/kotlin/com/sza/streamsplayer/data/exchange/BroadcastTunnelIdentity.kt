package com.sza.streamsplayer.data.exchange

import javax.net.ssl.SSLContext

/** The TLS server identity of one broadcast's tunnel: its context and the `SHA256:` pin of its leaf. */
class BroadcastTunnelIdentity(val context: SSLContext, val pin: String)

/**
 * Provider of one-off tunnel TLS identities for live broadcasts (contract DEVICE-EXCHANGE 27 item DM).
 */
interface BroadcastTunnelIdentityProvider {
    fun ensure(broadcastId: String): BroadcastTunnelIdentity?
    fun current(broadcastId: String): BroadcastTunnelIdentity?
    fun end()
}
