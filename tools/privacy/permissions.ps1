#requires -Version 7.0
<#
.SYNOPSIS
    S0033: the permissions table of the privacy policy, generated from the merged store release manifests.

.DESCRIPTION
    Reads the merged manifest of the store release of `phone` and `wear` and of the release of `watchface`,
    takes every <uses-permission> that is not the application's own signature permission, joins the sentences of
    docs/privacy/permission-purposes.json and renders the table between the permissions:begin and permissions:end
    markers of docs/PRIVACY_POLICY.md, docs/PRIVACY_POLICY-ru.md and docs/PRIVACY_POLICY-uk.md.

    Check (default)  exits 0 when the manifests, the sentences and the three tables agree; 1 on any difference,
                     each named; 2 when a manifest cannot be read or is older than its sources.
    Generate         rewrites the three tables from the manifests, then checks.
    -Build           at release, first runs the three manifest-merging Gradle tasks under the Build.Main lock,
                     so the manifests read are the ones of the tree as it is now.

    The tool never edits a manifest: a permission is added or removed in the manifest, then Generate runs.

    pwsh -NoProfile -File tools/privacy/permissions.ps1 -Build
    pwsh -NoProfile -File tools/privacy/permissions.ps1 -Mode Generate -Build
#>
[CmdletBinding()]
param(
    [ValidateSet('Check', 'Generate')][string]$Mode = 'Check',
    [switch]$Build
)
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$utf8 = [Text.UTF8Encoding]::new($false)

$modules = @(
    [pscustomobject]@{ Key = 'phone'; Label = 'phone'; Task = ':phone:processStoreReleaseManifest'
        Manifest = 'phone/build/intermediates/merged_manifests/storeRelease/processStoreReleaseManifest/AndroidManifest.xml' }
    [pscustomobject]@{ Key = 'wear'; Label = 'watch'; Task = ':wear:processStoreReleaseManifest'
        Manifest = 'wear/build/intermediates/merged_manifests/storeRelease/processStoreReleaseManifest/AndroidManifest.xml' }
    [pscustomobject]@{ Key = 'watchface'; Label = 'watchface'; Task = ':watchface:processReleaseManifest'
        Manifest = 'watchface/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml' }
)
$policies = @(
    [pscustomobject]@{ Locale = 'en'; Path = 'docs/PRIVACY_POLICY.md' }
    [pscustomobject]@{ Locale = 'ru'; Path = 'docs/PRIVACY_POLICY-ru.md' }
    [pscustomobject]@{ Locale = 'uk'; Path = 'docs/PRIVACY_POLICY-uk.md' }
)
$purposesPath = Join-Path $repoRoot 'docs/privacy/permission-purposes.json'

function Read-Text([string]$path) { ([IO.File]::ReadAllText($path, $utf8)) -replace "`r`n", "`n" }

function Invoke-ManifestBuild {
    # The build lock is owned by this process; it is queued for like any other Gradle caller.
    $cache = Join-Path ($env:USERPROFILE ?? $env:HOME) '.claude/plugins/cache/sza-unified-rules/sza'
    $lockLib = @($env:SZA_HARNESS_ROOT) + @(Get-ChildItem -LiteralPath $cache -Directory -ErrorAction SilentlyContinue |
            Sort-Object { [version]$_.Name } -Descending | ForEach-Object { Join-Path $_.FullName 'tools/harness' }) |
        Where-Object { $_ } | ForEach-Object { Join-Path $_ 'locks/agent-lock.ps1' } |
        Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
    if (-not $lockLib) { throw 'permissions: the harness lock library (sza plugin) was not found.' }
    $env:SZA_PROJECT_ROOT = $repoRoot
    . $lockLib
    Enter-BuildLockOrExit -Reason 'S0033 permissions.ps1: merge the store release manifests' -Name 'Build.Main'
    $gradle = Join-Path $repoRoot ($IsWindows ? 'gradlew.bat' : 'gradlew')
    Push-Location $repoRoot
    try {
        & $gradle @($modules.Task) '--console=plain' '-q' | Out-Host
        if ($LASTEXITCODE -ne 0) { throw "permissions: Gradle failed (exit $LASTEXITCODE)." }
    }
    finally { Pop-Location }
}

