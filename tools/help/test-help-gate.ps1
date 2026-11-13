#requires -Version 7.0
<#
.SYNOPSIS
    Self-test of tools/help/assert-help-map.ps1: a gate that has not been seen to fail is itself unverified.

.DESCRIPTION
    Copies the parts of the tree the gate reads into a throw-away folder, runs the gate on the unmodified copy
    (it must pass), then breaks the copy one way at a time and requires the gate to fail with exactly the finding
    kind that way is meant to raise (CONTEXT-HELP rule 19), or to report `COULD NOT VERIFY` where an input is
    missing. Touches neither the repository nor a device.

.EXAMPLE
    pwsh -NoProfile -File tools/help/test-help-gate.ps1

.NOTES
    Exit codes (CHECK-VERDICT): 0 every case behaved; 1 a case did not; 2 the fixture could not be built.
    The last line on stdout is the verdict line: `help-gate-selftest: PASS (run=<n> skipped=0)` or `FAIL (<k>)`; the
    names of the failing cases are printed above it.
#>
[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$gate = Join-Path $PSScriptRoot 'assert-help-map.ps1'
$fixture = Join-Path ([IO.Path]::GetTempPath()) ("help-gate-selftest-" + [Guid]::NewGuid().ToString('N'))

function Copy-Part([string]$relative) {
    $source = Join-Path $repoRoot $relative
    $target = Join-Path $fixture $relative
    $null = New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target)
    Copy-Item -LiteralPath $source -Destination $target -Recurse -Force
}

try {
    foreach ($part in @('phone/src/main/AndroidManifest.xml', 'phone/src/main/java', 'phone/src/main/res/layout',
            'phone/src/main/res/layout-land', 'phone/src/main/res/menu', 'phone/src/main/assets/help', 'docs/help/content',
            'wear/src/main/java', 'tools/help/help-map-baseline.txt')) {
        Copy-Part $part
    }
} catch {
    Write-Host "help-gate-selftest: COULD NOT VERIFY (fixture: $($_.Exception.Message))"
    exit 2
}

$mapPath = Join-Path $fixture 'phone/src/main/assets/help/help-map.json'
$baselinePath = Join-Path $fixture 'tools/help/help-map-baseline.txt'
$pagePath = Join-Path $fixture 'phone/src/main/assets/help/ru/player.html'
$originalMap = Get-Content -LiteralPath $mapPath -Raw -Encoding utf8
$originalBaseline = Get-Content -LiteralPath $baselinePath -Raw -Encoding utf8
$originalPage = Get-Content -LiteralPath $pagePath -Raw -Encoding utf8
$layoutPath = Join-Path $fixture 'phone/src/main/res/layout/sheet_stream_filter.xml'
$originalLayout = [IO.File]::ReadAllText($layoutPath)
$capturePath = Join-Path $fixture 'phone/src/main/assets/help/img/capture.json'
$originalCapture = [IO.File]::ReadAllText($capturePath)
$ghostShot = Join-Path $fixture 'phone/src/main/assets/help/img/ghost-en-light.jpg'
# The committed copy of the baseline (CHECK-BASELINE 0.12 item B.1): the fixture is not a repository, so the gate is
# given this file; a case that edits the baseline and commits the edit edits this copy too.
$committedPath = "$fixture-committed-baseline.txt"
$script:committedUsed = $committedPath

