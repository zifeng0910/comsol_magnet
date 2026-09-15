param(
  [int]$IntervalSec = 30,
  [int]$StallSeconds = 900,
  [string]$Stamp = '20260914',
  [switch]$Once,
  [switch]$NoClear
)
$ErrorActionPreference = 'SilentlyContinue'
$WorkDir = "H:\\comsolcc\\comsol_magnet\\work\\modelB_alpha_minus20_critical_$Stamp"
$PrescanCsv = 'H:\\comsolcc\\comsol_magnet\\data\\modelB_alpha_minus20_critical_phi90_prescan.csv'
$FullCsv = Join-Path $WorkDir 'modelB_alpha_minus20_critical_full360.csv'
$FullSummary = Join-Path $WorkDir 'modelB_alpha_minus20_critical_full360_summary.csv'
$PreviousCpuByPid = @{}

function Get-CriticalQueueProcesses { @(Get-CimInstance Win32_Process | Where-Object { $_.Name -match 'powershell|pwsh' -and $_.CommandLine -match 'RunModelBAlphaMinus20Critical(Prescan|Full360)\.ps1' }) }
function Get-CriticalJavaProcesses { @(Get-CimInstance Win32_Process | Where-Object { $_.Name -match '^java(\.exe)?$' -and $_.CommandLine -match 'RunModelBLocalMeshConvergence' -and $_.CommandLine -match 'alpha_minus20_critical' }) }
function Read-CsvRows([string]$Path) { if (Test-Path $Path) { @(Import-Csv $Path) } else { @() } }
function Show-CriticalState {
  if (-not $NoClear) { Clear-Host }
  $now=Get-Date; $queue=@(Get-CriticalQueueProcesses); $java=@(Get-CriticalJavaProcesses); $pre=@(Read-CsvRows $PrescanCsv); $full=@(Read-CsvRows $FullCsv); $sum=@(Read-CsvRows $FullSummary)
  $log=Get-Item (Join-Path $WorkDir 'runner.stdout.log') -ErrorAction SilentlyContinue; $logAge=$null; $csvAge=$null; if($log){$logAge=[math]::Round(($now-$log.LastWriteTime).TotalSeconds,0)}
  $activeCsv=if($full.Count -gt 0){$FullCsv}else{$PrescanCsv}; if(Test-Path $activeCsv){$csvAge=[math]::Round(($now-(Get-Item $activeCsv).LastWriteTime).TotalSeconds,0)}
  $text=''; if($log){$event=Select-String -Path $log.FullName -Pattern 'CRITICAL_PRESCAN_RESULT|CRITICAL_FULL360_RESULT|CRITICAL_B0_DONE|CRITICAL_B1_DONE|CRITICAL_PRESCAN_FINISH|CRITICAL_FULL360_FINISH|SOLVE_START|SOLVE_DONE|MESH_DONE|ERROR|FAIL' | Select-Object -Last 1; if($event){$text=$event.Line}}
  $mode=if($text -match 'CRITICAL_FULL360|critical full360'){ 'FULL360' } elseif($text -match 'CRITICAL_PRESCAN|critical prescan'){ 'PRESCAN' } else { 'WAITING' }
  $z=''; $phi=''; $stage='WAITING'; if($text -match 'z=([-+0-9.eE]+)'){ $z=$Matches[1] }; if($text -match 'phi=([-+0-9.eE]+)'){ $phi=$Matches[1] }
  if($text -match 'B0'){ $stage='B0' }; if($text -match 'B1'){ $stage='B1' }; if($text -match 'B2|CRITICAL_FULL360_RESULT|CRITICAL_PRESCAN_RESULT'){ $stage='B2' }; if($text -match 'FINISH'){ $stage='FINISHED' }; if($text -match 'ERROR|FAIL'){ $stage='ERROR' }
  $currentRows=@(); if($z -and $full.Count -gt 0){$currentRows=@($full | Where-Object {[math]::Abs(([double]$_.z_sphere_mm)-([double]$z)) -lt 1e-8})}
  $last=if($mode -eq 'FULL360'){if($full.Count -gt 0){$full[-1]}else{$null}}elseif($pre.Count -gt 0){$pre[-1]}else{$null}; $currentPeak=$currentRows | Sort-Object {[double]$_.Fx_total_corr_mN} -Descending | Select-Object -First 1
  $latestFx=if($last){$last.Fx_total_corr_mN}else{''}; $runningFmax=if($currentPeak){$currentPeak.Fx_total_corr_mN}elseif($last -and $mode -eq 'PRESCAN'){$last.Fx_total_corr_mN}else{''}; $peakPhi=if($currentPeak){$currentPeak.phi_deg}elseif($last -and $mode -eq 'PRESCAN'){$last.phi_deg}else{''}; $hold=if($currentPeak){$currentPeak.Fx_hold_corr_mN}elseif($last){$last.Fx_hold_corr_mN}else{''}
  $activeRows=if($mode -eq 'FULL360'){$full}else{$pre}; $success=@($activeRows | Where-Object {$_.status -eq 'SUCCESS'}).Count; $errors=@($activeRows | Where-Object {$_.status -ne 'SUCCESS'}).Count
  $doneZ=if($mode -eq 'FULL360'){$sum.Count}else{$pre.Count}; $totalZ=if($mode -eq 'FULL360'){4}else{9}; $currentPhase=if($mode -eq 'FULL360'){$currentRows.Count}elseif($pre.Count -gt 0){1}else{0}
  $cpuDelta=$null; foreach($jp in $java){$p=Get-Process -Id $jp.ProcessId -ErrorAction SilentlyContinue; if($p){$cpu=[double]$p.CPU;if($PreviousCpuByPid.ContainsKey($jp.ProcessId)){$cpuDelta=[math]::Round($cpu-$PreviousCpuByPid[$jp.ProcessId],1)};$PreviousCpuByPid[$jp.ProcessId]=$cpu}}
  $status='IDLE'; if($java.Count -gt 0){$status=if($logAge -ne $null -and $logAge -ge $StallSeconds){'POSSIBLE_STALL'}else{'RUNNING_NORMALLY'}} elseif(($mode -eq 'FULL360' -and $sum.Count -eq 4 -and $full.Count -eq 404) -or ($mode -eq 'PRESCAN' -and $pre.Count -eq 9)){$status='COMPLETED'} elseif($log){$status='UNEXPECTED_EXIT'}
  $queuePid=if($queue){$queue[0].ProcessId}else{'EXITED'}; $javaPid=if($java){$java[0].ProcessId}else{'EXITED'}; $phaseTotal=if($mode -eq 'FULL360'){101}else{1}
  Write-Host 'TASK: ALPHA_MINUS20_CRITICAL_ONLY' -ForegroundColor Cyan; Write-Host ("TIME: {0}   MODE: {1}   STATUS: {2}" -f $now.ToString('yyyy-MM-dd HH:mm:ss'),$mode,$status) -ForegroundColor $(if($status -eq 'RUNNING_NORMALLY'){'Green'}elseif($status -eq 'POSSIBLE_STALL'){'Yellow'}elseif($status -eq 'COMPLETED'){'Green'}else{'White'}); Write-Host ("QUEUE PID: {0}   JAVA PID: {1}" -f $queuePid,$javaPid); Write-Host ("CURRENT: z={0} mm  phi={1} deg  STAGE={2}" -f $z,$phi,$stage); Write-Host ("z completed: {0} / {1}   current z phases: {2} / {3}" -f $doneZ,$totalZ,$currentPhase,$phaseTotal); Write-Host ("total poses: {0}   SUCCESS: {1}   ERROR: {2}" -f $activeRows.Count,$success,$errors); Write-Host ("latest Fx_total_corr: {0} mN   running Fmax: {1} mN   phi_at_Fmax: {2} deg   Fx_hold_corr: {3} mN" -f $latestFx,$runningFmax,$peakPhi,$hold); Write-Host ("log_age_seconds={0}   csv_age_seconds={1}   cpu_delta_seconds={2}" -f $logAge,$csvAge,$cpuDelta)
  if($status -eq 'POSSIBLE_STALL'){Write-Host ("POSSIBLE_STALL: no log/CSV update for {0} seconds; inspect CPU/RAM only." -f $StallSeconds) -ForegroundColor Yellow}
}
do { Show-CriticalState; if(-not $Once){Start-Sleep -Seconds $IntervalSec} } while(-not $Once)
