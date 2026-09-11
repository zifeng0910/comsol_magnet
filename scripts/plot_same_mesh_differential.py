"""Create audit figures from the same-mesh differential-force exports.

The plotted quantities are measured COMSOL outputs.  No interpolation is used:
the Model A lines connect the six simulated gap values and the Model B lines
connect the nine simulated phase values at each of the three heights.
"""
from __future__ import annotations

import csv
from pathlib import Path

import matplotlib.pyplot as plt


ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "data"
FIG = ROOT / "figures"


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8-sig") as handle:
        return list(csv.DictReader(handle))


def save_all(fig: plt.Figure, stem: str) -> None:
    FIG.mkdir(parents=True, exist_ok=True)
    fig.savefig(FIG / f"{stem}.png", dpi=320, bbox_inches="tight")
    fig.savefig(FIG / f"{stem}.tiff", dpi=320, bbox_inches="tight")
    fig.savefig(FIG / f"{stem}.svg", bbox_inches="tight")
    fig.savefig(FIG / f"{stem}.pdf", bbox_inches="tight")
    plt.close(fig)


def model_a() -> None:
    rows = read_csv(DATA / "holding_force_same_mesh_diff_025_030.csv")
    old_rows = read_csv(DATA / "holding_force_vs_gap_025_030_step001.csv")

    fig, ax = plt.subplots(figsize=(8.2, 5.1), constrained_layout=True)
    colors = {"M_h003": "#1f77b4", "M_h002": "#d62728"}
    labels = {"M_h003": "same-mesh differential, h≈0.03 mm", "M_h002": "same-mesh differential, h≈0.02 mm"}
    for level in ("M_h003", "M_h002"):
        selected = [r for r in rows if r["mesh_level"] == level and r["status"] == "SUCCESS"]
        selected.sort(key=lambda r: float(r["gap_mm"]))
        ax.plot(
            [float(r["gap_mm"]) for r in selected],
            [float(r["Fx_hold_diff_mN"]) for r in selected],
            "o-", color=colors[level], label=labels[level], linewidth=1.8,
        )
    old_ok = [r for r in old_rows if r.get("solver_status") == "SUCCESS"]
    if old_ok and "gap_mm" in old_ok[0] and "Fx_hold_mN" in old_ok[0]:
        old_ok.sort(key=lambda r: float(r["gap_mm"]))
        ax.plot(
            [float(r["gap_mm"]) for r in old_ok],
            [float(r["Fx_hold_mN"]) for r in old_ok],
            "k--", alpha=0.55, linewidth=1.1, label="Old raw surface result (auxiliary)",
        )
    ax.axhline(0, color="black", linewidth=0.8)
    ax.set_xlabel("gap (mm)")
    ax.set_ylabel("corrected Fx hold (mN)")
    ax.set_title("Model A: same-mesh steel ON−OFF differential")
    ax.grid(True, alpha=0.25)
    ax.legend(fontsize=8)
    save_all(fig, "modelA_same_mesh_differential_vs_gap")


def model_b() -> None:
    rows = read_csv(DATA / "modelB_same_mesh_differential_sparse.csv")
    rows = [r for r in rows if r["mode"] == "B2" and r["status"] == "SUCCESS"]
    rows.sort(key=lambda r: (float(r["z_sphere_mm"]), float(r["phi_deg"])))

    fig, ax = plt.subplots(figsize=(8.2, 5.1), constrained_layout=True)
    colors = {120.0: "#1f77b4", 140.0: "#d62728", 150.0: "#2ca02c"}
    for z in (120.0, 140.0, 150.0):
        selected = [r for r in rows if abs(float(r["z_sphere_mm"]) - z) < 1e-9]
        ax.plot(
            [float(r["phi_deg"]) for r in selected],
            [float(r["Fx_total_corr_mN"]) for r in selected],
            "o-", color=colors[z], linewidth=1.7, label=f"z={z:g} mm",
        )
    ax.axhline(0, color="black", linewidth=0.8)
    ax.set_xlabel("phi (deg)")
    ax.set_ylabel("corrected Fx total (mN)")
    ax.set_title("Model B: same-mesh corrected force over sparse cycle")
    ax.set_xticks(range(0, 361, 45))
    ax.grid(True, alpha=0.25)
    ax.legend()
    save_all(fig, "modelB_same_mesh_corrected_force_vs_phi")


if __name__ == "__main__":
    plt.rcParams.update({
        "font.family": "sans-serif",
        "font.size": 10,
        "axes.axisbelow": True,
        "svg.fonttype": "none",
        "pdf.fonttype": 42,
        "ps.fonttype": 42,
    })
    model_a()
    model_b()
    print(FIG / "modelA_same_mesh_differential_vs_gap.png")
    print(FIG / "modelB_same_mesh_corrected_force_vs_phi.png")
