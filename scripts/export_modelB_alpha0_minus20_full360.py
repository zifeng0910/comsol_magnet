"""Aggregate and plot the alpha=0 / alpha=-20 full phase study.

The script intentionally validates every per-height CSV before writing any
delivery artifact.  It is safe to rerun after a completed or interrupted
batch; a failed validation stops before replacing the final data files.
"""

from __future__ import annotations

import math
from pathlib import Path

import matplotlib as mpl
import matplotlib.pyplot as plt
import pandas as pd


ROOT = Path(__file__).resolve().parents[1]
STAMP = "20260913"
WORK = ROOT / "work"
DATA = ROOT / "data"
FIGURES = ROOT / "figures"
REPORTS = ROOT / "reports"
ZS = [100.0, 102.5, 105.0, 107.5, 110.0, 112.5, 115.0, 117.5, 120.0]
PHI = [10.0 * i for i in range(37)]

EXPECTED = [
    "alpha_deg",
    "z_sphere_mm",
    "phi_deg",
    "mesh_level",
    "Fx_hold_corr_mN",
    "DeltaFx_ball_mN",
    "Fx_total_corr_mN",
    "Fy_total_corr_mN",
    "Fz_total_corr_mN",
    "Fmax_mN",
    "Fmin_mN",
    "phi_at_Fmax_deg",
    "phi_at_Fmin_deg",
    "all_sampled_phi_negative",
    "release_candidate",
    "elements",
    "DOF",
    "min_quality",
    "same_mesh_verified",
    "status",
]


def read_and_validate(alpha_name: str, alpha_value: float) -> pd.DataFrame:
    root_name = "modelB_alpha0_z100_120_full360" if alpha_name == "alpha0" else "modelB_alpha_minus20_z100_120_full360"
    root = WORK / f"{root_name}_{STAMP}"
    rows = []
    for z in ZS:
        path = root / f"z{z:g}" / ("modelB_alpha0_full360_phi.csv" if alpha_name == "alpha0" else "modelB_alpha_minus20_full360_phi.csv")
        if not path.exists():
            raise FileNotFoundError(f"missing completed CSV: {path}")
        df = pd.read_csv(path)
        missing = [c for c in EXPECTED if c not in df.columns]
        if missing:
            raise ValueError(f"{path}: missing columns {missing}")
        if len(df) != 37:
            raise ValueError(f"{path}: expected 37 rows, found {len(df)}")
        phases = df["phi_deg"].astype(float).tolist()
        if any(abs(a - b) > 1e-8 for a, b in zip(phases, PHI)):
            raise ValueError(f"{path}: phase grid is not exactly 0:10:360")
        if not (df["status"].eq("SUCCESS").all() and df["same_mesh_verified"].astype(str).str.lower().eq("true").all()):
            raise ValueError(f"{path}: status or same-mesh validation failed")
        if abs(float(df.iloc[0]["Fx_total_corr_mN"]) - float(df.iloc[-1]["Fx_total_corr_mN"])) > 1e-8:
            raise ValueError(f"{path}: 0/360 force closure failed")
        if abs(float(df["alpha_deg"].iloc[0]) - alpha_value) > 1e-8 or abs(float(df["z_sphere_mm"].iloc[0]) - z) > 1e-8:
            raise ValueError(f"{path}: alpha/z metadata mismatch")
        rows.append(df)
    return pd.concat(rows, ignore_index=True)


