#requires -Version 7.0
<#
.SYNOPSIS
    Checks this product's contract pointers, registry cells and vendored playlist vectors.
.DESCRIPTION
    CatalogRoot is supplied by the operator using CLAUDE.md's catalog locator.
    This read-only check does not prove runtime conformance or source parity.
    Every block runs and every finding is printed.
    Exit codes (CHECK-VERDICT): 0 every block passed; 1 at least one finding; 2 could not verify (a missing file or an
    unreadable catalog stops the run, as it cannot be checked past). The last line on stdout is the verdict line:
    `contract-sync: PASS (..)`, `FAIL (..)` or `COULD NOT VERIFY (..)`. The pre-release sweep runs this script, so this
    table of bindings is the only one.
#>
[CmdletBinding()]
param([Parameter(Mandatory)][string]$CatalogRoot)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$bindings = @{
    'rule-adoption' = @{ Pointer = 'RULE-ADOPTION'; Ids = @('REPO-STAMP','HARNESS-PROFILE','REPO-LAYOUT','RULE-DELIVERY') }
    'stream-catalog' = @{ Pointer = 'STREAM-BANK'; Ids = @('STREAM-BANK') }
    'user-playlist' = @{ Pointer = 'USER-PLAYLIST'; Ids = @('USER-PLAYLIST') }
    'media-classification' = @{ Pointer = 'MEDIA-CLASSIFICATION'; Ids = @('MEDIA-CLASSIFICATION') }
    'package-versioning' = @{ Pointer = 'PACKAGE-VERSIONING'; Ids = @('PACKAGE-VERSIONING') }
    'iconography' = @{ Pointer = 'ICONOGRAPHY'; Ids = @('ICON-SET','ICON-RENDER','ICON-EXTERNAL') }
    'input-controls' = @{ Pointer = 'INPUT-PARITY'; Ids = @('INPUT-PARITY') }
    'live-broadcast' = @{ Pointer = 'LIVE-BROADCAST'; Ids = @('LIVE-BROADCAST') }
    'device-exchange' = @{ Pointer = 'DEVICE-EXCHANGE'; Ids = @('DEVICE-EXCHANGE') }
    'install-trust' = @{ Pointer = 'INSTALL-TRUST'; Ids = @('INSTALL-TRUST') }
    'diagnostic-report' = @{ Pointer = 'DIAGNOSTIC-REPORT'; Ids = @('DIAGNOSTIC-REPORT') }
    'product-web-pages' = @{ Pointer = 'PRODUCT-WEB-PAGES'; Ids = @('PAGE-CONTENT','PAGE-STYLE','SITE-FAMILY-MAP') }
    'test-lab' = @{ Pointer = 'TEST-LAB'; Ids = @('TEST-LAB') }
    'documentation-quality' = @{ Pointer = 'PORTAL-DOCS'; Ids = @('DOC-EXTERNAL-QUALITY','DOC-INTERNAL-QUALITY') }
    'ui-sizing' = @{ Pointer = 'UNIFIED-UI'; Ids = @('UI-SIZING') }
    'buttons' = @{ Pointer = 'UNIFIED-UI'; Ids = @('BUTTONS') }
    'boolean-option' = @{ Pointer = 'UNIFIED-UI'; Ids = @('BOOLEAN-OPTION') }
    'collapsible-group' = @{ Pointer = 'UNIFIED-UI'; Ids = @('COLLAPSIBLE-GROUP') }
    'settings-navigation' = @{ Pointer = 'UNIFIED-UI'; Ids = @('SETTINGS-NAVIGATION') }
    'product-site' = @{ Pointer = 'PORTAL-DOCS'; Ids = @('SITE-STRUCTURE') }
    'in-app-help' = @{ Pointer = 'CONTEXT-HELP'; Ids = @('CONTEXT-HELP') }
    'automated-checks' = @{ Pointer = 'AUTOMATED-CHECKS'; Ids = @('CHECK-VERDICT','CHECK-BASELINE') }
    'exchange-mark' = @{ Pointer = 'EXCHANGE-MARK'; Ids = @('EXCHANGE-MARK') }
}

$failures = [System.Collections.Generic.List[string]]::new()
$blockFailures = 0
function Add-Failure([string]$Message) {
    $script:failures.Add($Message)
    $script:blockFailures++
    Write-Output "FAIL: $Message"
}

