# Same-mesh differential-force validation — 2026-09-11

## Scope

This run used the existing COMSOL 6.3 models and did not modify the original
raw scan files or source MPH files.  The purpose was to separate the stable
change caused by the steel or external ball from the mesh-dependent raw
Force Calculation self-force.

The force expression was kept as `mfnc.Forcex_force_magnet`, read from a
temporary Global evaluation with `getData()`, and converted from N to mN.
The active solver sequence was configured before each solve as Stationary +
PARDISO with `stol=1e-6`.  Failure states were not converted to zero.

## Model A — steel holding force

For every gap, geometry and mesh were built once.  The same mesh was then
used for two states:

- `OFF`: steel domain changed to air-like relative permeability (`mur=1`,
  no remanent source);
- `ON`: the captured original steel configuration was restored;
- corrected holding force: `Fx_hold = Fx_ON - Fx_OFF`.

| gap (mm) | h≈0.03 mm (mN) | h≈0.02 mm (mN) | relative difference |
|---:|---:|---:|---:|
| 0.25 | -0.167545334 | -0.167807061 | 0.156% |
| 0.26 | -0.158445532 | -0.159096422 | 0.411% |
| 0.27 | -0.149868339 | -0.150695933 | 0.552% |
| 0.28 | -0.142299509 | -0.142720533 | 0.296% |
| 0.29 | -0.135104259 | -0.135583749 | 0.355% |
| 0.30 | -0.128360012 | -0.128802075 | 0.344% |

All 12 cases succeeded and reported `same_mesh_verified=true`.  The maximum
cross-mesh difference is 0.552%, below the temporary 2% engineering gate.
At gap=0.30 mm, the corrected values differ by 0.000442 mN, whereas the raw
ON values previously changed strongly with mesh.  This supports using the
same-mesh differential as the Model A comparison quantity; it does not prove
that the raw surface net force is spatially converged.

The two mesh families had different practical costs.  The h≈0.03 mm family
was about 194k elements / 260k DOF, while the h≈0.02 mm family was about
423k elements / 565k DOF.  Minimum quality remained positive in every case.

## Model B — sparse fixed-pose differential decomposition

The 50 mm ball, `x_sphere=26 mm`, `alpha=30°`, gap=0.30 mm, cylinder, steel,
outer air and force object were retained.  At each height one geometry and
one mesh were reused for all states:

- `B0`: ball OFF, steel OFF;
- `B1`: ball OFF, steel ON;
- `B2`: ball ON, steel ON;
- `Fx_hold_corr = B1 - B0`;
- `Fx_total_corr = B2 - B0`;
- `DeltaFx_ball = B2 - B1`.

The sampled phases were 0, 45, 90, 135, 180, 225, 270, 315 and 360 degrees.
The 360-degree solve was independent and used only for closure checking.

| z (mm) | B0 raw Fx (mN) | B1 raw Fx (mN) | corrected hold (mN) | corrected-cycle min (mN) | corrected-cycle max (mN) | mean, excluding duplicate 360° (mN) |
|---:|---:|---:|---:|---:|---:|---:|
| 120 | +0.209087 | +0.084234 | -0.124853 | -0.219998 | -0.085210 | -0.152606 |
| 140 | +0.780910 | +0.656021 | -0.124889 | -0.174809 | -0.108164 | -0.141486 |
| 150 | +0.409281 | +0.284669 | -0.124612 | -0.163570 | -0.121059 | -0.142314 |

All 33 rows succeeded.  The corrected B2 force is negative at every sampled
phase and height.  This is a sparse, fixed-pose quasi-static result, not a
proof for every angle or a dynamic launch result.  The 0°/360° closure errors
were approximately `8.4e-11`, `7.1e-11` and `-3.3e-11` mN for z=120, 140 and
150 mm respectively.

The raw B0 value changes considerably with z even though it is intended as a
background/self-force reference.  In contrast, the corrected B1−B0 holding
force is stable within 0.000277 mN across the three heights.  This is direct
evidence that raw Force Calculation output contains a position-dependent
numerical self-force in this formulation; the differential removes that
common component for this controlled comparison.  It is not a license to
subtract the correction from unrelated meshes or configurations.

## Configuration notes and limitations

- The source B model was audited before the run: final domains were air=1,
  steel=2, cylinder=3 and ball=4; the ball bbox moved with `z_sphere` while
  the cylinder and steel bboxes stayed fixed.
- The source cylinder and ball use separate mfnc features and the source
  remaining-flux values read from the model were approximately 1.43 T and
  1.46 T respectively.  The steel feature uses the model's actual
  `UNS S30415 [solid]` material source and `mur_mat=from_mat`; its measured
  relative permeability was 1.01.  Experimental material validity remains
  unconfirmed.
- An initial Model B attempt failed because `zsp1` had no effective scalar
  reference and because source ON properties were captured after a
  normalization step.  The final script captures the source first, verifies
  or sets the reference point, and creates a fresh stationary sequence for
  each constitutive state/pose while retaining the same geometry and mesh.
- Raw Force Calculation sensitivity and the unsuccessful volume-force,
  force-probe and envelope branches remain preserved as historical evidence.
  They were not overwritten or silently folded into these corrected tables.

## Files

- `data/holding_force_same_mesh_diff_025_030.csv`
- `data/modelB_same_mesh_differential_sparse.csv`
- `scripts/RunModelASameMeshDifferential.java`
- `scripts/RunModelBSameMeshDifferential.java`
- `scripts/InspectModelBPhysics.java`
- `scripts/plot_same_mesh_differential.py`
- `figures/modelA_same_mesh_differential_vs_gap.{png,svg,pdf,tiff}`
- `figures/modelB_same_mesh_corrected_force_vs_phi.{png,svg,pdf,tiff}`
- `logs/modelA_same_mesh_diff_h003_20260911.log`
- `logs/modelA_same_mesh_diff_h002_20260911.log`
- `logs/modelB_same_mesh_diff_20260911.log`
- `logs/modelB_source_physics_audit_20260911.log`

The plotting source was checked with the Nature Figure static validator:
18 checks passed, 0 failed.  The remaining warnings concern journal-specific
figure width and using 320 dpi rather than a 600 dpi submission default.
