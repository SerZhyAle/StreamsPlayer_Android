#requires -Version 7.0
<#
.SYNOPSIS
    Gate of DOC-INTERNAL-QUALITY rules 1 and 2 (S0051): the document registry, checked in both directions.

.DESCRIPTION
    Reads docs/DOCUMENT_REGISTRY.jsonl (record shape 1, declared in .sza-canon.json) and tools/docs/registry-ignore.txt.
    The canon's document_registry/validate checks declared paths and indexable records; it reads no reverse coverage
    (item L), so this gate runs it and adds what it lacks.

    Findings, one per line, then the verdict line:
      BAD-RECORD         a record lacks a field, repeats an id, has a role other than source or render, a `generated`
                         that disagrees with its role, or a render record without an existing `rendered_by` tool
      NO-FILE            a record path matches no file on disk
      UNDECLARED         a file in the maintained area that no record and no ignore line names (rule 2)
      TWO-HOMES          a file two records name, or a record and an ignore line (rule 1)
      IGNORED-BY-GIT     a record names a file git ignores (item K, the sentence on inaccessible files)
      STALE-IGNORE       an ignore line that matches no file, or whose reason is under four words
      NO-INDEX           a record under docs/ that docs/README.md does not mention
      BAD-DESIGN-RECORD  a `design_record` that resolves to no file or heading
      STAMP              .sza-canon.json does not declare this registry
      VALIDATOR          what the canon's validator reported

    The maintained area is docs/, site/, phone/src/main/assets/help/ and every *.md git sees.

.PARAMETER RepoRoot
    Repository root. Default: two levels above this script's folder.

.NOTES
    Exit codes (CHECK-VERDICT): 0 passed; 1 at least one finding; 2 could not verify (no registry, no checkout, a validator
    that could not run). The plugin not being installed (a CI runner) is a named note, not a verdict: this gate's own
    forward check covers the same paths. The last line on stdout is the verdict line.
#>
[CmdletBinding()]
param([string]$RepoRoot = '')

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_docs-lib.ps1')

$findings = [Collections.Generic.List[string]]::new()
function Add-Finding([string]$Kind, [string]$Message) { $findings.Add("$Kind $Message") }

function Find-RegistryValidator {
    $roots = @($env:SZA_HARNESS_ROOT)
    $home_ = if ($env:USERPROFILE) { $env:USERPROFILE } else { $env:HOME }
    if ($home_) {
        $cache = Join-Path $home_ '.claude/plugins/cache/sza-unified-rules/sza'
        if (Test-Path -LiteralPath $cache) {
            $roots += @(Get-ChildItem -LiteralPath $cache -Directory | Sort-Object { $v = $null; if ([version]::TryParse($_.Name, [ref]$v)) { $v } else { [version]'0.0' } } -Descending |
                    ForEach-Object { Join-Path $_.FullName 'tools/harness' })
        }
    }
    foreach ($root in $roots) {
        if (-not $root) { continue }
        $candidate = Join-Path $root 'document_registry/validate.ps1'
        if (Test-Path -LiteralPath $candidate) { return $candidate }
    }
    return $null
}

