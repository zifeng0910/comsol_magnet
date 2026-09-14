param(
  [int]$IntervalSec = 30,
  [int]$StallSeconds = 900,
  [switch]$Once,
  [switch]$NoClear
)

$ErrorActionPreference = 'SilentlyContinue'
$WorkRoot = 'H:\comsolcc\comsol_magnet\work'
$WorkDir = Join-Path $WorkRoot 'modelB_alpha_minus20_comprehensive_20260914'
$PhiCsv = 'H:\comsolcc\comsol_magnet\data\modelB_alpha_minus20_macro_phi.csv'
$SummaryCsv = 'H:\comsolcc\comsol_magnet\data\modelB_alpha_minus20_macro_Fmax_summary.csv'
$ExpectedZ = @(40,50,60,70,80,90,100,110,120,130,140,150,160)
$ExpectedPhi = @(0,30,60,90,120,150,180,210,240,270,300,330,360)
$PreviousCpu = @{}

function Get-Queue {
  @(Get-CimInstance Win32_Process | Where-Object {
    $_.Name -match 'powershell|pwsh' -and $_.CommandLine -match 'RunModelBAlphaMinus20Comprehensive\.ps1'
  })
}

function Get-Runner {
  @(Get-CimInstance Win32_Process | Where-Object {
    $_.Name -match '^java(\.exe)?$' -and $_.CommandLine -match 'RunModelBLocalMeshConvergence' -and $_.CommandLine -match 'modelB_alpha_minus20_comprehensive_20260914'
  })
}

function Read-Rows {
  if (Test-Path $PhiCsv) { @(Import-Csv $PhiCsv) } else { @() }
}

