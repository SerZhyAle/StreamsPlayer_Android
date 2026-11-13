package com.sza.streamsplayer.ui.scanner

/**
 * S0023: Results returned by the QR scanner Activity contract.
 *
 * Requirements:
 * - [Text]: successfully scanned or pasted text payload.
 * - [Cancelled]: user cancelled the scan (via back button or close button).
 * - [Denied]: camera permission was denied by the user.
 * - [NoCamera]: device has no camera hardware or camera is unavailable.
 */
sealed interface ScanResult {
    data class Text(val text: String) : ScanResult
    data object Cancelled : ScanResult
    data object Denied : ScanResult
    data object NoCamera : ScanResult
}