try {
    $root = Resolve-DocsRoot -Given $RepoRoot -ScriptRoot $PSScriptRoot
    $problems = [Collections.Generic.List[string]]::new()
    $records = Read-DocRegistry -Root $root -Problems $problems
    foreach ($problem in $problems) { Add-Finding 'BAD-RECORD' $problem }
    $repoFiles = Get-RepoFiles -Root $root
    $required = @('id', 'title', 'category', 'audience', 'paths', 'published', 'indexable', 'product_areas', 'update_triggers', 'generated', 'role')
    $homes = @{}
    $ids = @{}
    foreach ($record in $records) {
        $id = [string](Get-RecordField $record 'id')
        foreach ($field in $required) {
            if ($null -eq $record.PSObject.Properties[$field]) { Add-Finding 'BAD-RECORD' "${id}: no field '$field'" }
        }
        if ($ids.ContainsKey($id)) { Add-Finding 'BAD-RECORD' "${id}: the id is used twice" }
        $ids[$id] = $true
        $role = [string](Get-RecordField $record 'role')
        if ($role -notin 'source', 'render') { Add-Finding 'BAD-RECORD' "${id}: role '$role' is neither source nor render" }
        if ([bool](Get-RecordField $record 'generated') -ne ($role -eq 'render')) { Add-Finding 'BAD-RECORD' "${id}: generated and role '$role' disagree" }
        if (@(Get-RecordField $record 'product_areas').Count -eq 0) { Add-Finding 'BAD-RECORD' "${id}: no product area" }
        if (@(Get-RecordField $record 'update_triggers').Count -eq 0) { Add-Finding 'BAD-RECORD' "${id}: no update trigger" }
        if ($role -eq 'render') {
            $tool = [string](Get-RecordField $record 'rendered_by')
            if (-not $tool) { Add-Finding 'BAD-RECORD' "${id}: a render record names its tool in rendered_by" }
            elseif (-not (Test-Path -LiteralPath (Join-Path $root $tool) -PathType Leaf)) { Add-Finding 'BAD-RECORD' "${id}: rendered_by '$tool' does not exist" }
        }
        foreach ($pattern in @(Get-RecordField $record 'paths')) {
            $matched = @(Get-DiskMatches -Root $root -Pattern ([string]$pattern))
            if ($matched.Count -eq 0) { Add-Finding 'NO-FILE' "${id}: no file matches $pattern"; continue }
            foreach ($rel in $matched) {
                if (-not $repoFiles.Contains($rel)) { Add-Finding 'IGNORED-BY-GIT' "${id}: $rel is ignored by git and has no place in this registry" ; continue }
                if ($homes.ContainsKey($rel) -and $homes[$rel] -ne $id) { Add-Finding 'TWO-HOMES' "$rel is named by $($homes[$rel]) and $id" }
                $homes[$rel] = $id
            }
        }
    }

    # Reverse coverage: the tree against the records and the ignore list.
    $ignores = [Collections.Generic.List[object]]::new()
    $ignorePath = Join-Path $root 'tools/docs/registry-ignore.txt'
    if (-not (Test-Path -LiteralPath $ignorePath -PathType Leaf)) { throw 'tools/docs/registry-ignore.txt not found' }
    foreach ($line in (Read-DocsText $ignorePath).Split("`n")) {
        if (-not $line.Trim() -or $line.TrimStart().StartsWith('#')) { continue }
        $parts = $line -split '\s+#\s+', 2
        $ignores.Add([pscustomobject]@{ Glob = $parts[0].Trim(); Regex = (ConvertTo-GlobRegex $parts[0].Trim()); Reason = $(if ($parts.Count -gt 1) { $parts[1].Trim() } else { '' }); Used = $false })
    }
    foreach ($entry in $ignores) {
        if (@($entry.Reason -split '\s+' | Where-Object { $_ }).Count -lt 4) { Add-Finding 'STALE-IGNORE' "'$($entry.Glob)' carries no reason of at least four words" }
    }
    $maintained = [regex]'^(docs/|site/|phone/src/main/assets/help/)|\.md$'
    $checked = 0
    foreach ($rel in $repoFiles) {
        $ignoredBy = $null
        foreach ($entry in $ignores) { if ($entry.Regex.IsMatch($rel)) { $entry.Used = $true; $ignoredBy = $entry; break } }
        if (-not $maintained.IsMatch($rel)) { continue }
        $checked++
        if ($homes.ContainsKey($rel)) {
            if ($ignoredBy) { Add-Finding 'TWO-HOMES' "$rel is named by $($homes[$rel]) and by the ignore line '$($ignoredBy.Glob)'" }
            continue
        }
        if (-not $ignoredBy) { Add-Finding 'UNDECLARED' "$rel is in a maintained place and no record names it" }
    }
    foreach ($entry in $ignores) { if (-not $entry.Used) { Add-Finding 'STALE-IGNORE' "'$($entry.Glob)' matches no file" } }

    # docs/README.md is the reader's index: every record under docs/ is reachable from it.
    $indexPath = Join-Path $root 'docs/README.md'
    if (-not (Test-Path -LiteralPath $indexPath -PathType Leaf)) { Add-Finding 'NO-INDEX' 'docs/README.md does not exist' }
    else {
        $index = Read-DocsText $indexPath
        foreach ($record in $records) {
            foreach ($pattern in @(Get-RecordField $record 'paths')) {
                $text = ([string]$pattern).Replace('\', '/')
                if (-not $text.StartsWith('docs/') -or $text -eq 'docs/README.md') { continue }
                $needle = $text.Substring(5)
                if ($needle.Contains('*')) { $needle = $needle.Substring(0, $needle.LastIndexOf('/') + 1) }
                if (-not $needle) { $needle = $text.Substring(5).Split('*')[0] }
                if (-not $index.Contains($needle)) { Add-Finding 'NO-INDEX' "$(Get-RecordField $record 'id'): docs/README.md does not mention $needle" }
            }
        }
    }

    # A permanent variant is recorded once, in a dated design record, and the row points at its heading.
    foreach ($record in $records) {
        $reference = [string](Get-RecordField $record 'design_record')
        if (-not $reference) { continue }
        $parts = $reference -split '#', 2
        $target = Join-Path $root $parts[0]
        if (-not (Test-Path -LiteralPath $target -PathType Leaf)) { Add-Finding 'BAD-DESIGN-RECORD' "$(Get-RecordField $record 'id'): $($parts[0]) does not exist"; continue }
        if ($parts.Count -eq 2 -and -not (Get-MarkdownAnchors (Read-DocsText $target)).Contains($parts[1])) {
            Add-Finding 'BAD-DESIGN-RECORD' "$(Get-RecordField $record 'id'): $($parts[0]) has no heading '#$($parts[1])'"
        }
    }

    $stampPath = Join-Path $root '.sza-canon.json'
    if (-not (Test-Path -LiteralPath $stampPath -PathType Leaf)) { Add-Finding 'STAMP' '.sza-canon.json does not exist' }
    else {
        $stamp = Read-DocsText $stampPath | ConvertFrom-Json
        if ([string](Get-RecordField $stamp 'docRegistryFile') -ne 'docs/DOCUMENT_REGISTRY.jsonl') { Add-Finding 'STAMP' "docRegistryFile is not docs/DOCUMENT_REGISTRY.jsonl" }
        if ([string](Get-RecordField $stamp 'docRegistryShape') -ne '1') { Add-Finding 'STAMP' 'docRegistryShape is not 1' }
    }

    $validator = Find-RegistryValidator
    if ($validator) {
        $output = & pwsh -NoProfile -File $validator -RepoRoot $root 2>&1 | Out-String
        $code = $LASTEXITCODE
        if ($code -eq 1) { foreach ($line in ($output -split '\r?\n' | Where-Object { $_ -match '^\s+\S' })) { Add-Finding 'VALIDATOR' $line.Trim() } }
        elseif ($code -ne 0) { throw "the canon's document_registry/validate exited ${code}: $($output.Trim())" }
    } else {
        Write-Host "note: the canon's document_registry/validate is not installed here; the forward check above stands in for it"
    }

    foreach ($finding in $findings) { Write-Host $finding }
    if ($findings.Count -gt 0) {
        Write-Host "assert-doc-registry: FAIL ($($findings.Count) finding(s))"
        exit 1
    }
    Write-Host "assert-doc-registry: PASS (records=$($records.Count) files=$checked ignore-lines=$($ignores.Count))"
    exit 0
} catch {
    Write-Host $_.Exception.Message
    Write-Host "assert-doc-registry: COULD NOT VERIFY ($($_.Exception.Message))"
    exit 2
}
