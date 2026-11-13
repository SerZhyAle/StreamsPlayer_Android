#requires -Version 7.0
<#
.SYNOPSIS
    The gate of CONTEXT-HELP rule 19 for this repository: every surface has a help-map row, every page row has its
    pages in every authored language, every link resolves, and the debt that is accepted only falls.

.DESCRIPTION
    Ported from FastMediaSorter Android scripts/quality/assert-help-map.ps1 (S0035 item 5) and adapted: no
    fixed-input attribution (every finding is charged to the run, CHECK-VERDICT rule 8), the verdict line and exit
    codes follow CHECK-VERDICT 0.13, and the baseline follows CHECK-BASELINE 0.11 as a set baseline.

    Enumerates the surfaces through Get-HelpSurfaces / Get-WearHelpSurfaces in tools/help/list-help-surfaces.ps1 (the
    one enumeration) and judges phone/src/main/assets/help/help-map.json against it. Finding kinds:
      UNMAPPED       - an enumerated surface with no map row and no line in tools/help/help-map-baseline.txt;
      NO-SURFACE     - a map row whose id is no enumerated surface;
      NO-REASON      - an `inherit` or `none` row without `reason`;
      NO-PARENT      - an `inherit` row whose `parent` names no map row;
      BAD-FORM       - a row whose `form` is not page, inherit or none, or a `page` row without `help`;
      MISSING-PAGE   - a help id of a `page` row (or of a page it lists, or the map's index) with no
                       <locale>/<help>.html for en, ru or uk under phone/src/main/assets/help/;
      BAD-ANCHOR     - an `inherit` row whose `anchor` is no element id of its parent's page in some locale;
      BROKEN-LINK    - a local href or src in a page that resolves to no file, or to a fragment the target lacks;
      SCRIPT         - a `<script` element in a page (rule 12, rung 1);
      REMOTE-URL     - an http(s) URL a page would load: in a src/srcset/poster attribute, in an href of any
                       element other than <a>, or in a CSS url(); an <a href> is a link the user leaves by and is allowed;
      PAGE-SHAPE     - a page missing lang, dir, the no-referrer meta, an h1, the order list or note, the related
                       list or the footer (rule 8), or an <img> without alt text;
      STALE-SHOT     - rung 2, freshness: a help id with pictures under img/ that has no entry in img/capture.json,
                       or whose entry's fingerprint (tools/help/help-shot-inputs.ps1: the layout and menu files the
                       capture recipe named and the page's marker list; since S0051 also `visibleSha256`, the strings
                       and drawables they show) differs from the tree's now - the screen changed after the picture was
                       taken; run capture-help-screenshots.ps1 -Help <id>;
      NO-BASELINE-REASON - a baseline line without `# <ticket>: <reason>` (CHECK-BASELINE rule 6);
      STALE-BASELINE - a baseline line whose surface now has a map row or is no longer enumerated, or a
                       `shot:<help-id>` line whose pictures are fresh again, so the debt only falls
                       (CHECK-BASELINE rule 2).
      BASELINE-RISE  - an identifier in the baseline that the committed copy lacks: a line added in the same
                       change would absorb a finding without a commit (CHECK-BASELINE 0.12 item B.1).
    The watch app is judged in the same run: its surfaces are the composable(..) routes, enumerated as `wear:<route>`.
    A `wear:` row in the map is judged like a phone row; a route without a row sits in the baseline.

.PARAMETER Root
    Tree to judge. Defaults to the repository root; the self-test passes a fixture tree.

.PARAMETER CommittedBaseline
    The committed copy of the baseline the comparison reads. Defaults to `git show HEAD:tools/help/help-map-baseline.txt`
    in the judged tree; the self-test passes a file, because its fixture is not a repository.

.EXAMPLE
    pwsh -NoProfile -File tools/help/assert-help-map.ps1

.NOTES
    Exit codes (CHECK-VERDICT):
      0 - the map covers every surface and every page row is complete.
      1 - at least one finding.
      2 - could not verify: the map, the baseline, the enumerator or a surface source is missing or unreadable, or the
          committed copy of the baseline is not readable (the file is not in HEAD yet).
    The last line on stdout is the verdict line: `assert-help-map: PASS (..)`, `FAIL (n finding(s))` or
    `COULD NOT VERIFY (..)`. Accepted debt is printed above it, one line per surface, never hidden.
#>
[CmdletBinding()]
param([string]$Root, [string]$CommittedBaseline)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$locales = @('en', 'ru', 'uk')

