#requires -Version 7.0
<#
.SYNOPSIS
    Self-test of the port ledger tools, on a throw-away source tree and ledger in the temp folder.

.DESCRIPTION
    Exercises the whole life of a row - port, in step, drift, decision, a second change, carry, removal -
    through the real scripts as child processes, so the exit codes are the ones a release run would see.
    Touches nothing in this repository and nothing in FastMediaSorter. Exit 0 when every check holds.
#>
[CmdletBinding(PositionalBinding = $false)]
param()

$ErrorActionPreference = 'Stop'
$here = $PSScriptRoot
$work = Join-Path ([System.IO.Path]::GetTempPath()) ("port-ledger-test-" + [guid]::NewGuid().ToString('N').Substring(0, 8))
$source = Join-Path $work 'fms'
$destination = Join-Path $work 'sp'
$ledger = Join-Path $work 'ledger.jsonl'
New-Item -ItemType Directory -Path "$source/app/src", "$destination/phone/src" -Force | Out-Null

$failures = 0
function Check([string] $Name, [bool] $Condition, [string] $Detail = '') {
    if ($Condition) { Write-Host "  ok    $Name" }
    else { $script:failures++; Write-Host "  FAIL  $Name $Detail" -ForegroundColor Red }
}
function Run([string] $Script, [string[]] $Arguments) {
    $output = & pwsh -NoProfile -File (Join-Path $here $Script) @Arguments 2>&1 | Out-String
    return [pscustomobject]@{ Exit = $LASTEXITCODE; Output = $output }
}
$common = @('-SourceRoot', $source, '-Ledger', $ledger, '-DestinationRoot', $destination)
$commonNoDest = @('-SourceRoot', $source, '-Ledger', $ledger)

