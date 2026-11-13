package com.sza.streamsplayer.data.exchange.publish

import com.sza.streamsplayer.data.broadcast.BroadcastEndpointDto

/**
 * What a broadcast published through the exchange server announces right now: its id and the endpoint list the
 * server's record carries (LAN first, then `RELAY` and `TUNNEL`). Another announcement channel of the same
 * broadcast - the Google Drive announcement of S0029 - writes exactly this list and this id, so a listener
 * that sees the broadcast through both finds one broadcast, not two.
 */
data class PublishedEndpoints(
    val broadcastId: String,
    val endpoints: List<BroadcastEndpointDto>,
) {
    /** The id is a capability (7.9): it stays out of logs and out of `toString`. */
    override fun toString(): String = "PublishedEndpoints(endpoints=${endpoints.size})"
}