function Stop-CannotVerify([string]$reason) {
    Write-Host "assert-help-map: COULD NOT VERIFY ($reason)"
    exit 2
}

if (-not $Root) { $Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot) }
if (-not (Test-Path -LiteralPath $Root -PathType Container)) { Stop-CannotVerify "root not found: $Root" }
$Root = (Resolve-Path -LiteralPath $Root).Path

# The enumerator is the gate's own code, not an input of the judged tree, so it is loaded from this repository
# even when -Root points at a fixture.
$enumerator = Join-Path $PSScriptRoot 'list-help-surfaces.ps1'
if (-not (Test-Path -LiteralPath $enumerator -PathType Leaf)) { Stop-CannotVerify "enumerator not found: $enumerator" }
. $enumerator

$helpRoot = Join-Path $Root 'phone/src/main/assets/help'
$mapPath = Join-Path $helpRoot 'help-map.json'
$baselinePath = Join-Path $Root 'tools/help/help-map-baseline.txt'
if (-not (Test-Path -LiteralPath $mapPath -PathType Leaf)) { Stop-CannotVerify "help map not found: $mapPath" }
if (-not (Test-Path -LiteralPath $baselinePath -PathType Leaf)) { Stop-CannotVerify "baseline not found: $baselinePath" }

try { $map = Get-Content -LiteralPath $mapPath -Raw -Encoding utf8 | ConvertFrom-Json } catch { Stop-CannotVerify "help map is not JSON: $($_.Exception.Message)" }
try { $phoneSurfaces = @(Get-HelpSurfaces -Root $Root) } catch { Stop-CannotVerify $_.Exception.Message }
if ($phoneSurfaces.Count -eq 0) { Stop-CannotVerify 'the enumerator found no surface' }
$wearSurfaces = @()
if (Test-Path -LiteralPath (Join-Path $Root 'wear/src/main/java') -PathType Container) {
    try { $wearSurfaces = @(Get-WearHelpSurfaces -Root $Root) } catch { Stop-CannotVerify $_.Exception.Message }
    if ($wearSurfaces.Count -eq 0) { Stop-CannotVerify 'the enumerator found no watch route' }
}
$surfaces = @($phoneSurfaces) + @($wearSurfaces)

$findings = [System.Collections.Generic.List[string]]::new()
function Add-Finding([string]$kind, [string]$message) {
    $script:findings.Add(('{0,-18} {1}' -f $kind, $message))
}

function Get-Field($object, [string]$name) {
    if ($null -eq $object) { return $null }
    $prop = $object.PSObject.Properties[$name]
    if ($prop) { return $prop.Value }
    return $null
}

# Baseline: one surface id per line, `id  # TICKET: reason`; blank lines and lines starting with # are comments.
$baseline = [ordered]@{}
foreach ($raw in Get-Content -LiteralPath $baselinePath -Encoding utf8) {
    $line = $raw.Trim()
    if (-not $line -or $line.StartsWith('#')) { continue }
    $parts = $line -split '#', 2
    $id = $parts[0].Trim()
    $reason = if ($parts.Count -gt 1) { $parts[1].Trim() } else { '' }
    if ($reason -notmatch '^S\d{4}\b.+') { Add-Finding 'NO-BASELINE-REASON' "'$id' is accepted debt without '# S0000: reason' (CHECK-BASELINE rule 6)" }
    $baseline[$id] = $reason
}

# CHECK-BASELINE 0.12 item B.1: the committed copy is the snapshot. The working file is recomputed from the tree,
# which cannot see a line added in the same change; only the committed copy shows that line as a rise.
if ($CommittedBaseline) {
    if (-not (Test-Path -LiteralPath $CommittedBaseline -PathType Leaf)) { Stop-CannotVerify "committed baseline not found: $CommittedBaseline" }
    $committedLines = @(Get-Content -LiteralPath $CommittedBaseline -Encoding utf8)
} else {
    try { $committedLines = @(& git -C $Root show 'HEAD:tools/help/help-map-baseline.txt' 2>$null) } catch { Stop-CannotVerify "git is not usable: $($_.Exception.Message)" }
    if ($LASTEXITCODE -ne 0) { Stop-CannotVerify 'no committed copy of tools/help/help-map-baseline.txt at HEAD - the comparison needs the first commit that carries the file' }
}
$committedIds = [System.Collections.Generic.HashSet[string]]::new()
foreach ($raw in $committedLines) {
    $line = $raw.Trim()
    if (-not $line -or $line.StartsWith('#')) { continue }
    $null = $committedIds.Add(($line -split '#', 2)[0].Trim())
}
foreach ($id in $baseline.Keys) {
    if (-not $committedIds.Contains($id)) { Add-Finding 'BASELINE-RISE' "'$id' is not in the committed baseline - commit its line with its reason to accept it (CHECK-BASELINE 0.12 item B.2)" }
}

