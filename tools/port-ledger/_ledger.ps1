# Shared helpers of the port ledger tools (handoff section 6a, mechanism 2). Dot-sourced by the scripts of
# this folder; it runs nothing on its own.
#
# The ledger is docs/port-ledger.jsonl, one JSON object per line, one line per ported file:
#
#   source        path in FastMediaSorter, relative to its root, forward slashes
#   sourceSha256  SHA-256 of that file's bytes when it was ported (or last carried), lower-case hex
#   destination   path in this repository, relative to its root, forward slashes
#   portedOn      yyyy-MM-dd
#   carriedOn     yyyy-MM-dd, present once a later FastMediaSorter change was carried across
#   decision      present when a change was deliberately left uncarried:
#                   sourceSha256  the source version the decision is about ("missing" for a removed source)
#                   note          why, in words - mandatory
#                   decidedOn     yyyy-MM-dd
#
# A row is in step while the source still hashes to sourceSha256. A changed source is drift until someone
# either carries the change (record-port.ps1 -Refresh) or writes the decision (decide-drift.ps1).

Set-StrictMode -Version Latest

$script:RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$script:DefaultLedger = Join-Path $script:RepoRoot 'docs\port-ledger.jsonl'
$script:DefaultSourceRoot = 'P:\ANDROID\FastMediaSorter_mob_v2'

function Resolve-LedgerPath {
    param([string] $Ledger)
    if ($Ledger) { return $Ledger }
    return $script:DefaultLedger
}

function Resolve-DestinationRoot {
    param([string] $DestinationRoot)
    if (-not $DestinationRoot) { return $script:RepoRoot }
    if (-not (Test-Path -LiteralPath $DestinationRoot -PathType Container)) {
        throw "Destination root '$DestinationRoot' does not exist."
    }
    return (Resolve-Path -LiteralPath $DestinationRoot).Path
}

function Resolve-SourceRoot {
    param([string] $SourceRoot)
    $root = if ($SourceRoot) { $SourceRoot }
            elseif ($env:STREAMSPLAYER_FMS_ROOT) { $env:STREAMSPLAYER_FMS_ROOT }
            else { $script:DefaultSourceRoot }
    if (-not (Test-Path -LiteralPath $root -PathType Container)) {
        throw "FastMediaSorter source root '$root' does not exist. Pass -SourceRoot or set STREAMSPLAYER_FMS_ROOT."
    }
    return (Resolve-Path -LiteralPath $root).Path
}

# Ledger paths are forward-slash, relative and inside their root; anything else would let a row point
# outside the two trees it is meant to relate.
function ConvertTo-LedgerPath {
    param([Parameter(Mandatory)][string] $Path, [Parameter(Mandatory)][string] $What)
    $normal = ($Path -replace '\\', '/').Trim()
    $normal = $normal -replace '^\./', ''
    if ($normal -eq '' -or $normal.StartsWith('/') -or $normal -match '^[A-Za-z]:' -or
        ($normal -split '/') -contains '..') {
        throw "$What path '$Path' must be relative to its repository root and stay inside it."
    }
    return $normal
}

function Get-Sha256 {
    param([Parameter(Mandatory)][string] $FilePath)
    return (Get-FileHash -LiteralPath $FilePath -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Read-PortLedger {
    param([Parameter(Mandatory)][string] $Path)
    $rows = [System.Collections.Generic.List[object]]::new()
    if (-not (Test-Path -LiteralPath $Path)) { return , $rows }
    $number = 0
    foreach ($line in [System.IO.File]::ReadAllLines($Path)) {
        $number++
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        try { $row = $line | ConvertFrom-Json -AsHashtable -ErrorAction Stop }
        catch { throw "$Path line ${number}: not valid JSON ($($_.Exception.Message))." }
        foreach ($key in 'source', 'sourceSha256', 'destination', 'portedOn') {
            if (-not $row.ContainsKey($key) -or [string]::IsNullOrWhiteSpace([string]$row[$key])) {
                throw "$Path line ${number}: row has no '$key'."
            }
        }
        if ($row['sourceSha256'] -notmatch '^[0-9a-f]{64}$') {
            throw "$Path line ${number}: sourceSha256 is not a lower-case SHA-256."
        }
        $rows.Add($row)
    }
    return , $rows
}

# Stable order (source, then destination) keeps a diff of the ledger to the rows that changed. Written
# through a temp file so an interrupted run never leaves half a ledger, with LF and no BOM so that the
# committed bytes do not depend on the machine.
function Write-PortLedger {
    param([Parameter(Mandatory)][string] $Path, [Parameter(Mandatory)] $Rows)
    $order = 'source', 'sourceSha256', 'destination', 'portedOn', 'carriedOn', 'decision'
    $sorted = [System.Collections.Generic.List[object]]::new($Rows)
    $sorted.Sort([Comparison[object]] {
        param($a, $b)
        $bySource = [string]::CompareOrdinal($a['source'], $b['source'])
        if ($bySource -ne 0) { $bySource } else { [string]::CompareOrdinal($a['destination'], $b['destination']) }
    })
    $lines = foreach ($row in $sorted) {
        $ordered = [ordered]@{}
        foreach ($key in $order) { if ($row.ContainsKey($key)) { $ordered[$key] = $row[$key] } }
        $ordered | ConvertTo-Json -Compress -Depth 4 -EscapeHandling Default
    }
    $text = if ($lines) { ($lines -join "`n") + "`n" } else { '' }
    $directory = Split-Path -Parent $Path
    if ($directory -and -not (Test-Path -LiteralPath $directory)) { New-Item -ItemType Directory -Path $directory | Out-Null }
    $temp = "$Path.tmp"
    [System.IO.File]::WriteAllText($temp, $text, [System.Text.UTF8Encoding]::new($false))
    Move-Item -LiteralPath $temp -Destination $Path -Force
}

function Find-LedgerRow {
    param([Parameter(Mandatory)] $Rows, [Parameter(Mandatory)][string] $Source, [string] $Destination)
    # The leading comma keeps an empty result an empty array: a bare `return @()` unrolls to nothing, and
    # the caller's `.Count` on that $null is an error under strict mode.
    return , @($Rows | Where-Object {
        $_['source'] -eq $Source -and (-not $Destination -or $_['destination'] -eq $Destination)
    })
}
