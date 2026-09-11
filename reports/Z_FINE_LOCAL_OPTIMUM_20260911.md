# Model B z fine scan and local optimum — 2026-09-11

## Scope

This run retained the validated Model B configuration from commit `8b552e7`:
gap=0.30 mm, 50 mm diameter ball, x_sphere=26 mm, alpha=30°, the existing
magnetization formula, N52 and steel settings, external air, Stationary,
PARDISO and `stol=1e-6`.  The same-mesh differential definitions were kept:

`Fx_hold_corr = Fx_B1 - Fx_B0`

`DeltaFx_ball = Fx_B2 - Fx_B1`

`Fx_total_corr = Fx_B2 - Fx_B0`

The z fine scan used one geometry and one mesh per height and calculated only
B0, B1 and B2 at phi=90°.  The subsequent local phase scan used the same
three-state method at the best z candidate and evaluated 0–360° in 45° steps.

## z fine scan at phi=90°

| z (mm) | B0 raw (mN) | B1 raw (mN) | hold corr (mN) | B2 raw (mN) | ball delta (mN) | total corr (mN) |
|---:|---:|---:|---:|---:|---:|---:|
| 110.0 | 0.713492 | 0.588999 | -0.124493 | 0.617747 | +0.028748 | -0.095744 |
| 112.5 | -0.075404 | -0.200210 | -0.124807 | -0.162375 | +0.037835 | -0.086971 |
| 115.0 | -0.650094 | -0.774812 | -0.124718 | -0.757587 | +0.017225 | -0.107493 |
| 117.5 | -0.453216 | -0.578172 | -0.124956 | -0.545196 | +0.032976 | -0.091979 |
| 120.0 | 0.209087 | 0.084234 | -0.124853 | 0.123876 | +0.039642 | **-0.085210** |
| 122.5 | 1.078081 | 0.953628 | -0.124452 | 0.969233 | +0.015605 | -0.108848 |
| 125.0 | -0.946364 | -1.071036 | -0.124672 | -1.050072 | +0.020964 | -0.103708 |
| 127.5 | 0.279395 | 0.154258 | -0.125137 | 0.177191 | +0.022933 | -0.102204 |
| 130.0 | 0.068196 | -0.056488 | -0.124684 | -0.034769 | +0.021719 | -0.102965 |
| 132.5 | 0.627032 | 0.502004 | -0.125028 | 0.525521 | +0.023517 | -0.101511 |
| 135.0 | -0.784592 | -0.909073 | -0.124481 | -0.898338 | +0.010735 | -0.113746 |
| 137.5 | 1.330090 | 1.205863 | -0.124227 | 1.207167 | +0.001304 | -0.122922 |
| 140.0 | 0.780910 | 0.656021 | -0.124889 | 0.672746 | +0.016725 | -0.108164 |

The largest (least negative) directly simulated value is at z=120 mm:
`Fx_total_corr=-0.085210451 mN`.  No interpolation was used to select this
point.  The holding differential remained near -0.125 mN; its range over the
13 points was -0.124227 to -0.125137 mN.  The raw B0 value continued to show
position-dependent numerical self-force and was not used as the selection
criterion.

## Local phase scan at z=120 mm

The independent 0–360° phase scan gave:

- `Fx_min=-0.219998493 mN` at 270°;
- `Fx_max=-0.085210451 mN` at 90°;
- mean over 0–315° (360° excluded as duplicate) `-0.152605778 mN`;
- 360°−0° closure `-1.88e-10 mN`.

All nine sampled phase values were negative.  This is a sparse phase check,
not a proof for all continuous angles.  It confirms that phi=90° is the
worst sampled phase at the selected z.

## h≈0.02 mm candidate check

The requested high-resolution candidate check was attempted using the source
model's native `hauto=1` mesh, which generated approximately 1.24M elements.
The B0 stationary PARDISO solve consumed nearly all available system memory
and made no progress to a readable Force Calculation result.  The process was
stopped after confirming resource exhaustion; no h≈0.02 mm force value was
accepted or substituted.  The original failure log and status CSV are kept
separately.  Therefore h≈0.03 mm remains the only valid resolution for the
local optimum conclusion.

## Decision

Within the directly simulated 110–140 mm grid, z=120 mm is the current local
optimum for maximizing corrected Fx at phi=90°, but it is still negative.
Under the current x position, alpha, gap and material settings, adjusting z
alone has not produced a positive quasi-static release candidate.  The next
design change should be considered only after deciding how to obtain a
resource-feasible h≈0.02 mm model; the high-resolution comparison cannot be
claimed complete from the failed resource-limited run.

## Files

- `data/modelB_z110_140_phi90_fine.csv`
- `data/modelB_local_optimum_phi_scan.csv`
- `data/modelB_local_optimum_h002_status.csv`
- `scripts/RunModelBSameMeshDifferential.java`
- `scripts/RunModelBZFinePhi90.ps1`
- `scripts/RunModelBLocalOptimumPhi.ps1`
- `scripts/RunModelBLocalOptimumH002Retry.ps1`
- `scripts/plot_modelB_z_fine_and_optimum.py`
- `figures/modelB_z110_140_Fx_total_corr_phi90.{png,svg,pdf,tiff}`
- `figures/modelB_z110_140_DeltaFx_ball_phi90.{png,svg,pdf,tiff}`
- `figures/modelB_local_optimum_z120_Fx_vs_phi.{png,svg,pdf,tiff}`
- `logs/modelB_z110_140_phi90_fine_20260911.log`
- `logs/modelB_local_optimum_phi_20260911.log`
- `logs/modelB_local_optimum_h002_retry_20260911.log`
