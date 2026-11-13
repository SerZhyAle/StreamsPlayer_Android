package com.sza.streamsplayer.ui.broadcast.helpers

import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.sza.streamsplayer.R
import com.sza.streamsplayer.core.di.ApplicationScope
import com.sza.streamsplayer.data.exchange.drive.DriveAuthorizer
import com.sza.streamsplayer.data.exchange.drive.DriveChannelStore
import com.sza.streamsplayer.data.exchange.drive.DriveSignInState
import com.sza.streamsplayer.databinding.FragmentSettingsBroadcastBinding
import com.sza.streamsplayer.domain.model.AppSettings
import com.sza.streamsplayer.domain.model.BroadcastSettings
import com.sza.streamsplayer.domain.repository.SettingsRepository
import com.sza.streamsplayer.ui.common.widget.SettingsDropdownRow
import com.sza.streamsplayer.ui.common.widget.SettingsToggleRow
import com.sza.streamsplayer.ui.exchange.ExchangeActivity
import com.sza.streamsplayer.ui.settings.fragments.BroadcastSettingsOptions
import com.sza.streamsplayer.utils.collectOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Drives the broadcast preferences of the Broadcast page of the settings screen.
 *
 * S3060 bound this layout on the unified broadcast screen; S0049 moved it to the settings screen, which is now its one
 * editor (SETTINGS-NAVIGATION 2.0 section 3.2), and Broadcast Control opens that page instead of embedding a copy. The
 * broadcasting switch itself lives on the General page.
 */
