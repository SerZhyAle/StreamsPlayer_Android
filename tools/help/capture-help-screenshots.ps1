#requires -Version 7.0
<#
.SYNOPSIS
    Captures the in-app help screenshots with numbered markers, per locale and theme (CONTEXT-HELP rule 14).

.DESCRIPTION
    Manual tool: drives an emulator with adb, so no gate or closure runs it. It is written for this repository
    (the FastMediaSorter source of the same job depends on that repository's device rig and is not ported).

    For each page of -Help (default: every page that has a recipe below), each locale of -Locales and each theme
    of -Themes:
    - seeds fictional demo channels once through the debug-only receiver HelpDemoSeedReceiver (phone/src/debug),
      so no author's channel, account, server address or key is ever on a picture;
    - sets the app language (`cmd locale set-app-locales`) and the system night mode (`cmd uimode night`), which
      the app follows;
    - puts the status bar into demo mode (fixed clock, full battery, no notifications);
    - reaches the surface by resource id or by label in the captured language, never by coordinate;
    - takes a screenshot and a uiautomator dump, reads the bounds of every element the page source
      docs/help/content/<help-id>.json lists (`viewId` or `descKeys`), and draws a numbered circle at each one;
      the number is the element's position in that list, the same number the generated page prints;
    - crops the navigation bar off, downscales to -Width and writes
      phone/src/main/assets/help/img/<help-id>-<locale>-<theme>.jpg;
    - writes img/capture.json: { versionName, date } of the build captured against, and under `pages` one entry
      per captured page with the layout and menu files its recipe names (`inputs`), the fingerprint of those
      files and of the page's marker list (`sha256`) and of the strings and drawables they show (`visibleSha256`,
      both tools/help/help-shot-inputs.ps1). The gate recomputes the
      fingerprint, so a picture whose screen changed after the capture fails it (rung 2). A run with -Help
      updates the entries of the pages it captured and keeps the others.
    An element absent from a frame is allowed only where the recipe lists it as optional (the radio bar exists
    while a station plays, a live-only control while on air); any other missing element fails the page.

    Device state touched - app language, night mode, animation scales, airplane mode, System UI demo mode - is read
    first and written back at the end, also after a failure. Only an emulator is accepted unless -AllowDevice is
    given: the seed replaces the channel list of the app.

    Pages are not regenerated here: run tools/help/generate-help-pages.ps1 afterwards so each page embeds its image.

.PARAMETER Serial
    adb serial. Must start with `emulator-` unless -AllowDevice is given.

.PARAMETER Help
    Capture only these help ids (comma-separated). Default: every page with a recipe. With -F1Proof or -TreeDir
    the names of the surfaces that inherit a page (channel-actions, recent, sleep-timer, import-playlist,
    import-broadcast) are accepted as well, and the default walks them after the pages.

.PARAMETER Locales
    Locales to capture. Default: en, ru, uk.

.PARAMETER Themes
    Themes to capture. Default: light, dark.

.PARAMETER Width
    Published width of a shot. Default: 540.

.PARAMETER Quality
    JPEG quality, 1-100. Default: 82.

.PARAMETER F1Proof
    Take no screenshot. Reach each surface, send the F1 key and require the help viewer (HelpPageActivity) in the
    foreground afterwards, then press the surface's help button and require the same page: the proof that the
    keyboard entry and the button open the page inside the app. Writes temp/S0035/walk.tsv, one row per surface.

.PARAMETER TreeDir
    Take no screenshot. Dump the control tree of each surface into this folder, one .tsv per surface, for
    tools/help/diff-control-trees.ps1.

.EXAMPLE
    pwsh -NoProfile -File tools/help/capture-help-screenshots.ps1 -Serial emulator-5590

.NOTES
    Exit codes (CHECK-VERDICT): 0 every requested page was captured; 1 a required element was missing or a capture
    step failed (the other pages were still captured); 2 no device, no adb, or an input is missing.
    The last line on stdout is the verdict line.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Serial,
    [string]$Help = '',
    [string[]]$Locales = @('en', 'ru', 'uk'),
    [ValidateSet('light', 'dark')]
    [string[]]$Themes = @('light', 'dark'),
    [int]$Width = 540,
    [ValidateRange(1, 100)]
    [int]$Quality = 82,
    [switch]$AllowDevice,
    [switch]$F1Proof,
    [switch]$SkipSeed,
    [string]$TreeDir = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
