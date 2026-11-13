package com.sza.streamsplayer.ui.scanner

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract

/**
 * S0023: ActivityResultContract for launching the QR scanner screen.
 *
 * Input: Unit.
 * Output: [ScanResult] ([ScanResult.Text], [ScanResult.Cancelled], [ScanResult.Denied], [ScanResult.NoCamera]).
 */
class ScanQrContract : ActivityResultContract<Unit, ScanResult>() {

    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(context, QrScannerActivity::class.java)

    override fun parseResult(resultCode: Int, intent: Intent?): ScanResult {
        return when {
            resultCode == Activity.RESULT_OK -> {
                val text = intent?.getStringExtra(QrScannerActivity.EXTRA_RESULT_TEXT)
                if (!text.isNullOrEmpty()) ScanResult.Text(text) else ScanResult.Cancelled
            }
            resultCode == QrScannerActivity.RESULT_DENIED ||
                intent?.getStringExtra(QrScannerActivity.EXTRA_RESULT_STATUS) == QrScannerActivity.STATUS_DENIED -> {
                ScanResult.Denied
            }
            resultCode == QrScannerActivity.RESULT_NO_CAMERA ||
                intent?.getStringExtra(QrScannerActivity.EXTRA_RESULT_STATUS) == QrScannerActivity.STATUS_NO_CAMERA -> {
                ScanResult.NoCamera
            }
            else -> ScanResult.Cancelled
        }
    }
}