function Show-State {
  if (-not $NoClear) { Clear-Host }
  $now = Get-Date
  $queue = @(Get-Queue)
  $runner = @(Get-Runner)
  $rows = @(Read-Rows)
  $summaries = if (Test-Path $SummaryCsv) { @(Import-Csv $SummaryCsv) } else { @() }
  $success = @($rows | Where-Object { $_.status -eq 'SUCCESS' })
  $errors = @($rows | Where-Object { $_.status -ne 'SUCCESS' })
  $badAlpha = @($rows | Where-Object { [double]$_.alpha_deg -ne -20.0 })

  $latestLog = Get-ChildItem -LiteralPath $WorkDir -Recurse -Filter 'runner.stdout.log' -File | Sort-Object LastWriteTime -Descending | Select-Object -First 1
  $latestLine = $null
  $currentZ = ''
  $currentPhi = ''
  $stage = 'WAITING'
  $logAge = $null
  if ($latestLog) {
    $logAge = [math]::Round(((Get-Date) - $latestLog.LastWriteTime).TotalSeconds, 0)
    if ($latestLog.Directory.Name -match '^z(.+)$') { $currentZ = $Matches[1] }
    $events = @(Select-String -Path $latestLog.FullName -Pattern 'MACRO_RESULT|MACRO_FINISH|SOLVE_START|SOLVE_DONE|MESH_DONE|ERROR|FAIL' | Select-Object -Last 1)
    if ($events.Count -gt 0) {
      $latestLine = $events[0].Line
      if ($latestLine -match 'MACRO_RESULT') { $stage = 'B2' }
      elseif ($latestLine -match 'MACRO_FINISH') { $stage = 'FINISHED' }
      elseif ($latestLine -match 'SOLVE_START B0') { $stage = 'B0' }
      elseif ($latestLine -match 'SOLVE_START B1') { $stage = 'B1' }
      elseif ($latestLine -match 'SOLVE_START B2') { $stage = 'B2' }
      elseif ($latestLine -match 'MESH_DONE') { $stage = 'MESH' }
      elseif ($latestLine -match 'ERROR|FAIL') { $stage = 'ERROR' }
    }
    if ($latestLine -and $latestLine -match 'z=([-+0-9.eE]+).*?phi=([-+0-9.eE]+)') { $currentZ = $Matches[1]; $currentPhi = $Matches[2] }
  }
  $csvAge = $null
  if (Test-Path $PhiCsv) { $csvAge = [math]::Round(((Get-Date) - (Get-Item $PhiCsv).LastWriteTime).TotalSeconds, 0) }

  $cpuText = 'n/a'
  $ramText = 'n/a'
  $cpuDelta = $null
  foreach ($rp in $runner) {
    $p = Get-Process -Id $rp.ProcessId
    if ($p) {
      $cpuNow = [double]$p.CPU
      if ($PreviousCpu.ContainsKey($rp.ProcessId)) { $cpuDelta = [math]::Round($cpuNow - $PreviousCpu[$rp.ProcessId], 1) }
      $PreviousCpu[$rp.ProcessId] = $cpuNow
      $cpuText = ('PID={0} CPU_s={1:N1}' -f $rp.ProcessId,$cpuNow)
      $ramText = ('RAM_GB={0:N2}' -f ($p.WorkingSet64/1GB))
    }
  }

  $zDone = $summaries.Count
  $currentRows = @($rows | Where-Object { $currentZ -and [math]::Abs(([double]$_.z_sphere_mm)-([double]$currentZ)) -lt 1e-7 })
  $last = if ($rows.Count -gt 0) { $rows[-1] } else { $null }
  $currentPeak = $currentRows | Sort-Object { [double]$_.Fx_total_corr_mN } -Descending | Select-Object -First 1
  $lastFx = if ($last) { $last.Fx_total_corr_mN } else { '' }
  $peakFx = if ($currentPeak) { $currentPeak.Fx_total_corr_mN } else { '' }
  $peakPhi = if ($currentPeak) { $currentPeak.phi_deg } else { '' }
  $hold = if ($currentPeak) { $currentPeak.Fx_hold_corr_mN } elseif ($last) { $last.Fx_hold_corr_mN } else { '' }

  $status = 'UNEXPECTED_EXIT'
  if ($summaries.Count -eq $ExpectedZ.Count -and $success.Count -eq ($ExpectedZ.Count*$ExpectedPhi.Count) -and $errors.Count -eq 0) { $status = 'COMPLETED' }
  elseif ($runner.Count -gt 0) {
    if (($logAge -ne $null -and $logAge -ge $StallSeconds) -or ($csvAge -ne $null -and $csvAge -ge $StallSeconds)) { $status = 'POSSIBLE_STALL' }
    else { $status = 'RUNNING_NORMALLY' }
  }

  Write-Host 'TASK: ALPHA_MINUS20_ONLY' -ForegroundColor Cyan
  Write-Host 'EXPECTED_ALPHA: -20 deg' -ForegroundColor Cyan
  Write-Host ("TIME: {0}" -f $now.ToString('yyyy-MM-dd HH:mm:ss'))
  Write-Host ("STATUS: {0}" -f $status) -ForegroundColor $(if($status -eq 'RUNNING_NORMALLY'){'Green'}elseif($status -eq 'POSSIBLE_STALL'){'Yellow'}elseif($status -eq 'COMPLETED'){'Green'}else{'Red'})
  if ($queue.Count -gt 0) { Write-Host ("QUEUE: PID={0} RUNNING" -f $queue[0].ProcessId) -ForegroundColor Green } else { Write-Host 'QUEUE: EXITED' -ForegroundColor Yellow }
  if ($runner.Count -gt 0) { Write-Host ("COMSOL JAVA: {0} {1}" -f $cpuText,$ramText) -ForegroundColor Green } else { Write-Host 'COMSOL JAVA: EXITED' -ForegroundColor Yellow }
  Write-Host ("CURRENT: z={0} mm  phi={1} deg  STAGE={2}" -f $currentZ,$currentPhi,$stage)
  Write-Host ("Macro z completed: {0} / {1}" -f $zDone,$ExpectedZ.Count)
  Write-Host ("Phi completed current z: {0} / {1}" -f $currentRows.Count,$ExpectedPhi.Count)
  Write-Host ("Total pose completed: {0} / {1}" -f $success.Count,($ExpectedZ.Count*$ExpectedPhi.Count))
  Write-Host ("SUCCESS: {0}   ERROR: {1}" -f $success.Count,$errors.Count)
  Write-Host ("Latest Fx_total_corr: {0} mN" -f $lastFx)
  Write-Host ("Current running Fmax: {0} mN  phi_at_Fmax: {1} deg" -f $peakFx,$peakPhi)
  Write-Host ("Fx_hold_corr: {0} mN" -f $hold)
  Write-Host ("log_age_seconds={0}  csv_age_seconds={1}  cpu_delta_seconds={2}" -f $logAge,$csvAge,$cpuDelta)
  if ($badAlpha.Count -gt 0) {
    $found = ($badAlpha | Select-Object -First 1).alpha_deg
    Write-Host ("ERROR: WRONG ALPHA  EXPECTED -20 DEG  FOUND {0}" -f $found) -ForegroundColor Red
  }
  if ($status -eq 'POSSIBLE_STALL') { Write-Host ("POSSIBLE_STALL: no log/CSV update for {0} seconds; inspect CPU before intervening." -f $StallSeconds) -ForegroundColor Yellow }
}

do {
  Show-State
  if (-not $Once) { Start-Sleep -Seconds $IntervalSec }
} while (-not $Once)