@Singleton
class BroadcastSettingsPanelManager @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val exchangeEntries: com.sza.streamsplayer.data.local.db.ExchangeEntryDao,
    private val driveChannel: DriveChannelStore,
    private val driveAuthorizer: DriveAuthorizer,
    @param:ApplicationScope private val appScope: CoroutineScope,
) {

    /** The two families a Drive choice is remembered for (S0029 scope 1): audio, and the camera modes. */
    private enum class Family { AUDIO, CAMERA }

    // A write lands back through the settings Flow, and re-rendering a row fires its listener again;
    // without this the panel would write the value it has just read, once per emission.
    private var renderingFromSettings = false
    private var guest = false

    // The last committed port, so a rejected edit can be put back without reading the Flow again.
    private var currentPort: Int = BroadcastSettingsOptions.PORT_MIN

    // The most recent settings write, joined by awaitPendingWrites before an action reads the store. It runs in the
    // application scope, so leaving the settings screen right after an edit cannot cancel it half-way.
    private var pendingWrite: Job? = null

    private var firstFrameSettings: AppSettings? = null

    /**
     * Reads what the first frame of the page shows, before the page is inflated, so [bind] can present it whole: no row
     * appears and then hides itself once the stored values arrive (SETTINGS-NAVIGATION 2.0 section 5.1).
     */
    suspend fun prepareFirstFrame() {
        guest = exchangeEntries.observeEntry().first()?.accountKind == "guest"
        firstFrameSettings = settingsRepository.getSettings().first()
    }

    /** The one editor of the broadcasting switch is the General page; it writes through the same path as these rows. */
    fun setBroadcastingEnabled(enabled: Boolean) {
        update { it.copy(enableBroadcasting = enabled) }
    }

    fun bind(activity: AppCompatActivity, panel: FragmentSettingsBroadcastBinding) {
        panel.rowBitRate.setEntries(BroadcastSettingsOptions.bitRateLabels(activity))
        panel.rowAudioFormat.setEntries(BroadcastSettingsOptions.audioFormatLabels(activity))
        panel.rowMicGain.setEntries(BroadcastSettingsOptions.micGainLabels())

        panel.rowStreamTitle.setOnCommitListener { value ->
            val title = value.toString().trim()
            if (title.isNotEmpty()) {
                update { it.copy(broadcast = it.broadcast.copy(streamTitle = title)) }
            }
        }

        panel.rowPort.setOnCommitListener { value ->
            val port = value.toString().trim().toIntOrNull()
            if (port != null && BroadcastSettingsOptions.isValidPort(port)) {
                update { it.copy(broadcast = it.broadcast.copy(port = port)) }
            } else {
                renderPort(panel, currentPort)
            }
        }

        bindDropdown(panel.rowBitRate) { index ->
            val bitRate = BroadcastSettingsOptions.bitRatesBps.getOrNull(index) ?: return@bindDropdown
            update { it.copy(broadcast = it.broadcast.copy(bitRateBps = bitRate)) }
        }

        bindDropdown(panel.rowAudioFormat) { index ->
            val format = BroadcastSettingsOptions.audioFormats.getOrNull(index) ?: return@bindDropdown
            update {
                it.copy(
                    broadcast = it.broadcast.copy(
                        sampleRateHz = format.first,
                        channelCount = format.second
                    )
                )
            }
        }

        bindDropdown(panel.rowMicGain) { index ->
            val gain = BroadcastSettingsOptions.micGainPercents.getOrNull(index) ?: return@bindDropdown
            update { it.copy(broadcast = it.broadcast.copy(micGainPercent = gain)) }
        }

        panel.rowFeedbackGuard.setOnCheckedChangeListener { checked ->
            if (!renderingFromSettings) {
                update { it.copy(broadcast = it.broadcast.copy(feedbackGuardEnabled = checked)) }
            }
        }

        panel.rowAutoOpenShare.setOnCheckedChangeListener { checked ->
            if (!renderingFromSettings) {
                update { it.copy(broadcast = it.broadcast.copy(autoOpenShare = checked)) }
            }
        }

        bindDriveRow(activity, panel.rowPublishToDrive, Family.AUDIO)
        bindDriveRow(activity, panel.rowPublishVideoToDrive, Family.CAMERA)

        firstFrameSettings?.let { render(panel, it) }
        activity.collectOnLifecycle(settingsRepository.getSettings()) { settings ->
            render(panel, settings)
        }
        activity.collectOnLifecycle(exchangeEntries.observeEntry()) { entry ->
            val wasGuest = guest
            guest = entry?.accountKind == "guest"
            if (guest && !wasGuest) {
                Timber.d("S0027: guest account; the Drive announcement is not offered")
            }
            renderPublishVisibility(panel, panel.broadcastConfigGroup.isVisible)
        }
    }

    private fun bindDriveRow(activity: AppCompatActivity, row: SettingsToggleRow, family: Family) {
        row.setOnCheckedChangeListener { checked ->
            if (renderingFromSettings) return@setOnCheckedChangeListener
            if (checked && !driveChannel.noticeShown) {
                // The first switch-on says what is written to Drive before anything is (S0029 scope 1).
                showDriveNotice(activity, row, family)
            } else {
                update { it.copy(broadcast = withDrive(it.broadcast, family, checked)) }
            }
        }
    }

    private fun showDriveNotice(activity: AppCompatActivity, row: SettingsToggleRow, family: Family) {
        var confirmed = false
        val signedOut = driveAuthorizer.signInState.value != DriveSignInState.SIGNED_IN
        val message = buildString {
            append(activity.getString(R.string.broadcast_drive_notice_text))
            if (signedOut) append("\n\n").append(activity.getString(R.string.broadcast_drive_notice_sign_in_hint))
        }
        val confirm = {
            confirmed = true
            driveChannel.noticeShown = true
            update { it.copy(broadcast = withDrive(it.broadcast, family, true)) }
        }
        val builder = androidx.appcompat.app.AlertDialog.Builder(activity)
            .setTitle(R.string.broadcast_drive_notice_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                confirm()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .setOnDismissListener {
                if (!confirmed) {
                    // A dismissal that chose nothing leaves the announcement off: the switch is put back by
                    // hand, because no settings write follows and no Flow emission would.
                    renderingFromSettings = true
                    row.setCheckedSilently(false)
                    renderingFromSettings = false
                }
            }
        if (signedOut) {
            builder.setNeutralButton(R.string.broadcast_drive_notice_open) { dialog, _ ->
                confirm()
                activity.startActivity(android.content.Intent(activity, ExchangeActivity::class.java))
                dialog.dismiss()
            }
        }
        builder.show()
    }

    private fun driveOf(settings: AppSettings, family: Family): Boolean = when (family) {
        Family.AUDIO -> settings.broadcast.publishAudioToDrive
        Family.CAMERA -> settings.broadcast.publishVideoToDrive
    }

    private fun withDrive(broadcast: BroadcastSettings, family: Family, checked: Boolean): BroadcastSettings =
        when (family) {
            Family.AUDIO -> broadcast.copy(publishAudioToDrive = checked)
            Family.CAMERA -> broadcast.copy(publishVideoToDrive = checked)
        }

    private fun renderToggle(row: SettingsToggleRow, value: Boolean) {
        if (row.isChecked != value) row.setCheckedSilently(value)
    }

    // A guest announces nothing, through the server or through Drive (S0027).
    private fun renderPublishVisibility(panel: FragmentSettingsBroadcastBinding, enabled: Boolean) {
        val offered = enabled && !guest
        panel.rowPublishToDrive.isVisible = offered
        panel.rowPublishVideoToDrive.isVisible = offered
        panel.guestPublishNotice.isVisible = enabled && guest
    }

    /**
     * Commits the text rows the user may still be editing. Leaving the page leaves the field focused, so without
     * this the next reader sees the stored value while the page showed the typed one (S3234). The dropdown and
     * switch rows commit on selection and need nothing here.
     */
    fun flushPending(panel: FragmentSettingsBroadcastBinding) {
        panel.rowPort.commitPending()
        panel.rowStreamTitle.commitPending()
    }

    /**
     * Waits for the write a commit started. Committing only hands the value to a coroutine, so a caller
     * that reads the stored settings right after [flushPending] still reads the previous port - measured
     * on a device, where the process died between the two and the typed port was lost (S3234).
     */
    suspend fun awaitPendingWrites() {
        pendingWrite?.join()
    }

    private fun render(panel: FragmentSettingsBroadcastBinding, settings: AppSettings) {
        currentPort = settings.broadcast.port
        renderingFromSettings = true
        panel.rowStreamTitle.isVisible = settings.enableBroadcasting
        panel.broadcastConfigGroup.isVisible = settings.enableBroadcasting
        panel.rowFeedbackGuard.isVisible = settings.enableBroadcasting
        panel.rowAutoOpenShare.isVisible = settings.enableBroadcasting
        renderPublishVisibility(panel, settings.enableBroadcasting)
        if (panel.rowStreamTitle.text.toString() != settings.broadcast.streamTitle) {
            panel.rowStreamTitle.text = settings.broadcast.streamTitle
        }
        renderPort(panel, settings.broadcast.port)
        renderToggle(panel.rowFeedbackGuard, settings.broadcast.feedbackGuardEnabled)
        renderToggle(panel.rowAutoOpenShare, settings.broadcast.autoOpenShare)
        renderToggle(panel.rowPublishToDrive, driveOf(settings, Family.AUDIO))
        renderToggle(panel.rowPublishVideoToDrive, driveOf(settings, Family.CAMERA))
        setSelection(panel.rowBitRate, BroadcastSettingsOptions.bitRateIndex(settings))
        setSelection(panel.rowAudioFormat, BroadcastSettingsOptions.audioFormatIndex(settings))
        setSelection(panel.rowMicGain, BroadcastSettingsOptions.micGainIndex(settings))
        renderingFromSettings = false
    }

    private fun renderPort(panel: FragmentSettingsBroadcastBinding, port: Int) {
        if (panel.rowPort.text.toString() != port.toString()) {
            panel.rowPort.text = port.toString()
        }
    }

    private fun setSelection(row: SettingsDropdownRow, index: Int) {
        if (row.getSelectedIndex() != index) {
            row.setSelection(index)
        }
    }

    private fun bindDropdown(row: SettingsDropdownRow, onUserSelected: (Int) -> Unit) {
        row.setOnItemSelectedListener { position ->
            if (!renderingFromSettings) {
                onUserSelected(position)
            }
        }
    }

    private fun update(transform: (AppSettings) -> AppSettings) {
        pendingWrite = appScope.launch {
            settingsRepository.updateSettings { current -> transform(current) }
        }
    }
}
