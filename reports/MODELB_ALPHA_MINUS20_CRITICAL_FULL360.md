# Model B alpha=-20 deg critical full360

## Scope and fixed method

This run contains alpha=-20 deg only. Alpha=0 and alpha=+20 were not run or merged into these deliverables. The fixed model is x_sphere=26 mm, gap=0.30 mm, the existing 50 mm sphere/steel/cylinder/air model, existing Br/magnetization and coordinates, Stationary, PARDISO, stol=1e-6, and the same-mesh differential: Fx_hold_corr=B1-B0, DeltaFx_ball=B2-B1, Fx_total_corr=B2-B0.

Phase A validated 9/9 real COMSOL phi=90 prescan points at z=108--116 mm. Phase B validated 404/404 real COMSOL full-cycle rows: four z values and 101 phases per z (`phi=0:3.6:360`). All rows are SUCCESS and same_mesh_verified=true.

## Final four heights

| z (mm) | role | Fmax (mN) | phi at Fmax (deg) | Fmin (mN) | phi at Fmin (deg) | positive phases | negative phases |
|---:|:---|---:|---:|---:|---:|---:|---:|
| 108 | more clearly positive | 0.011364377 | 90 | -0.193407955 | 270 | 15.0 | 86.0 |
| 109 | moderately positive | 0.006157430 | 90 | -0.190044878 | 270 | 11.0 | 90.0 |
| 110 | closest positive to zero | 0.001567561 | 90 | -0.186987398 | 270 | 5.0 | 96.0 |
| 111 | first negative | -0.001736987 | 90 | -0.188755674 | 270 | 0.0 | 101.0 |

The three positive-side curves are z=108, 109, and 110 mm. Their Fmax values descend toward zero as height increases; z=110 mm is the near-critical positive curve. z=111 mm is the first negative-side curve and is only slightly below zero, so the four curves show the requested progressive downward shift without selecting a deep negative branch.

## Phase A sign selection

| z (mm) | Fx_total_corr at phi=90 (mN) |
|---:|---:|
| 108 | 0.011364378 |
| 109 | 0.006157430 |
| 110 | 0.001567561 |
| 111 | -0.001736987 |
| 112 | -0.006787782 |
| 113 | -0.010516002 |
| 114 | -0.013649585 |
| 115 | -0.018156658 |
| 116 | -0.021339913 |

The closest positive prescan point is z=110 mm (0.001567561 mN). The first negative point is z=111 mm (-0.001736987 mN). The auxiliary linear estimate from the measured 110/111 bracket is z=110.4744 mm; it is only a locator and was not used to fabricate any full-cycle point.

## 0/360 closure

| z (mm) | Fx_total closure (mN) | DeltaFx closure (mN) | Fy closure (mN) | Fz closure (mN) |
|---:|---:|---:|---:|---:|
| 108 | 1.199e-10 | 1.199e-10 | 2.570e-11 | 4.411e-10 |
| 109 | 1.881e-10 | 1.881e-10 | 1.900e-11 | 1.417e-09 |
| 110 | 6.190e-11 | 6.190e-11 | 5.000e-13 | 3.466e-10 |
| 111 | 6.730e-11 | 6.730e-11 | 1.860e-11 | 1.331e-09 |

Maximum listed closure error across Fx_total, DeltaFx_ball, Fy, and Fz is 1.417e-09 mN. The 0° and 360° rows are both retained as real COMSOL solves; no periodic interpolation or point synthesis was used.

## Deliverables

- `data/modelB_alpha_minus20_critical_phi90_prescan.csv`
- `data/modelB_alpha_minus20_critical_full360.csv`
- `data/modelB_alpha_minus20_critical_full360_summary.csv`
- `figures/modelB_alpha_minus20_critical_Fx_vs_phi.png` and `.pdf`
- `figures/modelB_alpha_minus20_critical_Fmax_vs_z.png` and `.pdf`
- `figures/modelB_alpha_minus20_critical_DeltaFx_vs_phi.png` and `.pdf`

These four 101-point curves are suitable for a paper figure: they are full real COMSOL cycles with explicit B0/B1/B2 same-mesh bookkeeping, sign-side selection near the measured transition, phase closure checks, and no interpolated force samples.
