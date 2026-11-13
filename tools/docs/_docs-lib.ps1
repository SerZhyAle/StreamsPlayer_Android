#requires -Version 7.0
<#
.SYNOPSIS
    Shared helpers of the documentation gates in tools/docs (S0051). Dot-sourced; prints nothing.

.DESCRIPTION
    Every gate here takes -RepoRoot, so the self-test can run it on a seeded copy, and judges the files on disk: a file
    is seen when git would see it, tracked or not, and a file git ignores is not part of the corpus (DOC-INTERNAL-QUALITY
    item K).
#>

Set-StrictMode -Version Latest

function Resolve-DocsRoot {
    param([string]$Given, [string]$ScriptRoot)
    $path = if ($Given) { $Given } else { Join-Path $ScriptRoot '../..' }
    return [IO.Path]::GetFullPath($path).TrimEnd('\', '/')
}

function Read-DocsText {
    param([Parameter(Mandatory)][string]$Path)
    return [IO.File]::ReadAllText($Path, [Text.UTF8Encoding]::new($false)).Replace("`r`n", "`n")
}

function ConvertTo-GlobRegex {
    # `*` stays inside one folder and `**` crosses folders: the same reading the registry's plain paths get on disk.
    param([Parameter(Mandatory)][string]$Glob)
    $text = [regex]::Escape($Glob.Replace('\', '/')).Replace('\*\*', '.*').Replace('\*', '[^/]*')
    return [regex]::new("^$text$", [Text.RegularExpressions.RegexOptions]::CultureInvariant)
}

function Get-RepoFiles {
    # What git sees, tracked or not, minus what it ignores and what was deleted. Throws outside a checkout.
    param([Parameter(Mandatory)][string]$Root)
    $listed = & git -C $Root ls-files --cached --others --exclude-standard 2>&1
    if ($LASTEXITCODE -ne 0) { throw "git ls-files failed in ${Root}: $listed" }
    $seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($line in $listed) {
        $rel = ([string]$line).Trim('"')
        if ($rel -match '\\[0-9]{3}') { continue }
        if (Test-Path -LiteralPath (Join-Path $Root $rel) -PathType Leaf) { $null = $seen.Add($rel) }
    }
    return , $seen
}

function Get-DiskMatches {
    # The files on disk one registry path names, repository-relative, whether git sees them or not.
    param([Parameter(Mandatory)][string]$Root, [Parameter(Mandatory)][string]$Pattern)
    $full = Join-Path $Root ($Pattern -replace '/', [IO.Path]::DirectorySeparatorChar)
    return @(Get-ChildItem -Path $full -File -Force -ErrorAction SilentlyContinue | ForEach-Object {
            [IO.Path]::GetRelativePath($Root, $_.FullName).Replace('\', '/')
        })
}

function Read-DocRegistry {
    # Returns the records of docs/DOCUMENT_REGISTRY.jsonl; a line that is not JSON is reported through $Problems.
    param([Parameter(Mandatory)][string]$Root, [Collections.Generic.List[string]]$Problems)
    $path = Join-Path $Root 'docs/DOCUMENT_REGISTRY.jsonl'
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "registry not found: docs/DOCUMENT_REGISTRY.jsonl" }
    $records = [Collections.Generic.List[object]]::new()
    $number = 0
    foreach ($line in (Read-DocsText $path).Split("`n")) {
        $number++
        if (-not $line.Trim()) { continue }
        try { $records.Add(($line | ConvertFrom-Json)) } catch {
            if ($null -ne $Problems) { $Problems.Add("docs/DOCUMENT_REGISTRY.jsonl:$number is not JSON") }
        }
    }
    return , $records
}

function Get-RecordField {
    param($Record, [Parameter(Mandatory)][string]$Name)
    if ($null -eq $Record) { return $null }
    $property = $Record.PSObject.Properties[$Name]
    if ($null -eq $property) { return $null }
    return $property.Value
}

function Get-MarkdownCorpus {
    # The Markdown files the prose gates judge: every *.md a registry record names, except a frozen record's.
    param([Parameter(Mandatory)][string]$Root, [Parameter(Mandatory)]$Records)
    $files = [Collections.Generic.SortedSet[string]]::new([StringComparer]::Ordinal)
    foreach ($record in $Records) {
        if (Get-RecordField $record 'frozen') { continue }
        foreach ($pattern in @(Get-RecordField $record 'paths')) {
            if ([string]$pattern -notlike '*.md') { continue }
            foreach ($rel in (Get-DiskMatches -Root $Root -Pattern ([string]$pattern))) { $null = $files.Add($rel) }
        }
    }
    return @($files)
}

function Get-MarkdownProseLines {
    # The lines of a Markdown text with fenced blocks, inline code spans and HTML comments blanked. Line numbers hold.
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Text)
    $withoutComments = [regex]::Replace($Text, '(?s)<!--.*?-->', { param($m) [regex]::Replace($m.Value, '[^\n]', '') })
    $lines = $withoutComments.Split("`n")
    $fence = ''
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        $open = [regex]::Match($line, '^\s{0,3}(`{3,}|~{3,})')
        if ($fence) {
            if ($open.Success -and $open.Groups[1].Value.StartsWith($fence)) { $fence = '' }
            $lines[$i] = ''
            continue
        }
        if ($open.Success) { $fence = $open.Groups[1].Value; $lines[$i] = ''; continue }
        $lines[$i] = [regex]::Replace($line, '(?<!`)(`+)(?!`).+?(?<!`)\1(?!`)', '')
    }
    return , $lines
}

function Get-MarkdownAnchors {
    # The anchors a Markdown file answers: GitHub's heading slugs, `-1` on a repeat, and explicit id attributes.
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Text)
    $anchors = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $counts = @{}
    $fence = ''
    foreach ($line in $Text.Split("`n")) {
        $open = [regex]::Match($line, '^\s{0,3}(`{3,}|~{3,})')
        if ($fence) {
            if ($open.Success -and $open.Groups[1].Value.StartsWith($fence)) { $fence = '' }
            continue
        }
        if ($open.Success) { $fence = $open.Groups[1].Value; continue }
        $heading = [regex]::Match($line, '^#{1,6}\s+(.+?)\s*#*\s*$')
        if ($heading.Success) {
            $title = [regex]::Replace($heading.Groups[1].Value, '!?\[([^\]]*)\]\([^)]*\)', '$1')
            $slug = [regex]::Replace($title.ToLowerInvariant(), '[^\p{L}\p{M}\p{N}\s_-]', '')
            $slug = [regex]::Replace($slug.Trim(), '\s', '-')
            if ($counts.ContainsKey($slug)) { $counts[$slug]++; $null = $anchors.Add("$slug-$($counts[$slug])") }
            else { $counts[$slug] = 0; $null = $anchors.Add($slug) }
        }
        foreach ($id in [regex]::Matches($line, '\b(?:id|name)="([^"]+)"')) { $null = $anchors.Add($id.Groups[1].Value) }
    }
    return , $anchors
}

