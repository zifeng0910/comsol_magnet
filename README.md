# comsol_magnet

COMSOL 6.3 fixed-field magnetic-force scan records.

## Included

- COMSOL magnetic simulation lessons and force-direction analysis in Markdown.
- High-precision z-scan runner and solver progress logs.
- Per-point COMSOL progress, stdout, and stderr logs for z=120--150 mm.

## Scan definition

- `z_sphere`: 120, 125, 130, 135, 140, 145, 150 mm
- Near-field mesh: mesh level 1 on the steel plate, robot magnet, and moving magnet domains.
- Far-field air: coarse mesh retained.
- Each point: maximum two attempts.
- Full-negative criterion: `Fmax = max(Fx_mN) < 0` over one 360-degree cycle.

## Important exclusion

`.mph` models are intentionally excluded from this repository. The local COMSOL models are large binary files and should be transferred separately or through Git LFS after authentication is configured.

## Refined 138--145 scan

The completed 138--145 fixed-field scan is archived under
`results/fixed_field_138_145/` and `logs/fixed_field_refined_scan_138_145/`.
It contains all 37-angle CSV files, the aggregate summary, solver progress logs,
and retry history. Interpretation remains provisional: z=141 is all-negative,
z=142 and z=143 are positive, while z=144 and z=145 are all-negative. The
141/142 sign change is under a dedicated accuracy audit and is not yet treated
as a confirmed physical boundary.

The preserved audit plan is documented in
`docs/ACCURACY_AUDIT_141_142_STATUS.md`. Reproducible Java/PowerShell runners
are under `scripts/`. `.mph` models remain intentionally excluded.