function Reset-Fixture {
    [IO.File]::WriteAllText($mapPath, $originalMap, [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText($baselinePath, $originalBaseline, [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText($committedPath, $originalBaseline, [Text.UTF8Encoding]::new($false))
    $script:committedUsed = $committedPath
    [IO.File]::WriteAllText($pagePath, $originalPage, [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText($layoutPath, $originalLayout, [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText($capturePath, $originalCapture, [Text.UTF8Encoding]::new($false))
    if (Test-Path -LiteralPath $ghostShot) { Remove-Item -LiteralPath $ghostShot }
    $removed = Join-Path $fixture 'phone/src/main/assets/help/en/qr-scanner.html'
    if (-not (Test-Path -LiteralPath $removed)) {
        Copy-Item -LiteralPath (Join-Path $repoRoot 'phone/src/main/assets/help/en/qr-scanner.html') -Destination $removed
    }
}

function Invoke-Gate {
    $output = & pwsh -NoProfile -File $gate -Root $fixture -CommittedBaseline $script:committedUsed 2>&1 | Out-String
    return [pscustomobject]@{ Code = $LASTEXITCODE; Output = $output }
}

$failures = [System.Collections.Generic.List[string]]::new()
$cases = 0
function Assert-Case([string]$name, [int]$code, [string]$kind, [scriptblock]$break) {
    $script:cases++
    Reset-Fixture
    if ($break) { & $break }
    $run = Invoke-Gate
    $lastLine = ($run.Output -split "\r?\n" | Where-Object { $_.Trim() } | Select-Object -Last 1)
    $ok = ($run.Code -eq $code) -and $lastLine -match '^assert-help-map: (PASS|FAIL|COULD NOT VERIFY)' -and
        ($(if ($kind) { $run.Output -match "(?m)^\s+FAIL $kind\b" } else { $true }))
    if ($ok) { Write-Host "ok   $name" } else {
        Write-Host "FAIL $name (exit $($run.Code), wanted $code $kind)"
        $script:failures.Add($name)
        $run.Output -split "\r?\n" | Select-Object -Last 6 | ForEach-Object { Write-Host "     $_" }
    }
}

try {
    Assert-Case 'the unmodified copy passes' 0 '' $null
    Assert-Case 'a surface with no row is UNMAPPED' 1 'UNMAPPED' {
        $m = $originalMap -replace '(?m)^\s*\{ "id": "QrScannerActivity"[^\r\n]*\r?\n', ''
        [IO.File]::WriteAllText($mapPath, $m, [Text.UTF8Encoding]::new($false))
    }
    Assert-Case 'a row with no surface is NO-SURFACE' 1 'NO-SURFACE' {
        $m = $originalMap.Replace('"surfaces": [', '"surfaces": [' + "`n" + '    { "id": "GhostActivity", "name": "Ghost", "form": "none", "reason": "test" },')
        [IO.File]::WriteAllText($mapPath, $m, [Text.UTF8Encoding]::new($false))
    }
    Assert-Case 'a none row without a reason is NO-REASON' 1 'NO-REASON' {
        $m = $originalMap -replace '(\{ "id": "BroadcastShareActivity", "name": "Broadcast share", "form": "none",\s*)"reason": "[^"]*"', '$1"x": 1'
        [IO.File]::WriteAllText($mapPath, $m, [Text.UTF8Encoding]::new($false))
    }
    Assert-Case 'an inherit row naming no parent is NO-PARENT' 1 'NO-PARENT' {
        $m = $originalMap.Replace('"parent": "MainActivity", "anchor": "recent"', '"parent": "NoSuchRow", "anchor": "recent"')
        [IO.File]::WriteAllText($mapPath, $m, [Text.UTF8Encoding]::new($false))
    }
    Assert-Case 'a page missing in one language is MISSING-PAGE' 1 'MISSING-PAGE' {
        Remove-Item -LiteralPath (Join-Path $fixture 'phone/src/main/assets/help/en/qr-scanner.html')
    }
    Assert-Case 'an anchor the parent page lacks is BAD-ANCHOR' 1 'BAD-ANCHOR' {
        $m = $originalMap.Replace('"anchor": "recent"', '"anchor": "no-such-entry"')
        [IO.File]::WriteAllText($mapPath, $m, [Text.UTF8Encoding]::new($false))
    }
    Assert-Case 'a script in a page is SCRIPT' 1 'SCRIPT' {
        [IO.File]::WriteAllText($pagePath, $originalPage.Replace('</main>', '<script>1</script></main>'), [Text.UTF8Encoding]::new($false))
    }
    Assert-Case 'a remote image is REMOTE-URL' 1 'REMOTE-URL' {
        [IO.File]::WriteAllText($pagePath, $originalPage.Replace('</main>', '<img src="https://example.invalid/x.png" alt="x"></main>'), [Text.UTF8Encoding]::new($false))
    }
    Assert-Case 'a dead local link is BROKEN-LINK' 1 'BROKEN-LINK' {
        [IO.File]::WriteAllText($pagePath, $originalPage.Replace('</main>', '<a href="gone.html">x</a></main>'), [Text.UTF8Encoding]::new($false))
    }
    Assert-Case 'an image without alt text is PAGE-SHAPE' 1 'PAGE-SHAPE' {
        [IO.File]::WriteAllText($pagePath, $originalPage.Replace('</main>', '<img src="../help.css"></main>'), [Text.UTF8Encoding]::new($false))
    }
    Assert-Case 'a baseline line without a reason is NO-BASELINE-REASON' 1 'NO-BASELINE-REASON' {
        [IO.File]::WriteAllText($baselinePath, $originalBaseline + "ExchangeActivity2`n", [Text.UTF8Encoding]::new($false))
    }
    Assert-Case 'a baseline line of a mapped surface is STALE-BASELINE' 1 'STALE-BASELINE' {
        [IO.File]::WriteAllText($baselinePath, $originalBaseline + "MainActivity   # S0000: stale`n", [Text.UTF8Encoding]::new($false))
    }
    Assert-Case 'a layout changed after its capture is STALE-SHOT' 1 'STALE-SHOT' {
        [IO.File]::WriteAllText($layoutPath, $originalLayout.Replace('</androidx.core.widget.NestedScrollView>', "<!-- moved -->`n</androidx.core.widget.NestedScrollView>"), [Text.UTF8Encoding]::new($false))
    }
    Assert-Case 'a string or drawable changed after the capture is STALE-SHOT' 1 'STALE-SHOT' {
        $capture = Get-Content -LiteralPath $capturePath -Raw -Encoding utf8 | ConvertFrom-Json
        $capture.pages.'filter-and-sort' | Add-Member -NotePropertyName visibleSha256 -NotePropertyValue 'recorded-before-the-change' -Force
        [IO.File]::WriteAllText($capturePath, ($capture | ConvertTo-Json -Depth 6), [Text.UTF8Encoding]::new($false))
    }
    Assert-Case 'a picture set with no capture entry is STALE-SHOT' 1 'STALE-SHOT' {
        Copy-Item -LiteralPath (Join-Path $fixture 'phone/src/main/assets/help/img/catalog-en-light.jpg') -Destination $ghostShot
    }
    Assert-Case 'a stale picture set accepted by a committed baseline line passes' 0 '' {
        [IO.File]::WriteAllText($layoutPath, $originalLayout.Replace('</androidx.core.widget.NestedScrollView>', "<!-- moved -->`n</androidx.core.widget.NestedScrollView>"), [Text.UTF8Encoding]::new($false))
        $accepted = $originalBaseline + "shot:filter-and-sort   # S0000: recaptured later`n"
        [IO.File]::WriteAllText($baselinePath, $accepted, [Text.UTF8Encoding]::new($false))
        [IO.File]::WriteAllText($committedPath, $accepted, [Text.UTF8Encoding]::new($false))
    }
    Assert-Case 'a baseline line added without a commit is BASELINE-RISE' 1 'BASELINE-RISE' {
        [IO.File]::WriteAllText($baselinePath, $originalBaseline + "ghost-surface   # S0000: absorbed without a commit`n", [Text.UTF8Encoding]::new($false))
    }
    Assert-Case 'a committed copy that is not there is COULD NOT VERIFY' 2 '' {
        $script:committedUsed = Join-Path $fixture 'no-such-committed-baseline.txt'
    }
    Assert-Case 'a baseline line of a fresh picture set is STALE-BASELINE' 1 'STALE-BASELINE' {
        [IO.File]::WriteAllText($baselinePath, $originalBaseline + "shot:catalog   # S0000: stale`n", [Text.UTF8Encoding]::new($false))
    }
    Assert-Case 'a missing help map is COULD NOT VERIFY' 2 '' {
        Remove-Item -LiteralPath $mapPath
    }
} finally {
    Remove-Item -LiteralPath $fixture -Recurse -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $committedPath -Force -ErrorAction SilentlyContinue
}

if ($failures.Count -gt 0) {
    Write-Host "help-gate-selftest: FAIL ($($failures.Count))"
    exit 1
}
Write-Host "help-gate-selftest: PASS (run=$cases skipped=0)"
exit 0
