#requires -Version 7.0
<#
.SYNOPSIS
    S0033: renders the hosted privacy pages (site/) from the policy sources (docs/PRIVACY_POLICY*.md).

.DESCRIPTION
    site/privacy.html, site/privacy-ru.html, site/privacy-uk.html, site/sitemap.xml and site/robots.txt are
    GENERATED. Never hand-edit them: the next run reverts the edit. Edit the markdown source (the permissions table
    inside it comes from tools/privacy/permissions.ps1) and run this script.

    (no switch)   writes the files.
    -Check        writes nothing; exits 1 and names every file that differs from what the sources render to, every
                  page that loads a script, font, image or stylesheet from another site (the policy says the pages
                  load none), and a published help page without noindex, listed in the sitemap or blocked by
                  robots.txt, and exits 0 otherwise. Run it before the privacy URL is given to the store.

    The pages follow the product-web-pages family: the byte-identical reference kit vendored at
    site/assets/sza-kit.css (PAGE-STYLE 0 and 5; its sha256 is recorded in site/assets/PROVENANCE.txt and checked
    here), a page layer linked after it, the header with the RU EN UA control and the theme button, and the footer
    with the sibling tools of SITE-FAMILY-MAP 1.9 and the one contact of its rule 3. The pages are per language
    (PAGE-STYLE 6, "Per-language secondary documentation pages") and make no request outside their own host.

    pwsh -NoProfile -File tools/site/build-site.ps1
    pwsh -NoProfile -File tools/site/build-site.ps1 -Check
#>
[CmdletBinding()]
param([switch]$Check)
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$utf8 = [Text.UTF8Encoding]::new($false)
$base = 'https://serzhyale.github.io/StreamsPlayer_Android'

# One entry per hosted locale. `Key` is the PAGE-STYLE switch key (ua for Ukrainian), `Lang` the ISO code.
$pages = @(
    [pscustomobject]@{
        Key = 'en'; Lang = 'en'; Source = 'docs/PRIVACY_POLICY.md'; File = 'privacy.html'; Locale = 'en_US'
        Title = 'Privacy Policy - StreamsPlayer'
        Description = 'What StreamsPlayer for Android, Wear OS and its watch face store, send and ask permission for. The developer receives none of your data.'
        Theme = 'Theme'; ToLight = 'Switch to light theme'; ToDark = 'Switch to dark theme'; Top = 'Back to top'; LangLabel = 'Language'
        Tools = @('Android media sorter', 'Windows media and file manager','Windows layout fixer', 'Windows ebook converter', 'Windows storage CLI', 'Windows stream player', 'Windows tray launcher', 'AI-dev methodology')
    }
    [pscustomobject]@{
        Key = 'ru'; Lang = 'ru'; Source = 'docs/PRIVACY_POLICY-ru.md'; File = 'privacy-ru.html'; Locale = 'ru_RU'
        Title = 'Политика конфиденциальности - StreamsPlayer'
        Description = 'Что приложение StreamsPlayer для Android, Wear OS и его циферблат хранят, отправляют и какие разрешения просят. Разработчик не получает никаких ваших данных.'
        Theme = 'Тема'; ToLight = 'Переключить на светлую тему'; ToDark = 'Переключить на тёмную тему'; Top = 'Наверх'; LangLabel = 'Язык'
        Tools = @('Сортировщик медиа для Android', 'Медиа- и файловый менеджер для Windows','Исправление раскладки для Windows', 'Конвертер книг для Windows', 'Консоль хранилища для Windows', 'Плеер потоков для Windows', 'Трей-запускатель для Windows', 'Методология AI-разработки')
    }
    [pscustomobject]@{
        Key = 'ua'; Lang = 'uk'; Source = 'docs/PRIVACY_POLICY-uk.md'; File = 'privacy-uk.html'; Locale = 'uk_UA'
        Title = 'Політика конфіденційності - StreamsPlayer'
        Description = 'Що застосунок StreamsPlayer для Android, Wear OS і його циферблат зберігають, надсилають і які дозволи просять. Розробник не отримує жодних ваших даних.'
        Theme = 'Тема'; ToLight = 'Перемкнути на світлу тему'; ToDark = 'Перемкнути на темну тему'; Top = 'Нагору'; LangLabel = 'Мова'
        Tools = @('Сортувальник медіа для Android', 'Медіа- та файловий менеджер для Windows','Виправлення розкладки для Windows', 'Конвертер книг для Windows', 'Консоль сховища для Windows', 'Програвач потоків для Windows', 'Трей-запускач для Windows', 'Методологія AI-розробки')
    }
)