def make_summary(df: pd.DataFrame) -> pd.DataFrame:
    out = []
    for (alpha, z), g in df.groupby(["alpha_deg", "z_sphere_mm"], sort=True):
        imax = g["Fx_total_corr_mN"].astype(float).idxmax()
        imin = g["Fx_total_corr_mN"].astype(float).idxmin()
        hold = float(g["Fx_hold_corr_mN"].iloc[0])
        out.append(
            {
                "alpha_deg": float(alpha),
                "z_sphere_mm": float(z),
                "Fmax_mN": float(g["Fx_total_corr_mN"].max()),
                "phi_at_Fmax_deg": float(g.loc[imax, "phi_deg"]),
                "Fmin_mN": float(g["Fx_total_corr_mN"].min()),
                "phi_at_Fmin_deg": float(g.loc[imin, "phi_deg"]),
                "Fx_hold_corr_mN": hold,
                "DeltaFx_ball_at_Fmax_mN": float(g.loc[imax, "DeltaFx_ball_mN"]),
                "positive_phase_count": int((g["Fx_total_corr_mN"] > 0).sum()),
                "negative_phase_count": int((g["Fx_total_corr_mN"] < 0).sum()),
                "all_sampled_phi_negative": bool((g["Fx_total_corr_mN"] < 0).all()),
                "release_candidate": bool(g["Fx_total_corr_mN"].max() > 0),
                "phase_points": int(len(g)),
            }
        )
    return pd.DataFrame(out).sort_values(["alpha_deg", "z_sphere_mm"]).reset_index(drop=True)


def save_pub(fig: mpl.figure.Figure, stem: Path) -> None:
    fig.savefig(stem.with_suffix(".svg"), bbox_inches="tight")
    fig.savefig(stem.with_suffix(".pdf"), bbox_inches="tight")
    fig.savefig(stem.with_suffix(".tiff"), dpi=600, bbox_inches="tight")
    fig.savefig(stem.with_suffix(".png"), dpi=300, bbox_inches="tight")
    plt.close(fig)


def plot_full_curve(df: pd.DataFrame, alpha: float, label: str, stem: Path) -> None:
    fig, ax = plt.subplots(figsize=(7.2, 4.3))
    cmap = mpl.colormaps["viridis"]
    for i, z in enumerate(ZS):
        g = df[(df["alpha_deg"] == alpha) & (df["z_sphere_mm"] == z)].sort_values("phi_deg")
        ax.plot(g["phi_deg"], g["Fx_total_corr_mN"], lw=1.15, color=cmap(0.12 + 0.78 * i / (len(ZS) - 1)), label=f"z = {z:g} mm")
    ax.axhline(0, color="#444444", lw=0.75, ls="--")
    ax.set(xlim=(0, 360), xlabel="Sphere phase φ (deg)", ylabel="Corrected total force Fₓ (mN)", title=f"{label}: full 0–360° phase curves")
    ax.set_xticks(range(0, 361, 60))
    ax.legend(ncol=3, fontsize=7, frameon=False, loc="best")
    fig.tight_layout()
    save_pub(fig, stem)


def plot_fmax(summary: pd.DataFrame, stem: Path) -> None:
    fig, ax = plt.subplots(figsize=(6.6, 4.1))
    styles = [(0.0, "alpha = 0°", "#1769aa", "o"), (-20.0, "alpha = −20°", "#d95f02", "s")]
    for alpha, label, color, marker in styles:
        g = summary[summary["alpha_deg"] == alpha]
        ax.plot(g["z_sphere_mm"], g["Fmax_mN"], color=color, marker=marker, ms=4.7, lw=1.6, label=label)
    ax.axhline(0, color="#444444", lw=0.75, ls="--")
    ax.set(xlabel="Sphere height z (mm)", ylabel="Phase-max corrected force Fₓ (mN)", title="Release envelope: alpha = 0° versus −20°")
    ax.set_xticks(ZS)
    ax.tick_params(axis="x", rotation=45)
    ax.legend(frameon=False)
    fig.tight_layout()
    save_pub(fig, stem)


