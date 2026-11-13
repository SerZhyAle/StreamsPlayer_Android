#requires -Version 7.0
<#
.SYNOPSIS
    Self-test of the documentation gates (S0051): a gate that has not been seen to fail is itself unverified.

.DESCRIPTION
    Builds a small throw-away repository, requires every gate to pass on it, then seeds it with one defect of every kind and
    requires each gate to name exactly those defects: what sits in a code span, a code block, a frozen record or a re-wrapped
    paragraph must stay unreported. -Record must clear a stale block and refuse an untranslated one. The help generator's
    -Check runs on a copy of the real pages and the site's published-help rule on a copy of the real site. Touches neither
    the repository nor a device. The runs of a phase go in parallel: this is a release-tier self-test and each child start
    costs a third of a second.

.EXAMPLE
    pwsh -NoProfile -File tools/docs/test-docs-gates.ps1

.NOTES
    Exit codes (CHECK-VERDICT): 0 every case behaved; 1 a case did not; 2 the fixture could not be built.
    The last line on stdout is the verdict line: `docs-gates-selftest: PASS (run=<n> skipped=0)` or `FAIL (<k>)`; the names
    of the failing cases are printed above it.
#>
[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$fixtureBase = Join-Path ([IO.Path]::GetTempPath()) ('docs-gates-selftest-' + [Guid]::NewGuid().ToString('N'))
$fx = Join-Path $fixtureBase 'repo'
$utf8 = [Text.UTF8Encoding]::new($false)
$script:cases = 0
$script:failures = [Collections.Generic.List[string]]::new()

function Write-Fixture([string]$Rel, [string]$Text) {
    $path = Join-Path $fx $Rel
    $null = New-Item -ItemType Directory -Force -Path (Split-Path -Parent $path)
    [IO.File]::WriteAllText($path, $Text.Replace("`r`n", "`n"), $utf8)
}
function Edit-Fixture([string]$Rel, [string]$Old, [string]$New) {
    $path = Join-Path $fx $Rel
    $text = [IO.File]::ReadAllText($path, $utf8)
    if (-not $text.Contains($Old)) { throw "fixture edit: '$Old' is not in $Rel" }
    [IO.File]::WriteAllText($path, $text.Replace($Old, $New), $utf8)
}
function Add-Fixture([string]$Rel, [string]$Text) { [IO.File]::AppendAllText((Join-Path $fx $Rel), $Text.Replace("`r`n", "`n"), $utf8) }

function Invoke-Runs($Runs) {
    # Each run: Name, Path, Arguments. Returns name -> { Code, Output }.
    $results = @{}
    $done = $Runs | ForEach-Object -ThrottleLimit 8 -Parallel {
        $text = & pwsh -NoProfile -File $_.Path @($_.Arguments) 2>&1 | Out-String
        [pscustomobject]@{ Name = $_.Name; Code = $LASTEXITCODE; Output = $text }
    }
    foreach ($item in $done) { $results[$item.Name] = $item }
    return $results
}
function Assert-True([string]$Name, [bool]$Ok, [string]$Detail = '') {
    $script:cases++
    if (-not $Ok) {
        $script:failures.Add($Name)
        Write-Host "FAIL $Name"
        if ($Detail) { $Detail -split '\r?\n' | Where-Object { $_ } | Select-Object -Last 8 | ForEach-Object { Write-Host "     $_" } }
    }
}
function Assert-Verdict($Run, [string]$Subject, [int]$Code, [string]$Word) {
    $last = ($Run.Output -split '\r?\n' | Where-Object { $_.Trim() } | Select-Object -Last 1)
    Assert-True "$Subject exits $Code with the word $Word" (($Run.Code -eq $Code) -and ($last -cmatch "^$([regex]::Escape($Subject)): $Word\b")) $Run.Output
}
function Assert-Kind($Run, [string]$Kind, [int]$Count) {
    $found = [regex]::Matches($Run.Output, "(?m)^$Kind\b").Count
    Assert-True "$Kind is reported $Count time(s)" ($found -eq $Count) "found $found`n$($Run.Output)"
}

$en = @{ title = 'A page title'; purpose = 'What this screen is for and how a person uses it from the start to the end.'; close = 'Closes this screen and returns to the list you came from.' }
$ru = @{ title = 'Заголовок страницы'; purpose = 'Для чего этот экран и как человек пользуется им от начала и до конца работы.'; close = 'Закрывает этот экран и возвращает к списку, откуда вы пришли.' }
$uk = @{ title = 'Заголовок сторінки'; purpose = 'Для чого цей екран і як людина користується ним від початку й до кінця роботи.'; close = 'Закриває цей екран і повертає до списку, звідки ви прийшли.' }
function New-HelpLocale($t) {
    return "{ `"title`": `"$($t.title)`", `"purpose`": `"$($t.purpose)`", `"orderNote`": `"$($t.close)`", `"elements`": { `"close`": { `"summary`": `"$($t.close)`", `"details`": `"$($t.close)`" } } }"
}

try {
    $null = New-Item -ItemType Directory -Force -Path $fx
    & git -C $fx init --quiet 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'git init failed' }
    Write-Fixture '.gitignore' "docs/ignored.md`n"
    Write-Fixture 'docs/ignored.md' "# ignored`n"
    Write-Fixture '.sza-canon.json' '{ "docRegistryShape": 1, "docRegistryFile": "docs/DOCUMENT_REGISTRY.jsonl" }'
    Write-Fixture 'tools/docs/registry-ignore.txt' "tools/fixtures/*.md # kept only for the self-test of this gate`n"
    Write-Fixture 'tools/fixtures/a.md' "fixture`n"
    Write-Fixture 'phone/src/main/res/values/strings.xml' '<resources><string name="tile">Tile</string></resources>'
    Write-Fixture 'docs/README.md' "guide.md other.md design/V.md handoff.md dict.md img/ TERMBASE.json help/content/ PRIVACY_POLICY.md PRIVACY_POLICY-ru.md PRIVACY_POLICY-uk.md PORTAL_SECTIONS_STREAMSPLAYER.md l10n-fingerprints.jsonl DOCUMENT_REGISTRY.jsonl`n"
    Write-Fixture 'docs/guide.md' @"
# Guide

Plain [other](other.md#a-heading), [self](#guide) and ![picture](img/a.png). Written as code: ``[x](missing.md)`` and ``...``.

``````
[x](also-missing.md) and three periods ... in a block
``````
"@
    Write-Fixture 'docs/other.md' "# Other`n`n## A heading`n"
    Write-Fixture 'docs/handoff.md' "# Handoff`n`n[broken](gone.md) and a dash $([char]0x2014) in a frozen record.`n"
    Write-Fixture 'docs/img/a.png' 'not really a picture'
    Write-Fixture 'docs/design/V.md' "# Variants`n`n## V1 Alpha`n"
    Write-Fixture 'docs/dict.md' "| Term | English | Русский | Українська |`n| --- | --- | --- | --- |`n| broadcast | broadcast | трансляция | трансляція |`n"
    Write-Fixture 'docs/TERMBASE.json' @'
{ "shape": 1, "dictionary": "docs/dict.md",
  "forbidden": [
    { "id": "relay-outside", "locales": ["en"], "pattern": "\\brelay\\b(?!\\s+through\\s+the\\s+server)", "reason": "the only term is relay through the server" },
    { "id": "air-noun", "locales": ["ru"], "pattern": "(?<!\\p{L})(эфир|эфиры)(?!\\p{L})", "reason": "a person finds a broadcast here" },
    { "id": "wire", "locales": ["en"], "caseSensitive": true, "pattern": "\\b[A-Z]{2,}(_[A-Z]+)+\\b", "reason": "a reader meets no wire value" } ],
  "terms": [
    { "id": "broadcast", "dictionary": "broadcast", "definition": { "en": "A live stream that a person plays.", "ru": "Живой поток, который воспроизводят.", "uk": "Живий потік, який відтворюють." } },
    { "id": "tile", "labelKey": "tile", "definition": { "en": "A tile on the home screen.", "ru": "Плитка на домашнем экране.", "uk": "Плитка на домашньому екрані." } } ] }
'@
    Write-Fixture 'docs/help/content/page.json' "{ `"help`": `"page`", `"elements`": [ { `"id`": `"close`" } ], `"locales`": { `"en`": $(New-HelpLocale $en), `"ru`": $(New-HelpLocale $ru), `"uk`": $(New-HelpLocale $uk) } }"
    Write-Fixture 'docs/PRIVACY_POLICY.md' "# Policy`n`nThe opening of the policy.`n`n## 1. First`n`nThe relay through the`nserver and a value in code: ``FOO_BAR``.`n`n## 2. Second`n`nThe second section of the policy, as long as it needs to be.`n"
    Write-Fixture 'docs/PRIVACY_POLICY-ru.md' "# Политика`n`nНачало политики.`n`n## 1. Первый`n`nПока вы в эфире, раздел первый остаётся на своём месте и читается целиком.`n`n## 2. Второй`n`nВторой раздел политики, такой длины, какая нужна для проверки шлюза.`n"
    Write-Fixture 'docs/PRIVACY_POLICY-uk.md' "# Політика`n`nПочаток політики.`n`n## 1. Перший`n`nПерший розділ лишається на своєму місці й читається повністю від початку.`n`n## 2. Другий`n`nДругий розділ політики, такої довжини, яка потрібна для перевірки шлюзу.`n"
    Write-Fixture 'docs/PORTAL_SECTIONS_STREAMSPLAYER.md' "# Sections`n`nThe handover note may say relay and FOO_BAR.`n`n## Page ``one`` - section`n`n### English`n`nThe portal section in English, as long as it needs to be for a block.`n`n### Русский`n`nРаздел портала по-русски, такой длины, какая нужна для целого блока.`n`n### Українська`n`nРозділ порталу українською, такої довжини, яка потрібна для цілого блоку.`n"
    Write-Fixture 'site/page.html' '<!doctype html><html><body><h2 id="here">Here</h2><a href="#here">here</a><a href="https://example.com/">out</a></body></html>'
    $records = @(
        @{ id = 'guide'; paths = @('docs/guide.md', 'docs/other.md', 'docs/README.md', 'docs/DOCUMENT_REGISTRY.jsonl', 'docs/dict.md', 'docs/TERMBASE.json', 'docs/l10n-fingerprints.jsonl', 'docs/PRIVACY_POLICY.md', 'docs/PRIVACY_POLICY-ru.md', 'docs/PRIVACY_POLICY-uk.md', 'docs/PORTAL_SECTIONS_STREAMSPLAYER.md', 'docs/help/content/*.json') }
        @{ id = 'variants'; paths = @('docs/design/V.md') }
        @{ id = 'picture'; paths = @('docs/img/*.png') }
        @{ id = 'pages'; paths = @('site/page.html') }
        @{ id = 'handoff'; paths = @('docs/handoff.md'); frozen = $true; design_record = 'docs/design/V.md#v1-alpha' }
    )
    $registry = foreach ($r in $records) {
        $row = [ordered]@{ id = $r.id; title = 'A record'; category = 'x'; audience = 'x'; paths = $r.paths; published = $false; indexable = $false; product_areas = @('x'); update_triggers = @('x'); generated = $false; role = 'source' }
        if ($r.ContainsKey('frozen')) { $row.frozen = $r.frozen; $row.design_record = $r.design_record }
        $row | ConvertTo-Json -Compress -Depth 4
    }
    Write-Fixture 'docs/DOCUMENT_REGISTRY.jsonl' (($registry -join "`n") + "`n")
    Write-Fixture 'docs/l10n-fingerprints.jsonl' ''

    # The site and help fixtures: copies of the real pages, the real inputs read in place.
    $siteFx = Join-Path $fixtureBase 'site-repo'
    foreach ($part in @('tools/site/build-site.ps1', 'docs/PRIVACY_POLICY.md', 'docs/PRIVACY_POLICY-ru.md', 'docs/PRIVACY_POLICY-uk.md', 'site')) {
        $target = Join-Path $siteFx $part
        $null = New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target)
        Copy-Item -LiteralPath (Join-Path $repoRoot $part) -Destination $target -Recurse -Force
    }
    $helpOut = Join-Path $fixtureBase 'help-out'
    $null = New-Item -ItemType Directory -Force -Path $helpOut
    foreach ($part in 'en', 'ru', 'uk', 'help.css') { Copy-Item -LiteralPath (Join-Path $repoRoot "phone/src/main/assets/help/$part") -Destination (Join-Path $helpOut $part) -Recurse -Force }
} catch {
    Write-Host "docs-gates-selftest: COULD NOT VERIFY (fixture: $($_.Exception.Message))"
    if (Test-Path -LiteralPath $fixtureBase) { Remove-Item -LiteralPath $fixtureBase -Recurse -Force -ErrorAction SilentlyContinue }
    exit 2
}

