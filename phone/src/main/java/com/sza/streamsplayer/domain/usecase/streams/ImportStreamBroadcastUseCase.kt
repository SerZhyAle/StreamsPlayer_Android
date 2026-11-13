package com.sza.streamsplayer.domain.usecase.streams

import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorDto
import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorParser
import com.sza.streamsplayer.data.local.db.StreamSourceEntity
import com.sza.streamsplayer.data.repository.StreamSourceRepository
import com.sza.streamsplayer.domain.usecase.streams.AddStreamSourceUseCase.AddResult
import javax.inject.Inject

class ImportStreamBroadcastUseCase @Inject constructor(
    private val parser: BroadcastDescriptorParser,
    private val addStreamSourceUseCase: AddStreamSourceUseCase,
    private val repository: StreamSourceRepository,
    private val descriptorStore: com.sza.streamsplayer.data.broadcast.BroadcastDescriptorStore? = null,
    private val passwordStore: com.sza.streamsplayer.domain.repository.BroadcastPasswordStore? = null,
) {
    suspend operator fun invoke(
        rawPayload: String,
        exchangeEntryId: String? = null,
        exchangeBroadcastId: String? = null,
        password: String? = null,
    ): ImportResult =
        when (val outcome = parser.parseDetailed(rawPayload)) {
            is BroadcastDescriptorParser.ParseOutcome.Valid ->
                resolveImport(outcome.dto, exchangeEntryId, exchangeBroadcastId, password)
            BroadcastDescriptorParser.ParseOutcome.UnsupportedVersion ->
                ImportResult.UnsupportedVersion
            BroadcastDescriptorParser.ParseOutcome.Malformed -> ImportResult.InvalidDescriptor
        }

    suspend fun needsPassword(rawPayload: String): Boolean {
        val dto = parser.parse(rawPayload) ?: return false
        val auth = dto.auth?.takeIf { it.isPasswordV1 } ?: return false
        // DEVICE-EXCHANGE item BF: an own broadcast's opened record carries the account pair, so nothing is asked.
        if (dto.credentials != null) return false
        val known = dto.sourceId?.let { repository.getBySourceDeviceId(it) }
            ?: repository.getByUrl(dto.url.trim())
        val kept = known?.let { passwordStore?.get(it.id) }
        return kept?.user != auth.user || kept.password == null
    }

    private suspend fun resolveImport(
        dto: BroadcastDescriptorDto,
        exchangeEntryId: String?,
        exchangeBroadcastId: String?,
        password: String?,
    ): ImportResult {
        if (dto.auth?.isPasswordV1 == false) return ImportResult.UnsupportedAuth
        val address = runCatching { java.net.URI(dto.url) }.getOrNull()
        val tunnelOnly = address?.scheme == "fmsx" && dto.getEffectiveEndpoints().any {
            it.transport == "TUNNEL" && it.url == dto.url && !it.inner.isNullOrBlank()
        }
        if (address?.host.isNullOrBlank() || (address?.scheme !in setOf("http", "https", "rtsp") && !tunnelOnly)) {
            return ImportResult.InvalidUrl
        }
        val offered = dto.title?.trim()?.takeIf { it.isNotEmpty() }
        val deviceId = dto.sourceId?.trim()?.takeIf { it.isNotEmpty() }
        var addressHeldByAnother = false
        val known = if (exchangeBroadcastId != null && exchangeEntryId != null) {
            repository.getByExchangeBroadcastId(exchangeBroadcastId).firstOrNull { it.exchangeEntryId == exchangeEntryId }
                // S0038: a restarted source has a new broadcast id but keeps its opaque sourceId (LIVE-BROADCAST 3.1,
                // amendment G), so the person's explicit save finds the channel already kept for that source: one row
                // per source. Only a save does this; listing a directory never rebinds anything.
                ?: deviceId?.let { repository.getBySourceDeviceId(it) }
                // An equal address alone never merges two independently identified broadcasts.
                ?: repository.getByUrl(dto.url.trim())?.let { row ->
                    if (conflictsWith(row, deviceId, exchangeEntryId)) {
                        addressHeldByAnother = true
                        null
                    } else {
                        row
                    }
                }
        } else deviceId?.let { repository.getBySourceDeviceId(it) }
        val result = if (known == null) {
            addNew(dto, offered ?: FALLBACK_TITLE, sourceDeviceId = deviceId)
        } else {
            refresh(known, dto.url, offered)
        }
        // The address belongs to a channel of another identity: nothing of it is rewritten, bound or re-keyed.
        if (addressHeldByAnother) return result
        if (result != ImportResult.InvalidUrl && result != ImportResult.InvalidDescriptor) {
            descriptorStore?.saveDescriptor(dto.url, deviceId, dto)
        }
        if (exchangeEntryId != null && exchangeBroadcastId != null) {
            val target = (if (known != null) repository.getById(known.id) else null)
                ?: repository.getByUrl(dto.url.trim())
            if (target != null) {
                repository.bindExchangeBroadcast(target.id, exchangeEntryId, exchangeBroadcastId)
            }
        }
        if (result in listOf(ImportResult.Success, ImportResult.Updated, ImportResult.Duplicate)) {
            val target = known ?: repository.getByUrl(dto.url.trim())
            if (target != null) {
                val auth = dto.auth
                if (auth == null) passwordStore?.remove(target.id)
                else {
                    // Item BF: an own broadcast's record carries the account pair, which answers with no question.
                    val own = dto.credentials
                    val user = own?.user ?: auth.user
                    val kept = passwordStore?.get(target.id)?.takeIf { it.user == user }?.password
                    if (passwordStore?.put(target.id, user, password ?: own?.password ?: kept) != true) {
                        return ImportResult.SecretStoreFailure
                    }
                }
            }
        }
        return result
    }

    /**
     * S2813: one broadcasting device owns one row, so a device whose address moved refreshes that row
     * instead of adding a second one. Without it the entry the user had already pinned and launched
     * from keeps pointing at the previous session's dead address, which is the reported defect.
     *
     * S2868: the same visit may also carry a better name. A row created before the watch named itself
     * through the node still reads its model code, so it is renamed - but only while its title is one
     * this code generated; a title the user typed is never overwritten.
     */
    private suspend fun refresh(known: StreamSourceEntity, scannedUrl: String, offered: String?): ImportResult {
        val trimmed = scannedUrl.trim()
        val moved = trimmed != known.url
        val renamed = offered != null && offered != known.title && isGeneratedTitle(known.title)
        if (moved) repository.refreshSourceAddress(id = known.id, url = trimmed)
        if (renamed) repository.renameSource(id = known.id, title = requireNotNull(offered))
        return if (moved || renamed) ImportResult.Updated else ImportResult.Duplicate
    }

    private fun conflictsWith(row: StreamSourceEntity, sourceId: String?, entryId: String): Boolean =
        (row.sourceDeviceId != null && row.sourceDeviceId != sourceId) ||
            (row.exchangeEntryId != null && row.exchangeEntryId != entryId)

    private fun isGeneratedTitle(title: String): Boolean {
        val stored = title.trim()
        return stored.isEmpty() || stored == FALLBACK_TITLE || MODEL_CODE.matches(stored)
    }

    private suspend fun addNew(dto: BroadcastDescriptorDto, title: String, sourceDeviceId: String?): ImportResult {
        val mediaKind = if (dto.mode == "AUDIO_ONLY") "AUDIO" else "VIDEO"
        if (dto.url.startsWith("fmsx://") && dto.getEffectiveEndpoints().any {
            it.transport == "TUNNEL" && it.url == dto.url && !it.inner.isNullOrBlank()
        }) {
            if (repository.getByUrl(dto.url) != null) return ImportResult.Duplicate
            repository.add(StreamSourceEntity(id = java.util.UUID.randomUUID().toString(), url = dto.url,
                title = title, mediaKind = mediaKind, sourceOrigin = "IMPORTED", sortIndex = 0,
                addedAt = System.currentTimeMillis(), sourceDeviceId = sourceDeviceId))
            return ImportResult.Success
        }
        return when (addStreamSourceUseCase(url = dto.url, title = title, sourceDeviceId = sourceDeviceId,
            mediaKindOverride = mediaKind)) {
            AddResult.Success -> ImportResult.Success
            AddResult.Duplicate -> ImportResult.Duplicate
            AddResult.InvalidUrl -> ImportResult.InvalidUrl
        }
    }

    sealed interface ImportResult {
        data object Success : ImportResult

        /** S2813: a known device came back on a new address; its existing entry now points at it. */
        data object Updated : ImportResult
        data object Duplicate : ImportResult
        data object InvalidUrl : ImportResult
        data object InvalidDescriptor : ImportResult
        data object UnsupportedVersion : ImportResult
        data object UnsupportedAuth : ImportResult
        data object SecretStoreFailure : ImportResult
    }

    private companion object {
        const val FALLBACK_TITLE = "Audio Broadcast"

        // A manufacturer model code as Build.MODEL reports it (SM-L310, SMR870): one token of capitals
        // and digits with at least one digit. A human name has a space or lowercase letters.
        val MODEL_CODE = Regex("^(?=.*\\d)[A-Z][A-Z0-9]*(-[A-Z0-9]+)*$")
    }
}
