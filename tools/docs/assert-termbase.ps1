#requires -Version 7.0
<#
.SYNOPSIS
    Gate of DOC-EXTERNAL-QUALITY rule 4 (S0051): the term base is well-formed and no prose uses a forbidden word.

.DESCRIPTION
    Reads docs/TERMBASE.json. The schema half checks that each forbidden entry compiles, names its locales and gives a
    reason of at least four words, and that each term has a definition in en, ru and uk and a name that resolves: its row
    in the vendored dictionary table, its string on the screen, or its own words.

    The scan half reads, per locale, the text values of the help sources, the prose of the privacy policies and the
    portal page sections and the term base's own entries. Code spans, code blocks and HTML comments are not prose. The
    handover notes above the portal pages are for the publisher, not for a reader, and are not scanned.

    Findings, one per line, then the verdict line:
      BAD-TERMBASE    the term base itself is defective
      FORBIDDEN-TERM  a word the term base forbids, with the entry that forbids it and its reason

.PARAMETER RepoRoot
    Repository root. Default: two levels above this script's folder.

.NOTES
    Exit codes (CHECK-VERDICT): 0 passed; 1 at least one finding; 2 could not verify (no term base, a source that is not
    JSON, a dictionary that cannot be read). The last line on stdout is the verdict line.
#>
[CmdletBinding()]
param([string]$RepoRoot = '')

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_docs-lib.ps1')

$findings = [Collections.Generic.List[string]]::new()
$rules = [Collections.Generic.List[object]]::new()
$locales = @('en', 'ru', 'uk')

