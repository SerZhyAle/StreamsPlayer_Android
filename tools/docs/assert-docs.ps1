#requires -Version 7.0
<#
.SYNOPSIS
    The documentation gates that need only the tree, run together (S0051): one verdict for the sweep and CI.

.DESCRIPTION
    Runs, in this order, assert-doc-registry, assert-doc-links, assert-doc-style, assert-translation-freshness,
    assert-termbase and tools/help/generate-help-pages.ps1 -Check, quotes each child's output and answers once
    (CHECK-VERDICT rule 14: any FAIL makes FAIL, else any COULD NOT VERIFY makes COULD NOT VERIFY, else PASS). Every child
    runs whatever an earlier one found (rule 9).

    Not here, each run beside it: tools/site/build-site.ps1 -Check and tools/help/assert-help-map.ps1 (their own checks of
    the sweep), tools/privacy/permissions.ps1 (needs the merged release manifests) and tools/contracts/check-sync.ps1 (needs
    the catalog, so never on a runner).

.PARAMETER RepoRoot
    Repository root. Default: two levels above this script's folder.

.NOTES
    Exit codes (CHECK-VERDICT): 0 every child passed; 1 a child failed; 2 no child failed and one could not verify.
    A child exit outside 0 to 4 is read as FAIL and 4 as 2. The last line on stdout is the verdict line.
#>
[CmdletBinding()]
param([string]$RepoRoot = '')

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_docs-lib.ps1')

$root = Resolve-DocsRoot -Given $RepoRoot -ScriptRoot $PSScriptRoot
$children = @(
    @{ Name = 'assert-doc-registry'; Path = (Join-Path $PSScriptRoot 'assert-doc-registry.ps1'); Arguments = @('-RepoRoot', $root) }
    @{ Name = 'assert-doc-links'; Path = (Join-Path $PSScriptRoot 'assert-doc-links.ps1'); Arguments = @('-RepoRoot', $root) }
    @{ Name = 'assert-doc-style'; Path = (Join-Path $PSScriptRoot 'assert-doc-style.ps1'); Arguments = @('-RepoRoot', $root) }
    @{ Name = 'assert-translation-freshness'; Path = (Join-Path $PSScriptRoot 'assert-translation-freshness.ps1'); Arguments = @('-RepoRoot', $root) }
    @{ Name = 'assert-termbase'; Path = (Join-Path $PSScriptRoot 'assert-termbase.ps1'); Arguments = @('-RepoRoot', $root) }
    @{ Name = 'generate-help-pages'; Path = (Join-Path $PSScriptRoot '../help/generate-help-pages.ps1'); Arguments = @('-RepoRoot', $root, '-Check') }
)
$failed = [Collections.Generic.List[string]]::new()
$unverified = [Collections.Generic.List[string]]::new()
foreach ($child in $children) {
    $code = 2
    try {
        & pwsh -NoProfile -File $child.Path @($child.Arguments) 2>&1 | ForEach-Object { Write-Host "$_" }
        $code = $LASTEXITCODE
    } catch {
        Write-Host "$($child.Name): COULD NOT VERIFY ($($_.Exception.Message))"
    }
    if ($code -eq 0) { continue }
    if ($code -eq 2 -or $code -eq 4) { $unverified.Add($child.Name) } else { $failed.Add($child.Name) }
}
if ($failed.Count -gt 0) {
    Write-Host "assert-docs: FAIL ($($failed.Count): $($failed -join ', '))"
    exit 1
}
if ($unverified.Count -gt 0) {
    Write-Host "assert-docs: COULD NOT VERIFY ($($unverified.Count): $($unverified -join ', '))"
    exit 2
}
Write-Host "assert-docs: PASS ($($children.Count) gates)"
exit 0
