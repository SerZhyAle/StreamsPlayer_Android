package com.sza.streamsplayer.ui.scanner

import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import timber.log.Timber
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * S0023: Manages CameraX preview and image analysis lifecycle for QR scanning.
 */
open class QrScanSessionManager(
    private val lifecycleOwner: LifecycleOwner
) {
    private var provider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var analysis: ImageAnalysis? = null
    private var analysisExecutor: ExecutorService? = null
    private var generation = 0

    init {
        lifecycleOwner.lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) unbind()
        })
    }

    open fun bind(
        previewView: PreviewView,
        analyzer: QrCodeAnalyzer,
        onCameraUnavailable: () -> Unit
    ) {
        unbind()
        if (lifecycleOwner.lifecycle.currentState == Lifecycle.State.DESTROYED) return
        val request = generation
        val context = previewView.context.applicationContext
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            if (request != generation || lifecycleOwner.lifecycle.currentState == Lifecycle.State.DESTROYED) {
                return@addListener
            }
            try {
                val cameraProvider = providerFuture.get()
                if (!cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    Timber.d("S0023: no back camera on device")
                    onCameraUnavailable()
                    return@addListener
                }
                provider = cameraProvider
                val executor = Executors.newSingleThreadExecutor().also { analysisExecutor = it }
                val resolution = ResolutionSelector.Builder()
                    .setResolutionStrategy(ResolutionStrategy(
                        Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                    ))
                    .build()
                val rotation = previewView.display?.rotation ?: android.view.Surface.ROTATION_0
                val cameraPreview = Preview.Builder().setTargetRotation(rotation).build().also {
                    preview = it
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val cameraAnalysis = ImageAnalysis.Builder()
                    .setTargetRotation(rotation)
                    .setResolutionSelector(resolution)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build().also {
                        analysis = it
                        it.setAnalyzer(executor, analyzer)
                    }
                cameraProvider.bindToLifecycle(
                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, cameraPreview, cameraAnalysis
                )
                Timber.d("S0023: camera bound to lifecycle")
            } catch (e: Exception) {
                Timber.w(e, "S0023: camera initialization failed")
                unbind()
                onCameraUnavailable()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    open fun unbind() {
        generation++
        analysis?.clearAnalyzer()
        val ownedUseCases = listOfNotNull(preview, analysis).toTypedArray()
        if (ownedUseCases.isNotEmpty()) provider?.unbind(*ownedUseCases)
        preview = null
        analysis = null
        provider = null
        analysisExecutor?.shutdown()
        analysisExecutor = null
    }
}
