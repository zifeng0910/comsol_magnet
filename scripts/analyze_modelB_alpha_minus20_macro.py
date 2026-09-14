"""Validate and summarize the alpha=-20 macro scan."""
from pathlib import Path
import math
import pandas as pd

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "data"
REPORT = ROOT / "reports" / "MODELB_ALPHA_MINUS20_MACRO_SCAN_20260914.md"
PHI = [0.0, 30.0, 60.0, 90.0, 120.0, 150.0, 180.0, 210.0, 240.0, 270.0, 300.0, 330.0, 360.0]
Z = [40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 100.0, 110.0, 120.0, 130.0, 140.0, 150.0, 160.0]


def fmt(x):
    return f"{float(x):.9f}"


def phase_window(g):
    p = g[g.Fx_total_corr_mN.astype(float) > 0].phi_deg.astype(float).tolist()
    if not p:
        return "none"
    if len(p) == len(PHI):
        return "all sampled phases"
    return ", ".join(f"{x:g}" for x in p) + " deg sampled"


def main():
    phi_path = DATA / "modelB_alpha_minus20_macro_phi.csv"
    sum_path = DATA / "modelB_alpha_minus20_macro_Fmax_summary.csv"
    phi = pd.read_csv(phi_path)
    summary = pd.read_csv(sum_path)
    assert len(phi) == 169, len(phi)
    assert len(summary) == 13, len(summary)
    assert set(phi.alpha_deg.astype(float)) == {-20.0}
    assert set(phi.z_sphere_mm.astype(float)) == set(Z)
    assert all(abs(a - b) < 1e-8 for a, b in zip(sorted(phi.phi_deg.astype(float).unique()), PHI))
    assert phi.status.eq("SUCCESS").all()
    assert phi.same_mesh_verified.astype(str).str.lower().eq("true").all()
    assert summary.status.eq("SUCCESS").all()

    summary = summary.sort_values("z_sphere_mm").reset_index(drop=True)
    fmax = summary.Fmax_mN.astype(float)
    fmin = summary.Fmin_mN.astype(float)
    peak = summary.loc[fmax.idxmax()]
    positive_fmax = summary[fmax > 0]
    all_positive = summary[fmin > 0]
    all_negative = summary[fmax < 0]
    transition = None
    for i in range(len(summary) - 1):
        a, b = float(summary.loc[i, "Fmax_mN"]), float(summary.loc[i + 1, "Fmax_mN"])
        if a > 0 >= b:
            z0, z1 = float(summary.loc[i, "z_sphere_mm"]), float(summary.loc[i + 1, "z_sphere_mm"])
            transition = z0 + (0.0 - a) * (z1 - z0) / (b - a)
            break
    hold = phi.Fx_hold_corr_mN.astype(float)

    lines = [
        "# Model B alpha=-20° macro scan",
        "",
        "## Scope and validation",
        "",
        "Only alpha=-20° was calculated in this task. Fixed parameters are x_sphere=26 mm, gap=0.30 mm, the existing 50 mm magnetic sphere/steel/cylinder/air model, the existing Br and magnetization formula, LOCAL_M03, Stationary, PARDISO, and stol=1e-6.",
        "",
        "The scan contains 13 z values (40–160 mm in 10 mm steps) and 13 real COMSOL phases per height (`phi=0:30:360`). The same-mesh differential is `Fx_hold_corr=B1-B0`, `DeltaFx_ball=B2-B1`, and `Fx_total_corr=B2-B0`.",
        "",
        f"Validation passed: {len(phi)}/169 pose rows, {len(summary)}/13 z summaries, all SUCCESS, all alpha=-20°, all `same_mesh_verified=true`.",
        "",
        "## Fmax(z) summary",
        "",
        "| z (mm) | Fmax (mN) | phi at Fmax (deg) | Fmin (mN) | phi at Fmin (deg) | DeltaFx at Fmax (mN) | Fx hold (mN) |",
        "|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for _, r in summary.iterrows():
        lines.append(f"| {float(r.z_sphere_mm):g} | {fmt(r.Fmax_mN)} | {float(r.phi_at_Fmax_deg):g} | {fmt(r.Fmin_mN)} | {float(r.phi_at_Fmin_deg):g} | {fmt(r.DeltaFx_ball_at_Fmax_mN)} | {fmt(r.Fx_hold_corr_mN)} |")

    lines.extend([
        "",
        "## Findings",
        "",
        f"1. The largest sampled Fmax is {fmt(peak.Fmax_mN)} mN at z={float(peak.z_sphere_mm):g} mm and phi={float(peak.phi_at_Fmax_deg):g}°.",
        f"2. Fmax is positive from z=40 through {float(positive_fmax.z_sphere_mm.iloc[-1]):g} mm and becomes negative at z={float(all_negative.z_sphere_mm.iloc[0]):g} mm. Linear interpolation of the two bracketing macro points places the upper Fmax sign crossing near z={transition:.2f} mm.",
        f"3. The complete force curve is positive at every sampled phase only at z={', '.join(f'{x:g}' for x in all_positive.z_sphere_mm.astype(float))} mm. At z=40 mm the phase curve has both signs; at z=70–110 mm it has a positive window around phi=90°; z>=120 mm is fully negative on the sampled phase grid.",
        f"4. The holding differential is nearly constant because the sphere is disabled in B0/B1: mean={hold.mean():.9f} mN, SD={hold.std(ddof=1):.9f} mN, range=[{hold.min():.9f}, {hold.max():.9f}] mN.",
        "5. No second positive force lobe is observed in the 30° macro grid. The z=40 lobe peaks near phi=270°, z=50 also peaks at 270°, and z>=60 peaks at 90°; this phase reversal is a real orientation effect, not a sign correction.",
        "",
        "## Positive sampled-phase windows",
        "",
        "| z (mm) | positive sampled phases |",
        "|---:|:---|",
    ])
    for z in Z:
        g = phi[abs(phi.z_sphere_mm.astype(float) - z) < 1e-8]
        lines.append(f"| {z:g} | {phase_window(g)} |")

    lines.extend([
        "",
        "## Next calculation gate",
        "",
        "The upper transition is bracketed by z=110 mm (Fmax=+0.001568 mN) and z=120 mm (Fmax=-0.034620 mN). The next refinement should therefore use z=112.5 and 115 mm (optionally 117.5 mm) before choosing the 5–7 representative heights for the final 0:10:360 full curves. The macro scan itself is complete and is intentionally not mixed with alpha=0 or alpha=+20 jobs.",
        "",
    ])
    REPORT.write_text("\n".join(lines), encoding="utf-8")
    print(f"validated poses={len(phi)} summaries={len(summary)} peak_z={peak.z_sphere_mm} peak_Fmax={peak.Fmax_mN}")


if __name__ == "__main__":
    main()