# A process started with -File does not split a comma list.
$Locales = @($Locales | ForEach-Object { $_ -split ',' } | Where-Object { $_ })
$Themes = @($Themes | ForEach-Object { $_ -split ',' } | Where-Object { $_ })

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$package = 'com.sza.streamsplayer'
$adb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$sourceRoot = Join-Path $repoRoot 'docs/help/content'
$outRoot = Join-Path $repoRoot 'phone/src/main/assets/help/img'
$resRoot = Join-Path $repoRoot 'phone/src/main/res'
$work = Join-Path $repoRoot 'temp/S0035/capture'
$null = New-Item -ItemType Directory -Force -Path $work, $outRoot
$failures = [System.Collections.Generic.List[string]]::new()
$walk = [System.Collections.Generic.List[string]]::new()

function Get-Opt($object, [string]$name) {
    $p = $object.PSObject.Properties[$name]
    if ($p) { return $p.Value }
}

function Stop-CannotVerify([string]$reason) {
    Write-Host "capture-help-screenshots: COULD NOT VERIFY ($reason)"
    exit 2
}
if (-not (Test-Path -LiteralPath $adb)) { Stop-CannotVerify "adb not found: $adb" }
if (-not $AllowDevice -and $Serial -notmatch '^emulator-') { Stop-CannotVerify "$Serial is not an emulator; the seed replaces the channel list (use -AllowDevice only on a test device)" }

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments)][string[]]$AdbArgs)
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        for ($attempt = 1; $attempt -le 4; $attempt++) {
            $out = & $adb -s $Serial @AdbArgs 2>&1
            $text = ($out | ForEach-Object { "$_" }) -join "`n"
            # Another session may restart the shared adb server; an emulator on a port the server does not scan is re-attached by connect.
            if ($text -notmatch 'device .* not found|device offline|no devices/emulators found|cannot connect to daemon') { break }
            $port = if ($Serial -match '^emulator-(\d+)$') { [int]$Matches[1] + 1 } else { 0 }
            if ($port -gt 0) { $null = & $adb connect "localhost:$port" 2>&1 }
            Start-Sleep -Seconds 3
        }
    } finally { $ErrorActionPreference = $previous }
    return $text
}

if ((Invoke-Adb 'get-state') -notmatch 'device') { Stop-CannotVerify "no device $Serial" }

# ---------- recipes: how each page is reached, which of its elements one frame may lack, and which files draw it ----------
# Inputs are the layout and menu files whose controls are on the frame; they feed the fingerprint in capture.json.
$res = 'phone/src/main/res'
$settingsRows = @("$res/layout/view_settings_toggle_row.xml", "$res/layout/view_settings_dropdown_row.xml", "$res/layout/view_settings_input_row.xml")
$recipes = [ordered]@{
    'catalog'            = @{ Reach = 'catalog'; Optional = @('clear-filters', 'broadcast', 'add-channel', 'recent', 'import-playlist', 'import-broadcast', 'refresh', 'portal', 'exchange', 'settings', 'radio-bar', 'sleep-timer', 'own-status', 'own-refresh')
                              Inputs = @("$res/layout/activity_main.xml", "$res/layout/item_stream_channel.xml", "$res/layout/item_stream_section.xml", "$res/menu/main_menu.xml") }
    'filter-and-sort'    = @{ Reach = 'filter'; Optional = @(); Inputs = @("$res/layout/sheet_stream_filter.xml") }
    'edit-channel'       = @{ Reach = 'add-channel'; Optional = @(); Inputs = @("$res/layout/dialog_edit_stream.xml", "$res/layout/view_dialog_title_help.xml") }
    'player'             = @{ Reach = 'player'; Optional = @('waiting'); Inputs = @("$res/layout/activity_fullscreen_player.xml", "$res/layout/custom_stream_player_controls.xml") }
    'broadcast'          = @{ Reach = 'broadcast'; Optional = @('listeners', 'camera', 'camera-toggle', 'mic-toggle', 'switch-camera', 'screen-off', 'to-watch', 'stop', 'publish-state', 'link', 'copy', 'export', 'send', 'guide')
                              Inputs = @("$res/layout/activity_broadcast_control.xml") }
    'broadcast-settings' = @{ Reach = 'broadcast-settings'; Optional = @('publish', 'publish-camera', 'drive', 'drive-camera')
                              Inputs = @("$res/layout/activity_settings.xml", "$res/layout/page_settings_broadcast.xml", "$res/layout/fragment_settings_broadcast.xml") + $settingsRows }
    # The frame is the General page of a phone-width emulator: the list of pages and the About page are other frames.
    'settings'           = @{ Reach = 'settings'; Optional = @('pages', 'version', 'license', 'privacy', 'docs', 'portal', 'support', 'repository', 'updates', 'diagnostics', 'related', 'resources')
                              Inputs = @("$res/layout/activity_settings.xml", "$res/layout/page_settings_general.xml") + $settingsRows }
    'qr-scanner'         = @{ Reach = 'qr'; Optional = @(); Inputs = @("$res/layout/activity_qr_scanner.xml") }
    'widget-setup'       = @{ Reach = 'widget'; Optional = @(); Inputs = @("$res/layout/activity_stream_launch_widget_config.xml", "$res/layout/item_widget_config_channel.xml") }
    # The demo state has no exchange account, so the frame is the sign-in form; the account card and the broadcast
    # list exist only once signed in, and the lower part of the form may sit below the fold.
    'exchange'           = @{ Reach = 'exchange'
                              Optional = @('drive-sign-out', 'drive-guide', 'destination', 'password', 'sign-in', 'accounts-guide', 'diagnose', 'play-immediately', 'account', 'connect-watch', 'sign-in-again', 'replace-pin', 'change-server', 'sign-out', 'change-password', 'deletion-guide', 'publish', 'broadcasts', 'keep-all', 'broadcast-guide')
                              Inputs = @("$res/layout/activity_exchange.xml", "$res/menu/menu_help.xml") }
}
# Surfaces that inherit a page (help-map.json): no picture of their own, but the walk and the control diff reach them.
$walkOnly = [ordered]@{
    'channel-actions'  = @{ Reach = 'channel-actions' }
    'recent'           = @{ Reach = 'recent' }
    'sleep-timer'      = @{ Reach = 'sleep-timer' }
    'import-playlist'  = @{ Reach = 'import-playlist' }
    'import-broadcast' = @{ Reach = 'import-broadcast' }
}

