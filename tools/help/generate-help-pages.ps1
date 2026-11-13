#requires -Version 7.0
<#
.SYNOPSIS
    Generates the offline in-app help pages (CONTEXT-HELP 0.1) from one source per surface.

.DESCRIPTION
    Ported from FastMediaSorter Android scripts/docs/help/generate-help-pages.ps1 (S0035 item 5) and cut down to what
    this product has: this script writes the pages the package bundles; there is no portal copy and no page manifest,
    and the document registry (docs/DOCUMENT_REGISTRY.jsonl) names the pages as one render record.

    Input, all read-only:
    - the help map (phone/src/main/assets/help/help-map.json): every row with form `page` or `both` names a help
      id, and a row's `pages[]` adds one help id per entry; ids are de-duplicated in map order;
    - one source per help id, <SourceRoot>/<help-id>.json, plus <SourceRoot>/index.json for the help index page;
    - the UI strings (phone/src/main/res/values[-<locale>]/strings*.xml): an element's name is the value of
      @string/<labelKey>, so a renamed button is renamed in its help by the same edit (rule 13);
    - phone/src/main/assets/help/img/<help-id>-<locale>-light.jpg (optional -dark.jpg): a screenshot section is
      written only for a page whose light image exists;
    - phone/src/main/assets/help/img/capture.json (optional): { "versionName", "date" } of the build the screenshots
      were captured against, written by capture-help-screenshots.ps1; the footer prints it (rule 8 item 7);
    - docs/TERMBASE.json and the dictionary table it names: the Terms section of the help index (S0051).

    The help index also carries the subject index (DOC-EXTERNAL-QUALITY section 8 item B): every page title and element
    name of the locale, sorted by its alphabet, linking its pages. It is rendered from the sources, so no list is kept by hand.

    Source schema (one file per help id; the index file carries only `help`, `locales.<l>.title`, `locales.<l>.purpose`):
        { "help": "catalog", "surface": "MainActivity",
          "elements": [ { "id": "filter", "viewId": "topToolbar", "labelKey": "filter_and_sort" } ],
          "related":  [ { "help": "player" }
                        | { "url": "https://..", "label": { "en": "..", "ru": "..", "uk": ".." } } ],
          "locales":  { "en": { "title", "purpose", "steps": [ "text, may name a marker as (3)" ],
                                "orderNote": "used when there are no steps",
                                "groups": [ { "title", "elements": [ "<element id>" ] } ],
                                "elements": { "<element id>": { "summary", "details", "undo", "shortcut" } } },
                        "ru": { .. }, "uk": { .. } } }
    - `mark` (optional, root): "head" puts the exchange server's full form (128 px) above the title of a page about the
      server; "inline" puts the flat mark (28 px) beside the title of the page of a surface that shows it
      (EXCHANGE-MARK rules 10 and 11). The picture is img/exchange-mark-full.svg or img/exchange-mark-small.svg, its
      alt text is the proper name "Media Exchange Server" in every locale.
    - Marker numbers are the 1-based position in the root `elements` array, in every locale.
    - `steps` holds at most seven items; a "(N)" in a step becomes a link to marker N.
    - With `groups`, every element belongs to exactly one group and the entries are listed per group under an <h3>.
    - `shortcut` is a key combination written with `+` ("Ctrl+Shift+F"); alternatives are separated by " / ".
    - An element without `labelKey` may carry `name` in the locale entry instead; a labelKey is the normal case.
    - `details` and `purpose` are plain text; a blank line starts a new paragraph. All text is HTML-escaped.

    Output, written only when every input is valid (all problems are listed first, nothing is half-written):
    - <OutAssets>/<locale>/<help-id>.html and <OutAssets>/<locale>/index.html (page links are <help-id>.html,
      css is ../help.css, images are ../img/..);
    - <OutAssets>/help.css, copied from the help.css beside this script.
    Files are UTF-8 without BOM, LF line endings, and are rewritten only when their content changes. A rerun with
    unchanged inputs and the same -Date is byte-identical. Pages carry no script, no remote font and no URL
    outside an <a href>; the generator checks that on every page it renders.

    With -Check nothing is written: every page is rendered and compared with the file on disk, and a differing page, a
    missing page or a page no source renders is a finding (DOC-INTERNAL-QUALITY rule 4). The footer date is fixed for the
    comparison: the capture record's date, else the date the pages on disk already carry, so the day of the run never
    decides the verdict.

.PARAMETER RepoRoot
    Repository root. Default: two levels above this script's folder.

.PARAMETER Date
    Generation date (yyyy-MM-dd) printed in a footer that has no capture record. Default: today.

.PARAMETER Locales
    Locales to generate. Default: en, ru, uk. Every source needs a block for each; a string missing from a
    non-English values folder falls back to English. Comma-separated values are accepted.

.PARAMETER SourceRoot
    Folder holding <help-id>.json for every mapped help id and index.json. Default: <RepoRoot>/docs/help/content.

.PARAMETER HelpIds
    Optional comma-separated page ids to write. All source pages are still validated. Name index explicitly
    when its contents changed; common CSS remains synchronized.

.PARAMETER MapPath
    The help map. Default: <RepoRoot>/phone/src/main/assets/help/help-map.json.

.PARAMETER ImageRoot
    Folder searched for the screenshots. Default: <RepoRoot>/phone/src/main/assets/help/img.

.PARAMETER OutAssets
    Folder receiving the in-app pages and help.css. Default: <RepoRoot>/phone/src/main/assets/help.

.PARAMETER TermbasePath
    The term base. Default: <RepoRoot>/docs/TERMBASE.json.

.PARAMETER Check
    Write nothing; compare what would be written with the disk.

.EXAMPLE
    pwsh -NoProfile -File tools/help/generate-help-pages.ps1

.EXAMPLE
    pwsh -NoProfile -File tools/help/generate-help-pages.ps1 -Check

.NOTES
    Exit codes (CHECK-VERDICT):
      0 - pages written (or already up to date); with -Check, the disk equals the sources
      1 - source error: a mapped help id has no source file, a source is invalid or lacks a locale, a labelKey is
          missing from the English strings, a related target does not resolve, or a rendered page breaks the
          offline rules; with -Check also a page that differs, is missing or has no source
      2 - input not found: the help map, the English string folder, the term base or help.css
    The last line on stdout is the verdict line: `generate-help-pages: PASS (..)`, `FAIL (..)` or `COULD NOT VERIFY (..)`.
