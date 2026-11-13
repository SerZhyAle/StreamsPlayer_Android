#requires -Version 7.0
<#
.SYNOPSIS
    Gate of DOC-INTERNAL-QUALITY rule 5 (S0051): the house text style in the prose of the documents.

.DESCRIPTION
    Judges every *.md a registry record names, the text values of the help sources (docs/help/content/*.json) and the
    entries of the term base. Code blocks, code spans and HTML comments are not prose and are not read; a record with
    `"frozen": true` is a finished record and is exempt. The house style applies to prose and UI only, never to code.

    Findings, one per line, then the verdict line:
      LONG-DASH   a typographic long or middle dash; the house style is a plain hyphen
      ELLIPSIS    three periods or the ellipsis character; the house style is two periods
      MISSING-YO  a Russian word from the list below written without its letter yo

    The list holds the words of the app-string test (ExchangeTermsDictionaryTest) and the frequent verbs and participles
    of this corpus. It only holds words that have no spelling without the letter, so a hit is never a different word.

.PARAMETER RepoRoot
    Repository root. Default: two levels above this script's folder.

.NOTES
    Exit codes (CHECK-VERDICT): 0 passed; 1 at least one finding; 2 could not verify (no registry, a source that is not
    JSON). The last line on stdout is the verdict line.
#>
[CmdletBinding()]
param([string]$RepoRoot = '')

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_docs-lib.ps1')

$findings = [Collections.Generic.List[string]]::new()
$longDash = [regex]"[$([char]0x2014)$([char]0x2013)]"
$ellipsis = [regex]"\.\.\.|$([char]0x2026)"
$ignoreCase = [Text.RegularExpressions.RegexOptions]::IgnoreCase
$missingYo = [regex]::new('(?<![\p{L}])(еще|ее|ведет|идет|ждет|дает|берет|передает|счет|четко|четк[а-я]+|учетн[а-я]*|защищенн[а-я]*|закреплен|обновлен|обновленн[а-я]*|емкост[а-я]*|подключенн[а-я]*|отключенн[а-я]*|включенн[а-я]*|сохраненн[а-я]*|удаленн[а-я]*|темн(ая|ый|ой|ую|ом|ые|ых|ым|ое|ого|ому|ыми))(?![\p{L}])', $ignoreCase)

function Test-Prose([string]$Where, [string]$Text) {
    foreach ($m in $script:longDash.Matches($Text)) { $script:findings.Add("LONG-DASH ${Where}: a long dash, the house style is a plain hyphen") }
    foreach ($m in $script:ellipsis.Matches($Text)) { $script:findings.Add("ELLIPSIS ${Where}: '$($m.Value)', the house style is two periods") }
    foreach ($m in $script:missingYo.Matches($Text)) { $script:findings.Add("MISSING-YO ${Where}: '$($m.Value)' is written with the letter yo") }
}

function Test-JsonStrings([string]$Rel, $Node, [string]$Pointer) {
    if ($null -eq $Node) { return }
    if ($Node -is [string]) { Test-Prose "$Rel $Pointer" $Node; return }
    if ($Node -is [Collections.IEnumerable]) {
        $i = 0
        foreach ($item in $Node) { Test-JsonStrings $Rel $item "$Pointer[$i]"; $i++ }
        return
    }
    if ($Node -is [Management.Automation.PSCustomObject]) {
        foreach ($property in $Node.PSObject.Properties) {
            if ($property.Name.StartsWith('$')) { continue }
            Test-JsonStrings $Rel $property.Value "$Pointer.$($property.Name)"
        }
    }
}

try {
    $root = Resolve-DocsRoot -Given $RepoRoot -ScriptRoot $PSScriptRoot
    $records = Read-DocRegistry -Root $root
    $documents = @(Get-MarkdownCorpus -Root $root -Records $records)
    foreach ($rel in $documents) {
        $lines = Get-MarkdownProseLines (Read-DocsText (Join-Path $root $rel))
        for ($i = 0; $i -lt $lines.Count; $i++) {
            if ($lines[$i]) { Test-Prose "${rel}:$($i + 1)" $lines[$i] }
        }
    }
    $jsonFiles = @(Get-DiskMatches -Root $root -Pattern 'docs/help/content/*.json') + @(Get-DiskMatches -Root $root -Pattern 'docs/TERMBASE.json')
    foreach ($rel in $jsonFiles) {
        $source = Read-DocsText (Join-Path $root $rel) | ConvertFrom-Json
        # The term base's patterns hold the words it forbids; its entries and reasons are prose.
        if ($rel -eq 'docs/TERMBASE.json') {
            Test-JsonStrings $rel (Get-RecordField $source 'terms') '$.terms'
            foreach ($entry in @(Get-RecordField $source 'forbidden')) { Test-JsonStrings $rel (Get-RecordField $entry 'reason') "`$.forbidden.$(Get-RecordField $entry 'id').reason" }
            continue
        }
        Test-JsonStrings $rel $source '$'
    }

    foreach ($finding in $findings) { Write-Host $finding }
    if ($findings.Count -gt 0) {
        Write-Host "assert-doc-style: FAIL ($($findings.Count) finding(s))"
        exit 1
    }
    Write-Host "assert-doc-style: PASS (documents=$($documents.Count) sources=$($jsonFiles.Count))"
    exit 0
} catch {
    Write-Host $_.Exception.Message
    Write-Host "assert-doc-style: COULD NOT VERIFY ($($_.Exception.Message))"
    exit 2
}
