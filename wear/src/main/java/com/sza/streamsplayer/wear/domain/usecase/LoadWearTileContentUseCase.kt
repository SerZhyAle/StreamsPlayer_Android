package com.sza.streamsplayer.wear.domain.usecase
import com.sza.streamsplayer.wear.domain.model.*
import com.sza.streamsplayer.wear.domain.repository.WearTileAssignmentRepository
import com.sza.streamsplayer.wear.domain.repository.WearStreamChannelRepository
import javax.inject.Inject
class LoadWearTileContentUseCase @Inject constructor(
    private val tileAssignmentRepository: WearTileAssignmentRepository,
    private val wearStreamChannelRepository: WearStreamChannelRepository
) {
    suspend operator fun invoke(kind: WearTileKind): WearTileContent = loadStreamContent()
    private suspend fun loadStreamContent(): WearTileContent {
        val assignment = tileAssignmentRepository.assignmentFor(WearTileKind.STREAM)
            as? WearTileTargetRef.Stream

        return when {
            assignment == null -> WearTileContent.Unassigned(WearTileKind.STREAM)
            else -> {
                val channels = wearStreamChannelRepository.getAllChannels()
                val channel = channels.firstOrNull { channel ->
                    normalizeWearStreamUrl(channel.url) == assignment.normalizedUrl
                }
                if (channel == null) {
                    WearTileContent.TargetMissing(WearTileKind.STREAM)
                } else {
                    WearTileContent.Assigned(
                        title = channel.name,
                        subtitle = channel.url,
                        launchTarget = WearLaunchTarget.Open(assignment)
                    )
                }
            }
        }
    }
}
