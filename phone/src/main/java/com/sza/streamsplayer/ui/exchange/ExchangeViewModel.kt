package com.sza.streamsplayer.ui.exchange

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.streamsplayer.data.exchange.DeviceIdGenerator
import com.sza.streamsplayer.data.exchange.DeviceExchangeAccountEntry
import com.sza.streamsplayer.data.exchange.EntryStore
import com.sza.streamsplayer.data.exchange.ExchangeAccountEntry
import com.sza.streamsplayer.data.exchange.ExchangeAccountManager
import com.sza.streamsplayer.data.exchange.ExchangeConnectionString
import com.sza.streamsplayer.data.exchange.directory.AccountBroadcastDirectory
import com.sza.streamsplayer.data.exchange.directory.AccountDirectoryState
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeDirectoryClient
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeRecordCodec
import com.sza.streamsplayer.data.exchange.directory.DirectorySnapshot
import com.sza.streamsplayer.data.exchange.errorMessage
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeAccountClient
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeControlStream
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeCredential
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeEnrollResult
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeEnrollment
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeReasons
import com.sza.streamsplayer.domain.usecase.streams.ImportStreamBroadcastUseCase
import com.sza.streamsplayer.ui.exchange.model.DeviceBroadcastGroup
import com.sza.streamsplayer.ui.exchange.model.ExchangeBroadcastItem
import com.sza.streamsplayer.ui.exchange.model.ExchangeDirectoryState
import com.sza.streamsplayer.ui.exchange.model.ExchangeFormState
import com.sza.streamsplayer.ui.exchange.model.ExchangeUiState
import com.sza.streamsplayer.ui.exchange.model.PendingSignIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

/**
 * State holder and coordinator for the Exchange server join, directory, and account views
 * (S0019 Scope 1-8).
 */
