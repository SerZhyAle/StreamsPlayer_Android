package com.sza.streamsplayer.ui.broadcast.helpers

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.zxing.WriterException
import com.sza.streamsplayer.R
import com.sza.streamsplayer.broadcast.BroadcastLensChoice
import com.sza.streamsplayer.broadcast.BroadcastMode
import com.sza.streamsplayer.broadcast.BroadcastPreviewBinder
import com.sza.streamsplayer.broadcast.BroadcastSourceController
import com.sza.streamsplayer.broadcast.BroadcastState
import com.sza.streamsplayer.broadcast.ListBroadcastCameraLensesUseCase
import com.sza.streamsplayer.data.exchange.OutgoingExchangeEntry
import com.sza.streamsplayer.data.exchange.drive.DriveBroadcastAnnouncer
import com.sza.streamsplayer.data.exchange.publish.BroadcastExchangePublisher
import com.sza.streamsplayer.domain.model.ExchangeMessage
import com.sza.streamsplayer.ui.exchange.ExchangeActivity
import com.sza.streamsplayer.ui.exchange.ExchangeDiagnosisDialog
import com.sza.streamsplayer.core.capability.MediaCapabilities
import com.sza.streamsplayer.core.share.SharePayload
import com.sza.streamsplayer.core.share.SystemShareInvoker
import com.sza.streamsplayer.databinding.ActivityBroadcastControlBinding
import com.sza.streamsplayer.domain.repository.SettingsRepository
import com.sza.streamsplayer.domain.usecase.SendStreamToWatchUseCase
import com.sza.streamsplayer.ui.common.support.SupportIntentFactory
import com.sza.streamsplayer.ui.common.support.exposeAsLink
import com.sza.streamsplayer.ui.companionimport.qr.QrCodeEncoder
import com.sza.streamsplayer.ui.settings.SettingsActivity
import com.sza.streamsplayer.ui.settings.SettingsDestinations
import com.sza.streamsplayer.util.CaptureFileNamer
import com.sza.streamsplayer.util.showBoundTo
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class BroadcastControlManager @Inject constructor(
    private val controller: BroadcastSourceController,
    private val previewBinder: BroadcastPreviewBinder,
    private val listLenses: ListBroadcastCameraLensesUseCase,
    private val shareManager: BroadcastShareManager,
    private val blankScreenManager: BroadcastBlankScreenManager,
    private val settingsRepository: SettingsRepository,
    private val mediaCapabilities: MediaCapabilities,
    private val sendStreamToWatch: Lazy<SendStreamToWatchUseCase>,
    private val settingsPanelManager: BroadcastSettingsPanelManager,
    private val exchangePublisher: BroadcastExchangePublisher,
    private val driveAnnouncer: DriveBroadcastAnnouncer,
    private val outgoingEntry: OutgoingExchangeEntry,
) {
    /** Takes no dependencies of its own, so it is constructed here instead of going through Hilt. */
    private val preStreamPreview = BroadcastPreStreamPreviewManager()
    private val qrFullscreen = BroadcastQrFullscreenManager()

    /**
     * The mode a live switch is re-opening the session in, null while nothing switches. Until that mode
     * is on air the screen keeps its live layout: an audio-video switch passes through `Idle`, and the
     * idle layout would start the pre-start preview on the camera the new session is about to open.
     */
    private var switchTargetMode: BroadcastMode? = null

    private var selectedMode: BroadcastMode = BroadcastMode.AUDIO_ONLY
    private var selectedLensId: String? = null
    private var wearSendAvailable = false
    private var wearSendInProgress = false

    // Nullable and cleared in onDetach(): each launcher is registered on the activity's own
    // ActivityResultRegistry and its callback captures that activity, so a @Singleton holding
    // them would retain the last destroyed BroadcastControlActivity for the rest of the process.
    private var exportFileLauncher: ActivityResultLauncher<String>? = null
    private var cameraPermissionLauncher: ActivityResultLauncher<String>? = null
    private var startPermissionLauncher: ActivityResultLauncher<String>? = null

    /** The permission the pending start is waiting for, so its denial can name the feature it blocked. */
    private var pendingStartPermission: String? = null

    /** One prompt per screen: a refused camera leaves the preview area empty instead of asking again. */
    private var cameraPermissionAsked = false

    fun setup(
        activity: AppCompatActivity,
        binding: ActivityBroadcastControlBinding
    ) {
        blankScreenManager.attach(activity, binding.root)
        cameraPermissionAsked = false
        keepPendingStartPermission(activity)
        cameraPermissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) refreshPreStreamPreview(activity, binding)
        }
        startPermissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            val permission = pendingStartPermission
            pendingStartPermission = null
            if (granted) {
                startSession(activity, binding)
            } else if (permission != null) {
                (controller.state.value as? BroadcastState.Live)?.let { live ->
                    checkModeChip(binding, live.descriptor.mode)
                }
                showStartPermissionDenied(binding, permission)
            }
        }
        setupPreStreamControls(activity, binding)
        setupLiveControls(activity, binding)
        setupSharePanel(activity, binding)

        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    controller.state.collect { state ->
                        renderState(activity, binding, state)
                    }
                }
                launch {
                    controller.listenerCount.collect { count ->
                        renderListenerCount(activity, binding, count)
                    }
                }
                launch {
                    controller.feedbackSuppressed.collect { suppressed ->
                        renderFeedbackWarning(activity, binding, suppressed)
                    }
                }
                launch {
                    combine(
                        exchangePublisher.state,
                        exchangePublisher.published,
                        outgoingEntry.server,
                        controller.state,
                    ) { publishState, published, server, broadcast ->
                        ServerLine(
                            live = broadcast is BroadcastState.Live,
                            serverName = server?.name,
                            publishState = publishState,
                            relayRunning = published?.endpoints.orEmpty().any {
                                it.transport.equals("RELAY", ignoreCase = true)
                            },
                        )
                    }.distinctUntilChanged().collect { line ->
                        renderPublishState(activity, binding, line)
                    }
                }
                launch {
                    driveAnnouncer.state.collect { driveState ->
                        renderDriveState(binding, driveState)
                    }
                }
                launch {
                    settingsRepository.getSettings().collect { settings ->
                        wearSendAvailable = settings.enableWearCompanion &&
                            mediaCapabilities.supportsWearCompanion
                        renderSendToWatch(binding, controller.state.value)
                    }
                }
            }
        }
    }

    private fun setupPreStreamControls(
        activity: AppCompatActivity,
        binding: ActivityBroadcastControlBinding
    ) {
        binding.cgBroadcastMode.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: R.id.chipModeAudioOnly
            selectedMode = when (checkedId) {
                R.id.chipModeVideoAudio -> BroadcastMode.VIDEO_AUDIO
                R.id.chipModeVideoOnly -> BroadcastMode.VIDEO_ONLY
                else -> BroadcastMode.AUDIO_ONLY
            }
            updateLensSelectionVisibility(activity, binding)
            requestLiveModeSwitch(activity, binding)
        }

        // The pre-start preview owns the camera the broadcast is about to open, so the service starts from
        // the close callback rather than from the tap (S3174).
        binding.btnStartBroadcast.setOnClickListener {
            binding.btnStartBroadcast.isEnabled = false
            preStreamPreview.stop {
                // The settings page commits its text rows when it is left, but the commit only starts the write;
                // the stream must read the port the user typed, so the start waits for it to land (S3234).
                activity.lifecycleScope.launch {
                    settingsPanelManager.awaitPendingWrites()
                    binding.btnStartBroadcast.isEnabled = true
                    startSession(activity, binding)
                }
            }
        }

        // S0049: the broadcast settings have one editor, the Broadcast page of the settings screen.
        binding.btnBroadcastSettings.setOnClickListener {
            SettingsActivity.launch(activity, SettingsDestinations.BROADCAST)
        }
        updateLensSelectionVisibility(activity, binding)
    }

    /**
     * S3267: the screen used to start the service with no permission pre-flight at all, and a session
     * the service cannot honour is killed by the platform together with the process - so the grants the
     * chosen mode needs are collected here first, exactly as the main screen collects them. A live mode
     * switch goes through the same pre-flight: the new mode may need a grant the old one did not.
     */
    private fun startSession(
        activity: AppCompatActivity,
        binding: ActivityBroadcastControlBinding
    ) {
        val missing = missingStartPermission(activity)
        if (missing != null) {
            pendingStartPermission = missing
            startPermissionLauncher?.launch(missing)
        } else if (controller.state.value is BroadcastState.Live) {
            switchTargetMode = selectedMode
            setModeControlsEnabled(binding, false)
            controller.switchMode(selectedMode, selectedLensId)
            val message = binding.root.context.getString(R.string.broadcast_control_mode_switching)
            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
        } else {
            controller.start(selectedMode, selectedLensId)
        }
    }

    /**
     * A mode chip picked while live re-opens the session in that mode. The lens is resolved first, so
     * an audio session turning into video opens the lens the idle chips would have shown.
     */
    private fun requestLiveModeSwitch(
        activity: AppCompatActivity,
        binding: ActivityBroadcastControlBinding
    ) {
        val live = controller.state.value as? BroadcastState.Live ?: return
        if (switchTargetMode != null || live.descriptor.mode == selectedMode.name) return
        activity.lifecycleScope.launch {
            if (selectedMode != BroadcastMode.AUDIO_ONLY) {
                selectedLensId = live.activeLensId ?: resolveLensId(listLenses.listOptions())
            }
            startSession(activity, binding)
        }
    }

    @Suppress("ReturnCount")
    private fun missingStartPermission(activity: AppCompatActivity): String? {
        if (needsMicrophone() && !isGranted(activity, Manifest.permission.RECORD_AUDIO)) {
            return Manifest.permission.RECORD_AUDIO
        }
        if (selectedMode != BroadcastMode.AUDIO_ONLY && !isGranted(activity, Manifest.permission.CAMERA)) {
            return Manifest.permission.CAMERA
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !isGranted(activity, Manifest.permission.POST_NOTIFICATIONS)
        ) {
            return Manifest.permission.POST_NOTIFICATIONS
        }
        return null
    }

    // A build without the camera foreground-service type keeps a video-only session foreground through
    // the microphone type, which Android 14 refuses until the permission is granted (S3154).
    private fun needsMicrophone(): Boolean = selectedMode != BroadcastMode.VIDEO_ONLY ||
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !controller.cameraSurvivesBackground)

    private fun isGranted(activity: AppCompatActivity, permission: String): Boolean =
        ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED

    private fun showStartPermissionDenied(
        binding: ActivityBroadcastControlBinding,
        permission: String
    ) {
        Timber.w("Broadcast start permission denied: %s", permission)
        val messageRes = when (permission) {
            Manifest.permission.RECORD_AUDIO -> R.string.broadcast_permission_microphone_required
            Manifest.permission.CAMERA -> R.string.broadcast_permission_camera_required
            else -> R.string.broadcast_permission_notifications_required
        }
        Snackbar.make(binding.root, binding.root.context.getString(messageRes), Snackbar.LENGTH_LONG).show()
    }

    private fun updateLensSelectionVisibility(
        activity: AppCompatActivity,
        binding: ActivityBroadcastControlBinding
    ) {
        val hasCamera = selectedMode != BroadcastMode.AUDIO_ONLY
        if (hasCamera) {
            activity.lifecycleScope.launch {
                val choice = listLenses.listOptions()
                selectedLensId = (controller.state.value as? BroadcastState.Live)?.activeLensId
                    ?: resolveLensId(choice)
                val lensChips = BroadcastLensChipsRenderer(
                    label = binding.tvLensHeader,
                    group = binding.cgLensSelection,
                    onLensSelected = { lensId ->
                        selectedLensId = lensId
                        persistLensId(activity, lensId)
                        if (controller.state.value is BroadcastState.Live) {
                            controller.selectLens(lensId)
                        } else {
                            refreshPreStreamPreview(activity, binding)
                        }
                    }
                )
                lensChips.render(
                    BroadcastEntryUi.LensUi(
                        options = choice.options,
                        selectedLensId = selectedLensId,
                        visible = choice.options.isNotEmpty()
                    )
                )
                refreshPreStreamPreview(activity, binding)
            }
        } else {
            binding.tvLensHeader.visibility = View.GONE
            binding.cgLensSelection.visibility = View.GONE
            selectedLensId = null
            refreshPreStreamPreview(activity, binding)
        }
    }

    /**
     * S3237: the lens choice outlives the screen. The in-memory pick wins while the screen is alive,
     * the persisted one restores it after a recreation or a relaunch, and either is kept only while the
     * phone still enumerates that lens - a lens that no longer exists would open nothing.
     */
    private suspend fun resolveLensId(choice: BroadcastLensChoice): String? {
        val stored = settingsRepository.getSettings().first().broadcast.cameraLensId
        return listOfNotNull(selectedLensId, stored)
            .firstOrNull { candidate -> choice.options.any { it.id == candidate } }
            ?: choice.initialLensId
    }

    private fun persistLensId(activity: AppCompatActivity, lensId: String) {
        activity.lifecycleScope.launch {
            settingsRepository.updateSettings { settings ->
                settings.copy(broadcast = settings.broadcast.copy(cameraLensId = lensId))
            }
        }
    }

    /**
     * S3174: the picture the user picks a lens by. It exists only while the screen is idle - a live
     * session's own camera belongs to the service, and its preview arrives through [previewBinder].
     */
    private fun refreshPreStreamPreview(
        activity: AppCompatActivity,
        binding: ActivityBroadcastControlBinding
    ) {
        val wantsCamera = selectedMode != BroadcastMode.AUDIO_ONLY
        val granted = ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        when {
            controller.state.value is BroadcastState.Live || switchTargetMode != null -> Unit
            !wantsCamera -> {
                preStreamPreview.stop()
                binding.previewContainer.visibility = View.GONE
            }
            !granted -> {
                binding.previewContainer.visibility = View.GONE
                if (!cameraPermissionAsked) {
                    cameraPermissionAsked = true
                    cameraPermissionLauncher?.launch(Manifest.permission.CAMERA)
                }
            }
            else -> {
                binding.previewContainer.visibility = View.VISIBLE
                preStreamPreview.start(activity, binding.previewContainer, selectedLensId)
            }
        }
    }

    private fun setupLiveControls(
        activity: AppCompatActivity,
        binding: ActivityBroadcastControlBinding
    ) {
        binding.btnToggleCamera.setOnClickListener {
            controller.toggleCamera()
        }

        binding.btnToggleMic.setOnClickListener {
            controller.toggleMicrophone()
        }

        binding.btnSwitchCamera.setOnClickListener {
            showLensPicker(activity)
        }

        binding.btnScreenOff.setOnClickListener {
            blankScreenManager.onScreenOffRequested(activity)
        }

        binding.btnSendToWatch.setOnClickListener {
            sendBroadcastToWatch(activity, binding)
        }

        binding.btnStopBroadcast.setOnClickListener {
            switchTargetMode = null
            controller.stop()
        }
    }

    private fun sendBroadcastToWatch(
        activity: AppCompatActivity,
        binding: ActivityBroadcastControlBinding,
    ) {
        val liveState = controller.state.value as? BroadcastState.Live ?: return
        if (!wearSendAvailable || wearSendInProgress) return

        wearSendInProgress = true
        renderSendToWatch(binding, liveState)
        activity.lifecycleScope.launch {
            try {
                val descriptor = liveState.descriptor
                val outcome = sendStreamToWatch.get()(
                    title = descriptor.title ?: descriptor.url,
                    url = descriptor.url,
                    mediaKind = descriptor.mode,
                    openNow = true,
                )
                Toast.makeText(activity, outcome.toMessageRes(), Toast.LENGTH_SHORT).show()
            } finally {
                wearSendInProgress = false
                renderSendToWatch(binding, controller.state.value)
            }
        }
    }

    @StringRes
    private fun SendStreamToWatchUseCase.Outcome.toMessageRes(): Int = when (this) {
        is SendStreamToWatchUseCase.Outcome.Delivered ->
            if (updated) R.string.stream_send_to_watch_updated else R.string.stream_send_to_watch_done

        SendStreamToWatchUseCase.Outcome.WatchUnavailable ->
            R.string.stream_send_to_watch_watch_unavailable

        SendStreamToWatchUseCase.Outcome.NoReply -> R.string.stream_send_to_watch_no_reply
        is SendStreamToWatchUseCase.Outcome.Error -> R.string.stream_send_to_watch_failed
        SendStreamToWatchUseCase.Outcome.Opened -> R.string.stream_open_on_watch_opened
        SendStreamToWatchUseCase.Outcome.WatchAppNotOpen -> R.string.stream_open_on_watch_app_closed
    }

    private fun setupSharePanel(
        activity: AppCompatActivity,
        binding: ActivityBroadcastControlBinding
    ) {
        exportFileLauncher = activity.registerForActivityResult(
            ActivityResultContracts.CreateDocument(BROADCAST_DESCRIPTOR_MIME_TYPE)
        ) { uri ->
            if (uri != null) writeBroadcastDescriptor(activity, uri)
        }

        binding.btnBroadcastHelp.setOnClickListener { openViewerGuide(activity) }
        binding.btnBroadcastHelp.exposeAsLink()
    }

    /** S3175: the guide the broadcaster forwards to whoever receives the link, QR or file. */
    private fun openViewerGuide(activity: AppCompatActivity) {
        val url = SupportIntentFactory.broadcastGuideUrl(activity)
        try {
            activity.startActivity(SupportIntentFactory.openUrl(url))
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "No browser to open the broadcast viewer guide")
            Toast.makeText(activity, R.string.settings_no_browser_for_docs, Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun renderShareData(
        activity: AppCompatActivity,
        binding: ActivityBroadcastControlBinding
    ) {
        val liveState = controller.state.value as? BroadcastState.Live ?: return
        val descriptor = liveState.descriptor
        val url = descriptor.url

        binding.tvShareUrl.text = url

        binding.btnCopyUrl.setOnClickListener {
            val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Broadcast URL", url)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(activity, R.string.broadcast_share_saved, Toast.LENGTH_SHORT).show()
        }

        binding.btnShareSend.setOnClickListener {
            val link = shareManager.generateShareLink(liveState)
            SystemShareInvoker.invoke(
                activity,
                SharePayload.Text(link),
                chooserTitle = activity.getString(R.string.broadcast_share_send_chooser_title)
            )
        }

        binding.btnExportFile.setOnClickListener {
            // The stream URL is stable across exports, so a URL-derived name made every save collide
            // with the previous file and the system silently appended " (1)" (S3240).
            val descriptorName = CaptureFileNamer.shared.allocate(
                CaptureFileNamer.CaptureKind.BROADCAST,
                BROADCAST_DESCRIPTOR_EXTENSION
            )
            exportFileLauncher?.launch(descriptorName)
        }

        val payload = shareManager.generateQrPayload(liveState)
        binding.ivShareQr.setOnClickListener { qrFullscreen.show(activity, payload) }
        qrFullscreen.update(activity, payload)
        val metrics = activity.resources.displayMetrics
        val size = (min(metrics.widthPixels, metrics.heightPixels) * QR_SIZE_FRACTION)
            .toInt()
            .coerceIn(QR_SIZE_MIN_PX, QR_SIZE_MAX_PX)
        try {
            binding.ivShareQr.setImageBitmap(QrCodeEncoder.encode(payload, size))
        } catch (e: WriterException) {
            Timber.w(e, "Failed to encode QR in BroadcastControlManager")
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Illegal argument when encoding QR in BroadcastControlManager")
        }
    }

    private fun writeBroadcastDescriptor(activity: AppCompatActivity, uri: Uri) {
        val liveState = controller.state.value as? BroadcastState.Live ?: return
        val descriptor = liveState.descriptor
        val json = shareManager.generateJsonPayload(liveState)
        activity.lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    activity.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { writer ->
                        writer.write(json)
                    } ?: return@runCatching false
                    true
                }.onFailure { error ->
                    Timber.e(error, "Writing broadcast descriptor failed")
                }.getOrDefault(false)
            }
            Toast.makeText(
                activity,
                if (saved) R.string.broadcast_share_saved else R.string.broadcast_share_save_failed,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    /** Every lens the phone offers, not a front/back flip (S3050 owner requirement). */
    private fun showLensPicker(activity: AppCompatActivity) {
        activity.lifecycleScope.launch {
            val choice = listLenses.listOptions()
            if (choice.options.isEmpty()) {
                Toast.makeText(activity, R.string.broadcast_lens_none, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val activeLensId = (controller.state.value as? BroadcastState.Live)?.activeLensId ?: choice.initialLensId
            val labels = BroadcastLensLabelFormatter.labels(choice.options) { facing ->
                activity.getString(BroadcastLensLabelFormatter.facingNameRes(facing))
            }
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.broadcast_control_lens_dialog_title)
                .setSingleChoiceItems(
                    labels.toTypedArray(),
                    choice.options.indexOfFirst { it.id == activeLensId },
                ) { dialog, which ->
                    val lensId = choice.options[which].id
                    controller.selectLens(lensId)
                    // S3237: the live picker changes the same choice the idle chips show, so it is stored
                    // by the same rule - otherwise stopping the session restores the lens before it.
                    selectedLensId = lensId
                    persistLensId(activity, lensId)
                    dialog.dismiss()
                }
                .showBoundTo(activity)
        }
    }

    private fun renderState(
        activity: AppCompatActivity,
        binding: ActivityBroadcastControlBinding,
        state: BroadcastState
    ) {
        when (state) {
            is BroadcastState.Live -> {
                val switching = switchTargetMode?.let { it.name != state.descriptor.mode } == true
                if (!switching) switchTargetMode = null
                preStreamPreview.stop()
                binding.btnStartBroadcast.visibility = View.GONE
                binding.layoutLiveControls.visibility = View.VISIBLE
                binding.layoutSharePanel.visibility = View.VISIBLE
                renderScreenRealEstate(binding, state)
                setModeControlsEnabled(binding, !switching)
                if (!switching) {
                    checkModeChip(binding, state.descriptor.mode)
                    checkLensChip(binding, state.activeLensId)
                }
                renderModeControls(binding, state.descriptor.mode)
                renderToggles(binding, state)
                renderSendToWatch(binding, state)
                renderShareData(activity, binding)
                previewBinder.attach(binding.previewContainer)
            }
            // The transient idle between the two services of an audio-video switch is not the end of the session.
            is BroadcastState.Idle -> if (switchTargetMode == null) renderPreStream(activity, binding, state)
            is BroadcastState.Failed -> {
                switchTargetMode = null
                renderPreStream(activity, binding, state)
                showFailure(binding, state)
            }
        }
    }

    /** Keeps the mode chips on the mode on air: a relaunch or a refused switch would show another one. */
    private fun checkModeChip(binding: ActivityBroadcastControlBinding, modeName: String) {
        val chipId = when (modeName) {
            BroadcastMode.VIDEO_AUDIO.name -> R.id.chipModeVideoAudio
            BroadcastMode.VIDEO_ONLY.name -> R.id.chipModeVideoOnly
            else -> R.id.chipModeAudioOnly
        }
        if (binding.cgBroadcastMode.checkedChipId != chipId) binding.cgBroadcastMode.check(chipId)
    }

    /** The lens dialog switches the lens too, so the chips follow the lens on air, not the last chip tap. */
    private fun checkLensChip(binding: ActivityBroadcastControlBinding, lensId: String?) {
        if (lensId == null) return
        for (i in 0 until binding.cgLensSelection.childCount) {
            val chip = binding.cgLensSelection.getChildAt(i) as? Chip ?: continue
            chip.isChecked = chip.tag == lensId
        }
    }

    private fun renderPreStream(
        activity: AppCompatActivity,
        binding: ActivityBroadcastControlBinding,
        state: BroadcastState
    ) {
        previewBinder.detach()
        qrFullscreen.dismiss()
        binding.btnStartBroadcast.visibility = View.VISIBLE
        binding.layoutLiveControls.visibility = View.GONE
        binding.layoutSharePanel.visibility = View.GONE
        renderScreenRealEstate(binding, state)
        setModeControlsEnabled(binding, true)
        refreshPreStreamPreview(activity, binding)
    }

    /**
     * S3235: the two blocks that belong to one half of the screen's life only. Keeping both on screen
     * at all times pushed the live controls and the sharing block below the fold in both orientations.
     */
    private fun renderScreenRealEstate(
        binding: ActivityBroadcastControlBinding,
        state: BroadcastState
    ) {
        binding.btnBroadcastSettings.visibility =
            if (BroadcastEntryUi.showsSettingsCard(state)) View.VISIBLE else View.GONE
        binding.tvListenerCount.visibility =
            if (BroadcastEntryUi.showsListenerCount(state)) View.VISIBLE else View.GONE
    }

    private fun setModeControlsEnabled(binding: ActivityBroadcastControlBinding, enabled: Boolean) {
        binding.cgBroadcastMode.isEnabled = enabled
        for (i in 0 until binding.cgBroadcastMode.childCount) {
            binding.cgBroadcastMode.getChildAt(i).isEnabled = enabled
        }
        binding.cgLensSelection.isEnabled = enabled
        for (i in 0 until binding.cgLensSelection.childCount) {
            binding.cgLensSelection.getChildAt(i).isEnabled = enabled
        }
    }

    /**
     * Every entry surface that starts a broadcast from this screen - tile, widget, panel, shortcut -
     * used to return to the idle layout with no word of the failure, because only the main screen
     * reported it (S3054).
     */
    private fun showFailure(
        binding: ActivityBroadcastControlBinding,
        state: BroadcastState.Failed,
    ) {
        Timber.w("Broadcast failed: %s (%s)", state.failure, state.detail)
        val message = binding.root.context.getString(BroadcastFailureMessage.resFor(state.failure))
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
        // The state is static and its service is already gone, so nothing else retires it - without this
        // a rotation would replay the same message and the next start would begin from a failed state.
        controller.acknowledgeFailure()
    }

    private fun renderSendToWatch(
        binding: ActivityBroadcastControlBinding,
        state: BroadcastState,
    ) {
        val isVisible = state is BroadcastState.Live && wearSendAvailable
        binding.btnSendToWatch.visibility = if (isVisible) View.VISIBLE else View.GONE
        binding.btnSendToWatch.isEnabled = isVisible && !wearSendInProgress
    }

    private fun renderModeControls(binding: ActivityBroadcastControlBinding, modeName: String) {
        val hasCamera = modeName != BroadcastMode.AUDIO_ONLY.name
        val cameraVisibility = if (hasCamera) View.VISIBLE else View.GONE
        binding.previewContainer.visibility = cameraVisibility
        binding.btnToggleCamera.visibility = cameraVisibility
        binding.btnSwitchCamera.visibility = cameraVisibility
        binding.btnToggleMic.visibility =
            if (modeName == BroadcastMode.VIDEO_AUDIO.name) View.VISIBLE else View.GONE
        renderScreenOffLabel(binding)
    }

    /** The action blanks the app in store builds and truly sleeps the display where the camera survives it. */
    private fun renderScreenOffLabel(binding: ActivityBroadcastControlBinding) {
        val blanks = blankScreenManager.blanksInsteadOfSleeping()
        binding.btnScreenOff.setText(
            if (blanks) R.string.broadcast_control_blank_screen else R.string.broadcast_control_screen_off
        )
        binding.btnScreenOff.contentDescription = binding.root.context.getString(
            if (blanks) R.string.broadcast_control_blank_screen_cd else R.string.broadcast_control_screen_off_cd
        )
    }

    private fun renderToggles(binding: ActivityBroadcastControlBinding, state: BroadcastState.Live) {
        binding.btnToggleCamera.setText(
            if (state.cameraEnabled) R.string.broadcast_control_camera_on else R.string.broadcast_control_camera_off
        )
        binding.btnToggleCamera.setIconResource(R.drawable.ic_camera_capture)
        if (state.microphoneEnabled) {
            binding.btnToggleMic.setText(R.string.broadcast_control_mic_on)
            binding.btnToggleMic.setIconResource(R.drawable.ic_microphone)
        } else {
            binding.btnToggleMic.setText(R.string.broadcast_control_mic_off)
            binding.btnToggleMic.setIconResource(R.drawable.ic_microphone_off)
        }
    }

    private fun renderListenerCount(
        activity: AppCompatActivity,
        binding: ActivityBroadcastControlBinding,
        count: Int
    ) {
        binding.tvListenerCount.text = activity.getString(R.string.broadcast_control_listeners, count)
        binding.tvListenerCount.contentDescription = activity.getString(R.string.broadcast_control_listeners_cd, count)
    }

    /** What [renderPublishState] draws: the one line about the exchange server of a live broadcast (item DU). */
    private data class ServerLine(
        val live: Boolean,
        val serverName: String?,
        val publishState: BroadcastExchangePublisher.PublishState,
        val relayRunning: Boolean,
    )

    /**
     * `DEVICE-EXCHANGE` item DU: while a broadcast is live, one line names the exchange server and the state of the
     * broadcast there, or says that the broadcast is reachable on this network only and leads to adding a server.
     */
    private fun renderPublishState(
        activity: AppCompatActivity,
        binding: ActivityBroadcastControlBinding,
        line: ServerLine,
    ) {
        val publishState = line.publishState
        val serverName = line.serverName
        if (!line.live) {
            binding.tvPublishState.visibility = View.GONE
            binding.btnPublishDiagnose?.visibility = View.GONE
            binding.tvPublishState.setOnClickListener(null)
        } else if (serverName == null) {
            binding.tvPublishState.setText(R.string.broadcast_publish_state_network_only)
            binding.tvPublishState.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
            binding.tvPublishState.visibility = View.VISIBLE
            binding.btnPublishDiagnose?.visibility = View.GONE
            binding.tvPublishState.setOnClickListener {
                activity.startActivity(android.content.Intent(activity, ExchangeActivity::class.java))
            }
        } else {
            // Off with an outgoing entry is the moment before the service announces the broadcast (item DT).
            binding.tvPublishState.text = when (publishState) {
                BroadcastExchangePublisher.PublishState.Off,
                is BroadcastExchangePublisher.PublishState.Publishing ->
                    activity.getString(R.string.broadcast_publish_state_announcing, serverName)
                is BroadcastExchangePublisher.PublishState.Published -> activity.getString(
                    if (line.relayRunning) {
                        R.string.broadcast_publish_state_on_relay
                    } else {
                        R.string.broadcast_publish_state_on_server
                    },
                    serverName,
                )
                is BroadcastExchangePublisher.PublishState.NotPublished ->
                    activity.getString(R.string.broadcast_publish_state_not_on_server, serverName)
            }
            // S0039: EXCHANGE-MARK rule 8 (c) - the states that talk to the server carry its mark.
            val talksToServer = publishState !is BroadcastExchangePublisher.PublishState.NotPublished
            binding.tvPublishState.setCompoundDrawablesRelativeWithIntrinsicBounds(
                if (talksToServer) R.drawable.ic_exchange_mark_small else 0, 0, 0, 0,
            )
            binding.tvPublishState.visibility = View.VISIBLE
            if (publishState is BroadcastExchangePublisher.PublishState.NotPublished) {
                binding.btnPublishDiagnose?.visibility = View.VISIBLE
                val openDiagnosis = View.OnClickListener {
                    val msg = when (publishState.reason) {
                        BroadcastExchangePublisher.NotPublishedReason.SERVER_UNREACHABLE -> ExchangeMessage.UNREACHABLE_SERVER
                        BroadcastExchangePublisher.NotPublishedReason.REFUSED -> ExchangeMessage.UNAVAILABLE
                        BroadcastExchangePublisher.NotPublishedReason.RELAY_DROPPED -> ExchangeMessage.UNREACHABLE_SERVER
                        BroadcastExchangePublisher.NotPublishedReason.NO_ACCOUNT -> ExchangeMessage.BAD_CREDENTIALS
                        BroadcastExchangePublisher.NotPublishedReason.SERVER_NO_E2E -> ExchangeMessage.SERVER_NO_E2E
                    }
                    ExchangeDiagnosisDialog.show(
                        fragmentManager = activity.supportFragmentManager,
                        message = msg,
                        host = null,
                    )
                }
                binding.tvPublishState.setOnClickListener(openDiagnosis)
                binding.btnPublishDiagnose?.setOnClickListener(openDiagnosis)
            } else {
                binding.btnPublishDiagnose?.visibility = View.GONE
                binding.tvPublishState.setOnClickListener(null)
            }
        }
    }

    /**
     * S0029: the Google Drive line says in one sentence what the announcement is doing, and why it is not
     * announced when it is not. A failed write sits here as one line - it is not a toast that comes back.
     */
    private fun renderDriveState(binding: ActivityBroadcastControlBinding, state: DriveBroadcastAnnouncer.State) {
        val textRes = when (state) {
            DriveBroadcastAnnouncer.State.Off -> null
            is DriveBroadcastAnnouncer.State.Announcing -> R.string.broadcast_drive_state_announcing
            is DriveBroadcastAnnouncer.State.Announced -> R.string.broadcast_drive_state_announced
            is DriveBroadcastAnnouncer.State.Failed -> when (state.reason) {
                DriveBroadcastAnnouncer.FailureReason.NOT_SIGNED_IN -> R.string.broadcast_drive_failed_not_signed_in
                DriveBroadcastAnnouncer.FailureReason.SIGN_IN_LOST -> R.string.broadcast_drive_failed_sign_in_lost
                DriveBroadcastAnnouncer.FailureReason.DRIVE_FULL -> R.string.broadcast_drive_failed_drive_full
                DriveBroadcastAnnouncer.FailureReason.DAILY_LIMIT -> R.string.broadcast_drive_failed_daily_limit
                DriveBroadcastAnnouncer.FailureReason.REFUSED -> R.string.broadcast_drive_failed_refused
                DriveBroadcastAnnouncer.FailureReason.UNREACHABLE -> R.string.broadcast_drive_failed_unreachable
            }
        }
        if (textRes == null) {
            binding.tvDriveState.visibility = View.GONE
        } else {
            binding.tvDriveState.setText(textRes)
            binding.tvDriveState.visibility = View.VISIBLE
        }
    }

    /**
     * S3349: names the acoustic feedback loop as the reason the sound dipped. Without it a guard doing
     * its job is indistinguishable from a microphone that stopped working.
     */
    private fun renderFeedbackWarning(
        activity: AppCompatActivity,
        binding: ActivityBroadcastControlBinding,
        suppressed: Boolean
    ) {
        binding.tvFeedbackWarning.visibility = if (suppressed) View.VISIBLE else View.GONE
        binding.tvFeedbackWarning.contentDescription =
            activity.getString(R.string.broadcast_control_feedback_warning_cd)
    }

    /**
     * The singleton keeps the pending permission across a rotation but not across process death, and
     * the permission dialog survives both: a denial answered after a process death found nothing to
     * explain and stayed silent.
     */
    private fun keepPendingStartPermission(activity: AppCompatActivity) {
        val registry = activity.savedStateRegistry
        if (registry.isRestored && pendingStartPermission == null) {
            pendingStartPermission = registry.consumeRestoredStateForKey(START_PERMISSION_STATE_KEY)
                ?.getString(KEY_PENDING_START_PERMISSION)
        }
        registry.registerSavedStateProvider(START_PERMISSION_STATE_KEY) {
            bundleOf(KEY_PENDING_START_PERMISSION to pendingStartPermission)
        }
    }

    fun onDetach() {
        qrFullscreen.dismiss()
        preStreamPreview.stop()
        previewBinder.detach()
        blankScreenManager.detach()
        // setup() re-registers all three on the next attach, so dropping them here loses nothing.
        exportFileLauncher = null
        cameraPermissionLauncher = null
        startPermissionLauncher = null
    }

    companion object {
        private const val QR_SIZE_FRACTION = 0.45
        private const val QR_SIZE_MIN_PX = 200
        private const val QR_SIZE_MAX_PX = 500
        private const val BROADCAST_DESCRIPTOR_MIME_TYPE = "application/vnd.fms.bcast+json"
        private const val BROADCAST_DESCRIPTOR_EXTENSION = ".fmsbcast"
        private const val START_PERMISSION_STATE_KEY = "broadcast_control_pending_start_permission"
        private const val KEY_PENDING_START_PERMISSION = "permission"
    }
}
