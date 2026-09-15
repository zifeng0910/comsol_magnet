param([string]$Stamp = '20260915')
$ErrorActionPreference = 'Stop'
$Compile = 'I:\Program Files\COMSOL\COMSOL63\Multiphysics\bin\win64\comsolcompile.exe'
$Java = 'I:\Program Files\COMSOL\COMSOL63\Multiphysics\java\win64\jre\bin\java.exe'
$Plugins = 'I:\Program Files\COMSOL\COMSOL63\Multiphysics\plugins\*'
$Source = 'H:\comsolcc\z120_xsphere26_air200_mesh1.mph'
$Root = 'H:\comsolcc\comsol_magnet'
$Scripts = Join-Path $Root 'scripts'
$Work = Join-Path $Root "work\modelB_alpha_minus20_force_levels_$Stamp"
$Data = Join-Path $Root 'data'
$QueueLog = Join-Path $Work 'force_levels_queue.log'
$NewPrescan = Join-Path $Work 'force_level_prescan_new.csv'
$PrescanStdout = Join-Path $Work 'prescan.stdout.log'
$PrescanStderr = Join-Path $Work 'prescan.stderr.log'
$FullStdout = Join-Path $Work 'full360.stdout.log'
$FullStderr = Join-Path $Work 'full360.stderr.log'
$PrescanZs = @(106,104,102,98,96,94,92,88,86,84,82,78,76,74,72,68,66,64,62)
$Python = 'python'

function Add-QueueLog([string]$Line) { Add-Content -LiteralPath $QueueLog -Value ((Get-Date -Format 'yyyy-MM-dd HH:mm:ss') + ' ' + $Line) }
function Assert-NoForceLevelJava {
  $busy = @(Get-CimInstance Win32_Process | Where-Object {
    $_.Name -match '^java(\.exe)?$' -and $_.CommandLine -match 'RunModelBLocalMeshConvergence' -and $_.CommandLine -match 'comsol_magnet'
  })
  if ($busy.Count -gt 0) { throw ("COMSOL_JAVA_ALREADY_RUNNING pid=" + (($busy | ForEach-Object ProcessId) -join ',')) }
}
function Start-Comsol([string]$Mode, [string]$Stdout, [string]$Stderr, [string[]]$ZValues) {
  Assert-NoForceLevelJava
  $modeArgs = $ZValues -join ' '
  if ($Mode -eq 'alphaminus20forcelevelprescan') {
    $argLine = '-Xmx12g -cp "{0};{1}" RunModelBLocalMeshConvergence "{2}" "{3}" {4} "{5}" {6}' -f $Scripts,$Plugins,$Source,$Work,$Mode,$NewPrescan,$modeArgs
  } else {
    $argLine = '-Xmx12g -cp "{0};{1}" RunModelBLocalMeshConvergence "{2}" "{3}" {4} {5}' -f $Scripts,$Plugins,$Source,$Work,$Mode,$modeArgs
  }
  $p = Start-Process -FilePath $Java -ArgumentList $argLine -RedirectStandardOutput $Stdout -RedirectStandardError $Stderr -PassThru -WindowStyle Hidden
  Add-QueueLog ("STAGE={0} JAVA_START pid={1} args={2}" -f $(if($Mode -eq 'alphaminus20forcelevelprescan'){'PRESCAN'}else{'FULL360'}),$p.Id,$modeArgs)
  $finishPattern = if ($Mode -eq 'alphaminus20forcelevelprescan') { 'FORCE_LEVEL_PRESCAN_BATCH_FINISH' } else { 'FORCE_LEVEL_FULL360_BATCH_FINISH' }
  $finishedMarker = $false
  while (-not $p.HasExited) {
    Start-Sleep -Seconds 10
    $p.Refresh()
    if ((Test-Path -LiteralPath $Stdout) -and (Select-String -LiteralPath $Stdout -Pattern $finishPattern -Quiet)) {
      $finishedMarker = $true
      Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
      break
    }
  }
  $p.Refresh()
  Add-QueueLog ("JAVA_EXIT pid={0} exit_code={1}" -f $p.Id,$p.ExitCode)
  if (-not $finishedMarker -and $p.ExitCode -ne 0) { throw ("COMSOL_JAVA_EXIT_{0}_{1}" -f $Mode,$p.ExitCode) }
  $text = Get-Content -LiteralPath $Stdout -Raw
  if ($Mode -eq 'alphaminus20forcelevelprescan' -and $text -notmatch 'FORCE_LEVEL_PRESCAN_BATCH_FINISH') { throw 'PRESCAN_BATCH_FINISH_MARKER_MISSING' }
  if ($Mode -eq 'alphaminus20forcelevelfull360' -and $text -notmatch 'FORCE_LEVEL_FULL360_BATCH_FINISH') { throw 'FULL360_BATCH_FINISH_MARKER_MISSING' }
}