#>
[CmdletBinding()]
param(
    [string]$RepoRoot = (Join-Path $PSScriptRoot '../..'),
    [ValidatePattern('^\d{4}-\d{2}-\d{2}$')]
    [string]$Date = (Get-Date -Format 'yyyy-MM-dd'),
    [string[]]$Locales = @('en', 'ru', 'uk'),
    [string[]]$HelpIds = @(),
    [string]$SourceRoot = '',
    [string]$MapPath = '',
    [string]$ImageRoot = '',
    [string]$OutAssets = '',
    [string]$TermbasePath = '',
    [switch]$Check
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:SourceLocale = 'en'
$script:SupportUrl = 'https://github.com/SerZhyAle/StreamsPlayer_Android/issues'
$script:RtlLocales = @('ar', 'ur')
$script:MaxSteps = 7
$script:ImageExtension = 'jpg'

# EXCHANGE-MARK rules 10 and 11 (S0035, an extension made here; FastMediaSorter's generator has none). A source may
# carry "mark": "head" for a page about the exchange server (the full form once, 128 px) or "inline" for the page of a
# surface that shows the mark (the flat mark beside the title, 28 px). Both files are copied byte for byte from the
# contract's artwork/svg (Find-ExchangeMark.ps1 finds them by hash) and sit in the image root.
$script:ExchangeMarkName = 'Media Exchange Server'
$script:ExchangeMarkFiles = @{ head = 'exchange-mark-full.svg'; inline = 'exchange-mark-small.svg' }

# Section headings and small labels. A locale without a row falls back to English (rule 13).
$script:Ui = @{
    en = @{
        Help = 'Help'; Purpose = 'What this is for'; Order = 'In this order'; Elements = 'The elements'
        Related = 'Related'; Index = 'Help index'; Undo = 'Undo'; Shortcut = 'Shortcut'
        Support = 'Ask a question or report a problem'; Screenshot = 'Screenshot'
        Demo = 'Demo content. The numbers match the elements listed below.'
        KindScreen = 'Screen'; KindLink = 'Link'; Subjects = 'Subject index'; Terms = 'Terms'
    }
    ru = @{
        Help = 'Справка'; Purpose = 'Для чего этот экран'; Order = 'Порядок действий'; Elements = 'Элементы'
        Related = 'Связанное'; Index = 'Оглавление справки'; Undo = 'Отмена'; Shortcut = 'Сочетание клавиш'
        Support = 'Задать вопрос или сообщить о проблеме'; Screenshot = 'Снимок экрана'
        Demo = 'Демонстрационные данные. Номера совпадают с элементами в списке ниже.'
        KindScreen = 'Экран'; KindLink = 'Ссылка'; Subjects = 'Предметный указатель'; Terms = 'Термины'
    }
    uk = @{
        Help = 'Довідка'; Purpose = 'Для чого цей екран'; Order = 'Порядок дій'; Elements = 'Елементи'
        Related = 'Пов''язане'; Index = 'Зміст довідки'; Undo = 'Скасування'; Shortcut = 'Сполучення клавіш'
        Support = 'Поставити запитання або повідомити про проблему'; Screenshot = 'Знімок екрана'
        Demo = 'Демонстраційні дані. Номери збігаються з елементами в списку нижче.'
        KindScreen = 'Екран'; KindLink = 'Посилання'; Subjects = 'Предметний покажчик'; Terms = 'Терміни'
    }
}

$script:Problems = [System.Collections.Generic.List[string]]::new()
$script:ProblemSeen = [System.Collections.Generic.HashSet[string]]::new()
$script:Notes = [System.Collections.Generic.SortedSet[string]]::new()
$script:StringCache = @{}
$script:TermDictionary = $null
$script:Titles = @{}
$script:PageIds = @()
$script:Cfg = $null

# ---------- small helpers ----------

function Add-Problem {
    param([Parameter(Mandatory)][string]$Message)
    if ($script:ProblemSeen.Add($Message)) { $script:Problems.Add($Message) }
}

function Write-Problem {
    param([Parameter(Mandatory)][string]$Message)
    [Console]::Error.WriteLine("ERROR: $Message")
}

function Get-Prop {
    # Returns nothing (not $null) for an absent member, so @(Get-Prop ..) is an empty array.
    param($Object, [string]$Name)
    if ($null -eq $Object) { return }
    $member = $Object.PSObject.Properties[$Name]
    if ($null -eq $member) { return }
    return $member.Value
}

function ConvertTo-HtmlText {
    param([string]$Text)
    return $Text.Replace('&', '&amp;').Replace('<', '&lt;').Replace('>', '&gt;').Replace('"', '&quot;')
}

function ConvertTo-OneLine {
    param([string]$Text)
    return ([regex]::Replace($Text, '\s+', ' ')).Trim()
}

function ConvertTo-ParagraphsHtml {
    param([string]$Text, [string]$Class = '')
    $open = if ($Class) { "<p class=`"$Class`">" } else { '<p>' }
    $parts = [regex]::Split($Text.Trim(), '\r?\n[ \t]*\r?\n')
    foreach ($part in $parts) {
        $line = ConvertTo-OneLine $part
        if ($line) { $open + (ConvertTo-HtmlText $line) + '</p>' }
    }
}

function ConvertTo-StepHtml {
    param([string]$Text, [string[]]$MarkerIds)
    $html = ConvertTo-HtmlText (ConvertTo-OneLine $Text)
    return [regex]::Replace($html, '\((\d{1,2})\)', {
            param($match)
            $n = [int]$match.Groups[1].Value
            if ($n -ge 1 -and $n -le $MarkerIds.Count) {
                return "<a class=`"ref`" href=`"#$($MarkerIds[$n - 1])`">$n</a>"
            }
            return $match.Value
        })
}

function ConvertTo-ShortcutHtml {
    param([string]$Text)
    $alternatives = foreach ($alt in [regex]::Split($Text.Trim(), '\s+/\s+')) {
        $keys = foreach ($key in [regex]::Split($alt, '\s*\+\s*')) {
            if ($key) { '<kbd>' + (ConvertTo-HtmlText $key) + '</kbd>' }
        }
        $keys -join ' + '
    }
    return $alternatives -join ' / '
}

function Read-JsonFile {
    param([Parameter(Mandatory)][string]$Path)
    try {
        $value = Get-Content -LiteralPath $Path -Raw -Encoding UTF8 | ConvertFrom-Json
    } catch {
        Add-Problem "cannot read ${Path}: $($_.Exception.Message)"
        return
    }
    if ($null -eq $value) { Add-Problem "empty JSON: $Path"; return }
    return $value
}

