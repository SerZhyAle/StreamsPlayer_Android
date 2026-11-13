package com.sza.streamsplayer.wear

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.sza.streamsplayer.wear.data.wear.WatchStreamOpenEvents
import com.sza.streamsplayer.wear.domain.playback.WearBackgroundSessionState
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.navigation.*
import androidx.wear.compose.material.Text
import com.google.android.gms.wearable.Wearable
import com.sza.streamsplayer.wear.data.wear.WearDataLayerPaths
import com.sza.streamsplayer.wear.domain.model.*
import com.sza.streamsplayer.wear.domain.repository.*
import com.sza.streamsplayer.wear.domain.usecase.*
import com.sza.streamsplayer.wear.ui.broadcast.*
import com.sza.streamsplayer.wear.ui.common.*
import com.sza.streamsplayer.wear.ui.navigation.WearRoutes
import com.sza.streamsplayer.wear.ui.pictures.WearChannelPicturesScreen
import com.sza.streamsplayer.wear.ui.player.audio.AudioPlayerScreen
import com.sza.streamsplayer.wear.ui.player.video.VideoPlayerScreen
import com.sza.streamsplayer.wear.ui.streams.StreamsScreen
import com.sza.streamsplayer.wear.ui.theme.WearAppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var background: WearBackgroundSessionState
    @Inject lateinit var channels: WearStreamChannelRepository
    @Inject lateinit var preparePlayback: PrepareWearStreamPlaybackUseCase
    @Inject lateinit var assignments: WearTileAssignmentRepository
    @Inject lateinit var refreshTile: RequestWearTileRefreshUseCase
    @Inject lateinit var phone: WearOpenOnPhoneRepository
    @Inject lateinit var sendPins: SendStreamPinsDeltaUseCase
    @Inject lateinit var exchangePresence: com.sza.streamsplayer.wear.data.exchange.WearExchangePresence
    private val screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: Intent) {
            exchangePresence.interactive(intent.action == Intent.ACTION_SCREEN_ON)
        }
    }
    private var pendingLaunch by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("S0007: Wear entry opened")
        pendingLaunch = intent
        setContent {
            WearAppTheme {
                val nav = rememberSwipeDismissableNavController()
                var phoneResult by remember { mutableStateOf<Int?>(null) }
                val openPhone: (String, String, String) -> Unit = { url, name, kind ->
                    lifecycleScope.launch {
                        val result = phone.requestOpen(WearOpenOnPhoneRequest(url, name, kind))
                        phoneResult = when (result) {
                            WearOpenOnPhoneOutcome.SHOWN -> if (kind == WEAR_HELP_MEDIA_KIND) R.string.sp_help_shown else R.string.sp_phone_shown
                            WearOpenOnPhoneOutcome.NOTIFIED -> R.string.sp_phone_notified
                            WearOpenOnPhoneOutcome.REFUSED_NO_NOTIFICATION -> R.string.sp_phone_notifications_off
                            WearOpenOnPhoneOutcome.NOT_FOUND -> if (kind == WEAR_HELP_MEDIA_KIND) R.string.sp_help_not_found else R.string.sp_phone_not_found
                            null -> R.string.sp_phone_unreachable
                        }
                    }
                }
                LaunchedEffect(nav) {
                    nav.currentBackStackEntryFlow.collect { entry ->
                        exchangePresence.screen(entry.destination.route in setOf(WearRoutes.BROADCAST,
                            WearRoutes.BROADCAST_QR, WearRoutes.AUDIO_PLAYER_PATTERN))
                    }
                }
                LaunchedEffect(nav) {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        WatchStreamOpenEvents.requestFlow.collect { channel ->
                            val prepared = preparePlayback(channel)
                            nav.navigate(if (prepared.isVideo) WearRoutes.videoPlayer(prepared.fileId) else WearRoutes.audioPlayer(prepared.fileId))
                            WatchStreamOpenEvents.openedFlow.emit(channel.url)
                        }
                    }
                }
                LaunchedEffect(pendingLaunch) {
                    val launch = pendingLaunch ?: return@LaunchedEffect
                    pendingLaunch = null
                    when (val target = readWearLaunchTarget(launch)) {
                        is WearLaunchTarget.Open -> {
                            val stream = target.ref as? WearTileTargetRef.Stream
                            val channel = channels.getAllChannels().firstOrNull { normalizeWearStreamUrl(it.url) == stream?.normalizedUrl }
                            if (channel != null) {
                                val prepared = preparePlayback(channel)
                                nav.navigate(if (prepared.isVideo) WearRoutes.videoPlayer(prepared.fileId) else WearRoutes.audioPlayer(prepared.fileId))
                            } else nav.navigate(WearRoutes.STREAMS)
                        }
                        is WearLaunchTarget.Pick -> nav.navigate("tile_picker")
                        is WearLaunchTarget.Destination -> nav.navigate(if (target.id == WearDestinationId.BROADCAST) WearRoutes.BROADCAST else WearRoutes.STREAMS)
                        else -> Unit
                    }
                }
                SwipeDismissableNavHost(navController = nav, startDestination = WearRoutes.HOME) {
                    composable(WearRoutes.HOME) {
                        val session by background.session.collectAsState()
                        WearScreenScaffold {
                            WearListColumn(state = rememberWearListState(), centered = true) {
                                item { Text(stringResource(R.string.app_name)) }
                                session?.let { active -> item { StandardWearChip(stringResource(R.string.wear_now_playing_row_subtitle), { nav.navigate(WearRoutes.audioPlayer(active.fileId)) }) } }
                                item { StandardWearChip(stringResource(R.string.sp_channels), { nav.navigate(WearRoutes.STREAMS) }) }
                                item { StandardWearChip(stringResource(R.string.sp_broadcast), { nav.navigate(WearRoutes.BROADCAST) }) }
                                item { StandardWearChip(stringResource(R.string.sp_assign_tile), { nav.navigate("tile_picker") }) }
                                item { StandardWearChip(stringResource(R.string.sp_channel_pictures), { nav.navigate(WearRoutes.CHANNEL_PICTURES) }) }
                                item { StandardWearChip(stringResource(R.string.sp_help), { openPhone(WEAR_HELP_TOKEN_HOME, "", WEAR_HELP_MEDIA_KIND) }) }
                            }
                        }
                    }
                    composable(WearRoutes.STREAMS) { StreamsScreen(navController = nav) }
                    composable(WearRoutes.AUDIO_PLAYER_PATTERN, arguments = listOf(navArgument("fileId") { type = NavType.LongType })) {
                        AudioPlayerScreen({ nav.popBackStack() }, openPhone)
                    }
                    composable(WearRoutes.VIDEO_PLAYER_PATTERN, arguments = listOf(navArgument("fileId") { type = NavType.LongType })) {
                        VideoPlayerScreen({ nav.popBackStack() }, openPhone)
                    }
                    composable(WearRoutes.BROADCAST) { WearBroadcastScreen(onShowQr = { nav.navigate(WearRoutes.BROADCAST_QR) }, onHelpOnPhone = { openPhone(WEAR_PORTAL_TOKEN_WATCH_EXCHANGE, "", WEAR_PORTAL_MEDIA_KIND) }) }
                    composable(WearRoutes.BROADCAST_QR) { WearBroadcastQrScreen() }
                    composable(WearRoutes.CHANNEL_PICTURES) { WearChannelPicturesScreen() }
                    composable("tile_picker") {
                        var items by remember { mutableStateOf<List<WearStreamChannel>>(emptyList()) }
                        LaunchedEffect(Unit) { items = channels.getAllChannels() }
                        WearScreenScaffold {
                            WearListColumn(state = rememberWearListState()) {
                                item { Text(stringResource(R.string.sp_assign_tile)) }
                                items(items.size) { index ->
                                    val channel = items[index]
                                    StandardWearChip(channel.name, {
                                        lifecycleScope.launch {
                                            assignments.assign(WearTileKind.STREAM, streamTargetRef(channel.url))
                                            refreshTile(WearTileKind.STREAM)
                                            nav.popBackStack()
                                        }
                                    })
                                }
                            }
                        }
                    }
                }
                phoneResult?.let { message -> StandardWearAlertDialog(
                    title = stringResource(R.string.sp_open_on_phone), message = stringResource(message),
                    show = true, onConfirm = { phoneResult = null }, cancelLabel = null,
                    onDismissRequest = { phoneResult = null }) }
            }
        }
        lifecycleScope.launch {
            sendPins()
            runCatching {
                Wearable.getNodeClient(this@MainActivity).connectedNodes.await().forEach { node ->
                    Wearable.getMessageClient(this@MainActivity).sendMessage(node.id, WearDataLayerPaths.SYNC_REQUEST, byteArrayOf()).await()
                }
            }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; Timber.i(it, "Phone is unavailable for pin synchronization") }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingLaunch = intent
    }

    override fun onStart() {
        super.onStart()
        androidx.core.content.ContextCompat.registerReceiver(this, screenReceiver,
            android.content.IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_SCREEN_OFF) },
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onResume() {
        super.onResume()
        exchangePresence.interactive((getSystemService(POWER_SERVICE) as android.os.PowerManager).isInteractive)
        exchangePresence.resumed(true)
    }

    override fun onPause() {
        exchangePresence.resumed(false)
        super.onPause()
    }

    override fun onStop() {
        unregisterReceiver(screenReceiver)
        super.onStop()
    }
}

