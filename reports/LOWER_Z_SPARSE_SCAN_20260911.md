# Model B lower-z same-mesh sparse scan — 2026-09-11

## Scope and method

The existing Model B implementation from commit `cb90a93` was reused without
changing the 50 mm ball, x position, gap, materials, magnetic field formula,
or Force Calculation object.  Only four new heights were added: 110, 100, 90
and 80 mm.  Each height used one geometry build and one mesh build.  B0, B1
and B2 were then solved on that same mesh:

`Fx_hold_corr = B1 - B0`

`Fx_total_corr = B2 - B0`

`DeltaFx_ball = B2 - B1`

The nine independent stationary poses were 0, 45, 90, 135, 180, 225, 270,
315 and 360 degrees.  The 360-degree solve was retained for closure checking
and excluded from the cycle mean as the duplicate phase.

## Results

| z (mm) | B0 raw self-force (mN) | Fx_hold_corr (mN) | Fx_min (mN) | Fx_max (mN) | phi at Fx_max | mean Fx_corr (mN) | DeltaFx_ball at max (mN) | closure 360−0 (mN) | status |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| 110 | 0.713492 | -0.124493 | -0.259996 | -0.095744 | 90° | -0.177875 | +0.028748 | 3.2e-11 | MAGNETICALLY_HELD |
| 100 | 0.477937 | -0.124380 | -0.313060 | -0.111991 | 90° | -0.212538 | +0.012389 | 9.3e-11 | MAGNETICALLY_HELD |
| 90 | 1.693451 | -0.125093 | -0.397815 | -0.099860 | 90° | -0.248866 | +0.025233 | -2.0e-11 | MAGNETICALLY_HELD |
| 80 | 0.750859 | -0.124214 | -0.508076 | -0.162555 | 90° | -0.335397 | -0.038341 | 4.0e-12 | MAGNETICALLY_HELD |

All 44 rows in the new CSV completed successfully: four B0 rows, four B1
rows and 36 B2 phase rows.  Every sampled height has `Fx_max < 0`; therefore
no first negative-to-positive crossing was found in 80–110 mm.  The requested
phi=90° check is also negative at all four heights and is the sampled maximum
at each height.

The corrected holding force remains stable across the new heights, ranging
from -0.124214 to -0.125093 mN, a span of about 0.000879 mN.  The raw B0
self-force is not stable with height, so it must not be interpreted as the
physical holding force.  The corrected differential remains the controlled
comparison quantity.  This scan therefore supports a negative-force trend in
the sampled 80–110 mm range, but it does not establish a continuous-height
threshold and does not replace a denser scan if a crossing is required.

## Mesh and solver evidence

The solver was configured before every solve as Stationary + PARDISO with
`stol=1e-6`; the source scalar reference point was retained/verified.  Mesh
statistics were:

| z (mm) | elements | DOF | minimum quality |
|---:|---:|---:|---:|
| 110 | 61,022 | 81,932 | 0.2016 |
| 100 | 61,016 | 81,925 | 0.2016 |
| 90 | 61,008 | 81,915 | 0.2016 |
| 80 | 61,004 | 81,910 | 0.2016 |

The apparent constancy of the mesh metrics does not by itself prove identical
mesh topology; the script nevertheless keeps B0/B1/B2 on the same per-height
mesh, which is the relevant differential-force control.

## Files

- `data/modelB_lower_z_same_mesh_sparse.csv`
- `data/modelB_lower_z_same_mesh_summary.csv`
- `scripts/RunModelBSameMeshDifferential.java`
- `scripts/RunModelBLowerZ.ps1`
- `scripts/plot_modelB_lower_z.py`
- `figures/modelB_lower_z_Fxmax_vs_z.{png,svg,pdf,tiff}`
- `figures/modelB_lower_z_Fx_vs_phi.{png,svg,pdf,tiff}`
- `work/modelB_lower_z_same_mesh_20260911/runner.stdout.log`

The two plotting sources passed the static figure validator with zero failed
checks.  The plots use direct simulated points only; no interpolation or
extrapolation was added.
