#requires -Version 7.0
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('release', 'debug')][string]$BuildType,
    [ValidateSet('store', 'noLegal')][string]$Flavor = 'store',
    [ValidateSet('all', 'phone', 'wear', 'watchface')][string]$Module = 'all'
)
$ErrorActionPreference = 'Stop'
$BuildType = $BuildType.ToLowerInvariant()
$Flavor = if ($Flavor -eq 'noLegal') { 'noLegal' } else { 'store' }
$Module = $Module.ToLowerInvariant()
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$outDir = Join-Path $repoRoot 'temp/ci'
$packageDir = Join-Path $outDir 'packages'
$credentialDir = $null
$previousSigningPath = $env:STREAMSPLAYER_KEYSTORE_PROPERTIES
$verdict = [ordered]@{
    verdict = 'FAIL'; buildType = $BuildType; flavor = $Flavor
    commit = $env:GITHUB_SHA; versionName = $null; artifacts = @(); reason = 'Build did not complete.'
}

function Invoke-Checked {
    param([string]$Executable, [string[]]$Arguments)
    & $Executable @Arguments | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "$Executable failed (exit $LASTEXITCODE)." }
}

function ConvertTo-JavaProperty {
    param([string]$Value)
    # java.util.Properties consumes escapes and leading spaces, and defaults to Latin-1.
    $escaped = $Value.Replace('\', '\\').Replace("`r", '\r').Replace("`n", '\n').Replace("`t", '\t').Replace(' ', '\ ')
    return [regex]::Replace($escaped, '[^\x20-\x7e]', { param($m) '\u{0:x4}' -f [int][char]$m.Value })
}

function Get-TagInstant {
    param([string]$Tag)
    $match = [regex]::Match($Tag, '^v(\d)\.(\d{2})\.(\d{4})\.(\d{3})$')
    if (-not $match.Success) { throw 'Release tag must encode versionName as vY.YM.MDDH.Hmm.' }
    $yearMonth = $match.Groups[2].Value
    $monthDayHour = $match.Groups[3].Value
    $raw = $match.Groups[1].Value + $yearMonth[0] + $yearMonth[1] + $monthDayHour[0] +
        $monthDayHour.Substring(1, 2) + $monthDayHour[3] + $match.Groups[4].Value
    [void][datetime]::ParseExact($raw, 'yyMMddHHmm', [Globalization.CultureInfo]::InvariantCulture)
    return $raw
}

Push-Location $repoRoot
try {
    New-Item -ItemType Directory -Path $packageDir -Force | Out-Null
    # Never allow an old successful package set to survive a failed rerun.
    if (@(Get-ChildItem -LiteralPath $packageDir -File).Count) {
        throw 'Package output is not empty. Use a fresh checkout for each CI build.'
    }
    $gradle = if ($IsWindows) { Join-Path $repoRoot 'gradlew.bat' } else { Join-Path $repoRoot 'gradlew' }
    $modules = if ($Module -eq 'all') { @('phone', 'wear', 'watchface') } else { @($Module) }
    $variantFlavor = if ($Flavor -eq 'store') { 'Store' } else { 'NoLegal' }
    $typeSuffix = if ($BuildType -eq 'release') { 'Release' } else { 'Debug' }
    # One Europe/Malta clock reading, pinned separately for each package family's band.
    $zone = [TimeZoneInfo]::FindSystemTimeZoneById('Europe/Malta')
    $instant = [TimeZoneInfo]::ConvertTimeFromUtc([datetime]::UtcNow, $zone).ToString('yyMMddHHmm')
    if ($BuildType -eq 'release' -and $env:GITHUB_REF_TYPE -eq 'tag') {
        $instant = Get-TagInstant $env:GITHUB_REF_NAME
    }
    $versionName = '{0}.{1}{2}.{3}{4}{5}{6}.{7}{8}{9}' -f @($instant.ToCharArray())
    $verdict.versionName = $versionName
    $verdict.instant = $instant
    Write-Host "Building $BuildType $Flavor ($versionName), modules: $($modules -join ', ')"

    if ($BuildType -eq 'release') {
        foreach ($name in @('SP_KEYSTORE_BASE64', 'SP_STORE_PASSWORD', 'SP_KEY_ALIAS', 'SP_KEY_PASSWORD')) {
            if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
                throw "Required GitHub Actions secret is missing: $name. Unsigned release packages are refused."
            }
        }
        $credentialDir = Join-Path ([IO.Path]::GetTempPath()) ("streamsplayer-signing-" + [guid]::NewGuid().ToString('N'))
        New-Item -ItemType Directory -Path $credentialDir | Out-Null
        $keyPath = Join-Path $credentialDir 'release.keystore'
        [IO.File]::WriteAllBytes($keyPath, [Convert]::FromBase64String($env:SP_KEYSTORE_BASE64))
        $propsPath = Join-Path $credentialDir 'keystore.properties'
        @(
            'storeFile=' + (ConvertTo-JavaProperty $keyPath)
            'storePassword=' + (ConvertTo-JavaProperty $env:SP_STORE_PASSWORD)
            'keyAlias=' + (ConvertTo-JavaProperty $env:SP_KEY_ALIAS)
            'keyPassword=' + (ConvertTo-JavaProperty $env:SP_KEY_PASSWORD)
        ) | Set-Content -LiteralPath $propsPath -Encoding ascii
        $env:STREAMSPLAYER_KEYSTORE_PROPERTIES = $propsPath
        Invoke-Checked 'pwsh' @('-NoProfile', '-File', 'tools/port-ledger/check-drift.ps1')
        # S0051: the documents a release carries. The contract sync stays in the sweep: the catalog is not on a runner.
        Invoke-Checked 'pwsh' @('-NoProfile', '-File', 'tools/docs/assert-docs.ps1')
        Invoke-Checked 'pwsh' @('-NoProfile', '-File', 'tools/promotion/assert-promotion.ps1')
        Invoke-Checked 'pwsh' @('-NoProfile', '-File', 'tools/help/assert-help-map.ps1')
        Invoke-Checked 'pwsh' @('-NoProfile', '-File', 'tools/site/build-site.ps1', '-Check')
    }
    Invoke-Checked $gradle @('-p', 'build-logic', ':convention:test', '--no-daemon', '--stacktrace', '--max-workers=2')

    $sdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $env:ANDROID_SDK_ROOT }
    if (-not $sdkRoot) { throw 'ANDROID_HOME or ANDROID_SDK_ROOT is required.' }
    $buildTools = Get-ChildItem -LiteralPath (Join-Path $sdkRoot 'build-tools') -Directory |
        Where-Object Name -Match '^\d+\.\d+\.\d+$' | Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
    if (-not $buildTools) { throw 'Android SDK build-tools are missing.' }
    $apksigner = Join-Path $buildTools.FullName $(if ($IsWindows) { 'apksigner.bat' } else { 'apksigner' })
    $expectedFingerprint = '61cf0209340f65d127291faedeee64e998ecc5dfa4b9f04a79e084cb22f1f497'
    $debugFingerprint = $null

    foreach ($target in $modules) {
        $partition = if ($target -eq 'wear') { 6 + [math]::Floor([int]$instant.Substring(8, 2) / 15) } else { [math]::Floor([int]$instant.Substring(8, 2) / 10) }
        $versionCode = [int]$instant.Substring(0, 8) * 10 + [int]$partition
        $variant = if ($target -eq 'watchface') { $typeSuffix } else { "$variantFlavor$typeSuffix" }
        $tasks = @()
        if ($target -ne 'watchface') {
            $tasks += ":${target}:test${variantFlavor}DebugUnitTest"
        }
        $tasks += ":${target}:assemble$variant"
        if ($BuildType -eq 'release') {
            $tasks += ":${target}:bundle$variant"
        }
        Invoke-Checked $gradle ($tasks + @("-Psp.versionName=$versionName", "-Psp.versionCode=$versionCode", '--no-daemon', '--stacktrace', '--max-workers=2'))
        $variantPath = if ($target -eq 'watchface') { $BuildType } else { "$Flavor/$BuildType" }
        $apkRoot = Join-Path $repoRoot "$target/build/outputs/apk/$variantPath"
        $metadata = Get-Content -LiteralPath (Join-Path $apkRoot 'output-metadata.json') -Raw | ConvertFrom-Json
        if (@($metadata.elements).Count -ne 1) { throw "Expected one APK for $target/$variant." }
        $element = $metadata.elements[0]
        if ($element.versionName -ne $versionName -or $element.versionCode -ne $versionCode) {
            throw "Package version mismatch for $target/$variant."
        }
        $apk = Join-Path $apkRoot $element.outputFile
        $signature = & $apksigner verify --verbose --print-certs $apk 2>&1 | Out-String
        if ($LASTEXITCODE -ne 0) { throw "APK signature verification failed for $target/$variant." }
        $fingerprint = [regex]::Match($signature, 'certificate SHA-256 digest:\s*([a-fA-F0-9]{64})').Groups[1].Value.ToLowerInvariant()
        if (-not $fingerprint) { throw "No signing certificate found for $target/$variant." }
        if ($BuildType -eq 'release' -and $fingerprint -ne $expectedFingerprint) { throw 'Release signing certificate differs from the frozen product key.' }
        if ($BuildType -eq 'debug') {
            if ($debugFingerprint -and $fingerprint -ne $debugFingerprint) { throw 'Debug packages must share a signing certificate for the Wear Data Layer.' }
            $debugFingerprint = $fingerprint
        }
        if ($target -ne 'watchface') {
            $archive = [IO.Compression.ZipFile]::OpenRead($apk)
            try {
                $notice = $archive.GetEntry('assets/THIRD-PARTY-NOTICES.txt')
                if (-not $notice -or $notice.Length -eq 0) { throw "Runtime notices are missing from $target APK." }
            } finally { $archive.Dispose() }
        }
        $edition = if ($target -eq 'watchface') { 'watchface' } else { "$target-$Flavor" }
        $apkName = "StreamsPlayer-$edition-$BuildType-$versionName.apk"
        Copy-Item -LiteralPath $apk -Destination (Join-Path $packageDir $apkName)
        $verdict.artifacts += [ordered]@{ file = $apkName; versionCode = $versionCode; sha256 = (Get-FileHash $apk -Algorithm SHA256).Hash; certificateSha256 = $fingerprint }
        if ($BuildType -eq 'release') {
            $bundles = @(Get-ChildItem -LiteralPath (Join-Path $repoRoot "$target/build/outputs/bundle/$($variant.Substring(0,1).ToLowerInvariant() + $variant.Substring(1))") -Filter '*.aab' -File)
            if ($bundles.Count -ne 1) { throw "Expected one release bundle for $target." }
            $bundle = $bundles[0].FullName
            $jarVerification = & jarsigner -verify $bundle 2>&1 | Out-String
            if ($LASTEXITCODE -ne 0 -or $jarVerification -notmatch 'jar verified') { throw "Bundle signature verification failed for $target." }
            $bundleCertificate = & keytool -printcert -jarfile $bundle 2>&1 | Out-String
            if ($LASTEXITCODE -ne 0) { throw "Cannot read bundle certificate for $target." }
            $bundleFingerprint = [regex]::Match($bundleCertificate, 'SHA256:\s*([A-Fa-f0-9:]+)').Groups[1].Value.Replace(':', '').ToLowerInvariant()
            if ($bundleFingerprint -ne $expectedFingerprint) { throw 'Bundle signing certificate differs from the frozen product key.' }
            $aabName = "StreamsPlayer-$edition-$BuildType-$versionName.aab"
            Copy-Item -LiteralPath $bundle -Destination (Join-Path $packageDir $aabName)
            $verdict.artifacts += [ordered]@{ file = $aabName; versionCode = $versionCode; sha256 = (Get-FileHash $bundle -Algorithm SHA256).Hash; certificateSha256 = $bundleFingerprint }
            $mapping = Join-Path $repoRoot "$target/build/outputs/mapping/$($variant.Substring(0,1).ToLowerInvariant() + $variant.Substring(1))/mapping.txt"
            if (Test-Path -LiteralPath $mapping) { Copy-Item -LiteralPath $mapping -Destination (Join-Path $packageDir "StreamsPlayer-$edition-$versionName-mapping.txt") }
        }
    }
    # S0051: the permissions tables against the merged store manifests this run has just produced.
    if ($BuildType -eq 'release' -and $Flavor -eq 'store' -and $Module -eq 'all') {
        Invoke-Checked 'pwsh' @('-NoProfile', '-File', 'tools/privacy/permissions.ps1')
    }
    $verdict.verdict = 'PASS'
    $verdict.reason = 'Build, applicable unit tests, package versions and signatures verified. This is build evidence; publication requires a separate release pre-flight, lint and device update proof.'
    $verdict | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $packageDir 'build-verdict.json') -Encoding utf8
    $verdict.artifacts | ForEach-Object { "$($_.sha256.ToLowerInvariant())  $($_.file)" } | Set-Content -LiteralPath (Join-Path $packageDir 'SHA256SUMS.txt') -Encoding utf8
    Write-Host "PASS: $versionName - $($verdict.artifacts.Count) verified packages."
} catch {
    $verdict.reason = $_.Exception.Message
    throw
} finally {
    $verdict | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $outDir 'build-verdict.json') -Encoding utf8
    $env:STREAMSPLAYER_KEYSTORE_PROPERTIES = $previousSigningPath
    if ($credentialDir) {
        # Delete only the two files created above, each within the dedicated temporary directory.
        foreach ($leaf in @('keystore.properties', 'release.keystore')) {
            $credentialFile = Join-Path $credentialDir $leaf
            if (Test-Path -LiteralPath $credentialFile) { Remove-Item -LiteralPath $credentialFile -Force }
        }
        Remove-Item -LiteralPath $credentialDir -Force
    }
    Pop-Location
}
