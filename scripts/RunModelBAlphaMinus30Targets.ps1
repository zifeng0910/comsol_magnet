param([string]$Stamp = '20260917', [switch]$Resume)
$ErrorActionPreference = 'Stop'

$Root = 'H:\comsolcc\comsol_magnet'
$Compile = 'I:\Program Files\COMSOL\COMSOL63\Multiphysics\bin\win64\comsolcompile.exe'
$Java = 'I:\Program Files\COMSOL\COMSOL63\Multiphysics\java\win64\jre\bin\java.exe'
$Plugins = 'I:\Program Files\COMSOL\COMSOL63\Multiphysics\plugins\*'
$Source = 'H:\comsolcc\z120_xsphere26_air200_mesh1.mph'
$Scripts = Join-Path $Root 'scripts'
$Work = Join-Path $Root "work\modelB_alpha_minus30_targets_$Stamp"
$QueueLog = Join-Path $Work 'alpha_minus30_queue.log'
$Prep = Join-Path $Scripts 'prepare_modelB_alpha_minus30_targets.py'
$Python = 'python'
$CoarseCsv = Join-Path $Work 'alpha_minus30_phi90_coarse.csv'
$ProposalCsv = Join-Path $Work 'alpha_minus30_refinement_candidates.csv'
$RefineCsv = Join-Path $Work 'alpha_minus30_phi90_refine.csv'
$SelectedCsv = Join-Path $Work 'alpha_minus30_selected_targets.csv'
$FullCsv = Join-Path $Work 'modelB_alpha_minus30_target_full360.csv'
$FullSummary = Join-Path $Work 'modelB_alpha_minus30_target_full360_summary.csv'

function Add-QueueLog([string]$Line) {
  Add-Content -LiteralPath $QueueLog -Value ((Get-Date -Format 'yyyy-MM-dd HH:mm:ss') + ' ' + $Line)
}

function Assert-NoModelBJava {
  $busy = @(Get-CimInstance Win32_Process | Where-Object {
    $_.Name -match '^java(\.exe)?$' -and $_.CommandLine -match 'RunModelBLocalMeshConvergence'
  })
  if ($busy.Count -gt 0) { throw ('COMSOL_MODEL_B_JAVA_ALREADY_RUNNING pid=' + (($busy | ForEach-Object ProcessId) -join ',')) }
}

function Start-ComsolStage([string]$Mode, [string]$Stdout, [string]$Stderr, [string[]]$ModeArgs, [string]$FinishPattern) {
  Assert-NoModelBJava
  $tail = $ModeArgs -join ' '
  $argLine = '-Xmx12g -cp "{0};{1}" RunModelBLocalMeshConvergence "{2}" "{3}" {4} {5}' -f $Scripts,$Plugins,$Source,$Work,$Mode,$tail
  $proc = Start-Process -FilePath $Java -ArgumentList $argLine -RedirectStandardOutput $Stdout -RedirectStandardError $Stderr -PassThru -WindowStyle Hidden
  Add-QueueLog ("STAGE={0} JAVA_START pid={1} args={2}" -f $Mode,$proc.Id,$tail)
  $finishSeen = $false
  $finishAt = $null
  while ($true) {
    $proc.Refresh()
    if ($proc.HasExited) { break }
    if (-not $finishSeen -and (Test-Path -LiteralPath $Stdout) -and (Select-String -LiteralPath $Stdout -Pattern $FinishPattern -Quiet)) {
      $finishSeen = $true
      $finishAt = Get-Date
      Add-QueueLog ("STAGE={0} FINISH_MARKER_SEEN" -f $Mode)
    }
    if ($finishSeen -and ((Get-Date) - $finishAt).TotalSeconds -ge 90) {
      Add-QueueLog ("STAGE={0} CLEANUP_TIMEOUT stopping Java after finish marker" -f $Mode)
      Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
      break
    }
    Start-Sleep -Seconds 5
  }
  $proc.Refresh()
  Add-QueueLog ("STAGE={0} JAVA_EXIT pid={1} exit_code={2}" -f $Mode,$proc.Id,$proc.ExitCode)
  if (-not $finishSeen -and (Test-Path -LiteralPath $Stdout)) {
    $finishSeen = [bool](Select-String -LiteralPath $Stdout -Pattern $FinishPattern -Quiet)
  }
  if (-not $finishSeen) { throw ("FINISH_MARKER_MISSING mode={0}" -f $Mode) }
  if ($proc.ExitCode -ne 0 -and -not (Test-Path -LiteralPath $Stdout)) { throw ("COMSOL_JAVA_EXIT mode={0} code={1}" -f $Mode,$proc.ExitCode) }
}

