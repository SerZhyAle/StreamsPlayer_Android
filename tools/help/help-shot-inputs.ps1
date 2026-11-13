#requires -Version 7.0
<#
.SYNOPSIS
    The fingerprint of what a help screenshot shows (CONTEXT-HELP section 4, rung 2). Dot-sourced by
    capture-help-screenshots.ps1, which records it in img/capture.json, and by assert-help-map.ps1, which
    recomputes it and fails a page whose picture no longer matches its inputs.

.DESCRIPTION
    A picture is fresh while two things are unchanged since it was captured: the layout and menu files its
    recipe names (the controls on the frame), and the element list of its page source (the numbered markers:
    `id`, `viewId`, `descKeys` of each element, in order). Page prose is not part of it, so an edit of the wording
    of a page does not ask for a new capture.

    What the frame draws from those files has its own fingerprint (Get-HelpShotVisible, section 7 item D of the
    contract, S0051): the strings they and the page's elements name, in en, ru and uk, and the drawables they name.
    A renamed button then asks for a new capture, as it should. A capture record written before S0051 has no such
    fingerprint; the gate names it and the next capture writes it.

    Line endings are normalised to LF before hashing, so a checkout with other line endings reads the same.
#>

Set-StrictMode -Version Latest

function Get-HelpShotFingerprint {
    <#
    .OUTPUTS
        [pscustomobject] @{ Sha256 = <lowercase hex or $null>; Missing = <relative paths that do not exist> }
    #>
    param(
        [Parameter(Mandatory)][string]$Root,
        [Parameter(Mandatory)][string]$Help,
        [string[]]$Inputs = @()
    )
    $missing = [System.Collections.Generic.List[string]]::new()
    $text = [System.Text.StringBuilder]::new()
    foreach ($rel in @($Inputs | ForEach-Object { $_.Replace('\', '/') } | Sort-Object -Unique)) {
        $path = Join-Path $Root $rel
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { $missing.Add($rel); continue }
        $content = [System.IO.File]::ReadAllText($path).Replace("`r`n", "`n")
        $null = $text.Append("file $rel`n").Append($content).Append("`n")
    }
    $sourceRel = "docs/help/content/$Help.json"
    $sourcePath = Join-Path $Root $sourceRel
    if (Test-Path -LiteralPath $sourcePath -PathType Leaf) {
        $source = Get-Content -LiteralPath $sourcePath -Raw -Encoding utf8 | ConvertFrom-Json
        $null = $text.Append("markers $sourceRel`n")
        foreach ($element in @($source.elements)) {
            $get = { param($name) $p = $element.PSObject.Properties[$name]; if ($p) { @($p.Value) -join ',' } else { '' } }
            $null = $text.Append(('{0}|{1}|{2}' -f (& $get 'id'), (& $get 'viewId'), (& $get 'descKeys'))).Append("`n")
        }
    } else {
        $missing.Add($sourceRel)
    }
    $sha = $null
    if ($missing.Count -eq 0) {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($text.ToString())
        $sha = ([System.Security.Cryptography.SHA256]::HashData($bytes) | ForEach-Object { $_.ToString('x2') }) -join ''
    }
    [pscustomobject]@{ Sha256 = $sha; Missing = @($missing) }
}

$script:HelpShotStrings = @{}

function Get-HelpShotStringTable {
    # name -> raw value of every string of one locale folder. Read once per run: every page asks for the same tables.
    param([Parameter(Mandatory)][string]$Root, [Parameter(Mandatory)][string]$Folder)
    $key = "$Root|$Folder"
    if ($script:HelpShotStrings.ContainsKey($key)) { return $script:HelpShotStrings[$key] }
    $table = @{}
    $path = Join-Path $Root "phone/src/main/res/$Folder"
    if (Test-Path -LiteralPath $path -PathType Container) {
        foreach ($file in Get-ChildItem -LiteralPath $path -File -Filter '*.xml' | Sort-Object Name) {
            $text = [System.IO.File]::ReadAllText($file.FullName).Replace("`r`n", "`n")
            foreach ($m in [regex]::Matches($text, '(?s)<string\s+name="([^"]+)"[^>]*>(.*?)</string>')) { $table[$m.Groups[1].Value] = $m.Groups[2].Value }
        }
    }
    $script:HelpShotStrings[$key] = $table
    return $table
}

function Get-HelpShotVisible {
    <#
    .SYNOPSIS
        The fingerprint of the strings and drawables a help screenshot can show. Lowercase hex; never $null.
    #>
    param(
        [Parameter(Mandatory)][string]$Root,
        [Parameter(Mandatory)][string]$Help,
        [string[]]$Inputs = @()
    )
    $strings = [System.Collections.Generic.SortedSet[string]]::new([StringComparer]::Ordinal)
    $drawables = [System.Collections.Generic.SortedSet[string]]::new([StringComparer]::Ordinal)
    foreach ($rel in @($Inputs | ForEach-Object { $_.Replace('\', '/') } | Sort-Object -Unique)) {
        $path = Join-Path $Root $rel
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { continue }
        $content = [System.IO.File]::ReadAllText($path)
        foreach ($m in [regex]::Matches($content, '@string/([A-Za-z0-9_]+)')) { $null = $strings.Add($m.Groups[1].Value) }
        foreach ($m in [regex]::Matches($content, '@(?:drawable|mipmap)/([A-Za-z0-9_]+)')) { $null = $drawables.Add($m.Groups[1].Value) }
    }
    $sourcePath = Join-Path $Root "docs/help/content/$Help.json"
    if (Test-Path -LiteralPath $sourcePath -PathType Leaf) {
        $source = Get-Content -LiteralPath $sourcePath -Raw -Encoding utf8 | ConvertFrom-Json
        foreach ($element in @($source.elements)) {
            foreach ($name in 'labelKey', 'descKeys') {
                $property = $element.PSObject.Properties[$name]
                if ($property) { foreach ($key in @($property.Value)) { $null = $strings.Add([string]$key) } }
            }
        }
    }
    $text = [System.Text.StringBuilder]::new()
    foreach ($folder in 'values', 'values-ru', 'values-uk') {
        $table = Get-HelpShotStringTable -Root $Root -Folder $folder
        foreach ($name in $strings) {
            if ($table.ContainsKey($name)) { $null = $text.Append("$folder/$name=").Append($table[$name]).Append("`n") }
        }
    }
    $resRoot = Join-Path $Root 'phone/src/main/res'
    if ($drawables.Count -gt 0 -and (Test-Path -LiteralPath $resRoot -PathType Container)) {
        $folders = @(Get-ChildItem -LiteralPath $resRoot -Directory | Where-Object { $_.Name -match '^(drawable|mipmap)' } | Sort-Object Name)
        foreach ($name in $drawables) {
            foreach ($folder in $folders) {
                foreach ($file in @(Get-ChildItem -LiteralPath $folder.FullName -File -Filter "$name.*" | Sort-Object Name)) {
                    $hash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
                    $null = $text.Append("$($folder.Name)/$($file.Name)=$hash`n")
                }
            }
        }
    }
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($text.ToString())
    return ([System.Security.Cryptography.SHA256]::HashData($bytes) | ForEach-Object { $_.ToString('x2') }) -join ''
}
