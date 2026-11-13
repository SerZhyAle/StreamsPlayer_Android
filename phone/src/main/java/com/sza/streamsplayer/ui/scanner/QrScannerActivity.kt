package com.sza.streamsplayer.ui.scanner

import android.Manifest
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.addCallback
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sza.streamsplayer.R
import com.sza.streamsplayer.ui.common.help.HelpKeys
import com.sza.streamsplayer.ui.common.help.HelpOpenManager
import com.sza.streamsplayer.ui.common.help.HelpSurfaceIds
import com.sza.streamsplayer.ui.common.help.HelpTooltipManager
import com.sza.streamsplayer.core.util.LocaleHelper
import com.sza.streamsplayer.databinding.ActivityQrScannerBinding
import timber.log.Timber

/**
 * S0023: CameraX QR scanner screen.
 *
 * Requirements:
 * - Contract: [ScanQrContract] returning Text, Cancelled, Denied, NoCamera.
 * - Camera: CameraX preview and ImageAnalysis, back lens, one frame in flight, closed with screen.
 * - Decode: Pure-JVM ZXing MultiFormatReader restricted to QR_CODE, luminance plane, rotation applied,
 *   double-read guard, text > 4096 refused.
 * - Nothing connects, nothing opens: text returned directly.
 * - Permission: CAMERA requested at scan moment; one-sentence justification shown first; refusal leaves
 *   paste/file message; permanently denied offers system settings.
 * - Accessibility: TalkBack description of viewfinder, announced result, "Paste instead" action,
 *   D-pad reachable close button, Back cancels.
 * - Probes: Timber.d("S0023: .."); scanned text is never logged, only length and outcome.
 */
class QrScannerActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    private lateinit var binding: ActivityQrScannerBinding

    @VisibleForTesting
    internal var sessionManager: QrScanSessionManager? = null

    private fun getSessionManager(): QrScanSessionManager =
        sessionManager ?: QrScanSessionManager(this).also { sessionManager = it }

    @VisibleForTesting
    internal var analyzer: QrCodeAnalyzer? = null

    private fun getAnalyzer(): QrCodeAnalyzer =
        analyzer ?: QrCodeAnalyzer(onPayloadTooLong = {
            runOnUiThread {
                if (!hasCompleted && !isFinishing && !isDestroyed) {
                    Toast.makeText(this, R.string.qr_scanner_payload_too_long, Toast.LENGTH_SHORT).show()
                }
            }
        }) { payload ->
            runOnUiThread {
                finishWithText(payload)
            }
        }.also { analyzer = it }

    private var hasCompleted = false

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Timber.d("S0023: camera permission granted")
            startScan()
        } else {
            handlePermissionDenied()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("S0023: scanner started")
        binding = ActivityQrScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.qrPreview.contentDescription = getString(R.string.qr_scanner_viewfinder_cd)

        binding.btnQrClose.setOnClickListener {
            cancelAndFinish()
        }

        binding.btnQrPaste.setOnClickListener {
            handlePaste()
        }

        binding.btnQrHelp.setOnClickListener { HelpOpenManager.open(this, HelpSurfaceIds.QR_SCANNER) }
        androidx.appcompat.widget.TooltipCompat.setTooltipText(binding.btnQrHelp, HelpTooltipManager.tooltipText(this))

        onBackPressedDispatcher.addCallback(this) { cancelAndFinish() }

        if (!hasCameraHardware()) {
            finishWithNoCamera()
            return
        }

        checkPermissionAndStart()
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (HelpKeys.handle(this, HelpSurfaceIds.QR_SCANNER, event)) return true
        return super.dispatchKeyEvent(event)
    }

    private fun hasCameraHardware(): Boolean {
        return packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }

    private fun checkPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startScan()
        } else {
            showCameraRationaleDialog()
        }
    }

    private fun showCameraRationaleDialog() {
        Timber.d("S0023: showing camera rationale dialog")
        AlertDialog.Builder(this)
            .setTitle(R.string.qr_scanner_title)
            .setMessage(R.string.qr_scanner_camera_rationale_message)
            .setPositiveButton(R.string.qr_scanner_continue) { _, _ ->
                Timber.d("S0023: requesting camera permission")
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                denyAndFinish()
            }
            .setOnCancelListener {
                denyAndFinish()
            }
            .show()
    }

    private fun handlePermissionDenied() {
        Timber.d("S0023: camera permission denied")
        if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            Timber.d("S0023: camera permission permanently denied")
            AlertDialog.Builder(this)
                .setTitle(R.string.qr_scanner_title)
                .setMessage(R.string.qr_scanner_permission_permanent_denied_message)
                .setPositiveButton(R.string.qr_scanner_open_settings) { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", packageName, null)
                    )
                    startActivity(intent)
                    denyAndFinish()
                }
                .setNegativeButton(R.string.cancel) { _, _ ->
                    denyAndFinish()
                }
                .setOnCancelListener {
                    denyAndFinish()
                }
                .show()
        } else {
            denyAndFinish()
        }
    }

    private fun startScan() {
        if (hasCompleted || isFinishing || isDestroyed) return
        Timber.d("S0023: starting camera scan session")
        getSessionManager().bind(
            previewView = binding.qrPreview,
            analyzer = getAnalyzer(),
            onCameraUnavailable = {
                runOnUiThread { finishWithNoCamera() }
            }
        )
    }

    private fun handlePaste() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = clipboard?.primaryClip
        val clipText = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).text?.toString() else null
        if (!clipText.isNullOrEmpty()) {
            if (clipText.length > QrCodeAnalyzer.MAX_PAYLOAD_LENGTH) {
                Timber.d("S0023: pasted text too long (${clipText.length} > ${QrCodeAnalyzer.MAX_PAYLOAD_LENGTH})")
                Toast.makeText(this, R.string.qr_scanner_payload_too_long, Toast.LENGTH_SHORT).show()
            } else {
                Timber.d("S0023: paste accepted, length=${clipText.length}")
                finishWithText(clipText)
            }
        } else {
            Toast.makeText(this, R.string.qr_scanner_clipboard_empty, Toast.LENGTH_SHORT).show()
        }
    }

    fun finishWithText(text: String) {
        if (hasCompleted || isFinishing || isDestroyed) return
        hasCompleted = true
        Timber.d("S0023: finish with Text, length=${text.length}")
        binding.root.announceForAccessibility(getString(R.string.qr_scanner_code_scanned))
        val intent = Intent().putExtra(EXTRA_RESULT_TEXT, text)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    private fun cancelAndFinish() {
        if (hasCompleted) return
        hasCompleted = true
        Timber.d("S0023: finish with Cancelled")
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    private fun denyAndFinish() {
        if (hasCompleted) return
        hasCompleted = true
        Toast.makeText(this, R.string.qr_scanner_permission_denied_message, Toast.LENGTH_LONG).show()
        Timber.d("S0023: finish with Denied")
        val intent = Intent().putExtra(EXTRA_RESULT_STATUS, STATUS_DENIED)
        setResult(RESULT_DENIED, intent)
        finish()
    }

    private fun finishWithNoCamera() {
        if (hasCompleted) return
        hasCompleted = true
        Timber.d("S0023: finish with NoCamera")
        val intent = Intent().putExtra(EXTRA_RESULT_STATUS, STATUS_NO_CAMERA)
        setResult(RESULT_NO_CAMERA, intent)
        finish()
    }

    override fun finish() {
        hasCompleted = true
        sessionManager?.unbind()
        super.finish()
    }

    override fun onDestroy() {
        sessionManager?.unbind()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_RESULT_TEXT = "extra_result_text"
        const val EXTRA_RESULT_STATUS = "extra_result_status"
        const val STATUS_DENIED = "status_denied"
        const val STATUS_NO_CAMERA = "status_no_camera"
        const val RESULT_DENIED = Activity.RESULT_FIRST_USER + 1
        const val RESULT_NO_CAMERA = Activity.RESULT_FIRST_USER + 2
    }
}