def write_report(summary: pd.DataFrame, all_df: pd.DataFrame) -> None:
    lines = [
        "# Model B: alpha=0° versus alpha=-20° full phase comparison",
        "",
        "## Scope",
        "",
        "This report contains only the two requested orientation groups. The fixed setup is x_sphere=26 mm, gap=0.30 mm, the 50 mm sphere, the same materials/Br/force definition, LOCAL_M03, Stationary, PARDISO, and stol=1e-6. Each height uses the same-mesh differential `Fx_total_corr = B2-B0`, with `Fx_hold_corr = B1-B0` and `DeltaFx_ball = B2-B1`.",
        "",
        "The full phase grid is 37 real COMSOL points per height (`phi=0:10:360`) at z = 100, 102.5, 105, 107.5, 110, 112.5, 115, 117.5, 120 mm.",
        "",
        "## Validation",
        "",
        f"Validated rows: {len(all_df)} ({len(summary)} height/orientation summaries). Every per-height file contains 37 SUCCESS rows, `same_mesh_verified=true`, the requested phase grid, and 0°/360° force closure within 1e-8 mN.",
        "",
        "## Fmax summary",
        "",
        "| alpha (deg) | z (mm) | Fmax (mN) | phi at Fmax (deg) | Fmin (mN) | phi at Fmin (deg) | positive phases | release candidate |",
        "|---:|---:|---:|---:|---:|---:|---:|:---|",
    ]
    for _, r in summary.iterrows():
        lines.append(f"| {r.alpha_deg:.0f} | {r.z_sphere_mm:g} | {r.Fmax_mN:.9f} | {r.phi_at_Fmax_deg:g} | {r.Fmin_mN:.9f} | {r.phi_at_Fmin_deg:g} | {int(r.positive_phase_count)} | {'yes' if r.release_candidate else 'no'} |")
    for alpha, label in [(0.0, "alpha=0°"), (-20.0, "alpha=-20°")]:
        g = summary[summary.alpha_deg == alpha].sort_values("z_sphere_mm")
        positive = g[g.Fmax_mN > 0]
        transition = "none in the sampled interval" if positive.empty or len(positive) == len(g) else f"between {positive.z_sphere_mm.iloc[-1]:g} and {g[g.Fmax_mN <= 0].z_sphere_mm.iloc[0]:g} mm"
        lines.extend(["", f"### {label}", "", f"The sampled Fmax sign transition is {transition}. The phase maximizing Fmax is {g.phi_at_Fmax_deg.unique().tolist()} degrees across the grid."])
    lines.extend(["", "## Deliverables", "", "- `data/modelB_alpha0_z100_120_full360.csv`", "- `data/modelB_alpha_minus20_z100_120_full360.csv`", "- `data/modelB_alpha0_minus20_z100_120_Fmax_summary.csv`", "- Full-phase curves for each alpha group and the Fmax-versus-z comparison in `figures/`.", ""])
    (REPORTS / "MODELB_ALPHA0_VS_ALPHA_MINUS20_Z100_120_FULL360.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    DATA.mkdir(exist_ok=True)
    FIGURES.mkdir(exist_ok=True)
    REPORTS.mkdir(exist_ok=True)
    mpl.rcParams.update({"font.family": "sans-serif", "font.sans-serif": ["Arial", "Helvetica", "DejaVu Sans"], "font.size": 7.5, "svg.fonttype": "none", "pdf.fonttype": 42, "axes.spines.right": False, "axes.spines.top": False, "axes.linewidth": 0.8})
    alpha0 = read_and_validate("alpha0", 0.0)
    minus20 = read_and_validate("minus20", -20.0)
    summary = make_summary(pd.concat([alpha0, minus20], ignore_index=True))
    alpha0.to_csv(DATA / "modelB_alpha0_z100_120_full360.csv", index=False)
    minus20.to_csv(DATA / "modelB_alpha_minus20_z100_120_full360.csv", index=False)
    summary.to_csv(DATA / "modelB_alpha0_minus20_z100_120_Fmax_summary.csv", index=False)
    combined = pd.concat([alpha0, minus20], ignore_index=True)
    plot_full_curve(combined, 0.0, "alpha = 0°", FIGURES / "modelB_alpha0_z100_120_full360_Fx_vs_phi")
    plot_full_curve(combined, -20.0, "alpha = −20°", FIGURES / "modelB_alpha_minus20_z100_120_full360_Fx_vs_phi")
    plot_fmax(summary, FIGURES / "modelB_alpha0_minus20_z100_120_Fmax_vs_z")
    write_report(summary, combined)
    print(f"WROTE rows alpha0={len(alpha0)} minus20={len(minus20)} summaries={len(summary)}")


if __name__ == "__main__":
    main()
