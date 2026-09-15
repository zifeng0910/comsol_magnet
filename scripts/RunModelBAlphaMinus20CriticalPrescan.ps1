param([string]$Stamp = '20260914')
$ErrorActionPreference = 'Stop'
$Compile = 'I:\\Program Files\\COMSOL\\COMSOL63\\Multiphysics\\bin\\win64\\comsolcompile.exe'
$Java = 'I:\\Program Files\\COMSOL\\COMSOL63\\Multiphysics\\java\\win64\\jre\\bin\\java.exe'
$Plugins = 'I:\\Program Files\\COMSOL\\COMSOL63\\Multiphysics\\plugins\\*'
$Source = 'H:\\comsolcc\\z120_xsphere26_air200_mesh1.mph'
$ScriptRoot = 'H:\\comsolcc\\comsol_magnet\\scripts'
$WorkDir = "H:\\comsolcc\\comsol_magnet\\work\\modelB_alpha_minus20_critical_$Stamp"
$DataRoot = 'H:\\comsolcc\\comsol_magnet\\data'
$Csv = Join-Path $DataRoot 'modelB_alpha_minus20_critical_phi90_prescan.csv'
$Zs = @(108,109,110,111,112,113,114,115,116)
$QueueLog = Join-Path $WorkDir 'critical_prescan_queue.log'
$Stdout = Join-Path $WorkDir 'runner.stdout.log'
$Stderr = Join-Path $WorkDir 'runner.stderr.log'

New-Item -ItemType Directory -Force -Path $WorkDir,$DataRoot | Out-Null
Set-Content -LiteralPath $Csv -Value 'z_sphere_mm,alpha_deg,phi_deg,Fx_B0_raw_mN,Fx_B1_raw_mN,Fx_B2_raw_mN,Fx_hold_corr_mN,DeltaFx_ball_mN,Fx_total_corr_mN,Fy_total_corr_mN,Fz_total_corr_mN,elements,DOF,min_quality,same_mesh_verified,release_candidate,status' -Encoding ascii
Set-Content -LiteralPath $QueueLog -Value ("TASK=ALPHA_MINUS20_CRITICAL_PRESCAN alpha=-20 phi=90 z={0}" -f ($Zs -join ',')) -Encoding ascii
& $Compile -classpath $Plugins (Join-Path $ScriptRoot 'RunModelBLocalMeshConvergence.java') 2>&1 | Tee-Object (Join-Path $WorkDir 'compile.log')
if ($LASTEXITCODE -ne 0) { throw 'COMSOL_JAVA_COMPILE_FAILED' }
$argLine = '-Xmx12g -cp "{0};{1}" RunModelBLocalMeshConvergence "{2}" "{3}" alphaminus20criticalprescan' -f $ScriptRoot,$Plugins,$Source,$WorkDir
$proc = Start-Process -FilePath $Java -ArgumentList $argLine -RedirectStandardOutput $Stdout -RedirectStandardError $Stderr -PassThru -WindowStyle Hidden
Add-Content -LiteralPath $QueueLog -Value ("START queue_pid={0} java_pid={1} csv={2}" -f $PID,$proc.Id,$Csv)
 $finishedMarker = $false
while (-not $proc.HasExited) {
  Start-Sleep -Seconds 10
  if (Test-Path $Stdout -and (Select-String -LiteralPath $Stdout -Pattern 'CRITICAL_PRESCAN_FINISH' -Quiet)) {
    $finishedMarker = $true
    Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
    break
  }
  $proc.Refresh()
}
$proc.Refresh()
Add-Content -LiteralPath $QueueLog -Value ("JAVA_EXIT code={0}" -f $proc.ExitCode)
if (-not $finishedMarker -and $proc.ExitCode -ne 0) { throw ("CRITICAL_PRESCAN_JAVA_EXIT_{0}" -f $proc.ExitCode) }
 $runCsv = Join-Path $WorkDir 'modelB_alpha_minus20_critical_phi90_prescan.csv'
if (-not (Test-Path $runCsv)) { throw 'CRITICAL_PRESCAN_OUTPUT_MISSING' }
Copy-Item -LiteralPath $runCsv -Destination $Csv -Force
$rows = @(Import-Csv $Csv)
if ($rows.Count -ne $Zs.Count -or @($rows | Where-Object { $_.status -ne 'SUCCESS' -or [double]$_.alpha_deg -ne -20 -or [double]$_.phi_deg -ne 90 }).Count -ne 0) { throw 'CRITICAL_PRESCAN_VALIDATION_FAILED' }
Add-Content -LiteralPath $QueueLog -Value 'CRITICAL_PRESCAN_FINISH'
Write-Output ("CRITICAL_PRESCAN_FINISH rows={0} csv={1}" -f $rows.Count,$Csv)