try {
    $versions = @{}
    $blockFailures = 0
    foreach ($domain in ($bindings.Keys | Sort-Object)) {
        $binding = $bindings[$domain]
        $domainText = Get-Content -Raw -LiteralPath (Join-Path $CatalogRoot "$domain/README.md")
        $pointer = Get-Content -Raw -LiteralPath (Join-Path $repoRoot "docs/contracts/$($binding.Pointer).md")
        $declared = @{}
        foreach ($match in [regex]::Matches($domainText, '(?m)^id: (.+)\r?\nversion: (.+)')) {
            $declared[$match.Groups[1].Value.Trim()] = $match.Groups[2].Value.Trim()
        }
        foreach ($id in $binding.Ids) {
            if (-not $declared.ContainsKey($id)) { Add-Failure "$id has no domain header"; continue }
            $version = $declared[$id]
            $versions[$id] = $version
            if ($pointer -notmatch [regex]::Escape($id) -or
                $pointer -notmatch ('(?<![\d.])' + [regex]::Escape($version) + '(?![\d.])')) {
                Add-Failure "$id pointer does not name current version $version"
            }
        }
    }
    if ($blockFailures -eq 0) { Write-Output "PASS: $($versions.Count) pointer versions match domain headers" }
    else { Write-Output "INFO: $($versions.Count - $blockFailures) of $($versions.Count) pointer versions match domain headers" }

    $blockFailures = 0
    $registry = Get-Content -LiteralPath (Join-Path $CatalogRoot '_meta/REGISTRY.md')
    $adoption = $false
    $seen = @{}
    foreach ($line in $registry) {
        if ($line -match '^## 2\.') { $adoption = $true; continue }
        if ($line -match '^## 3\.') { $adoption = $false }
        if (-not $adoption -or $line -notmatch '^\|') { continue }
        $cells = @($line.Split('|') | ForEach-Object { $_.Trim() })
        if ($cells.Count -lt 9 -or $cells[2] -ne 'StreamsPlayer Android') { continue }
        $id = $cells[1].Trim('`')
        if (-not $versions.ContainsKey($id) -or $seen.ContainsKey($id)) { Add-Failure "Unexpected or duplicate own row: $id"; continue }
        $seen[$id] = $true
        foreach ($cell in @($cells[4], $cells[5])) {
            if ($cell -ne '-' -and $cell -ne $versions[$id] -and $cell -ne "partial $($versions[$id])") {
                Add-Failure "$id has stale or invalid version cell: $cell"
            }
        }
        if ($cells[6] -ne 'pending' -and $cells[6] -notmatch '^\d{4}-\d{2}-\d{2}$') {
            Add-Failure "$id has an invalid Verified cell"
        }
    }
    $required = @('STREAM-BANK','USER-PLAYLIST','MEDIA-CLASSIFICATION','PACKAGE-VERSIONING','INPUT-PARITY',
        'LIVE-BROADCAST','DEVICE-EXCHANGE','ICON-SET','ICON-RENDER','ICON-EXTERNAL','REPO-STAMP','HARNESS-PROFILE','REPO-LAYOUT','RULE-DELIVERY','TEST-LAB','CONTEXT-HELP','CHECK-VERDICT','CHECK-BASELINE','EXCHANGE-MARK')
    foreach ($id in $required) { if (-not $seen.ContainsKey($id)) { Add-Failure "Missing own row: $id" } }
    if ($blockFailures -eq 0) { Write-Output "PASS: $($seen.Count) own adoption rows have unique ids and current version cells" }
    else { Write-Output "INFO: $($seen.Count) own adoption rows read, $blockFailures finding(s) above" }

    $blockFailures = 0
    $vectors = @('valid-extended.m3u8','valid-extended-hls-entry.m3u8','hls-manifest-rejection.m3u8')
    foreach ($name in $vectors) {
        $original = Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $CatalogRoot "user-playlist/vectors/$name")
        $vendored = Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $repoRoot "phone/src/test/resources/user-playlist/$name")
        if ($original.Hash -ne $vendored.Hash) { Add-Failure "USER-PLAYLIST vector differs from current catalog: $name" }
    }
    if ($blockFailures -eq 0) { Write-Output "PASS: $($vectors.Count) required USER-PLAYLIST vectors match current catalog bytes" }

    $blockFailures = 0
    $exchangeVectors = @('connection-string-v1.json','fmsw-224/connection-string-v1.json','fmsw-224/frames-v2.json','protection-v1.json','records-v1.json','server-frames-v2.json','server-records-v1.json')
    foreach ($name in $exchangeVectors) {
        $original = Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $CatalogRoot "device-exchange/vectors/$name")
        $vendored = Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $repoRoot "phone/src/test/resources/device-exchange/$name")
        if ($original.Hash -ne $vendored.Hash) { Add-Failure "DEVICE-EXCHANGE vector differs from current catalog: $name" }
    }
    if ($blockFailures -eq 0) { Write-Output "PASS: $($exchangeVectors.Count) DEVICE-EXCHANGE vectors match current catalog bytes" }

    # S0032: the end-to-end encryption vectors live with the shared core that replays them.
    $blockFailures = 0
    $coreVectors = @('e2e-v1.json')
    $coreProvenance = Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'exchange-core/src/test/resources/device-exchange/PROVENANCE.txt')
    foreach ($name in $coreVectors) {
        $original = Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $CatalogRoot "device-exchange/vectors/$name")
        $vendored = Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $repoRoot "exchange-core/src/test/resources/device-exchange/$name")
        if ($original.Hash -ne $vendored.Hash) { Add-Failure "DEVICE-EXCHANGE vector differs from current catalog: $name" }
        if ($coreProvenance -notmatch ('(?m)^sha256 ' + [regex]::Escape($name) + ' ' + $vendored.Hash.ToLower() + '\s*$')) {
            Add-Failure "exchange-core PROVENANCE.txt row for $name does not carry the hash of the vendored file"
        }
    }
    if ($blockFailures -eq 0) { Write-Output "PASS: $($coreVectors.Count) DEVICE-EXCHANGE E2E vector matches current catalog bytes and its provenance row" }

    # S0030: three tables are part of the DEVICE-EXCHANGE text, not catalog files: the terms (item CW), the one table
    # of messages (item CZ) and the ladder of checks (item CY). The same lines are cut out of the catalog's README and
    # compared with the vendored bytes, and the provenance row of each must carry the hash of the vendored file.
    $blockFailures = 0
    $catalogLines = @(Get-Content -LiteralPath (Join-Path $CatalogRoot 'device-exchange/README.md') -Encoding utf8)
    $provenance = Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'phone/src/test/resources/device-exchange/PROVENANCE.txt')
    $tables = @(
        @{ Item = 'CW'; File = 'terms-item-cw.md'; Heading = '**CW [NEW] - the terms (amends 2).**'; First = '^\| Term \|' }
        @{ Item = 'CZ'; File = 'messages-item-cz.md'; Heading = '**CZ [NEW] - the one table of messages (fulfils item BR; amends 7.10 and 9).**'; First = '^\| Code \|' }
        @{ Item = 'CY'; File = 'ladder-item-cy.md'; Heading = '**CY [NEW] - the ladder of checks for a connection that does not work (amends 9; reads with item BR).**'; First = '^\| # \|' }
    )
    foreach ($spec in $tables) {
        $heading = [array]::IndexOf($catalogLines, $spec.Heading)
        if ($heading -lt 0) { Add-Failure "DEVICE-EXCHANGE item $($spec.Item) heading not found in the catalog"; continue }
        $row = $heading
        while ($row -lt $catalogLines.Count -and $catalogLines[$row] -notmatch $spec.First) { $row++ }
        $table = [System.Collections.Generic.List[string]]::new()
        while ($row -lt $catalogLines.Count -and $catalogLines[$row] -match '^\|') { $table.Add($catalogLines[$row]); $row++ }
        if ($table.Count -eq 0) { Add-Failure "DEVICE-EXCHANGE item $($spec.Item) table not found in the catalog"; continue }
        $vendoredPath = Join-Path $repoRoot "phone/src/test/resources/device-exchange/$($spec.File)"
        $vendoredTable = [System.IO.File]::ReadAllText($vendoredPath).Replace("`r`n", "`n")
        if ((($table -join "`n") + "`n") -ne $vendoredTable) { Add-Failure "DEVICE-EXCHANGE item $($spec.Item) table differs from the catalog text ($($spec.File))" }
        $vendoredHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $vendoredPath).Hash.ToLower()
        if ($provenance -notmatch ('(?m)^sha256 ' + [regex]::Escape($spec.File) + ' ' + $vendoredHash + '\s*$')) {
            Add-Failure "PROVENANCE.txt row for $($spec.File) does not carry the hash of the vendored file"
        }
    }
    if ($blockFailures -eq 0) { Write-Output "PASS: $($tables.Count) DEVICE-EXCHANGE text tables (items CW, CZ, CY) match the catalog text and their provenance rows" }

    if ($failures.Count -gt 0) {
        Write-Output "contract-sync: FAIL ($($failures.Count) finding(s))"
        exit 1
    }
    Write-Output "contract-sync: PASS ($($versions.Count) pointer versions)"
    exit 0
} catch {
    Write-Output "contract-sync: COULD NOT VERIFY ($($_.Exception.Message))"
    exit 2
}