try {
    $gate = @{}
    foreach ($name in 'assert-doc-registry', 'assert-doc-links', 'assert-doc-style', 'assert-translation-freshness', 'assert-termbase', 'assert-docs') { $gate[$name] = Join-Path $PSScriptRoot "$name.ps1" }
    $generator = Join-Path $PSScriptRoot '../help/generate-help-pages.ps1'
    $siteGate = Join-Path $siteFx 'tools/site/build-site.ps1'

    # First record: every translated block of the fixture is rebuilt from its English block.
    $recorded = Invoke-Runs @(@{ Name = 'record'; Path = $gate['assert-translation-freshness']; Arguments = @('-RepoRoot', $fx, '-Record') })
    Assert-Verdict $recorded['record'] 'assert-translation-freshness' 0 'PASS'

    # A clean tree passes every gate; the aggregator answers for a generator that has no inputs here.
    $clean = Invoke-Runs @(
        foreach ($name in 'assert-doc-registry', 'assert-doc-links', 'assert-doc-style', 'assert-translation-freshness', 'assert-termbase', 'assert-docs') {
            @{ Name = $name; Path = $gate[$name]; Arguments = @('-RepoRoot', $fx) }
        }
        @{ Name = 'site'; Path = $siteGate; Arguments = @('-Check') }
    )
    foreach ($name in 'assert-doc-registry', 'assert-doc-links', 'assert-doc-style', 'assert-translation-freshness', 'assert-termbase') { Assert-Verdict $clean[$name] $name 0 'PASS' }
    Assert-Verdict $clean['assert-docs'] 'assert-docs' 2 'COULD NOT VERIFY'
    Assert-True 'the aggregator names the child it could not verify' ($clean['assert-docs'].Output -match 'assert-docs: COULD NOT VERIFY \(1: generate-help-pages\)') $clean['assert-docs'].Output
    Assert-True 'the site passes before it is seeded' ($clean['site'].Code -eq 0) $clean['site'].Output

    # One defect of every kind.
    Edit-Fixture 'docs/DOCUMENT_REGISTRY.jsonl' '"id":"variants"' '"id":"variants","design_record":"docs/design/V.md#no-such-heading"'
    Edit-Fixture 'docs/DOCUMENT_REGISTRY.jsonl' '"id":"picture","title":"A record","category":"x","audience":"x","paths":["docs/img/*.png"],"published":false,"indexable":false,"product_areas":["x"],"update_triggers":["x"],"generated":false,"role":"source"' '"id":"picture","title":"A record","category":"x","audience":"x","paths":["docs/img/*.png"],"published":false,"indexable":false,"product_areas":["x"],"update_triggers":["x"],"generated":true,"role":"source"'
    Add-Fixture 'docs/DOCUMENT_REGISTRY.jsonl' (@(
            '{"id":"ghost","title":"A record","category":"x","audience":"x","paths":["docs/ghost.md"],"published":false,"indexable":false,"product_areas":["x"],"update_triggers":["x"],"generated":false,"role":"source"}'
            '{"id":"secret","title":"A record","category":"x","audience":"x","paths":["docs/ignored.md"],"published":false,"indexable":false,"product_areas":["x"],"update_triggers":["x"],"generated":false,"role":"source"}'
            '{"id":"again","title":"A record","category":"x","audience":"x","paths":["docs/other.md"],"published":false,"indexable":false,"product_areas":["x"],"update_triggers":["x"],"generated":false,"role":"source"}'
        ) -join "`n") # no trailing newline: the next line is not JSON
    Add-Fixture 'docs/DOCUMENT_REGISTRY.jsonl' "`nnot json`n"
    Write-Fixture 'docs/undeclared.md' "# Nobody registered this`n"
    Edit-Fixture 'docs/README.md' 'other.md ' ''
    Add-Fixture 'tools/docs/registry-ignore.txt' "docs/gone-for-good.txt # a line that no longer matches any file`n"
    Edit-Fixture '.sza-canon.json' '"docRegistryShape": 1' '"docRegistryShape": 2'

    Add-Fixture 'docs/guide.md' @"

[a broken link](missing.md), [a broken anchor](other.md#no-such-heading), ![a missing picture](img/none.png),
[not https](http://example.com/page), ![a remote picture](https://example.com/a.png) and <script src="x.js"></script>.
A loopback address is fine: [here](http://localhost:8080/).
A long dash $([char]0x2014) and a middle dash $([char]0x2013), three periods ... and the character $([char]0x2026), and ещё written еще.
"@
    Edit-Fixture 'site/page.html' '<a href="#here">here</a>' '<a href="#nowhere">here</a><a href="gone.html">gone</a><a href="http://example.com/">plain</a>'

    Edit-Fixture 'docs/help/content/page.json' $en.title 'A changed title'
    Edit-Fixture 'docs/help/content/page.json' "`"title`": `"$($ru.title)`"" "`"title`": `"$($ru.title)`", `"steps`": [ `"Шаг`" ]"
    Edit-Fixture 'docs/help/content/page.json' $uk.purpose 'What this screen is for and how a person uses it, left in English by mistake.'
    Edit-Fixture 'docs/PRIVACY_POLICY.md' "The second section of the policy, as long as it needs to be." "The second section of the policy,`nas long as it needs to be."
    Add-Fixture 'docs/PRIVACY_POLICY-ru.md' "`n## 3. Лишний`n`nРаздел, которого нет в английском оригинале этой политики вообще.`n"
    Edit-Fixture 'docs/l10n-fingerprints.jsonl' '"doc":"portal"' '"doc":"portal-retired"'

    Edit-Fixture 'docs/PORTAL_SECTIONS_STREAMSPLAYER.md' 'The portal section in English' 'The relay and a value FOO_BAR in the portal section in English'
    Edit-Fixture 'docs/PRIVACY_POLICY-ru.md' 'Второй раздел политики' 'Эфир идёт, второй раздел политики'
    Edit-Fixture 'docs/TERMBASE.json' '"dictionary": "broadcast"' '"dictionary": "no such row"'
    Edit-Fixture 'docs/TERMBASE.json' '"labelKey": "tile"' '"labelKey": "no_such_string"'
    Edit-Fixture 'docs/TERMBASE.json' '"reason": "a reader meets no wire value"' '"reason": "too thin"'

    [IO.File]::WriteAllText((Join-Path $helpOut 'en/index.html'), 'edited by hand', $utf8)
    Remove-Item -LiteralPath (Join-Path $helpOut 'ru/index.html')
    [IO.File]::WriteAllText((Join-Path $helpOut 'uk/retired.html'), 'no source renders this', $utf8)

    $siteFile = { param($rel) Join-Path $siteFx $rel }
    $null = New-Item -ItemType Directory -Force -Path (& $siteFile 'site/help/en')
    [IO.File]::WriteAllText((& $siteFile 'site/help/en/index.html'), '<!doctype html><title>Help</title>', $utf8)
    [IO.File]::AppendAllText((& $siteFile 'site/robots.txt'), "Disallow: /help/`n", $utf8)
    $sitemap = [IO.File]::ReadAllText((& $siteFile 'site/sitemap.xml'), $utf8)
    [IO.File]::WriteAllText((& $siteFile 'site/sitemap.xml'), $sitemap.Replace('</urlset>', "  <url><loc>https://example.com/help/en/</loc></url>`n</urlset>"), $utf8)

    $seeded = Invoke-Runs @(
        foreach ($name in 'assert-doc-registry', 'assert-doc-links', 'assert-doc-style', 'assert-translation-freshness', 'assert-termbase', 'assert-docs') {
            @{ Name = $name; Path = $gate[$name]; Arguments = @('-RepoRoot', $fx) }
        }
        @{ Name = 'help'; Path = $generator; Arguments = @('-Check', '-RepoRoot', $repoRoot, '-OutAssets', $helpOut) }
        @{ Name = 'site'; Path = $siteGate; Arguments = @('-Check') }
    )

    $run = $seeded['assert-doc-registry']
    Assert-Verdict $run 'assert-doc-registry' 1 'FAIL'
    Assert-Kind $run 'BAD-RECORD' 2       # the `generated` that disagrees, the line that is not JSON
    Assert-Kind $run 'NO-FILE' 1
    Assert-Kind $run 'UNDECLARED' 1
    Assert-Kind $run 'TWO-HOMES' 1
    Assert-Kind $run 'IGNORED-BY-GIT' 1
    Assert-Kind $run 'STALE-IGNORE' 1
    Assert-Kind $run 'NO-INDEX' 4         # other.md in its two records, and the two records added without an index line
    Assert-Kind $run 'BAD-DESIGN-RECORD' 1
    Assert-Kind $run 'STAMP' 1

    $run = $seeded['assert-doc-links']
    Assert-Verdict $run 'assert-doc-links' 1 'FAIL'
    Assert-Kind $run 'BROKEN-LINK' 2      # missing.md, gone.html; never the two in code or the frozen record's
    Assert-Kind $run 'BROKEN-ANCHOR' 2    # other.md#no-such-heading, #nowhere
    Assert-Kind $run 'MISSING-ASSET' 1
    Assert-Kind $run 'INSECURE-LINK' 2    # the document's and the page's; never the loopback address
    Assert-Kind $run 'REMOTE-EMBED' 2     # the remote picture, the script

    $run = $seeded['assert-doc-style']
    Assert-Verdict $run 'assert-doc-style' 1 'FAIL'
    Assert-Kind $run 'LONG-DASH' 2
    Assert-Kind $run 'ELLIPSIS' 2
    Assert-Kind $run 'MISSING-YO' 1

    $run = $seeded['assert-translation-freshness']
    Assert-Verdict $run 'assert-translation-freshness' 1 'FAIL'
    Assert-Kind $run 'STALE' 2            # the changed title in ru and uk; never the re-wrapped section
    Assert-Kind $run 'EXTRA-VARIANT' 2    # the steps only the Russian source has, the section only the Russian policy has
    Assert-Kind $run 'UNTRANSLATED' 1
    Assert-True 'a block only a translation has is no English block' ($run.Output -match '(?m)^EXTRA-VARIANT privacy s03 \[ru\]') $run.Output
    Assert-True 'a re-wrapped English section is not stale' ($run.Output -notmatch '(?m)^STALE privacy') $run.Output
    Assert-Kind $run 'GONE-BLOCK' 1
    Assert-Kind $run 'NEW-BLOCK' 2        # the portal page whose row was renamed, in ru and uk

    $run = $seeded['assert-termbase']
    Assert-Verdict $run 'assert-termbase' 1 'FAIL'
    Assert-Kind $run 'BAD-TERMBASE' 3     # the dictionary row, the string, the thin reason
    Assert-Kind $run 'FORBIDDEN-TERM' 3   # relay and FOO_BAR in the block, the bare noun; never the term, the code span, `в эфире` or the handover note

    Assert-Verdict $seeded['assert-docs'] 'assert-docs' 1 'FAIL'

    $run = $seeded['help']
    Assert-Verdict $run 'generate-help-pages' 1 'FAIL'
    Assert-Kind $run 'STALE-PAGE' 1
    Assert-Kind $run 'MISSING-PAGE' 1
    Assert-Kind $run 'ORPHAN-PAGE' 1

    $run = $seeded['site']
    Assert-True 'the site gate fails a seeded site' ($run.Code -eq 1) $run.Output
    Assert-True 'a published help page without noindex is named' ($run.Output -match 'carries no noindex meta') $run.Output
    Assert-True 'a help page in the sitemap is named' ($run.Output -match 'lists a help page') $run.Output
    Assert-True 'robots blocking the help is named' ($run.Output -match 'blocks the help pages') $run.Output

    # -Record refuses what is not translated and writes nothing; once the translation is rebuilt it clears the flags.
    $before = [IO.File]::ReadAllText((Join-Path $fx 'docs/l10n-fingerprints.jsonl'), $utf8)
    $refused = Invoke-Runs @(@{ Name = 'refused'; Path = $gate['assert-translation-freshness']; Arguments = @('-RepoRoot', $fx, '-Record') })
    Assert-Verdict $refused['refused'] 'assert-translation-freshness' 1 'FAIL'
    Assert-True '-Record that refuses writes nothing' ($before -ceq [IO.File]::ReadAllText((Join-Path $fx 'docs/l10n-fingerprints.jsonl'), $utf8))
    Edit-Fixture 'docs/help/content/page.json' 'What this screen is for and how a person uses it, left in English by mistake.' $uk.purpose
    $rebuilt = Invoke-Runs @(@{ Name = 'rebuilt'; Path = $gate['assert-translation-freshness']; Arguments = @('-RepoRoot', $fx, '-Record') })
    Assert-Verdict $rebuilt['rebuilt'] 'assert-translation-freshness' 0 'PASS'
    Edit-Fixture 'docs/PRIVACY_POLICY-ru.md' "`n## 3. Лишний`n`nРаздел, которого нет в английском оригинале этой политики вообще.`n" ''
    Edit-Fixture 'docs/help/content/page.json' ", `"steps`": [ `"Шаг`" ]" ''
    $cleared = Invoke-Runs @(@{ Name = 'cleared'; Path = $gate['assert-translation-freshness']; Arguments = @('-RepoRoot', $fx) })
    Assert-Verdict $cleared['cleared'] 'assert-translation-freshness' 0 'PASS'
} catch {
    Write-Host "docs-gates-selftest: COULD NOT VERIFY ($($_.Exception.Message))"
    Remove-Item -LiteralPath $fixtureBase -Recurse -Force -ErrorAction SilentlyContinue
    exit 2
}

Remove-Item -LiteralPath $fixtureBase -Recurse -Force -ErrorAction SilentlyContinue
if ($script:failures.Count -gt 0) {
    Write-Host "docs-gates-selftest: FAIL ($($script:failures.Count) of $($script:cases))"
    exit 1
}
Write-Host "docs-gates-selftest: PASS (run=$($script:cases) skipped=0)"
exit 0
