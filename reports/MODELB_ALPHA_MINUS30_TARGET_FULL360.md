# Model B alpha=-30 deg target-matched full360

## Scope and method

This run contains alpha=-30 deg only. The previously accepted alpha=-20 deg Fmax values were used as targets; their z coordinates were not reused. Four new alpha=-30 deg heights were selected from real COMSOL phi=90 prescan/refinement values, then each selected height was scanned at 101 real phases (`phi=0:3.6:360`). The model used x_sphere=26 mm, gap=0.30 mm, the existing 50 mm sphere/steel/cylinder/air geometry, existing Br and magnetization definitions, Stationary/PARDISO, stol=1e-6, LOCAL_M03 mesh, and same-mesh B0/B1/B2 differentials.

The final dataset contains 404/404 SUCCESS rows. Each height has 101 phases, same_mesh_verified=true, and the workbook contains a single `full360` sheet with 404 data rows. The first 47 points at z=113 mm were preserved after interruption; the continuation rebuilt the mesh and verified element count, minimum quality, DOF, and B0/B1 force baselines before appending missing phases.

## Matched peaks

| Reference alpha=-20 Fmax (mN) | alpha=-30 z (mm) | Measured Fmax (mN) | phi at Fmax (deg) | Signed error (mN) | Positive / negative phases |
|---:|---:|---:|---:|---:|---:|
| -0.001736987 | 113 | -0.002947655 | 90 | -0.001210668 | 0 / 101 |
| 0.094320603 | 96.5 | 0.093701594 | 90 | -0.000619009 | 41 / 60 |
| 0.146497737 | 91 | 0.142765196 | 90 | -0.003732541 | 47 / 54 |
| 0.210857003 | 85 | 0.208951966 | 90 | -0.001905038 | 56 / 45 |

All four peaks occur at phi=90 deg, as in the alpha=-20 reference data. Every measured peak is slightly below its target. The mean absolute peak error is 0.001866814 mN and the largest is 0.003732541 mN (2.55% of the 0.146497737 mN target). For the near-zero negative target, absolute error is more informative than relative error: the measured curve remains negative at all 101 phases, with Fmax=-0.002947655 mN.

As z decreases from 113 to 85 mm, Fmax rises monotonically from a small negative value to +0.208951966 mN. The three positive-Fmax groups retain both positive and negative phases, so they represent mixed-sign cycles rather than wholly positive curves. Their minima are approximately -0.154 to -0.172 mN at phi=270 deg.

## Phase closure and quality

| z (mm) | Fx closure (mN) | DeltaFx closure (mN) | Fy closure (mN) | Fz closure (mN) | Max closure (mN) |
|---:|---:|---:|---:|---:|---:|
| 113 | 5.840e-11 | 6.270e-11 | 7.640e-11 | 6.176e-10 | 6.176e-10 |
| 96.5 | 1.136e-10 | 1.136e-10 | 1.610e-11 | 5.160e-10 | 5.160e-10 |
| 91 | 1.877e-10 | 1.880e-10 | 4.110e-11 | 9.907e-10 | 9.907e-10 |
| 85 | 3.949e-10 | 3.950e-10 | 9.600e-11 | 8.990e-10 | 8.990e-10 |

All 0 and 360 deg endpoints are retained as measured COMSOL rows; the maximum closure across Fx_total_corr, DeltaFx_ball, Fy_total_corr, and Fz_total_corr is below 1.0e-9 mN. No curve points were interpolated.

## Conclusion

The four new alpha=-30 deg heights reproduce the four alpha=-20 deg peak levels closely in absolute force, and clearly retain the requested one-negative/three-positive Fmax pattern. The three positive peaks increase in the expected order as z decreases. These data support a comparative full-cycle figure; the z=91 mm peak has the largest target mismatch, but remains within 0.00374 mN. The curves are suitable for comparative plotting, subject to the usual mesh-convergence and physical-model limitations of this stationary magnetic-force setup.

## Deliverables

- `data/modelB_alpha_minus30_target_prescan.csv`
- `data/modelB_alpha_minus30_selected_targets.csv`
- `data/modelB_alpha_minus30_target_full360.csv`
- `data/modelB_alpha_minus30_target_full360_summary.csv`
- `data/modelB_alpha_minus30_selected4_full360.xlsx`
