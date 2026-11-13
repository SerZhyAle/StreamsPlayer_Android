$lines = Get-Content -LiteralPath 'tools/site/build-site.ps1'
$s = 0
while ($lines[$s] -ne '$tools = @(') { $s++ }
$e = $s
while ($lines[$e] -ne ')') { $e++ }
"block=$($s + 1)-$($e + 1)"
$code = ($lines[$s..$e] -join "`n") -replace '^\$tools = ', ''
$tools = Invoke-Expression $code
"count=$($tools.Count)"
"t1=$($tools[1][0])"
"u1=$($tools[1][1])"
"t7=$($tools[7][0])"
"elements=" + (($tools | ForEach-Object { $_.GetType().Name }) -join ',')
