param(
  [ValidateSet('alpha0','minus20','both')][string]$Alpha = 'both',
  [int]$MaxParallel = 3,
  [string]$Stamp = '20260913'
)
$ErrorActionPreference = 'Stop'
$Compile = 'I:\Program Files\COMSOL\COMSOL63\Multiphysics\bin\win64\comsolcompile.exe'
$Java = 'I:\Program Files\COMSOL\COMSOL63\Multiphysics\java\win64\jre\bin\java.exe'
$Plugins = 'I:\Program Files\COMSOL\COMSOL63\Multiphysics\plugins\*'
$Source = 'H:\comsolcc\z120_xsphere26_air200_mesh1.mph'
$ScriptRoot = 'H:\comsolcc\comsol_magnet\scripts'
$WorkRoot = 'H:\comsolcc\comsol_magnet\work'
$zs = @(100,102.5,105,107.5,110,112.5,115,117.5,120)
& $Compile -classpath $Plugins (Join-Path $ScriptRoot 'RunModelBLocalMeshConvergence.java') 2>&1 | Tee-Object (Join-Path $WorkRoot "full360_compile_$Stamp.log")
if ($LASTEXITCODE -ne 0) { throw 'COMSOL Java compilation failed' }
$jobs = @()
if ($Alpha -in @('alpha0','both')) { $jobs += $zs | ForEach-Object { [pscustomobject]@{Alpha='alpha0'; Z=$_; Mode='alpha0full360z'; Root=(Join-Path $WorkRoot "modelB_alpha0_z100_120_full360_$Stamp") } } }
if ($Alpha -in @('minus20','both')) { $jobs += $zs | ForEach-Object { [pscustomobject]@{Alpha='minus20'; Z=$_; Mode='alphaminus20full360z'; Root=(Join-Path $WorkRoot "modelB_alpha_minus20_z100_120_full360_$Stamp") } } }
$active = @()
foreach ($job in $jobs) {
  while (@($active | Where-Object { -not $_.Process.HasExited }).Count -ge $MaxParallel) {
    Start-Sleep -Seconds 10
    $active = @($active | Where-Object { -not $_.Process.HasExited })
  }
  $dir = Join-Path $job.Root ("z{0}" -f $job.Z)
  New-Item -ItemType Directory -Force -Path $dir | Out-Null
  $stdout = Join-Path $dir 'runner.stdout.log'; $stderr = Join-Path $dir 'runner.stderr.log'
  $argLine = '-Xmx12g -cp "{0};{1}" RunModelBLocalMeshConvergence "{2}" "{3}" {4} {5}' -f $ScriptRoot,$Plugins,$Source,$dir,$job.Mode,$job.Z
  $proc = Start-Process -FilePath $Java -ArgumentList $argLine -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru -WindowStyle Hidden
  $active += [pscustomobject]@{Process=$proc;Alpha=$job.Alpha;Z=$job.Z;Dir=$dir}
  Write-Output ("START PID={0} alpha={1} z={2} dir={3}" -f $proc.Id,$job.Alpha,$job.Z,$dir)
}
while (@($active | Where-Object { -not $_.Process.HasExited }).Count -gt 0) {
  Start-Sleep -Seconds 20
  foreach ($item in @($active | Where-Object { $_.Process.HasExited })) { Write-Output ("DONE PID={0} alpha={1} z={2} exit={3}" -f $item.Process.Id,$item.Alpha,$item.Z,$item.Process.ExitCode) }
  $active = @($active | Where-Object { -not $_.Process.HasExited })
}
Write-Output 'FULL360_GRID_FINISH'
