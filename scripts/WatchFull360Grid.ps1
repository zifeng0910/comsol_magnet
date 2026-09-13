param(
  [int]$GridPid = 37144,
  [string]$GridLog = 'H:\comsolcc\comsol_magnet\work\full360_grid.stdout.log'
)
$ErrorActionPreference = 'Stop'
$seen = @{}
while ($true) {
  $grid = Get-Process -Id $GridPid -ErrorAction SilentlyContinue
  if (-not $grid) { break }
  if (Test-Path $GridLog) {
    $lines = Get-Content $GridLog
    foreach ($line in $lines) {
      if ($line -match 'START PID=(\d+) alpha=.*? dir=(.+)$') {
        $pid = [int]$Matches[1]
        $dir = $Matches[2].Trim()
        if ($seen.ContainsKey($pid)) { continue }
        $seen[$pid] = $dir
      }
    }
  }
  foreach ($entry in @($seen.GetEnumerator())) {
    $pid = [int]$entry.Key
    $dir = [string]$entry.Value
    $proc = Get-Process -Id $pid -ErrorAction SilentlyContinue
    if (-not $proc) { continue }
    $logs = Get-ChildItem -LiteralPath $dir -Filter 'runner.stdout.log' -File -ErrorAction SilentlyContinue
    if ($logs) {
      $tail = Get-Content -LiteralPath $logs.FullName -Tail 4 -ErrorAction SilentlyContinue
      if ($tail -match 'FULL360_FINISH') {
        Stop-Process -Id $pid -Force
        Add-Content -LiteralPath ($GridLog -replace '\.stdout\.log$','.watch.log') -Value ("STOP PID={0} FINISH dir={1}" -f $pid,$dir)
      }
    }
  }
  Start-Sleep -Seconds 15
}