$rows = @($map.surfaces)
$surfaceIds = [System.Collections.Generic.HashSet[string]]::new([string[]]@($surfaces | ForEach-Object { [string]$_.id }))
$rowIds = [System.Collections.Generic.HashSet[string]]::new()
foreach ($row in $rows) { $null = $rowIds.Add([string](Get-Field $row 'id')) }

foreach ($s in $surfaces) {
    if (-not $rowIds.Contains([string]$s.id) -and -not $baseline.Contains([string]$s.id)) {
        Add-Finding 'UNMAPPED' "$($s.id) ($($s.source)): add a help-map row - page, inherit with parent and reason, or none with reason"
    }
}

$helpIds = [System.Collections.Generic.List[string]]::new()
$rowsById = @{}
foreach ($row in $rows) { $rowsById[[string](Get-Field $row 'id')] = $row }
foreach ($row in $rows) {
    $id = [string](Get-Field $row 'id')
    $form = [string](Get-Field $row 'form')
    if (-not $surfaceIds.Contains($id)) { Add-Finding 'NO-SURFACE' "row '$id' names no surface of the manifest, the dialog classes, the dialog layouts or the watch routes" }
    switch ($form) {
        'page' {
            $rowHelp = [string](Get-Field $row 'help')
            if (-not $rowHelp) { Add-Finding 'BAD-FORM' "row '$id' is a page without help" } elseif (-not $helpIds.Contains($rowHelp)) { $helpIds.Add($rowHelp) }
            foreach ($sub in @(Get-Field $row 'pages')) {
                $subHelp = [string](Get-Field $sub 'help')
                if ($subHelp -and -not $helpIds.Contains($subHelp)) { $helpIds.Add($subHelp) }
            }
        }
        'inherit' {
            if (-not [string](Get-Field $row 'reason')) { Add-Finding 'NO-REASON' "row '$id' inherits without a reason" }
            $parent = [string](Get-Field $row 'parent')
            if (-not $parent -or -not $rowIds.Contains($parent)) { Add-Finding 'NO-PARENT' "row '$id' inherits from '$parent', which is no map row" }
        }
        'none' {
            if (-not [string](Get-Field $row 'reason')) { Add-Finding 'NO-REASON' "row '$id' has no help and no reason" }
        }
        default { Add-Finding 'BAD-FORM' "row '$id' has form '$form'; expected page, inherit or none" }
    }
}
$indexId = [string](Get-Field $map 'index')
if ($indexId -and -not $helpIds.Contains($indexId)) { $helpIds.Add($indexId) }

foreach ($entry in $baseline.Keys) {
    if ($entry.StartsWith('shot:')) { continue }
    if ($rowIds.Contains($entry)) { Add-Finding 'STALE-BASELINE' "$entry has a map row now - delete its baseline line" }
    elseif (-not $surfaceIds.Contains($entry)) { Add-Finding 'STALE-BASELINE' "$entry is no longer a surface - delete its baseline line" }
}

foreach ($pageHelp in $helpIds) {
    foreach ($locale in $locales) {
        $page = Join-Path $helpRoot "$locale/$pageHelp.html"
        if (-not (Test-Path -LiteralPath $page -PathType Leaf)) { Add-Finding 'MISSING-PAGE' "$locale/$pageHelp.html (run tools/help/generate-help-pages.ps1)" }
    }
}

$pageFiles = @()
if (Test-Path -LiteralPath $helpRoot -PathType Container) {
    $pageFiles = @(Get-ChildItem -LiteralPath $helpRoot -Recurse -File -Filter '*.html')
}
$idCache = @{}
function Get-PageIds([string]$path) {
    if (-not $idCache.ContainsKey($path)) {
        $text = Get-Content -LiteralPath $path -Raw -Encoding utf8
        $idCache[$path] = [System.Collections.Generic.HashSet[string]]::new([string[]]@([regex]::Matches($text, '\bid="([^"]+)"') | ForEach-Object { $_.Groups[1].Value }))
    }
    return $idCache[$path]
}

