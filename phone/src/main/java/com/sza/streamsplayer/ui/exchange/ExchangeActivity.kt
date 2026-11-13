package com.sza.streamsplayer.ui.exchange

import androidx.appcompat.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.sza.streamsplayer.R
import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorParser
import com.sza.streamsplayer.data.exchange.ExchangeConnectionString
import com.sza.streamsplayer.data.exchange.formatUserMessage
import com.sza.streamsplayer.databinding.ActivityExchangeBinding
import com.sza.streamsplayer.databinding.ItemExchangeLockedResourceBinding
import com.sza.streamsplayer.domain.usecase.streams.StreamMediaKindClassifier
import com.sza.streamsplayer.data.exchange.ExchangeLocalFailureClassifier
import com.sza.streamsplayer.domain.model.ExchangeMessage
import com.sza.streamsplayer.ui.exchange.ExchangeDiagnosisDialog
import com.sza.streamsplayer.ui.common.help.HelpKeys
import com.sza.streamsplayer.ui.common.help.HelpOpenManager
import com.sza.streamsplayer.ui.common.help.HelpSurfaceIds
import com.sza.streamsplayer.ui.common.help.HelpTooltipManager
import com.sza.streamsplayer.ui.common.support.exposeAsLink
import com.sza.streamsplayer.ui.common.widget.CollapsibleSectionsManager
import com.sza.streamsplayer.ui.common.support.ExchangePortalLinks
import com.sza.streamsplayer.ui.exchange.model.ExchangeBroadcastItem
import com.sza.streamsplayer.ui.exchange.model.ExchangeUiState
import com.sza.streamsplayer.ui.exchange.model.LockedResourceItem
import com.sza.streamsplayer.ui.exchange.model.PendingSignIn
import com.sza.streamsplayer.ui.player.FullscreenPlayerActivity
import com.sza.streamsplayer.ui.scanner.ScanQrContract
import com.sza.streamsplayer.ui.scanner.ScanResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.InputStream
import javax.inject.Inject

/**
 * Exchange Server screen handling quick join, directory view, and account management (S0019 Scope 1-8).
 */
@AndroidEntryPoint
class ExchangeActivity : AppCompatActivity() {

    private var lastErrorMessage: ExchangeMessage? = null
    private var lastErrorHost: String? = null
    private var lastErrorPort: Int? = null

    private val viewModel: ExchangeViewModel by viewModels()
    private val watchViewModel: WatchExchangeViewModel by viewModels()
    private val driveViewModel: DriveViewModel by viewModels()
    private var _binding: ActivityExchangeBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var broadcastParser: BroadcastDescriptorParser
    @Inject lateinit var descriptorStore: com.sza.streamsplayer.data.broadcast.BroadcastDescriptorStore
    @Inject lateinit var receiverRuntime: ExchangeReceiverRuntime

    @Inject
    lateinit var mediaKindClassifier: StreamMediaKindClassifier

    private val broadcastPasswordPrompt by lazy {
        com.sza.streamsplayer.ui.streams.helpers.StreamBroadcastPasswordPromptManager(this)
    }

    private val sections by lazy { CollapsibleSectionsManager(this) }

    private lateinit var directoryAdapter: ExchangeDirectoryAdapter
    private lateinit var driveAdapter: ExchangeDirectoryAdapter