function Write-TextFile {
    param([Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)][string]$Text)
    $utf8 = [System.Text.UTF8Encoding]::new($false)
    $bytes = $utf8.GetBytes($Text)
    if (Test-Path -LiteralPath $Path -PathType Leaf) {
        $existing = [System.IO.File]::ReadAllBytes($Path)
        if ([Convert]::ToBase64String($existing) -ceq [Convert]::ToBase64String($bytes)) { return $false }
    }
    $dir = Split-Path -Parent $Path
    if (-not (Test-Path -LiteralPath $dir)) { $null = New-Item -ItemType Directory -Path $dir -Force }
    [System.IO.File]::WriteAllBytes($Path, $bytes)
    return $true
}

# ---------- configuration and inputs ----------

function Initialize-Config {
    $root = [System.IO.Path]::GetFullPath($RepoRoot)
    $pick = { param($given, $default) if ($given) { [System.IO.Path]::GetFullPath($given) } else { [System.IO.Path]::GetFullPath($default) } }
    $tags = foreach ($entry in $Locales) { foreach ($tag in ($entry -split ',')) { $tag.Trim() } }
    $tags = @($tags | Where-Object { $_ } | Select-Object -Unique)
    foreach ($tag in $tags) {
        if ($tag -notmatch '^[a-z]{2,3}(-[A-Za-z0-9]+)*$') { throw "invalid locale tag '$tag'" }
    }
    $script:Cfg = @{
        Root       = $root
        Locales    = $tags
        SourceRoot = & $pick $SourceRoot (Join-Path $root 'docs/help/content')
        MapPath    = & $pick $MapPath (Join-Path $root 'phone/src/main/assets/help/help-map.json')
        ImageRoot  = & $pick $ImageRoot (Join-Path $root 'phone/src/main/assets/help/img')
        OutAssets  = & $pick $OutAssets (Join-Path $root 'phone/src/main/assets/help')
        Termbase   = & $pick $TermbasePath (Join-Path $root 'docs/TERMBASE.json')
        ResRoot    = Join-Path $root 'phone/src/main/res'
        CssSource  = Join-Path $PSScriptRoot 'help.css'
    }
}

function Get-ValuesFolderName {
    param([string]$Locale)
    if ($Locale -eq $script:SourceLocale) { return 'values' }
    if ($Locale.Contains('-')) { return 'values-b+' + $Locale.Replace('-', '+') }
    return "values-$Locale"
}

function ConvertFrom-AndroidString {
    # What aapt does to a <string> body: a quoted value keeps its spaces, an unquoted one collapses them,
    # and a backslash escapes the next character (\' \" \n \t \uXXXX \@ \?).
    param([string]$Raw)
    $value = $Raw.Trim()
    if ($value.Length -ge 2 -and $value.StartsWith('"') -and $value.EndsWith('"')) {
        $value = $value.Substring(1, $value.Length - 2)
    } else {
        $value = ConvertTo-OneLine $value
    }
    return [regex]::Replace($value, '\\(u[0-9a-fA-F]{4}|.)', {
            param($match)
            $token = $match.Groups[1].Value
            if ($token.Length -eq 5) { return [string][char][Convert]::ToInt32($token.Substring(1), 16) }
            if ($token -ceq 'n') { return "`n" }
            if ($token -ceq 't') { return "`t" }
            return $token
        })
}

function Get-StringTable {
    param([Parameter(Mandatory)][string]$Locale)
    if ($script:StringCache.ContainsKey($Locale)) { return $script:StringCache[$Locale] }
    $table = @{}
    $folder = Join-Path $script:Cfg.ResRoot (Get-ValuesFolderName $Locale)
    if (Test-Path -LiteralPath $folder -PathType Container) {
        foreach ($file in Get-ChildItem -LiteralPath $folder -Filter 'strings*.xml' -File | Sort-Object Name) {
            $doc = [System.Xml.XmlDocument]::new()
            try { $doc.Load($file.FullName) } catch {
                Add-Problem "cannot parse $($file.FullName): $($_.Exception.Message)"
                continue
            }
            foreach ($node in $doc.SelectNodes('/resources/string')) {
                $table[$node.GetAttribute('name')] = ConvertFrom-AndroidString $node.InnerText
            }
        }
    } else {
        $null = $script:Notes.Add("no string folder for '$Locale' ($folder): every name falls back to $($script:SourceLocale)")
    }
    $script:StringCache[$Locale] = $table
    return $table
}

function Get-CaptureRecord {
    # The build the screenshots were captured against (rule 8 item 7). This repository computes versionName from a
    # clock reading, so the generator never guesses it: it reads what the capture script recorded.
    $path = Join-Path $script:Cfg.ImageRoot 'capture.json'
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { return }
    return Read-JsonFile $path
}

function Get-HelpMapPages {
    param($Map)
    $ids = [System.Collections.Generic.List[string]]::new()
    $take = {
        param($row, $where)
        $id = Get-Prop $row 'help'
        if (-not $id) { Add-Problem "help map: $where has form page but no help id"; return }
        if ($id -notmatch '^[a-z0-9][a-z0-9-]*$') { Add-Problem "help map: $where has an invalid help id '$id'"; return }
        if ($id -eq 'index') { Add-Problem "help map: $where uses the reserved help id 'index'"; return }
        if (-not $ids.Contains($id)) { $ids.Add($id) }
    }
    foreach ($surface in @(Get-Prop $Map 'surfaces')) {
        $form = Get-Prop $surface 'form'
        if ($form -notin 'page', 'both') { continue }
        $sid = Get-Prop $surface 'id'
        & $take $surface "surface '$sid'"
        foreach ($page in @(Get-Prop $surface 'pages')) {
            $pageForm = Get-Prop $page 'form'
            if ($pageForm -and $pageForm -notin 'page', 'both') { continue }
            & $take $page "page '$(Get-Prop $page 'id')' of '$sid'"
        }
    }
    return , $ids.ToArray()
}

# ---------- source validation ----------