function Test-CyrillicShare {
    # True when at least half of the letters of a text are Cyrillic; a text under 40 letters proves nothing and passes.
    param([string]$Text)
    $letters = [regex]::Matches($Text, '\p{L}').Count
    if ($letters -lt 40) { return $true }
    return ([regex]::Matches($Text, '\p{IsCyrillic}').Count / $letters) -ge 0.5
}

function Get-NormalizedText {
    param([AllowEmptyString()][string]$Text)
    return [regex]::Replace($Text, '\s+', ' ').Trim()
}

function Get-TextFingerprint {
    # The first 16 hex of the SHA-256 of a block with its whitespace collapsed, so a re-wrap of a paragraph flags nothing.
    param([AllowEmptyString()][string]$Text)
    $bytes = [Text.Encoding]::UTF8.GetBytes((Get-NormalizedText $Text))
    return (([Security.Cryptography.SHA256]::HashData($bytes) | ForEach-Object { $_.ToString('x2') }) -join '').Substring(0, 16)
}

function Add-TranslatedBlock {
    param([hashtable]$Blocks, [string]$Doc, [string]$Block, [string]$Locale, [AllowEmptyString()][string]$Text)
    if (-not (Get-NormalizedText $Text)) { return }
    $key = "$Doc`t$Block"
    if (-not $Blocks.ContainsKey($key)) { $Blocks[$key] = @{ Doc = $Doc; Block = $Block } }
    $Blocks[$key][$Locale] = $Text
}

function Get-HelpLocaleBlocks {
    # block name -> text for one locale of one help source: the same blocks the page shows, one per element.
    param($Locale)
    $blocks = [ordered]@{}
    foreach ($field in 'title', 'purpose', 'orderNote') {
        $value = Get-RecordField $Locale $field
        if ($value) { $blocks[$field] = [string]$value }
    }
    $steps = @(Get-RecordField $Locale 'steps')
    if ($steps.Count -gt 0 -and $null -ne $steps[0]) { $blocks['steps'] = $steps -join "`n" }
    $groups = @(Get-RecordField $Locale 'groups')
    if ($groups.Count -gt 0 -and $null -ne $groups[0]) { $blocks['groups'] = @($groups | ForEach-Object { [string](Get-RecordField $_ 'title') }) -join "`n" }
    $elements = Get-RecordField $Locale 'elements'
    if ($null -ne $elements) {
        foreach ($property in $elements.PSObject.Properties) {
            $parts = foreach ($part in 'name', 'summary', 'details', 'undo') { [string](Get-RecordField $property.Value $part) }
            $blocks["elements.$($property.Name)"] = ($parts | Where-Object { $_ }) -join "`n"
        }
    }
    return $blocks
}

function Get-PolicySections {
    # The sections of one privacy policy file by position, with the table the permissions tool generates cut out.
    param([Parameter(Mandatory)][string]$Text)
    $withoutTable = [regex]::Replace($Text, '(?s)<!-- permissions:begin.*?<!-- permissions:end -->', '')
    $sections = [ordered]@{}
    $parts = [regex]::Split($withoutTable, '(?m)^(?=## )')
    for ($i = 0; $i -lt $parts.Count; $i++) { $sections['s{0:d2}' -f $i] = $parts[$i] }
    return $sections
}

