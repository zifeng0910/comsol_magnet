param([string]$Stamp = '20260915')
$ErrorActionPreference = 'Stop'

$Root = 'H:\comsolcc\comsol_magnet'
$Compile = 'I:\Program Files\COMSOL\COMSOL63\Multiphysics\bin\win64\comsolcompile.exe'
$Java = 'I:\Program Files\COMSOL\COMSOL63\Multiphysics\java\win64\jre\bin\java.exe'
$Plugins = 'I:\Program Files\COMSOL\COMSOL63\Multiphysics\plugins\*'
$Source = 'H:\comsolcc\z120_xsphere26_air200_mesh1.mph'
$Scripts = Join-Path $Root 'scripts'
$Work = Join-Path $Root "work\modelB_alpha_minus20_z88_full360_$Stamp"
$QueueLog = Join-Path $Work 'z88_queue.log'
$Stdout = Join-Path $Work 'z88_full360.stdout.log'
$Stderr = Join-Path $Work 'z88_full360.stderr.log'
$RunCsv = Join-Path $Work 'modelB_alpha_minus20_z88_full360.csv'
$CandidateXlsx = Join-Path $Work 'modelB_alpha_minus20_selected4_full360_candidate.xlsx'
$Preflight = Join-Path $Scripts 'prepare_modelB_alpha_minus20_selected4_full360.py'
$Python = 'python'

function Add-QueueLog([string]$Line) {
  Add-Content -LiteralPath $QueueLog -Value ((Get-Date -Format 'yyyy-MM-dd HH:mm:ss') + ' ' + $Line)
}

function Assert-NoModelBJava {
  $busy = @(Get-CimInstance Win32_Process | Where-Object {
    $_.Name -match '^java(\.exe)?$' -and $_.CommandLine -match 'RunModelBLocalMeshConvergence'
  })
  if ($busy.Count -gt 0) {
    throw ('COMSOL_MODEL_B_JAVA_ALREADY_RUNNING pid=' + (($busy | ForEach-Object ProcessId) -join ','))
  }
}

try {
  if ((git -C $Root branch --show-current) -ne 'main') { throw 'EXPECTED_MAIN_BRANCH' }
  if (Test-Path -LiteralPath $Work) { throw "Z88_WORK_DIRECTORY_ALREADY_EXISTS: $Work" }
  foreach ($path in @($Compile, $Java, $Source, $Preflight)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "REQUIRED_INPUT_MISSING: $path" }
  }
  Assert-NoModelBJava
  New-Item -ItemType Directory -Path $Work | Out-Null
  Set-Content -LiteralPath $QueueLog -Value 'TASK=ALPHA_MINUS20_Z88_FULL360 alpha=-20 z=88 x=26 gap=0.30 phi=0:3.6:360' -Encoding ascii
  Add-QueueLog ("QUEUE_START queue_pid={0} expected_alpha=-20 expected_z=88" -f $PID)

  & $Python $Preflight --preflight 2>&1 | Tee-Object -FilePath (Join-Path $Work 'preflight.log')
  if ($LASTEXITCODE -ne 0) { throw 'Z88_PREFLIGHT_FAILED' }

  & $Compile -classpath $Plugins (Join-Path $Scripts 'RunModelBLocalMeshConvergence.java') 2>&1 | Tee-Object -FilePath (Join-Path $Work 'compile.log')
  if ($LASTEXITCODE -ne 0) { throw 'COMSOL_JAVA_COMPILE_FAILED' }

  Assert-NoModelBJava
  $argLine = '-Xmx12g -cp "{0};{1}" RunModelBLocalMeshConvergence "{2}" "{3}" alphaminus20z88full360' -f $Scripts,$Plugins,$Source,$Work
  $process = Start-Process -FilePath $Java -ArgumentList $argLine -RedirectStandardOutput $Stdout -RedirectStandardError $Stderr -PassThru -WindowStyle Hidden
  Add-QueueLog ("JAVA_START pid={0} alpha=-20 z=88 mode=alphaminus20z88full360" -f $process.Id)
  $finishSeen = $false
  $finishAt = $null
  while ($true) {
    $process.Refresh()
    if ($process.HasExited) { break }
    if (-not $finishSeen -and (Test-Path -LiteralPath $Stdout) -and (Select-String -LiteralPath $Stdout -Pattern 'Z88_FULL360_BATCH_FINISH alpha=-20 z=88 phase_points=101' -Quiet)) {
      $finishSeen = $true
      $finishAt = Get-Date
      Add-QueueLog 'JAVA_FINISH_MARKER_SEEN'
    }
    if ($finishSeen -and ((Get-Date) - $finishAt).TotalSeconds -ge 120) {
      Add-QueueLog 'JAVA_CLEANUP_TIMEOUT stopping Java after completed 101-point marker'
      Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
      break
    }
    Start-Sleep -Seconds 5
  }
  $process.Refresh()
  Add-QueueLog ("JAVA_EXIT pid={0} exit_code={1}" -f $process.Id,$process.ExitCode)
  if (-not $finishSeen -and (Test-Path -LiteralPath $Stdout)) {
    $finishSeen = [bool](Select-String -LiteralPath $Stdout -Pattern 'Z88_FULL360_BATCH_FINISH alpha=-20 z=88 phase_points=101' -Quiet)
  }
  if (-not $finishSeen) { throw 'Z88_FULL360_FINISH_MARKER_MISSING' }
  if ($process.ExitCode -ne 0 -and -not (Test-Path -LiteralPath $RunCsv)) { throw "COMSOL_JAVA_EXIT_$($process.ExitCode)" }

  & $Python $Preflight --finalize --run-csv $RunCsv --candidate-xlsx $CandidateXlsx 2>&1 | Tee-Object -FilePath (Join-Path $Work 'finalize.log')
  if ($LASTEXITCODE -ne 0) { throw 'Z88_FINAL_VALIDATION_OR_EXCEL_EXPORT_FAILED' }
  Add-QueueLog 'FINAL_DATA_VALIDATION_OK z88_rows=101 excel_rows=404'

  Push-Location $Root
  try {
    git add -- scripts/RunModelBLocalMeshConvergence.java scripts/RunModelBAlphaMinus20Z88Full360.ps1 scripts/MonitorModelBAlphaMinus20Z88Full360.ps1 scripts/prepare_modelB_alpha_minus20_selected4_full360.py data/modelB_alpha_minus20_z88_full360.csv data/modelB_alpha_minus20_selected4_full360.xlsx
    git commit -m 'Add alpha minus20 z88 full360 and selected four-group dataset'
    if ($LASTEXITCODE -ne 0) { throw 'FINAL_COMMIT_FAILED' }
    git push origin main
    if ($LASTEXITCODE -ne 0) { throw 'FINAL_PUSH_FAILED' }
  } finally { Pop-Location }
  $commit = git -C $Root rev-parse HEAD
  Add-QueueLog ("TASK_COMPLETED commit={0} z88_rows=101 excel_rows=404" -f $commit)
  Write-Output ("ALPHA_MINUS20_Z88_FULL360_COMPLETED commit={0}" -f $commit)
} catch {
  if (Test-Path -LiteralPath $QueueLog) { Add-QueueLog ("TASK_ERROR {0}" -f $_.Exception.Message) }
  throw
}
