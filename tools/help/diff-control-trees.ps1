#requires -Version 7.0
<#
.SYNOPSIS
    The before / after control diff of CONTEXT-HELP rule 2: the help button must be the only addition to a surface.

.DESCRIPTION
    Compares the control trees that capture-help-screenshots.ps1 -TreeDir wrote for a build without the help
    actions (-Before) and for the current build (-After), one .tsv per surface (id, class, content description,
    text of every node of the UI automation tree). A control is a line; a line present only in -After is an
    addition, a line present only in -Before is a removal. Nodes that carry a clock, a battery level or a
    changing count are ignored (the status bar, the listener counter).
    The verdict is `PASS` when every addition of every surface is a help control (an id or a description that is
    the vocabulary name of `app.help`) and there is no removal, `FAIL` otherwise.

.PARAMETER Before
    Folder with the trees of the build before the help actions.

.PARAMETER After
    Folder with the trees of the current build.

.PARAMETER Allow
    Extra lines (`id|class|desc|text`) that are a known, intended change, separated by `;`; each is named in the output.

.EXAMPLE
    pwsh -NoProfile -File tools/help/diff-control-trees.ps1 -Before temp/S0035/trees-before -After temp/S0035/trees-after

.NOTES
    Exit codes (CHECK-VERDICT): 0 only help controls were added; 1 another addition or a removal; 2 an input
    folder or a pair of files is missing. The last line on stdout is the verdict line.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Before,
    [Parameter(Mandatory)][string]$After,
    [string[]]$Allow = @()
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
# A process started with -File does not split a comma list, so several entries are also accepted joined by ;.
$Allow = @($Allow | ForEach-Object { $_ -split ';' } | Where-Object { $_ })

foreach ($dir in @($Before, $After)) {
    if (-not (Test-Path -LiteralPath $dir -PathType Container)) {
        Write-Host "diff-control-trees: COULD NOT VERIFY (folder not found: $dir)"
        exit 2
    }
}
$ignore = '^(statusBarBackground|navigationBarBackground|clock|battery|wifi_combo|status_bar|notification_icon_area|system_icons|statusIcons|date|carrier|operator_name|signal_cluster|mobile_signal|battery_meter|battery_percentage|tvListenerCount)'
$helpNames = @('Help', 'Справка', 'Довідка')
$bad = [System.Collections.Generic.List[string]]::new()
$summary = [System.Collections.Generic.List[string]]::new()
$files = Get-ChildItem -LiteralPath $After -Filter '*.tsv' -File | Sort-Object Name
if ($files.Count -eq 0) {
    Write-Host "diff-control-trees: COULD NOT VERIFY (no trees in $After)"
    exit 2
}
foreach ($file in $files) {
    $old = Join-Path $Before $file.Name
    if (-not (Test-Path -LiteralPath $old -PathType Leaf)) {
        Write-Host "diff-control-trees: COULD NOT VERIFY (no before tree for $($file.Name))"
        exit 2
    }
    $read = {
        param($path)
        @(Get-Content -LiteralPath $path -Encoding UTF8 | Where-Object { $_ -and ($_ -split "`t")[0] -notmatch $ignore })
    }
    $b = & $read $old
    $a = & $read $file.FullName
    $diff = Compare-Object -ReferenceObject $b -DifferenceObject $a
    foreach ($d in @($diff)) {
        $cells = $d.InputObject -split "`t"
        $id = $cells[0]; $desc = if ($cells.Count -gt 2) { $cells[2] } else { '' }; if ($cells.Count -lt 4) { $cells = @($cells) + @('') * (4 - $cells.Count) }
        $line = '{0} {1}: {2} [{3}] "{4}"' -f $(if ($d.SideIndicator -eq '=>') { '+' } else { '-' }), $file.BaseName, $(if ($id) { $id } else { '(no id)' }), $cells[1], $desc
        # A layout container with no id, no description and no text is structure, not a control.
        $structural = (-not $id) -and (-not $desc) -and (-not $cells[3]) -and ($cells[1] -match '(FrameLayout|LinearLayout|LinearLayoutCompat|RelativeLayout|ConstraintLayout|ViewGroup)$')
        $isHelp = ($d.SideIndicator -eq '=>') -and (($id -match 'help' -and $id -match '(?i)help') -or ($helpNames -contains $desc))
        $key = ($d.InputObject -replace "`t", '|').TrimEnd('|')
        $allowed = @($Allow | ForEach-Object { $_.TrimEnd('|') }) -contains $key
        if ($isHelp) { $summary.Add("$line   (help control)") }
        elseif ($structural) { $summary.Add("$line   (structure)") }
        elseif ($allowed) { $summary.Add("$line   (allowed)") }
        else { $summary.Add($line); $bad.Add($line) }
    }
    if (@($diff).Count -eq 0) { $summary.Add("= $($file.BaseName): identical") }
}
$summary | ForEach-Object { Write-Host $_ }
if ($bad.Count -eq 0) {
    Write-Host "diff-control-trees: PASS ($($files.Count) surface(s), only help controls added)"
    exit 0
}
Write-Host "diff-control-trees: FAIL ($($bad.Count) change(s) other than a help control)"
exit 1
