#requires -Version 7.0
<#
.SYNOPSIS
    Build StreamsPlayer on GitHub Actions and download the packages.
.EXAMPLE
    ./a.ps1 r
.EXAMPLE
    ./a.ps1 d
.EXAMPLE
    ./a.ps1 r -Flavor noLegal -Module phone
#>
[CmdletBinding()]
param(
    [ValidateSet('', 'r', 'd', 'help')][string]$Command = '',
    [string]$Repo = 'SerZhyAle/StreamsPlayer_Android',
    [string]$Ref = 'main',
    [ValidateSet('store', 'noLegal')][string]$Flavor = 'store',
    [ValidateSet('all', 'phone', 'wear', 'watchface')][string]$Module = 'all',
    [switch]$NoWait,
    [switch]$DryRun
)
$ErrorActionPreference = 'Stop'
$Flavor = if ($Flavor -eq 'noLegal') { 'noLegal' } else { 'store' }
$Module = $Module.ToLowerInvariant()
if ($Command -in @('', 'help')) {
    Write-Host 'StreamsPlayer GitHub builds (PowerShell 7 + GitHub CLI)'
    Write-Host '  ./a.ps1 r    Signed release APKs + AABs for publication'
    Write-Host '  ./a.ps1 d    Debug APKs'
    Write-Host 'Options: -Flavor store|noLegal -Module all|phone|wear|watchface'
    Write-Host '         -Repo owner/repo -Ref main -NoWait -DryRun'
    Write-Host 'Builds the selected GitHub ref. Results: DOWNLOADS/<run-id>/.'
    exit 0
}
try {
    $kind = if ($Command -eq 'r') { 'release' } else { 'debug' }
    $requestId = [guid]::NewGuid().ToString('N')
    $title = "StreamsPlayer $kind $Flavor $Module [$requestId]"
    $dispatchArgs = @('workflow', 'run', 'android-build.yml', '--repo', $Repo, '--ref', $Ref,
        '-f', "build_type=$kind", '-f', "flavor=$Flavor", '-f', "module=$Module", '-f', "request_id=$requestId")
    if ($DryRun) {
        Write-Host "gh $($dispatchArgs -join ' ')"
        Write-Host "Expected run title: $title"
        exit 0
    }
    if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { throw 'GitHub CLI is required. Install gh and run gh auth login.' }
    & gh repo view $Repo --json nameWithOwner | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Repository $Repo is unavailable. Create it and push the project with android-build.yml first." }
    & gh @dispatchArgs
    if ($LASTEXITCODE -ne 0) { throw 'Workflow dispatch failed. Check that android-build.yml exists on the default branch and selected ref.' }
    # A unique dispatch input identifies this run even when several users build the same ref.
    $run = $null
    $deadline = [datetime]::UtcNow.AddMinutes(3)
    while ([datetime]::UtcNow -lt $deadline) {
        $json = & gh run list --repo $Repo --workflow android-build.yml --event workflow_dispatch --limit 100 --json databaseId,displayTitle,url
        if ($LASTEXITCODE -ne 0) { throw 'Unable to locate the dispatched workflow. Inspect GitHub Actions before dispatching again.' }
        $run = @($json | ConvertFrom-Json | Where-Object displayTitle -eq $title) | Select-Object -First 1
        if ($run) { break }
        Write-Host 'Waiting for GitHub to register the build...'
        Start-Sleep -Seconds 5
    }
    if (-not $run) { throw 'Run registration timed out. Inspect GitHub Actions before dispatching again.' }
    Write-Host "Build: $($run.url)"
    if ($NoWait) {
        Write-Host "Download later: gh run download $($run.databaseId) --repo $Repo --dir DOWNLOADS/$($run.databaseId)"
        exit 0
    }
    & gh run watch $run.databaseId --repo $Repo --exit-status --interval 10
    if ($LASTEXITCODE -ne 0) {
        & gh run view $run.databaseId --repo $Repo --log-failed
        throw "GitHub build failed. See $($run.url)"
    }
    $destination = Join-Path $PSScriptRoot "DOWNLOADS/$($run.databaseId)"
    & gh run download $run.databaseId --repo $Repo --name "streamsplayer-$kind-$Flavor-$($run.databaseId)" --dir $destination
    if ($LASTEXITCODE -ne 0) { throw 'Artifact download failed.' }
    Write-Host "Packages downloaded to: $destination"
    exit 0
} catch {
    Write-Error $_ -ErrorAction Continue
    exit 1
}