try {
    # --- an empty ledger is green
    $r = Run 'check-drift.ps1' $common
    Check 'an absent ledger is in step' ($r.Exit -eq 0) $r.Output
    [System.IO.File]::WriteAllText($ledger, '')
    $r = Run 'check-drift.ps1' $common
    Check 'an empty ledger is in step' ($r.Exit -eq 0 -and $r.Output -match 'OK: nothing uncarried') $r.Output

    # --- port two files
    [System.IO.File]::WriteAllText("$source/app/src/A.kt", "class A { fun a() = 1 }`n")
    [System.IO.File]::WriteAllText("$source/app/src/B.kt", "class B`n")
    [System.IO.File]::WriteAllText("$destination/phone/src/A.kt", "class A { fun a() = 1 }`n")
    [System.IO.File]::WriteAllText("$destination/phone/src/B.kt", "class B`n")

    $r = Run 'record-port.ps1' ($common + @('-Source', 'app/src/B.kt', '-Destination', 'phone/src/B.kt', '-Date', '2026-10-02'))
    Check 'port B' ($r.Exit -eq 0) $r.Output
    $r = Run 'record-port.ps1' ($common + @('-Source', 'app\src\A.kt', '-Destination', 'phone\src\A.kt', '-Date', '2026-10-02'))
    Check 'port A (backslashes are normalised)' ($r.Exit -eq 0) $r.Output

    $lines = Get-Content -LiteralPath $ledger
    Check 'two rows, ordered by source' ($lines.Count -eq 2 -and $lines[0] -like '{"source":"app/src/A.kt"*' -and $lines[1] -like '{"source":"app/src/B.kt"*')
    $expectedHash = (Get-FileHash -LiteralPath "$source/app/src/A.kt" -Algorithm SHA256).Hash.ToLowerInvariant()
    Check 'the recorded hash is the SHA-256 of the source bytes' ($lines[0] -like "*`"sourceSha256`":`"$expectedHash`"*")
    Check 'LF line endings, no BOM' (([System.IO.File]::ReadAllBytes($ledger) | Select-Object -First 1) -eq [byte][char]'{' -and
        -not ([System.IO.File]::ReadAllText($ledger)).Contains("`r"))

    $r = Run 'record-port.ps1' ($common + @('-Source', 'app/src/A.kt', '-Destination', 'phone/src/A.kt'))
    Check 'the same pair twice is refused' ($r.Exit -eq 2 -and $r.Output -match 'already has') $r.Output
    $r = Run 'record-port.ps1' ($common + @('-Source', 'app/src/B.kt', '-Destination', 'phone/src/A.kt'))
    Check 'one destination, one source' ($r.Exit -eq 2 -and $r.Output -match 'one destination has one source') $r.Output
    $r = Run 'record-port.ps1' ($common + @('-Source', 'app/src/A.kt', '-Destination', 'phone/src/Missing.kt'))
    Check 'a destination that is not there is refused' ($r.Exit -eq 2 -and $r.Output -match 'does not exist') $r.Output
    $r = Run 'record-port.ps1' ($common + @('-Source', '../escape.kt', '-Destination', 'phone/src/A.kt'))
    Check 'a path that leaves its root is refused' ($r.Exit -eq 2 -and $r.Output -match 'stay inside') $r.Output

    $r = Run 'check-drift.ps1' $common
    Check 'in step right after the port' ($r.Exit -eq 0 -and $r.Output -match '2 in step') $r.Output

    # --- FastMediaSorter changes A: drift
    [System.IO.File]::WriteAllText("$source/app/src/A.kt", "class A { fun a() = 2 }`n")
    $r = Run 'check-drift.ps1' $common
    Check 'a changed source is drift (exit 1, listed)' ($r.Exit -eq 1 -and $r.Output -match 'DRIFT\s+app/src/A\.kt' -and $r.Output -notmatch 'DRIFT\s+app/src/B\.kt') $r.Output
    $r = Run 'check-drift.ps1' ($common + @('-Json'))
    $json = $r.Output | ConvertFrom-Json
    Check 'json report counts' ($r.Exit -eq 1 -and $json.rows -eq 2 -and $json.inStep -eq 1 -and $json.uncarried.Count -eq 1)

    # --- the decision
    $r = Run 'decide-drift.ps1' ($commonNoDest + @('-Source', 'app/src/A.kt', '-Note', 'n/a'))
    Check 'a one-word note is not a decision' ($r.Exit -eq 2) $r.Output
    $r = Run 'decide-drift.ps1' ($commonNoDest + @('-Source', 'app/src/B.kt', '-Note', 'B has not changed at all'))
    Check 'an unchanged file has nothing to decide' ($r.Exit -eq 2 -and $r.Output -match 'nothing to decide') $r.Output
    $r = Run 'decide-drift.ps1' ($commonNoDest + @('-Source', 'app/src/A.kt', '-Note', 'The change is the sorter-only branch of the fix.'))
    Check 'a written decision is accepted' ($r.Exit -eq 0) $r.Output
    $r = Run 'check-drift.ps1' $common
    Check 'a decided change is not uncarried, and its note is shown' ($r.Exit -eq 0 -and $r.Output -match 'decided\s+app/src/A\.kt' -and $r.Output -match 'sorter-only') $r.Output

    # --- the decision covers one source version only
    [System.IO.File]::WriteAllText("$source/app/src/A.kt", "class A { fun a() = 3 }`n")
    $r = Run 'check-drift.ps1' $common
    Check 'a second change is drift again' ($r.Exit -eq 1 -and $r.Output -match 'DRIFT\s+app/src/A\.kt') $r.Output

    # --- carry it
    [System.IO.File]::WriteAllText("$destination/phone/src/A.kt", "class A { fun a() = 3 }`n")
    $r = Run 'record-port.ps1' ($common + @('-Source', 'app/src/A.kt', '-Destination', 'phone/src/A.kt', '-Refresh', '-Date', '2026-10-09'))
    Check 'carry is recorded' ($r.Exit -eq 0) $r.Output
    $row = (Get-Content -LiteralPath $ledger | Select-Object -First 1) | ConvertFrom-Json
    Check 'the row keeps portedOn, gains carriedOn, loses the old decision' ($row.portedOn -eq '2026-10-02' -and $row.carriedOn -eq '2026-10-09' -and $null -eq $row.decision)
    $r = Run 'check-drift.ps1' $common
    Check 'in step after the carry' ($r.Exit -eq 0 -and $r.Output -match '2 in step') $r.Output

    # --- the source file disappears
    Remove-Item -LiteralPath "$source/app/src/B.kt"
    $r = Run 'check-drift.ps1' $common
    Check 'a removed source is reported' ($r.Exit -eq 1 -and $r.Output -match 'SOURCE GONE\s+app/src/B\.kt') $r.Output
    $r = Run 'decide-drift.ps1' ($commonNoDest + @('-Source', 'app/src/B.kt', '-Note', 'FastMediaSorter folded B into A; we keep B.'))
    Check 'a removal can be decided' ($r.Exit -eq 0) $r.Output
    $r = Run 'check-drift.ps1' $common
    Check 'a decided removal is not uncarried' ($r.Exit -eq 0) $r.Output

    # --- the destination disappears
    Remove-Item -LiteralPath "$destination/phone/src/A.kt"
    $r = Run 'check-drift.ps1' $common
    Check 'a missing destination is reported' ($r.Exit -eq 1 -and $r.Output -match 'NO DEST\s+app/src/A\.kt') $r.Output

    # --- the check cannot run
    $r = Run 'check-drift.ps1' @('-SourceRoot', (Join-Path $work 'nowhere'), '-Ledger', $ledger)
    Check 'a missing source root is exit 2, not a green' ($r.Exit -eq 2) $r.Output
    [System.IO.File]::WriteAllText($ledger, "not json`n")
    $r = Run 'check-drift.ps1' $common
    Check 'a malformed ledger is exit 2, not a green' ($r.Exit -eq 2 -and $r.Output -match 'not valid JSON') $r.Output
}
finally {
    Remove-Item -LiteralPath $work -Recurse -Force -ErrorAction SilentlyContinue
}

if ($failures -gt 0) { Write-Host "test-port-ledger: $failures check(s) FAILED" -ForegroundColor Red; exit 1 }
Write-Host 'test-port-ledger: all checks passed'
exit 0