# ---------- strings of a locale (same reading of strings*.xml as the generator) ----------
$stringCache = @{}
function Get-Strings([string]$locale) {
    if ($stringCache.ContainsKey($locale)) { return $stringCache[$locale] }
    $folder = Join-Path $resRoot ($(if ($locale -eq 'en') { 'values' } else { "values-$locale" }))
    $table = @{}
    foreach ($file in Get-ChildItem -LiteralPath $folder -Filter 'strings*.xml' -File) {
        $doc = [xml](Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8)
        foreach ($node in $doc.resources.string) {
            $v = ([string]$node.InnerText).Trim()
            if ($v.Length -ge 2 -and $v.StartsWith('"') -and $v.EndsWith('"')) { $v = $v.Substring(1, $v.Length - 2) }
            $v = [regex]::Replace($v, '\\(.)', { param($m) $m.Groups[1].Value })
            $table[[string]$node.name] = $v
        }
    }
    $stringCache[$locale] = $table
    return $table
}

# ---------- the UI tree ----------
function Get-Tree {
    param([int]$Attempts = 6)
    for ($i = 1; $i -le $Attempts; $i++) {
        $remote = '/sdcard/help-ui.xml'
        $dump = Invoke-Adb 'shell' 'uiautomator' 'dump' $remote
        if ($dump -match 'dumped to') {
            $local = Join-Path $work 'ui.xml'
            $null = Invoke-Adb 'pull' $remote $local
            $xml = [xml](Get-Content -LiteralPath $local -Raw -Encoding UTF8)
            $nodes = foreach ($n in $xml.SelectNodes('//node')) {
                if ($n.bounds -match '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {
                    [pscustomobject]@{
                        Id = ([string]$n.'resource-id' -replace '^.*:id/', '')
                        Text = [string]$n.text; Desc = [string]$n.'content-desc'; Class = [string]$n.class
                        Clickable = ([string]$n.clickable -eq 'true')
                        Checked = ([string]$n.checked -eq 'true')
                        X1 = [int]$Matches[1]; Y1 = [int]$Matches[2]; X2 = [int]$Matches[3]; Y2 = [int]$Matches[4]
                    }
                }
            }
            return @($nodes | Where-Object { $null -ne $_ })
        }
        Start-Sleep -Milliseconds 1500
    }
    return @()
}

function Find-Node($tree, [string]$viewId, [string[]]$labels, [bool]$overflow = $false) {
    $tree = @($tree | Where-Object { $null -ne $_ })
    if ($tree.Count -eq 0) { return $null }
    if ($viewId) {
        $hit = @($tree | Where-Object { $_.Id -eq $viewId -and ($_.X2 - $_.X1) -gt 0 })
        if ($hit.Count -gt 0) { return $hit[0] }
    }
    if ($labels -and $labels.Count -gt 0) {
        $hit = @($tree | Where-Object { ($labels -contains $_.Desc -or $labels -contains $_.Text) -and ($_.X2 - $_.X1) -gt 0 })
        if ($hit.Count -gt 0) { return $hit[0] }
    }
    if ($overflow) {
        $screenW = ($tree | Measure-Object -Property X2 -Maximum).Maximum
        $hit = @($tree | Where-Object { $_.Class -eq 'android.widget.ImageView' -and $_.Clickable -and $_.X2 -eq $screenW -and $_.Y1 -lt 400 } | Sort-Object Y1)
        if ($hit.Count -gt 0) { return $hit[0] }
    }
    return $null
}

