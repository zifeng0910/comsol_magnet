$ErrorActionPreference = 'Stop'
$ComsolCompile = 'I:\Program Files\COMSOL\COMSOL63\Multiphysics\bin\win64\comsolcompile.exe'
$Java = 'I:\Program Files\COMSOL\COMSOL63\Multiphysics\java\win64\jre\bin\java.exe'
$Plugins = 'I:\Program Files\COMSOL\COMSOL63\Multiphysics\plugins\*'
$Source = 'H:\comsolcc\z120_xsphere26_air200_mesh1.mph'
$Audit = 'H:\comsolcc\accuracy_audit_141_142_20260910_221721'
$JavaFile = 'H:\comsolcc\AuditFixedFieldKeyPhases.java'
$Log = Join-Path $Audit 'audit_runner.log'

New-Item -ItemType Directory -Force -Path $Audit | Out-Null
& $ComsolCompile -classpath $Plugins $JavaFile 2>&1 | Tee-Object -FilePath (Join-Path $Audit 'compile.log') -Append
if ($LASTEXITCODE -ne 0) { throw 'AuditFixedFieldKeyPhases.java compile failed' }

foreach ($profile in @('A0','A1','A2')) {
  foreach ($z in @(141,142)) {
    $name = "${profile}_z${z}"
    $out = Join-Path $Audit $name
    New-Item -ItemType Directory -Force -Path $out | Out-Null
    $stdout = Join-Path $out 'stdout.log'
    $stderr = Join-Path $out 'stderr.log'
    $arg = '-cp "H:\comsolcc;{0}" AuditFixedFieldKeyPhases "{1}" {2} {3} "{4}"' -f $Plugins,$Source,$z,$profile,$out
    Add-Content -LiteralPath $Log -Value ("START profile={0} z={1} {2}" -f $profile,$z,(Get-Date -Format o))
    $p = Start-Process -FilePath $Java -ArgumentList $arg -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru -WindowStyle Hidden
    $null = Wait-Process -Id $p.Id -Timeout 1800 -ErrorAction SilentlyContinue
    if (Get-Process -Id $p.Id -ErrorAction SilentlyContinue) {
      Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
      Add-Content -LiteralPath $Log -Value ("TIMEOUT profile={0} z={1}" -f $profile,$z)
    } elseif (Test-Path -LiteralPath (Join-Path $out 'key_phases.csv')) {
      Add-Content -LiteralPath $Log -Value ("DONE profile={0} z={1}" -f $profile,$z)
    } else {
      Add-Content -LiteralPath $Log -Value ("ERROR profile={0} z={1}" -f $profile,$z)
    }
  }
}
Add-Content -LiteralPath $Log -Value ("FINISH {0}" -f (Get-Date -Format o))
