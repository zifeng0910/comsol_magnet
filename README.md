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

## Latest observed status

The final runner log records z=140 as successful and all-negative; later points may still be running when this snapshot was collected.
