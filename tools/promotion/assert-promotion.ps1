#requires -Version 7.0
<#
.SYNOPSIS
    Gate of the promotion sources (S0014, canon PROMOTION.md sections 1 and 2): no forbidden term in a published source or a
    discoverability field, the Play listing inside its limits, the topics and contacts honest, the pillars in order.

.DESCRIPTION
    Reads tools/promotion/forbidden-terms.json and these sources of the repository:

      published sources  README.md, CHANGELOG.md, POSITIONING.md, the privacy policies, the portal sections, docs/play/PLAY_LISTING*.md,
                         the help sources (docs/help/content/*.json), every string resource of phone, wear and watchface, site/*.html
      discoverability    the Topics and Search terms lines of POSITIONING.md, and the Title and Short description of every
                         docs/play/PLAY_LISTING*.md (the Full description is a published source, not a keyword field)

    Findings, one per line, then the verdict line:
      BAD-LIST        the forbidden-terms list itself is defective (shape, a pattern that does not compile, a repeated id, a reason
                      under four words, no entry of a scope)
      MISSING         a source this gate needs is absent or lacks a field
      FORBIDDEN-TERM  a `published` entry matched in a published source, with file:line and the entry that forbids it
      FORBIDDEN-FIELD a `published` or `fields` entry matched in a discoverability field
      LIMIT           a Play title over 30 characters, a short description over 80, a full description over 4000, or an empty field
      TOPICS          more than 20 topics, or a topic that GitHub would refuse
      CONTACT         an e-mail address in a published source other than sza@ukr.net
      PILLAR-ORDER    the listing does not name the pillars of POSITIONING.md section 2 in their order (the full description names
                      all four, the short description a leading run of them)

.PARAMETER RepoRoot
    Repository root. Default: two levels above this script's folder.

.NOTES
    Exit codes (CHECK-VERDICT): 0 passed; 1 at least one finding; 2 could not verify (a source cannot be read). The last line on
    stdout is the verdict line.
#>
[CmdletBinding()]
param([string]$RepoRoot = '')

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$findings = [Collections.Generic.List[string]]::new()
$contact = 'sza@ukr.net'
$locales = @('en', 'ru', 'uk')
$listingFiles = @{ en = 'docs/play/PLAY_LISTING.md'; ru = 'docs/play/PLAY_LISTING-ru.md'; uk = 'docs/play/PLAY_LISTING-uk.md' }

function Add-Finding([string]$Kind, [string]$Message) { $script:findings.Add("$Kind $Message") }

function Read-Utf8([string]$Path) { [IO.File]::ReadAllText($Path, [Text.UTF8Encoding]::new($false)) }

function Get-Sources([string]$Root) {
    $rel = [Collections.Generic.List[string]]::new()
    foreach ($f in 'README.md', 'CHANGELOG.md', 'POSITIONING.md', 'docs/PRIVACY_POLICY.md', 'docs/PRIVACY_POLICY-ru.md', 'docs/PRIVACY_POLICY-uk.md', 'docs/PORTAL_SECTIONS_STREAMSPLAYER.md') {
        if (Test-Path -LiteralPath (Join-Path $Root $f) -PathType Leaf) { $rel.Add($f) }
    }
    foreach ($spec in @(@('docs/play', 'PLAY_LISTING*.md'), @('docs/help/content', '*.json'), @('site', '*.html'))) {
        $dir = Join-Path $Root $spec[0]
        if (Test-Path -LiteralPath $dir -PathType Container) {
            foreach ($file in Get-ChildItem -LiteralPath $dir -Filter $spec[1] -File) { $rel.Add("$($spec[0])/$($file.Name)") }
        }
    }
    foreach ($module in 'phone', 'wear', 'watchface') {
        $res = Join-Path $Root "$module/src/main/res"
        if (-not (Test-Path -LiteralPath $res -PathType Container)) { continue }
        foreach ($values in Get-ChildItem -LiteralPath $res -Directory -Filter 'values*') {
            foreach ($file in Get-ChildItem -LiteralPath $values.FullName -Filter 'strings*.xml' -File) { $rel.Add("$module/src/main/res/$($values.Name)/$($file.Name)") }
        }
    }
    return $rel
}

function Get-ListingField([string]$Text, [string]$Heading) {
    $m = [regex]::Match($Text, "(?ms)^## $([regex]::Escape($Heading))[ \t]*\r?\n(.*?)(?=^## |\z)")
    if (-not $m.Success) { return $null }
    return $m.Groups[1].Value.Trim()
}

function Get-FirstIndex([string]$Text, [string]$Term) {
    return $Text.IndexOf($Term, [StringComparison]::OrdinalIgnoreCase)
}

try {
    $root = if ($RepoRoot) { (Resolve-Path -LiteralPath $RepoRoot).Path } else { (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path }

    # ---- the list ---------------------------------------------------------------------------------------------------------
    $listPath = Join-Path $root 'tools/promotion/forbidden-terms.json'
    if (-not (Test-Path -LiteralPath $listPath -PathType Leaf)) { throw 'tools/promotion/forbidden-terms.json not found' }
    $list = Read-Utf8 $listPath | ConvertFrom-Json
    if ([string]$list.shape -ne '1') { Add-Finding 'BAD-LIST' 'shape is not 1' }
    $rules = [Collections.Generic.List[object]]::new()
    $seen = @{}
    foreach ($entry in @($list.forbidden)) {
        $id = [string]$entry.id
        if (-not $id -or $seen.ContainsKey($id)) { Add-Finding 'BAD-LIST' "entry '$id' has no id or repeats one"; continue }
        $seen[$id] = $true
        if ([string]$entry.scope -notin 'published', 'fields') { Add-Finding 'BAD-LIST' "${id}: scope must be published or fields"; continue }
        if (@(([string]$entry.reason) -split '\s+' | Where-Object { $_ }).Count -lt 4) { Add-Finding 'BAD-LIST' "${id}: the reason needs at least four words" }
        try { $regex = [regex]::new([string]$entry.pattern, [Text.RegularExpressions.RegexOptions]::IgnoreCase -bor [Text.RegularExpressions.RegexOptions]::CultureInvariant) }
        catch { Add-Finding 'BAD-LIST' "${id}: the pattern does not compile"; continue }
        $rules.Add([pscustomobject]@{ Id = $id; Scope = [string]$entry.scope; Regex = $regex; Reason = [string]$entry.reason })
    }
    foreach ($scope in 'published', 'fields') {
        if (@($rules | Where-Object { $_.Scope -eq $scope }).Count -eq 0) { Add-Finding 'BAD-LIST' "no entry of scope '$scope': the gate would judge nothing there" }
    }

    # ---- published sources ------------------------------------------------------------------------------------------------
    $sources = @(Get-Sources $root)
    $publishedRules = @($rules | Where-Object { $_.Scope -eq 'published' })
    foreach ($rel in $sources) {
        $text = Read-Utf8 (Join-Path $root $rel)
        foreach ($rule in $publishedRules) {
            foreach ($m in $rule.Regex.Matches($text)) {
                $line = $text.Substring(0, $m.Index).Split("`n").Count
                Add-Finding 'FORBIDDEN-TERM' "${rel}:${line}: '$($m.Value)' - $($rule.Id): $($rule.Reason)"
            }
        }
        foreach ($m in [regex]::Matches($text, '[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)+')) {
            if ($m.Value.ToLowerInvariant() -ne $contact) {
                $line = $text.Substring(0, $m.Index).Split("`n").Count
                Add-Finding 'CONTACT' "${rel}:${line}: '$($m.Value)' - the one contact of every published surface is $contact"
            }
        }
    }

    # ---- discoverability fields -------------------------------------------------------------------------------------------
    $fieldTexts = [Collections.Generic.List[object]]::new()   # @{ Where; Text }
    $positioningPath = Join-Path $root 'POSITIONING.md'
    $positioning = $null
    if (-not (Test-Path -LiteralPath $positioningPath -PathType Leaf)) { Add-Finding 'MISSING' 'POSITIONING.md' }
    else {
        $positioning = Read-Utf8 $positioningPath
        $topicsLine = [regex]::Match($positioning, '(?m)^Topics:[ \t]*(.+?)[ \t]*\r?$')
        if (-not $topicsLine.Success) { Add-Finding 'MISSING' 'POSITIONING.md: the Topics line' }
        else {
            $topics = @($topicsLine.Groups[1].Value.Split(',') | ForEach-Object { $_.Trim() } | Where-Object { $_ })
            if ($topics.Count -eq 0) { Add-Finding 'TOPICS' 'the Topics line is empty' }
            if ($topics.Count -gt 20) { Add-Finding 'TOPICS' "$($topics.Count) topics; GitHub takes at most 20" }
            foreach ($topic in $topics) {
                if ($topic -notmatch '^[a-z0-9][a-z0-9-]{0,49}$') { Add-Finding 'TOPICS' "'$topic' is not a valid GitHub topic (lowercase letters, digits and hyphens, at most 50 characters)" }
                $fieldTexts.Add(@{ Where = "POSITIONING.md topic '$topic'"; Text = $topic })
            }
        }
        foreach ($locale in $locales) {
            $terms = [regex]::Match($positioning, "(?m)^Search terms ${locale}:[ \t]*(.+?)[ \t]*\r?$")
            if (-not $terms.Success) { Add-Finding 'MISSING' "POSITIONING.md: the 'Search terms $locale' line"; continue }
            foreach ($term in @($terms.Groups[1].Value.Split(',') | ForEach-Object { $_.Trim() } | Where-Object { $_ })) {
                $fieldTexts.Add(@{ Where = "POSITIONING.md search term [$locale] '$term'"; Text = $term })
            }
        }
    }

    # ---- the listing: limits, fields, pillars -----------------------------------------------------------------------------
    $pillars = @()
    if ($null -ne $positioning) {
        $rows = [regex]::Matches($positioning, '(?m)^\|\s*(\d)\s*\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|\s*\r?$')
        $pillars = @($rows | ForEach-Object { [pscustomobject]@{ No = [int]$_.Groups[1].Value; en = $_.Groups[4].Value; ru = $_.Groups[5].Value; uk = $_.Groups[6].Value } } | Sort-Object No)
        if ($pillars.Count -ne 4) { Add-Finding 'MISSING' "POSITIONING.md: the four-row pillars table of section 2 (found $($pillars.Count))" }
    }
    $limits = @{ 'Title' = 30; 'Short description' = 80; 'Full description' = 4000 }
    foreach ($locale in $locales) {
        $rel = $listingFiles[$locale]
        $path = Join-Path $root $rel
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { Add-Finding 'MISSING' $rel; continue }
        $text = Read-Utf8 $path
        $fields = @{}
        foreach ($heading in $limits.Keys) {
            $value = Get-ListingField $text $heading
            if ([string]::IsNullOrWhiteSpace($value)) { Add-Finding 'LIMIT' "${rel}: the '$heading' field is missing or empty"; continue }
            $fields[$heading] = $value
            if ($value.Length -gt $limits[$heading]) { Add-Finding 'LIMIT' "${rel}: '$heading' is $($value.Length) characters, the limit is $($limits[$heading])" }
        }
        foreach ($heading in 'Title', 'Short description') {
            if ($fields.ContainsKey($heading)) { $fieldTexts.Add(@{ Where = "$rel '$heading'"; Text = $fields[$heading] }) }
        }
        if ($pillars.Count -eq 4) {
            $terms = @($pillars | ForEach-Object { $_.$locale })
            foreach ($pair in @(@('Short description', $false), @('Full description', $true))) {
                if (-not $fields.ContainsKey($pair[0])) { continue }
                $at = @($terms | ForEach-Object { Get-FirstIndex $fields[$pair[0]] $_ })
                $present = @(0..3 | Where-Object { $at[$_] -ge 0 })
                $ordered = $true
                for ($i = 1; $i -lt $present.Count; $i++) { if ($at[$present[$i]] -lt $at[$present[$i - 1]]) { $ordered = $false } }
                $needed = if ($pair[1]) { 4 } else { 0 }
                $prefix = ($present.Count -eq 0) -or ($present[-1] -eq $present.Count - 1)
                if (-not $ordered -or -not $prefix -or $present.Count -lt $needed) {
                    Add-Finding 'PILLAR-ORDER' "${rel}: '$($pair[0])' names pillars [$($present | ForEach-Object { $terms[$_] } | Join-String -Separator ', ')] - expected $(if ($pair[1]) { 'all four' } else { 'a leading run' }) of [$($terms -join ', ')] in that order"
                }
            }
        }
    }
    foreach ($field in $fieldTexts) {
        foreach ($rule in $rules) {
            foreach ($m in $rule.Regex.Matches($field.Text)) {
                Add-Finding 'FORBIDDEN-FIELD' "$($field.Where): '$($m.Value)' - $($rule.Id): $($rule.Reason)"
            }
        }
    }

    foreach ($finding in $findings) { Write-Host $finding }
    if ($findings.Count -gt 0) {
        Write-Host "assert-promotion: FAIL ($($findings.Count) finding(s))"
        exit 1
    }
    Write-Host "assert-promotion: PASS (rules=$($rules.Count) published-files=$($sources.Count) field-texts=$($fieldTexts.Count))"
    exit 0
} catch {
    Write-Host $_.Exception.Message
    Write-Host "assert-promotion: COULD NOT VERIFY ($($_.Exception.Message))"
    exit 2
}
