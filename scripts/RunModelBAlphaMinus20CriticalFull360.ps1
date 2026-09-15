param(
  [double[]]$Zs = @(108,109,110,111),
  [string]$Stamp = '20260914'
)
$ErrorActionPreference = 'Stop'
$Compile = 'I:\\Program Files\\COMSOL\\COMSOL63\\Multiphysics\\bin\\win64\\comsolcompile.exe'
$Java = 'I:\\Program Files\\COMSOL\\COMSOL63\\Multiphysics\\java\\win64\\jre\\bin\\java.exe'
$Plugins = 'I:\\Program Files\\COMSOL\\COMSOL63\\Multiphysics\\plugins\\*'
$Source = 'H:\\comsolcc\\z120_xsphere26_air200_mesh1.mph'
$ScriptRoot = 'H:\\comsolcc\\comsol_magnet\\scripts'
$WorkDir = "H:\\comsolcc\\comsol_magnet\\work\\modelB_alpha_minus20_critical_$Stamp"
$DataRoot = 'H:\\comsolcc\\comsol_magnet\\data'
$Csv = Join-Path $DataRoot 'modelB_alpha_minus20_critical_full360.csv'
$Summary = Join-Path $DataRoot 'modelB_alpha_minus20_critical_full360_summary.csv'
$QueueLog = Join-Path $WorkDir 'critical_full360_queue.log'
$Stdout = Join-Path $WorkDir 'runner.stdout.log'
$Stderr = Join-Path $WorkDir 'runner.stderr.log'

if (@($Zs).Count -ne 4) { throw 'EXACTLY_FOUR_ZS_REQUIRED' }
if (@($Zs | Where-Object { $_ -lt 100 -or $_ -gt 120 }).Count -gt 0) { throw 'Z_OUTSIDE_CRITICAL_BAND' }
New-Item -ItemType Directory -Force -Path $WorkDir,$DataRoot | Out-Null
Set-Content -LiteralPath $Csv -Value 'z_sphere_mm,alpha_deg,phi_deg,Fx_B0_raw_mN,Fx_B1_raw_mN,Fx_B2_raw_mN,Fx_hold_corr_mN,DeltaFx_ball_mN,Fx_total_corr_mN,Fy_total_corr_mN,Fz_total_corr_mN,elements,DOF,min_quality,same_mesh_verified,status' -Encoding ascii
Set-Content -LiteralPath $Summary -Value 'z_sphere_mm,alpha_deg,Fmax_mN,phi_at_Fmax_deg,Fmin_mN,phi_at_Fmin_deg,positive_phase_count,negative_phase_count,all_sampled_phi_negative,phase_points,Fx_hold_corr_mN,elements,DOF,min_quality,same_mesh_verified,status' -Encoding ascii
Set-Content -LiteralPath $QueueLog -Value ("TASK=ALPHA_MINUS20_CRITICAL_FULL360 alpha=-20 phi=0:3.6:360 z={0}" -f ($Zs -join ',')) -Encoding ascii
& $Compile -classpath $Plugins (Join-Path $ScriptRoot 'RunModelBLocalMeshConvergence.java') 2>&1 | Tee-Object (Join-Path $WorkDir 'compile.log')
if ($LASTEXITCODE -ne 0) { throw 'COMSOL_JAVA_COMPILE_FAILED' }
$zArgs = ($Zs | ForEach-Object { '{0:g}' -f $_ }) -join '" "'
$argLine = '-Xmx12g -cp "{0};{1}" RunModelBLocalMeshConvergence "{2}" "{3}" alphaminus20criticalfull360 "{4}"' -f $ScriptRoot,$Plugins,$Source,$WorkDir,$zArgs
$proc = Start-Process -FilePath $Java -ArgumentList $argLine -RedirectStandardOutput $Stdout -RedirectStandardError $Stderr -PassThru -WindowStyle Hidden
Add-Content -LiteralPath $QueueLog -Value ("START queue_pid={0} java_pid={1} csv={2} summary={3}" -f $PID,$proc.Id,$Csv,$Summary)
 $finishedMarker = $false
while (-not $proc.HasExited) {
  Start-Sleep -Seconds 20
  if (Test-Path $Stdout -and (Select-String -LiteralPath $Stdout -Pattern 'CRITICAL_FULL360_BATCH_FINISH' -Quiet)) {
    $finishedMarker = $true
    Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
    break
  }
  $proc.Refresh()
}
$proc.Refresh()
Add-Content -LiteralPath $QueueLog -Value ("JAVA_EXIT code={0}" -f $proc.ExitCode)
if (-not $finishedMarker -and $proc.ExitCode -ne 0) { throw ("CRITICAL_FULL360_JAVA_EXIT_{0}" -f $proc.ExitCode) }
 $runCsv = Join-Path $WorkDir 'modelB_alpha_minus20_critical_full360.csv'
 $runSummary = Join-Path $WorkDir 'modelB_alpha_minus20_critical_full360_summary.csv'
if (-not (Test-Path $runCsv) -or -not (Test-Path $runSummary)) { throw 'CRITICAL_FULL360_OUTPUT_MISSING' }
Copy-Item -LiteralPath $runCsv -Destination $Csv -Force
Copy-Item -LiteralPath $runSummary -Destination $Summary -Force
$rows = @(Import-Csv $Csv); $summaries = @(Import-Csv $Summary)
if ($rows.Count -ne 404 -or $summaries.Count -ne 4) { throw 'CRITICAL_FULL360_ROW_COUNT_VALIDATION_FAILED' }
if (@($rows | Where-Object { $_.status -ne 'SUCCESS' -or [double]$_.alpha_deg -ne -20 -or $_.same_mesh_verified -ne 'true' }).Count -ne 0) { throw 'CRITICAL_FULL360_STATUS_VALIDATION_FAILED' }
if (@($rows | Group-Object z_sphere_mm | Where-Object Count -ne 101).Count -ne 0) { throw 'CRITICAL_FULL360_PHASE_COUNT_VALIDATION_FAILED' }
Add-Content -LiteralPath $QueueLog -Value 'CRITICAL_FULL360_FINISH'
Write-Output ("CRITICAL_FULL360_FINISH rows={0} summaries={1} csv={2} summary={3}" -f $rows.Count,$summaries.Count,$Csv,$Summary)
