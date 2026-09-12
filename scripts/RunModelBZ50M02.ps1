param([Parameter(Mandatory=$true)][double]$Phi)
$ErrorActionPreference = 'Stop'
$Compile = 'I:\Program Files\COMSOL\COMSOL63\Multiphysics\bin\win64\comsolcompile.exe'
$Java = 'I:\Program Files\COMSOL\COMSOL63\Multiphysics\java\win64\jre\bin\java.exe'
$Plugins = 'I:\Program Files\COMSOL\COMSOL63\Multiphysics\plugins\*'
$Source = 'H:\comsolcc\z120_xsphere26_air200_mesh1.mph'
$Out = 'H:\comsolcc\comsol_magnet\work\modelB_z50_stageA_M02_20260912'
New-Item -ItemType Directory -Force -Path $Out | Out-Null
& $Compile -classpath $Plugins 'H:\comsolcc\comsol_magnet\scripts\RunModelBLocalMeshConvergence.java' 2>&1 | Tee-Object (Join-Path $Out 'compile.log')
if ($LASTEXITCODE -ne 0) { throw 'COMSOL Java compilation failed' }
$stdout=Join-Path $Out 'runner.stdout.log'; $stderr=Join-Path $Out 'runner.stderr.log'
$argLine='-Xmx12g -cp "H:\comsolcc\comsol_magnet\scripts;{0}" RunModelBLocalMeshConvergence "{1}" "{2}" m02pose 50 {3}' -f $Plugins,$Source,$Out,$Phi
$proc=Start-Process -FilePath $Java -ArgumentList $argLine -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru -WindowStyle Hidden
Write-Output ("PID={0} OUT={1} PHI={2}" -f $proc.Id,$Out,$Phi)
