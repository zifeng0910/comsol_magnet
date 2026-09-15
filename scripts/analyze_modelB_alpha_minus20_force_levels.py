"""Validate and plot the alpha=-20 force-level full-cycle comparison."""
from __future__ import annotations

from pathlib import Path

import matplotlib as mpl
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "data"
FIG = ROOT / "figures"
WORK = ROOT / "work" / "modelB_alpha_minus20_force_levels_20260915"
FULL = DATA / "modelB_alpha_minus20_force_levels_full360.csv"
SUMMARY = DATA / "modelB_alpha_minus20_force_levels_full360_summary.csv"
SELECTED = DATA / "modelB_alpha_minus20_force_level_selected_targets.csv"
PRESCAN = DATA / "modelB_alpha_minus20_force_level_prescan.csv"
CRITICAL_FULL = DATA / "modelB_alpha_minus20_critical_full360.csv"
CRITICAL_SUMMARY = DATA / "modelB_alpha_minus20_critical_full360_summary.csv"
MACRO_SUMMARY = DATA / "modelB_alpha_minus20_macro_Fmax_summary.csv"
REPORT = ROOT / "reports" / "MODELB_ALPHA_MINUS20_FORCE_LEVEL_FULL360.md"
EXPECTED_PHI = np.round(np.arange(101) * 3.6, 10)

mpl.rcParams.update({
    "font.family": "sans-serif",
    "font.sans-serif": ["Arial", "Helvetica", "DejaVu Sans", "sans-serif"],
    "svg.fonttype": "none",
    "pdf.fonttype": 42,
    "font.size": 7,
    "axes.labelsize": 8,
    "axes.titlesize": 9,
    "legend.fontsize": 7,
    "axes.spines.right": False,
    "axes.spines.top": False,
    "axes.linewidth": 0.8,
})


def validate_full() -> tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame]:
    full = pd.read_csv(FULL)
    summary = pd.read_csv(SUMMARY)
    selected = pd.read_csv(SELECTED)
    assert len(full) == 303, len(full)
    assert len(summary) == 3, len(summary)
    assert len(selected) == 3, len(selected)
    assert set(full.alpha_deg.astype(float)) == {-20.0}
    assert full.status.eq("SUCCESS").all()
    assert full.same_mesh_verified.astype(str).str.lower().eq("true").all()
    zs = sorted(full.z_sphere_mm.astype(float).unique())
    assert zs == sorted(selected.selected_z_mm.astype(float).tolist()), (zs, selected)
    for z in zs:
        group = full[np.isclose(full.z_sphere_mm.astype(float), z)].sort_values("phi_deg")
        assert len(group) == 101, (z, len(group))
        assert np.allclose(group.phi_deg.astype(float), EXPECTED_PHI, atol=1e-7, rtol=0)
    return full, summary, selected


def derive_summary(full: pd.DataFrame) -> pd.DataFrame:
    out = []
    for z, group in full.groupby("z_sphere_mm", sort=True):
        g = group.copy()
        g["Fx_total_corr_mN"] = g.Fx_total_corr_mN.astype(float)
        imax, imin = g.Fx_total_corr_mN.idxmax(), g.Fx_total_corr_mN.idxmin()
        out.append({
            "z_sphere_mm": float(z),
            "Fmax_mN": float(g.loc[imax, "Fx_total_corr_mN"]),
            "phi_at_Fmax_deg": float(g.loc[imax, "phi_deg"]),
            "Fmin_mN": float(g.loc[imin, "Fx_total_corr_mN"]),
            "phi_at_Fmin_deg": float(g.loc[imin, "phi_deg"]),
        })
    return pd.DataFrame(out)


