$ErrorActionPreference = 'Stop'

$Java = 'I:\Program Files\COMSOL\COMSOL63\Multiphysics\java\win64\jre\bin\java.exe'
$Plugins = 'I:\Program Files\COMSOL\COMSOL63\Multiphysics\plugins\*'
$Source = 'H:\comsolcc\z120_xsphere26_air200_mesh1.mph'
$Out = 'H:\comsolcc\comsol_magnet\work\modelB_lower_z_same_mesh_20260911'

New-Item -ItemType Directory -Force -Path $Out | Out-Null
$stdout = Join-Path $Out 'runner.stdout.log'
$stderr = Join-Path $Out 'runner.stderr.log'
Remove-Item -LiteralPath $stdout,$stderr -Force -ErrorAction SilentlyContinue

# .class 文件由 comsolcompile 放在 scripts 目录；插件类路径必须保留引号。
$argLine = '-cp "H:\comsolcc\comsol_magnet\scripts;{0}" RunModelBSameMeshDifferential "{1}" "{2}" 110 100 90 80' -f $Plugins,$Source,$Out
$proc = Start-Process -FilePath $Java -ArgumentList $argLine -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru -WindowStyle Hidden
Write-Output ("PID={0} OUT={1}" -f $proc.Id,$Out)