@HiltViewModel
class ExchangeViewModel @Inject constructor(
    private val entryStore: EntryStore,
    private val accountClient: DeviceExchangeAccountClient,
    private val accountManager: ExchangeAccountManager,
    private val importBroadcastUseCase: ImportStreamBroadcastUseCase,
    private val controlStream: DeviceExchangeControlStream,
    private val directory: AccountBroadcastDirectory,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ExchangeUiState>(ExchangeUiState.NoAccount())
    val uiState: StateFlow<ExchangeUiState> = _uiState.asStateFlow()

    init {
        loadAccount()
        viewModelScope.launch {
            controlStream.signInReason.collect { reason ->
                if (reason != null) entryStore.getAccountEntry()?.let { showSignIn(it, reason) }
            }
        }
    }

    private var directoryVisible = false
    private var directoryJob: kotlinx.coroutines.Job? = null
    private var directoryLease: AccountBroadcastDirectory.Lease? = null

    fun setDirectoryVisible(visible: Boolean) {
        directoryVisible = visible
        stopDirectory()
        if (visible) (uiState.value as? ExchangeUiState.AccountReady)?.entry?.let(::fetchDirectory)
    }

    private fun stopDirectory() {
        directoryJob?.cancel()
        directoryJob = null
        directoryLease?.close()
        directoryLease = null
    }

    private fun showSignIn(entry: ExchangeAccountEntry, reason: String? = entry.signInReason) {
        stopDirectory()
        Timber.d("S0027: sign-in surface shown for the retained entry id=${ExchangeAccountManager.hashId(entry.entryId)} reason=$reason")
        _uiState.value = ExchangeUiState.NoAccount(ExchangeFormState(
            host = entry.host, port = entry.port, login = entry.login,
            labelHint = entry.label, fingerprint = entry.pin,
            errorText = reason?.let { DeviceExchangeReasons.message(it) },
            errorAboutServer = reason != null,
        ))
    }

    fun onSignInAgain() {
        viewModelScope.launch { entryStore.getAccountEntry()?.let { showSignIn(it) } }
    }

    val passwordChange = MutableStateFlow<PasswordChangeState>(PasswordChangeState())

    fun onChangePassword(old: CharArray, new: CharArray, confirmation: CharArray) {
        if (passwordChange.value.busy) {
            old.fill('\u0000'); new.fill('\u0000'); confirmation.fill('\u0000')
            return
        }
        val error = when {
            old.isEmpty() || new.isEmpty() -> com.sza.streamsplayer.R.string.exchange_password_required
            !new.contentEquals(confirmation) -> com.sza.streamsplayer.R.string.exchange_password_mismatch
            else -> null
        }
        confirmation.fill('\u0000')
        if (error != null) {
            old.fill('\u0000'); new.fill('\u0000')
            passwordChange.value = PasswordChangeState(messageRes = error)
            return
        }
        Timber.d("S0027: password change submitted")
        passwordChange.value = PasswordChangeState(busy = true)
        viewModelScope.launch {
            val oldCredential = DeviceExchangeCredential.Password(old)
            val newCredential = DeviceExchangeCredential.Password(new)
            try {
                val entry = entryStore.getAccountEntry() ?: throw IOException("No account")
                stopDirectory()
            controlStream.setEntry(null)
                withContext(Dispatchers.IO) {
                    accountClient.changePassword(entry.toDeviceExchangeAccountEntry(), oldCredential, newCredential)
                }
                Timber.d("S0027: password change succeeded; kept channels and entry remain")
                passwordChange.value = PasswordChangeState(messageRes = com.sza.streamsplayer.R.string.exchange_password_changed)
            } catch (failure: com.sza.streamsplayer.data.exchange.session.DeviceExchangeRefusedException) {
                Timber.d("S0027: password change refused reason=${failure.reason}")
                passwordChange.value = PasswordChangeState(messageRes = DeviceExchangeReasons.stringResId(failure.reason))
            } catch (failure: IOException) {
                Timber.d("S0027: password change did not reach the server")
                passwordChange.value = PasswordChangeState(messageRes = com.sza.streamsplayer.R.string.exchange_account_action_failed)
            } finally {
                oldCredential.wipe(); newCredential.wipe()
                entryStore.getAccountEntry()?.let { entry ->
                    if (entry.state == com.sza.streamsplayer.data.exchange.ExchangeAccountState.Connected) {
                        controlStream.setEntry(entry.toDeviceExchangeAccountEntry())
                    } else showSignIn(entry)
                }
            }
        }.invokeOnCompletion { old.fill('\u0000'); new.fill('\u0000') }
    }

    data class PasswordChangeState(val busy: Boolean = false, val messageRes: Int? = null)

    fun loadAccount() {
        viewModelScope.launch {
            val entry = entryStore.getAccountEntry()
            if (entry != null && entry.state != com.sza.streamsplayer.data.exchange.ExchangeAccountState.Connected) {
                showSignIn(entry)
            } else if (entry != null) {
                Timber.d("S0019: loaded existing account entry id=${ExchangeAccountManager.hashId(entry.entryId)}")
                controlStream.setEntry(entry.toDeviceExchangeAccountEntry())
                _uiState.value = ExchangeUiState.AccountReady(
                    entry = entry,
                    // Hidden screen: no load is running, so it must not read as loading.
                    directory = ExchangeDirectoryState(isLoading = directoryVisible)
                )
                fetchDirectory(entry)
            } else {
                Timber.d("S0019: no account entry found; showing sign-in surface")
                _uiState.value = ExchangeUiState.NoAccount(ExchangeFormState())
            }
        }
    }

    /**
     * Applies a connection string text into the form (S0019 Scope 2, 3 and 5):
     * Fills the form and STOPS - no TLS, no enroll!
     */
    fun onApplyConnectionStringText(rawText: String): ExchangeConnectionString.Outcome {
        val outcome = ExchangeConnectionString.parse(rawText)
        when (outcome) {
            is ExchangeConnectionString.Outcome.Valid -> {
                if (outcome.isInvitation) {
                    Timber.d("S0027: invitation string opens sign-in only; invitation bytes are validated and discarded")
                }
                val hostPort = outcome.hostPort
                val colon = hostPort.lastIndexOf(':')
                val host = if (hostPort.startsWith("[")) {
                    hostPort.substringBeforeLast("]:").trimStart('[')
                } else {
                    hostPort.substring(0, colon)
                }
                val port = hostPort.substring(colon + 1).toIntOrNull() ?: 443

                Timber.d("S0019: applied connection string host=$host port=$port")
                _uiState.update { current ->
                    val existingForm = (current as? ExchangeUiState.NoAccount)?.form ?: ExchangeFormState()
                    ExchangeUiState.NoAccount(
                        existingForm.copy(
                            host = host,
                            port = port,
                            login = outcome.login ?: existingForm.login,
                            labelHint = outcome.name,
                            fingerprint = outcome.fingerprint,
                            errorText = null,
                            isInvitation = outcome.isInvitation,
                        )
                    )
                }
            }
            is ExchangeConnectionString.Outcome.UnsupportedMajor,
            is ExchangeConnectionString.Outcome.NotExchange,
            is ExchangeConnectionString.Outcome.Refused -> {
                val errorMsg = outcome.errorMessage() ?: "Invalid connection string"
                Timber.d("S0019: connection string refused: $errorMsg")
                _uiState.update { current ->
                    val existingForm = (current as? ExchangeUiState.NoAccount)?.form ?: ExchangeFormState()
                    ExchangeUiState.NoAccount(
                        existingForm.copy(errorText = errorMsg, errorAboutServer = false)
                    )
                }
            }
        }
        return outcome
    }

    fun onFormHostChanged(host: String) {
        updateForm { it.copy(host = host.trim(), errorText = null) }
    }

    fun onFormPortChanged(port: Int) {
        updateForm { it.copy(port = port, errorText = null) }
    }

    fun onFormLoginChanged(login: String) {
        updateForm { it.copy(login = login.trim(), errorText = null) }
    }

    fun onFormPairingCodeToggled(isPairingCode: Boolean) {
        updateForm { it.copy(isPairingCode = isPairingCode, errorText = null) }
    }

    fun onFormSecretChanged(secret: String) {
        updateForm { it.copy(secret = secret, errorText = null) }
    }

    private fun updateForm(transform: (ExchangeFormState) -> ExchangeFormState) {
        _uiState.update { current ->
            when (current) {
                is ExchangeUiState.NoAccount -> ExchangeUiState.NoAccount(transform(current.form))
                else -> current
            }
        }
    }

    /**
     * Executes the Join Order (S0019 Scope 4):
     * parse/validate -> TLS and trust rule -> enroll -> token to secret store and entry recorded -> list
     */
    fun onSignIn(
        host: String,
        port: Int,
        login: String,
        secret: String,
        isPairingCode: Boolean,
        fingerprint: String? = null,
        labelHint: String? = null,
        acceptedFingerprint: String? = null,
    ) {
        if (_uiState.value is ExchangeUiState.Connecting) return
        val trimmedHost = host.trim()
        val trimmedLogin = login.trim()

        if (trimmedHost.isBlank()) {
            setFormError("Server address is required")
            return
        }
        if (port !in 1..65535) {
            setFormError("Port must be between 1 and 65535")
            return
        }
        if (trimmedLogin.isBlank()) {
            setFormError("Login is required")
            return
        }
        if (secret.isEmpty()) {
            setFormError("Password or pairing code is required")
            return
        }

        val pinToUse = acceptedFingerprint ?: fingerprint

        viewModelScope.launch {
            _uiState.value = ExchangeUiState.Connecting("Connecting to exchange server…")

            stopDirectory()
            controlStream.setEntry(null)
            val endpoint = if (trimmedHost.contains(':')) "[$trimmedHost]:$port" else "$trimmedHost:$port"
            val existing = entryStore.getAccountEntry()
            val sameIdentity = existing?.identity == "${ExchangeAccountEntry.normalizeEndpoint(endpoint)}/${ExchangeAccountEntry.normalizeLogin(trimmedLogin)}"
            val createResult = accountManager.createOrReplaceEntry(
                endpoint = endpoint, login = trimmedLogin,
                pin = acceptedFingerprint ?: existing?.takeIf { sameIdentity }?.pin ?: fingerprint, label = labelHint,
            )
            val prepared = when (createResult) {
                is ExchangeAccountManager.CreateEntryResult.Created -> createResult.entry
                is ExchangeAccountManager.CreateEntryResult.Reenrolled -> createResult.entry
            }.copy(state = com.sza.streamsplayer.data.exchange.ExchangeAccountState.NeedsSignIn)
            check(entryStore.saveAccountEntry(prepared))
            val deviceEntry = prepared.toDeviceExchangeAccountEntry()

            val credential = if (isPairingCode) {
                DeviceExchangeCredential.PairingCode(secret)
            } else {
                DeviceExchangeCredential.Password(secret.toCharArray())
            }

            val enrollment = DeviceExchangeEnrollment(
                deviceName = "Android",
                productVersion = com.sza.streamsplayer.BuildConfig.VERSION_NAME,
            )

            Timber.d("S0019: starting enroll host=$trimmedHost:$port login=$trimmedLogin")
            val enrollResult = withContext(Dispatchers.IO) {
                accountClient.enroll(deviceEntry, credential, enrollment)
            }

            // Wipe secret from memory
            (credential as? DeviceExchangeCredential.Password)?.wipe()

            when (enrollResult) {
                is DeviceExchangeEnrollResult.Enrolled -> {
                    Timber.d("S0019: enrolled successfully, recording account entry")
                    val entry = checkNotNull(entryStore.getAccountEntry())
                    controlStream.setEntry(entry.toDeviceExchangeAccountEntry())
                    _uiState.value = ExchangeUiState.AccountReady(
                        entry = entry,
                        // Hidden screen: no load is running, so it must not read as loading.
                        directory = ExchangeDirectoryState(isLoading = directoryVisible),
                    )
                    fetchDirectory(entry)
                }
                is DeviceExchangeEnrollResult.CertificateUntrusted -> {
                    Timber.d("S0019: untrusted server certificate presented=${enrollResult.presented}")
                    _uiState.value = ExchangeUiState.UntrustedCertificate(
                        presentedFingerprint = enrollResult.presented,
                        pendingSignIn = PendingSignIn(
                            host = trimmedHost,
                            port = port,
                            login = trimmedLogin,
                            secret = secret,
                            isPairingCode = isPairingCode,
                            labelHint = labelHint,
                        )
                    )
                }
                is DeviceExchangeEnrollResult.CertificateChanged -> {
                    Timber.d("S0019: server certificate changed pinned=${enrollResult.pinned} presented=${enrollResult.presented}")
                    _uiState.value = ExchangeUiState.CertificateChanged(
                        pinnedFingerprint = enrollResult.pinned,
                        presentedFingerprint = enrollResult.presented,
                        pendingSignIn = PendingSignIn(
                            host = trimmedHost,
                            port = port,
                            login = trimmedLogin,
                            secret = secret,
                            isPairingCode = isPairingCode,
                            labelHint = labelHint,
                        )
                    )
                }
                is DeviceExchangeEnrollResult.Refused -> {
                    val message = DeviceExchangeReasons.message(enrollResult.reason)
                    Timber.d("S0019: enrollment refused: $message")
                    _uiState.value = ExchangeUiState.NoAccount(
                        ExchangeFormState(
                            host = trimmedHost,
                            port = port,
                            login = trimmedLogin,
                            labelHint = labelHint,
                            fingerprint = pinToUse,
                            isPairingCode = isPairingCode,
                            errorText = message,
                            errorAboutServer = true,
                        )
                    )
                }
                is DeviceExchangeEnrollResult.Unreachable -> {
                    val message = enrollResult.cause.message ?: "Server unreachable"
                    Timber.d(enrollResult.cause, "S0019: server unreachable during enroll")
                    _uiState.value = ExchangeUiState.NoAccount(
                        ExchangeFormState(
                            host = trimmedHost,
                            port = port,
                            login = trimmedLogin,
                            labelHint = labelHint,
                            fingerprint = pinToUse,
                            isPairingCode = isPairingCode,
                            errorText = message,
                            errorAboutServer = true,
                        )
                    )
                }
            }
        }
    }

    fun onAcceptUntrustedCertificate(pending: PendingSignIn, presentedFingerprint: String) {
        Timber.d("S0019: user accepted certificate fingerprint $presentedFingerprint")
        onSignIn(
            host = pending.host,
            port = pending.port,
            login = pending.login,
            secret = pending.secret,
            isPairingCode = pending.isPairingCode,
            fingerprint = presentedFingerprint,
            labelHint = pending.labelHint,
            acceptedFingerprint = presentedFingerprint,
        )
    }

    private fun setFormError(msg: String) {
        _uiState.update { current ->
            when (current) {
                is ExchangeUiState.NoAccount -> ExchangeUiState.NoAccount(current.form.copy(errorText = msg, errorAboutServer = false))
                else -> current
            }
        }
    }

    /**
     * Follows the account directory through the shared observation (S0019 Scope 6, S0038): the screen takes a
     * lease while it is visible and renders what the observer holds for this entry.
     */
    fun fetchDirectory(entry: ExchangeAccountEntry) {
        stopDirectory()
        if (!directoryVisible) return
        directoryLease = directory.acquire()
        directoryJob = viewModelScope.launch {
            _uiState.update { current ->
                if (current is ExchangeUiState.AccountReady) current.copy(
                    directory = current.directory.copy(isLoading = true, errorText = null, errorRes = null)) else current
            }
            directory.state.collect { observed ->
                val current = uiState.value as? ExchangeUiState.AccountReady
                if (current?.entry?.entryId == entry.entryId && observed.entryId == entry.entryId) showObserved(entry, observed)
            }
        }
    }

    private fun showObserved(entry: ExchangeAccountEntry, observed: AccountDirectoryState) {
        when (val failure = observed.failure) {
            is AccountDirectoryState.Failure.AuthRequired -> showSignIn(entry, failure.reason)
            is AccountDirectoryState.Failure.Refused -> directoryError(DeviceExchangeReasons.message(failure.reason))
            AccountDirectoryState.Failure.Disconnected ->
                observed.directory?.let { processDirectorySnapshot(entry, it, com.sza.streamsplayer.R.string.exchange_directory_disconnected) }
                    ?: directoryErrorRes(com.sza.streamsplayer.R.string.exchange_directory_disconnected)
            null -> observed.directory?.let { processDirectorySnapshot(entry, it) }
        }
    }

    private fun directoryError(message: String) {
        _uiState.update { current ->
            if (current is ExchangeUiState.AccountReady) current.copy(
                directory = current.directory.copy(isLoading = false, errorText = message)) else current
        }
    }

    private fun directoryErrorRes(message: Int) {
        _uiState.update { current ->
            if (current is ExchangeUiState.AccountReady) current.copy(directory = current.directory.copy(
                isLoading = false, errorText = null, errorRes = message)) else current
        }
    }

    suspend fun needsBroadcastPassword(raw: String) = importBroadcastUseCase.needsPassword(raw)

    private fun processDirectorySnapshot(entry: ExchangeAccountEntry, snapshot: DirectorySnapshot, errorRes: Int? = null) {
        // Scope 6: unprotected sftp-share resources are not shown, with one line saying they are not playable;
        // a protected resource is listed locked (DEVICE-EXCHANGE item AJ)
        val keptIds = (uiState.value as? ExchangeUiState.AccountReady)?.directory?.devices
            .orEmpty().flatMap { it.broadcasts }.filter { it.isKept }.map { it.broadcastId }.toSet()
        val hasSftpShares = ExchangeLockedResources.hasUnlockedShares(snapshot)
        val lockedResources = ExchangeLockedResources.rows(snapshot)

        // Scope 6: Group broadcasts by device
        val devicesMap = snapshot.devices.associateBy { it.deviceId }

        // Filter playable broadcasts (modes audio, video, etc.)
        val broadcastsByDevice = snapshot.broadcasts
            .groupBy { it.deviceId }

        val groups = mutableListOf<DeviceBroadcastGroup>()

        for ((deviceId, broadcasts) in broadcastsByDevice) {
            val deviceRecord = devicesMap[deviceId]
            val deviceName = deviceRecord?.deviceName ?: "Device"
            val presence = deviceRecord?.presence ?: "online"
            val isOnline = deviceRecord?.isOnline ?: true

            val items = broadcasts.map { b ->
                val isPlayable = isModePlayable(b.mode) && b.sealIssue == null
                ExchangeBroadcastItem(
                    broadcastId = b.broadcastId,
                    deviceId = b.deviceId,
                    title = b.title ?: "Live Broadcast",
                    mode = b.mode,
                    descriptorJson = b.descriptorJson,
                    isPlayable = isPlayable,
                    isKept = b.broadcastId in keptIds,
                    sealNotice = b.sealIssue?.message,
                )
            }
            groups.add(
                DeviceBroadcastGroup(
                    deviceId = deviceId,
                    deviceName = deviceName,
                    presence = presence,
                    isOnline = isOnline,
                    broadcasts = items,
                )
            )
        }

        // Also add devices that are online but have no broadcasts yet (empty presence group)
        for (dev in snapshot.devices) {
            if (!broadcastsByDevice.containsKey(dev.deviceId)) {
                groups.add(
                    DeviceBroadcastGroup(
                        deviceId = dev.deviceId,
                        deviceName = dev.deviceName,
                        presence = dev.presence,
                        isOnline = dev.isOnline,
                        broadcasts = emptyList(),
                    )
                )
            }
        }

        Timber.d("S0019: directory loaded devices=${groups.size} hasSftpShares=$hasSftpShares")
        _uiState.update { current ->
            if (current is ExchangeUiState.AccountReady) {
                current.copy(
                    directory = ExchangeDirectoryState(
                        isLoading = false,
                        devices = groups,
                        hasSftpShares = hasSftpShares,
                        lockedResources = lockedResources,
                        errorText = null,
                        errorRes = errorRes,
                    )
                )
            } else current
        }
    }

    private fun isModePlayable(mode: String): Boolean {
        val m = mode.lowercase()
        return com.sza.streamsplayer.data.exchange.ExchangeReceiverDeclaration.modes.any { it.equals(mode, true) } ||
            m in listOf("audio", "video", "av", "rtsp", "radio", "stream")
    }

    /**
     * Scope 6: Keep one broadcast as a channel.
     */
    fun onKeepBroadcast(broadcast: ExchangeBroadcastItem, entryId: String, password: String? = null, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            Timber.d("S0019: keeping broadcast id=${com.sza.streamsplayer.data.exchange.ExchangeTunnelProxy.hashId(broadcast.broadcastId)}")
            val result = importBroadcastUseCase(
                rawPayload = broadcast.descriptorJson,
                exchangeEntryId = entryId,
                exchangeBroadcastId = broadcast.broadcastId,
                password = password,
            )
            if (!importSucceeded(result)) return@launch
            updateBroadcastKept(broadcast.broadcastId, isKept = true)
            onComplete()
        }
    }

    /**
     * Scope 6: Keep everything listed now.
     */
    fun onKeepAllBroadcasts(entryId: String, passwordFor: suspend (ExchangeBroadcastItem) -> String? = { null }, onComplete: (count: Int) -> Unit = {}) {
        viewModelScope.launch {
            val current = _uiState.value as? ExchangeUiState.AccountReady ?: return@launch
            var count = 0
            for (group in current.directory.devices) {
                for (broadcast in group.broadcasts) {
                    if (broadcast.isPlayable && !broadcast.isKept) {
                        val password = if (importBroadcastUseCase.needsPassword(broadcast.descriptorJson)) {
                            passwordFor(broadcast) ?: continue
                        } else null
                        val result = importBroadcastUseCase(
                            rawPayload = broadcast.descriptorJson,
                            exchangeEntryId = entryId,
                            exchangeBroadcastId = broadcast.broadcastId,
                            password = password,
                        )
                        if (!importSucceeded(result)) continue
                        updateBroadcastKept(broadcast.broadcastId, isKept = true)
                        count++
                    }
                }
            }
            Timber.d("S0019: kept all broadcasts count=$count")
            onComplete(count)
        }
    }

    private fun importSucceeded(result: ImportStreamBroadcastUseCase.ImportResult): Boolean = when (result) {
        ImportStreamBroadcastUseCase.ImportResult.Success,
        ImportStreamBroadcastUseCase.ImportResult.Updated,
        ImportStreamBroadcastUseCase.ImportResult.Duplicate -> true
        else -> {
            directoryErrorRes(when (result) {
                ImportStreamBroadcastUseCase.ImportResult.SecretStoreFailure -> com.sza.streamsplayer.R.string.broadcast_password_save_failed
                ImportStreamBroadcastUseCase.ImportResult.UnsupportedAuth -> com.sza.streamsplayer.R.string.broadcast_auth_unsupported
                else -> com.sza.streamsplayer.R.string.import_broadcast_invalid
            })
            false
        }
    }

    private fun updateBroadcastKept(broadcastId: String, isKept: Boolean) {
        _uiState.update { current ->
            if (current is ExchangeUiState.AccountReady) {
                val updatedGroups = current.directory.devices.map { group ->
                    group.copy(
                        broadcasts = group.broadcasts.map { item ->
                            if (item.broadcastId == broadcastId) item.copy(isKept = isKept) else item
                        }
                    )
                }
                current.copy(directory = current.directory.copy(devices = updatedGroups))
            } else current
        }
    }

    /**
     * Scope 7: Remove account entry.
     */
    fun onRemoveAccount(onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            Timber.d("S0019: removing account entry")
            Timber.d("S0027: sign out on this device requested; kept channels remain")
            stopDirectory()
            controlStream.setEntry(null)
            val result = withContext(Dispatchers.IO) { accountManager.removeAccountEntry() }
            _uiState.value = ExchangeUiState.NoAccount(ExchangeFormState())
            Timber.d("S0027: signed out on this device; token forgotten")
            onComplete(result is ExchangeAccountManager.RemoveEntryResult.ServerUnreachable)
        }
    }

    /**
     * Scope 7: Replace PIN.
     */
    fun onReplacePin(newPin: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val currentEntry = entryStore.getAccountEntry() ?: return@launch
            Timber.d("S0019: replacing pinned certificate for entry id=${ExchangeAccountManager.hashId(currentEntry.entryId)}")
            val updated = currentEntry.copy(pin = newPin, pinnedPublicKey = null)
            entryStore.saveAccountEntry(updated)
            _uiState.update { current ->
                if (current is ExchangeUiState.AccountReady) {
                    current.copy(entry = updated)
                } else current
            }
            onComplete()
        }
    }

    /**
     * Scope 7: Change server.
     */
    fun onChangeServer(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            Timber.d("S0019: user selected change server")
            stopDirectory()
            controlStream.setEntry(null)
            withContext(Dispatchers.IO) { accountManager.removeAccountEntry() }
            _uiState.value = ExchangeUiState.NoAccount(ExchangeFormState())
            onComplete()
        }
    }
}
