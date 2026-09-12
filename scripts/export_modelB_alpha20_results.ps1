param(
  [string]$MacroWorkDir = 'H:\comsolcc\comsol_magnet\work\modelB_alpha20_sparse_20260912',
  [string]$RefinementWorkDir = 'H:\comsolcc\comsol_magnet\work\modelB_alpha20_z70_90_refinement_20260912',
  [string]$M02WorkDir = 'H:\comsolcc\comsol_magnet\work\modelB_alpha20_peak_M02_z90_phi90_20260912',
  [string]$DataDir = 'H:\comsolcc\comsol_magnet\data'
)

$ErrorActionPreference = 'Stop'
$ExpectedAngles = @(0, 45, 90, 135, 180, 225, 270, 315, 360)

function Import-Alpha20Rows {
  param([string]$Directory, [string]$FileName, [int]$ExpectedFiles)
  $sourceFiles = @(Get-ChildItem -LiteralPath $Directory -Recurse -Filter $FileName)
  if ($sourceFiles.Count -ne $ExpectedFiles) {
    throw "Expected $ExpectedFiles $FileName files in $Directory, found $($sourceFiles.Count)."
  }
  @(
    foreach ($file in $sourceFiles) {
      foreach ($row in (Import-Csv -LiteralPath $file.FullName)) {
        [pscustomobject]@{
          z_sphere_mm        = $row.z_sphere_mm
          alpha_deg          = '20.0000000000'
          phi_deg            = $row.phi_deg
          mesh_level         = $row.mesh_level
          Fx_B0_raw_mN       = $row.Fx_B0_raw_mN
          Fx_B1_raw_mN       = $row.Fx_B1_raw_mN
          Fx_B2_raw_mN       = $row.Fx_B2_raw_mN
          Fx_hold_corr_mN    = $row.Fx_hold_corr_mN
          DeltaFx_ball_mN    = $row.DeltaFx_ball_mN
          Fx_total_corr_mN   = $row.Fx_total_corr_mN
          Fy_total_corr_mN   = $row.Fy_total_corr_mN
          Fz_total_corr_mN   = $row.Fz_total_corr_mN
          elements           = $row.elements
          DOF                = $row.DOF
          min_quality        = $row.min_quality
          same_mesh_verified = $row.same_mesh_verified
          release_candidate  = $row.release_candidate
          status             = $row.status
        }
      }
    }
  ) | Sort-Object { [double]$_.z_sphere_mm }, { [double]$_.phi_deg }
}

function Get-FmaxSummary {
  param([object[]]$Rows)
  @(
    foreach ($group in ($Rows | Group-Object z_sphere_mm | Sort-Object { [double]$_.Name })) {
      $angles = @($group.Group | ForEach-Object { [double]$_.phi_deg } | Sort-Object)
      if ($angles.Count -ne $ExpectedAngles.Count -or
          (Compare-Object -ReferenceObject $ExpectedAngles -DifferenceObject $angles).Count -ne 0) {
        throw "Incomplete phase set for z=$($group.Name): $($angles -join ', ')."
      }
      if (($group.Group | Where-Object status -ne 'SUCCESS').Count -ne 0) {
        throw "Failed alpha=20 row for z=$($group.Name)."
      }
      $maximum = $group.Group | Sort-Object { [double]$_.Fx_total_corr_mN } -Descending | Select-Object -First 1
      $minimum = $group.Group | Sort-Object { [double]$_.Fx_total_corr_mN } | Select-Object -First 1
      $allNegative = [double]$maximum.Fx_total_corr_mN -lt 0
      [pscustomobject]@{
        z_sphere_mm               = $group.Name
        alpha_deg                 = '20.0000000000'
        Fmin_mN                   = $minimum.Fx_total_corr_mN
        Fmax_mN                   = $maximum.Fx_total_corr_mN
        phi_at_Fmax_deg           = $maximum.phi_deg
        phi_at_Fmin_deg           = $minimum.phi_deg
        DeltaFx_ball_at_Fmax_mN   = $maximum.DeltaFx_ball_mN
        Fx_hold_corr_mN           = $maximum.Fx_hold_corr_mN
        all_sampled_phi_negative  = $allNegative.ToString()
        release_candidate         = (-not $allNegative).ToString()
        elements                  = $maximum.elements
        min_quality               = $maximum.min_quality
        zero_360_closure_error_mN = [math]::Abs(
          [double]($group.Group | Where-Object { [double]$_.phi_deg -eq 0 }).Fx_total_corr_mN -
          [double]($group.Group | Where-Object { [double]$_.phi_deg -eq 360 }).Fx_total_corr_mN
        ).ToString('G12', [Globalization.CultureInfo]::InvariantCulture)
        status                    = 'SUCCESS'
      }
    }
  )
}

