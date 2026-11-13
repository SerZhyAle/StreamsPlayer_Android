package com.sza.streamsplayer.ui.scanner

import android.content.Context
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import com.google.common.util.concurrent.ListenableFuture
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QrScanSessionManagerTest {
    private class Owner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry(this).apply { currentState = Lifecycle.State.CREATED }
    }

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun pendingProvider_isIgnoredAfterUnbindOrDestroy() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val future = mockk<ListenableFuture<ProcessCameraProvider>>()
        val pending = mutableListOf<Runnable>()
        every { future.addListener(any(), any<Executor>()) } answers { pending.add(firstArg()); Unit }
        mockkObject(ProcessCameraProvider.Companion)
        every { ProcessCameraProvider.getInstance(any()) } returns future

        for (destroy in listOf(false, true)) {
            val owner = Owner()
            val session = QrScanSessionManager(owner)
            var unavailable = false
            session.bind(PreviewView(context), QrCodeAnalyzer {}) { unavailable = true }
            if (destroy) owner.lifecycle.currentState = Lifecycle.State.DESTROYED else session.unbind()
            pending.removeAt(0).run()
            assertFalse(unavailable)
        }
        verify(exactly = 0) { future.get() }
    }
}