function Read-Permissions($module) {
    $path = Join-Path $repoRoot $module.Manifest
    if (-not (Test-Path -LiteralPath $path)) {
        Write-Host "permissions: merged manifest missing: $($module.Manifest) - run with -Build at release."
        exit 2
    }
    # A merged manifest older than the files that feed it describes an earlier tree.
    $manifestTime = (Get-Item -LiteralPath $path).LastWriteTimeUtc
    $sources = @(Get-ChildItem -LiteralPath (Join-Path $repoRoot "$($module.Key)/src") -Recurse -Filter AndroidManifest.xml -File) +
        @(Get-Item -LiteralPath (Join-Path $repoRoot "$($module.Key)/build.gradle.kts")) +
        @(Get-Item -LiteralPath (Join-Path $repoRoot 'gradle/libs.versions.toml'))
    $newer = $sources | Where-Object { $_.LastWriteTimeUtc -gt $manifestTime } | Select-Object -First 1
    if ($newer) {
        Write-Host "permissions: $($module.Manifest) is older than $($newer.FullName.Substring($repoRoot.Length + 1)) - run with -Build at release."
        exit 2
    }
    $xml = [xml](Read-Text $path)
    $appId = $xml.manifest.GetAttribute('package')
    $names = foreach ($node in $xml.manifest.ChildNodes) {
        if ($node.NodeType -eq 'Element' -and $node.LocalName -in @('uses-permission', 'uses-permission-sdk-23')) {
            $node.GetAttribute('name', 'http://schemas.android.com/apk/res/android')
        }
    }
    # <applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION and the like belong to the app, not to the user.
    @($names | Where-Object { $_ -and -not ($appId -and $_.StartsWith("$appId.")) } | Sort-Object -Unique -CaseSensitive)
}

if ($Build) { Invoke-ManifestBuild }

$declared = @{}
foreach ($module in $modules) {
    foreach ($name in Read-Permissions $module) {
        if (-not $declared.ContainsKey($name)) { $declared[$name] = [Collections.Generic.List[string]]::new() }
        $declared[$name].Add($module.Label)
    }
}

$data = (Read-Text $purposesPath) | ConvertFrom-Json -AsHashtable
$problems = [Collections.Generic.List[string]]::new()
foreach ($name in $declared.Keys) {
    foreach ($locale in 'en', 'ru', 'uk') {
        if (-not ($data.purposes.ContainsKey($name) -and $data.purposes[$name][$locale])) {
            $problems.Add("no $locale sentence in docs/privacy/permission-purposes.json for $name (declared by $($declared[$name] -join ', '))")
        }
    }
}
foreach ($name in $data.purposes.Keys) {
    if (-not $declared.ContainsKey($name)) {
        $problems.Add("docs/privacy/permission-purposes.json describes $name, which no merged store release manifest declares")
    }
}

function New-Block([string]$locale) {
    $label = $data.labels[$locale]
    $lines = [Collections.Generic.List[string]]::new()
    $lines.Add("| $($label.permission) | $($label.where) | $($label.purpose) |")
    $lines.Add('|---|---|---|')
    $order = @('phone', 'watch', 'watchface')
    foreach ($name in ($declared.Keys | Sort-Object -CaseSensitive)) {
        $where = @($order | Where-Object { $declared[$name] -contains $_ } | ForEach-Object { $label[$_] }) -join ', '
        $purpose = if ($data.purposes.ContainsKey($name)) { $data.purposes[$name][$locale] } else { '' }
        $lines.Add("| ``$name`` | $where | $purpose |")
    }
    $watchfaceHas = $declared.Values | Where-Object { $_ -contains 'watchface' }
    if (-not $watchfaceHas) { $lines.Add(''); $lines.Add($label.noWatchface) }
    $lines -join "`n"
}

$beginPattern = '(?m)^<!-- permissions:begin[^\n]*-->\n'
$endPattern = '(?m)^<!-- permissions:end -->$'
foreach ($policy in $policies) {
    $path = Join-Path $repoRoot $policy.Path
    $text = Read-Text $path
    $begin = [regex]::Match($text, $beginPattern)
    $end = [regex]::Match($text, $endPattern)
    if (-not $begin.Success -or -not $end.Success -or $end.Index -lt $begin.Index) {
        $problems.Add("$($policy.Path) has no permissions:begin / permissions:end markers")
        continue
    }
    $expected = $begin.Value + (New-Block $policy.Locale) + "`n"
    $current = $text.Substring($begin.Index, $end.Index - $begin.Index)
    if ($current -ceq $expected) { continue }
    if ($Mode -eq 'Generate' -and $problems.Count -eq 0) {
        $updated = $text.Substring(0, $begin.Index) + $expected + $text.Substring($end.Index)
        [IO.File]::WriteAllText($path, $updated, $utf8)
        Write-Host "permissions: rewrote the table of $($policy.Path)"
    }
    else {
        $problems.Add("the permissions table of $($policy.Path) differs from the merged manifests - run with -Mode Generate")
    }
}

if ($problems.Count) {
    $problems | ForEach-Object { Write-Host "FAIL permissions: $_" }
    exit 1
}
Write-Host "OK permissions: $($declared.Count) permissions in $($modules.Count) merged manifests, 3 policy tables agree."
exit 0
