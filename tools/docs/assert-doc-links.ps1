#requires -Version 7.0
<#
.SYNOPSIS
    Gate of DOC-INTERNAL-QUALITY rules 3, 6 and 7 and DOC-EXTERNAL-QUALITY rule 7 (S0051): links, anchors and assets.

.DESCRIPTION
    Judges every *.md a registry record names and the site's own pages (site/*.html). Code blocks, code spans and HTML
    comments are not read: a path written as code is not a link. A record with `"frozen": true` is exempt for the links
    inside it (item Q); a link into it from outside must still resolve. The help pages have their own gate
    (tools/help/assert-help-map.ps1) and the site's outside loads are held by tools/site/build-site.ps1 -Check.

    Findings, one per line, then the verdict line:
      BROKEN-LINK    a relative link resolves to no file or folder, or leaves the repository
      BROKEN-ANCHOR  a fragment names no heading or id in its target
      MISSING-ASSET  an image resolves to no file, or to a file git ignores
      INSECURE-LINK  an http:// link in prose (rule 7); loopback addresses are allowed
      REMOTE-EMBED   a document loads a script, font, frame or image from another host, or carries a <script> (rule 7)

.PARAMETER RepoRoot
    Repository root. Default: two levels above this script's folder.

.NOTES
    Exit codes (CHECK-VERDICT): 0 passed; 1 at least one finding; 2 could not verify (no registry, no checkout).
    The last line on stdout is the verdict line.
#>
[CmdletBinding()]
param([string]$RepoRoot = '')

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_docs-lib.ps1')

$findings = [Collections.Generic.List[string]]::new()
function Add-Finding([string]$Kind, [string]$Message) { $findings.Add("$Kind $Message") }

$anchorCache = @{}
function Get-TargetAnchors([string]$Path) {
    if (-not $anchorCache.ContainsKey($Path)) {
        $text = Read-DocsText $Path
        if ($Path -match '\.html?$') {
            $ids = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
            foreach ($m in [regex]::Matches($text, '\b(?:id|name)="([^"]+)"')) { $null = $ids.Add($m.Groups[1].Value) }
            $anchorCache[$Path] = $ids
        } else {
            $anchorCache[$Path] = Get-MarkdownAnchors $text
        }
    }
    return , $anchorCache[$Path]
}