$macroRows = @(Import-Alpha20Rows -Directory $MacroWorkDir -FileName 'modelB_alpha20_phi_sparse.csv' -ExpectedFiles 6)
$refinementRows = @(Import-Alpha20Rows -Directory $RefinementWorkDir -FileName 'modelB_alpha20_refinement_phi_sparse.csv' -ExpectedFiles 4)
if ($macroRows.Count -ne 54 -or $refinementRows.Count -ne 36) {
  throw "Unexpected row counts: macro=$($macroRows.Count), refinement=$($refinementRows.Count)."
}

$macroRows | Export-Csv -LiteralPath (Join-Path $DataDir 'modelB_alpha20_sparse.csv') -NoTypeInformation
$refinementRows | Export-Csv -LiteralPath (Join-Path $DataDir 'modelB_alpha20_z70_90_refinement.csv') -NoTypeInformation

$refinementSummary = @(Get-FmaxSummary -Rows $refinementRows)
$refinementSummary | Export-Csv -LiteralPath (Join-Path $DataDir 'modelB_alpha20_z70_90_Fmax_summary.csv') -NoTypeInformation

$summary = @(Get-FmaxSummary -Rows @($macroRows + $refinementRows))
if ($summary.Count -ne 10) { throw "Expected 10 combined alpha=20 heights, found $($summary.Count)." }
$summary | Export-Csv -LiteralPath (Join-Path $DataDir 'modelB_alpha20_Fmax_summary.csv') -NoTypeInformation

$alpha0 = Import-Csv -LiteralPath (Join-Path $DataDir 'modelB_x26_macro_Fmax_summary.csv')
$comparison = @(
  foreach ($baseline in ($alpha0 | Where-Object { [double]$_.z_sphere_mm -in @(60,80,100,120,140,160) })) {
    $row = $summary | Where-Object { [double]$_.z_sphere_mm -eq [double]$baseline.z_sphere_mm }
    if ($null -eq $row) { throw "Missing alpha=20 result for z=$($baseline.z_sphere_mm)." }
    [pscustomobject]@{
      z_sphere_mm               = $row.z_sphere_mm
      alpha0_Fmax_mN            = $baseline.Fmax_mN
      alpha20_Fmax_mN           = $row.Fmax_mN
      alpha20_minus_alpha0_mN   = ([double]$row.Fmax_mN - [double]$baseline.Fmax_mN).ToString('G12', [Globalization.CultureInfo]::InvariantCulture)
      alpha0_phi_at_Fmax_deg    = $baseline.phi_at_Fmax_deg
      alpha20_phi_at_Fmax_deg   = $row.phi_at_Fmax_deg
      alpha0_release_candidate  = $baseline.release_candidate
      alpha20_release_candidate = $row.release_candidate
      status                    = 'SUCCESS'
    }
  }
) | Sort-Object { [double]$_.z_sphere_mm }
$comparison | Export-Csv -LiteralPath (Join-Path $DataDir 'modelB_alpha0_alpha20_Fmax_comparison.csv') -NoTypeInformation

$m02 = @(Import-Csv -LiteralPath (Join-Path $M02WorkDir 'modelB_alpha20_M02_validation.csv'))
if ($m02.Count -ne 1 -or $m02[0].status -ne 'SUCCESS' -or [double]$m02[0].alpha_deg -ne 20) {
  throw 'The alpha=20 M02 validation row is missing or invalid.'
}
$m02 | Export-Csv -LiteralPath (Join-Path $DataDir 'modelB_alpha20_peak_M02_validation.csv') -NoTypeInformation

$peak = $summary | Sort-Object { [double]$_.Fmax_mN } -Descending | Select-Object -First 1
Write-Output "Exported macro=$($macroRows.Count), refinement=$($refinementRows.Count), combined_heights=$($summary.Count)."
Write-Output "Peak alpha=20 point: z=$($peak.z_sphere_mm) mm, phi=$($peak.phi_at_Fmax_deg) deg, Fmax=$($peak.Fmax_mN) mN."
Write-Output "M02 Fx_total_corr=$($m02[0].Fx_total_corr_mN) mN."
