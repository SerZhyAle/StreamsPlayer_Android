#requires -Version 7.0
<#
.SYNOPSIS
    Writes one dated promotion checkpoint (S0014 P0 and P10, canon PROMOTION.md section 7): the counters of the S0014 baseline
    table, read now, as a file in PLAN/S0014_free-promotion-campaign/metrics/.

.DESCRIPTION
    Reads, with no telemetry and no credentials, only what a stranger could read: the git remote, the commit and tag counts, the
    declared channels, the repository's existence and counters through the GitHub CLI when it is installed, the Play listing
    status of the two package ids, the product site and the host-root robots.txt. A counter that cannot exist yet is written
    `not applicable - <why>`; a read that fails is written `unreadable - <reason>` and never guessed.

    Default name: metrics/<yyyy-MM-dd>-checkpoint.md; with -Baseline the name is metrics/<yyyy-MM-dd>-baseline.md. An existing file
    of the same name is refused unless -Force. Outreach is active at most fourteen days apart: the previous checkpoint's date is
    printed with the number of days since it.

.PARAMETER RepoRoot
    Repository root. Default: two levels above this script's folder.
.PARAMETER Baseline
    Write the baseline file instead of a checkpoint.
.PARAMETER Force
    Overwrite a file of the same name.
.PARAMETER NoNetwork
    Skip every network read (each such row is written `skipped - -NoNetwork`).

.NOTES
    Exit codes: 0 written; 2 could not write. Reading is not a gate: an unreadable counter is a row, not a failure.
