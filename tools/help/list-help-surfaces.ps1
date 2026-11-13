<#
.SYNOPSIS
    Enumerates the app's user-facing surfaces for the in-app help map (CONTEXT-HELP rule 19).

.DESCRIPTION
    Ported from FastMediaSorter Android scripts/docs/help/list-help-surfaces.ps1 (S0035 item 5) and adapted to this
    tree. The source of truth is the code, never memory:
    - every <activity> of phone/src/main/AndroidManifest.xml, minus the explicit exclusion list below;
    - every Kotlin class under phone/src/main/java that extends DialogFragment, BottomSheetDialogFragment or
      AppCompatDialogFragment;
    - every layout file dialog_*.xml and fragment_*.xml under phone/src/main/res/layout*, unless a surface class
      above already inflates it (a dialog built from a layout by a plain object, or a settings panel included in a
      screen, has no class of its own). sheet_*.xml layouts belong to the sheet classes and are never listed.
    Surface id = the class simple name for an activity or a dialog class, the layout file name for a layout.
    Plain confirmations built with an AlertDialog builder and no layout of their own are not enumerated: their
    text is the whole explanation (CONTEXT-HELP rule 4, `none`).

    Get-WearHelpSurfaces enumerates the watch app from its route table: every composable(..) destination under
    wear/src/main/java. The watch has one activity, so a route is the only declaration of a screen; the surface id is
    `wear:` followed by the route reference as written (wear:WearRoutes.HOME) or the literal route.

    The gate tools/help/assert-help-map.ps1 dot-sources this file and calls the two functions, so the enumeration
    lives in one place.

.PARAMETER Json
    Print a JSON array of { id, kind, source } instead of one id per line.

.PARAMETER RepoRoot
    Repository root; defaults to two levels above this script's folder.

.PARAMETER Module
    Which app to list when run standalone: phone (default) or wear.

.NOTES
    Exit codes:
      0 - surfaces listed
      2 - the manifest or the source root was not found
#>
[CmdletBinding()]
param(
    [switch]$Json,
    [string]$RepoRoot = '',
    [ValidateSet('phone', 'wear')]
    [string]$Module = 'phone'
)

# Not surfaces: system-hosted receivers and anything that is not an <activity>. None needed today; an entry here
# is a decision with a reason, written next to the id.
$script:HelpSurfaceExclusions = @()

function ConvertTo-BindingName {
    param([Parameter(Mandatory)][string]$LayoutName)
    return (($LayoutName -split '_' | ForEach-Object { if ($_) { $_.Substring(0, 1).ToUpperInvariant() + $_.Substring(1) } }) -join '') + 'Binding'
}