New-Item -ItemType Directory -Force -Path $Work,$Data | Out-Null
if (-not (Test-Path $QueueLog)) { Set-Content -LiteralPath $QueueLog -Value 'TASK=ALPHA_MINUS20_FORCE_LEVEL_SCAN alpha=-20 x_sphere=26 gap=0.30' -Encoding ascii }
Add-QueueLog ("QUEUE_START queue_pid={0} expected_alpha=-20" -f $PID)
Assert-NoForceLevelJava
& $Compile -classpath $Plugins (Join-Path $Scripts 'RunModelBLocalMeshConvergence.java') 2>&1 | Tee-Object (Join-Path $Work 'compile.log')
if ($LASTEXITCODE -ne 0) { throw 'COMSOL_JAVA_COMPILE_FAILED' }

# The first real point is validated and pushed together with the runner/monitor before the long queue continues.
if (-not (Test-Path $NewPrescan)) {
  Start-Comsol 'alphaminus20forcelevelprescan' $PrescanStdout $PrescanStderr @('106')
}
$newRows = @(Import-Csv -LiteralPath $NewPrescan)
if (@($newRows | Where-Object { [double]$_.z_sphere_mm -eq 106 -and [double]$_.alpha_deg -eq -20 -and [double]$_.phi_deg -eq 90 -and $_.status -eq 'SUCCESS' -and $_.same_mesh_verified -eq 'true' }).Count -ne 1) { throw 'FIRST_REAL_POINT_VALIDATION_FAILED' }
& $Python (Join-Path $Scripts 'prepare_modelB_alpha_minus20_force_levels.py') --partial
if ($LASTEXITCODE -ne 0) { throw 'PARTIAL_PRESCAN_ASSEMBLY_FAILED' }
Push-Location $Root
try {
  git add -- scripts/RunModelBLocalMeshConvergence.java scripts/RunModelBAlphaMinus20ForceLevels.ps1 scripts/MonitorModelBAlphaMinus20ForceLevels.ps1 scripts/prepare_modelB_alpha_minus20_force_levels.py scripts/analyze_modelB_alpha_minus20_force_levels.py data/modelB_alpha_minus20_force_level_prescan.csv
  git commit -m 'Start alpha minus20 force-level scan'
  if ($LASTEXITCODE -ne 0) { throw 'FIRST_POINT_COMMIT_FAILED' }
  git push origin main
  if ($LASTEXITCODE -ne 0) { throw 'FIRST_POINT_PUSH_FAILED' }
} finally { Pop-Location }
Add-QueueLog 'FIRST_POINT_PUSHED z=106 alpha=-20 phi=90 status=SUCCESS same_mesh=true'

