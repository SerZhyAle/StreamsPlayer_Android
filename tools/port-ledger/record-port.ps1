#requires -Version 7.0
<#
.SYNOPSIS
    Records a ported file in the port ledger, or records that a later FastMediaSorter change was carried.

.DESCRIPTION
    Handoff section 6a, mechanism 2: every file ported from FastMediaSorter has a row - source path, the
    source's SHA-256 at port time, destination path, port date. The hash is computed here, from the source
    file as it is now; nobody types one.

      record-port.ps1 -Source app_v2/src/main/java/.../Foo.kt -Destination phone/src/main/java/.../Foo.kt
          adds the row (refused if the pair is already in the ledger)

      record-port.ps1 -Source .../Foo.kt -Destination .../Foo.kt -Refresh
          the destination now carries FastMediaSorter's newer version of the file: the row takes the
          source's current hash, gets carriedOn, and loses any decision made about an older version

    Run it after the destination file is in place. The destination must exist.

.PARAMETER Source
    Path of the source file relative to the FastMediaSorter root.
.PARAMETER Destination
    Path of the ported file relative to this repository's root.
.PARAMETER Refresh
    Update an existing row after carrying a FastMediaSorter change instead of adding one.
.PARAMETER Date
    yyyy-MM-dd to record; defaults to today.
.PARAMETER SourceRoot
    FastMediaSorter root; defaults to $env:STREAMSPLAYER_FMS_ROOT, then P:\ANDROID\FastMediaSorter_mob_v2.
.PARAMETER Ledger
    Ledger file; defaults to docs/port-ledger.jsonl.
.PARAMETER DestinationRoot
    Root the destination paths are relative to; defaults to this repository (the self-test points it elsewhere).
#>
[CmdletBinding(PositionalBinding = $false)]
param(
    [Parameter(Mandatory)][string] $Source,
    [Parameter(Mandatory)][string] $Destination,
    [switch] $Refresh,
    [string] $Date = (Get-Date -Format 'yyyy-MM-dd'),
    [string] $SourceRoot,
    [string] $Ledger,
    [string] $DestinationRoot
)

. (Join-Path $PSScriptRoot '_ledger.ps1')
$ErrorActionPreference = 'Stop'
trap { Write-Host "record-port: $($_.Exception.Message)" -ForegroundColor Red; exit 2 }

if ($Date -notmatch '^\d{4}-\d{2}-\d{2}$') { throw "-Date '$Date' is not yyyy-MM-dd." }
$ledgerPath = Resolve-LedgerPath $Ledger
$root = Resolve-SourceRoot $SourceRoot
$sourcePath = ConvertTo-LedgerPath $Source 'Source'
$destinationPath = ConvertTo-LedgerPath $Destination 'Destination'

$sourceFile = Join-Path $root $sourcePath
if (-not (Test-Path -LiteralPath $sourceFile -PathType Leaf)) { throw "Source file '$sourcePath' does not exist under $root." }
$destinationFile = Join-Path (Resolve-DestinationRoot $DestinationRoot) $destinationPath
if (-not (Test-Path -LiteralPath $destinationFile -PathType Leaf)) {
    throw "Destination file '$destinationPath' does not exist in this repository; port the file first."
}

$rows = Read-PortLedger $ledgerPath
$existing = Find-LedgerRow $rows $sourcePath $destinationPath
$hash = Get-Sha256 $sourceFile

if ($Refresh) {
    if ($existing.Count -ne 1) { throw "-Refresh: no ledger row for $sourcePath -> $destinationPath." }
    $row = $existing[0]
    $was = $row['sourceSha256']
    $row['sourceSha256'] = $hash
    $row['carriedOn'] = $Date
    $row.Remove('decision')
    Write-PortLedger $ledgerPath $rows
    if ($was -eq $hash) { Write-Host "record-port: $sourcePath was already at $($hash.Substring(0, 12)); carriedOn set to $Date." }
    else { Write-Host "record-port: $sourcePath carried, $($was.Substring(0, 12)) -> $($hash.Substring(0, 12))." }
    exit 0
}

if ($existing.Count -gt 0) { throw "The ledger already has $sourcePath -> $destinationPath. Use -Refresh to record a carried change." }
$sameDestination = @($rows | Where-Object { $_['destination'] -eq $destinationPath })
if ($sameDestination.Count -gt 0) {
    throw "The ledger already ports $($sameDestination[0]['source']) to $destinationPath; one destination has one source."
}

$rows.Add(@{ source = $sourcePath; sourceSha256 = $hash; destination = $destinationPath; portedOn = $Date })
Write-PortLedger $ledgerPath $rows
Write-Host "record-port: $sourcePath -> $destinationPath at $($hash.Substring(0, 12))."
exit 0
