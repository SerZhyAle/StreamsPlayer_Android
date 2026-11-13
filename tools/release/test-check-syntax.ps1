#requires -Version 7.0
<#
.SYNOPSIS
  Exercise all three outcomes of the release syntax check on disposable inputs.
.DESCRIPTION
  Exit 0 when all three cases hold, 1 on a mismatch, 2 when fixtures cannot be prepared.
#>
[CmdletBinding(PositionalBinding = $false)]
param()
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$work = Join-Path $root ('temp/scratch/release-syntax-' + [guid]::NewGuid().ToString('N'))
$check = Join-Path $PSScriptRoot 'check-syntax.ps1'
try {
    New-Item -ItemType Directory -Path $work -Force | Out-Null
    [IO.File]::WriteAllText((Join-Path $work 'valid.ps1'), 'Write-Output 1')
    [IO.File]::WriteAllText((Join-Path $work 'invalid.ps1'), 'function {')
} catch {
    Write-Host "release-syntax-selftest: COULD NOT VERIFY ($($_.Exception.Message))"
    exit 2
}
try {
    $cases = @(
        @{ File = 'valid.ps1'; Code = 0; Verdict = 'release-syntax: PASS ' },
        @{ File = 'invalid.ps1'; Code = 1; Verdict = 'release-syntax: FAIL ' },
        @{ File = 'missing.ps1'; Code = 2; Verdict = 'release-syntax: COULD NOT VERIFY ' }
    )
    foreach ($case in $cases) {
        $result = & pwsh -NoProfile -File $check -Path (Join-Path $work $case.File) 2>&1 | Out-String
        $code = $LASTEXITCODE
        if ($code -ne $case.Code -or -not $result.Contains($case.Verdict)) {
            Write-Host "release-syntax-selftest: FAIL ($($case.File), exit $code; $result)"
            exit 1
        }
    }
    Write-Host 'release-syntax-selftest: PASS (3 cases)'
    exit 0
} finally {
    foreach ($leaf in @('valid.ps1', 'invalid.ps1')) {
        $fixture = Join-Path $work $leaf
        if (Test-Path -LiteralPath $fixture) { Remove-Item -LiteralPath $fixture -Force }
    }
    Remove-Item -LiteralPath $work -Force
}
