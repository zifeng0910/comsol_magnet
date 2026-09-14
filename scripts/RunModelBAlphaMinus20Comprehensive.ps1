param([string]$Stamp = '20260914')
$ErrorActionPreference = 'Stop'
$Compile = 'I:\Program Files\COMSOL\COMSOL63\Multiphysics\bin\win64\comsolcompile.exe'
$Java = 'I:\Program Files\COMSOL\COMSOL63\Multiphysics\java\win64\jre\bin\java.exe'
$Plugins = 'I:\Program Files\COMSOL\COMSOL63\Multiphysics\plugins\*'
$Source = 'H:\comsolcc\z120_xsphere26_air200_mesh1.mph'
$ScriptRoot = 'H:\comsolcc\comsol_magnet\scripts'
$WorkRoot = 'H:\comsolcc\comsol_magnet\work'
$DataRoot = 'H:\comsolcc\comsol_magnet\data'
$WorkDir = Join-Path $WorkRoot 'modelB_alpha_minus20_comprehensive_20260914'
$PhiCsv = Join-Path $DataRoot 'modelB_alpha_minus20_macro_phi.csv'
$SummaryCsv = Join-Path $DataRoot 'modelB_alpha_minus20_macro_Fmax_summary.csv'
$QueueLog = Join-Path $WorkDir 'alpha_minus20_queue.stdout.log'
$Zs = @(40,50,60,70,80,90,100,110,120,130,140,150,160)

Write-Output '=== PRE-RUN AUDIT ==='
Write-Output 'TASK=ALPHA_MINUS20_ONLY'
Write-Output 'alpha=-20'
Write-Output 'x_sphere=26'
Write-Output 'gap=0.30'
Write-Output 'z_list=40,50,60,70,80,90,100,110,120,130,140,150,160'
Write-Output 'phi_list=0,30,60,90,120,150,180,210,240,270,300,330,360'
Write-Output ("workdir={0}" -f $WorkDir)

$related = @(Get-CimInstance Win32_Process | Where-Object {
  $_.CommandLine -match 'RunModelBAlphaMinus20Comprehensive\.ps1|modelB_alpha_minus20_comprehensive_20260914'
})
if ($related.Count -gt 0) { throw 'RELATED_ALPHA_MINUS20_TASK_ALREADY_RUNNING' }

New-Item -ItemType Directory -Force -Path $WorkDir,$DataRoot | Out-Null
Set-Content -LiteralPath $PhiCsv -Value 'z_sphere_mm,alpha_deg,phi_deg,Fx_B0_raw_mN,Fx_B1_raw_mN,Fx_B2_raw_mN,Fx_hold_corr_mN,DeltaFx_ball_mN,Fx_total_corr_mN,Fy_total_corr_mN,Fz_total_corr_mN,elements,DOF,min_quality,same_mesh_verified,status' -Encoding ascii
Set-Content -LiteralPath $SummaryCsv -Value 'z_sphere_mm,alpha_deg,Fmax_mN,phi_at_Fmax_deg,Fmin_mN,phi_at_Fmin_deg,DeltaFx_ball_at_Fmax_mN,Fx_hold_corr_mN,positive_phase_count,negative_phase_count,all_sampled_phi_negative,release_candidate,phase_points,status' -Encoding ascii
& $Compile -classpath $Plugins (Join-Path $ScriptRoot 'RunModelBLocalMeshConvergence.java') 2>&1 | Tee-Object (Join-Path $WorkDir 'compile.log')
if ($LASTEXITCODE -ne 0) { throw 'COMSOL_JAVA_COMPILE_FAILED' }

foreach ($z in $Zs) {
  $zDir = Join-Path $WorkDir ("z{0}" -f $z)
  New-Item -ItemType Directory -Force -Path $zDir | Out-Null
  $stdout = Join-Path $zDir 'runner.stdout.log'
  $stderr = Join-Path $zDir 'runner.stderr.log'
  $argLine = '-Xmx12g -cp "{0};{1}" RunModelBLocalMeshConvergence "{2}" "{3}" alphaminus20macro30z {4} "{5}" "{6}"' -f $ScriptRoot,$Plugins,$Source,$zDir,$z,$PhiCsv,$SummaryCsv
  $proc = Start-Process -FilePath $Java -ArgumentList $argLine -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru -WindowStyle Hidden
  $childPid = $proc.Id
  Add-Content -LiteralPath $QueueLog -Value ("START PID={0} alpha=-20 z={1} dir={2}" -f $childPid,$z,$zDir)
  $finished = $false
  while (Get-Process -Id $childPid -ErrorAction SilentlyContinue) {
    if (Test-Path $stdout) {
      $tail = Get-Content -LiteralPath $stdout -Tail 5 -ErrorAction SilentlyContinue
      if ($tail -match 'MACRO_FINISH alpha=-20') {
        $finished = $true
        Stop-Process -Id $childPid -Force -ErrorAction SilentlyContinue
        Add-Content -LiteralPath $QueueLog -Value ("FINISH PID={0} alpha=-20 z={1}" -f $childPid,$z)
        break
      }
    }
    Start-Sleep -Seconds 15
  }
  while (Get-Process -Id $childPid -ErrorAction SilentlyContinue) { Start-Sleep -Seconds 2 }
  if (-not $finished) { throw ("RUNNER_EXITED_WITHOUT_FINISH z={0}" -f $z) }
  if ($z -eq 40) {
    $first = @(Import-Csv $PhiCsv | Select-Object -First 1)
    if ($first.Count -ne 1 -or [double]$first[0].z_sphere_mm -ne 40 -or [double]$first[0].alpha_deg -ne -20 -or [double]$first[0].phi_deg -ne 0 -or $first[0].status -ne 'SUCCESS') {
      throw 'FIRST_POINT_VALIDATION_FAILED_EXPECTED_Z40_PHI0_ALPHA_MINUS20'
    }
    Write-Output 'FIRST_POINT_VALIDATED z=40 phi=0 alpha=-20 status=SUCCESS'
  }
}
Add-Content -LiteralPath $QueueLog -Value 'ALPHA_MINUS20_FINISH'
Write-Output 'ALPHA_MINUS20_FINISH'
