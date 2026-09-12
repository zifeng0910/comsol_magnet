# Model B: alpha=-20, 0, +20 directionality comparison

## Scope and invariant model definition

This comparison changes only the signed inclination angle `alpha`. The magnetic-sphere direction remains

```text
ux = sin(alpha)
uy = cos(alpha)*cos(phi)
uz = -cos(alpha)*sin(phi)
```

All cases use `x_sphere=26 mm`, `gap=0.30 mm`, a 50 mm sphere, the same steel and cylinder definitions, `Br=1.46 T`, the existing Force Calculation, Stationary/PARDISO with `stol=1e-6`, and same-mesh B0/B1/B2 differencing. The macro and transition scans use LOCAL_M03; selected validation points use LOCAL_M02.

## Alpha=-20 results

| z (mm) | Fmax (mN) | Fmin (mN) | phi at Fmax (deg) | Ball contribution at Fmax (mN) | State |
|---:|---:|---:|---:|---:|:---|
| 60 | +0.547306 | +0.022798 | 90 | +0.675584 | positive at all sampled phases |
| 80 | +0.234380 | -0.217798 | 90 | +0.362405 | release candidate |
| 100 | +0.053739 | -0.205203 | 90 | +0.181890 | release candidate |
| 105 | +0.025548 | -0.196829 | 90 | +0.153706 | release candidate |
| 110 | +0.001568 | -0.186987 | 90 | +0.129652 | near-threshold positive |
| 115 | -0.018157 | -0.180190 | 90 | +0.109989 | near-threshold negative |
| 120 | -0.034620 | -0.174105 | 90 | +0.093524 | negative |
| 140 | -0.079260 | -0.153228 | 90 | +0.048908 | negative |
| 160 | -0.103426 | -0.138854 | 90 | +0.024693 | negative |

Within the tested 60-160 mm interval, alpha=-20 has a positive-Fmax release window from 60 through 110 mm. The upper positive-to-negative transition is bracketed at approximately **110-115 mm**. No claim is made about a lower boundary below 60 mm because that range was not scanned in this stage.

The largest force is **+0.547306 mN at z=60 mm, phi=90 deg**. The sampled minimum at the same height remains positive, so z=60 is positive for the complete sparse phase cycle rather than only at the optimum phase.

## Signed-alpha comparison

At common macro heights, alpha=-20 increases Fmax relative to alpha=0 by +0.394538 mN at 60 mm, +0.121516 mN at 80 mm, and +0.044057 mN at 100 mm. Its upper transition moves from alpha=0's 100-105 mm bracket to 110-115 mm, an approximately 10 mm upward shift at the present 5 mm resolution.

Alpha=+20 is negative at every tested height. Its least-negative sampled peak is -0.035768 mN at z=90 mm, whereas alpha=-20 reaches +0.547306 mN. At z=60 mm the signed-angle contrast is 0.823116 mN (+0.547306 versus -0.275811 mN). Therefore the release response is strongly directional with respect to signed `alpha`; it is not determined only by `|alpha|`.

The ball-contribution comparison gives the direct mechanism in the force decomposition. At z=60 mm, the contribution at Fmax changes from +0.675584 mN for alpha=-20 to +0.281046 mN for alpha=0 and -0.147533 mN for alpha=+20. The invariant holding contribution remains near -0.1281 mN, so the signed inclination primarily changes whether the external sphere contribution overcomes that holding branch.

For all three groups over the shared 60-160 mm range, the sampled Fmax phase is 90 deg. There is no observed optimum-phase switch to 270 deg. At high z, the ball contribution decays and all three curves trend toward the holding-force branch: at 160 mm their Fmax values are -0.103426, -0.109267, and -0.117382 mN for alpha=-20, 0, and +20, respectively, while `Fx_hold_corr` is about -0.12812 mN.

## LOCAL_M02 validation

| Point | LOCAL_M03 Fx total (mN) | LOCAL_M02 Fx total (mN) | Sign agreement | M02 elements | M02 DOF |
|:---|---:|---:|:---:|---:|---:|
| z=60 mm, phi=90 deg | +0.547306 | +0.534200 | yes | 1,935,733 | 2,582,448 |
| z=115 mm, phi=90 deg | -0.018157 | -0.018013 | yes | 1,935,588 | 2,582,343 |

LOCAL_M02 supports both the strongest positive result and the negative sign at the upper-transition endpoint. The peak-point magnitude changes by about 2.4%, while the transition-point value changes by only 0.000143 mN and remains negative.

## Numerical checks

- Each z uses one geometry build and one mesh build, followed by one B0, one B1, and nine B2 solves.
- The alpha=-20 LOCAL_M03 meshes contain 871,414-872,902 elements with minimum quality 0.2103-0.2206.
- The maximum 0/360 phase-closure error is `5.96e-10 mN`.
- `Fx_hold_corr` remains approximately `-0.1281 mN` across all heights.
- z=160 is negative, so the conditional higher-z/outer-air expansion check was not triggered.

## Data and figures

- `data/modelB_alpha_minus20_sparse.csv`: six macro heights, 54 direct COMSOL phase rows.
- `data/modelB_alpha_minus20_refinement.csv`: three transition heights, 27 direct COMSOL phase rows.
- `data/modelB_alpha_minus20_Fmax_summary.csv`: combined nine-height extrema and numerical checks.
- `data/modelB_alpha_minus20_M02_validation.csv`: two direct LOCAL_M02 checks.
- `data/modelB_alpha_minus20_0_plus20_comparison.csv`: common-height three-group comparison.
- `figures/modelB_alpha_minus20_0_plus20_Fmax_vs_z.*`: Fmax comparison with zero-force reference.
- `figures/modelB_alpha_minus20_0_plus20_ball_contribution_vs_z.*`: ball contribution at Fmax.
- `figures/modelB_alpha_minus20_0_plus20_phi_at_Fmax_vs_z.*`: actual optimum phase.

All plotted markers are direct COMSOL results. Connecting lines are guides to the eye only; no polynomial or other fitted curve is used.
