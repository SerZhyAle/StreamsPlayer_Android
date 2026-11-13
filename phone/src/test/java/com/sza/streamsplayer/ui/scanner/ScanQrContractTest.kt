package com.sza.streamsplayer.ui.scanner

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ScanQrContractTest {

    private val contract = ScanQrContract()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun createIntent_targetsQrScannerActivity() {
        val intent = contract.createIntent(context, Unit)
        assertEquals(QrScannerActivity::class.java.name, intent.component?.className)
    }

    @Test
    fun parseResult_okWithText_returnsTextResult() {
        val intent = Intent().putExtra(QrScannerActivity.EXTRA_RESULT_TEXT, "scanned_text_data")
        val result = contract.parseResult(Activity.RESULT_OK, intent)
        assertTrue(result is ScanResult.Text)
        assertEquals("scanned_text_data", (result as ScanResult.Text).text)
    }

    @Test
    fun parseResult_okWithEmptyOrNullText_returnsCancelled() {
        val emptyIntent = Intent().putExtra(QrScannerActivity.EXTRA_RESULT_TEXT, "")
        assertEquals(ScanResult.Cancelled, contract.parseResult(Activity.RESULT_OK, emptyIntent))

        val nullIntent = Intent()
        assertEquals(ScanResult.Cancelled, contract.parseResult(Activity.RESULT_OK, nullIntent))

        assertEquals(ScanResult.Cancelled, contract.parseResult(Activity.RESULT_OK, null))
    }

    @Test
    fun parseResult_canceled_returnsCancelled() {
        assertEquals(ScanResult.Cancelled, contract.parseResult(Activity.RESULT_CANCELED, null))
        assertEquals(ScanResult.Cancelled, contract.parseResult(Activity.RESULT_CANCELED, Intent()))
    }

    @Test
    fun parseResult_deniedByResultCode_returnsDenied() {
        val result = contract.parseResult(QrScannerActivity.RESULT_DENIED, null)
        assertEquals(ScanResult.Denied, result)
    }

    @Test
    fun parseResult_deniedByExtraStatus_returnsDenied() {
        val intent = Intent().putExtra(QrScannerActivity.EXTRA_RESULT_STATUS, QrScannerActivity.STATUS_DENIED)
        val result = contract.parseResult(Activity.RESULT_CANCELED, intent)
        assertEquals(ScanResult.Denied, result)
    }

    @Test
    fun parseResult_noCameraByResultCode_returnsNoCamera() {
        val result = contract.parseResult(QrScannerActivity.RESULT_NO_CAMERA, null)
        assertEquals(ScanResult.NoCamera, result)
    }

    @Test
    fun parseResult_noCameraByExtraStatus_returnsNoCamera() {
        val intent = Intent().putExtra(QrScannerActivity.EXTRA_RESULT_STATUS, QrScannerActivity.STATUS_NO_CAMERA)
        val result = contract.parseResult(Activity.RESULT_CANCELED, intent)
        assertEquals(ScanResult.NoCamera, result)
    }
}