def closure_errors(full: pd.DataFrame) -> dict[float, dict[str, float]]:
    fields = [
        "Fx_B0_raw_mN", "Fx_B1_raw_mN", "Fx_B2_raw_mN", "Fx_hold_corr_mN",
        "DeltaFx_ball_mN", "Fx_total_corr_mN", "Fy_total_corr_mN", "Fz_total_corr_mN",
    ]
    result = {}
    for z, group in full.groupby("z_sphere_mm", sort=True):
        a = group[np.isclose(group.phi_deg.astype(float), 0.0)].iloc[0]
        b = group[np.isclose(group.phi_deg.astype(float), 360.0)].iloc[0]
        result[float(z)] = {field: abs(float(a[field]) - float(b[field])) for field in fields}
    return result


def save_figures(full: pd.DataFrame, derived: pd.DataFrame, selected: pd.DataFrame,
                 prescan: pd.DataFrame, critical: pd.DataFrame) -> None:
    FIG.mkdir(exist_ok=True)
    selected = selected.sort_values("target_force_mN")
    colors = {0.10: "#0072B2", 0.20: "#D55E00", 0.40: "#009E73"}

    fig, ax = plt.subplots(figsize=(7.1, 4.35))
    ref = critical[np.isclose(critical.z_sphere_mm.astype(float), 110.0)].sort_values("phi_deg")
    ax.plot(ref.phi_deg.astype(float), ref.Fx_total_corr_mN.astype(float), color="#525B65", lw=1.6,
            label="Near-zero reference, z=110 mm")
    for _, target in selected.iterrows():
        z = float(target.selected_z_mm)
        force = float(target.target_force_mN)
        group = full[np.isclose(full.z_sphere_mm.astype(float), z)].sort_values("phi_deg")
        ax.plot(group.phi_deg.astype(float), group.Fx_total_corr_mN.astype(float), color=colors[force], lw=1.7,
                label=f"Target {force:.2f} mN, z={z:g} mm")
    ax.axhline(0, color="#222222", lw=0.7)
    ax.set(xlim=(0, 360), xlabel=r"Phase, $\phi$ (deg)", ylabel=r"$F_{x,\mathrm{total,corr}}$ (mN)",
           title="Force-level evolution across full rotation")
    ax.set_xticks(np.arange(0, 361, 60))
    ax.legend(loc="best", ncol=2, frameon=False)
    fig.tight_layout()
    for ext, opts in [("png", {"dpi": 600}), ("pdf", {})]:
        fig.savefig(FIG / f"modelB_alpha_minus20_force_level_evolution_Fx_vs_phi.{ext}", bbox_inches="tight", **opts)
    plt.close(fig)

    macro = pd.read_csv(MACRO_SUMMARY)
    macro = macro[(macro.alpha_deg.astype(float) == -20.0) & macro.z_sphere_mm.astype(float).between(60, 160)].copy()
    new_scan = prescan[prescan.z_sphere_mm.astype(float).isin(WORK_SCAN_ZS)].copy()
    full360 = derived.copy()
    critical_summary = pd.read_csv(CRITICAL_SUMMARY)
    critical_summary = critical_summary[critical_summary.z_sphere_mm.astype(float).between(108, 111)].copy()
    # Keep the legacy z=110 observation visible beneath the higher-density critical result.
    fig, ax = plt.subplots(figsize=(7.1, 4.45))
    ax.plot(macro.z_sphere_mm.astype(float), macro.Fmax_mN.astype(float), linestyle="none", marker="o", ms=4.3,
            mfc="white", mec="#697783", mew=1, label="Prior 13-phase real scans")
    ax.plot(new_scan.z_sphere_mm.astype(float), new_scan.Fx_total_corr_mN.astype(float), linestyle="none", marker="s", ms=4.0,
            color="#2878B5", label="New real $\phi=90^\circ$ prescan")
    ax.plot(critical_summary.z_sphere_mm.astype(float), critical_summary.Fmax_mN.astype(float), linestyle="none", marker="D", ms=4.8,
            color="#6B5B95", label="Critical full360 (101 points)")
    ax.plot(full360.z_sphere_mm.astype(float), full360.Fmax_mN.astype(float), linestyle="none", marker="*", ms=9,
            color="#CC4C02", label="Selected force-level full360")
    ax.axhline(0, color="#222222", lw=0.7)
    ax.axvspan(110, 111, color="#8A8F98", alpha=0.12, lw=0)
    ax.annotate("critical crossing\n110–111 mm", xy=(110.5, 0), xytext=(119, 0.13),
                arrowprops={"arrowstyle": "-", "color": "#525B65", "lw": 0.8}, color="#40474F", fontsize=7)
    for _, target in selected.iterrows():
        z = float(target.selected_z_mm)
        row = full360[np.isclose(full360.z_sphere_mm.astype(float), z)].iloc[0]
        ax.annotate(f"~{float(target.target_force_mN):.1f} mN: z={z:g}",
                    (z, float(row.Fmax_mN)), xytext=(7, 7), textcoords="offset points", fontsize=6.5,
                    color=colors[round(float(target.target_force_mN), 2)])
    ax.set(xlim=(58, 162), ylim=(-0.13, 0.62), xlabel="Sphere height, z (mm)", ylabel=r"$F_{x,\max}$ (mN)",
           title="Measured force envelope versus sphere height")
    ax.legend(loc="upper right", frameon=False, fontsize=6.5)
    ax.text(0.01, 0.015, "Prescan squares show the measured $F_x$ at $\phi=90^\circ$; stars are full-cycle maxima.",
            transform=ax.transAxes, fontsize=6.2, color="#40474F")
    fig.tight_layout()
    for ext, opts in [("png", {"dpi": 600}), ("pdf", {})]:
        fig.savefig(FIG / f"modelB_alpha_minus20_force_level_Fmax_vs_z.{ext}", bbox_inches="tight", **opts)
    plt.close(fig)


