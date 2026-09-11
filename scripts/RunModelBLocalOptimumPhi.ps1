$ErrorActionPreference = 'Stop'

$Compile = 'I:\Program Files\COMSOL\COMSOL63\Multiphysics\bin\win64\comsolcompile.exe'
$Java = 'I:\Program Files\COMSOL\COMSOL63\Multiphysics\java\win64\jre\bin\java.exe'
$Plugins = 'I:\Program Files\COMSOL\COMSOL63\Multiphysics\plugins\*'
$Source = 'H:\comsolcc\z120_xsphere26_air200_mesh1.mph'
$Out = 'H:\comsolcc\comsol_magnet\work\modelB_local_optimum_phi_20260911'

New-Item -ItemType Directory -Force -Path $Out | Out-Null
& $Compile -classpath $Plugins 'H:\comsolcc\comsol_magnet\scripts\RunModelBSameMeshDifferential.java' 2>&1 | Tee-Object (Join-Path $Out 'compile.log')
if ($LASTEXITCODE -ne 0) { throw 'COMSOL Java compilation failed' }

$stdout = Join-Path $Out 'runner.stdout.log'
$stderr = Join-Path $Out 'runner.stderr.log'
$argLine = '-cp "H:\comsolcc\comsol_magnet\scripts;{0}" RunModelBSameMeshDifferential "{1}" "{2}" 120' -f $Plugins,$Source,$Out
$proc = Start-Process -FilePath $Java -ArgumentList $argLine -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru -WindowStyle Hidden
Write-Output ("PID={0} OUT={1}" -f $proc.Id,$Out)
