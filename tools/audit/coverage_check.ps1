#requires -Version 7.0
<#
.SYNOPSIS
    S0025 coverage gate: every capability of CLAUDE.md section 1 maps to a scene, every scene carries five legal cells.

.DESCRIPTION
    Reads two live inputs - CLAUDE.md section 1 and the scene catalog plus matrix of the S0025 spec - and the keyword
    table kept beside this script (coverage_keywords.json). Asserts:
      - every capability of the keyword table that CLAUDE.md section 1 names is still named there, and section 1 is the
        text the table was reviewed against (the pin), so a capability added to section 1 cannot slip past the table;
      - every capability maps to at least one scene of the catalog;
      - every catalog scene has exactly one matrix row, every matrix row a catalog scene, every row five cells;
      - every cell is a legal state: 'open (A..E)', 'n/a (<tag>)' with a declared tag, or
        'measured (YYYY-MM-DD; <run-id>)' - a date and a command pointer.
    All gaps are listed (the run does not stop at the first), then one verdict line, last on stdout.

    Exit codes (CHECK-VERDICT, rule set of the catalog):
      0  all covered:        coverage: PASS (<n> capabilities, <m> cells)
      1  a gap:              coverage: FAIL (<k> gap(s): <first gap>)
      2  an input unreadable or unparsable (CLAUDE.md, the matrix, the table):
                             coverage: COULD NOT VERIFY (<what>)

.PARAMETER ClaudeMd
    CLAUDE.md to read. Default: the repository's.
.PARAMETER Matrix
    The spec file holding the scene catalog and the matrix. Default: PLAN/S0025_*.md, else PLAN/DONE/S0025_*.md.
.PARAMETER Keywords
    The keyword table. Default: coverage_keywords.json beside this script.
