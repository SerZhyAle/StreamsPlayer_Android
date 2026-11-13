package com.sza.streamsplayer.ui.common.help

/**
 * The surface ids the F1 handlers and the `?` actions pass to [HelpOpenManager]. Each is a row of
 * `assets/help/help-map.json` (contract `CONTEXT-HELP` rule 17) and the simple class name of an activity or a
 * dialog class, or the name of a dialog layout; `tools/help/assert-help-map.ps1` enumerates the same names from
 * the manifest, the dialog classes and the layouts, so a surface cannot be added without a map row.
 */
object HelpSurfaceIds {
    const val CATALOG = "MainActivity"
    const val PLAYER = "FullscreenPlayerActivity"
    const val BROADCAST = "BroadcastControlActivity"
    const val SETTINGS = "SettingsActivity"
    const val BROADCAST_SETTINGS = "fragment_settings_broadcast"
    const val QR_SCANNER = "QrScannerActivity"
    const val WIDGET_SETUP = "StreamLaunchWidgetConfigActivity"
    const val FILTER_AND_SORT = "StreamFilterBottomSheet"
    const val CHANNEL_ACTIONS = "StreamChannelActionsSheet"
    const val RECENTLY_PLAYED = "RecentlyPlayedBottomSheet"
    const val SLEEP_TIMER = "SleepTimerBottomSheet"
    const val EDIT_CHANNEL = "dialog_edit_stream"
    const val IMPORT_PLAYLIST = "dialog_import_playlist"
    const val IMPORT_BROADCAST = "dialog_import_broadcast"
    const val PORTAL_TOPICS = "MediaExchangePortalDialog"
    const val EXCHANGE = "ExchangeActivity"
    const val EXCHANGE_PASSWORD = "dialog_exchange_password"
    const val EXCHANGE_DIAGNOSIS = "ExchangeDiagnosisDialog"

    /** The watch's start screen: its Help row asks the phone for this surface's page. */
    const val WATCH_HOME = "wear:WearRoutes.HOME"

    /**
     * The token prefix of a watch request that names a help-map surface and not a file; the watch module keeps the
     * same literal (`WEAR_HELP_TOKEN_PREFIX`). The phone answers only for surfaces of the watch ([WATCH_SURFACE_PREFIX]).
     */
    const val WATCH_TOKEN_PREFIX = "help:"
    const val WATCH_SURFACE_PREFIX = "wear:"
}
