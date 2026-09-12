param(
  [int]$ProcessId = 29148,
  [int]$RefreshSeconds = 30,
  [string]$OutDir = 'H:\\comsolcc\\comsol_magnet\\work\\modelB_x26_macro_phi_sparse_20260912'
)

$ErrorActionPreference = 'SilentlyContinue'
$logPath = Join-Path $OutDir 'runner.stdout.log'
$progressPath = Join-Path $OutDir 'progress.log'

while ($true) {
  Clear-Host
  $now = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
  $proc = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
  $rows = 0
  $success = 0
  $failed = 0
  $csvFiles = @(Get-ChildItem -LiteralPath $OutDir -Recurse -Filter 'modelB_x26_macro_phi_sparse.csv' -File -ErrorAction SilentlyContinue)
  foreach ($csvFile in $csvFiles) {
    $csvLines = @(Get-Content -LiteralPath $csvFile.FullName)
    if ($csvLines.Count -gt 1) {
      $rows += $csvLines.Count - 1
      $success += @($csvLines | Select-String ',SUCCESS$').Count
      $failed += @($csvLines | Select-String ',ERROR$').Count
    }
  }

  Write-Host "COMSOL Model B macro sparse monitor  $now"
  Write-Host ('=' * 78)
  if ($proc) {
    $cpu = [math]::Round($proc.CPU, 1)
    $ws = [math]::Round($proc.WorkingSet64 / 1GB, 2)
    Write-Host "PID=$ProcessId RUNNING   CPU_s=$cpu   RAM_GB=$ws"
  } else {
    Write-Host "PID=$ProcessId NOT RUNNING"
  }
  Write-Host "Recursive CSV rows: $rows / 54   SUCCESS: $success   ERROR: $failed"
  Write-Host "Output: $OutDir"

  if (Test-Path -LiteralPath $logPath) {
    $recent = @(Get-Content -LiteralPath $logPath -Tail 80)
    $lastStart = $recent | Where-Object { $_ -match 'MACRO_SPARSE_START|SPARSE_RESULT|SOLVE_START|SOLVE_DONE|SPARSE_FINISH' } | Select-Object -Last 1
    if ($lastStart) { Write-Host "Latest: $lastStart" }
    $lastResult = $recent | Where-Object { $_ -match 'SPARSE_RESULT|SPARSE_FINISH' } | Select-Object -Last 1
    if ($lastResult) { Write-Host "Result: $lastResult" }
  }
  if (Test-Path -LiteralPath $progressPath) {
    Write-Host '--- Solver progress tail ---'
    Get-Content -LiteralPath $progressPath -Tail 8
  }
  Write-Host "Refresh: ${RefreshSeconds}s   Press Ctrl+C to stop monitoring only"
  Start-Sleep -Seconds $RefreshSeconds
}