.PARAMETER PrintSection1Hash
    Print the pin for the current CLAUDE.md section 1 and exit 0 (used to regenerate the table's pin after a review).
#>
[CmdletBinding(PositionalBinding = $false)]
param(
    [string] $ClaudeMd,
    [string] $Matrix,
    [string] $Keywords,
    [switch] $PrintSection1Hash
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent

class Unreadable : System.Exception {
    Unreadable([string] $m) : base($m) {}
}

function Get-Section([string[]] $Lines, [string] $HeadingPattern, [string] $What) {
    $start = -1
    for ($i = 0; $i -lt $Lines.Count; $i++) {
        if ($Lines[$i] -match $HeadingPattern) { $start = $i; break }
    }
    if ($start -lt 0) { throw [Unreadable]::new("$What not found") }
    $end = $Lines.Count
    for ($i = $start + 1; $i -lt $Lines.Count; $i++) {
        if ($Lines[$i] -match '^##\s') { $end = $i; break }
    }
    $body = @($Lines[($start + 1)..($end - 1)])
    if (($body -join '').Trim().Length -eq 0) { throw [Unreadable]::new("$What is empty") }
    return , $body
}

function Get-TableRows([string[]] $Section, [string] $HeaderStart, [string] $What) {
    $rows = [System.Collections.Generic.List[string[]]]::new()
    $inTable = $false
    foreach ($line in $Section) {
        $t = $line.Trim()
        if (-not $inTable) {
            if ($t.StartsWith('|') -and $t -match $HeaderStart) { $inTable = $true }
            continue
        }
        if (-not $t.StartsWith('|')) { break }
        if ($t -match '^\|[\s:|-]+\|?$') { continue }
        $cells = @($t.Trim('|').Split('|') | ForEach-Object { $_.Trim() })
        $rows.Add($cells)
    }
    if (-not $inTable) { throw [Unreadable]::new("$What table not found") }
    if ($rows.Count -eq 0) { throw [Unreadable]::new("$What table has no rows") }
    return , $rows
}

function Get-Sha256([string] $Text) {
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($Text)
    return ([System.BitConverter]::ToString([System.Security.Cryptography.SHA256]::HashData($bytes)) -replace '-', '').ToLowerInvariant()
}

function Read-Lines([string] $Path, [string] $What) {
    if (-not $Path -or -not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw [Unreadable]::new("$What missing ($Path)") }
    $text = [System.IO.File]::ReadAllText($Path)
    if ($text.Trim().Length -eq 0) { throw [Unreadable]::new("$What is empty") }
    return , @($text -split "\r?\n")
}

function Get-Section1Text([string] $Path) {
    $lines = Read-Lines $Path 'CLAUDE.md'
    $section = Get-Section $lines '^##\s+1\.\s' 'CLAUDE.md section 1'
    return (($section -join ' ') -replace '\s+', ' ').Trim()
}

$verdictCode = 2
$verdictLine = 'coverage: COULD NOT VERIFY (internal error before any input was read)'
$gaps = [System.Collections.Generic.List[string]]::new()

try {
    if (-not $ClaudeMd) { $ClaudeMd = Join-Path $repoRoot 'CLAUDE.md' }
    if (-not $Keywords) { $Keywords = Join-Path $PSScriptRoot 'coverage_keywords.json' }
    if (-not $Matrix) {
        $found = @(Get-ChildItem -Path (Join-Path $repoRoot 'PLAN/S0025_*.md'), (Join-Path $repoRoot 'PLAN/DONE/S0025_*.md') -File -ErrorAction SilentlyContinue)
        if ($found.Count -ge 1) { $Matrix = $found[0].FullName }
    }

    $section1 = Get-Section1Text $ClaudeMd
    if ($PrintSection1Hash) {
        Write-Host (Get-Sha256 $section1)
        exit 0
    }

    if (-not (Test-Path -LiteralPath $Keywords -PathType Leaf)) { throw [Unreadable]::new("keyword table missing ($Keywords)") }
    try { $table = Get-Content -Raw -LiteralPath $Keywords | ConvertFrom-Json -Depth 8 }
    catch { throw [Unreadable]::new("keyword table unparsable ($($_.Exception.Message))") }
    if (-not $table.capabilities -or @($table.capabilities).Count -eq 0) { throw [Unreadable]::new('keyword table has no capabilities') }

    $specLines = Read-Lines $Matrix 'matrix'
    $catalogSection = Get-Section $specLines '^##\s+4\.\s' 'scene catalog (section 4)'
    $matrixSection = Get-Section $specLines '^##\s+5\.\s' 'matrix (section 5)'
    $sceneRows = Get-TableRows $catalogSection '^\|\s*Id\s*\|' 'scene catalog'
    $matrixRows = Get-TableRows $matrixSection '^\|\s*Scene\s*\|' 'matrix'
    $properties = @('P1', 'P2', 'P3', 'P4', 'P5')

    # --- the pin and the capability nouns against CLAUDE.md section 1
    if ($table.reviewedSection1Sha256 -and (Get-Sha256 $section1) -ne [string]$table.reviewedSection1Sha256) {
        $gaps.Add('CLAUDE.md section 1 changed since the keyword table was reviewed (re-review the table, then regenerate the pin with -PrintSection1Hash)')
    }
    foreach ($cap in $table.capabilities) {
        if ($cap.claudeMd -and $section1 -notmatch "(?i)$($cap.claudeMd)") {
            $gaps.Add("capability '$($cap.noun)' is no longer named in CLAUDE.md section 1 (the table row is stale)")
        }
    }

    # --- scenes: ids, text
    $sceneText = @{}
    foreach ($row in $sceneRows) {
        if ($row.Count -lt 4) { $gaps.Add("scene catalog row '$($row -join ' | ')' has fewer than 4 columns"); continue }
        if ($row[0] -notmatch '^SC-\d{2}$') { $gaps.Add("scene catalog id '$($row[0])' is not SC-nn"); continue }
        if ($sceneText.ContainsKey($row[0])) { $gaps.Add("scene $($row[0]) is listed twice in the catalog"); continue }
        $sceneText[$row[0]] = (($row[1..3] -join ' ') -replace '\s+', ' ').ToLowerInvariant()
    }

    # --- capabilities -> scenes
    foreach ($cap in $table.capabilities) {
        $covered = $false
        foreach ($alt in $cap.scenes) {
            foreach ($id in $sceneText.Keys) {
                $hit = $true
                foreach ($token in $alt) { if (-not $sceneText[$id].Contains(([string]$token).ToLowerInvariant())) { $hit = $false; break } }
                if ($hit) { $covered = $true; break }
            }
            if ($covered) { break }
        }
        if (-not $covered) { $gaps.Add("capability '$($cap.noun)' maps to no scene") }
    }

    # --- matrix rows and cells
    $legalNa = @($table.legalNaTags)
    $matrixIds = @{}
    $cellCount = 0
    foreach ($row in $matrixRows) {
        $id = $row[0]
        if ($id -notmatch '^SC-\d{2}$') { $gaps.Add("matrix row id '$id' is not SC-nn"); continue }
        if ($matrixIds.ContainsKey($id)) { $gaps.Add("matrix has two rows for $id"); continue }
        $matrixIds[$id] = $true
        if (-not $sceneText.ContainsKey($id)) { $gaps.Add("matrix row $id has no scene in the catalog") }
        if ($row.Count -ne 1 + $properties.Count) {
            $gaps.Add("$id carries $($row.Count - 1) cell(s), needs $($properties.Count) (P1..P5)")
            continue
        }
        for ($p = 0; $p -lt $properties.Count; $p++) {
            $cell = $row[$p + 1]
            $name = "$id $($properties[$p])"
            $cellCount++
            if ($cell -match '^open \(([A-E])\)$') { continue }
            if ($cell -match '^open$|^open\b') { $gaps.Add("$name is '$cell': an open cell names its phase, 'open (A..E)'"); continue }
            if ($cell -match '^n/a \(([a-z0-9-]+)\)$') {
                if ($legalNa -notcontains $Matches[1]) { $gaps.Add("${name}: n/a tag '$($Matches[1])' is not declared in the keyword table") }
                continue
            }
            if ($cell -match '^measured\b') {
                if ($cell -notmatch '^measured \((\d{4}-\d{2}-\d{2}); ([A-Za-z0-9._/-]{3,})\)$') {
                    $gaps.Add("$name is '$cell': a measured cell carries a date and a command pointer, 'measured (YYYY-MM-DD; <run-id>)'")
                    continue
                }
                $date = [datetime]::MinValue
                if (-not [datetime]::TryParseExact($Matches[1], 'yyyy-MM-dd', [cultureinfo]::InvariantCulture, 'None', [ref]$date)) {
                    $gaps.Add("${name}: '$($Matches[1])' is not a calendar date")
                }
                continue
            }
            $gaps.Add("$name is '$cell': not a legal cell state (open (A..E) | n/a (<tag>) | measured (YYYY-MM-DD; <run-id>))")
        }
    }
    foreach ($id in ($sceneText.Keys | Sort-Object)) {
        if (-not $matrixIds.ContainsKey($id)) { $gaps.Add("scene $id has no matrix row") }
    }

    if ($gaps.Count -eq 0) {
        $verdictCode = 0
        $verdictLine = "coverage: PASS ($(@($table.capabilities).Count) capabilities, $cellCount cells)"
    }
    else {
        $verdictCode = 1
        foreach ($g in $gaps) { Write-Host "GAP  $g" }
        $verdictLine = "coverage: FAIL ($($gaps.Count) gap(s): $($gaps[0]))"
    }
}
catch [Unreadable] {
    $verdictCode = 2
    Write-Host "UNREADABLE  $($_.Exception.Message)"
    $verdictLine = "coverage: COULD NOT VERIFY ($($_.Exception.Message))"
}
catch {
    $verdictCode = 2
    Write-Host "ERROR  $($_.Exception.Message)"
    $verdictLine = "coverage: COULD NOT VERIFY (internal error: $($_.Exception.Message))"
}

Write-Host $verdictLine
exit $verdictCode
