param(
  [int]$IntervalSec = 10,
  [int]$StallSeconds = 900,
  [string]$Stamp = '20260915',
  [switch]$Once,
  [switch]$NoClear
)
$ErrorActionPreference = 'SilentlyContinue'
$Root = 'H:\comsolcc\comsol_magnet'
$Work = Join-Path $Root "work\modelB_alpha_minus20_z88_full360_$Stamp"
$QueueLog = Join-Path $Work 'z88_queue.log'
$Log = Join-Path $Work 'z88_full360.stdout.log'
$Csv = Join-Path $Work 'modelB_alpha_minus20_z88_full360.csv'
$PreviousCpu = @{}

function Show-Z88State {
  if (-not $NoClear) { Clear-Host }
  $now = Get-Date
  $queueProcess = @(Get-CimInstance Win32_Process | Where-Object {
    $_.Name -match '^(powershell|pwsh)(\.exe)?$' -and $_.CommandLine -match 'RunModelBAlphaMinus20Z88Full360\.ps1'
  }) | Select-Object -First 1
  $queueLines = if (Test-Path -LiteralPath $QueueLog) { @(Get-Content -LiteralPath $QueueLog) } else { @() }
  $queueText = $queueLines -join "`n"
  $queueStart = $queueLines | Select-String -Pattern 'QUEUE_START queue_pid=(\d+)' | Select-Object -Last 1
  $queuePid = if ($queueProcess) { [int]$queueProcess.ProcessId } elseif ($queueStart -and $queueStart.Matches.Count) { [int]$queueStart.Matches[0].Groups[1].Value } else { $null }
  $javaStart = $queueLines | Select-String -Pattern 'JAVA_START pid=(\d+)' | Select-Object -Last 1
  $javaPid = if ($javaStart -and $javaStart.Matches.Count) { [int]$javaStart.Matches[0].Groups[1].Value } else { $null }
  $java = @(Get-CimInstance Win32_Process | Where-Object {
    $_.Name -match '^java(\.exe)?$' -and $_.CommandLine -match 'RunModelBLocalMeshConvergence' -and $_.CommandLine -match [regex]::Escape($Work)
  }) | Select-Object -First 1

  $logItem = Get-Item -LiteralPath $Log
  $csvItem = Get-Item -LiteralPath $Csv
  $logAge = if ($logItem) { [math]::Round(($now - $logItem.LastWriteTime).TotalSeconds, 0) } else { $null }
  $csvAge = if ($csvItem) { [math]::Round(($now - $csvItem.LastWriteTime).TotalSeconds, 0) } else { $null }
  $event = if ($logItem) {
    Select-String -LiteralPath $Log -Pattern 'Z88_FULL360_START|SOLVE_START B0 critical|SOLVE_START B1 critical|SOLVE_START B2 critical|CRITICAL_FULL360_RESULT|CRITICAL_FULL360_FINISH|Z88_FULL360_BATCH_FINISH|ERROR|Exception' | Select-Object -Last 1
  } else { $null }
  $eventLine = if ($event) { $event.Line } else { '' }
  $stage = 'WAITING'; $phi = ''
  if ($eventLine -match 'SOLVE_START B0') { $stage = 'B0' }
  elseif ($eventLine -match 'SOLVE_START B1') { $stage = 'B1' }
  elseif ($eventLine -match 'SOLVE_START B2|CRITICAL_FULL360_RESULT') { $stage = 'B2' }
  elseif ($eventLine -match 'Z88_FULL360_START') { $stage = 'MESH' }
  if ($eventLine -match 'phi=([-+0-9.eE]+)') { $phi = $Matches[1] }

  $rows = if ($csvItem) { @(Import-Csv -LiteralPath $Csv) } else { @() }
  $successCount = @($rows | Where-Object status -eq 'SUCCESS').Count
  $errorCount = @($rows | Where-Object status -ne 'SUCCESS').Count
  $latest = if ($rows.Count) { $rows[-1] } else { $null }
  $latestFx = if ($latest) { $latest.Fx_total_corr_mN } else { '' }
  $peak = if ($rows.Count) { $rows | Sort-Object { [double]$_.Fx_total_corr_mN } -Descending | Select-Object -First 1 } else { $null }
  $fmax = if ($peak) { $peak.Fx_total_corr_mN } else { '' }
  $phiAtFmax = if ($peak) { $peak.phi_deg } else { '' }

  $cpu = $null; $ram = $null; $cpuDelta = $null
  if ($java) {
    $proc = Get-Process -Id $java.ProcessId
    if ($proc) {
      $cpu = [double]$proc.CPU
      $ram = [math]::Round($proc.WorkingSet64 / 1GB, 2)
      if ($PreviousCpu.ContainsKey([int]$java.ProcessId)) { $cpuDelta = [math]::Round($cpu - $PreviousCpu[[int]$java.ProcessId], 1) }
      $PreviousCpu[[int]$java.ProcessId] = $cpu
    }
  }
  $queueAlive = $false
  if ($queueProcess) { $queueAlive = $true }
  elseif ($queuePid) { $queueAlive = [bool](Get-Process -Id $queuePid) }
  $javaAlive = [bool]$java
  if ($queueText -match 'TASK_COMPLETED') { $status = 'COMPLETED' }
  elseif ($queueText -match 'TASK_ERROR') { $status = 'ERROR' }
  elseif ($javaAlive -and $logAge -ne $null -and $csvAge -ne $null -and $logAge -ge $StallSeconds -and $csvAge -ge $StallSeconds) { $status = 'POSSIBLE_STALL' }
  elseif ($javaAlive -or $queueAlive) { $status = 'RUNNING_NORMALLY' }
  elseif ($queueText) { $status = 'UNEXPECTED_EXIT' }
  else { $status = 'IDLE' }

  $queueDisplay = if ($queuePid) { $queuePid } else { 'not found' }
  $javaDisplay = if ($java) { $java.ProcessId } elseif ($javaPid) { "$javaPid (exited)" } else { 'not started' }
  $phase = if ($rows.Count -gt 101) { '101+' } else { $rows.Count }
  Write-Host 'TASK: ALPHA_MINUS20_Z88_FULL360' -ForegroundColor Cyan
  Write-Host 'EXPECTED: alpha=-20 deg, z=88 mm, phi=0:3.6:360'
  Write-Host ("STATUS: {0}   STAGE: {1}   CURRENT phi={2} deg" -f $status,$stage,$phi) -ForegroundColor $(if($status -eq 'RUNNING_NORMALLY'){'Green'}elseif($status -eq 'COMPLETED'){'Green'}else{'Yellow'})
  Write-Host ("queue PID={0}   Java PID={1}   CPU_s={2}   RAM_GB={3}" -f $queueDisplay,$javaDisplay,$cpu,$ram)
  Write-Host ("z=88 phases {0}/101   SUCCESS={1}   ERROR={2}" -f $phase,$successCount,$errorCount)
  Write-Host ("Latest Fx_total_corr={0} mN   Current Fmax={1} mN   phi_at_Fmax={2} deg" -f $latestFx,$fmax,$phiAtFmax)
  Write-Host ("log_age_seconds={0}   csv_age_seconds={1}   cpu_delta_seconds={2}" -f $logAge,$csvAge,$cpuDelta)
  if ($status -eq 'POSSIBLE_STALL') { Write-Host ("POSSIBLE_STALL: no log or CSV update for {0} seconds; monitor only, no process action taken." -f $StallSeconds) -ForegroundColor Yellow }
  if ($status -eq 'UNEXPECTED_EXIT') { Write-Host 'UNEXPECTED_EXIT: queue and Java are absent before completion.' -ForegroundColor Red }
}

do { Show-Z88State; if (-not $Once) { Start-Sleep -Seconds $IntervalSec } } while (-not $Once)