Start-Comsol 'alphaminus20forcelevelprescan' $PrescanStdout $PrescanStderr ($PrescanZs | ForEach-Object { "$_" })
& $Python (Join-Path $Scripts 'prepare_modelB_alpha_minus20_force_levels.py')
if ($LASTEXITCODE -ne 0) { throw 'FORCE_LEVEL_SELECTION_FAILED' }
$chosen = @(Import-Csv (Join-Path $Data 'modelB_alpha_minus20_force_level_selected_targets.csv'))
if ($chosen.Count -ne 3 -or @($chosen | Select-Object -ExpandProperty selected_z_mm -Unique).Count -ne 3) { throw 'THREE_DISTINCT_TARGET_HEIGHTS_REQUIRED' }
$zs = @($chosen | ForEach-Object { '{0:g}' -f [double]$_.selected_z_mm })
Add-QueueLog ("STAGE=FULL360_SELECTED z={0}" -f ($zs -join ','))
Start-Comsol 'alphaminus20forcelevelfull360' $FullStdout $FullStderr $zs

$runFull = Join-Path $Work 'modelB_alpha_minus20_force_levels_full360.csv'
$runSummary = Join-Path $Work 'modelB_alpha_minus20_force_levels_full360_summary.csv'
if (-not (Test-Path $runFull) -or -not (Test-Path $runSummary)) { throw 'FULL360_OUTPUT_MISSING' }
$fullRows = @(Import-Csv -LiteralPath $runFull); $summaryRows = @(Import-Csv -LiteralPath $runSummary)
if ($fullRows.Count -ne 303 -or $summaryRows.Count -ne 3) { throw 'FULL360_ROW_COUNT_VALIDATION_FAILED' }
if (@($fullRows | Where-Object { $_.status -ne 'SUCCESS' -or [double]$_.alpha_deg -ne -20 -or $_.same_mesh_verified -ne 'true' }).Count -ne 0) { throw 'FULL360_STATUS_VALIDATION_FAILED' }
if (@($fullRows | Group-Object z_sphere_mm | Where-Object Count -ne 101).Count -ne 0) { throw 'FULL360_PHASE_COUNT_VALIDATION_FAILED' }
Copy-Item -LiteralPath $runFull -Destination (Join-Path $Data 'modelB_alpha_minus20_force_levels_full360.csv') -Force
Copy-Item -LiteralPath $runSummary -Destination (Join-Path $Data 'modelB_alpha_minus20_force_levels_full360_summary.csv') -Force
& $Python (Join-Path $Scripts 'analyze_modelB_alpha_minus20_force_levels.py')
if ($LASTEXITCODE -ne 0) { throw 'FORCE_LEVEL_ANALYSIS_FAILED' }
Push-Location $Root
try {
  git add -- data/modelB_alpha_minus20_force_level_prescan.csv data/modelB_alpha_minus20_force_level_selected_targets.csv data/modelB_alpha_minus20_force_levels_full360.csv data/modelB_alpha_minus20_force_levels_full360_summary.csv figures/modelB_alpha_minus20_force_level_evolution_Fx_vs_phi.png figures/modelB_alpha_minus20_force_level_evolution_Fx_vs_phi.pdf figures/modelB_alpha_minus20_force_level_Fmax_vs_z.png figures/modelB_alpha_minus20_force_level_Fmax_vs_z.pdf reports/MODELB_ALPHA_MINUS20_FORCE_LEVEL_FULL360.md
  git commit -m 'Complete alpha minus20 force-level full360 study'
  if ($LASTEXITCODE -ne 0) { throw 'FINAL_COMMIT_FAILED' }
  git push origin main
  if ($LASTEXITCODE -ne 0) { throw 'FINAL_PUSH_FAILED' }
} finally { Pop-Location }
Add-QueueLog ("TASK_COMPLETED full_rows={0} selected_z={1}" -f $fullRows.Count,($zs -join ','))
Write-Output ("ALPHA_MINUS20_FORCE_LEVEL_SCAN_COMPLETED rows={0} selected_z={1}" -f $fullRows.Count,($zs -join ','))
