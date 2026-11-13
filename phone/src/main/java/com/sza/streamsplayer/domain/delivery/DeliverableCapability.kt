package com.sza.streamsplayer.domain.delivery

/**
 * Per-[DeliverableSet] availability state (S0386 strategic Pillar A).
 *
 * - [NOT_INSTALLED]: not downloaded - needs delivery before use.
 * - [INSTALLED]: already downloaded - usable.
 */
enum class DeliverableCapability {
    NOT_INSTALLED,
    INSTALLED
}