# An inherit row's anchor must be an element of the parent's page in every locale (rule 3: F1 lands on the entry).
foreach ($row in $rows) {
    if ([string](Get-Field $row 'form') -ne 'inherit') { continue }
    $anchor = [string](Get-Field $row 'anchor')
    if (-not $anchor) { continue }
    $cursor = $row
    $seen = @{}
    while ($cursor -and [string](Get-Field $cursor 'form') -eq 'inherit' -and -not $seen.ContainsKey([string](Get-Field $cursor 'id'))) {
        $seen[[string](Get-Field $cursor 'id')] = $true
        $cursor = $rowsById[[string](Get-Field $cursor 'parent')]
    }
    $owner = if ($cursor -and [string](Get-Field $cursor 'form') -eq 'page') { [string](Get-Field $cursor 'help') } else { '' }
    if (-not $owner) { continue }
    foreach ($locale in $locales) {
        $page = Join-Path $helpRoot "$locale/$owner.html"
        if ((Test-Path -LiteralPath $page -PathType Leaf) -and -not (Get-PageIds $page).Contains($anchor)) {
            Add-Finding 'BAD-ANCHOR' "row '$([string](Get-Field $row 'id'))': anchor '$anchor' is no element of $locale/$owner.html"
        }
    }
}

foreach ($file in $pageFiles) {
    $rel = [System.IO.Path]::GetRelativePath($Root, $file.FullName).Replace('\', '/')
    $html = Get-Content -LiteralPath $file.FullName -Raw -Encoding utf8
    if ($html -match '(?i)<script\b') { Add-Finding 'SCRIPT' "${rel}: a page runs no script (contract rule 12)" }
    foreach ($m in [regex]::Matches($html, '(?i)<(\w+)\b[^>]*?\b(src|srcset|poster|href)\s*=\s*"(https?://[^"]*)"')) {
        $tag = $m.Groups[1].Value.ToLowerInvariant()
        $attr = $m.Groups[2].Value.ToLowerInvariant()
        if ($tag -eq 'a' -and $attr -eq 'href') { continue }
        Add-Finding 'REMOTE-URL' "${rel}: <$tag $attr> loads $($m.Groups[3].Value); a page loads nothing from the network"
    }
    foreach ($m in [regex]::Matches($html, '(?i)url\(\s*[''"]?(https?://[^)''"]+)')) {
        Add-Finding 'REMOTE-URL' "${rel}: CSS url() loads $($m.Groups[1].Value)"
    }
    foreach ($m in [regex]::Matches($html, '(?i)\b(?:href|src)\s*=\s*"([^"]*)"')) {
        $target = $m.Groups[1].Value
        if (-not $target -or $target -match '^(?i)(https?:|mailto:|tel:|data:|intent:|javascript:)') { continue }
        $pathPart = ($target -split '[?#]', 2)[0]
        $fragment = if ($target.Contains('#')) { $target.Substring($target.IndexOf('#') + 1) } else { '' }
        $resolved = if ($pathPart) { [System.IO.Path]::GetFullPath((Join-Path $file.DirectoryName ([uri]::UnescapeDataString($pathPart)))) } else { $file.FullName }
        if (-not (Test-Path -LiteralPath $resolved -PathType Leaf)) {
            Add-Finding 'BROKEN-LINK' "${rel}: '$target' resolves to no file"
            continue
        }
        if ($fragment -and $resolved.EndsWith('.html') -and -not (Get-PageIds $resolved).Contains($fragment)) {
            Add-Finding 'BROKEN-LINK' "${rel}: '$target' names a fragment the target page lacks"
        }
    }
    # Rule 8 / rule 12 / rule 13: the shape every page has.
    $shape = [System.Collections.Generic.List[string]]::new()
    if ($html -notmatch '<html lang="[a-z]{2,3}(-[A-Za-z0-9]+)*" dir="(ltr|rtl)"') { $shape.Add('lang and dir on <html>') }
    if ($html -notmatch '<meta name="referrer" content="no-referrer">') { $shape.Add('the no-referrer meta') }
    if ($html -notmatch '<h1>') { $shape.Add('the title (h1)') }
    if ($html -notmatch 'class="help-foot"') { $shape.Add('the footer') }
    if ($file.Name -ne 'index.html') {
        if ($html -notmatch 'class="order"|class="order-note"') { $shape.Add('the order list or its note') }
        if ($html -notmatch 'class="related"') { $shape.Add('the related list') }
        if ($html -notmatch 'class="elements"') { $shape.Add('the elements') }
    }
    foreach ($img in [regex]::Matches($html, '(?i)<img\b[^>]*>')) {
        if ($img.Value -notmatch '(?i)\balt="[^"]+"') { $shape.Add('alt text on an image') }
    }
    foreach ($part in ($shape | Select-Object -Unique)) { Add-Finding 'PAGE-SHAPE' "${rel}: missing $part" }
}

# Rung 2: a picture is fresh while the files that draw its screen and its marker list are unchanged since the capture.
$fingerprint = Join-Path $PSScriptRoot 'help-shot-inputs.ps1'
if (-not (Test-Path -LiteralPath $fingerprint -PathType Leaf)) { Stop-CannotVerify "fingerprint helper not found: $fingerprint" }
. $fingerprint
$imgRoot = Join-Path $helpRoot 'img'
$shotIds = @()
if (Test-Path -LiteralPath $imgRoot -PathType Container) {
    $shotIds = @(Get-ChildItem -LiteralPath $imgRoot -File -Filter '*.jpg' | ForEach-Object {
            if ($_.BaseName -match '^(.+)-[a-z]{2,3}-(light|dark)$') { $Matches[1] }
        } | Sort-Object -Unique)
}
$capturePages = @{}
$capturePath = Join-Path $imgRoot 'capture.json'
if ($shotIds.Count -gt 0 -and (Test-Path -LiteralPath $capturePath -PathType Leaf)) {
    try { $capture = Get-Content -LiteralPath $capturePath -Raw -Encoding utf8 | ConvertFrom-Json } catch { Stop-CannotVerify "capture.json is not JSON: $($_.Exception.Message)" }
    $pagesProp = Get-Field $capture 'pages'
    if ($pagesProp) { foreach ($p in $pagesProp.PSObject.Properties) { $capturePages[$p.Name] = $p.Value } }
}
$noVisible = [System.Collections.Generic.List[string]]::new()
foreach ($shot in $shotIds) {
    $stale = ''
    $entry = $capturePages[$shot]
    if (-not $entry) {
        $stale = 'img/capture.json has no entry for it'
    } else {
        $print = Get-HelpShotFingerprint -Root $Root -Help $shot -Inputs @(Get-Field $entry 'inputs')
        if (-not $print.Sha256) { $stale = "its inputs are gone: $($print.Missing -join ', ')" }
        elseif ($print.Sha256 -ne [string](Get-Field $entry 'sha256')) { $stale = "its screen or marker list changed after the capture of $([string](Get-Field $entry 'date'))" }
        else {
            # Item D of the contract: a record taken since S0051 also holds what the frame draws. Older records say so.
            $visibleRecorded = [string](Get-Field $entry 'visibleSha256')
            if (-not $visibleRecorded) { $noVisible.Add($shot) }
            elseif ($visibleRecorded -ne (Get-HelpShotVisible -Root $Root -Help $shot -Inputs @(Get-Field $entry 'inputs'))) {
                $stale = "a string or drawable it shows changed after the capture of $([string](Get-Field $entry 'date'))"
            }
        }
    }
    $debt = $baseline.Contains("shot:$shot")
    if ($stale -and -not $debt) { Add-Finding 'STALE-SHOT' "${shot}: $stale (run tools/help/capture-help-screenshots.ps1 -Help $shot, then generate-help-pages.ps1)" }
    if (-not $stale -and $debt) { Add-Finding 'STALE-BASELINE' "shot:$shot is fresh now - delete its baseline line" }
}
if ($noVisible.Count -gt 0) {
    Write-Host "note: strings and drawables are not fingerprinted yet for $($noVisible.Count) picture set(s) ($($noVisible -join ', ')); the next capture writes visibleSha256"
}
foreach ($entry in @($baseline.Keys | Where-Object { $_.StartsWith('shot:') })) {
    if ($shotIds -notcontains $entry.Substring(5)) { Add-Finding 'STALE-BASELINE' "$entry has no pictures - delete its baseline line" }
}

foreach ($entry in $baseline.Keys) { Write-Host "accepted debt: $entry - $($baseline[$entry])" }

if ($findings.Count -eq 0) {
    Write-Host "assert-help-map: PASS ($($surfaces.Count) surfaces, $($rows.Count) rows, $($baseline.Count) baseline, $($pageFiles.Count) pages, $($shotIds.Count) picture sets checked, subject=$Root)"
    exit 0
}

foreach ($f in $findings) { Write-Host "  FAIL $f" }
Write-Host "assert-help-map: FAIL ($($findings.Count) finding(s))"
exit 1