function Test-SourceLocale {
    param([string]$Path, $Json, [string]$Locale, [string[]]$ElementIds, [bool]$IsIndex)
    $where = "$Path [$Locale]"
    $block = Get-Prop (Get-Prop $Json 'locales') $Locale
    if ($null -eq $block) { Add-Problem "${where}: locale block is missing"; return }
    foreach ($field in 'title', 'purpose') {
        if (-not (Get-Prop $block $field)) { Add-Problem "${where}: '$field' is missing or empty" }
    }
    if ($IsIndex) { return }

    $steps = @(Get-Prop $block 'steps')
    if ($steps.Count -eq 0 -and -not (Get-Prop $block 'orderNote')) {
        Add-Problem "${where}: needs 'steps' or 'orderNote' (anatomy item 4)"
    }
    if ($steps.Count -gt $script:MaxSteps) {
        Add-Problem "${where}: $($steps.Count) steps, at most $($script:MaxSteps) allowed (rule 8)"
    }

    $entries = Get-Prop $block 'elements'
    foreach ($id in $ElementIds) {
        $entry = Get-Prop $entries $id
        if ($null -eq $entry) { Add-Problem "${where}: no entry for element '$id'"; continue }
        foreach ($field in 'summary', 'details') {
            if (-not (Get-Prop $entry $field)) { Add-Problem "${where}: element '$id' has no '$field'" }
        }
    }

    $groups = @(Get-Prop $block 'groups')
    if ($groups.Count -gt 0) {
        $placed = [System.Collections.Generic.List[string]]::new()
        foreach ($group in $groups) {
            if (-not (Get-Prop $group 'title')) { Add-Problem "${where}: a group has no title" }
            foreach ($member in @(Get-Prop $group 'elements')) {
                if ($member -notin $ElementIds) { Add-Problem "${where}: group member '$member' is not in the root elements"; continue }
                if ($placed.Contains($member)) { Add-Problem "${where}: element '$member' is in two groups"; continue }
                $placed.Add($member)
            }
        }
        foreach ($id in $ElementIds) {
            if (-not $placed.Contains($id)) { Add-Problem "${where}: element '$id' is in no group" }
        }
    }
}

function Read-HelpSource {
    param([Parameter(Mandatory)][string]$Id, [switch]$IsIndex)
    $path = Join-Path $script:Cfg.SourceRoot "$Id.json"
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        Add-Problem "missing source for help id '$Id': $path"
        return
    }
    $json = Read-JsonFile $path
    if ($null -eq $json) { return }
    if ((Get-Prop $json 'help') -ne $Id) {
        Add-Problem "${path}: 'help' is '$(Get-Prop $json 'help')', expected '$Id'"
    }
    $ids = [System.Collections.Generic.List[string]]::new()
    if (-not $IsIndex) {
        foreach ($element in @(Get-Prop $json 'elements')) {
            $eid = [string](Get-Prop $element 'id')
            if ($eid -notmatch '^[A-Za-z][A-Za-z0-9_.-]*$') { Add-Problem "${path}: invalid element id '$eid'"; continue }
            if ($ids.Contains($eid)) { Add-Problem "${path}: duplicate element id '$eid'"; continue }
            $ids.Add($eid)
        }
    }
    $script:Titles[$Id] = @{}
    foreach ($locale in $script:Cfg.Locales) {
        Test-SourceLocale -Path $path -Json $json -Locale $locale -ElementIds $ids.ToArray() -IsIndex $IsIndex.IsPresent
        $title = Get-Prop (Get-Prop (Get-Prop $json 'locales') $locale) 'title'
        if ($title) { $script:Titles[$Id][$locale] = ConvertTo-OneLine $title }
    }
    return $json
}

# ---------- hrefs and ui text ----------

function Get-PageTitle {
    param([string]$Id, [string]$Locale)
    if ($script:Titles.ContainsKey($Id) -and $script:Titles[$Id].ContainsKey($Locale)) { return $script:Titles[$Id][$Locale] }
}

function Get-Ui {
    param([string]$Locale)
    if ($script:Ui.ContainsKey($Locale)) { return $script:Ui[$Locale] }
    $null = $script:Notes.Add("no heading table for '$Locale': headings fall back to $($script:SourceLocale)")
    return $script:Ui[$script:SourceLocale]
}

function Get-OutputPath {
    param([string]$Id, [string]$Locale)
    return Join-Path (Join-Path $script:Cfg.OutAssets $Locale) "$Id.html"
}

# ---------- rendering ----------

function Resolve-ElementName {
    param($Element, [string]$HelpId, [string]$Locale, $Entry)
    $key = [string](Get-Prop $Element 'labelKey')
    if (-not $key) {
        $name = Get-Prop $Entry 'name'
        if ($name) { return ConvertTo-OneLine $name }
        Add-Problem "help '$HelpId': element '$(Get-Prop $Element 'id')' has neither labelKey nor a localized name"
        return ''
    }
    $english = (Get-StringTable $script:SourceLocale)[$key]
    if ($null -eq $english) {
        Add-Problem "help '$HelpId': element '$(Get-Prop $Element 'id')': labelKey '$key' is not in values/strings*.xml"
        return ''
    }
    if ($Locale -eq $script:SourceLocale) { return $english }
    $local = (Get-StringTable $Locale)[$key]
    if ($null -eq $local) {
        $null = $script:Notes.Add("labelKey '$key' is missing in ${Locale}: the English name is used")
        return $english
    }
    return $local
}