    /** Google's consent screen for the one Drive scope; its result goes back to the Drive view model (S0029). */
    private val driveConsentLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        driveViewModel.onConsentResult(result.data)
    }

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            readExchangeFileUri(uri)
        }
    }

    private val scanQrLauncher = registerForActivityResult(ScanQrContract()) { result ->
        when (result) {
            is ScanResult.Text -> {
                Timber.d("S0019: received text from QR scan")
                applyConnectionString(result.text)
            }
            ScanResult.Denied -> {
                Toast.makeText(this, R.string.qr_scanner_permission_denied_message, Toast.LENGTH_SHORT).show()
            }
            ScanResult.NoCamera -> {
                Toast.makeText(this, R.string.qr_scanner_no_camera_message, Toast.LENGTH_LONG).show()
            }
            ScanResult.Cancelled -> {
                Timber.d("S0019: QR scan cancelled")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityExchangeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Timber.d("S0019: ExchangeActivity opened")

        setupToolbar()
        setupGroups()
        setupDirectoryRecycler()
        setupDrive()
        setupSignInControls()
        setupAccountControls()
        observeState()

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val connectionString = intent?.getStringExtra(EXTRA_CONNECTION_STRING)
        if (!connectionString.isNullOrBlank()) {
            Timber.d("S0019: connection string delivered via intent extra")
            applyConnectionString(connectionString)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (HelpKeys.handle(this, HelpSurfaceIds.EXCHANGE, event)) return true
        return super.dispatchKeyEvent(event)
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
        // S0027: the help action (CONTEXT-HELP rule 2), the only item the bar carries besides its back icon.
        binding.toolbar.inflateMenu(R.menu.menu_help)
        HelpTooltipManager.apply(this, binding.toolbar.menu.findItem(R.id.menu_help))
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.menu_help) {
                HelpOpenManager.open(this, HelpSurfaceIds.EXCHANGE)
                true
            } else {
                false
            }
        }
    }

    /** The screen's three topics, first use expanded, their state kept (COLLAPSIBLE-GROUP 1.1 sections 2.3 and 4.7). */
    private fun setupGroups() {
        Timber.d("S0049: exchange groups registered")
        sections.register(binding.headerDrive, binding.cardDrive, "exchange__drive", defaultExpanded = true)
        sections.register(binding.headerAccount, binding.groupAccountBody, "exchange__account", defaultExpanded = true)
        sections.register(binding.headerDirectory, binding.layoutDirectory, "exchange__broadcasts", defaultExpanded = true)
        listOf(
            binding.btnHelpDrive,
            binding.btnHelpAccounts,
            binding.btnHelpAccountsReady,
            binding.btnHelpDeleteAccount,
            binding.btnHelpBroadcastList,
            binding.btnHelpBroadcastStart,
        ).forEach { it.exposeAsLink() }
    }

    private fun setupDirectoryRecycler() {
        directoryAdapter = ExchangeDirectoryAdapter(
            onPlayClick = { item -> playBroadcast(item) },
            onKeepClick = { item -> keepBroadcast(item) }
        )
        binding.rvDirectory.layoutManager = LinearLayoutManager(this)
        binding.rvDirectory.adapter = directoryAdapter
        binding.btnHelpBroadcastList.setOnClickListener { ExchangePortalLinks.open(this, ExchangePortalLinks.Page.BROADCAST_RECEIVE) }
        binding.btnHelpBroadcastStart.setOnClickListener { ExchangePortalLinks.open(this, ExchangePortalLinks.Page.BROADCAST_START) }
        binding.btnDiagnose.setOnClickListener {
            val current = viewModel.uiState.value
            val (host, port) = when (current) {
                is ExchangeUiState.NoAccount -> current.form.host.ifBlank { null } to current.form.port
                is ExchangeUiState.AccountReady -> current.entry.host to current.entry.port
                else -> lastErrorHost to lastErrorPort
            }
            ExchangeDiagnosisDialog.show(
                fragmentManager = supportFragmentManager,
                message = lastErrorMessage ?: ExchangeMessage.UNREACHABLE_SERVER,
                host = host,
                port = port ?: 443,
            )
        }
        binding.btnDriveDiagnose.setOnClickListener {
            ExchangeDiagnosisDialog.show(
                fragmentManager = supportFragmentManager,
                message = ExchangeMessage.UNREACHABLE_SERVER,
                host = "drive.google.com",
            )
        }
    }

    /** S0029: the "through Google Drive" source of the broadcast list; it works with no exchange account. */
    private fun setupDrive() {
        driveAdapter = ExchangeDirectoryAdapter(
            onPlayClick = { item -> playBroadcast(item) },
            onKeepClick = {},
            sourceLabel = getString(R.string.drive_source_label),
            showKeep = false,
        )
        binding.rvDriveDirectory.layoutManager = LinearLayoutManager(this)
        binding.rvDriveDirectory.adapter = driveAdapter
        binding.btnDriveSignIn.setOnClickListener { driveViewModel.signIn() }
        binding.btnHelpDrive.setOnClickListener { ExchangePortalLinks.open(this, ExchangePortalLinks.Page.FOLDERS_GOOGLE_DRIVE) }
        binding.btnDriveSignOut.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.drive_action_sign_out)
                .setMessage(R.string.drive_sign_out_message)
                .setPositiveButton(android.R.string.ok) { _, _ -> driveViewModel.signOut() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun renderDrive(state: DriveUiState) {
        binding.tvDriveStatus.setText(
            if (state.signedIn) R.string.drive_status_signed_in else R.string.drive_status_signed_out
        )
        binding.btnDriveSignIn.isVisible = !state.signedIn
        binding.btnDriveSignIn.isEnabled = !state.busy
        binding.btnDriveSignOut.isVisible = state.signedIn
        binding.tvDriveMessage.isVisible = state.messageRes != null
        binding.btnDriveDiagnose.isVisible = state.messageRes != null
        state.messageRes?.let {
            binding.tvDriveMessage.setText(it)
            binding.tvDriveMessage.announceForAccessibility(getString(it))
        }
        binding.layoutDriveDirectory.isVisible = state.signedIn
        binding.tvDriveEmpty.isVisible = state.signedIn && state.isEmpty
        driveAdapter.submitGroups(state.groups)
    }

    private fun setupSignInControls() {
        binding.switchPairingCode.setOnCheckedChangeListener { isChecked ->
            binding.tilSecret.hint = if (isChecked) {
                getString(R.string.exchange_pairing_code_label)
            } else {
                getString(R.string.exchange_password_label)
            }
            viewModel.onFormPairingCodeToggled(isChecked)
        }

        binding.btnPaste.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString()?.trim().orEmpty()
                if (text.isNotEmpty()) {
                    applyConnectionString(text)
                } else {
                    Toast.makeText(this, R.string.exchange_clipboard_empty, Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, R.string.exchange_clipboard_empty, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnOpenFile.setOnClickListener {
            openDocumentLauncher.launch(arrayOf(
                "application/x-fmsexch",
                "text/plain",
                "*/*"
            ))
        }

        binding.btnScanQr.setOnClickListener {
            scanQrLauncher.launch(Unit)
        }

        binding.btnHelpAccounts.setOnClickListener { ExchangePortalLinks.open(this, ExchangePortalLinks.Page.EXCHANGE_ACCOUNTS) }
        binding.btnSignIn.setOnClickListener {
            submitSignIn()
        }

        binding.etSecret.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitSignIn()
                true
            } else {
                false
            }
        }
    }

    private fun setupAccountControls() {
        binding.btnHelpAccountsReady.setOnClickListener { ExchangePortalLinks.open(this, ExchangePortalLinks.Page.EXCHANGE_ACCOUNTS) }
        binding.btnHelpDeleteAccount.setOnClickListener { ExchangePortalLinks.open(this, ExchangePortalLinks.Page.ACCOUNT_DELETION) }
        binding.btnConnectWatch.setOnClickListener { watchViewModel.connectWatch() }
        binding.switchPlayCastImmediately.isChecked = receiverRuntime.playImmediately
        binding.switchPlayCastImmediately.setOnCheckedChangeListener { enabled ->
            receiverRuntime.playImmediately = enabled
        }
        binding.btnSignInAgain.setOnClickListener { viewModel.onSignInAgain() }
        binding.btnChangePassword.setOnClickListener {
            val entry = (viewModel.uiState.value as? ExchangeUiState.AccountReady)?.entry ?: return@setOnClickListener
            ExchangePasswordDialog.show(this, viewModel, "${entry.host}:${entry.port}")
        }

        binding.btnReplacePin.setOnClickListener {
            val state = viewModel.uiState.value
            if (state is ExchangeUiState.AccountReady) {
                showReplacePinDialog(state.entry.pin)
            }
        }

        binding.btnChangeServer.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.exchange_action_change_server)
                .setMessage(R.string.exchange_change_server_confirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    viewModel.onChangeServer()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        binding.btnRemoveAccount.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.exchange_action_remove_account)
                .setMessage(R.string.exchange_remove_confirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    viewModel.onRemoveAccount {
                        Toast.makeText(this, watchViewModel.removalMessage(), Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        binding.btnKeepAll.setOnClickListener {
            val state = viewModel.uiState.value
            if (state is ExchangeUiState.AccountReady) {
                viewModel.onKeepAllBroadcasts(state.entry.entryId, passwordFor = { broadcastPasswordPrompt.ask() }) { count ->
                    Toast.makeText(
                        this,
                        getString(R.string.exchange_keep_all_toast, count),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun submitSignIn() {
        val host = binding.etHost.text?.toString()?.trim().orEmpty()
        val port = binding.etPort.text?.toString()?.trim()?.toIntOrNull() ?: 443
        val login = binding.etLogin.text?.toString()?.trim().orEmpty()
        val secret = binding.etSecret.text?.toString().orEmpty()
        val isPairingCode = binding.switchPairingCode.isChecked

        val currentForm = (viewModel.uiState.value as? ExchangeUiState.NoAccount)?.form
        val fingerprint = currentForm?.fingerprint
        val labelHint = currentForm?.labelHint

        viewModel.onSignIn(
            host = host,
            port = port,
            login = login,
            secret = secret,
            isPairingCode = isPairingCode,
            fingerprint = fingerprint,
            labelHint = labelHint,
        )

        // Clear secret from password field after sending (Scope 4)
        binding.etSecret.text?.clear()
    }

    private fun applyConnectionString(text: String) {
        // The form it fills may be in a group the user collapsed (COLLAPSIBLE-GROUP 1.1 section 4.2).
        sections.expandSectionContaining(listOf(binding.groupAccountBody.id))
        val outcome = viewModel.onApplyConnectionStringText(text)
        when (outcome) {
            is ExchangeConnectionString.Outcome.Valid -> {
                binding.etHost.setText(outcome.hostPort.substringBeforeLast(':').trimStart('[').trimEnd(']'))
                binding.etPort.setText(outcome.hostPort.substringAfterLast(':'))
                if (!outcome.login.isNullOrBlank()) {
                    binding.etLogin.setText(outcome.login)
                }
                if (!outcome.name.isNullOrBlank()) {
                    binding.tvLabelHint.isVisible = true
                    binding.tvLabelHint.text = getString(R.string.exchange_label_hint, outcome.name)
                } else {
                    binding.tvLabelHint.isVisible = false
                }
                if (!outcome.fingerprint.isNullOrBlank()) {
                    binding.tvFingerprintHint.isVisible = true
                    binding.tvFingerprintHint.text = getString(R.string.exchange_fingerprint_from_string, outcome.fingerprint)
                } else {
                    binding.tvFingerprintHint.isVisible = false
                }
                binding.tvInvitationNotice.isVisible = outcome.isInvitation
                binding.etSecret.text?.clear()
                binding.tvDestination.text = outcome.hostPort
                binding.tvDestination.isVisible = true
                binding.layoutDestinationFields.isVisible = false
                binding.cardError.isVisible = false
            }
            is ExchangeConnectionString.Outcome.UnsupportedMajor,
            is ExchangeConnectionString.Outcome.NotExchange,
            is ExchangeConnectionString.Outcome.Refused -> {
                val msg = outcome.formatUserMessage(this) ?: getString(R.string.exchange_connection_invalid)
                showError(msg)
            }
        }
    }

    private fun readExchangeFileUri(uri: android.net.Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val outcome = ExchangeConnectionString.readConnectionStringFile(stream)
                when (outcome) {
                    is ExchangeConnectionString.Outcome.Valid -> {
                        applyConnectionString(ExchangeConnectionString.encode(outcome.connectionString))
                    }
                    else -> {
                        val msg = outcome.formatUserMessage(this) ?: getString(R.string.exchange_connection_invalid)
                        showError(msg)
                    }
                }
            } ?: showError(getString(R.string.exchange_error_open_file))
        } catch (e: Exception) {
            Timber.d(e, "S0019: error reading exchange file")
            showError(getString(R.string.exchange_error_read_file))
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                watchViewModel.state.collectLatest { state ->
                    binding.btnConnectWatch.isEnabled = !state.busy
                    binding.tvWatchPairingStatus.isVisible = state.message != null
                    state.message?.let(binding.tvWatchPairingStatus::setText)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    renderState(state)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                driveViewModel.uiState.collectLatest(::renderDrive)
            }
        }
        lifecycleScope.launch {
            driveViewModel.consent.collect { pending ->
                driveConsentLauncher.launch(IntentSenderRequest.Builder(pending).build())
            }
        }
    }

    private fun renderState(state: ExchangeUiState) {
        // A broadcast the server's list shows is not listed a second time under Drive (S0029).
        driveViewModel.setExchangeBroadcastIds(
            (state as? ExchangeUiState.AccountReady)?.directory?.devices
                ?.flatMap { group -> group.broadcasts.map { it.broadcastId } }?.toSet().orEmpty()
        )
        when (state) {
            is ExchangeUiState.NoAccount -> {
                receiverRuntime.refreshAccount()
                binding.progressBar.isVisible = false
                binding.layoutSignIn.isVisible = true
                binding.layoutAccountReady.isVisible = false
                binding.layoutDirectoryGroup.isVisible = false

                val form = state.form
                binding.tvInvitationNotice.isVisible = form.isInvitation
                binding.tvDestination.text = form.hostPortDisplay
                binding.tvDestination.isVisible = form.host.isNotBlank()
                binding.layoutDestinationFields.isVisible = form.host.isBlank()
                if (form.host.isNotBlank()) {
                    binding.etHost.setText(form.host)
                    binding.etPort.setText(form.port.toString())
                    binding.etLogin.setText(form.login)
                }
                if (form.errorText != null) {
                    showError(form.errorText, form.host.ifBlank { null }, form.port, aboutServer = form.errorAboutServer)
                } else {
                    binding.cardError.isVisible = false
                }

                if (binding.etHost.text.isNullOrBlank() && form.host.isNotBlank()) {
                    binding.etHost.setText(form.host)
                }
                if (form.labelHint != null) {
                    binding.tvLabelHint.isVisible = true
                    binding.tvLabelHint.text = getString(R.string.exchange_label_hint, form.labelHint)
                } else {
                    binding.tvLabelHint.isVisible = false
                }
                if (form.fingerprint != null) {
                    binding.tvFingerprintHint.isVisible = true
                    binding.tvFingerprintHint.text = getString(R.string.exchange_fingerprint_from_string, form.fingerprint)
                } else {
                    binding.tvFingerprintHint.isVisible = false
                }
            }
            is ExchangeUiState.Connecting -> {
                binding.progressBar.isVisible = true
                binding.cardError.isVisible = false
            }
            is ExchangeUiState.UntrustedCertificate -> {
                binding.progressBar.isVisible = false
                showUntrustedCertDialog(state.presentedFingerprint, state.pendingSignIn)
            }
            is ExchangeUiState.CertificateChanged -> {
                binding.progressBar.isVisible = false
                showCertChangedDialog(state.pinnedFingerprint, state.presentedFingerprint, state.pendingSignIn)
            }
            is ExchangeUiState.AccountReady -> {
                receiverRuntime.refreshAccount()
                binding.progressBar.isVisible = state.directory.isLoading
                binding.layoutSignIn.isVisible = false
                binding.layoutAccountReady.isVisible = true
                binding.layoutDirectoryGroup.isVisible = true
                binding.cardError.isVisible = false

                val entry = state.entry
                binding.tvAccountEndpoint.text = entry.endpoint
                binding.tvAccountLogin.text = entry.login
                binding.tvAccountStatus.setText(if (entry.isGuest) R.string.exchange_guest_account else R.string.exchange_status_connected)
                binding.tvBroadcastsOnServer.isVisible = !entry.isGuest
                binding.btnHelpBroadcastStart.isVisible = !entry.isGuest
                binding.tvGuestNotice.isVisible = entry.isGuest
                binding.btnChangePassword.isEnabled = !viewModel.passwordChange.value.busy
                binding.tvAccountPin.text =
                    getString(R.string.exchange_account_pin, entry.pin ?: getString(R.string.exchange_account_pin_none))

                val dir = state.directory
                binding.tvSftpNotice.isVisible = dir.hasSftpShares
                renderLockedResources(dir.lockedResources)
                binding.tvDirectoryEmpty.isVisible = dir.isEmpty
                binding.btnKeepAll.isEnabled = dir.totalPlayableBroadcasts > 0

                directoryAdapter.submitGroups(dir.devices)

                dir.errorRes?.let { showError(getString(it), entry.host, entry.port, aboutServer = true) }
                if (dir.errorText != null) {
                    showError(dir.errorText, entry.host, entry.port, aboutServer = true)
                }
            }
            is ExchangeUiState.Error -> {
                binding.progressBar.isVisible = false
                showError(state.message)
            }
        }
    }

    /**
     * One row per resource behind a PIN (DEVICE-EXCHANGE item AJ): read by TalkBack as one unit, skipped by the D-pad
     * because the row offers no action - this product asks for no PIN.
     */
    private fun renderLockedResources(rows: List<LockedResourceItem>) {
        val container = binding.layoutLockedResources
        container.removeAllViews()
        container.isVisible = rows.isNotEmpty()
        for (row in rows) {
            val item = ItemExchangeLockedResourceBinding.inflate(layoutInflater, container, false)
            item.tvLockedName.text = row.name
            item.tvLockedDevice.text = row.deviceName
            item.tvLockedDevice.isVisible = !row.deviceName.isNullOrBlank()
            ViewCompat.setScreenReaderFocusable(item.root, true)
            container.addView(item.root)
        }
    }

    private fun showError(
        message: String,
        host: String? = null,
        port: Int? = null,
        msgEnum: ExchangeMessage? = null,
        aboutServer: Boolean = false,
    ) {
        binding.cardError.isVisible = true
        // S0039: EXCHANGE-MARK rule 8 (c) - only an error about the server carries the mark (list in S0039 7.1).
        binding.tvError.setCompoundDrawablesRelativeWithIntrinsicBounds(
            if (aboutServer) R.drawable.ic_exchange_mark_small else 0, 0, 0, 0,
        )
        binding.tvError.text = message
        binding.tvError.announceForAccessibility(message)
        lastErrorMessage = msgEnum ?: ExchangeMessage.UNREACHABLE_SERVER
        lastErrorHost = host
        lastErrorPort = port
    }

    private fun showUntrustedCertDialog(presented: String, pending: PendingSignIn) {
        AlertDialog.Builder(this)
            .setIcon(R.drawable.ic_exchange_mark_small)
            .setTitle(R.string.exchange_untrusted_cert_dialog_title)
            .setMessage(getString(R.string.exchange_untrusted_cert_dialog_message, presented))
            .setPositiveButton(R.string.exchange_action_trust) { _, _ ->
                viewModel.onAcceptUntrustedCertificate(pending, presented)
            }
            .setNegativeButton(R.string.action_cancel) { _, _ ->
                viewModel.loadAccount()
            }
            .setCancelable(false)
            .show()
    }

    private fun showCertChangedDialog(pinned: String, presented: String, pending: PendingSignIn) {
        AlertDialog.Builder(this)
            .setIcon(R.drawable.ic_exchange_mark_small)
            .setTitle(R.string.exchange_replace_pin_dialog_title)
            .setMessage(getString(R.string.exchange_replace_pin_dialog_message, pinned, presented))
            .setPositiveButton(R.string.exchange_action_replace) { _, _ ->
                viewModel.onAcceptUntrustedCertificate(pending, presented)
            }
            .setNegativeButton(R.string.action_cancel) { _, _ ->
                viewModel.loadAccount()
            }
            .setCancelable(false)
            .show()
    }

    private fun showReplacePinDialog(currentPin: String?) {
        val input = com.google.android.material.textfield.TextInputEditText(this)
        input.hint = "SHA256:..."
        AlertDialog.Builder(this)
            .setTitle(R.string.exchange_action_replace_pin)
            .setView(input)
            .setPositiveButton(R.string.exchange_action_replace) { _, _ ->
                val newPin = input.text?.toString()?.trim().orEmpty()
                if (newPin.isNotEmpty()) {
                    viewModel.onReplacePin(newPin) {
                        Toast.makeText(this, R.string.exchange_pin_replaced, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun playBroadcast(item: ExchangeBroadcastItem) {
        val outcome = broadcastParser.parseDetailed(item.descriptorJson)
        if (outcome is BroadcastDescriptorParser.ParseOutcome.Valid) {
            val url = outcome.dto.url
            descriptorStore.saveDescriptor(url, outcome.dto.sourceId, outcome.dto)
            Timber.d("S0020: playing directory broadcast")
            val playerIntent = Intent(this, FullscreenPlayerActivity::class.java).apply {
                putExtra(FullscreenPlayerActivity.EXTRA_CHANNEL_URL, url)
                putExtra(FullscreenPlayerActivity.EXTRA_CHANNEL_TITLE, item.title)
            }
            startActivity(playerIntent)
        } else {
            Toast.makeText(this, R.string.import_broadcast_unsupported_version, Toast.LENGTH_SHORT).show()
        }
    }

    private fun keepBroadcast(item: ExchangeBroadcastItem) {
        val state = viewModel.uiState.value as? ExchangeUiState.AccountReady ?: return
        lifecycleScope.launch {
            val password = if (viewModel.needsBroadcastPassword(item.descriptorJson)) {
                broadcastPasswordPrompt.ask() ?: return@launch
            } else null
            viewModel.onKeepBroadcast(item, state.entry.entryId, password) {
                Toast.makeText(this@ExchangeActivity, R.string.exchange_channel_kept_toast, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.setDirectoryVisible(true)
        driveViewModel.setVisible(true)
    }

    override fun onStop() {
        viewModel.setDirectoryVisible(false)
        driveViewModel.setVisible(false)
        super.onStop()
    }

    override fun onDestroy() {
        broadcastPasswordPrompt.close()
        super.onDestroy()
        _binding = null
    }

    companion object {
        const val EXTRA_CONNECTION_STRING = "com.sza.streamsplayer.extra.CONNECTION_STRING"
    }
}
