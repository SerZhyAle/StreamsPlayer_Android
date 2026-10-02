#requires -Version 7.0
<#
.SYNOPSIS
    Runs one script of the canon-shipped harness (the sza plugin's tools/harness) for this repository.

.DESCRIPTION
    The harness is adopted, not copied: this file only finds the installed plugin and forwards the call.
    What this repository configures lives in .sza-profile.json at the root.

    pwsh -NoProfile -File tools/harness.ps1 spec_catalog/validate
    pwsh -NoProfile -File tools/harness.ps1 spec_catalog/insert -Name "x" -File PLAN/S0011_x.md
    pwsh -NoProfile -File tools/harness.ps1 spec_catalog/release-queue -List

    The first argument is the harness-relative script path without '.ps1'; every other argument is
    passed through unchanged. No parameter is declared on purpose: a declared name would capture a
    prefix of the forwarded script's own parameters.

    Lookup order: $env:SZA_HARNESS_ROOT, then the newest installed plugin version under the user's
    Claude plugin cache, ordered as versions (2026.1002.2 sorts above 2026.913.1), not as strings.

Exit codes: whatever the forwarded script returns; 2 when the call shape is wrong or the harness or the
named script cannot be found.
#>

$repoRoot = Split-Path $PSScriptRoot -Parent

if ($args.Count -lt 1 -or [string]::IsNullOrWhiteSpace([string]$args[0])) {
    Write-Host 'usage: tools/harness.ps1 <cluster>/<script> [arguments..]   e.g. spec_catalog/validate'
    exit 2
}
$relative = ([string]$args[0]).Trim() -replace '\\', '/' -replace '\.ps1$', ''
$forwarded = @($args | Select-Object -Skip 1)

$candidates = @()
if ($env:SZA_HARNESS_ROOT) { $candidates += $env:SZA_HARNESS_ROOT }
$userHome = if ($env:USERPROFILE) { $env:USERPROFILE } elseif ($env:HOME) { $env:HOME } else { $null }
if ($userHome) {
    $cache = Join-Path $userHome '.claude/plugins/cache/sza-unified-rules/sza'
    if (Test-Path -LiteralPath $cache) {
        $candidates += @(Get-ChildItem -LiteralPath $cache -Directory -ErrorAction SilentlyContinue |
            ForEach-Object {
                $parsed = $null
                [void][version]::TryParse($_.Name, [ref]$parsed)
                [pscustomobject]@{ Path = $_.FullName; Version = $parsed }
            } |
            Sort-Object @{ Expression = { $null -ne $_.Version }; Descending = $true },
                        @{ Expression = { $_.Version }; Descending = $true } |
            ForEach-Object { Join-Path $_.Path 'tools/harness' })
    }
}

$target = $null
foreach ($dir in $candidates) {
    $probe = Join-Path $dir "$relative.ps1"
    if (Test-Path -LiteralPath $probe) { $target = $probe; break }
}
if (-not $target) {
    Write-Host "harness: '$relative.ps1' not found. Install the sza plugin (sza@sza-unified-rules) or set SZA_HARNESS_ROOT."
    exit 2
}

$env:SZA_PROJECT_ROOT = $repoRoot
& pwsh -NoProfile -File $target @forwarded
exit $LASTEXITCODE