function Get-HelpSurfaces {
    param([Parameter(Mandatory)][string]$Root)

    $manifest = Join-Path $Root 'phone/src/main/AndroidManifest.xml'
    $javaRoot = Join-Path $Root 'phone/src/main/java'
    $resRoot = Join-Path $Root 'phone/src/main/res'
    if (-not (Test-Path -LiteralPath $manifest) -or -not (Test-Path -LiteralPath $javaRoot)) {
        throw "help surfaces: source not found under $Root"
    }

    $surfaces = [System.Collections.Generic.List[object]]::new()
    $seen = [System.Collections.Generic.HashSet[string]]::new()

    $manifestText = [IO.File]::ReadAllText($manifest)
    foreach ($m in [regex]::Matches($manifestText, '<activity\s+android:name="([^"]+)"')) {
        $id = ($m.Groups[1].Value -split '\.')[-1]
        if ($script:HelpSurfaceExclusions -contains $id) { continue }
        if ($seen.Add($id)) {
            $surfaces.Add([pscustomobject]@{ id = $id; kind = 'activity'; source = 'phone/src/main/AndroidManifest.xml' })
        }
    }

    $dialogPattern = '(?m)^\s*(?:(?:open|abstract|internal|private)\s+)*class\s+(\w+)[^{\n]*?:\s*(?:DialogFragment|BottomSheetDialogFragment|AppCompatDialogFragment)\b'
    $classTexts = [System.Collections.Generic.List[string]]::new()
    foreach ($file in Get-ChildItem -LiteralPath $javaRoot -Recurse -Filter '*.kt' -File) {
        $text = [IO.File]::ReadAllText($file.FullName)
        foreach ($m in [regex]::Matches($text, $dialogPattern)) {
            $id = $m.Groups[1].Value
            if ($text -match "abstract\s+class\s+$id\b") { continue }
            $classTexts.Add($text)
            if ($seen.Add($id)) {
                $rel = [IO.Path]::GetRelativePath($Root, $file.FullName).Replace('\', '/')
                $surfaces.Add([pscustomobject]@{ id = $id; kind = 'dialog'; source = $rel })
            }
        }
    }

    if (Test-Path -LiteralPath $resRoot -PathType Container) {
        $layoutFiles = Get-ChildItem -LiteralPath $resRoot -Directory -Filter 'layout*' |
            ForEach-Object { Get-ChildItem -LiteralPath $_.FullName -Filter '*.xml' -File } |
            Where-Object { $_.BaseName -match '^(dialog|fragment)_' } | Sort-Object BaseName
        foreach ($layout in $layoutFiles) {
            $name = $layout.BaseName
            if ($seen.Contains($name)) { continue }
            $binding = ConvertTo-BindingName $name
            $covered = $false
            foreach ($text in $classTexts) {
                if ($text.Contains($binding) -or $text.Contains("R.layout.$name")) { $covered = $true; break }
            }
            if ($covered) { continue }
            $seen.Add($name) | Out-Null
            $rel = [IO.Path]::GetRelativePath($Root, $layout.FullName).Replace('\', '/')
            $surfaces.Add([pscustomobject]@{ id = $name; kind = 'layout'; source = $rel })
        }
    }

    return $surfaces | Sort-Object id
}

function Get-WearHelpSurfaces {
    param([Parameter(Mandatory)][string]$Root)

    $javaRoot = Join-Path $Root 'wear/src/main/java'
    if (-not (Test-Path -LiteralPath $javaRoot -PathType Container)) {
        throw "wear help surfaces: source not found under $Root"
    }

    # A destination is `composable(` then an optional `route =` then a route reference or a literal; the
    # call is often split over lines, so the gap is whitespace of any kind.
    $routePattern = '\bcomposable\(\s*(?:route\s*=\s*)?(?:(\w+Routes\.\w+)|"([^"]+)")'
    $surfaces = [System.Collections.Generic.List[object]]::new()
    $seen = [System.Collections.Generic.HashSet[string]]::new()

    foreach ($file in Get-ChildItem -LiteralPath $javaRoot -Recurse -Filter '*.kt' -File) {
        $text = [IO.File]::ReadAllText($file.FullName)
        foreach ($m in [regex]::Matches($text, $routePattern)) {
            $route = if ($m.Groups[1].Success) { $m.Groups[1].Value } else { $m.Groups[2].Value }
            $id = "wear:$route"
            if ($seen.Add($id)) {
                $rel = [IO.Path]::GetRelativePath($Root, $file.FullName).Replace('\', '/')
                $surfaces.Add([pscustomobject]@{ id = $id; kind = 'route'; source = $rel })
            }
        }
    }

    return $surfaces | Sort-Object id
}

if ($MyInvocation.InvocationName -ne '.') {
    if (-not $RepoRoot) { $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path }
    try {
        $list = if ($Module -eq 'wear') { Get-WearHelpSurfaces -Root $RepoRoot } else { Get-HelpSurfaces -Root $RepoRoot }
    } catch {
        [Console]::Error.WriteLine($_.Exception.Message)
        exit 2
    }
    if ($Json) { $list | ConvertTo-Json -Depth 3 } else { $list | ForEach-Object { $_.id } }
    exit 0
}
