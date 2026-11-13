#requires -Version 7.0
<#
.SYNOPSIS
    Gate of DOC-EXTERNAL-QUALITY rule 3, section 9 items F and G (S0051): a translation whose English block moved is stale.

.DESCRIPTION
    English is the original. docs/l10n-fingerprints.jsonl holds one row per English block with the fingerprint each
    locale was last rebuilt from: { "doc", "block", "ru": "<16 hex>", "uk": "<16 hex>" }. The blocks are the elements of
    each help source, the sections of the privacy policy by position (the generated permissions table cut out), each
    permission sentence, each portal page section and each term base entry. Whitespace is collapsed before hashing, so
    re-wrapping a paragraph flags nothing.

    Findings, one per line, then the verdict line:
      STALE            the English block changed after this locale was rebuilt from it
      NEW-BLOCK        an English block with no row: it was never recorded as translated
      GONE-BLOCK       a row whose English block no longer exists
      MISSING-VARIANT  a block this locale does not have (item F: a page exists in all three variants or in none)
      EXTRA-VARIANT    a block a translation has and the English original does not
      UNTRANSLATED     a block of 40 or more letters that is not mostly Cyrillic: English left in a translation

    -Record is the statement that a translation was rebuilt from the English text as it stands now, the only thing that
    clears the flag (item G). It rewrites the rows of the blocks selected by -Doc and -Locale and refuses to record a block
    that has no variant or is not translated.

.PARAMETER RepoRoot
    Repository root. Default: two levels above this script's folder.

.PARAMETER Record
    Record the selected blocks as rebuilt from the current English text, then report.

.PARAMETER Doc
    With -Record: a wildcard on the document name (help/player, privacy, privacy-permissions, portal, termbase). Default: all.

.PARAMETER Locale
    With -Record: the locales rebuilt. Default: ru, uk.

.EXAMPLE
    pwsh -NoProfile -File tools/docs/assert-translation-freshness.ps1

.EXAMPLE
    pwsh -NoProfile -File tools/docs/assert-translation-freshness.ps1 -Record -Doc help/player -Locale ru

.NOTES
    Exit codes (CHECK-VERDICT): 0 passed; 1 at least one finding; 2 could not verify (a source that is not JSON, no ledger
    without -Record). The last line on stdout is the verdict line.