function Test-Text([string]$Where, [string]$Locale, [AllowEmptyString()][string]$Text, [bool]$CountLines) {
    foreach ($rule in $script:rules) {
        if ($Locale -notin $rule.Locales) { continue }
        foreach ($m in $rule.Regex.Matches($Text)) {
            $at = if ($CountLines) { ":$(($Text.Substring(0, $m.Index).Split("`n")).Count)" } else { '' }
            $script:findings.Add("FORBIDDEN-TERM $Where$at [$Locale]: '$($m.Value)' - $($rule.Id): $($rule.Reason)")
        }
    }
}

function Test-JsonStrings([string]$Where, [string]$Locale, $Node, [string]$Pointer) {
    if ($null -eq $Node) { return }
    if ($Node -is [string]) { Test-Text "$Where $Pointer" $Locale $Node $false; return }
    if ($Node -is [Collections.IEnumerable]) {
        $i = 0
        foreach ($item in $Node) { Test-JsonStrings $Where $Locale $item "$Pointer[$i]"; $i++ }
        return
    }
    if ($Node -is [Management.Automation.PSCustomObject]) {
        foreach ($property in $Node.PSObject.Properties) {
            # A group's `elements` is a list of element ids, not words a reader meets.
            if ($property.Name -eq 'elements' -and $property.Value -is [array]) { continue }
            Test-JsonStrings $Where $Locale $property.Value "$Pointer.$($property.Name)"
        }
    }
}

try {
    $root = Resolve-DocsRoot -Given $RepoRoot -ScriptRoot $PSScriptRoot
    $termbasePath = Join-Path $root 'docs/TERMBASE.json'
    if (-not (Test-Path -LiteralPath $termbasePath -PathType Leaf)) { throw 'docs/TERMBASE.json not found' }
    $termbase = Read-DocsText $termbasePath | ConvertFrom-Json
    if ([string](Get-RecordField $termbase 'shape') -ne '1') { $findings.Add("BAD-TERMBASE shape is not 1") }

    $seen = @{}
    foreach ($entry in @(Get-RecordField $termbase 'forbidden')) {
        $id = [string](Get-RecordField $entry 'id')
        if (-not $id -or $seen.ContainsKey($id)) { $findings.Add("BAD-TERMBASE forbidden entry '$id' has no id or repeats one"); continue }
        $seen[$id] = $true
        $reason = [string](Get-RecordField $entry 'reason')
        if (@($reason -split '\s+' | Where-Object { $_ }).Count -lt 4) { $findings.Add("BAD-TERMBASE ${id}: the reason needs at least four words") }
        $entryLocales = @(Get-RecordField $entry 'locales')
        if ($entryLocales.Count -eq 0 -or @($entryLocales | Where-Object { $_ -notin $locales }).Count -gt 0) { $findings.Add("BAD-TERMBASE ${id}: locales must be among $($locales -join ', ')") }
        $options = [Text.RegularExpressions.RegexOptions]::CultureInvariant
        if (-not (Get-RecordField $entry 'caseSensitive')) { $options = $options -bor [Text.RegularExpressions.RegexOptions]::IgnoreCase }
        try { $regex = [regex]::new([string](Get-RecordField $entry 'pattern'), $options) } catch { $findings.Add("BAD-TERMBASE ${id}: the pattern does not compile"); continue }
        $rules.Add([pscustomobject]@{ Id = $id; Locales = $entryLocales; Regex = $regex; Reason = $reason })
    }
    if ($rules.Count -eq 0) { $findings.Add('BAD-TERMBASE no forbidden entry: the gate would judge nothing') }

    $dictionaryRel = [string](Get-RecordField $termbase 'dictionary')
    $dictionaryPath = Join-Path $root $dictionaryRel
    if (-not $dictionaryRel -or -not (Test-Path -LiteralPath $dictionaryPath -PathType Leaf)) { throw "the dictionary table '$dictionaryRel' cannot be read" }
    $dictionary = @{}
    foreach ($row in @((Read-DocsText $dictionaryPath).Split("`n") | Where-Object { $_ -match '^\|' } | Select-Object -Skip 2)) {
        $cells = @($row.Trim().Trim('|').Split('|') | ForEach-Object { $_.Trim() })
        if ($cells.Count -ge 4) { $dictionary[$cells[0]] = $true }
    }
    $stringsPath = Join-Path $root 'phone/src/main/res/values/strings.xml'
    $strings = @{}
    foreach ($file in (Get-DiskMatches -Root $root -Pattern 'phone/src/main/res/values/strings*.xml')) {
        foreach ($m in [regex]::Matches((Read-DocsText (Join-Path $root $file)), '<string\s+name="([^"]+)"')) { $strings[$m.Groups[1].Value] = $true }
    }
    $termIds = @{}
    foreach ($term in @(Get-RecordField $termbase 'terms')) {
        $id = [string](Get-RecordField $term 'id')
        if ($id -notmatch '^[a-z0-9]+(-[a-z0-9]+)*$' -or $termIds.ContainsKey($id)) { $findings.Add("BAD-TERMBASE term '$id' has a malformed or repeated id"); continue }
        $termIds[$id] = $true
        foreach ($locale in $locales) {
            if (-not [string](Get-RecordField (Get-RecordField $term 'definition') $locale)) { $findings.Add("BAD-TERMBASE ${id}: no definition in $locale") }
        }
        $key = [string](Get-RecordField $term 'dictionary')
        $label = [string](Get-RecordField $term 'labelKey')
        if ($key) { if (-not $dictionary.ContainsKey($key)) { $findings.Add("BAD-TERMBASE ${id}: '$key' is not a row of the dictionary") } }
        elseif ($label) { if (-not $strings.ContainsKey($label)) { $findings.Add("BAD-TERMBASE ${id}: the string '$label' does not exist") } }
        else {
            foreach ($locale in $locales) {
                if (-not [string](Get-RecordField (Get-RecordField $term 'name') $locale)) { $findings.Add("BAD-TERMBASE ${id}: no name in $locale") }
            }
        }
        foreach ($locale in $locales) {
            Test-Text "docs/TERMBASE.json $id" $locale ("$([string](Get-RecordField (Get-RecordField $term 'name') $locale)) $([string](Get-RecordField (Get-RecordField $term 'definition') $locale))") $false
        }
    }

    $scanned = 0
    foreach ($rel in (Get-DiskMatches -Root $root -Pattern 'docs/help/content/*.json')) {
        $source = Get-RecordField (Read-DocsText (Join-Path $root $rel) | ConvertFrom-Json) 'locales'
        foreach ($locale in $locales) { Test-JsonStrings $rel $locale (Get-RecordField $source $locale) '$'; $scanned++ }
    }
    foreach ($policy in @(@('docs/PRIVACY_POLICY.md', 'en'), @('docs/PRIVACY_POLICY-ru.md', 'ru'), @('docs/PRIVACY_POLICY-uk.md', 'uk'))) {
        $path = Join-Path $root $policy[0]
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { continue }
        Test-Text $policy[0] $policy[1] ((Get-MarkdownProseLines (Read-DocsText $path)) -join "`n") $true
        $scanned++
    }
    $portalPath = Join-Path $root 'docs/PORTAL_SECTIONS_STREAMSPLAYER.md'
    if (Test-Path -LiteralPath $portalPath -PathType Leaf) {
        $portal = Get-PortalBlocks (Read-DocsText $portalPath)
        foreach ($page in $portal.Keys) {
            foreach ($locale in $portal[$page].Keys) {
                Test-Text "docs/PORTAL_SECTIONS_STREAMSPLAYER.md $page" $locale ((Get-MarkdownProseLines $portal[$page][$locale]) -join "`n") $false
                $scanned++
            }
        }
    }

    foreach ($finding in $findings) { Write-Host $finding }
    if ($findings.Count -gt 0) {
        Write-Host "assert-termbase: FAIL ($($findings.Count) finding(s))"
        exit 1
    }
    Write-Host "assert-termbase: PASS (terms=$($termIds.Count) forbidden=$($rules.Count) blocks=$scanned)"
    exit 0
} catch {
    Write-Host $_.Exception.Message
    Write-Host "assert-termbase: COULD NOT VERIFY ($($_.Exception.Message))"
    exit 2
}