WORK_SCAN_ZS = [106, 104, 102, 98, 96, 94, 92, 88, 86, 84, 82, 78, 76, 74, 72, 68, 66, 64, 62]


def write_report(full: pd.DataFrame, derived: pd.DataFrame, selected: pd.DataFrame,
                 closures: dict[float, dict[str, float]], critical_summary: pd.DataFrame) -> None:
    prescan = pd.read_csv(PRESCAN)
    lines = [
        "# Model B alpha=-20 deg force-level full360",
        "",
        "## Scope and fixed method",
        "",
        "Only alpha=-20 deg was calculated in this run. Previously completed critical heights (108–111 mm) were reused, not recomputed. The model retained x_sphere=26 mm, gap=0.30 mm, existing materials/magnetization/coordinates, Stationary, PARDISO, stol=1e-6, LOCAL_M03, and the same-mesh B0/B1/B2 force differences.",
        "",
        f"Stage A used {len(prescan)} real phi=90 records in the 60–106 mm search band, including reused prior same-configuration rows, and stopped at the first newly measured point reaching +0.45 mN. Stage B validated {len(full)}/303 real COMSOL rows: three measured heights with 101 points each at phi=0:3.6:360. All rows are SUCCESS and same_mesh_verified=true.",
        "",
        "## Critical region reused from prior run",
        "",
        "| z (mm) | Fmax (mN) | phi@Fmax (deg) |",
        "|---:|---:|---:|",
    ]
    for _, row in critical_summary.sort_values("z_sphere_mm").iterrows():
        lines.append(f"| {float(row.z_sphere_mm):g} | {float(row.Fmax_mN):.9f} | {float(row.phi_at_Fmax_deg):g} |")
    lines.extend(["", "## Selected force levels", "", "| Target (mN) | z (mm) | measured phi=90 (mN) | selection error (mN) | full360 Fmax (mN) | phi@Fmax (deg) | Fmin (mN) | phi@Fmin (deg) |", "|---:|---:|---:|---:|---:|---:|---:|---:|"])
    for _, target in selected.sort_values("target_force_mN").iterrows():
        row = derived[np.isclose(derived.z_sphere_mm.astype(float), float(target.selected_z_mm))].iloc[0]
        lines.append(f"| {float(target.target_force_mN):.2f} | {float(target.selected_z_mm):g} | {float(target.actual_Fx_phi90_mN):.9f} | {float(target.absolute_error_mN):.9f} | {float(row.Fmax_mN):.9f} | {float(row.phi_at_Fmax_deg):g} | {float(row.Fmin_mN):.9f} | {float(row.phi_at_Fmin_deg):g} |")
    lines.extend([
        "",
        "The measured force increases as z decreases along the z>=60 mm branch. The phi=90 prescan served only to select discrete measured heights; final peak and trough values below come from the 101 real COMSOL phase solves at each selected height, with no interpolation.",
        "",
        "## 0/360 closure",
        "",
        "| z (mm) | Fx total (mN) | DeltaFx ball (mN) | Fy total (mN) | Fz total (mN) |",
        "|---:|---:|---:|---:|---:|",
    ])
    for z, err in closures.items():
        lines.append(f"| {z:g} | {err['Fx_total_corr_mN']:.3e} | {err['DeltaFx_ball_mN']:.3e} | {err['Fy_total_corr_mN']:.3e} | {err['Fz_total_corr_mN']:.3e} |")
    max_err = max(value for fields in closures.values() for value in fields.values())
    lines.extend([
        "",
        f"Maximum 0/360 closure error over raw/corrected force columns is {max_err:.3e} mN. The 0 and 360 degree endpoints are both separately solved COMSOL points.",
        "",
        "## Figures and data",
        "",
        "- Figure A, critical transition reused: `figures/modelB_alpha_minus20_critical_Fx_vs_phi.png`.",
        "- Figure B, force-level evolution: `figures/modelB_alpha_minus20_force_level_evolution_Fx_vs_phi.png` and `.pdf`.",
        "- Figure C, measured force envelope: `figures/modelB_alpha_minus20_force_level_Fmax_vs_z.png` and `.pdf`.",
        "- Full-cycle data: `data/modelB_alpha_minus20_force_levels_full360.csv` and `data/modelB_alpha_minus20_force_levels_full360_summary.csv`.",
        "- Real phi=90 screening points: `data/modelB_alpha_minus20_force_level_prescan.csv`.",
        "",
        "Figure C distinguishes prior 13-phase sampled maxima, new single-phase prescreen values, the 101-point critical curves, and the selected 101-point force-level curves. Lines are guides to the eye; no polynomial fit or interpolated z was used.",
        "",
    ])
    REPORT.parent.mkdir(exist_ok=True)
    REPORT.write_text("\n".join(lines), encoding="utf-8")


def main() -> None:
    full, summary, selected = validate_full()
    derived = derive_summary(full)
    summary = summary.sort_values("z_sphere_mm").reset_index(drop=True)
    derived = derived.sort_values("z_sphere_mm").reset_index(drop=True)
    assert np.allclose(summary.Fmax_mN.astype(float), derived.Fmax_mN, atol=1e-8, rtol=0)
    assert np.allclose(summary.Fmin_mN.astype(float), derived.Fmin_mN, atol=1e-8, rtol=0)
    assert np.all(summary.phase_points.astype(int) == 101)
    prescan = pd.read_csv(PRESCAN)
    critical = pd.read_csv(CRITICAL_FULL)
    critical_summary = pd.read_csv(CRITICAL_SUMMARY)
    assert len(critical) == 404 and len(critical_summary) == 4
    assert critical.status.eq("SUCCESS").all()
    closures = closure_errors(full)
    save_figures(full, derived, selected, prescan, critical)
    write_report(full, derived, selected, closures, critical_summary)
    print(f"VALIDATED full_rows={len(full)} z={derived.z_sphere_mm.tolist()} max_closure={max(v for d in closures.values() for v in d.values()):.3e}")


if __name__ == "__main__":
    main()