function Wait-Node([string]$viewId, [string[]]$labels, [int]$Seconds = 12, [bool]$overflow = $false) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        $tree = Get-Tree -Attempts 2
        $node = Find-Node $tree $viewId $labels $overflow
        if ($node) { return $node }
        Start-Sleep -Milliseconds 700
    } while ((Get-Date) -lt $deadline)
    return $null
}

function Tap-Node($node) {
    $x = [int](($node.X1 + $node.X2) / 2); $y = [int](($node.Y1 + $node.Y2) / 2)
    $null = Invoke-Adb 'shell' 'input' 'tap' "$x" "$y"
    Start-Sleep -Milliseconds 900
}

function Tap-ByLabel([string[]]$labels, [string]$viewId = '', [int]$Seconds = 10, [bool]$overflow = $false) {
    $node = Wait-Node $viewId $labels $Seconds $overflow
    if (-not $node) { throw "nothing to tap: $viewId / $($labels -join '|')" }
    Tap-Node $node
}

# ---------- reaching a surface ----------
function Start-App {
    $null = Invoke-Adb 'shell' 'am' 'force-stop' $package
    $null = Invoke-Adb 'shell' 'am' 'start' '-n' "$package/.MainActivity"
    $null = Wait-Node 'etSearch' @() 15
}

function Open-OverflowEntry([string]$stringKey, [string]$locale) {
    $label = (Get-Strings $locale)[$stringKey]
    Tap-ByLabel @() '' 10 $true
    Tap-ByLabel @($label) '' 8
}

# Settings opens on its page list at phone width; this opens one page of it. The demo state has no remembered page.
function Open-SettingsPage([string]$pageKey, [string]$locale) {
    $s = Get-Strings $locale
    Start-App; Open-OverflowEntry 'settings' $locale; $null = Wait-Node 'settingsRail' @() 10
    Tap-ByLabel @($s[$pageKey]) '' 8
}

# The rows of the Broadcast page are hidden while live broadcasting is off, and its switch is on the General page; the
# demo state turns it on, checking the row's own checkbox so that an already enabled state is left alone.
function Enable-BroadcastRows([string]$locale) {
    Open-SettingsPage 'settings_nav_general' $locale
    $null = Wait-Node 'rowEnableBroadcasting' @() 8
    $box = Find-Node (Get-Tree) 'str_switch' @()
    if ($box -and -not $box.Checked) { Tap-Node $box }
}

function Reach-Surface([string]$reach, [string]$locale) {
    $s = Get-Strings $locale
    switch ($reach) {
        'catalog' { Start-App }
        'filter' { Start-App; Tap-ByLabel @($s['filter_and_sort']) 'menu_filter'; $null = Wait-Node 'btnApplyFilters' @() 8 }
        'add-channel' { Start-App; Open-OverflowEntry 'add_channel' $locale; $null = Wait-Node 'etStreamUrl' @() 8 }
        'player' {
            Start-App
            Tap-ByLabel @('City Camera') ''
            $null = Wait-Node 'playerView' @() 12
            $tree = Get-Tree
            $pv = Find-Node $tree 'playerView' @()
            if ($pv) { Tap-Node $pv }
            $null = Wait-Node 'btnHelp' @() 8
        }
        'broadcast' { Start-App; Open-OverflowEntry 'broadcast_control_title' $locale; $null = Wait-Node 'btnStartBroadcast' @() 10 }
        'settings' { Open-SettingsPage 'settings_nav_general' $locale; $null = Wait-Node 'rowTheme' @() 8 }
        'broadcast-settings' {
            Enable-BroadcastRows $locale
            Open-SettingsPage 'settings_nav_broadcast' $locale
            $null = Wait-Node 'rowPort' @() 8
            # Scroll until the Drive rows are in view, in steps, so they are not scrolled past.
            for ($step = 0; $step -lt 4; $step++) {
                if (Find-Node (Get-Tree) 'rowPublishToDrive' @()) { break }
                $null = Invoke-Adb 'shell' 'input' 'swipe' '540' '1700' '540' '1100' '400'
                Start-Sleep -Milliseconds 700
            }
        }
        'qr' {
            Start-App; Open-OverflowEntry 'menu_import_broadcast' $locale
            Tap-ByLabel @($s['qr_scanner_title']) 'btnScanBroadcastQr' 8
            $null = Wait-Node 'btnQrClose' @() 12
        }
        'widget' {
            $null = Invoke-Adb 'shell' 'am' 'force-stop' $package
            $null = Invoke-Adb 'shell' 'am' 'start' '-n' "$package/.widget.StreamLaunchWidgetConfigActivity" '-a' 'android.appwidget.action.APPWIDGET_CONFIGURE' '--ei' 'appWidgetId' '7'
            $null = Wait-Node 'rvChannels' @() 12
        }
        'exchange' { Start-App; Open-OverflowEntry 'menu_exchange_server' $locale; $null = Wait-Node 'btnPaste' @() 10 }
        'channel-actions' { Start-App; Tap-ByLabel @() 'btnMoreActions'; $null = Wait-Node 'actionPlay' @() 8 }
        'recent' { Start-App; Open-OverflowEntry 'menu_recently_played' $locale; $null = Wait-Node 'btnClearRecents' @() 8 }
        'sleep-timer' {
            # The timer button is on the radio bar, which shows while a station plays.
            Start-App
            Tap-ByLabel @('Harbour Radio') ''
            Tap-ByLabel @() 'btnSleepTimer' 15
            $null = Wait-Node 'btnPreset15' @() 8
        }
        'import-playlist' { Start-App; Open-OverflowEntry 'menu_import_playlist' $locale; $null = Wait-Node 'etPlaylistUrl' @() 8 }
        'import-broadcast' { Start-App; Open-OverflowEntry 'menu_import_broadcast' $locale; $null = Wait-Node 'etBroadcastPayload' @() 8 }
        default { throw "unknown recipe '$reach'" }
    }
    Start-Sleep -Milliseconds 1200
}