#>
[CmdletBinding()]
param([string]$RepoRoot = '', [switch]$Baseline, [switch]$Force, [switch]$NoNetwork)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Read-Status([string]$Url) {
    if ($script:NoNetwork) { return 'skipped - -NoNetwork' }
    try {
        $response = Invoke-WebRequest -Uri $Url -Method Get -MaximumRedirection 5 -TimeoutSec 8 -SkipHttpErrorCheck -UseBasicParsing
        return "HTTP $([int]$response.StatusCode)"
    } catch { return "unreadable - $($_.Exception.Message.Split("`n")[0])" }
}

try {
    $root = if ($RepoRoot) { (Resolve-Path -LiteralPath $RepoRoot).Path } else { (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path }
    $today = Get-Date -Format 'yyyy-MM-dd'
    $dir = Join-Path $root 'PLAN/S0014_free-promotion-campaign/metrics'
    $name = if ($Baseline) { "$today-baseline.md" } else { "$today-checkpoint.md" }
    $path = Join-Path $dir $name
    if ((Test-Path -LiteralPath $path) -and -not $Force) { throw "$name exists; pass -Force to overwrite it" }

    $previous = @(Get-ChildItem -LiteralPath $dir -Filter '*.md' -File -ErrorAction SilentlyContinue | Where-Object { $_.Name -match '^(\d{4}-\d{2}-\d{2})-' -and $_.Name -ne $name } | Sort-Object Name)

    Push-Location $root
    try {
        $remote = @(& git remote -v 2>$null)
        $commits = (& git rev-list --count HEAD 2>$null)
        $tags = @(& git tag 2>$null).Count
    } finally { Pop-Location }
    $canon = Get-Content -LiteralPath (Join-Path $root '.sza-canon.json') -Raw | ConvertFrom-Json
    $channels = if ($canon.PSObject.Properties.Name -contains 'channels') { ($canon.channels | ConvertTo-Json -Compress -AsArray) } else { 'not declared' }
    $siteKind = if ($canon.PSObject.Properties.Name -contains 'site') { [string]$canon.site.kind } else { 'none' }
    $privacyPage = if ($canon.PSObject.Properties.Name -contains 'privacy') { [string]$canon.privacy.page } else { 'null' }

    $repoName = 'SerZhyAle/StreamsPlayer_Android'
    $repoRow = 'not applicable - no git remote'
    $countersRow = 'not applicable - no repository exists'
    $socialRow = 'not applicable - no repository exists'
    if (-not $NoNetwork) {
        $gh = Get-Command gh -ErrorAction SilentlyContinue
        if ($gh) {
            $json = & gh api "repos/$repoName" --jq '{description,homepage,topics,stargazers_count,forks_count}' 2>$null
            if ($LASTEXITCODE -eq 0 -and $json) {
                $repoRow = "exists: $json"
                $countersRow = 'read through `gh api repos/<name>`; traffic and clones need push access and are not read here'
                $og = & gh api graphql -f query='query { repository(owner:"SerZhyAle", name:"StreamsPlayer_Android") { usesCustomOpenGraphImage } }' --jq '.data.repository.usesCustomOpenGraphImage' 2>$null
                $socialRow = "usesCustomOpenGraphImage: $og"
            } else { $repoRow = 'HTTP 404 (or unreadable) - `gh api repos/' + $repoName + '` returned no repository' }
        } else { $repoRow = 'unreadable - the GitHub CLI is not installed' }
    } else { $repoRow = 'skipped - -NoNetwork' }

    $rows = [ordered]@{
        'Git remote' = $(if ($remote.Count -eq 0) { 'none (empty `git remote -v`)' } else { ($remote -join '; ') })
        'Commits / tags' = "$commits commits, $tags tags"
        'Declared channels' = $channels
        'Site kind' = $siteKind
        'Privacy page' = $privacyPage
        "GitHub repository $repoName" = $repoRow
        'Stars, forks' = $countersRow
        'Custom social card' = $socialRow
        'Play listing com.sza.streamsplayer' = (Read-Status 'https://play.google.com/store/apps/details?id=com.sza.streamsplayer')
        'Play listing com.sza.streamsplayer.watchface' = (Read-Status 'https://play.google.com/store/apps/details?id=com.sza.streamsplayer.watchface')
        'Hosted privacy page' = (Read-Status 'https://serzhyale.github.io/StreamsPlayer_Android/privacy.html')
        'Product landing' = (Read-Status 'https://serzhyale.github.io/StreamsPlayer_Android/')
        'Host root robots.txt (the hub ticket)' = (Read-Status 'https://serzhyale.github.io/robots.txt')
    }

    $title = if ($Baseline) { 'baseline' } else { 'checkpoint' }
    $lines = [Collections.Generic.List[string]]::new()
    $lines.Add("# S0014 $title - $today")
    $lines.Add('')
    $lines.Add('Written by `tools/promotion/write-checkpoint.ps1`; read-only, no telemetry, nothing sent from this repository. A row is')
    $lines.Add('`not applicable` when the counter cannot exist yet, `unreadable` when the read failed - never a guess.')
    $lines.Add('')
    $lines.Add('| What | Reading |')
    $lines.Add('| --- | --- |')
    foreach ($key in $rows.Keys) { $lines.Add("| $key | $($rows[$key] -replace '\|', '/') |") }
    $lines.Add('')
    if ($previous.Count -gt 0) {
        $last = $previous[-1].Name.Substring(0, 10)
        $days = [int]((Get-Date).Date - [datetime]::ParseExact($last, 'yyyy-MM-dd', $null)).TotalDays
        $lines.Add("Previous file: $($previous[-1].Name), $days day(s) ago$(if ($days -gt 14) { ' - over the fourteen-day limit while outreach is active' }).")
    } else { $lines.Add('Previous file: none.') }

    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    [IO.File]::WriteAllText($path, (($lines -join "`n") + "`n"), [Text.UTF8Encoding]::new($false))
    Write-Host "write-checkpoint: wrote PLAN/S0014_free-promotion-campaign/metrics/$name"
    exit 0
} catch {
    Write-Host "write-checkpoint: COULD NOT WRITE ($($_.Exception.Message))"
    exit 2
}
