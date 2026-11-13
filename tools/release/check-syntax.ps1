#requires -Version 7.0
<#
.SYNOPSIS
  Parse a release PowerShell script without running the release.
.DESCRIPTION
  Exit 0 when the subject parses, 1 for syntax errors, 2 when it cannot be read.
#>
[CmdletBinding(PositionalBinding = $false)]
param([string] $Path = (Join-Path $PSScriptRoot 'pre-release-sweep.ps1'))

$ErrorActionPreference = 'Stop'
try {
    $subject = (Resolve-Path -LiteralPath $Path).Path
    $tokens = $null
    $parseErrors = $null
    [void][System.Management.Automation.Language.Parser]::ParseFile($subject, [ref] $tokens, [ref] $parseErrors)
    if ($parseErrors.Count -gt 0) {
        foreach ($parseError in $parseErrors) { Write-Host $parseError.Message }
        Write-Host "release-syntax: FAIL ($subject)"
        exit 1
    }
    Write-Host "release-syntax: PASS ($subject)"
    exit 0
} catch {
    Write-Host "release-syntax: COULD NOT VERIFY ($Path; $($_.Exception.Message))"
    exit 2
}
