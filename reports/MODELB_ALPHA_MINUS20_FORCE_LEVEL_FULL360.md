# Model B alpha=-20 deg force-level full360

## Scope and fixed method

Only alpha=-20 deg was calculated in this run. Previously completed critical heights (108–111 mm) were reused, not recomputed. The model retained x_sphere=26 mm, gap=0.30 mm, existing materials/magnetization/coordinates, Stationary, PARDISO, stol=1e-6, LOCAL_M03, and the same-mesh B0/B1/B2 force differences.

Stage A used 22 real phi=90 records in the 60–106 mm search band, including reused prior same-configuration rows, and stopped at the first newly measured point reaching +0.45 mN. Stage B validated 303/303 real COMSOL rows: three measured heights with 101 points each at phi=0:3.6:360. All rows are SUCCESS and same_mesh_verified=true.

## Critical region reused from prior run

| z (mm) | Fmax (mN) | phi@Fmax (deg) |
|---:|---:|---:|
| 108 | 0.011364377 | 90 |
| 109 | 0.006157430 | 90 |
| 110 | 0.001567561 | 90 |
| 111 | -0.001736987 | 90 |

## Selected force levels

| Target (mN) | z (mm) | measured phi=90 (mN) | selection error (mN) | full360 Fmax (mN) | phi@Fmax (deg) | Fmin (mN) | phi@Fmin (deg) |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 0.10 | 94 | 0.094320603 | 0.005679397 | 0.094320603 | 90 | -0.210432341 | 270 |
| 0.20 | 82 | 0.210857003 | 0.010857003 | 0.210857003 | 90 | -0.220692718 | 270 |
| 0.40 | 68 | 0.411602228 | 0.011602228 | 0.411602227 | 90 | -0.147633563 | 270 |

The measured force increases as z decreases along the z>=60 mm branch. The phi=90 prescan served only to select discrete measured heights; final peak and trough values below come from the 101 real COMSOL phase solves at each selected height, with no interpolation.

## 0/360 closure

| z (mm) | Fx total (mN) | DeltaFx ball (mN) | Fy total (mN) | Fz total (mN) |
|---:|---:|---:|---:|---:|
| 68 | 6.100e-10 | 6.090e-10 | 6.200e-11 | 2.173e-09 |
| 82 | 5.848e-10 | 5.840e-10 | 7.200e-11 | 4.951e-09 |
| 94 | 2.815e-10 | 2.815e-10 | 5.320e-11 | 1.008e-09 |

Maximum 0/360 closure error over raw/corrected force columns is 4.951e-09 mN. The 0 and 360 degree endpoints are both separately solved COMSOL points.

## Figures and data

- Figure A, critical transition reused: `figures/modelB_alpha_minus20_critical_Fx_vs_phi.png`.
- Figure B, force-level evolution: `figures/modelB_alpha_minus20_force_level_evolution_Fx_vs_phi.png` and `.pdf`.
- Figure C, measured force envelope: `figures/modelB_alpha_minus20_force_level_Fmax_vs_z.png` and `.pdf`.
- Full-cycle data: `data/modelB_alpha_minus20_force_levels_full360.csv` and `data/modelB_alpha_minus20_force_levels_full360_summary.csv`.
- Real phi=90 screening points: `data/modelB_alpha_minus20_force_level_prescan.csv`.

Figure C distinguishes prior 13-phase sampled maxima, new single-phase prescreen values, the 101-point critical curves, and the selected 101-point force-level curves. Lines are guides to the eye; no polynomial fit or interpolated z was used.