# ---------- device state ----------
function Set-DemoBar([bool]$on) {
    if ($on) {
        $null = Invoke-Adb 'shell' 'settings' 'put' 'global' 'sysui_demo_allowed' '1'
        foreach ($cmd in @(
                @('enter'), @('clock', '-e', 'hhmm', '1000'), @('battery', '-e', 'level', '100', '-e', 'plugged', 'false'),
                @('network', '-e', 'wifi', 'show', '-e', 'level', '4'), @('notifications', '-e', 'visible', 'false'))) {
            $null = Invoke-Adb 'shell' 'am' 'broadcast' '-a' 'com.android.systemui.demo' '-e' 'command' @cmd
        }
    } else {
        $null = Invoke-Adb 'shell' 'am' 'broadcast' '-a' 'com.android.systemui.demo' '-e' 'command' 'exit'
    }
}

$saved = @{
    Night = (Invoke-Adb 'shell' 'cmd' 'uimode' 'night') -replace '^.*:\s*', ''
    Anim = @('window_animation_scale', 'transition_animation_scale', 'animator_duration_scale') |
        ForEach-Object { @{ Key = $_; Value = (Invoke-Adb 'shell' 'settings' 'get' 'global' $_).Trim() } }
    Locales = (Invoke-Adb 'shell' 'cmd' 'locale' 'get-app-locales' $package) -replace '^.*:\s*', ''
}

function Restore-Device {
    Set-DemoBar $false
    foreach ($a in $saved.Anim) { $null = Invoke-Adb 'shell' 'settings' 'put' 'global' $a.Key $(if ($a.Value -and $a.Value -ne 'null') { $a.Value } else { '1' }) }
    $null = Invoke-Adb 'shell' 'cmd' 'uimode' 'night' $(if ($saved.Night -match 'yes') { 'yes' } elseif ($saved.Night -match 'no') { 'no' } else { 'auto' })
    $null = Invoke-Adb 'shell' 'cmd' 'connectivity' 'airplane-mode' 'disable'
    $restore = ($saved.Locales -replace '\s', '')
    if ($restore -and $restore -notmatch 'No|empty|null') { $null = Invoke-Adb 'shell' 'cmd' 'locale' 'set-app-locales' $package '--locales' $restore }
}

