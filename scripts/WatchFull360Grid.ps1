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
        $childPid = [int]$Matches[1]
        $dir = $Matches[2].Trim()
        if ($seen.ContainsKey($childPid)) { continue }
        $seen[$childPid] = $dir
      }
    }
  }
  foreach ($entry in @($seen.GetEnumerator())) {
    $childPid = [int]$entry.Key
    $dir = [string]$entry.Value
    $proc = Get-Process -Id $childPid -ErrorAction SilentlyContinue
    if (-not $proc) { continue }
    $logs = Get-ChildItem -LiteralPath $dir -Filter 'runner.stdout.log' -File -ErrorAction SilentlyContinue
    if ($logs) {
      $tail = Get-Content -LiteralPath $logs.FullName -Tail 4 -ErrorAction SilentlyContinue
      if ($tail -match 'FULL360_FINISH') {
        Stop-Process -Id $childPid -Force
        Add-Content -LiteralPath ($GridLog -replace '\.stdout\.log$','.watch.log') -Value ("STOP PID={0} FINISH dir={1}" -f $childPid,$dir)
      }
    }
  }
  Start-Sleep -Seconds 15
}
