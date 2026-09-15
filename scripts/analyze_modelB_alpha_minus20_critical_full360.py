"""Validate, plot, and report the alpha=-20 critical full360 run."""
from pathlib import Path
import math

import matplotlib.pyplot as plt
import pandas as pd

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "data"
FIG = ROOT / "figures"
REPORT = ROOT / "reports" / "MODELB_ALPHA_MINUS20_CRITICAL_FULL360.md"
PRESCAN = DATA / "modelB_alpha_minus20_critical_phi90_prescan.csv"
FULL = DATA / "modelB_alpha_minus20_critical_full360.csv"
SUMMARY = DATA / "modelB_alpha_minus20_critical_full360_summary.csv"
EXPECTED_Z = [108.0, 109.0, 110.0, 111.0]


def f(x):
    return f"{float(x):.9f}"


def near(a, b, tol=1e-8):
    return abs(float(a) - float(b)) <= tol


def validate_prescan(df):
    assert len(df) == 9, len(df)
    assert set(df.alpha_deg.astype(float)) == {-20.0}
    assert set(df.phi_deg.astype(float)) == {90.0}
    assert df.status.eq("SUCCESS").all()
    assert df.same_mesh_verified.astype(str).str.lower().eq("true").all()
    return df.sort_values("z_sphere_mm").reset_index(drop=True)


def validate_full(df):
    assert len(df) == 404, len(df)
    assert set(df.alpha_deg.astype(float)) == {-20.0}
    assert df.status.eq("SUCCESS").all()
    assert df.same_mesh_verified.astype(str).str.lower().eq("true").all()
    zs = sorted(df.z_sphere_mm.astype(float).unique())
    assert zs == EXPECTED_Z, zs
    expected_phi = [round(3.6 * i, 10) for i in range(101)]
    for z in EXPECTED_Z:
        g = df[abs(df.z_sphere_mm.astype(float) - z) < 1e-9].copy()
        assert len(g) == 101, (z, len(g))
        got = g.sort_values("phi_deg").phi_deg.astype(float).tolist()
        assert all(abs(a - b) < 1e-7 for a, b in zip(got, expected_phi)), (z, got[:3], got[-3:])
    return df


def closure_table(full):
    rows = []
    for z in EXPECTED_Z:
        g = full[abs(full.z_sphere_mm.astype(float) - z) < 1e-9]
        first = g.iloc[(g.phi_deg.astype(float) - 0.0).abs().argmin()]
        last = g.iloc[(g.phi_deg.astype(float) - 360.0).abs().argmin()]
        fields = ["Fx_B2_raw_mN", "Fx_hold_corr_mN", "DeltaFx_ball_mN", "Fx_total_corr_mN", "Fy_total_corr_mN", "Fz_total_corr_mN"]
        errs = {field: abs(float(first[field]) - float(last[field])) for field in fields}
        rows.append({"z": z, **errs})
    return rows


def summary_from_full(full):
    rows = []
    for z in EXPECTED_Z:
        g = full[abs(full.z_sphere_mm.astype(float) - z) < 1e-9].copy()
        g["Fx_total_corr_mN"] = g.Fx_total_corr_mN.astype(float)
        imax, imin = g.Fx_total_corr_mN.idxmax(), g.Fx_total_corr_mN.idxmin()
        rows.append({
            "z": z,
            "Fmax": float(g.loc[imax, "Fx_total_corr_mN"]),
            "phi_max": float(g.loc[imax, "phi_deg"]),
            "Fmin": float(g.loc[imin, "Fx_total_corr_mN"]),
            "phi_min": float(g.loc[imin, "phi_deg"]),
            "positive": int((g.Fx_total_corr_mN > 0).sum()),
            "negative": int((g.Fx_total_corr_mN < 0).sum()),
            "hold": float(g.Fx_hold_corr_mN.astype(float).iloc[0]),
        })
    return pd.DataFrame(rows)