# ---------- drawing ----------
function Save-Shot([string]$pngPath, $nodesWithNumber, [string]$target, [int]$cropBottom) {
    $src = [System.Drawing.Image]::FromFile($pngPath)
    try {
        $h = $src.Height - $cropBottom
        $bmp = [System.Drawing.Bitmap]::new($src.Width, $h)
        $g = [System.Drawing.Graphics]::FromImage($bmp)
        $g.SmoothingMode = 'AntiAlias'; $g.TextRenderingHint = 'AntiAliasGridFit'
        $g.DrawImage($src, [System.Drawing.Rectangle]::new(0, 0, $src.Width, $h), [System.Drawing.Rectangle]::new(0, 0, $src.Width, $h), 'Pixel')
        $gold = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 227, 179, 65))
        $ink = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 28, 20, 3))
        $ring = [System.Drawing.Pen]::new([System.Drawing.Color]::White, 4)
        $font = [System.Drawing.Font]::new('Segoe UI', 30, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
        $fmt = [System.Drawing.StringFormat]::new(); $fmt.Alignment = 'Center'; $fmt.LineAlignment = 'Center'
        $placed = [System.Collections.Generic.List[object]]::new()
        $d = 56
        foreach ($item in $nodesWithNumber) {
            $n = $item.Node
            # The marker is centred on the top-start corner of the element, so it covers the element's edge and not its content.
            $cx = [Math]::Min([Math]::Max($n.X1, $d / 2 + 4), $src.Width - $d / 2 - 4)
            $cy = [Math]::Min([Math]::Max($n.Y1, $d / 2 + 4), $h - $d / 2 - 4)
            # keep two markers from landing on one another
            for ($try = 0; $try -lt 12; $try++) {
                $clash = $placed | Where-Object { [Math]::Abs($_.X - $cx) -lt ($d + 4) -and [Math]::Abs($_.Y - $cy) -lt ($d + 4) }
                if (-not $clash) { break }
                $cx += ($d + 6)
                if ($cx -gt $src.Width - $d / 2 - 4) { $cx = $d / 2 + 4; $cy += ($d + 6) }
            }
            $placed.Add([pscustomobject]@{ X = $cx; Y = $cy })
            $rect = [System.Drawing.RectangleF]::new($cx - $d / 2, $cy - $d / 2, $d, $d)
            $g.FillEllipse($gold, $rect); $g.DrawEllipse($ring, $rect)
            $g.DrawString([string]$item.Number, $font, $ink, $rect, $fmt)
        }
        $g.Dispose()
        $scaledH = [int][Math]::Round($h * $Width / $src.Width)
        $out = [System.Drawing.Bitmap]::new($Width, $scaledH)
        $og = [System.Drawing.Graphics]::FromImage($out)
        $og.InterpolationMode = 'HighQualityBicubic'; $og.SmoothingMode = 'HighQuality'
        $og.DrawImage($bmp, 0, 0, $Width, $scaledH)
        $og.Dispose()
        $codec = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object { $_.MimeType -eq 'image/jpeg' }
        $params = [System.Drawing.Imaging.EncoderParameters]::new(1)
        $params.Param[0] = [System.Drawing.Imaging.EncoderParameter]::new([System.Drawing.Imaging.Encoder]::Quality, [long]$Quality)
        $out.Save($target, $codec, $params)
        $out.Dispose(); $bmp.Dispose()
    } finally { $src.Dispose() }
}

# ---------- main ----------
$walking = [bool]($F1Proof -or $TreeDir)
$selected = @(if ($Help) { $Help -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ } } elseif ($walking) { @($recipes.Keys) + @($walkOnly.Keys) } else { $recipes.Keys })
$unknown = @($selected | Where-Object { -not $recipes.Contains($_) -and -not ($walking -and $walkOnly.Contains($_)) })
if ($unknown.Count -gt 0) { Stop-CannotVerify "no recipe for: $($unknown -join ', ')" }
. (Join-Path $PSScriptRoot 'help-shot-inputs.ps1')