try {
  foreach ($path in @($Compile,$Java,$Source,$Prep)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "REQUIRED_INPUT_MISSING: $path" }
  }
  Assert-NoModelBJava
  if ($Resume) {
    if (-not (Test-Path -LiteralPath $Work -PathType Container)) { throw "RESUME_WORK_DIRECTORY_MISSING: $Work" }
    foreach ($path in @($QueueLog,$CoarseCsv,$RefineCsv,$SelectedCsv,$FullCsv)) {
      if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "RESUME_INPUT_MISSING: $path" }
    }
    Add-QueueLog ("RESUME_START queue_pid={0} expected_alpha=-30" -f $PID)
  } else {
    if (Test-Path -LiteralPath $Work) { throw "WORK_DIRECTORY_ALREADY_EXISTS: $Work" }
    New-Item -ItemType Directory -Path $Work | Out-Null
    Set-Content -LiteralPath $QueueLog -Value 'TASK=ALPHA_MINUS30_TARGET_FULL360 alpha=-30 x=26 gap=0.30 targets=previous_alpha_minus20_selected4' -Encoding ascii
    Add-QueueLog ("QUEUE_START queue_pid={0} expected_alpha=-30" -f $PID)
    & $Python $Prep --preflight 2>&1 | Tee-Object -FilePath (Join-Path $Work 'preflight.log')
    if ($LASTEXITCODE -ne 0) { throw 'ALPHA_MINUS30_PREFLIGHT_FAILED' }
  }

  $compileLog = if ($Resume) { Join-Path $Work 'compile_resume.log' } else { Join-Path $Work 'compile.log' }
  & $Compile -classpath $Plugins (Join-Path $Scripts 'RunModelBLocalMeshConvergence.java') 2>&1 | Tee-Object -FilePath $compileLog
  if ($LASTEXITCODE -ne 0) { throw 'COMSOL_JAVA_COMPILE_FAILED' }

  if (-not $Resume) {
    $coarseZs = @(72,76,80,84,88,92,96,100,104,108,112,116,120,124)
    $coarseArgs = @($CoarseCsv) + @($coarseZs | ForEach-Object { [string]$_ })
    Start-ComsolStage 'alphaminus30targetprescan' (Join-Path $Work 'coarse.stdout.log') (Join-Path $Work 'coarse.stderr.log') $coarseArgs 'ALPHA_MINUS30_PRESCAN_BATCH_FINISH alpha=-30'
    if (-not (Test-Path -LiteralPath $CoarseCsv)) { throw 'COARSE_PRESCAN_CSV_MISSING' }

    & $Python $Prep --propose-refinement --coarse-csv $CoarseCsv --proposal-csv $ProposalCsv 2>&1 | Tee-Object -FilePath (Join-Path $Work 'proposal.log')
    if ($LASTEXITCODE -ne 0) { throw 'TARGET_BRACKETING_FAILED' }
    $proposals = @(Import-Csv -LiteralPath $ProposalCsv)
    $refineZs = @($proposals | Where-Object { $_.candidate_already_measured -ne 'true' } | ForEach-Object { [string]$_.candidate_z_mm })
    if ($refineZs.Count -gt 0) {
      $refineArgs = @($RefineCsv) + $refineZs
      Start-ComsolStage 'alphaminus30targetprescan' (Join-Path $Work 'refine.stdout.log') (Join-Path $Work 'refine.stderr.log') $refineArgs 'ALPHA_MINUS30_PRESCAN_BATCH_FINISH alpha=-30'
    } else {
      Set-Content -LiteralPath $RefineCsv -Value 'z_sphere_mm,alpha_deg,phi_deg,Fx_B0_raw_mN,Fx_B1_raw_mN,Fx_B2_raw_mN,Fx_hold_corr_mN,DeltaFx_ball_mN,Fx_total_corr_mN,Fy_total_corr_mN,Fz_total_corr_mN,elements,DOF,min_quality,same_mesh_verified,status' -Encoding ascii
      Add-QueueLog 'REFINEMENT_NOT_REQUIRED candidates_already_measured=true'
    }

    & $Python $Prep --select --coarse-csv $CoarseCsv --refine-csv $RefineCsv --selected-csv $SelectedCsv 2>&1 | Tee-Object -FilePath (Join-Path $Work 'selection.log')
    if ($LASTEXITCODE -ne 0) { throw 'MEASURED_TARGET_HEIGHT_SELECTION_FAILED' }
  }
  $selected = @(Import-Csv -LiteralPath $SelectedCsv)
  if ($selected.Count -ne 4 -or @($selected | Select-Object -ExpandProperty selected_z_mm -Unique).Count -ne 4) { throw 'FOUR_UNIQUE_SELECTED_HEIGHTS_REQUIRED' }
  $selectedZs = @($selected | ForEach-Object { '{0:g}' -f [double]$_.selected_z_mm })
  if (-not $Resume) { Add-QueueLog ("SELECTED_REAL_Z alpha=-30 z={0}" -f ($selectedZs -join ',')) }

  $fullArgs = @($selectedZs)
  $fullStdout = if ($Resume) { Join-Path $Work ("full360_resume_{0}.stdout.log" -f (Get-Date -Format 'yyyyMMdd_HHmmss')) } else { Join-Path $Work 'full360.stdout.log' }
  $fullStderr = [System.IO.Path]::ChangeExtension($fullStdout, '.stderr.log')
  Start-ComsolStage 'alphaminus30targetfull360' $fullStdout $fullStderr $fullArgs 'ALPHA_MINUS30_FULL360_BATCH_FINISH alpha=-30 z_count=4 phase_points=101'
  if (-not (Test-Path -LiteralPath $FullCsv) -or -not (Test-Path -LiteralPath $FullSummary)) { throw 'FULL360_OUTPUT_MISSING' }

  & $Python $Prep --finalize --coarse-csv $CoarseCsv --refine-csv $RefineCsv --selected-csv $SelectedCsv --full-csv $FullCsv --run-summary $FullSummary 2>&1 | Tee-Object -FilePath (Join-Path $Work 'finalize.log')
  if ($LASTEXITCODE -ne 0) { throw 'ALPHA_MINUS30_FINAL_VALIDATION_FAILED' }
  Add-QueueLog 'TASK_COMPLETED alpha=-30 selected_z_count=4 full360_rows=404'
  Write-Output 'ALPHA_MINUS30_TARGET_FULL360_COMPLETED rows=404'
} catch {
  if (Test-Path -LiteralPath $QueueLog) { Add-QueueLog ("TASK_ERROR {0}" -f $_.Exception.Message) }
  throw
}