def save_figures(prescan, full, derived):
    FIG.mkdir(exist_ok=True)
    plt.rcParams.update({"font.size": 10, "axes.grid": True, "grid.alpha": 0.25})
    colors = ["#1b9e77", "#d95f02", "#7570b3", "#e7298a"]

    fig, ax = plt.subplots(figsize=(8.2, 5.0))
    for color, z in zip(colors, EXPECTED_Z):
        g = full[abs(full.z_sphere_mm.astype(float) - z) < 1e-9].sort_values("phi_deg")
        ax.plot(g.phi_deg.astype(float), g.Fx_total_corr_mN.astype(float), lw=1.7, color=color, label=f"z={z:g} mm")
    ax.axhline(0, color="black", lw=0.8)
    ax.set(xlabel="phi (deg)", ylabel="Fx_total_corr (mN)", title="Model B alpha=-20 deg: critical full 360 curves")
    ax.set_xlim(0, 360)
    ax.legend(frameon=False, ncol=2)
    fig.tight_layout()
    fig.savefig(FIG / "modelB_alpha_minus20_critical_Fx_vs_phi.png", dpi=220)
    fig.savefig(FIG / "modelB_alpha_minus20_critical_Fx_vs_phi.pdf")
    plt.close(fig)

    fig, ax = plt.subplots(figsize=(7.6, 4.8))
    pz = prescan.z_sphere_mm.astype(float)
    pf = prescan.Fx_total_corr_mN.astype(float)
    ax.plot(pz, pf, "o-", color="#555555", lw=1.2, ms=4, label="phi=90 prescan (real COMSOL)")
    for color, z in zip(colors, EXPECTED_Z):
        r = derived[derived.z == z].iloc[0]
        ax.scatter([z], [r.Fmax], s=55, color=color, zorder=4, label=f"full360 z={z:g}")
    ax.axhline(0, color="black", lw=0.8)
    ax.axvspan(110, 111, color="#fdae61", alpha=0.18)
    ax.set(xlabel="z_sphere (mm)", ylabel="Fmax / Fx(phi=90) (mN)", title="Critical sign transition, alpha=-20 deg")
    ax.legend(frameon=False, fontsize=8)
    fig.tight_layout()
    fig.savefig(FIG / "modelB_alpha_minus20_critical_Fmax_vs_z.png", dpi=220)
    fig.savefig(FIG / "modelB_alpha_minus20_critical_Fmax_vs_z.pdf")
    plt.close(fig)

    fig, ax = plt.subplots(figsize=(8.2, 5.0))
    for color, z in zip(colors, EXPECTED_Z):
        g = full[abs(full.z_sphere_mm.astype(float) - z) < 1e-9].sort_values("phi_deg")
        ax.plot(g.phi_deg.astype(float), g.DeltaFx_ball_mN.astype(float), lw=1.5, color=color, label=f"z={z:g} mm")
    ax.axhline(0, color="black", lw=0.8)
    ax.set(xlabel="phi (deg)", ylabel="DeltaFx_ball (mN)", title="Ball contribution across the real full 360 cycles")
    ax.set_xlim(0, 360)
    ax.legend(frameon=False, ncol=2)
    fig.tight_layout()
    fig.savefig(FIG / "modelB_alpha_minus20_critical_DeltaFx_vs_phi.png", dpi=220)
    fig.savefig(FIG / "modelB_alpha_minus20_critical_DeltaFx_vs_phi.pdf")
    plt.close(fig)