try {
    foreach ($a in $saved.Anim) { $null = Invoke-Adb 'shell' 'settings' 'put' 'global' $a.Key '0' }
    $null = Invoke-Adb 'shell' 'cmd' 'connectivity' 'airplane-mode' 'enable'
    Set-DemoBar $true
    $null = Invoke-Adb 'shell' 'pm' 'grant' $package 'android.permission.CAMERA'
    $null = Invoke-Adb 'shell' 'pm' 'grant' $package 'android.permission.POST_NOTIFICATIONS'
    $null = Invoke-Adb 'shell' 'pm' 'grant' $package 'android.permission.RECORD_AUDIO'
    $version = ((Invoke-Adb 'shell' 'dumpsys' 'package' $package) -split "`n" | Where-Object { $_ -match 'versionName=' } | Select-Object -First 1) -replace '^.*versionName=', ''
    if (-not $SkipSeed) {
        $null = Invoke-Adb 'shell' 'am' 'broadcast' '-a' 'com.sza.streamsplayer.debug.SEED_HELP_DEMO' '-n' "$package/com.sza.streamsplayer.debug.HelpDemoSeedReceiver" '--ez' 'wipe' 'true'
    Start-Sleep -Seconds 3
    }

    foreach ($locale in $Locales) {
        $null = Invoke-Adb 'shell' 'cmd' 'locale' 'set-app-locales' $package '--locales' $locale
        foreach ($theme in $Themes) {
            $null = Invoke-Adb 'shell' 'cmd' 'uimode' 'night' $(if ($theme -eq 'dark') { 'yes' } else { 'no' })
            Start-Sleep -Seconds 1
            foreach ($helpId in $selected) {
                $recipe = if ($recipes.Contains($helpId)) { $recipes[$helpId] } else { $walkOnly[$helpId] }
                $label = "$helpId [$locale/$theme]"
                # Another session may restart the shared adb server or leave a screen of its own in front: a surface gets three tries.
                for ($attempt = 1; $attempt -le 3; $attempt++) {
                try {
                    Reach-Surface $recipe.Reach $locale
                    $strings = Get-Strings $locale
                    $tree = Get-Tree
                    if ($tree.Count -eq 0) { throw 'no UI tree' }

                    if ($TreeDir) {
                        $null = New-Item -ItemType Directory -Force -Path $TreeDir
                        $rows = $tree | ForEach-Object { "{0}`t{1}`t{2}`t{3}" -f $_.Id, $_.Class, $_.Desc, $_.Text }
                        $rows | Set-Content -LiteralPath (Join-Path $TreeDir "$helpId-$locale-$theme.tsv") -Encoding UTF8
                        Write-Host "tree $label : $(@($tree).Count) nodes"
                        break
                    }

                    if ($F1Proof) {
                        # F1 and the help button must open the same page (CONTEXT-HELP rule 3): the texts of the
                        # viewer after each are compared.
                        $viewerTexts = {
                            Start-Sleep -Seconds 2
                            for ($wait = 0; $wait -lt 8; $wait++) {
                                $top = Invoke-Adb 'shell' 'dumpsys' 'activity' 'activities'
                                # API 29+ names the front activity topResumedActivity; older releases name it mResumedActivity.
                                if ($top -notmatch '(topResumedActivity|mResumedActivity).*HelpPageActivity') { return $null }
                                # The title the viewer shows names the page; it replaces the plain word once the page has loaded.
                                $viewerTree = Get-Tree
                                $title = [string](@($viewerTree | Where-Object { $_.Text -like 'Help - *' -or $_.Text -like 'Справка - *' -or $_.Text -like 'Довідка - *' } | Select-Object -First 1 -ExpandProperty Text))
                                if ($title) { return $title }
                                Start-Sleep -Seconds 1
                            }
                            return $null
                        }
                        $null = Invoke-Adb 'shell' 'input' 'keyevent' 'KEYCODE_F1'
                        $viaKey = & $viewerTexts
                        if (-not $viaKey) { $walk.Add("$helpId`t$locale`t$theme`tF1`tFAILED"); throw 'F1 did not open the help viewer' }
                        $null = Invoke-Adb 'shell' 'input' 'keyevent' 'KEYCODE_BACK'
                        Start-Sleep -Seconds 2
                        # The player hides its controls after a few seconds: a tap on the picture shows them again.
                        if ($helpId -eq 'player') {
                            # The controls hide 4 s after a tap: tap, dump once and press at once; a tap that hid them is repeated.
                            for ($show = 0; $show -lt 3; $show++) {
                                $pv = Find-Node (Get-Tree) 'playerView' @()
                                if ($pv) { Tap-Node $pv }
                                $tree = Get-Tree
                                if (Find-Node $tree 'btnHelp' @()) { break }
                            }
                        } else {
                            $tree = Get-Tree
                        }
                        $button = $null
                        foreach ($candidate in @('menu_help', 'btnHelp', 'btnQrHelp', 'btnFilterHelp', 'btnDialogHelp', 'btnChannelActionsHelp', 'btnRecentHelp', 'btnSleepTimerHelp')) {
                            $button = Find-Node $tree $candidate @()
                            if ($button) { break }
                        }
                        if (-not $button) { $walk.Add("$helpId`t$locale`t$theme`tbutton`tFAILED"); throw 'no help button on the surface' }
                        Tap-Node $button
                        $viaButton = & $viewerTexts
                        $same = $viaButton -and ($viaButton -eq $viaKey)
                        $walk.Add("$helpId`t$locale`t$theme`tF1+button`t$(if ($same) { 'same-page' } else { 'FAILED' })`t$viaKey`t$viaButton")
                        if (-not $same) { throw "F1 and the help button did not open the same page (key: '$viaKey', button: '$viaButton' at $($button.Id))" }
                        $null = Invoke-Adb 'shell' 'input' 'keyevent' 'KEYCODE_BACK'
                        break
                    }

                    $source = Get-Content -LiteralPath (Join-Path $sourceRoot "$helpId.json") -Raw -Encoding UTF8 | ConvertFrom-Json
                    $found = [System.Collections.Generic.List[object]]::new()
                    $number = 0
                    foreach ($element in @($source.elements)) {
                        $number++
                        $labels = @()
                        foreach ($k in @(Get-Opt $element 'descKeys')) { if ($k -and $strings.ContainsKey($k)) { $labels += $strings[$k] } }
                        $viewId = [string](Get-Opt $element 'viewId')
                        $overflow = (@(Get-Opt $element 'descKeys') -contains 'more_options')
                        if (-not $viewId -and $labels.Count -eq 0 -and -not $overflow) { continue }
                        $node = Find-Node $tree $viewId $labels $overflow
                        if ($node) { $found.Add([pscustomobject]@{ Number = $number; Node = $node; Id = [string]$element.id }) }
                        elseif ($recipe.Optional -notcontains [string]$element.id) { throw "element '$($element.id)' ($viewId $($labels -join '|')) is missing from the frame" }
                    }

                    $png = Join-Path $work "$helpId-$locale-$theme.png"
                    $null = Invoke-Adb 'shell' 'screencap' '-p' '/sdcard/help-shot.png'
                    $null = Invoke-Adb 'pull' '/sdcard/help-shot.png' $png
                    $nav = $tree | Where-Object { $_.Id -eq 'navigationBarBackground' } | Select-Object -First 1
                    $cropBottom = 0
                    if ($nav) {
                        $img = [System.Drawing.Image]::FromFile($png); $full = $img.Height; $img.Dispose()
                        $cropBottom = $full - $nav.Y1
                    }
                    $target = Join-Path $outRoot "$helpId-$locale-$theme.jpg"
                    Save-Shot $png $found $target $cropBottom
                    Write-Host ("captured {0}: {1} markers, {2:N0} KB" -f $label, $found.Count, ((Get-Item $target).Length / 1KB))
                    break
                } catch {
                    if ($attempt -lt 3) {
                        Write-Host "retry $label ($attempt): $($_.Exception.Message)"
                        $null = Invoke-Adb 'shell' 'am' 'force-stop' $package
                        Start-Sleep -Seconds 3
                        continue
                    }
                    $failures.Add("$label : $($_.Exception.Message)")
                    Write-Host "FAIL $label : $($_.Exception.Message)"
                    Write-Host (($_.ScriptStackTrace -split "`n" | Select-Object -First 3 | ForEach-Object { "    at $_" }) -join "`n")
                }
                }
            }
        }
    }

    if (-not $walking -and $failures.Count -eq 0) {
        $capturePath = Join-Path $outRoot 'capture.json'
        $date = Get-Date -Format 'yyyy-MM-dd'
        $pages = [ordered]@{}
        if (Test-Path -LiteralPath $capturePath) {
            $previous = Get-Content -LiteralPath $capturePath -Raw -Encoding UTF8 | ConvertFrom-Json
            $prop = $previous.PSObject.Properties['pages']
            if ($prop) { foreach ($p in $prop.Value.PSObject.Properties) { $pages[$p.Name] = $p.Value } }
        }
        foreach ($helpId in $selected) {
            $inputs = @($recipes[$helpId].Inputs)
            $print = Get-HelpShotFingerprint -Root $repoRoot -Help $helpId -Inputs $inputs
            if (-not $print.Sha256) { throw "fingerprint of $helpId names missing files: $($print.Missing -join ', ')" }
            $visible = Get-HelpShotVisible -Root $repoRoot -Help $helpId -Inputs $inputs
            $pages[$helpId] = [ordered]@{ versionName = $version; date = $date; inputs = $inputs; sha256 = $print.Sha256; visibleSha256 = $visible }
        }
        $sorted = [ordered]@{}
        foreach ($k in @($pages.Keys | Sort-Object)) { $sorted[$k] = $pages[$k] }
        [ordered]@{ versionName = $version; date = $date; pages = $sorted } | ConvertTo-Json -Depth 5 |
            Set-Content -LiteralPath $capturePath -Encoding UTF8
    }
    if ($F1Proof) { $walk | Set-Content -LiteralPath (Join-Path $repoRoot 'temp/S0035/walk.tsv') -Encoding UTF8 }
} finally {
    Restore-Device
}

if ($failures.Count -gt 0) {
    Write-Host "capture-help-screenshots: FAIL ($($failures.Count) capture(s) failed)"
    exit 1
}
Write-Host "capture-help-screenshots: PASS ($($selected.Count) page(s), $($Locales.Count) locale(s), $($Themes.Count) theme(s))"
exit 0
