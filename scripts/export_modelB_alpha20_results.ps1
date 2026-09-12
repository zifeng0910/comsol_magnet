param(
  [string]$WorkDir = 'H:\comsolcc\comsol_magnet\work\modelB_alpha20_sparse_20260912',
  [string]$DataDir = 'H:\comsolcc\comsol_magnet\data'
)

$ErrorActionPreference = 'Stop'

$sourceFiles = Get-ChildItem -LiteralPath $WorkDir -Recurse -Filter 'modelB_alpha20_phi_sparse.csv'
if ($sourceFiles.Count -ne 6) {
  throw "Expected 6 alpha=20 result files, found $($sourceFiles.Count)."
}

$rows = @(
  foreach ($file in $sourceFiles) {
    foreach ($row in (Import-Csv -LiteralPath $file.FullName)) {
      [pscustomobject]@{
        z_sphere_mm       = $row.z_sphere_mm
        alpha_deg         = '20.0000000000'
        phi_deg           = $row.phi_deg
        mesh_level        = $row.mesh_level
        Fx_B0_raw_mN      = $row.Fx_B0_raw_mN
        Fx_B1_raw_mN      = $row.Fx_B1_raw_mN
        Fx_B2_raw_mN      = $row.Fx_B2_raw_mN
        Fx_hold_corr_mN   = $row.Fx_hold_corr_mN
        DeltaFx_ball_mN   = $row.DeltaFx_ball_mN
        Fx_total_corr_mN  = $row.Fx_total_corr_mN
        Fy_total_corr_mN  = $row.Fy_total_corr_mN
        Fz_total_corr_mN  = $row.Fz_total_corr_mN
        elements          = $row.elements
        DOF               = $row.DOF
        min_quality       = $row.min_quality
        same_mesh_verified = $row.same_mesh_verified
        release_candidate = $row.release_candidate
        status            = $row.status
      }
    }
  }
) | Sort-Object { [double]$_.z_sphere_mm }, { [double]$_.phi_deg }

if ($rows.Count -ne 54 -or ($rows | Where-Object status -ne 'SUCCESS').Count -ne 0) {
  throw 'The alpha=20 scan is incomplete or contains failed rows.'
}

$rawPath = Join-Path $DataDir 'modelB_alpha20_sparse.csv'
$rows | Export-Csv -LiteralPath $rawPath -NoTypeInformation

$summary = @(
  foreach ($group in ($rows | Group-Object z_sphere_mm | Sort-Object { [double]$_.Name })) {
    $angles = @($group.Group | ForEach-Object { [double]$_.phi_deg } | Sort-Object)
    $expectedAngles = @(0, 45, 90, 135, 180, 225, 270, 315, 360)
    if ($angles.Count -ne $expectedAngles.Count -or
        (Compare-Object -ReferenceObject $expectedAngles -DifferenceObject $angles).Count -ne 0) {
      throw "Incomplete phase set for z=$($group.Name): $($angles -join ', ')."
    }
    $maximum = $group.Group | Sort-Object { [double]$_.Fx_total_corr_mN } -Descending | Select-Object -First 1
    $minimum = $group.Group | Sort-Object { [double]$_.Fx_total_corr_mN } | Select-Object -First 1
    $allNegative = [double]$maximum.Fx_total_corr_mN -lt 0
    [pscustomobject]@{
      z_sphere_mm                = $group.Name
      alpha_deg                  = '20.0000000000'
      Fmin_mN                    = $minimum.Fx_total_corr_mN
      Fmax_mN                    = $maximum.Fx_total_corr_mN
      phi_at_Fmax_deg            = $maximum.phi_deg
      phi_at_Fmin_deg            = $minimum.phi_deg
      DeltaFx_ball_at_Fmax_mN    = $maximum.DeltaFx_ball_mN
      Fx_hold_corr_mN            = $maximum.Fx_hold_corr_mN
      all_sampled_phi_negative   = $allNegative.ToString()
      release_candidate          = (-not $allNegative).ToString()
      elements                   = $maximum.elements
      min_quality                = $maximum.min_quality
      zero_360_closure_error_mN  = [math]::Abs(
        [double]($group.Group | Where-Object { [double]$_.phi_deg -eq 0 }).Fx_total_corr_mN -
        [double]($group.Group | Where-Object { [double]$_.phi_deg -eq 360 }).Fx_total_corr_mN
      ).ToString('G12', [Globalization.CultureInfo]::InvariantCulture)
      status                     = 'SUCCESS'
    }
  }
)

$summaryPath = Join-Path $DataDir 'modelB_alpha20_Fmax_summary.csv'
$summary | Export-Csv -LiteralPath $summaryPath -NoTypeInformation

$alpha0 = Import-Csv -LiteralPath (Join-Path $DataDir 'modelB_x26_macro_Fmax_summary.csv')
$comparison = foreach ($row in $summary) {
  $baseline = $alpha0 | Where-Object { [double]$_.z_sphere_mm -eq [double]$row.z_sphere_mm }
  if ($null -eq $baseline) {
    throw "Missing alpha=0 baseline for z=$($row.z_sphere_mm)."
  }
  [pscustomobject]@{
    z_sphere_mm             = $row.z_sphere_mm
    alpha0_Fmax_mN          = $baseline.Fmax_mN
    alpha20_Fmax_mN         = $row.Fmax_mN
    alpha20_minus_alpha0_mN = ([double]$row.Fmax_mN - [double]$baseline.Fmax_mN).ToString(
      'G12', [Globalization.CultureInfo]::InvariantCulture
    )
    alpha0_phi_at_Fmax_deg  = $baseline.phi_at_Fmax_deg
    alpha20_phi_at_Fmax_deg = $row.phi_at_Fmax_deg
    alpha0_release_candidate = $baseline.release_candidate
    alpha20_release_candidate = $row.release_candidate
    status                  = 'SUCCESS'
  }
}

$comparisonPath = Join-Path $DataDir 'modelB_alpha0_alpha20_Fmax_comparison.csv'
$comparison | Export-Csv -LiteralPath $comparisonPath -NoTypeInformation

Write-Output "Exported $($rows.Count) raw rows to $rawPath"
Write-Output "Exported $($summary.Count) summary rows to $summaryPath"
Write-Output "Exported $($comparison.Count) comparison rows to $comparisonPath"
