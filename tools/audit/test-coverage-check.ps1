#requires -Version 7.0
<#
.SYNOPSIS
    Self-test of coverage_check.ps1 (S0025 section 6): fixture inputs force every documented exit code.

.DESCRIPTION
    Runs the gate as a child process on small fixture inputs (tools/audit/fixtures/coverage) and on mutations of
    them written to a temp folder, so the exit codes are the ones a caller would see. Then runs the real
    CLAUDE.md and matrix pair. The fixtures run before any coverage verdict is trusted: coverage_check.ps1 on the
    real pair is only believed when this script has exited 0 since the last change to the gate.

    Exit codes (CHECK-VERDICT): 0 every case held; 1 a case did not hold; 2 a fixture or the gate is missing.
    The last line is the verdict line, a test verdict line of CHECK-VERDICT 0.14 item A: run counts every case
    executed, passed or failed; no case is ever skipped.
#>
[CmdletBinding(PositionalBinding = $false)]
param()

$ErrorActionPreference = 'Stop'
$here = $PSScriptRoot
$fixtures = Join-Path $here 'fixtures/coverage'
$gate = Join-Path $here 'coverage_check.ps1'
$failures = 0
$cases = 0

function Check([string] $Name, [bool] $Condition, [string] $Detail = '') {
    $script:cases++
    if ($Condition) { Write-Host "  ok    $Name" }
    else { $script:failures++; Write-Host "  FAIL  $Name $Detail" -ForegroundColor Red }
}

function Run-Gate([string[]] $Arguments) {
    $output = & pwsh -NoProfile -File $gate @Arguments 2>&1 | Out-String
    $code = $LASTEXITCODE
    $last = (($output -split "\r?\n") | Where-Object { $_.Trim() } | Select-Object -Last 1)
    return [pscustomobject]@{ Exit = $code; Last = $last; Output = $output }
}

function Expect([string] $Name, [object] $Result, [int] $Code, [string] $LastPattern, [string] $GapPattern = '') {
    $ok = $Result.Exit -eq $Code -and $Result.Last -match $LastPattern
    if ($ok -and $GapPattern) { $ok = $Result.Output -match $GapPattern }
    Check $Name $ok "(exit $($Result.Exit), last line: $($Result.Last))`n$($Result.Output)"
}

if (-not (Test-Path -LiteralPath $gate) -or -not (Test-Path -LiteralPath $fixtures)) {
    Write-Host 'missing: the gate or its fixtures'
    Write-Host 'coverage-selftest: COULD NOT VERIFY (run=0 skipped=0, the gate or its fixtures are missing)'
    exit 2
}

$work = Join-Path ([System.IO.Path]::GetTempPath()) ("coverage-selftest-" + [guid]::NewGuid().ToString('N').Substring(0, 8))
New-Item -ItemType Directory -Path $work -Force | Out-Null

function Mutate([string] $Name, [object] $From, [string] $To = '') {
    $pairs = @(, @($From, $To))
    if ($From -is [array]) { $pairs = $From }
    $changed = [System.IO.File]::ReadAllText((Join-Path $fixtures 'spec-ok.md'))
    foreach ($pair in $pairs) {
        $before = $changed
        $changed = $changed.Replace([string]$pair[0], [string]$pair[1])
        if ($changed -eq $before) { throw "fixture mutation '$Name' changed nothing: '$($pair[0])' is not in the text" }
    }
    $path = Join-Path $work "$Name.md"
    [System.IO.File]::WriteAllText($path, $changed)
    return $path
}

