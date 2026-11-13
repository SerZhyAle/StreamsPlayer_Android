package com.sza.streamsplayer.ui.scanner

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.sza.streamsplayer.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QrScannerActivityRobolectricTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private class NoOpSessionManager(owner: LifecycleOwner) : QrScanSessionManager(owner) {
        var binds = 0
        var unbinds = 0
        override fun bind(previewView: PreviewView, analyzer: QrCodeAnalyzer, onCameraUnavailable: () -> Unit) {
            binds++
        }

        override fun unbind() { unbinds++ }
    }

    @Before
    fun setUp() {
        val shadowPm = shadowOf(context.packageManager)
        shadowPm.setSystemFeature(PackageManager.FEATURE_CAMERA_ANY, true)
        shadowPm.setSystemFeature(PackageManager.FEATURE_CAMERA, true)
    }

    @Test
    fun noCameraHardware_finishesWithNoCamera() {
        val shadowPm = shadowOf(context.packageManager)
        shadowPm.setSystemFeature(PackageManager.FEATURE_CAMERA_ANY, false)
        shadowPm.setSystemFeature(PackageManager.FEATURE_CAMERA, false)

        val controller = Robolectric.buildActivity(QrScannerActivity::class.java).create()
        val activity = controller.get()
        val shadowActivity = shadowOf(activity)

        assertTrue("Activity should finish when no camera", activity.isFinishing)
        assertEquals(QrScannerActivity.RESULT_NO_CAMERA, shadowActivity.resultCode)
        val status = shadowActivity.resultIntent?.getStringExtra(QrScannerActivity.EXTRA_RESULT_STATUS)
        assertEquals(QrScannerActivity.STATUS_NO_CAMERA, status)
    }

    @Test
    fun permissionNotGranted_showsRationaleDialog_cancelFinishesDenied() {
        val shadowApp = shadowOf(context as android.app.Application)
        shadowApp.denyPermissions(Manifest.permission.CAMERA)

        val controller = Robolectric.buildActivity(QrScannerActivity::class.java)
        val activity = controller.get()
        activity.sessionManager = NoOpSessionManager(activity)
        controller.create().start().resume()

        val dialog = ShadowDialog.getLatestDialog() as? AlertDialog
        assertNotNull("Rationale dialog should be shown", dialog)
        assertTrue("Dialog should be showing", dialog!!.isShowing)

        // Click Cancel (negative button)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue("Activity should finish on cancel", activity.isFinishing)

        val shadowActivity = shadowOf(activity)
        assertEquals(QrScannerActivity.RESULT_DENIED, shadowActivity.resultCode)
        val status = shadowActivity.resultIntent?.getStringExtra(QrScannerActivity.EXTRA_RESULT_STATUS)
        assertEquals(QrScannerActivity.STATUS_DENIED, status)
    }

    @Test
    fun grantingPermission_bindsCameraOnlyAfterGrant() {
        shadowOf(context as android.app.Application).denyPermissions(Manifest.permission.CAMERA)
        val controller = Robolectric.buildActivity(QrScannerActivity::class.java)
        val activity = controller.get()
        val session = NoOpSessionManager(activity)
        activity.sessionManager = session
        controller.setup()
        assertEquals(0, session.binds)
        val rationale = ShadowDialog.getLatestDialog() as AlertDialog
        rationale.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val request = shadowOf(activity).lastRequestedPermission
        activity.activityResultRegistry.dispatchResult(request.requestCode, true)
        assertEquals(1, session.binds)
        assertFalse(activity.isFinishing)
    }

    @Test
    fun deniedPermission_returnsDeniedAndPermanentDenialOffersSettings() {
        for (permanent in listOf(false, true)) {
            shadowOf(context as android.app.Application).denyPermissions(Manifest.permission.CAMERA)
            shadowOf(context.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.CAMERA, !permanent)
            val controller = Robolectric.buildActivity(QrScannerActivity::class.java)
            val activity = controller.get()
            val session = NoOpSessionManager(activity)
            activity.sessionManager = session
            controller.setup()
            (ShadowDialog.getLatestDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("android.content.pm.action.REQUEST_PERMISSIONS", shadowOf(activity).nextStartedActivity.action)
            activity.activityResultRegistry.dispatchResult(shadowOf(activity).lastRequestedPermission.requestCode, false)
            if (permanent) {
                val dialog = ShadowDialog.getLatestDialog() as AlertDialog
                assertEquals(activity.getString(R.string.qr_scanner_open_settings), dialog.getButton(AlertDialog.BUTTON_POSITIVE).text)
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                shadowOf(Looper.getMainLooper()).idle()
                val settingsIntent = shadowOf(activity).nextStartedActivity
                assertEquals(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, settingsIntent.action)
                assertEquals("package:${activity.packageName}", settingsIntent.data.toString())
            }
            assertEquals(QrScannerActivity.RESULT_DENIED, shadowOf(activity).resultCode)
            assertEquals(0, session.binds)
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun closeButton_finishesWithCancelled() {
        val shadowApp = shadowOf(context as android.app.Application)
        shadowApp.grantPermissions(Manifest.permission.CAMERA)

        val controller = Robolectric.buildActivity(QrScannerActivity::class.java)
        val activity = controller.get()
        activity.sessionManager = NoOpSessionManager(activity)
        controller.setup()

        val closeBtn = activity.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnQrClose)
        closeBtn.performClick()

        assertTrue("Activity should finish on close", activity.isFinishing)
        val shadowActivity = shadowOf(activity)
        assertEquals(Activity.RESULT_CANCELED, shadowActivity.resultCode)
    }

    @Test
    fun pasteButton_validClipboardText_finishesWithOkAndText() {
        val shadowApp = shadowOf(context as android.app.Application)
        shadowApp.grantPermissions(Manifest.permission.CAMERA)

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val testPayload = "  FMSEXCH1:v1:device99:token777\n  "
        clipboard.setPrimaryClip(ClipData.newPlainText("qr_test", testPayload))

        val controller = Robolectric.buildActivity(QrScannerActivity::class.java)
        val activity = controller.get()
        activity.sessionManager = NoOpSessionManager(activity)
        controller.setup()

        val pasteBtn = activity.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnQrPaste)
        pasteBtn.performClick()

        assertTrue("Activity should finish after paste", activity.isFinishing)
        val shadowActivity = shadowOf(activity)
        assertEquals(Activity.RESULT_OK, shadowActivity.resultCode)
        val returnedText = shadowActivity.resultIntent?.getStringExtra(QrScannerActivity.EXTRA_RESULT_TEXT)
        assertEquals(testPayload, returnedText)
    }

    @Test
    fun pasteButton_oversizedClipboardText_refusesPaste() {
        val shadowApp = shadowOf(context as android.app.Application)
        shadowApp.grantPermissions(Manifest.permission.CAMERA)

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val oversizedPayload = "a".repeat(4097)
        clipboard.setPrimaryClip(ClipData.newPlainText("oversized", oversizedPayload))

        val controller = Robolectric.buildActivity(QrScannerActivity::class.java)
        val activity = controller.get()
        activity.sessionManager = NoOpSessionManager(activity)
        controller.setup()

        val pasteBtn = activity.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnQrPaste)
        pasteBtn.performClick()

        assertFalse("Activity should not finish on oversized paste", activity.isFinishing)
    }

    @Test
    fun decode_finishesWithText() {
        val shadowApp = shadowOf(context as android.app.Application)
        shadowApp.grantPermissions(Manifest.permission.CAMERA)

        val controller = Robolectric.buildActivity(QrScannerActivity::class.java)
        val activity = controller.get()
        activity.sessionManager = NoOpSessionManager(activity)
        controller.setup()

        // Simulate successful decode callback
        activity.finishWithText("scanned_via_camera")

        assertTrue("Activity should finish after decode", activity.isFinishing)
        val shadowActivity = shadowOf(activity)
        assertEquals(Activity.RESULT_OK, shadowActivity.resultCode)
        val returnedText = shadowActivity.resultIntent?.getStringExtra(QrScannerActivity.EXTRA_RESULT_TEXT)
        assertEquals("scanned_via_camera", returnedText)
    }

    @Test
    fun backCancelsAndLateDecodeCannotOverwriteResult() {
        shadowOf(context as android.app.Application).grantPermissions(Manifest.permission.CAMERA)
        val controller = Robolectric.buildActivity(QrScannerActivity::class.java)
        val activity = controller.get()
        val session = NoOpSessionManager(activity)
        activity.sessionManager = session
        controller.setup()
        activity.onBackPressedDispatcher.onBackPressed()
        activity.finishWithText("late result")
        assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
        assertTrue(session.unbinds > 0)
    }

    @Test
    fun accessibility_attributesConfigured() {
        val shadowApp = shadowOf(context as android.app.Application)
        shadowApp.grantPermissions(Manifest.permission.CAMERA)

        val controller = Robolectric.buildActivity(QrScannerActivity::class.java)
        val activity = controller.get()
        activity.sessionManager = NoOpSessionManager(activity)
        controller.setup()

        val preview = activity.findViewById<androidx.camera.view.PreviewView>(R.id.qrPreview)
        assertEquals(
            activity.getString(R.string.qr_scanner_viewfinder_cd),
            preview.contentDescription
        )
        val closeBtn = activity.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnQrClose)
        assertTrue("Close button should be focusable for D-pad", closeBtn.isFocusable)
        val pasteBtn = activity.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnQrPaste)
        assertTrue("Paste button should be focusable for D-pad", pasteBtn.isFocusable)
    }
}
