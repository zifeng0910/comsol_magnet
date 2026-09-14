param([string]$Stamp = '20260914')
$ErrorActionPreference = 'Stop'
$Compile = 'I:\Program Files\COMSOL\COMSOL63\Multiphysics\bin\win64\comsolcompile.exe'
$Java = 'I:\Program Files\COMSOL\COMSOL63\Multiphysics\java\win64\jre\bin\java.exe'
$Plugins = 'I:\Program Files\COMSOL\COMSOL63\Multiphysics\plugins\*'
$Source = 'H:\comsolcc\z120_xsphere26_air200_mesh1.mph'
$ScriptRoot = 'H:\comsolcc\comsol_magnet\scripts'
$WorkRoot = 'H:\comsolcc\comsol_magnet\work'
$zs = @(100,102.5,105,107.5,110,112.5,115,117.5,120)
$jobs = @()
foreach ($z in $zs) { $jobs += [pscustomobject]@{Alpha='alpha0'; Mode='alpha0full360z'; Root=(Join-Path $WorkRoot "modelB_alpha0_z100_120_full360_$Stamp"); Z=$z; Csv='modelB_alpha0_full360_phi.csv'} }
foreach ($z in $zs) { $jobs += [pscustomobject]@{Alpha='minus20'; Mode='alphaminus20full360z'; Root=(Join-Path $WorkRoot "modelB_alpha_minus20_z100_120_full360_$Stamp"); Z=$z; Csv='modelB_alpha_minus20_full360_phi.csv'} }

& $Compile -classpath $Plugins (Join-Path $ScriptRoot 'RunModelBLocalMeshConvergence.java') 2>&1 | Tee-Object (Join-Path $WorkRoot "full360_remaining_compile_$Stamp.log")
if ($LASTEXITCODE -ne 0) { throw 'COMSOL Java compilation failed' }
$queueLog = Join-Path $WorkRoot ("full360_remaining_{0}.stdout.log" -f $Stamp)
foreach ($job in $jobs) {
  $dir = Join-Path $job.Root ("z{0}" -f $job.Z)
  $csv = Join-Path $dir $job.Csv
  if (Test-Path $csv) {
    $rows = @(Import-Csv $csv)
    if ($rows.Count -eq 37 -and (@($rows | Where-Object {$_.status -ne 'SUCCESS'}).Count -eq 0)) {
      Add-Content $queueLog ("SKIP alpha={0} z={1} csv_complete" -f $job.Alpha,$job.Z)
      continue
    }
  }
  New-Item -ItemType Directory -Force -Path $dir | Out-Null
  $stdout = Join-Path $dir 'runner.stdout.log'
  $stderr = Join-Path $dir 'runner.stderr.log'
  $argLine = '-Xmx12g -cp "{0};{1}" RunModelBLocalMeshConvergence "{2}" "{3}" {4} {5}' -f $ScriptRoot,$Plugins,$Source,$dir,$job.Mode,$job.Z
  $proc = Start-Process -FilePath $Java -ArgumentList $argLine -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru -WindowStyle Hidden
  $childPid = $proc.Id
  Add-Content $queueLog ("START PID={0} alpha={1} z={2} dir={3}" -f $childPid,$job.Alpha,$job.Z,$dir)
  $finished = $false
  while (Get-Process -Id $childPid -ErrorAction SilentlyContinue) {
    if (Test-Path $stdout) {
      $tail = Get-Content -LiteralPath $stdout -Tail 5 -ErrorAction SilentlyContinue
      if ($tail -match 'FULL360_FINISH') {
        $finished = $true
        Stop-Process -Id $childPid -Force -ErrorAction SilentlyContinue
        Add-Content $queueLog ("STOP PID={0} FINISH alpha={1} z={2}" -f $childPid,$job.Alpha,$job.Z)
        break
      }
    }
    Start-Sleep -Seconds 15
  }
  while (Get-Process -Id $childPid -ErrorAction SilentlyContinue) { Start-Sleep -Seconds 2 }
  if (-not $finished) {
    Add-Content $queueLog ("FAIL alpha={0} z={1} runner_exited_without_finish" -f $job.Alpha,$job.Z)
    throw ("runner exited without FULL360_FINISH: alpha={0} z={1}" -f $job.Alpha,$job.Z)
  }
}
Add-Content $queueLog 'FULL360_REMAINING_FINISH'