# SITE-FAMILY-MAP 1.10, section 2: every tool but this product (which is not in the map until its page answers).
# Each pair carries a leading comma: without it PowerShell flattens the outer @( .. ) into sixteen strings.
$tools = @(
    ,@('Fast Media Sorter &amp; Organizer', 'https://serzhyale.github.io/FastMediaSorter_mob_v2/')
    ,@('Fast Media Sorter &amp; Sharing for Windows', 'https://serzhyale.github.io/FastMediaSorter_Lite/')
    ,@('CyrFlip', 'https://serzhyale.github.io/CyrFlip/')
    ,@('doc-html-translate', 'https://serzhyale.github.io/doc-html-translate/')
    ,@('FileDO', 'https://serzhyale.github.io/FileDO/')
    ,@('STREAMS Player', 'https://serzhyale.github.io/StreamsPlayer/')
    ,@('OneClickRunner', 'https://serzhyale.github.io/OneClickRunner/')
    ,@('Universal Agent Kit', 'https://serzhyale.github.io/universal-agent-kit/')
)

function Read-Text([string]$path) { ([IO.File]::ReadAllText($path, $utf8)) -replace "`r`n", "`n" }
function Esc([string]$s) { [Net.WebUtility]::HtmlEncode($s) }

function ConvertTo-Body([string]$markdownPath) {
    $html = (ConvertFrom-Markdown -Path $markdownPath).Html -replace "`r`n", "`n"
    $html = [regex]::Replace($html, '(?s)<!--.*?-->\n?', '')
    $html = $html.Replace('<table>', '<div class="table-wrap"><table>').Replace('</table>', '</table></div>')
    $html = $html.Replace('<a href="http', '<a rel="noopener" href="http')
    $html.Trim()
}

