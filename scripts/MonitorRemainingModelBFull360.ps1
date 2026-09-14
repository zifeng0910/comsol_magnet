param(
  [int]$IntervalSec = 20,
  [int]$StallMinutes = 20,
  [switch]$Once,
  [switch]$NoClear
)

$ErrorActionPreference = 'SilentlyContinue'
$WorkRoot = 'H:\comsolcc\comsol_magnet\work'
$Stamp = '20260913'
$Groups = @(
  @{ Name = 'alpha=0'; Dir = Join-Path $WorkRoot "modelB_alpha0_z100_120_full360_$Stamp"; Csv = 'modelB_alpha0_full360_phi.csv' },
  @{ Name = 'alpha=-20'; Dir = Join-Path $WorkRoot "modelB_alpha_minus20_z100_120_full360_$Stamp"; Csv = 'modelB_alpha_minus20_full360_phi.csv' }
)
$Heights = @(100,102.5,105,107.5,110,112.5,115,117.5,120)

function Get-RunnerProcesses {
  $items = @(Get-CimInstance Win32_Process | Where-Object {
    $_.Name -match '^java(\.exe)?$' -and $_.CommandLine -match 'RunModelBLocalMeshConvergence'
  })
  if ($items.Count -eq 0) {
    $items = @(Get-Process -Name java -ErrorAction SilentlyContinue | ForEach-Object {
      [pscustomobject]@{ ProcessId = $_.Id; Name = 'java.exe'; CommandLine = '' }
    })
  }
  return $items
}

function Get-QueueProcesses {
  $items = @(Get-CimInstance Win32_Process | Where-Object {
    $_.Name -match 'powershell|pwsh' -and $_.CommandLine -match 'RunRemainingModelBFull360\.ps1'
  })
  return $items
}

function Get-HeightState($group, $z) {
  $dir = Join-Path $group.Dir ("z{0}" -f $z)
  $log = Join-Path $dir 'runner.stdout.log'
  $csv = Join-Path $dir $group.Csv
  $state = [ordered]@{
    Group = $group.Name
    Z = $z
    Dir = $dir
    Log = $log
    Csv = $csv
    Rows = 0
    Finished = $false
    LastWrite = $null
    AgeMin = $null
    Event = 'not started'
    Phi = ''
    Fx = ''
    Fmax = ''
    PhiFmax = ''
    Hold = ''
    Warning = ''
  }
  if (Test-Path $csv) {
    $rows = @(Import-Csv $csv)
    $state.Rows = $rows.Count
    $state.Finished = ($rows.Count -eq 37 -and @($rows | Where-Object { $_.status -ne 'SUCCESS' }).Count -eq 0)
    if ($rows.Count -gt 0) {
      $last = $rows[-1]
      $state.Fx = $last.Fx_total_corr_mN
      $state.Phi = $last.phi_deg
      $state.Hold = $last.Fx_hold_corr_mN
      $peak = $rows | Sort-Object { [double]$_.Fx_total_corr_mN } -Descending | Select-Object -First 1
      $state.Fmax = $peak.Fx_total_corr_mN
      $state.PhiFmax = $peak.phi_deg
    }
  }
  if (Test-Path $log) {
    $item = Get-Item $log
    $state.LastWrite = $item.LastWriteTime
    $state.AgeMin = [math]::Round(((Get-Date) - $item.LastWriteTime).TotalMinutes, 1)
    $event = Select-String -Path $log -Pattern 'FULL360_FINISH|FULL360_RESULT|SOLVE_START|SOLVE_DONE|MESH_DONE|ERROR|FAIL' | Select-Object -Last 1
    if ($event) { $state.Event = ($event.Line -replace '^.*?(FULL360_FINISH|FULL360_RESULT|SOLVE_START|SOLVE_DONE|MESH_DONE|ERROR|FAIL).*','$1') }
    $result = Select-String -Path $log -Pattern 'FULL360_RESULT' | Select-Object -Last 1
    if ($result -and $result.Line -match 'phi=([-+0-9.eE]+).*?Fx_total=([-+0-9.eE]+)') {
      $state.Phi = $Matches[1]
      $state.Fx = $Matches[2]
    }
  }
  return [pscustomobject]$state
}

function Show-Monitor {
  if (-not $NoClear) { Clear-Host }
  $now = Get-Date
  $runner = @(Get-RunnerProcesses)
  $queue = @(Get-QueueProcesses)
  $states = @()
  foreach ($group in $Groups) {
    foreach ($z in $Heights) { $states += Get-HeightState $group $z }
  }
  $done = @($states | Where-Object { $_.Finished }).Count
  $total = $states.Count
  Write-Host ("Model B full360 monitor  {0}   complete={1}/{2}" -f $now.ToString('yyyy-MM-dd HH:mm:ss'),$done,$total) -ForegroundColor Cyan
  Write-Host ("runner_processes={0}  queue_processes={1}  stall_threshold={2} min" -f $runner.Count,$queue.Count,$StallMinutes)
  if ($runner.Count -gt 0) {
    foreach ($rp in $runner) {
      $p = Get-Process -Id $rp.ProcessId
      if ($p) { Write-Host ("  JAVA PID={0} CPU={1:N1}s RAM={2:N1}GB" -f $rp.ProcessId,$p.CPU,($p.WorkingSet64/1GB)) -ForegroundColor Green }
    }
  } else {
    Write-Host '  No active COMSOL Java runner found.' -ForegroundColor Yellow
  }
  if ($queue.Count -gt 0) { Write-Host '  Remaining-queue PowerShell is running.' -ForegroundColor Green }
  else { Write-Host '  Remaining-queue PowerShell is not running.' -ForegroundColor Yellow }
  Write-Host ''
  $states | Where-Object { $_.Log -and (Test-Path $_.Log) } | Format-Table Group,Z,Rows,Finished,Event,Fmax,PhiFmax,Fx,Hold,AgeMin -AutoSize
  $activeStates = @($states | Where-Object { $_.AgeMin -ne $null -and -not $_.Finished })
  $stalled = @($activeStates | Where-Object { $_.AgeMin -ge $StallMinutes })
  if ($stalled.Count -gt 0 -and $runner.Count -gt 0) {
    Write-Host ''
    Write-Host ('WARNING: log has not changed for at least {0} minutes. This can be a long PARDISO solve; inspect CPU/RAM before intervening.' -f $StallMinutes) -ForegroundColor Yellow
    $stalled | Format-Table Group,Z,Event,AgeMin,Dir -AutoSize
  }
  if ($done -eq $total) { Write-Host 'ALL 18 HEIGHTS COMPLETE.' -ForegroundColor Green }
}

do {
  Show-Monitor
  if (-not $Once) { Start-Sleep -Seconds $IntervalSec }
} while (-not $Once)
