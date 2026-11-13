package com.sza.streamsplayer.ui.exchange

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.sza.streamsplayer.R
import com.sza.streamsplayer.data.exchange.ExchangeConnectionString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.io.File
import java.io.FileOutputStream

/**
 * Tests covering hostile inputs for the exported components (S0019 Scope 5)
 * and verifying password saveEnabled=false (Scope 4).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExchangeImportActivityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `hostile input - oversized file greater than 4096 bytes is refused with one message and no connection`() {
        val oversizedFile = File(context.cacheDir, "oversized.fmsexch")
        val largeContent = "FMSEXCH1:x=10.0.0.1%3A8080&n=" + "A".repeat(5000)
        oversizedFile.writeText(largeContent, Charsets.UTF_8)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.fromFile(oversizedFile)
            type = "application/x-fmsexch"
        }

        val controller = Robolectric.buildActivity(ExchangeImportActivity::class.java, intent).setup()
        val activity = controller.get()

        assertEquals("Activity must return RESULT_CANCELED without result", Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
        assertTrue("Activity must finish immediately", activity.isFinishing)

        val toastText = ShadowToast.getTextOfLatestToast()
        assertNotNull("Must show toast with refusal message", toastText)
        assertEquals("This is not a valid connection string.", toastText)
    }

    @Test
    fun `hostile input - binary file is refused with one message and no connection`() {
        val binaryFile = File(context.cacheDir, "binary.fmsexch")
        val binaryBytes = byteArrayOf(0x00, 0x01, 0x02, 0xFF.toByte(), 0xFE.toByte(), 0x80.toByte())
        binaryFile.writeBytes(binaryBytes)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.fromFile(binaryFile)
            type = "application/x-fmsexch"
        }

        val controller = Robolectric.buildActivity(ExchangeImportActivity::class.java, intent).setup()
        val activity = controller.get()

        assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
        assertTrue(activity.isFinishing)

        val toastText = ShadowToast.getTextOfLatestToast()
        assertNotNull(toastText)
        assertEquals("This is not a valid connection string.", toastText)
    }

    @Test
    fun `hostile input - non-UTF-8 text is refused with one message and no connection`() {
        val nonUtf8File = File(context.cacheDir, "non_utf8.fmsexch")
        // Write invalid UTF-8 sequence (e.g. 0xC0, 0xC1, or standalone trail byte 0x80)
        FileOutputStream(nonUtf8File).use { it.write(byteArrayOf(0xC3.toByte(), 0x28)) }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.fromFile(nonUtf8File)
        }

        val controller = Robolectric.buildActivity(ExchangeImportActivity::class.java, intent).setup()
        val activity = controller.get()

        assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
        assertTrue(activity.isFinishing)

        val toastText = ShadowToast.getTextOfLatestToast()
        assertNotNull(toastText)
        assertEquals("This is not a valid connection string.", toastText)
    }

    @Test
    fun `hostile input - repeated intent processed independently without loops or connection`() {
        for (i in 1..3) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "FMSEXCH1:x=127.0.0.1%3A8080&u=tester$i")
            }

            val controller = Robolectric.buildActivity(ExchangeImportActivity::class.java, intent).setup()
            val activity = controller.get()

            assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
            assertTrue(activity.isFinishing)

            val nextStarted = shadowOf(activity).nextStartedActivity
            assertNotNull("Must launch ExchangeActivity with prefilled string", nextStarted)
            val connectionString = nextStarted.getStringExtra(ExchangeActivity.EXTRA_CONNECTION_STRING)
            assertNotNull(connectionString)
            assertTrue(connectionString!!.contains("tester$i"))
        }
    }

    @Test
    fun `password field has saveEnabled false and is not in saved state bundle`() {
        // Inflate the activity and check etSecret saveEnabled attribute
        val controller = Robolectric.buildActivity(ExchangeActivity::class.java).setup()
        val activity = controller.get()

        val etSecret = activity.findViewById<EditText>(R.id.etSecret)
        assertNotNull("etSecret must exist in layout", etSecret)
        assertFalse("Password field must have isSaveEnabled = false (Scope 4)", etSecret.isSaveEnabled)

        // Type a password and perform onSaveInstanceState
        etSecret.setText("SuperSecretPassword123")
        val savedState = Bundle()
        controller.saveInstanceState(savedState)

        // Verify the typed password is NOT present in the savedState bundle
        val bundleString = savedState.toString()
        assertFalse("Saved state bundle must not contain the secret", bundleString.contains("SuperSecretPassword123"))
    }
}
