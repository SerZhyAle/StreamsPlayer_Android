package com.sza.streamsplayer.ui.scanner

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sza.streamsplayer.ui.companionimport.qr.QrCodeEncoder
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Host loads each exported PNG into the emulator's virtual-scene wall before running its scan. */
@RunWith(AndroidJUnit4::class)
class QrScannerVirtualSceneTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test
    fun exportFixtures() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("s0023VirtualScene") == "prepare")
        for ((name, payload) in fixtures) {
            val bitmap = QrCodeEncoder.encode(payload, 1024)
            File(context.cacheDir, "s0023-$name.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            bitmap.recycle()
        }
    }

    @Test
    fun scanConnectionString() = scan("connection")

    @Test
    fun scanDescriptor() = scan("descriptor")

    private fun scan(name: String) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("s0023VirtualScene") == name)
        ActivityScenario.launchActivityForResult<QrScannerActivity>(
            Intent(context, QrScannerActivity::class.java)
        ).use { scenario ->
            val result = scenario.result
            assertEquals(Activity.RESULT_OK, result.resultCode)
            assertEquals(ScanResult.Text(fixtures.getValue(name)), ScanQrContract().parseResult(result.resultCode, result.resultData))
        }
    }

    companion object {
        private val fixtures = mapOf(
            "connection" to "FMSEXCH1:x=192.168.1.50%3A8080&u=scanner-test",
            "descriptor" to """{"schemaVersion":1,"url":"http://192.168.1.50:8080/audio.mp3","title":"Scanner test","mode":"AUDIO_ONLY"}"""
        )
    }
}
