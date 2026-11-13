#requires -Version 7.0
# Isolated GitHub CLI doubles exercise dispatch correlation and failure propagation without network writes.
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$evidenceDir = Join-Path $root 'temp/ci-launcher-tests'
New-Item -ItemType Directory -Path $evidenceDir -Force | Out-Null
$driver = @'
function global:gh {
    $tokens = @($args | ForEach-Object { [string]$_ })
    ConvertTo-Json -InputObject $tokens -Compress | Add-Content -LiteralPath $env:SP_TEST_LOG
    $global:LASTEXITCODE = 0
    $operation = ($tokens | Select-Object -First 2) -join ' '
    switch ($operation) {
        'repo view' {
            if ($env:SP_TEST_CASE -eq 'repo-failure') { $global:LASTEXITCODE = 1; return }
            return '{"nameWithOwner":"example/repo"}'
        }
        'workflow run' {
            if ($env:SP_TEST_CASE -eq 'dispatch-failure') { $global:LASTEXITCODE = 2; return }
            $script:request = ($tokens | Where-Object { $_ -like 'request_id=*' }).Substring(11)
            $script:kind = ($tokens | Where-Object { $_ -like 'build_type=*' }).Substring(11)
            return
        }
        'run list' {
            return ConvertTo-Json -InputObject @(
                @{ databaseId = 111; displayTitle = 'Another user build'; url = 'https://example.invalid/111' },
                @{ databaseId = 222; displayTitle = "StreamsPlayer $script:kind store all [$script:request]"; url = 'https://example.invalid/222' }
            ) -Compress
        }
        'run watch' {
            if ($env:SP_TEST_CASE -eq 'build-failure') { $global:LASTEXITCODE = 17 }
            return
        }
        'run view' { return 'Build failure evidence' }
        'run download' {
            if ($env:SP_TEST_CASE -eq 'download-failure') { $global:LASTEXITCODE = 3 }
            return
        }
        default { throw "Unexpected GitHub CLI call: $operation" }
    }
}
$launchArgs = @{ Command = $env:SP_TEST_COMMAND; Repo = 'example/repo' }
if ($env:SP_TEST_CASE -eq 'no-wait') { $launchArgs.NoWait = $true }
if ($env:SP_TEST_CASE -eq 'mixed-case') { $launchArgs.Flavor = 'STORE'; $launchArgs.Module = 'ALL' }
& $env:SP_TEST_LAUNCHER @launchArgs
exit $LASTEXITCODE
'@
$encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($driver))
$savedEnv = @{}
foreach ($key in @('SP_TEST_CASE', 'SP_TEST_LOG', 'SP_TEST_COMMAND', 'SP_TEST_LAUNCHER')) { $savedEnv[$key] = [Environment]::GetEnvironmentVariable($key) }
try {
    $env:SP_TEST_LAUNCHER = Join-Path $root 'a.ps1'
    foreach ($case in @('release-success', 'debug-success', 'mixed-case', 'no-wait', 'repo-failure', 'dispatch-failure', 'build-failure', 'download-failure')) {
        $env:SP_TEST_CASE = $case
        $env:SP_TEST_COMMAND = if ($case -eq 'release-success') { 'r' } else { 'd' }
        $env:SP_TEST_LOG = Join-Path $evidenceDir "$case-calls.jsonl"
        Set-Content -LiteralPath $env:SP_TEST_LOG -Value ''
        $output = & pwsh -NoProfile -EncodedCommand $encoded 2>&1 | Out-String
        $exitCode = $LASTEXITCODE
        $output | Set-Content -LiteralPath (Join-Path $evidenceDir "$case-output.txt")
        $expectedExit = if ($case -like '*failure') { 1 } else { 0 }
        if ($exitCode -ne $expectedExit) { throw "$case exit $exitCode, expected $expectedExit. $output" }
        $calls = @(Get-Content -LiteralPath $env:SP_TEST_LOG | Where-Object { $_.Trim() } | ForEach-Object { ,($_ | ConvertFrom-Json) })
        $operations = @($calls | ForEach-Object { ($_ | Select-Object -First 2) -join ' ' })
        $downloaded = 'run download' -in $operations
        if ($downloaded -ne ($case -in @('release-success', 'debug-success', 'mixed-case', 'download-failure'))) { throw "$case download gate failed." }
        if ($case -eq 'no-wait' -and 'run watch' -in $operations) { throw 'NoWait must not watch the run.' }
        if ($case -eq 'repo-failure' -and 'workflow run' -in $operations) { throw 'Unavailable repository must prevent dispatch.' }
        if ($case -eq 'dispatch-failure' -and 'run list' -in $operations) { throw 'Dispatch failure must prevent run polling.' }
        if ($case -eq 'build-failure' -and 'run view' -notin $operations) { throw 'Build failure must show failure logs.' }
        foreach ($call in $calls) {
            if ($call[0] -eq 'run' -and $call[1] -in @('watch', 'view', 'download') -and $call[2] -ne '222') { throw "$case selected another user's run." }
        }
        Write-Host "PASS: $case (exit $exitCode)"
    }
} finally {
    foreach ($key in $savedEnv.Keys) { [Environment]::SetEnvironmentVariable($key, $savedEnv[$key]) }
}
