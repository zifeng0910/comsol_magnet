param(
  [int]$IntervalSec = 30,
  [int]$StallSeconds = 900,
  [string]$Stamp = '20260915',
  [switch]$Once,
  [switch]$NoClear
)
$ErrorActionPreference = 'SilentlyContinue'
$Root = 'H:\comsolcc\comsol_magnet'
$Work = Join-Path $Root "work\modelB_alpha_minus20_force_levels_$Stamp"
$QueueLog = Join-Path $Work 'force_levels_queue.log'
$PrescanCsv = Join-Path $Work 'force_level_prescan_new.csv'
$FullCsv = Join-Path $Work 'modelB_alpha_minus20_force_levels_full360.csv'
$FullSummary = Join-Path $Work 'modelB_alpha_minus20_force_levels_full360_summary.csv'
$QueuePid = $null
$JavaPid = $null
$PreviousCpu = @{}
$PlannedScanZ = 19

function Read-Rows([string]$Path) {
  if (Test-Path -LiteralPath $Path) { @(Import-Csv -LiteralPath $Path) } else { @() }
}

function Show-ForceLevelState {
  if (-not $NoClear) { Clear-Host }
  $now = Get-Date
  $queueProcess = @(Get-CimInstance Win32_Process | Where-Object {
    $_.Name -match '^(powershell|pwsh)(\.exe)?$' -and $_.CommandLine -match 'RunModelBAlphaMinus20ForceLevels\.ps1'
  }) | Select-Object -First 1
  $javaProcesses = @(Get-CimInstance Win32_Process | Where-Object {
    $_.Name -match '^java(\.exe)?$' -and $_.CommandLine -match 'RunModelBLocalMeshConvergence' -and $_.CommandLine -match 'modelB_alpha_minus20_force_levels'
  })
  $queueLines = if (Test-Path -LiteralPath $QueueLog) { @(Get-Content -LiteralPath $QueueLog) } else { @() }
  $queueText = $queueLines -join "`n"
  $queueStart = $queueLines | Select-String -Pattern 'QUEUE_START queue_pid=(\d+)' | Select-Object -Last 1
  $QueuePid = if ($queueProcess) { [int]$queueProcess.ProcessId } elseif ($queueStart -and $queueStart.Matches.Count) { [int]$queueStart.Matches[0].Groups[1].Value } else { $null }
  $javaStart = $queueLines | Select-String -Pattern 'JAVA_START pid=(\d+)' | Select-Object -Last 1
  $JavaPid = if ($javaStart -and $javaStart.Matches.Count) { [int]$javaStart.Matches[0].Groups[1].Value } else { $null }
  $stageMatch = $queueLines | Select-String -Pattern 'STAGE=(PRESCAN|FULL360)' | Select-Object -Last 1
  $mode = if ($queueText -match 'TASK_COMPLETED') { 'COMPLETED' } elseif ($stageMatch -and $stageMatch.Matches.Count) { $stageMatch.Matches[0].Groups[1].Value } else { 'PRESCAN' }
  $activeLog = if ($mode -eq 'FULL360') { Join-Path $Work 'full360.stdout.log' } else { Join-Path $Work 'prescan.stdout.log' }
  $activeCsv = if ($mode -eq 'FULL360') { $FullCsv } else { $PrescanCsv }
  $logItem = Get-Item -LiteralPath $activeLog
  $csvItem = Get-Item -LiteralPath $activeCsv
  $logAge = if ($logItem) { [math]::Round(($now - $logItem.LastWriteTime).TotalSeconds, 0) } else { $null }
  $csvAge = if ($csvItem) { [math]::Round(($now - $csvItem.LastWriteTime).TotalSeconds, 0) } else { $null }
  $events = if ($logItem) {
    Select-String -LiteralPath $activeLog -Pattern 'FORCE_LEVEL_PRESCAN_START|FORCE_LEVEL_PRESCAN_RESULT|B0 force-level|B1 force-level|FORCE_LEVEL_FULL360_START|FORCE_LEVEL_FULL360_RESULT|SOLVE_START|MESH_DONE|ERROR|FAIL|BATCH_FINISH' | Select-Object -Last 1
  } else { $null }
  $eventLine = if ($events) { $events.Line } else { '' }
  if ($mode -eq 'COMPLETED') { $eventLine = 'TASK_COMPLETED' }
  $stage = 'WAITING'; $z = ''; $phi = ''
  if ($eventLine -match 'B0') { $stage = 'B0' }
  if ($eventLine -match 'B1') { $stage = 'B1' }
  if ($eventLine -match 'B2|FORCE_LEVEL_.*RESULT|SOLVE_START') { $stage = 'B2' }
  if ($eventLine -match 'MESH_DONE') { $stage = 'MESH' }
  if ($eventLine -match 'ERROR|FAIL') { $stage = 'ERROR' }
  if ($eventLine -match '(?:^|\s)z=([-+0-9.eE]+)') { $z = $Matches[1] }
  if ($eventLine -match 'phi=([-+0-9.eE]+)') { $phi = $Matches[1] }

  $prescan = @(Read-Rows $PrescanCsv)
  $full = @(Read-Rows $FullCsv)
  $summaries = @(Read-Rows $FullSummary)
  $latest = if ($mode -eq 'FULL360' -and $full.Count) { $full[-1] } elseif ($prescan.Count) { $prescan[-1] } else { $null }
  $currentRows = if ($z -and $mode -eq 'FULL360') { @($full | Where-Object { [math]::Abs(([double]$_.z_sphere_mm)-([double]$z)) -lt 1e-8 }) } else { @() }
  $currentPeak = if ($currentRows.Count) { $currentRows | Sort-Object { [double]$_.Fx_total_corr_mN } -Descending | Select-Object -First 1 } else { $null }
  $latestFx = if ($latest) { $latest.Fx_total_corr_mN } else { '' }
  $runningMax = if ($currentPeak) { $currentPeak.Fx_total_corr_mN } elseif ($latest) { $latest.Fx_total_corr_mN } else { '' }
  $peakPhi = if ($currentPeak) { $currentPeak.phi_deg } elseif ($latest -and $mode -eq 'PRESCAN') { $latest.phi_deg } else { '' }
  $hold = if ($currentPeak) { $currentPeak.Fx_hold_corr_mN } elseif ($latest) { $latest.Fx_hold_corr_mN } else { '' }
  $phaseDone = if ($mode -eq 'FULL360') { $currentRows.Count } elseif ($latest) { 1 } else { 0 }
  $stageAComplete = [math]::Min($prescan.Count, $PlannedScanZ)
  $stageBComplete = [math]::Min($summaries.Count, 3)

  $cpu = $null; $ram = $null; $cpuDelta = $null
  $java = $javaProcesses | Where-Object { -not $JavaPid -or $_.ProcessId -eq $JavaPid } | Select-Object -First 1
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
  if ($QueuePid) { $queueAlive = [bool](Get-Process -Id $QueuePid) }
  $javaAlive = [bool]$java
  if ($mode -eq 'COMPLETED') { $status = 'COMPLETED' }
  elseif ($javaAlive -and (($logAge -ne $null -and $logAge -ge $StallSeconds) -or ($csvAge -ne $null -and $csvAge -ge $StallSeconds))) { $status = 'POSSIBLE_STALL' }
  elseif ($javaAlive -or $queueAlive) { $status = 'RUNNING_NORMALLY' }
  elseif ($queueText -match 'TASK_COMPLETED') { $status = 'COMPLETED' }
  elseif ($queueText) { $status = 'UNEXPECTED_EXIT' }
  else { $status = 'IDLE' }

  $queueDisplay = if ($QueuePid) { $QueuePid } else { 'not found' }
  $javaDisplay = if ($java) { $java.ProcessId } elseif ($JavaPid) { "$JavaPid (exited)" } else { 'not started' }
  $modeDisplay = if ($mode -eq 'COMPLETED') { 'FULL360 / FINALIZING' } else { $mode }
  Write-Host 'TASK: ALPHA_MINUS20_FORCE_LEVEL_SCAN' -ForegroundColor Cyan
  Write-Host 'EXPECTED_ALPHA: -20 deg'
  Write-Host ("MODE: {0}    STATUS: {1}" -f $modeDisplay,$status) -ForegroundColor $(if($status -eq 'RUNNING_NORMALLY'){'Green'}elseif($status -eq 'POSSIBLE_STALL'){'Yellow'}elseif($status -eq 'COMPLETED'){'Green'}else{'Red'})
  Write-Host ("CURRENT: z={0} mm   phi={1} deg   stage={2}" -f $z,$phi,$stage)
  Write-Host ("queue PID={0}   Java PID={1}   CPU_s={2}   RAM_GB={3}" -f $queueDisplay,$javaDisplay,$cpu,$ram)
  Write-Host ("Stage A: new z {0}/{1}   reused coarse heights=5" -f $stageAComplete,$PlannedScanZ)
  Write-Host ("Stage B: completed selected z {0}/3   current full360 phases {1}/101" -f $stageBComplete,$phaseDone)
  Write-Host ("Latest Fx_total_corr={0} mN   Current Fmax={1} mN   phi_at_Fmax={2} deg   Fx_hold_corr={3} mN" -f $latestFx,$runningMax,$peakPhi,$hold)
  Write-Host ("log_age_seconds={0}   csv_age_seconds={1}   cpu_delta_seconds={2}" -f $logAge,$csvAge,$cpuDelta)
  if ($status -eq 'POSSIBLE_STALL') { Write-Host ("POSSIBLE_STALL: no log or CSV update for {0} seconds; monitor only, no process action taken." -f $StallSeconds) -ForegroundColor Yellow }
  if ($status -eq 'UNEXPECTED_EXIT') { Write-Host 'UNEXPECTED_EXIT: queue and Java are absent before completion.' -ForegroundColor Red }
}

do { Show-ForceLevelState; if (-not $Once) { Start-Sleep -Seconds $IntervalSec } } while (-not $Once)