function New-ElementHtml {
    param([string]$Id, [int]$Number, [string]$Name, $Entry, $Ui)
    $lines = [System.Collections.Generic.List[string]]::new()
    $summary = ConvertTo-HtmlText (ConvertTo-OneLine ([string](Get-Prop $Entry 'summary')))
    $lines.Add("<details class=`"el`" id=`"$Id`">")
    $lines.Add("<summary><span class=`"mk`">$Number</span><span><span class=`"name`">$(ConvertTo-HtmlText $Name)</span><span class=`"one`">$summary</span></span></summary>")
    $lines.Add('<div class="full">')
    foreach ($paragraph in @(ConvertTo-ParagraphsHtml ([string](Get-Prop $Entry 'details')))) { $lines.Add($paragraph) }
    $undo = [string](Get-Prop $Entry 'undo')
    $shortcut = [string](Get-Prop $Entry 'shortcut')
    if ($undo -or $shortcut) {
        $lines.Add('<dl class="facts">')
        if ($undo) { $lines.Add("<dt>$($Ui.Undo)</dt><dd>$(ConvertTo-HtmlText (ConvertTo-OneLine $undo))</dd>") }
        if ($shortcut) { $lines.Add("<dt>$($Ui.Shortcut)</dt><dd>$(ConvertTo-ShortcutHtml $shortcut)</dd>") }
        $lines.Add('</dl>')
    }
    $lines.Add('</div>')
    $lines.Add('</details>')
    return $lines.ToArray()
}

function New-RelatedHtml {
    param($Source, [string]$HelpId, [string]$Locale, $Ui)
    $items = [System.Collections.Generic.List[string]]::new()
    foreach ($rel in @(Get-Prop $Source 'related')) {
        $helpRef = Get-Prop $rel 'help'
        $urlRef = Get-Prop $rel 'url'
        if ($helpRef) {
            $title = if ($helpRef -eq 'index') { $Ui.Index } else { Get-PageTitle $helpRef $Locale }
            if ($helpRef -ne 'index' -and $helpRef -notin $script:PageIds) {
                Add-Problem "help '$HelpId': related help '$helpRef' is not a page of the help map"; continue
            }
            if (-not $title) { Add-Problem "help '$HelpId': related help '$helpRef' has no title in $Locale"; continue }
            $items.Add("<li><a href=`"$helpRef.html`"><b>$(ConvertTo-HtmlText $title)</b><span class=`"pill`">$($Ui.KindScreen)</span></a></li>")
        } elseif ($urlRef) {
            if ($urlRef -notmatch '^https://') { Add-Problem "help '$HelpId': related url '$urlRef' is not https"; continue }
            $labels = Get-Prop $rel 'label'
            $label = Get-Prop $labels $Locale
            if (-not $label) { $label = Get-Prop $labels $script:SourceLocale }
            if (-not $label) { Add-Problem "help '$HelpId': related url '$urlRef' has no label"; continue }
            $items.Add("<li><a href=`"$(ConvertTo-HtmlText $urlRef)`"><b>$(ConvertTo-HtmlText ([string]$label))</b><span class=`"pill gold`">$($Ui.KindLink)</span></a></li>")
        } else {
            Add-Problem "help '$HelpId': a related entry has neither help nor url"
        }
    }
    $items.Add("<li><a href=`"index.html`"><b>$(ConvertTo-HtmlText $Ui.Index)</b></a></li>")
    return @('<ul class="related">') + $items.ToArray() + @('</ul>')
}

function New-FooterHtml {
    param($Ui, [string]$ProductName, $Capture)
    $stamp = $ProductName
    $stampDate = $Date
    if ($null -ne $Capture) {
        $captureVersion = [string](Get-Prop $Capture 'versionName')
        $captureDate = [string](Get-Prop $Capture 'date')
        if ($captureVersion) { $stamp += " $captureVersion" }
        if ($captureDate) { $stampDate = $captureDate }
    }
    $stamp += ", $stampDate"
    return @(
        '<footer class="help-foot">',
        "<span>$(ConvertTo-HtmlText $stamp)</span>",
        "<a href=`"index.html`">$(ConvertTo-HtmlText $Ui.Index)</a>",
        "<a href=`"$($script:SupportUrl)`">$(ConvertTo-HtmlText $Ui.Support)</a>",
        '</footer>'
    )
}

function New-ScreenshotHtml {
    param([string]$HelpId, [string]$Locale, [string]$Title, $Ui)
    $ext = $script:ImageExtension
    $light = "$HelpId-$Locale-light.$ext"
    $dark = "$HelpId-$Locale-dark.$ext"
    if (-not (Test-Path -LiteralPath (Join-Path $script:Cfg.ImageRoot $light) -PathType Leaf)) { return @() }
    $alt = ConvertTo-HtmlText "$($Ui.Screenshot): $Title"
    $hasDark = Test-Path -LiteralPath (Join-Path $script:Cfg.ImageRoot $dark) -PathType Leaf
    $lines = @('<figure class="shot">')
    if ($hasDark) {
        $lines += "<img class=`"on-light`" src=`"../img/$light`" alt=`"$alt`">"
        $lines += "<img class=`"on-dark`" src=`"../img/$dark`" alt=`"$alt`">"
    } else {
        $lines += "<img src=`"../img/$light`" alt=`"$alt`">"
    }
    $lines += "<figcaption>$(ConvertTo-HtmlText $Ui.Demo)</figcaption>"
    return $lines + @('</figure>')
}

function New-HeroHtml {
    # The page head: the title, with the exchange server's mark when the source asks for one (EXCHANGE-MARK rules 10, 11).
    param($Source, [string]$HelpId, [string]$Title)
    $heading = "<h1>$(ConvertTo-HtmlText $Title)</h1>"
    $mark = [string](Get-Prop $Source 'mark')
    if (-not $mark) { return @('<header class="hero">', $heading, '</header>') }
    if (-not $script:ExchangeMarkFiles.ContainsKey($mark)) {
        Add-Problem "help '$HelpId': mark '$mark' is neither 'head' nor 'inline'"
        return @('<header class="hero">', $heading, '</header>')
    }
    $file = $script:ExchangeMarkFiles[$mark]
    if (-not (Test-Path -LiteralPath (Join-Path $script:Cfg.ImageRoot $file) -PathType Leaf)) {
        Add-Problem "help '$HelpId': mark '$mark' needs img/$file, copied from the EXCHANGE-MARK artwork"
    }
    $alt = ConvertTo-HtmlText $script:ExchangeMarkName
    if ($mark -eq 'head') {
        return @('<header class="hero">', "<img class=`"mark-head`" src=`"../img/$file`" width=`"128`" height=`"128`" alt=`"$alt`">", $heading, '</header>')
    }
    return @('<header class="hero hero-inline">', "<img class=`"mark-inline`" src=`"../img/$file`" width=`"28`" height=`"28`" alt=`"$alt`">", $heading, '</header>')
}

function New-HelpPageBody {
    param($Source, [string]$HelpId, [string]$Locale, [string]$ProductName, $Capture)
    $ui = Get-Ui $Locale
    $block = Get-Prop (Get-Prop $Source 'locales') $Locale
    $title = ConvertTo-OneLine ([string](Get-Prop $block 'title'))
    $roots = @(Get-Prop $Source 'elements')
    $markerIds = @($roots | ForEach-Object { [string](Get-Prop $_ 'id') })
    $entries = Get-Prop $block 'elements'

    $body = [System.Collections.Generic.List[string]]::new()
    $body.Add("<nav class=`"help-crumbs`" aria-label=`"$(ConvertTo-HtmlText $ui.Index)`">")
    $body.Add("<a href=`"index.html`">$(ConvertTo-HtmlText $ui.Index)</a><span class=`"sep`" aria-hidden=`"true`">/</span><span>$(ConvertTo-HtmlText $title)</span>")
    $body.Add('</nav>')
    foreach ($line in (New-HeroHtml -Source $Source -HelpId $HelpId -Title $title)) { $body.Add($line) }

    $body.Add("<h2>$($ui.Purpose)</h2>")
    foreach ($paragraph in @(ConvertTo-ParagraphsHtml ([string](Get-Prop $block 'purpose')) 'lead')) { $body.Add($paragraph) }

    foreach ($line in @(New-ScreenshotHtml $HelpId $Locale $title $ui)) { $body.Add($line) }

    $body.Add("<h2>$($ui.Order)</h2>")
    $steps = @(Get-Prop $block 'steps')
    if ($steps.Count -gt 0) {
        $body.Add('<ol class="order">')
        foreach ($step in $steps) { $body.Add('<li>' + (ConvertTo-StepHtml ([string]$step) $markerIds) + '</li>') }
        $body.Add('</ol>')
    } else {
        $body.Add('<p class="order-note">' + (ConvertTo-HtmlText (ConvertTo-OneLine ([string](Get-Prop $block 'orderNote')))) + '</p>')
    }

    if ($roots.Count -gt 0) {
        $body.Add("<h2>$($ui.Elements)</h2>")
        $body.Add('<div class="elements">')
        $rendered = @{}
        foreach ($root in $roots) {
            $id = [string](Get-Prop $root 'id')
            $entry = Get-Prop $entries $id
            if ($null -eq $entry) { continue }
            $number = [array]::IndexOf($markerIds, $id) + 1
            $name = Resolve-ElementName -Element $root -HelpId $HelpId -Locale $Locale -Entry $entry
            $rendered[$id] = New-ElementHtml -Id $id -Number $number -Name $name -Entry $entry -Ui $ui
        }
        $groups = @(Get-Prop $block 'groups')
        if ($groups.Count -gt 0) {
            $shown = [System.Collections.Generic.List[string]]::new()
            foreach ($group in $groups) {
                $body.Add("<h3>$(ConvertTo-HtmlText (ConvertTo-OneLine ([string](Get-Prop $group 'title'))))</h3>")
                foreach ($member in @(Get-Prop $group 'elements')) {
                    if ($rendered.ContainsKey($member)) { foreach ($line in $rendered[$member]) { $body.Add($line) } }
                    $shown.Add([string]$member)
                }
            }
            if (($shown -join ',') -cne ($markerIds -join ',')) {
                $null = $script:Notes.Add("help '$HelpId' [$Locale]: group order differs from marker order, so the marker numbers on the page do not run 1..n")
            }
        } else {
            foreach ($id in $markerIds) {
                if ($rendered.ContainsKey($id)) { foreach ($line in $rendered[$id]) { $body.Add($line) } }
            }
        }
        $body.Add('</div>')
    }

    $body.Add("<h2>$($ui.Related)</h2>")
    foreach ($line in (New-RelatedHtml -Source $Source -HelpId $HelpId -Locale $Locale -Ui $ui)) { $body.Add($line) }
    foreach ($line in (New-FooterHtml -Ui $ui -ProductName $ProductName -Capture $Capture)) { $body.Add($line) }
    return $body.ToArray()
}

function Get-SortKey {
    # Sorts Latin before Cyrillic and Cyrillic by the alphabets of ru and uk (ordinal order puts the letters they do not
    # share after the last letter). No culture is read, so a Windows and a Linux run write the same page.
    param([string]$Text)
    $alphabet = 'абвгґдеёєжзиіїйклмнопрстуфхцчшщъыьэюя'
    $key = [System.Text.StringBuilder]::new()
    foreach ($char in $Text.ToLowerInvariant().ToCharArray()) {
        $at = $alphabet.IndexOf($char)
        if ($at -ge 0) { $null = $key.Append([char](0x3000 + $at)) } else { $null = $key.Append($char) }
    }
    return $key.ToString()
}

function New-SubjectIndexHtml {
    # Every page title and element name of the locale, once, linking each page that carries it once.
    param($Sources, [string]$Locale, $Ui)
    $subjects = @{}
    $note = {
        param([string]$Name, [string]$PageId, [string]$Href, [string]$PageTitle)
        $key = Get-SortKey $Name
        if (-not $subjects.ContainsKey($key)) { $subjects[$key] = [pscustomobject]@{ Name = $Name; Links = [ordered]@{} } }
        if (-not $subjects[$key].Links.Contains($PageId)) { $subjects[$key].Links[$PageId] = [pscustomobject]@{ Href = $Href; Title = $PageTitle } }
    }
    foreach ($id in $script:PageIds) {
        $source = $Sources[$id]
        $pageTitle = Get-PageTitle $id $Locale
        if (-not $pageTitle) { continue }
        & $note $pageTitle $id "$id.html" $pageTitle
        $entries = Get-Prop (Get-Prop (Get-Prop $source 'locales') $Locale) 'elements'
        foreach ($element in @(Get-Prop $source 'elements')) {
            $elementId = [string](Get-Prop $element 'id')
            $entry = Get-Prop $entries $elementId
            if ($null -eq $entry) { continue }
            $name = Resolve-ElementName -Element $element -HelpId $id -Locale $Locale -Entry $entry
            if ($name) { & $note $name $id "$id.html#$elementId" $pageTitle }
        }
    }
    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add("<h2 id=`"subjects`">$(ConvertTo-HtmlText $Ui.Subjects)</h2>")
    $lines.Add('<ul class="subjects">')
    [string[]]$keys = @($subjects.Keys)
    [Array]::Sort($keys, [StringComparer]::Ordinal)
    foreach ($key in $keys) {
        $subject = $subjects[$key]
        $links = foreach ($link in $subject.Links.Values) { "<a href=`"$($link.Href)`">$(ConvertTo-HtmlText $link.Title)</a>" }
        $lines.Add("<li><b>$(ConvertTo-HtmlText $subject.Name)</b> $($links -join ', ')</li>")
    }
    $lines.Add('</ul>')
    return $lines.ToArray()
}

function Get-TermDictionary {
    # term -> locale -> text, from the dictionary table the term base names. Only the words before a `;` are the term.
    param($Termbase)
    if ($null -ne $script:TermDictionary) { return $script:TermDictionary }
    $script:TermDictionary = @{}
    $rel = [string](Get-Prop $Termbase 'dictionary')
    if (-not $rel) { return $script:TermDictionary }
    $path = Join-Path $script:Cfg.Root $rel
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { Add-Problem "term base: dictionary '$rel' not found"; return $script:TermDictionary }
    foreach ($row in @(Get-Content -LiteralPath $path -Encoding UTF8 | Where-Object { $_ -match '^\|' } | Select-Object -Skip 2)) {
        $cells = @($row.Trim().Trim('|').Split('|') | ForEach-Object { $_.Trim() })
        if ($cells.Count -lt 4) { continue }
        $script:TermDictionary[$cells[0]] = @{ en = $cells[1]; ru = $cells[2]; uk = $cells[3] }
    }
    return $script:TermDictionary
}

function New-TermsHtml {
    # The glossary of external rule 1: a dictionary term keeps the words of its table, a screen term the words of the screen.
    param($Termbase, [string]$Locale, $Ui)
    $rows = @{}
    foreach ($term in @(Get-Prop $Termbase 'terms')) {
        $id = [string](Get-Prop $term 'id')
        $definition = [string](Get-Prop (Get-Prop $term 'definition') $Locale)
        if (-not $definition) { Add-Problem "term base: '$id' has no definition in $Locale"; continue }
        $name = ''
        $dictionaryKey = [string](Get-Prop $term 'dictionary')
        $labelKey = [string](Get-Prop $term 'labelKey')
        if ($dictionaryKey) {
            $row = (Get-TermDictionary $Termbase)[$dictionaryKey]
            if ($null -eq $row) { Add-Problem "term base: '$id' names '$dictionaryKey', which the dictionary does not have"; continue }
            $name = ($row[$Locale] -split ';')[0].Trim()
        } elseif ($labelKey) {
            $name = (Get-StringTable $Locale)[$labelKey]
            if ($null -eq $name) { $name = (Get-StringTable $script:SourceLocale)[$labelKey] }
            if ($null -eq $name) { Add-Problem "term base: '$id' names the string '$labelKey', which does not exist"; continue }
        } else {
            $name = [string](Get-Prop (Get-Prop $term 'name') $Locale)
            if (-not $name) { Add-Problem "term base: '$id' has no name in $Locale"; continue }
        }
        $rows["$(Get-SortKey $name)`0$id"] = [pscustomobject]@{ Id = $id; Name = $name; Definition = $definition }
    }
    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add("<h2 id=`"terms`">$(ConvertTo-HtmlText $Ui.Terms)</h2>")
    $lines.Add('<dl class="terms">')
    [string[]]$keys = @($rows.Keys)
    [Array]::Sort($keys, [StringComparer]::Ordinal)
    foreach ($key in $keys) {
        $row = $rows[$key]
        $lines.Add("<dt id=`"term-$($row.Id)`">$(ConvertTo-HtmlText $row.Name)</dt><dd>$(ConvertTo-HtmlText (ConvertTo-OneLine $row.Definition))</dd>")
    }
    $lines.Add('</dl>')
    return $lines.ToArray()
}

function New-IndexPageBody {
    param($Source, $Sources, $Termbase, [string]$Locale, [string]$ProductName, $Capture)
    $ui = Get-Ui $Locale
    $block = Get-Prop (Get-Prop $Source 'locales') $Locale
    $title = ConvertTo-OneLine ([string](Get-Prop $block 'title'))
    $body = [System.Collections.Generic.List[string]]::new()
    $body.Add('<header class="hero">')
    $body.Add("<h1>$(ConvertTo-HtmlText $title)</h1>")
    $body.Add('</header>')
    foreach ($paragraph in @(ConvertTo-ParagraphsHtml ([string](Get-Prop $block 'purpose')) 'lead')) { $body.Add($paragraph) }
    $body.Add('<ul class="related">')
    foreach ($id in $script:PageIds) {
        $pageTitle = Get-PageTitle $id $Locale
        if (-not $pageTitle) { Add-Problem "help index: page '$id' has no title in $Locale"; continue }
        $body.Add("<li><a href=`"$id.html`"><b>$(ConvertTo-HtmlText $pageTitle)</b></a></li>")
    }
    $body.Add('</ul>')
    foreach ($line in (New-SubjectIndexHtml -Sources $Sources -Locale $Locale -Ui $ui)) { $body.Add($line) }
    foreach ($line in (New-TermsHtml -Termbase $Termbase -Locale $Locale -Ui $ui)) { $body.Add($line) }
    foreach ($line in (New-FooterHtml -Ui $ui -ProductName $ProductName -Capture $Capture)) { $body.Add($line) }
    return $body.ToArray()
}

function New-PageHtml {
    param([string]$Locale, [string]$DocTitle, [string[]]$Main)
    $dir = if ($script:RtlLocales -contains $Locale) { 'rtl' } else { 'ltr' }
    $head = @(
        '<!doctype html>',
        "<html lang=`"$Locale`" dir=`"$dir`">",
        '<head>',
        '<meta charset="utf-8">',
        '<meta name="viewport" content="width=device-width, initial-scale=1">',
        '<meta name="referrer" content="no-referrer">',
        '<meta name="color-scheme" content="light dark">',
        "<title>$(ConvertTo-HtmlText $DocTitle)</title>",
        '<link rel="stylesheet" href="../help.css">',
        '</head>',
        '<body class="help">',
        '<main class="container">'
    )
    return (($head + $Main + @('</main>', '</body>', '</html>')) -join "`n") + "`n"
}

function Test-PageRules {
    # CONTEXT-HELP rule 12, checked on what is about to be written: no script, no URL outside an <a href>.
    param([string]$Path, [string]$Html)
    if ($Html -match '(?i)<script') { Add-Problem "${Path}: contains a <script>" }
    $stripped = [regex]::Replace($Html, 'href="[^"]*"', 'href=""')
    if ($stripped -match 'https?://') { Add-Problem "${Path}: a URL outside an <a href> (put links in 'related')" }
}

# ---------- main ----------

function Invoke-Main {
    Initialize-Config
    $missing = @($script:Cfg.MapPath, $script:Cfg.CssSource, $script:Cfg.Termbase, (Join-Path $script:Cfg.ResRoot 'values')) |
        Where-Object { -not (Test-Path -LiteralPath $_) }
    if ($missing) {
        foreach ($path in $missing) { Write-Problem "input not found: $path" }
        Write-Host "generate-help-pages: COULD NOT VERIFY ($(@($missing).Count) input(s) not found)"
        return 2
    }

    $map = Read-JsonFile $script:Cfg.MapPath
    if ($null -eq $map) {
        $script:Problems | ForEach-Object { Write-Problem $_ }
        Write-Host "generate-help-pages: FAIL ($($script:Problems.Count) problem(s))"
        return 1
    }
    $script:PageIds = Get-HelpMapPages $map
    $selectedIds = @($HelpIds | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim() } |
        Where-Object { $_ } | Select-Object -Unique)
    $writeAll = $selectedIds.Count -eq 0 -or $Check.IsPresent
    $unknownIds = @($selectedIds | Where-Object { $_ -ne 'index' -and $_ -notin $script:PageIds })
    if ($unknownIds.Count -gt 0) {
        Write-Problem "unknown help ids: $($unknownIds -join ', ')"
        Write-Host "generate-help-pages: FAIL ($($unknownIds.Count) unknown help id(s))"
        return 1
    }

    $sources = [ordered]@{}
    foreach ($id in $script:PageIds) { $sources[$id] = Read-HelpSource -Id $id }
    $indexSource = Read-HelpSource -Id 'index' -IsIndex
    if ($script:Problems.Count -gt 0) {
        foreach ($problem in $script:Problems) { Write-Problem $problem }
        Write-Host "generate-help-pages: FAIL ($($script:Problems.Count) problem(s))"
        return 1
    }

    $productName = [string](Get-Prop $map 'product')
    if (-not $productName) { $productName = 'StreamsPlayer' }
    $capture = Get-CaptureRecord
    if ($Check) {
        # The date is a fact of the last generation, not of this run.
        $indexOnDisk = Get-OutputPath 'index' $script:Cfg.Locales[0]
        if ((Test-Path -LiteralPath $indexOnDisk -PathType Leaf)) {
            $carried = [regex]::Match([System.IO.File]::ReadAllText($indexOnDisk), '<footer class="help-foot">\s*<span>[^<]*, (\d{4}-\d{2}-\d{2})</span>')
            if ($carried.Success) { $script:Date = $carried.Groups[1].Value }
        }
    }
    $termbase = Read-JsonFile $script:Cfg.Termbase
    $outputs = [System.Collections.Generic.List[object]]::new()
    foreach ($locale in $script:Cfg.Locales) {
        $ui = Get-Ui $locale
        foreach ($id in $script:PageIds) {
            $body = New-HelpPageBody -Source $sources[$id] -HelpId $id -Locale $locale -ProductName $productName -Capture $capture
            $docTitle = "$($ui.Help) - $(Get-PageTitle $id $locale)"
            $path = Get-OutputPath $id $locale
            $html = New-PageHtml -Locale $locale -DocTitle $docTitle -Main $body
            Test-PageRules -Path $path -Html $html
            if ($writeAll -or $id -in $selectedIds) {
                $outputs.Add([pscustomobject]@{ Path = $path; Text = $html })
            }
        }
        $body = New-IndexPageBody -Source $indexSource -Sources $sources -Termbase $termbase -Locale $locale -ProductName $productName -Capture $capture
        $path = Get-OutputPath 'index' $locale
        $html = New-PageHtml -Locale $locale -DocTitle (Get-PageTitle 'index' $locale) -Main $body
        Test-PageRules -Path $path -Html $html
        if ($writeAll -or 'index' -in $selectedIds) {
            $outputs.Add([pscustomobject]@{ Path = $path; Text = $html })
        }
    }
    if ($script:Problems.Count -gt 0) {
        foreach ($problem in $script:Problems) { Write-Problem $problem }
        Write-Host "generate-help-pages: FAIL ($($script:Problems.Count) problem(s))"
        return 1
    }

    $css = (Get-Content -LiteralPath $script:Cfg.CssSource -Raw -Encoding UTF8).Replace("`r`n", "`n").TrimEnd() + "`n"
    $outputs.Add([pscustomobject]@{ Path = Join-Path $script:Cfg.OutAssets 'help.css'; Text = $css })

    if ($Check) {
        $differ = [System.Collections.Generic.List[string]]::new()
        $utf8 = [System.Text.UTF8Encoding]::new($false)
        foreach ($output in $outputs) {
            $rel = [System.IO.Path]::GetRelativePath($script:Cfg.OutAssets, $output.Path).Replace('\', '/')
            if (-not (Test-Path -LiteralPath $output.Path -PathType Leaf)) { $differ.Add("MISSING-PAGE $rel is not on disk"); continue }
            if ([System.IO.File]::ReadAllText($output.Path, $utf8).Replace("`r`n", "`n") -cne $output.Text) {
                $differ.Add("STALE-PAGE $rel differs from what its sources render to")
            }
        }
        $expectedPaths = [System.Collections.Generic.HashSet[string]]::new([string[]]@($outputs | ForEach-Object { [System.IO.Path]::GetFullPath($_.Path) }), [StringComparer]::OrdinalIgnoreCase)
        foreach ($locale in $script:Cfg.Locales) {
            $folder = Join-Path $script:Cfg.OutAssets $locale
            if (-not (Test-Path -LiteralPath $folder -PathType Container)) { continue }
            foreach ($file in Get-ChildItem -LiteralPath $folder -File -Filter '*.html') {
                if (-not $expectedPaths.Contains($file.FullName)) { $differ.Add("ORPHAN-PAGE $locale/$($file.Name) has no source") }
            }
        }
        foreach ($note in $script:Notes) { Write-Host "note: $note" }
        foreach ($finding in $differ) { Write-Host $finding }
        if ($differ.Count -gt 0) {
            Write-Host "generate-help-pages: FAIL ($($differ.Count) file(s) out of step; run tools/help/generate-help-pages.ps1)"
            return 1
        }
        Write-Host "generate-help-pages: PASS (checked=$($outputs.Count) pages=$($script:PageIds.Count) locales=$($script:Cfg.Locales -join ',') date=$Date)"
        return 0
    }

    $written = 0
    foreach ($output in $outputs) { if (Write-TextFile -Path $output.Path -Text $output.Text) { $written++ } }
    foreach ($note in $script:Notes) { Write-Host "note: $note" }
    Write-Host "generate-help-pages: PASS (files=$($outputs.Count) written=$written pages=$($script:PageIds.Count) locales=$($script:Cfg.Locales -join ',') date=$Date)"
    return 0
}

try {
    $code = Invoke-Main
} catch {
    Write-Problem $_.Exception.Message
    Write-Host "generate-help-pages: COULD NOT VERIFY ($($_.Exception.Message))"
    $code = 2
}
exit $code
