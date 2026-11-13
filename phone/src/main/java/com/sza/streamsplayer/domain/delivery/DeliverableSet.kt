package com.sza.streamsplayer.domain.delivery

/**
 * Heavy artifact groups delivered on demand (S0386 strategic §5.4).
 *
 * - [CHANNEL_PREVIEW_ATLAS]: on-demand stream channel-preview sprite sheet + url->index sidecar (S1154).
 * - [STREAM_LOGO_ATLAS]: on-demand stream logo sprite sheet + url->index sidecar (S1201). Covers what
 *   the preview atlas structurally cannot - a station with no video track has no frame to capture.
 */
enum class DeliverableSet {
    CHANNEL_PREVIEW_ATLAS,
    STREAM_LOGO_ATLAS
}