function Get-PortalBlocks {
    # page -> locale -> text, from the handover file: `## Page `x``, then one `###` heading per language.
    param([Parameter(Mandatory)][string]$Text)
    $names = @{ 'English' = 'en'; "$([char]0x420)$([char]0x443)$([char]0x441)$([char]0x441)$([char]0x43A)$([char]0x438)$([char]0x439)" = 'ru'; "$([char]0x423)$([char]0x43A)$([char]0x440)$([char]0x430)$([char]0x457)$([char]0x43D)$([char]0x441)$([char]0x44C)$([char]0x43A)$([char]0x430)" = 'uk' }
    $blocks = [ordered]@{}
    $page = ''
    $locale = ''
    foreach ($line in $Text.Split("`n")) {
        $pageHeading = [regex]::Match($line, '^## Page `([^`]+)`')
        if ($pageHeading.Success) { $page = $pageHeading.Groups[1].Value; $locale = ''; $blocks[$page] = @{}; continue }
        if ($line -match '^## ') { $page = ''; $locale = ''; continue }
        $localeHeading = [regex]::Match($line, '^### (.+?)\s*$')
        if ($page -and $localeHeading.Success -and $names.ContainsKey($localeHeading.Groups[1].Value)) {
            $locale = $names[$localeHeading.Groups[1].Value]
            $blocks[$page][$locale] = ''
            continue
        }
        if ($page -and $locale) { $blocks[$page][$locale] += "$line`n" }
    }
    return $blocks
}

function Get-TranslatedBlocks {
    # Every block the translations are held to: help sources, policies, permission sentences, portal sections, term base.
    param([Parameter(Mandatory)][string]$Root)
    $blocks = @{}
    foreach ($rel in (Get-DiskMatches -Root $Root -Pattern 'docs/help/content/*.json')) {
        $id = [IO.Path]::GetFileNameWithoutExtension($rel)
        $locales = Get-RecordField (Read-DocsText (Join-Path $Root $rel) | ConvertFrom-Json) 'locales'
        foreach ($locale in 'en', 'ru', 'uk') {
            $block = Get-RecordField $locales $locale
            if ($null -eq $block) { continue }
            $parts = Get-HelpLocaleBlocks $block
            foreach ($name in $parts.Keys) { Add-TranslatedBlock $blocks "help/$id" $name $locale $parts[$name] }
        }
    }
    $policies = @{ en = 'docs/PRIVACY_POLICY.md'; ru = 'docs/PRIVACY_POLICY-ru.md'; uk = 'docs/PRIVACY_POLICY-uk.md' }
    foreach ($locale in $policies.Keys) {
        $path = Join-Path $Root $policies[$locale]
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { continue }
        $sections = Get-PolicySections (Read-DocsText $path)
        foreach ($name in $sections.Keys) { Add-TranslatedBlock $blocks 'privacy' $name $locale $sections[$name] }
    }
    $purposesPath = Join-Path $Root 'docs/privacy/permission-purposes.json'
    if (Test-Path -LiteralPath $purposesPath -PathType Leaf) {
        $purposes = Read-DocsText $purposesPath | ConvertFrom-Json
        foreach ($locale in 'en', 'ru', 'uk') {
            $labels = Get-RecordField (Get-RecordField $purposes 'labels') $locale
            if ($null -ne $labels) { Add-TranslatedBlock $blocks 'privacy-permissions' 'labels' $locale (@($labels.PSObject.Properties | ForEach-Object { [string]$_.Value }) -join "`n") }
            foreach ($property in (Get-RecordField $purposes 'purposes').PSObject.Properties) {
                Add-TranslatedBlock $blocks 'privacy-permissions' $property.Name $locale ([string](Get-RecordField $property.Value $locale))
            }
        }
    }
    $portalPath = Join-Path $Root 'docs/PORTAL_SECTIONS_STREAMSPLAYER.md'
    if (Test-Path -LiteralPath $portalPath -PathType Leaf) {
        $portal = Get-PortalBlocks (Read-DocsText $portalPath)
        foreach ($page in $portal.Keys) { foreach ($locale in $portal[$page].Keys) { Add-TranslatedBlock $blocks 'portal' $page $locale $portal[$page][$locale] } }
    }
    $termbasePath = Join-Path $Root 'docs/TERMBASE.json'
    if (Test-Path -LiteralPath $termbasePath -PathType Leaf) {
        foreach ($term in @(Get-RecordField (Read-DocsText $termbasePath | ConvertFrom-Json) 'terms')) {
            foreach ($locale in 'en', 'ru', 'uk') {
                $name = [string](Get-RecordField (Get-RecordField $term 'name') $locale)
                Add-TranslatedBlock $blocks 'termbase' ([string](Get-RecordField $term 'id')) $locale ("$name`n" + [string](Get-RecordField (Get-RecordField $term 'definition') $locale))
            }
        }
    }
    return $blocks
}