def main():
    prescan = validate_prescan(pd.read_csv(PRESCAN))
    full = validate_full(pd.read_csv(FULL))
    summary_file = pd.read_csv(SUMMARY)
    assert len(summary_file) == 4
    derived = summary_from_full(full)
    for _, row in summary_file.iterrows():
        z = float(row.z_sphere_mm)
        d = derived[derived.z == z].iloc[0]
        assert near(row.Fmax_mN, d.Fmax, 1e-8)
        assert near(row.Fmin_mN, d.Fmin, 1e-8)
        assert int(row.phase_points) == 101
    closure = closure_table(full)
    max_closure = max(max(row[field] for row in closure) for field in closure[0] if field != "z")
    positive = prescan[prescan.Fx_total_corr_mN.astype(float) > 0].copy()
    negative = prescan[prescan.Fx_total_corr_mN.astype(float) < 0].copy()
    closest = positive.iloc[(positive.Fx_total_corr_mN.astype(float)).abs().argmin()]
    first_negative = negative.iloc[0]
    bracket_hi = prescan[prescan.z_sphere_mm.astype(float) == 110.0].iloc[0]
    a, b = float(bracket_hi.Fx_total_corr_mN), float(first_negative.Fx_total_corr_mN)
    crossing = 110.0 + (0.0 - a) / (b - a)
    save_figures(prescan, full, derived)

    lines = [
        "# Model B alpha=-20 deg critical full360",
        "",
        "## Scope and fixed method",
        "",
        "This run contains alpha=-20 deg only. Alpha=0 and alpha=+20 were not run or merged into these deliverables. The fixed model is x_sphere=26 mm, gap=0.30 mm, the existing 50 mm sphere/steel/cylinder/air model, existing Br/magnetization and coordinates, Stationary, PARDISO, stol=1e-6, and the same-mesh differential: Fx_hold_corr=B1-B0, DeltaFx_ball=B2-B1, Fx_total_corr=B2-B0.",
        "",
        f"Phase A validated {len(prescan)}/9 real COMSOL phi=90 prescan points at z=108--116 mm. Phase B validated {len(full)}/404 real COMSOL full-cycle rows: four z values and 101 phases per z (`phi=0:3.6:360`). All rows are SUCCESS and same_mesh_verified=true.",
        "",
        "## Final four heights",
        "",
        "| z (mm) | role | Fmax (mN) | phi at Fmax (deg) | Fmin (mN) | phi at Fmin (deg) | positive phases | negative phases |",
        "|---:|:---|---:|---:|---:|---:|---:|---:|",
    ]
    roles = {108.0: "more clearly positive", 109.0: "moderately positive", 110.0: "closest positive to zero", 111.0: "first negative"}
    for _, row in derived.iterrows():
        lines.append(f"| {row.z:g} | {roles[row.z]} | {f(row.Fmax)} | {row.phi_max:g} | {f(row.Fmin)} | {row.phi_min:g} | {row.positive} | {row.negative} |")
    lines.extend([
        "",
        "The three positive-side curves are z=108, 109, and 110 mm. Their Fmax values descend toward zero as height increases; z=110 mm is the near-critical positive curve. z=111 mm is the first negative-side curve and is only slightly below zero, so the four curves show the requested progressive downward shift without selecting a deep negative branch.",
        "",
        "## Phase A sign selection",
        "",
        "| z (mm) | Fx_total_corr at phi=90 (mN) |",
        "|---:|---:|",
    ])
    for _, row in prescan.iterrows():
        lines.append(f"| {float(row.z_sphere_mm):g} | {f(row.Fx_total_corr_mN)} |")
    lines.extend([
        "",
        f"The closest positive prescan point is z={float(closest.z_sphere_mm):g} mm ({f(closest.Fx_total_corr_mN)} mN). The first negative point is z={float(first_negative.z_sphere_mm):g} mm ({f(first_negative.Fx_total_corr_mN)} mN). The auxiliary linear estimate from the measured 110/111 bracket is z={crossing:.4f} mm; it is only a locator and was not used to fabricate any full-cycle point.",
        "",
        "## 0/360 closure",
        "",
        "| z (mm) | Fx_total closure (mN) | DeltaFx closure (mN) | Fy closure (mN) | Fz closure (mN) |",
        "|---:|---:|---:|---:|---:|",
    ])
    for row in closure:
        lines.append(f"| {row['z']:g} | {row['Fx_total_corr_mN']:.3e} | {row['DeltaFx_ball_mN']:.3e} | {row['Fy_total_corr_mN']:.3e} | {row['Fz_total_corr_mN']:.3e} |")
    lines.extend([
        "",
        f"Maximum listed closure error across Fx_total, DeltaFx_ball, Fy, and Fz is {max_closure:.3e} mN. The 0° and 360° rows are both retained as real COMSOL solves; no periodic interpolation or point synthesis was used.",
        "",
        "## Deliverables",
        "",
        "- `data/modelB_alpha_minus20_critical_phi90_prescan.csv`",
        "- `data/modelB_alpha_minus20_critical_full360.csv`",
        "- `data/modelB_alpha_minus20_critical_full360_summary.csv`",
        "- `figures/modelB_alpha_minus20_critical_Fx_vs_phi.png` and `.pdf`",
        "- `figures/modelB_alpha_minus20_critical_Fmax_vs_z.png` and `.pdf`",
        "- `figures/modelB_alpha_minus20_critical_DeltaFx_vs_phi.png` and `.pdf`",
        "",
        "These four 101-point curves are suitable for a paper figure: they are full real COMSOL cycles with explicit B0/B1/B2 same-mesh bookkeeping, sign-side selection near the measured transition, phase closure checks, and no interpolated force samples.",
        "",
    ])
    REPORT.parent.mkdir(exist_ok=True)
    REPORT.write_text("\n".join(lines), encoding="utf-8")
    print(f"validated prescan={len(prescan)} full_rows={len(full)} z={EXPECTED_Z} max_closure={max_closure:.3e} crossing={crossing:.6f}")


if __name__ == "__main__":
    main()