#>
[CmdletBinding()]
param(
    [string]$RepoRoot = '',
    [switch]$Record,
    [string]$Doc = '*',
    [ValidateSet('ru', 'uk')][string[]]$Locale = @('ru', 'uk')
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_docs-lib.ps1')

$ledgerRel = 'docs/l10n-fingerprints.jsonl'
$translations = @('ru', 'uk')
$findings = [Collections.Generic.List[string]]::new()
function Add-Finding([string]$Kind, [string]$Message) { $findings.Add("$Kind $Message") }

try {
    $root = Resolve-DocsRoot -Given $RepoRoot -ScriptRoot $PSScriptRoot
    $blocks = Get-TranslatedBlocks -Root $root
    if ($blocks.Count -eq 0) { throw 'no translated block found: the sources are not there' }

    $rows = @{}
    $ledgerPath = Join-Path $root $ledgerRel
    if (Test-Path -LiteralPath $ledgerPath -PathType Leaf) {
        foreach ($line in (Read-DocsText $ledgerPath).Split("`n")) {
            if (-not $line.Trim()) { continue }
            $row = $line | ConvertFrom-Json
            $rows["$($row.doc)`t$($row.block)"] = @{ doc = [string]$row.doc; block = [string]$row.block; ru = [string](Get-RecordField $row 'ru'); uk = [string](Get-RecordField $row 'uk') }
        }
    } elseif (-not $Record) {
        throw "$ledgerRel not found: record the rebuilt translations with -Record"
    }

    if ($Record) {
        $recorded = 0
        foreach ($key in $blocks.Keys) {
            $block = $blocks[$key]
            if (-not $block.ContainsKey('en') -or $block.Doc -notlike $Doc) { continue }
            if (-not $rows.ContainsKey($key)) { $rows[$key] = @{ doc = $block.Doc; block = $block.Block; ru = ''; uk = '' } }
            foreach ($name in $Locale) {
                if (-not $block.ContainsKey($name)) { Add-Finding 'MISSING-VARIANT' "$($block.Doc) $($block.Block) [$name]: nothing to record"; continue }
                if (-not (Test-CyrillicShare $block[$name])) { Add-Finding 'UNTRANSLATED' "$($block.Doc) $($block.Block) [$name]: not recorded, it is not translated"; continue }
                $rows[$key][$name] = Get-TextFingerprint $block['en']
                $recorded++
            }
        }
        foreach ($key in @($rows.Keys)) { if (-not $blocks.ContainsKey($key) -or -not $blocks[$key].ContainsKey('en')) { $rows.Remove($key) } }
        if ($findings.Count -gt 0) {
            foreach ($finding in $findings) { Write-Host $finding }
            Write-Host "assert-translation-freshness: FAIL ($($findings.Count) finding(s), nothing recorded)"
            exit 1
        }
        [string[]]$ordered = @($rows.Keys)
        [Array]::Sort($ordered, [StringComparer]::Ordinal)
        $text = ($ordered | ForEach-Object { $row = $rows[$_]; [ordered]@{ doc = $row.doc; block = $row.block; ru = $row.ru; uk = $row.uk } | ConvertTo-Json -Compress }) -join "`n"
        [IO.File]::WriteAllText($ledgerPath, "$text`n", [Text.UTF8Encoding]::new($false))
        Write-Host "assert-translation-freshness: PASS (recorded=$recorded blocks=$($rows.Count))"
        exit 0
    }

    foreach ($key in $blocks.Keys) {
        $block = $blocks[$key]
        $label = "$($block.Doc) $($block.Block)"
        if (-not $block.ContainsKey('en')) {
            foreach ($name in $translations) { if ($block.ContainsKey($name)) { Add-Finding 'EXTRA-VARIANT' "$label [$name]: the English original has no such block" } }
            continue
        }
        $fingerprint = Get-TextFingerprint $block['en']
        foreach ($name in $translations) {
            if (-not $block.ContainsKey($name)) { Add-Finding 'MISSING-VARIANT' "$label [$name]: no such block"; continue }
            if (-not (Test-CyrillicShare $block[$name])) { Add-Finding 'UNTRANSLATED' "$label [$name]: fewer than half of its letters are Cyrillic" }
            if (-not $rows.ContainsKey($key) -or -not $rows[$key][$name]) { Add-Finding 'NEW-BLOCK' "$label [$name]: never recorded as rebuilt from the English text"; continue }
            if ($rows[$key][$name] -ne $fingerprint) { Add-Finding 'STALE' "$label [$name]: the English block changed after this translation was rebuilt" }
        }
    }
    foreach ($key in $rows.Keys) {
        if (-not $blocks.ContainsKey($key) -or -not $blocks[$key].ContainsKey('en')) { Add-Finding 'GONE-BLOCK' "$($rows[$key].doc) $($rows[$key].block): the English block no longer exists" }
    }

    foreach ($finding in $findings) { Write-Host $finding }
    if ($findings.Count -gt 0) {
        Write-Host "assert-translation-freshness: FAIL ($($findings.Count) finding(s); after rebuilding a translation run this script with -Record)"
        exit 1
    }
    Write-Host "assert-translation-freshness: PASS (blocks=$($blocks.Count) locales=$($translations -join ','))"
    exit 0
} catch {
    Write-Host $_.Exception.Message
    Write-Host "assert-translation-freshness: COULD NOT VERIFY ($($_.Exception.Message))"
    exit 2
}
