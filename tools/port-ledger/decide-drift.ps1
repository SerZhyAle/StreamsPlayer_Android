#requires -Version 7.0
<#
.SYNOPSIS
    Writes, next to its ledger row, the decision not to carry a FastMediaSorter change.

.DESCRIPTION
    Handoff section 6a: a change left uncarried is a decision written next to its ledger row, never
    silence. The decision is pinned to the source version it was made about, so it stops covering the file
    the moment FastMediaSorter changes it again - the next change is drift until it is decided too.

    The note is mandatory and is the reason, in words ("the sorter-only branch of the fix", "superseded by
    our own change in S0005", not "n/a"). A source file that FastMediaSorter has removed or moved is
    decided the same way; the decision then covers the removal.

.PARAMETER Source
    Path of the source file relative to the FastMediaSorter root, as in the ledger row.
.PARAMETER Destination
    The row's destination, when one source is ported to more than one place.
.PARAMETER Note
    Why the change is not carried.
.PARAMETER Date
    yyyy-MM-dd to record; defaults to today.
#>
[CmdletBinding(PositionalBinding = $false)]
param(
    [Parameter(Mandatory)][string] $Source,
    [string] $Destination,
    [Parameter(Mandatory)][string] $Note,
    [string] $Date = (Get-Date -Format 'yyyy-MM-dd'),
    [string] $SourceRoot,
    [string] $Ledger
)

. (Join-Path $PSScriptRoot '_ledger.ps1')
$ErrorActionPreference = 'Stop'
trap { Write-Host "decide-drift: $($_.Exception.Message)" -ForegroundColor Red; exit 2 }

if ($Date -notmatch '^\d{4}-\d{2}-\d{2}$') { throw "-Date '$Date' is not yyyy-MM-dd." }
if ([string]::IsNullOrWhiteSpace($Note) -or $Note.Trim().Length -lt 12) {
    throw 'The note is the decision: write the reason in words (at least a short sentence).'
}
$ledgerPath = Resolve-LedgerPath $Ledger
$root = Resolve-SourceRoot $SourceRoot
$sourcePath = ConvertTo-LedgerPath $Source 'Source'
$destinationPath = if ($Destination) { ConvertTo-LedgerPath $Destination 'Destination' } else { $null }

$rows = Read-PortLedger $ledgerPath
$found = Find-LedgerRow $rows $sourcePath $destinationPath
if ($found.Count -eq 0) { throw "No ledger row for $sourcePath." }
if ($found.Count -gt 1) { throw "$sourcePath is ported to more than one destination; pass -Destination." }
$row = $found[0]

$sourceFile = Join-Path $root $sourcePath
$current = if (Test-Path -LiteralPath $sourceFile -PathType Leaf) { Get-Sha256 $sourceFile } else { 'missing' }
if ($current -eq $row['sourceSha256']) {
    throw "$sourcePath has not changed since it was ported or carried; there is nothing to decide."
}

$row['decision'] = [ordered]@{ sourceSha256 = $current; note = $Note.Trim(); decidedOn = $Date }
Write-PortLedger $ledgerPath $rows
Write-Host "decide-drift: $sourcePath left uncarried at $($current.Substring(0, [Math]::Min(12, $current.Length))): $($Note.Trim())"
exit 0
