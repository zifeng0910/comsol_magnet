param(
  [int]$IntervalSec = 10,
  [int]$StallSeconds = 900,
  [string]$Stamp = '20260917',
  [switch]$Once,
  [switch]$NoClear
)
$ErrorActionPreference = 'SilentlyContinue'
$Root = 'H:\comsolcc\comsol_magnet'
$Work = Join-Path $Root "work\modelB_alpha_minus30_targets_$Stamp"
$QueueLog = Join-Path $Work 'alpha_minus30_queue.log'
$CoarseCsv = Join-Path $Work 'alpha_minus30_phi90_coarse.csv'
$RefineCsv = Join-Path $Work 'alpha_minus30_phi90_refine.csv'
$SelectedCsv = Join-Path $Work 'alpha_minus30_selected_targets.csv'
$FullCsv = Join-Path $Work 'modelB_alpha_minus30_target_full360.csv'
$PreviousCpu = @{}

function Get-Rows([string]$Path) {
  if (Test-Path -LiteralPath $Path) { return @(Import-Csv -LiteralPath $Path) }
  return @()
}

function Show-AlphaMinus30State {
  if (-not $NoClear) { Clear-Host }
  $now = Get-Date
  $queueProc = @(Get-CimInstance Win32_Process | Where-Object {
    $_.Name -match '^(powershell|pwsh)(\.exe)?$' -and $_.CommandLine -match 'RunModelBAlphaMinus30Targets\.ps1'
  }) | Select-Object -First 1
  $queueLines = if (Test-Path -LiteralPath $QueueLog) { @(Get-Content -LiteralPath $QueueLog) } else { @() }
  $queueText = $queueLines -join "`n"
  $queueStart = $queueLines | Select-String -Pattern '(?:QUEUE_START|RESUME_START) queue_pid=(\d+)' | Select-Object -Last 1
  $queuePid = if ($queueProc) { [int]$queueProc.ProcessId } elseif ($queueStart -and $queueStart.Matches.Count) { [int]$queueStart.Matches[0].Groups[1].Value } else { $null }
  $javaStart = $queueLines | Select-String -Pattern 'JAVA_START pid=(\d+)' | Select-Object -Last 1
  $javaPid = if ($javaStart -and $javaStart.Matches.Count) { [int]$javaStart.Matches[0].Groups[1].Value } else { $null }
  $java = @(Get-CimInstance Win32_Process | Where-Object {
    $_.Name -match '^java(\.exe)?$' -and $_.CommandLine -match 'RunModelBLocalMeshConvergence' -and $_.CommandLine -match [regex]::Escape($Work)
  }) | Select-Object -First 1

  $coarse = Get-Rows $CoarseCsv
  $refine = Get-Rows $RefineCsv
  $selected = Get-Rows $SelectedCsv
  $full = Get-Rows $FullCsv
  $success = @($full | Where-Object status -eq 'SUCCESS').Count
  $errors = @($full | Where-Object status -ne 'SUCCESS').Count
  $completedZ = @($full | Group-Object z_sphere_mm | Where-Object { @($_.Group | Where-Object status -eq 'SUCCESS').Count -eq 101 }).Count

  $stageEntry = $queueLines | Select-String -Pattern 'STAGE=([^ ]+)' | Select-Object -Last 1
  $stage = if ($stageEntry -and $stageEntry.Matches.Count) { $stageEntry.Matches[0].Groups[1].Value } else { 'PREFLIGHT' }
  $activeLog = Join-Path $Work 'preflight.log'
  $activeCsv = $CoarseCsv
  if ($stage -eq 'alphaminus30targetprescan') {
    if (Test-Path (Join-Path $Work 'refine.stdout.log')) { $activeLog = Join-Path $Work 'refine.stdout.log'; $activeCsv = $RefineCsv }
    else { $activeLog = Join-Path $Work 'coarse.stdout.log'; $activeCsv = $CoarseCsv }
  } elseif ($stage -eq 'alphaminus30targetfull360') {
    $latestFullLog = Get-ChildItem -LiteralPath $Work -Filter 'full360*.stdout.log' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($latestFullLog) { $activeLog = $latestFullLog.FullName } else { $activeLog = Join-Path $Work 'full360.stdout.log' }
    $activeCsv = $FullCsv
  }
  $logItem = Get-Item -LiteralPath $activeLog
  $csvItem = Get-Item -LiteralPath $activeCsv
  $logAge = if ($logItem) { [math]::Round(($now - $logItem.LastWriteTime).TotalSeconds, 0) } else { $null }
  $csvAge = if ($csvItem) { [math]::Round(($now - $csvItem.LastWriteTime).TotalSeconds, 0) } else { $null }

  $current = ''
  $eventLine = ''
  if ($logItem) {
    $event = Select-String -LiteralPath $activeLog -Pattern 'ALPHA_MINUS30_PRESCAN_START|ALPHA_MINUS30_FULL360_START|SOLVE_START B[012]|ALPHA_MINUS30_FULL360_RESULT|ERROR|Exception' | Select-Object -Last 1
    if ($event) { $eventLine = $event.Line }
  }
  $phase = ''
  if ($eventLine -match 'z=([-+0-9.eE]+)') { $current = $Matches[1] }
  if ($eventLine -match 'phi=([-+0-9.eE]+)') { $phase = $Matches[1] }
  if ($eventLine -match 'SOLVE_START B([012])') { $solverStage = 'B' + $Matches[1] }
  elseif ($eventLine -match 'PRESCAN_START') { $solverStage = 'PHI90' }
  elseif ($eventLine -match 'FULL360_START') { $solverStage = 'MESH/B0' }
  else { $solverStage = 'IDLE' }

  $latestFx = ''; $runningFmax = ''; $phiFmax = ''
  $screenRows = @($coarse) + @($refine)
  if ($screenRows.Count) { $latestProxyFx = $screenRows[-1].Fx_total_corr_mN; $latestProxyZ = $screenRows[-1].z_sphere_mm }
  else { $latestProxyFx = ''; $latestProxyZ = '' }
  if ($current -and $full.Count) {
    $currentRows = @($full | Where-Object { [math]::Abs([double]$_.z_sphere_mm - [double]$current) -lt 1e-8 })
    if ($currentRows.Count) {
      $latestFx = $currentRows[-1].Fx_total_corr_mN
      $peak = $currentRows | Sort-Object { [double]$_.Fx_total_corr_mN } -Descending | Select-Object -First 1
      $runningFmax = $peak.Fx_total_corr_mN; $phiFmax = $peak.phi_deg
    }
  }

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
  $queueAlive = [bool]$queueProc
  if (-not $queueAlive -and $queuePid) { $queueAlive = [bool](Get-Process -Id $queuePid) }
  $javaAlive = [bool]$java
  if ($queueText -match 'TASK_COMPLETED') { $status = 'COMPLETED' }
  elseif ($queueText -match 'TASK_ERROR') { $status = 'ERROR' }
  elseif ($javaAlive -and $logAge -ne $null -and $csvAge -ne $null -and $logAge -ge $StallSeconds -and $csvAge -ge $StallSeconds) { $status = 'POSSIBLE_STALL' }
  elseif ($javaAlive -or $queueAlive) { $status = 'RUNNING_NORMALLY' }
  elseif ($queueText) { $status = 'UNEXPECTED_EXIT' }
  else { $status = 'IDLE' }

  $queueDisplay = if ($queuePid) { $queuePid } else { 'not found' }
  $javaDisplay = if ($java) { $java.ProcessId } elseif ($javaPid) { "$javaPid (exited)" } else { 'not started' }
  Write-Host 'TASK: ALPHA_MINUS30_TARGET_FULL360' -ForegroundColor Cyan
  Write-Host 'EXPECTED: alpha=-30 deg; four measured target heights; phi=0:3.6:360'
  Write-Host ("STATUS={0}  QUEUE_STAGE={1}  SOLVER_STAGE={2}  z={3}  phi={4}" -f $status,$stage,$solverStage,$current,$phase) -ForegroundColor $(if($status -eq 'RUNNING_NORMALLY' -or $status -eq 'COMPLETED'){'Green'}else{'Yellow'})
  Write-Host ("queue PID={0}  Java PID={1}  CPU_s={2}  RAM_GB={3}  cpu_delta_s={4}" -f $queueDisplay,$javaDisplay,$cpu,$ram,$cpuDelta)
  Write-Host ("prescan rows={0}/14 coarse + {1}/4 refine  selected z={2}/4" -f $coarse.Count,$refine.Count,$selected.Count)
  Write-Host ("full360={0}/404 phases  completed heights={1}/4  SUCCESS={2} ERROR={3}" -f $success,$completedZ,$success,$errors)
  Write-Host ("full360 latest Fx_total_corr={0} mN  running Fmax={1} mN @ {2} deg" -f $latestFx,$runningFmax,$phiFmax)
  Write-Host ("latest measured prescan Fx_total_corr(phi=90)={0} mN at z={1} mm" -f $latestProxyFx,$latestProxyZ)
  Write-Host ("active log age={0}s  csv age={1}s" -f $logAge,$csvAge)
  if ($status -eq 'POSSIBLE_STALL') { Write-Host ("POSSIBLE_STALL: no active log/CSV update for {0}s; monitor is read-only." -f $StallSeconds) -ForegroundColor Yellow }
  if ($status -eq 'UNEXPECTED_EXIT') { Write-Host 'UNEXPECTED_EXIT: queue and COMSOL Java are absent before TASK_COMPLETED.' -ForegroundColor Red }
}

do { Show-AlphaMinus30State; if (-not $Once) { Start-Sleep -Seconds $IntervalSec } } while (-not $Once)