try {
    $claudeOk = Join-Path $fixtures 'claude-ok.md'
    $keywords = Join-Path $fixtures 'keywords.json'
    $specOk = Join-Path $fixtures 'spec-ok.md'
    $base = @('-ClaudeMd', $claudeOk, '-Keywords', $keywords)

    Write-Host 'exit 0 - the fixture pair is covered'
    Expect 'fixture pair passes' (Run-Gate ($base + @('-Matrix', $specOk))) 0 '^coverage: PASS \(3 capabilities, 15 cells\)$'

    Write-Host 'exit 1 - every kind of gap'
    $noScene = Mutate 'no-scene' @(
        @("| SC-02 | Stream playback | open and play | stalls |`n", ''),
        @("| SC-02 | open (B) | open (B) | open (B) | open (B) | open (E) |`n", ''))
    Expect 'a capability with no scene' (Run-Gate ($base + @('-Matrix', $noScene))) 1 '^coverage: FAIL \(1 gap\(s\): capability ''beta stream'' maps to no scene\)$'

    Expect 'an open cell without a phase' (Run-Gate ($base + @('-Matrix', (Mutate 'open-bare' '| open (B) | open (B) | open (B) | open (B) | open (E) |' '| open | open (B) | open (B) | open (B) | open (E) |')))) 1 '^coverage: FAIL' 'GAP\s+SC-02 P1 is ''open'''
    Expect 'an open cell with a phase outside A-E' (Run-Gate ($base + @('-Matrix', (Mutate 'open-f' '| open (B) | open (B) | open (B) | open (B) | open (E) |' '| open (F) | open (B) | open (B) | open (B) | open (E) |')))) 1 '^coverage: FAIL' 'GAP\s+SC-02 P1'
    Expect 'a measured cell without date and pointer' (Run-Gate ($base + @('-Matrix', (Mutate 'measured-bare' 'measured (2026-10-23; run-0001)' 'measured')))) 1 '^coverage: FAIL' 'GAP\s+SC-01 P4 is ''measured'''
    Expect 'a measured cell without a pointer' (Run-Gate ($base + @('-Matrix', (Mutate 'measured-nopointer' 'measured (2026-10-23; run-0001)' 'measured (2026-10-23)')))) 1 '^coverage: FAIL' 'GAP\s+SC-01 P4'
    Expect 'a measured cell with an impossible date' (Run-Gate ($base + @('-Matrix', (Mutate 'measured-baddate' 'measured (2026-10-23; run-0001)' 'measured (2026-02-30; run-0001)')))) 1 '^coverage: FAIL' 'not a calendar date'
    Expect 'an unknown n/a tag' (Run-Gate ($base + @('-Matrix', (Mutate 'na-tag' '| n/a (face) | n/a (face) | n/a (face) | n/a (face) |' '| n/a (face) | n/a (face) | n/a (nope) | n/a (face) |')))) 1 '^coverage: FAIL' 'n/a tag ''nope'''
    Expect 'an illegal cell state' (Run-Gate ($base + @('-Matrix', (Mutate 'illegal' '| open (B) | open (B) | open (B) | open (B) | open (E) |' '| open (B) | maybe | open (B) | open (B) | open (E) |')))) 1 '^coverage: FAIL' 'GAP\s+SC-02 P2 is ''maybe'''
    Expect 'a row with four cells' (Run-Gate ($base + @('-Matrix', (Mutate 'four-cells' '| open (B) | open (B) | open (B) | open (B) | open (E) |' '| open (B) | open (B) | open (B) | open (E) |')))) 1 '^coverage: FAIL' 'carries 4 cell'
    Expect 'a catalog scene with no matrix row' (Run-Gate ($base + @('-Matrix', (Mutate 'no-row' "| SC-03 | n/a (face) | n/a (face) | n/a (face) | n/a (face) | open (E) |`n" '')))) 1 '^coverage: FAIL' 'scene SC-03 has no matrix row'
    Expect 'a matrix row with no catalog scene' (Run-Gate ($base + @('-Matrix', (Mutate 'orphan-row' "| SC-03 | n/a (face)" "| SC-09 | n/a (face) | n/a (face) | n/a (face) | n/a (face) | open (E) |`n| SC-03 | n/a (face)")))) 1 '^coverage: FAIL' 'matrix row SC-09 has no scene'

    $twoGaps = Mutate 'two-gaps-b' '| open (B) | open (B) | open (B) | open (B) | open (E) |' '| open | open | open (B) | open (B) | open (E) |'
    $r = Run-Gate ($base + @('-Matrix', $twoGaps))
    Check 'two gaps are both listed before the verdict line' ($r.Exit -eq 1 -and $r.Last -match '^coverage: FAIL \(2 gap\(s\): ' -and ([regex]::Matches($r.Output, '(?m)^GAP ')).Count -eq 2) $r.Output

    # the pin: a capability added to CLAUDE.md section 1 must not slip past the table
    $pin = (& pwsh -NoProfile -File $gate -ClaudeMd $claudeOk -PrintSection1Hash | Select-Object -Last 1).Trim()
    Check 'the pin printer prints a SHA-256' ($pin -match '^[0-9a-f]{64}$') $pin
    $pinned = Join-Path $work 'keywords-pinned.json'
    $kw = Get-Content -Raw -LiteralPath $keywords | ConvertFrom-Json -Depth 8
    $kw | Add-Member -NotePropertyName reviewedSection1Sha256 -NotePropertyValue $pin
    [System.IO.File]::WriteAllText($pinned, ($kw | ConvertTo-Json -Depth 8))
    Expect 'a matching pin passes' (Run-Gate @('-ClaudeMd', $claudeOk, '-Keywords', $pinned, '-Matrix', $specOk)) 0 '^coverage: PASS'
    $claudeGrown = Join-Path $work 'claude-grown.md'
    [System.IO.File]::WriteAllText($claudeGrown, ([System.IO.File]::ReadAllText($claudeOk).Replace('beta stream player.', 'beta stream player and a delta recorder.')))
    Expect 'a new capability in section 1 fails the pinned table' (Run-Gate @('-ClaudeMd', $claudeGrown, '-Keywords', $pinned, '-Matrix', $specOk)) 1 '^coverage: FAIL' 'changed since the keyword table was reviewed'
    $claudeDropped = Join-Path $work 'claude-dropped.md'
    [System.IO.File]::WriteAllText($claudeDropped, ([System.IO.File]::ReadAllText($claudeOk).Replace('Alpha app and a ', '')))
    Expect 'a capability dropped from section 1 is a stale table row' (Run-Gate @('-ClaudeMd', $claudeDropped, '-Keywords', $keywords, '-Matrix', $specOk)) 1 '^coverage: FAIL' 'no longer named in CLAUDE.md section 1'

    Write-Host 'exit 2 - an input that cannot be read'
    Expect 'a truncated CLAUDE.md' (Run-Gate @('-ClaudeMd', (Join-Path $fixtures 'claude-truncated.md'), '-Keywords', $keywords, '-Matrix', $specOk)) 2 '^coverage: COULD NOT VERIFY \(CLAUDE\.md section 1 is empty\)$'
    Expect 'a missing CLAUDE.md' (Run-Gate @('-ClaudeMd', (Join-Path $work 'absent.md'), '-Keywords', $keywords, '-Matrix', $specOk)) 2 '^coverage: COULD NOT VERIFY \(CLAUDE\.md missing'
    Expect 'a missing matrix file' (Run-Gate ($base + @('-Matrix', (Join-Path $work 'absent-spec.md')))) 2 '^coverage: COULD NOT VERIFY \(matrix missing'
    $noMatrix = Join-Path $work 'no-matrix.md'
    [System.IO.File]::WriteAllText($noMatrix, "# spec`n`n## 4. Scene catalog`n`n| Id | Scene | The user's action | Where the risk lives |`n| --- | --- | --- | --- |`n| SC-01 | First start | a | b |`n")
    Expect 'a matrix section that is not there' (Run-Gate ($base + @('-Matrix', $noMatrix))) 2 '^coverage: COULD NOT VERIFY \(matrix \(section 5\) not found\)$'
    $badKeywords = Join-Path $work 'keywords-bad.json'
    [System.IO.File]::WriteAllText($badKeywords, '{ "capabilities": [ ')
    Expect 'an unparsable keyword table' (Run-Gate @('-ClaudeMd', $claudeOk, '-Keywords', $badKeywords, '-Matrix', $specOk)) 2 '^coverage: COULD NOT VERIFY \(keyword table unparsable'
    Expect 'exit 2 is not exit 0 or 1 (an unreadable input never passes)' (Run-Gate @('-ClaudeMd', (Join-Path $fixtures 'claude-truncated.md'), '-Keywords', $keywords, '-Matrix', $specOk)) 2 'COULD NOT VERIFY'

    Write-Host 'the real pair'
    $real = Run-Gate @()
    Check 'the real CLAUDE.md and matrix pair is covered' ($real.Exit -eq 0 -and $real.Last -match '^coverage: PASS \(\d+ capabilities, \d+ cells\)$') "(exit $($real.Exit), last line: $($real.Last))`n$($real.Output)"
}
catch {
    $failures++
    Write-Host "  ERROR $($_.Exception.Message)" -ForegroundColor Red
}
finally {
    Remove-Item -LiteralPath $work -Recurse -Force -ErrorAction SilentlyContinue
}

if ($failures -eq 0) { Write-Host "coverage-selftest: PASS (run=$cases skipped=0)"; exit 0 }
Write-Host "coverage-selftest: FAIL (run=$cases skipped=0 failed=$failures)"
exit 1
