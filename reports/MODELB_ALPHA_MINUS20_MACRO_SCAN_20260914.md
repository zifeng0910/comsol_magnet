# Model B alpha=-20° macro scan

## Scope and validation

Only alpha=-20° was calculated in this task. Fixed parameters are x_sphere=26 mm, gap=0.30 mm, the existing 50 mm magnetic sphere/steel/cylinder/air model, the existing Br and magnetization formula, LOCAL_M03, Stationary, PARDISO, and stol=1e-6.

The scan contains 13 z values (40–160 mm in 10 mm steps) and 13 real COMSOL phases per height (`phi=0:30:360`). The same-mesh differential is `Fx_hold_corr=B1-B0`, `DeltaFx_ball=B2-B1`, and `Fx_total_corr=B2-B0`.

Validation passed: 169/169 pose rows, 13/13 z summaries, all SUCCESS, all alpha=-20°, all `same_mesh_verified=true`.

## Fmax(z) summary

| z (mm) | Fmax (mN) | phi at Fmax (deg) | Fmin (mN) | phi at Fmin (deg) | DeltaFx at Fmax (mN) | Fx hold (mN) |
|---:|---:|---:|---:|---:|---:|---:|
| 40 | 3.147877830 | 270 | -0.613330173 | 90 | 3.275992392 | -0.128114562 |
| 50 | 0.758916802 | 270 | 0.519130016 | 90 | 0.887099044 | -0.128182242 |
| 60 | 0.547305578 | 90 | 0.022797903 | 270 | 0.675583574 | -0.128277997 |
| 70 | 0.375253877 | 90 | -0.163269858 | 270 | 0.503342793 | -0.128088916 |
| 80 | 0.234379950 | 90 | -0.217797838 | 270 | 0.362405328 | -0.128025378 |
| 90 | 0.128734513 | 90 | -0.220420083 | 270 | 0.256937540 | -0.128203027 |
| 100 | 0.053738577 | 90 | -0.205203037 | 270 | 0.181890152 | -0.128151575 |
| 110 | 0.001567561 | 90 | -0.186987398 | 270 | 0.129651692 | -0.128084132 |
| 120 | -0.034619984 | 90 | -0.174104936 | 270 | 0.093524049 | -0.128144033 |
| 130 | -0.060557932 | 90 | -0.162508912 | 270 | 0.067557660 | -0.128115592 |
| 140 | -0.079259530 | 90 | -0.153227511 | 270 | 0.048907517 | -0.128167047 |
| 150 | -0.093163247 | 90 | -0.146019967 | 270 | 0.034984803 | -0.128148049 |
| 160 | -0.103425970 | 90 | -0.138853963 | 270 | 0.024692733 | -0.128118703 |

## Findings

1. The largest sampled Fmax is 3.147877830 mN at z=40 mm and phi=270°.
2. Fmax is positive from z=40 through 110 mm and becomes negative at z=120 mm. Linear interpolation of the two bracketing macro points places the upper Fmax sign crossing near z=110.43 mm.
3. The complete force curve is positive at every sampled phase only at z=50, 60 mm. At z=40 mm the phase curve has both signs; at z=70–110 mm it has a positive window around phi=90°; z>=120 mm is fully negative on the sampled phase grid.
4. The holding differential is nearly constant because the sphere is disabled in B0/B1: mean=-0.128140096 mN, SD=0.000059864 mN, range=[-0.128277997, -0.128025378] mN.
5. No second positive force lobe is observed in the 30° macro grid. The z=40 lobe peaks near phi=270°, z=50 also peaks at 270°, and z>=60 peaks at 90°; this phase reversal is a real orientation effect, not a sign correction.

## Positive sampled-phase windows

| z (mm) | positive sampled phases |
|---:|:---|
| 40 | 0, 30, 150, 180, 210, 240, 270, 300, 330, 360 deg sampled |
| 50 | all sampled phases |
| 60 | all sampled phases |
| 70 | 0, 30, 60, 90, 120, 150, 180, 360 deg sampled |
| 80 | 0, 30, 60, 90, 120, 150, 180, 360 deg sampled |
| 90 | 30, 60, 90, 120, 150 deg sampled |
| 100 | 60, 90, 120 deg sampled |
| 110 | 90 deg sampled |
| 120 | none |
| 130 | none |
| 140 | none |
| 150 | none |
| 160 | none |

## Next calculation gate

The upper transition is bracketed by z=110 mm (Fmax=+0.001568 mN) and z=120 mm (Fmax=-0.034620 mN). The next refinement should therefore use z=112.5 and 115 mm (optionally 117.5 mm) before choosing the 5–7 representative heights for the final 0:10:360 full curves. The macro scan itself is complete and is intentionally not mixed with alpha=0 or alpha=+20 jobs.
