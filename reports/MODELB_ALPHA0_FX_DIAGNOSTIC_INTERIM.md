# Model B Fx diagnostic (interim)

## Finding

The repeated value near `-0.128 mN` in the monitoring table is not the phase-max force. It is the last sampled row (`phi=360 deg`) of `Fx_total_corr`. At `phi=360 deg`, the ball magnetization returns to the `phi=0 deg` direction, and the ball contribution is close to zero, so the total is close to the holding term.

The holding term is defined as `Fx_hold_corr = B1-B0`, where the steel is toggled with the sphere disabled. Because the sphere is disabled in both states, changing `z_sphere` does not change this differential except for numerical noise. Its approximately constant value near `-0.128 mN` is therefore expected.

## Completed alpha=0 heights

| z (mm) | Fx_hold_corr (mN) | Fmax (mN) | phi at Fmax (deg) | Fmin (mN) | phi at Fmin (deg) | Fx at phi=360 (mN) |
|---:|---:|---:|---:|---:|---:|---:|
| 100.0 | -0.128151575 | +0.009681753 | 90 | -0.265886957 | 270 | -0.127977333 |
| 102.5 | -0.128120611 | -0.001621901 | 90 | -0.254539223 | 270 | -0.128350904 |
| 105.0 | -0.128157745 | -0.009797755 | 90 | -0.246453358 | 270 | -0.127980312 |
| 107.5 | -0.128175465 | -0.016056589 | 90 | -0.240241014 | 270 | -0.128265831 |
| 110.0 | -0.128084132 | -0.027730641 | 90 | -0.228392286 | 270 | -0.128139459 |
| 112.5 | -0.128147151 | -0.034716306 | 90 | -0.221541643 | 270 | -0.128088550 |

The actual phase-max force crosses zero between z=100 and 102.5 mm; it is not flat. The current full study is still incomplete for z=115, 117.5, 120 and the alpha=-20 group.

## Monitoring convention

The PowerShell monitor now reports both `Fmax/phi_at_Fmax` and the last sampled `Fx` so that the holding-force-like `phi=360 deg` row is not mistaken for the release envelope.
