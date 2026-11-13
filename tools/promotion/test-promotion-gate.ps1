#requires -Version 7.0
<#
.SYNOPSIS
    Self-test of tools/promotion/assert-promotion.ps1 (S0014): a small seeded repository, one defect per case, and the gate must
    name exactly that defect - and stay green on what is allowed.

.DESCRIPTION
    Copies the real list, positioning source, listing source, README and phone strings into a scratch folder, mutates one thing per
    case and runs the gate against it. Cases: a clean seed; a competitor name, a piracy word, a free-TV claim and its Russian form
    in published sources; the field-only playlist term allowed in the README and refused in a topic; a title, short description
    over the limit; twenty-one topics; a foreign contact; a pillar missing from the listing; a pattern that does not compile; a
    missing listing file.

.NOTES
    Exit codes (CHECK-VERDICT): 0 passed; 1 a case failed; 2 could not run. The last line on stdout is the verdict line. The
    scratch folder is removed at the end.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
$gate = Join-Path $repo 'tools/promotion/assert-promotion.ps1'
$scratch = Join-Path ([IO.Path]::GetTempPath()) "spp-$([guid]::NewGuid().ToString('N').Substring(0, 8))"
$utf8 = [Text.UTF8Encoding]::new($false)
$failures = [Collections.Generic.List[string]]::new()
$cases = 0

function Copy-Seed([string]$To) {
    foreach ($rel in 'tools/promotion/forbidden-terms.json', 'POSITIONING.md', 'README.md', 'docs/play/PLAY_LISTING.md', 'docs/play/PLAY_LISTING-ru.md', 'docs/play/PLAY_LISTING-uk.md', 'phone/src/main/res/values/strings.xml') {
        $target = Join-Path $To $rel
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
        Copy-Item -LiteralPath (Join-Path $repo $rel) -Destination $target
    }
}

function Invoke-Case([string]$Name, [scriptblock]$Mutate, [int]$ExpectExit, [string]$ExpectKind) {
    $script:cases++
    $dir = Join-Path $scratch $Name
    Copy-Seed $dir
    & $Mutate $dir
    $out = & pwsh -NoProfile -File $gate -RepoRoot $dir 2>&1 | Out-String
    $code = $LASTEXITCODE
    $ok = ($code -eq $ExpectExit)
    if ($ExpectKind) { $ok = $ok -and ($out -match "(?m)^$ExpectKind ") }
    else { $ok = $ok -and ($out -notmatch '(?m)^(BAD-LIST|MISSING|FORBIDDEN-TERM|FORBIDDEN-FIELD|LIMIT|TOPICS|CONTACT|PILLAR-ORDER) ') }
    if (-not $ok) { $script:failures.Add("$Name : expected exit $ExpectExit and '$ExpectKind', got exit $code`n$out") }
}

function Edit-Text([string]$Dir, [string]$Rel, [scriptblock]$Change) {
    $path = Join-Path $Dir $Rel
    [IO.File]::WriteAllText($path, (& $Change ([IO.File]::ReadAllText($path, $utf8))), $utf8)
}

try {
    New-Item -ItemType Directory -Force -Path $scratch | Out-Null

    Invoke-Case 'clean' { param($d) } 0 ''
    Invoke-Case 'competitor-name' { param($d) Edit-Text $d 'README.md' { param($t) "$t`nWorks like VLC.`n" } } 1 'FORBIDDEN-TERM'
    Invoke-Case 'piracy-word' { param($d) Edit-Text $d 'README.md' { param($t) "$t`nWatch IPTV channels.`n" } } 1 'FORBIDDEN-TERM'
    Invoke-Case 'free-tv-claim' { param($d) Edit-Text $d 'README.md' { param($t) "$t`nFree TV on your phone.`n" } } 1 'FORBIDDEN-TERM'
    Invoke-Case 'free-tv-russian' { param($d) Edit-Text $d 'docs/play/PLAY_LISTING-ru.md' { param($t) "$t`nБесплатное ТВ.`n" } } 1 'FORBIDDEN-TERM'
    Invoke-Case 'field-term-allowed-in-prose' { param($d) Edit-Text $d 'README.md' { param($t) "$t`nImports an m3u playlist.`n" } } 0 ''
    Invoke-Case 'field-term-in-topic' { param($d) Edit-Text $d 'POSITIONING.md' { param($t) $t -replace '(?m)^(Topics: .+)$', '$1, m3u-playlist' } } 1 'FORBIDDEN-FIELD'
    Invoke-Case 'title-over-limit' { param($d) Edit-Text $d 'docs/play/PLAY_LISTING.md' { param($t) $t -replace 'StreamsPlayer: Internet Radio', 'StreamsPlayer: Internet Radio Player' } } 1 'LIMIT'
    Invoke-Case 'short-over-limit' { param($d) Edit-Text $d 'docs/play/PLAY_LISTING-uk.md' { param($t) $t -replace '(?m)^(Інтернет-радіо, live-відео[^\r\n]+)$', '$1 і ще багато-багато інших корисних можливостей для вас' } } 1 'LIMIT'
    Invoke-Case 'too-many-topics' { param($d) Edit-Text $d 'POSITIONING.md' { param($t) $t -replace '(?m)^(Topics: .+)$', ('$1, ' + ((1..8 | ForEach-Object { "extra-$_" }) -join ', ')) } } 1 'TOPICS'
    Invoke-Case 'foreign-contact' { param($d) Edit-Text $d 'README.md' { param($t) "$t`nMail someone@example.org.`n" } } 1 'CONTACT'
    Invoke-Case 'pillar-missing' { param($d) Edit-Text $d 'POSITIONING.md' { param($t) $t -replace '\| Live broadcast \|', '| Zzz broadcast |' } } 1 'PILLAR-ORDER'
    Invoke-Case 'broken-pattern' { param($d) Edit-Text $d 'tools/promotion/forbidden-terms.json' { param($t) $t.Replace('"\\bip[\\s-]?tv\\b"', '"(["') } } 1 'BAD-LIST'
    Invoke-Case 'listing-file-missing' { param($d) Remove-Item -LiteralPath (Join-Path $d 'docs/play/PLAY_LISTING-ru.md') } 1 'MISSING'

    foreach ($failure in $failures) { Write-Host $failure }
    if ($failures.Count -gt 0) {
        Write-Host "promotion-gate-selftest: FAIL ($($failures.Count) of $cases cases)"
        exit 1
    }
    Write-Host "promotion-gate-selftest: PASS ($cases cases)"
    exit 0
} catch {
    Write-Host $_.Exception.Message
    Write-Host "promotion-gate-selftest: COULD NOT RUN ($($_.Exception.Message))"
    exit 2
} finally {
    if (Test-Path -LiteralPath $scratch) { Remove-Item -LiteralPath $scratch -Recurse -Force -ErrorAction SilentlyContinue }
}
