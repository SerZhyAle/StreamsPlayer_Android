#requires -Version 7.0
<#
.SYNOPSIS
    Runs every self-test of the S0025 audit tooling and the coverage gate on the real pair, in the order a session needs them.

.DESCRIPTION
    1. test-coverage-check.ps1 - fixtures force every exit code of the coverage gate.
    2. rig.py selftest         - the fixture suite of the rig; with -Serial also the live synthetic-subject checks on a device.
    3. coverage_check.ps1      - the gate on the real CLAUDE.md and matrix pair, trusted only after step 1 passed.
    Every step runs (the run does not stop at the first failure). Each step's own verdict line is quoted before the
    aggregate, whose word follows the aggregator precedence of CHECK-VERDICT rule 2.14: FAIL, then COULD NOT VERIFY,
    then PASS. A step exit of 2 or 4 (a lock queue, converted) reads as could not verify; any other non-zero exit,
    one outside 0..4 included, reads as FAIL. The last line is the verdict line.

    Exit codes: 0 every step passed; 1 a step failed; 2 a step could not verify (a tool, a device or an input was missing).

.PARAMETER Serial
    adb serial of the device for the live instrument checks. Without it the live checks are not run, and the verdict
    line says so.
#>
[CmdletBinding(PositionalBinding = $false)]
param([string] $Serial)

$ErrorActionPreference = 'Stop'
$here = $PSScriptRoot
# python.exe may be absent from PATH with only the Windows launcher installed (seen 2026-10-24 on this host).
$python = (Get-Command python -ErrorAction SilentlyContinue)?.Source
$pythonPreArgs = @()
if (-not $python) {
    $launcher = (Get-Command py -ErrorAction SilentlyContinue)?.Source
    if ($launcher) { $python = $launcher; $pythonPreArgs = @('-3') }
}
$steps = @(
    @{ Name = 'coverage-selftest'; Run = { & pwsh -NoProfile -File (Join-Path $here 'test-coverage-check.ps1') 2>&1 } },
    @{ Name = 'rig-selftest'; Run = {
            if (-not $python) { Write-Output 'neither python nor py is on PATH'; $global:LASTEXITCODE = 2; return }
            $arguments = $pythonPreArgs + @((Join-Path $here 'rig.py'), 'selftest')
            if ($Serial) { $arguments += @('--serial', $Serial) }
            & $python @arguments 2>&1 } },
    @{ Name = 'coverage'; Run = { & pwsh -NoProfile -File (Join-Path $here 'coverage_check.ps1') 2>&1 } }
)

$results = @()
foreach ($step in $steps) {
    $output = & $step.Run | Out-String
    $code = $LASTEXITCODE
    $last = (($output -split "\r?\n") | Where-Object { $_.Trim() } | Select-Object -Last 1)
    Write-Host "[$($step.Name)] exit $code - $last"
    $results += [pscustomobject]@{ Name = $step.Name; Code = $code; Last = $last }
}

$unverified = @($results | Where-Object { $_.Code -eq 2 -or $_.Code -eq 4 })
$failed = @($results | Where-Object { $_.Code -ne 0 -and $_.Code -ne 2 -and $_.Code -ne 4 })
$live = if ($Serial) { "live checks on $Serial" } else { 'live checks not run' }
if ($failed.Count -gt 0) {
    Write-Host "audit-selftests: FAIL ($($failed.Name -join ', '))"
    exit 1
}
if ($unverified.Count -gt 0) {
    Write-Host "audit-selftests: COULD NOT VERIFY ($($unverified.Name -join ', '))"
    exit 2
}
Write-Host "audit-selftests: PASS ($($results.Count) steps, $live)"
exit 0