function New-Page($page) {
    $url = "$base/$($page.File)"
    $body = ConvertTo-Body (Join-Path $repoRoot $page.Source)
    $alternates = ($pages | ForEach-Object { "    <link rel=`"alternate`" hreflang=`"$($_.Lang)`" href=`"$base/$($_.File)`">" }) -join "`n"
    $segButtons = ($pages | Sort-Object { @('ru', 'en', 'ua').IndexOf($_.Key) } | ForEach-Object {
            $pressed = if ($_.Key -eq $page.Key) { 'true' } else { 'false' }
            "      <button type=`"button`" data-lang=`"$($_.Key)`" aria-pressed=`"$pressed`">$($_.Key.ToUpperInvariant())</button>"
        }) -join "`n"
    $noscript = ($pages | ForEach-Object { "<a href=`"$($_.File)`" hreflang=`"$($_.Lang)`">$(@{ en = 'English'; ru = 'Русский'; uk = 'Українська' }[$_.Lang])</a>" }) -join ' &middot; '
    $grid = (0..($tools.Count - 1) | ForEach-Object { "      <a href=`"$($tools[$_][1])`" rel=`"noopener`"><b>$($tools[$_][0])</b><span>$(Esc $page.Tools[$_])</span></a>" }) -join "`n"
    $ld = [ordered]@{
        '@context' = 'https://schema.org'; '@type' = 'WebPage'; name = $page.Title; description = $page.Description
        url = $url; inLanguage = $page.Lang
        publisher = [ordered]@{ '@type' = 'Person'; name = 'Serhii Zhyhunenko'; url = 'https://sza.od.ua' }
    } | ConvertTo-Json -Compress -Depth 4
    $ld = $ld.Replace('<', '<')
    $title = Esc $page.Title
    $desc = Esc $page.Description

    @"
<!doctype html>
<html lang="$($page.Lang)" dir="ltr" data-theme="dark">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>$title</title>
  <meta name="description" content="$desc">
  <meta name="theme-color" content="#0a0f0a">
  <link rel="canonical" href="$url">
$alternates
  <link rel="alternate" hreflang="x-default" href="$base/privacy.html">
  <meta property="og:type" content="website">
  <meta property="og:site_name" content="StreamsPlayer">
  <meta property="og:title" content="$title">
  <meta property="og:description" content="$desc">
  <meta property="og:url" content="$url">
  <meta property="og:locale" content="$($page.Locale)">
  <meta name="twitter:card" content="summary">
  <meta name="twitter:title" content="$title">
  <meta name="twitter:description" content="$desc">
  <script type="application/ld+json">$ld</script>
  <!-- PAGE-STYLE section 7: theme and the touch profile are resolved before the first stylesheet, so nothing flashes.
       sza-theme holds exactly dark or light and is shared with every page of this origin. -->
  <script>
(function(){try{
  var h=document.documentElement,t=localStorage.getItem('sza-theme');
  if(t!=='dark'&&t!=='light')t=matchMedia('(prefers-color-scheme: light)').matches?'light':'dark';
  h.setAttribute('data-theme',t);
  h.setAttribute('data-ui-profile',matchMedia('(pointer: coarse)').matches?'touch':'pointer');
}catch(e){}})();
  </script>
  <link rel="stylesheet" href="assets/sza-kit.css">
  <link rel="stylesheet" href="assets/policy.css">
</head>
<body>
<div class="bg-blobs"><div class="blob blob-1"></div><div class="blob blob-2"></div><div class="blob blob-3"></div></div>
<header class="site-header">
  <span class="brand">StreamsPlayer<span class="dot">.</span></span>
  <div class="seg" role="group" aria-label="$($page.LangLabel)">
$segButtons
  </div>
  <button type="button" class="theme-btn" id="themeBtn" aria-label="$($page.ToLight)"><span class="gi gi-theme" aria-hidden="true"></span><span>$($page.Theme)</span></button>
</header>
<main class="container section" id="main">
  <noscript><p class="lang-links">$noscript</p></noscript>
  <article class="policy-card">
$body
  </article>
</main>
<footer class="site-footer"><div class="container">
  <div class="tools-grid">
$grid
  </div>
  <div class="footer-bottom">
    <span>&copy; 2026 <a href="https://sza.od.ua" rel="noopener">Serhii Zhyhunenko</a></span>
    <span><a href="https://sza.od.ua" rel="noopener">sza.od.ua</a> &middot; <a href="https://github.com/SerZhyAle" rel="noopener">GitHub</a> &middot; <a href="mailto:sza@ukr.net">sza@ukr.net</a></span>
  </div>
</div></footer>
<button type="button" class="to-top" id="toTop" aria-label="$($page.Top)"><span class="gi gi-top" aria-hidden="true"></span></button>
<script>
(function(){
  var h=document.documentElement,urls={ru:'privacy-ru.html',en:'privacy.html',ua:'privacy-uk.html'};
  var tb=document.getElementById('themeBtn'),labels={light:'$($page.ToDark)',dark:'$($page.ToLight)'};
  function sync(){tb.setAttribute('aria-label',labels[h.getAttribute('data-theme')==='light'?'light':'dark'])}
  sync();
  tb.addEventListener('click',function(){
    var n=h.getAttribute('data-theme')==='light'?'dark':'light';
    h.setAttribute('data-theme',n);
    try{localStorage.setItem('sza-theme',n)}catch(e){}
    sync();
  });
  Array.prototype.forEach.call(document.querySelectorAll('.seg button[data-lang]'),function(b){
    b.addEventListener('click',function(){
      var k=b.getAttribute('data-lang');
      try{localStorage.setItem('sza-lang',k)}catch(e){}
      location.href=urls[k];
    });
  });
  var top=document.getElementById('toTop');
  addEventListener('scroll',function(){top.classList.toggle('show',scrollY>600)},{passive:true});
  top.addEventListener('click',function(){scrollTo({top:0})});
})();
</script>
</body>
</html>
"@ -replace "`r`n", "`n"
}

$expected = [ordered]@{}
foreach ($page in $pages) { $expected["site/$($page.File)"] = New-Page $page }
$expected['site/sitemap.xml'] = (@('<?xml version="1.0" encoding="UTF-8"?>', '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml">') +
    @($pages | ForEach-Object {
            $page = $_
            @('  <url>', "    <loc>$base/$($page.File)</loc>") +
            @($pages | ForEach-Object { "    <xhtml:link rel=`"alternate`" hreflang=`"$($_.Lang)`" href=`"$base/$($_.File)`"/>" }) +
            @('  </url>')
        }) + @('</urlset>', '')) -join "`n"
$expected['site/robots.txt'] = "User-agent: *`nAllow: /`n`nSitemap: $base/sitemap.xml`n"
$expected['site/.nojekyll'] = ''

$problems = [Collections.Generic.List[string]]::new()
foreach ($rel in $expected.Keys) {
    $path = Join-Path $repoRoot $rel
    if ($Check) {
        if (-not (Test-Path -LiteralPath $path)) { $problems.Add("$rel is missing - run tools/site/build-site.ps1"); continue }
        if ((Read-Text $path) -cne $expected[$rel]) { $problems.Add("$rel differs from what its sources render to - run tools/site/build-site.ps1 (never hand-edit it)") }
    }
    else {
        New-Item -ItemType Directory -Force -Path (Split-Path $path) | Out-Null
        [IO.File]::WriteAllText($path, $expected[$rel], $utf8)
        Write-Host "site: wrote $rel"
    }
}

# The vendored kit is read-only: it must still be the bytes recorded when it was copied.
$provenance = Join-Path $repoRoot 'site/assets/PROVENANCE.txt'
$kit = Join-Path $repoRoot 'site/assets/sza-kit.css'
if (-not (Test-Path -LiteralPath $kit) -or -not (Test-Path -LiteralPath $provenance)) {
    $problems.Add('site/assets/sza-kit.css or site/assets/PROVENANCE.txt is missing')
}
else {
    $recorded = [regex]::Match((Read-Text $provenance), '(?m)^sza-kit\.css sha256 ([0-9a-f]{64})$')
    $actual = (Get-FileHash -LiteralPath $kit -Algorithm SHA256).Hash.ToLowerInvariant()
    if (-not $recorded.Success) { $problems.Add('site/assets/PROVENANCE.txt has no "sza-kit.css sha256 <hash>" line') }
    elseif ($recorded.Groups[1].Value -ne $actual) { $problems.Add("site/assets/sza-kit.css (sha256 $actual) is not the vendored kit recorded in PROVENANCE.txt - the kit is never edited here") }
}

# "The pages load no script, font, image or tracker from any other site" - proved, not asserted.
foreach ($rel in ($expected.Keys | Where-Object { $_ -like '*.html' })) {
    $html = $expected[$rel]
    $loads = [regex]::Matches($html, '(?i)<(script|img|iframe|source|video|audio|embed|object)\b[^>]*\b(src|data)\s*=\s*["'']?(https?:)?//') +
        [regex]::Matches($html, '(?i)<link\b[^>]*\brel\s*=\s*["''](stylesheet|preload|preconnect|prefetch|icon)["''][^>]*\bhref\s*=\s*["'']?(https?:)?//')
    foreach ($m in $loads) { $problems.Add("$rel loads a resource from another site: $($m.Value)") }
    if (([regex]::Matches($html, '<h1[\s>]')).Count -ne 1) { $problems.Add("$rel must have exactly one h1") }
}

# DOC-EXTERNAL-QUALITY section 8 item O and CONTEXT-HELP section 7 item G: a help page published on the site carries
# noindex, is out of the sitemap, and robots.txt does not block it (S0051).
$helpRoot = Join-Path $repoRoot 'site/help'
if (Test-Path -LiteralPath $helpRoot -PathType Container) {
    foreach ($file in Get-ChildItem -LiteralPath $helpRoot -Recurse -File -Filter '*.html') {
        if ((Read-Text $file.FullName) -notmatch '<meta name="robots" content="noindex">') {
            $problems.Add("site/help/$([IO.Path]::GetRelativePath($helpRoot, $file.FullName).Replace('\', '/')) carries no noindex meta")
        }
    }
}
if ($Check) {
    foreach ($rel in 'site/sitemap.xml', 'site/robots.txt') {
        $path = Join-Path $repoRoot $rel
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { continue }
        $text = Read-Text $path
        if ($rel -like '*sitemap*' -and $text -match '<loc>[^<]*/help/') { $problems.Add("$rel lists a help page; a published help page is not in the sitemap") }
        if ($rel -like '*robots*' -and $text -match '(?im)^\s*Disallow:\s*/?help\b') { $problems.Add("$rel blocks the help pages; they stay crawlable") }
    }
}

if ($problems.Count) {
    $problems | ForEach-Object { Write-Host "FAIL site: $_" }
    exit 1
}
if ($Check) { Write-Host "OK site: $($pages.Count) pages, sitemap and robots equal their sources; the kit is the vendored one; no outside loads." }
exit 0