function Test-LocalLink([string]$Rel, [int]$Line, [string]$Target, [bool]$IsImage) {
    $where = "${Rel}:$Line"
    if ($Target -match '^(?i)(https:|mailto:|tel:|data:)') { return }
    $parts = $Target -split '#', 2
    $pathPart = ($parts[0] -split '\?', 2)[0]
    $fragment = if ($parts.Count -gt 1) { [uri]::UnescapeDataString($parts[1]) } else { '' }
    $resolved = Join-Path $script:root $Rel
    if ($pathPart) {
        $relative = [uri]::UnescapeDataString($pathPart) -replace '/', [IO.Path]::DirectorySeparatorChar
        $resolved = [IO.Path]::GetFullPath((Join-Path (Split-Path $resolved -Parent) $relative))
        if (-not $resolved.StartsWith($script:root, [StringComparison]::OrdinalIgnoreCase)) { Add-Finding 'BROKEN-LINK' "${where}: '$Target' leaves the repository"; return }
    }
    if (-not (Test-Path -LiteralPath $resolved)) {
        if ($IsImage) { Add-Finding 'MISSING-ASSET' "${where}: '$Target' resolves to no file" } else { Add-Finding 'BROKEN-LINK' "${where}: '$Target' resolves to no file" }
        return
    }
    if ($IsImage -and (Test-Path -LiteralPath $resolved -PathType Leaf)) {
        $targetRel = [IO.Path]::GetRelativePath($script:root, $resolved).Replace('\', '/')
        if (-not $script:repoFiles.Contains($targetRel)) { Add-Finding 'MISSING-ASSET' "${where}: '$Target' is ignored by git, so no reader has it" }
    }
    if ($fragment -and (Test-Path -LiteralPath $resolved -PathType Leaf) -and $resolved -match '\.(md|html?)$' -and -not (Get-TargetAnchors $resolved).Contains($fragment)) {
        Add-Finding 'BROKEN-ANCHOR' "${where}: '$Target' names no heading or id there"
    }
}

function Test-InsecureLink([string]$Where, [string]$Target) {
    if ($Target -match '^(?i)http://' -and $Target -notmatch '^(?i)http://(localhost|127\.0\.0\.1|\[::1\])([:/]|$)') {
        Add-Finding 'INSECURE-LINK' "${Where}: '$Target' is not https"
    }
}

try {
    $script:root = Resolve-DocsRoot -Given $RepoRoot -ScriptRoot $PSScriptRoot
    $records = Read-DocRegistry -Root $script:root
    $script:repoFiles = Get-RepoFiles -Root $script:root
    $documents = @(Get-MarkdownCorpus -Root $script:root -Records $records)
    $links = 0
    foreach ($rel in $documents) {
        $lines = Get-MarkdownProseLines (Read-DocsText (Join-Path $script:root $rel))
        for ($i = 0; $i -lt $lines.Count; $i++) {
            $line = $lines[$i]
            if (-not $line) { continue }
            $number = $i + 1
            foreach ($m in [regex]::Matches($line, '(!?)\[[^\]]*\]\(\s*<?([^)\s>]+)')) {
                $links++
                $target = $m.Groups[2].Value
                $isImage = $m.Groups[1].Value -eq '!'
                Test-InsecureLink "${rel}:$number" $target
                if ($isImage -and $target -match '^(?i)https?://') { Add-Finding 'REMOTE-EMBED' "${rel}:${number}: an image loaded from '$target'"; continue }
                if ($target -notmatch '^(?i)http://') { Test-LocalLink $rel $number $target $isImage }
            }
            $reference = [regex]::Match($line, '^\s{0,3}\[[^\]]+\]:\s*<?(\S+?)>?(\s|$)')
            if ($reference.Success) {
                $links++
                Test-InsecureLink "${rel}:$number" $reference.Groups[1].Value
                if ($reference.Groups[1].Value -notmatch '^(?i)http://') { Test-LocalLink $rel $number $reference.Groups[1].Value $false }
            }
            foreach ($m in [regex]::Matches($line, '(?<![(<"''\w])http://[^\s)>"'']+')) { Test-InsecureLink "${rel}:$number" $m.Value }
            if ($line -match '(?i)<script\b') { Add-Finding 'REMOTE-EMBED' "${rel}:${number}: a document carries no <script>" }
            foreach ($m in [regex]::Matches($line, '(?i)<(img|iframe|embed|object|video|audio|source|link)\b[^>]*\b(?:src|href|data)\s*=\s*"((?:https?:)?//[^"]+)"')) {
                Add-Finding 'REMOTE-EMBED' "${rel}:${number}: <$($m.Groups[1].Value)> loads '$($m.Groups[2].Value)'"
            }
            foreach ($m in [regex]::Matches($line, '(?i)<(a|img)\b[^>]*\b(?:href|src)\s*=\s*"([^"]+)"')) {
                $links++
                $target = $m.Groups[2].Value
                Test-InsecureLink "${rel}:$number" $target
                if ($target -notmatch '^(?i)(https?:)?//') { Test-LocalLink $rel $number $target ($m.Groups[1].Value -ieq 'img') }
            }
        }
    }

    # The site's own pages: every relative link and fragment resolves, every outside link is https.
    $pages = @($script:repoFiles | Where-Object { $_ -match '^site/[^/]+\.html$' })
    foreach ($rel in $pages) {
        $html = Read-DocsText (Join-Path $script:root $rel)
        foreach ($m in [regex]::Matches($html, '(?i)<a\b[^>]*\bhref\s*=\s*"([^"]+)"')) {
            $links++
            $target = $m.Groups[1].Value
            $number = ($html.Substring(0, $m.Index).Split("`n")).Count
            Test-InsecureLink "${rel}:$number" $target
            if ($target -notmatch '^(?i)(https?:)?//') { Test-LocalLink $rel $number $target $false }
        }
    }

    foreach ($finding in $findings) { Write-Host $finding }
    if ($findings.Count -gt 0) {
        Write-Host "assert-doc-links: FAIL ($($findings.Count) finding(s))"
        exit 1
    }
    Write-Host "assert-doc-links: PASS (documents=$($documents.Count) pages=$($pages.Count) links=$links)"
    exit 0
} catch {
    Write-Host $_.Exception.Message
    Write-Host "assert-doc-links: COULD NOT VERIFY ($($_.Exception.Message))"
    exit 2
}
