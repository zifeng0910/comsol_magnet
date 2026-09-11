"""Summarize and plot the lower-z same-mesh Model B scan.

All plotted points are direct COMSOL solves.  The 360-degree row is retained
for closure auditing but is excluded from cycle statistics to avoid duplicate
weighting of the 0-degree phase.
"""
from __future__ import annotations

import csv
from pathlib import Path

import matplotlib.pyplot as plt


ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "data"
FIG = ROOT / "figures"
INPUT = DATA / "modelB_lower_z_same_mesh_sparse.csv"
SUMMARY = DATA / "modelB_lower_z_same_mesh_summary.csv"


def read_rows() -> list[dict[str, str]]:
    with INPUT.open(newline="", encoding="utf-8-sig") as handle:
        return list(csv.DictReader(handle))


def write_summary(rows: list[dict[str, str]]) -> list[dict[str, str]]:
    out: list[dict[str, str]] = []
    for z in sorted({float(r["z_sphere_mm"]) for r in rows}, reverse=True):
        group = [r for r in rows if float(r["z_sphere_mm"]) == z]
        b0 = next(r for r in group if r["mode"] == "B0")
        b1 = next(r for r in group if r["mode"] == "B1")
        b2 = sorted(
            (r for r in group if r["mode"] == "B2"),
            key=lambda r: float(r["phi_deg"]),
        )
        cycle = b2[:-1]
        vals = [float(r["Fx_total_corr_mN"]) for r in cycle]
        peak = max(b2, key=lambda r: float(r["Fx_total_corr_mN"]))
        row = {
            "z_sphere_mm": f"{z:.1f}",
            "Fx_B0_raw_mN": b0["Fx_raw_mN"],
            "Fx_hold_corr_mN": b1["Fx_hold_corr_mN"],
            "Fx_min_mN": f"{min(vals):.12g}",
            "Fx_max_mN": f"{max(vals):.12g}",
            "phi_at_Fx_max_deg": peak["phi_deg"],
            "mean_Fx_corr_mN": f"{sum(vals) / len(vals):.12g}",
            "DeltaFx_ball_at_Fx_max_mN": peak["DeltaFx_ball_mN"],
            "closure_360_minus_0_mN": f"{float(b2[-1]['Fx_total_corr_mN']) - float(b2[0]['Fx_total_corr_mN']):.12g}",
            "status": "MAGNETIC_RELEASE_CANDIDATE" if max(vals) > 0 else "MAGNETICALLY_HELD",
            "rows": str(len(b2)),
            "solve_status": "SUCCESS" if all(r["status"] == "SUCCESS" for r in group) else "INCOMPLETE",
        }
        out.append(row)
    with SUMMARY.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(out[0]))
        writer.writeheader()
        writer.writerows(out)
    return out


def save(fig: plt.Figure, stem: str) -> None:
    FIG.mkdir(parents=True, exist_ok=True)
    fig.savefig(FIG / f"{stem}.png", dpi=320, bbox_inches="tight")
    fig.savefig(FIG / f"{stem}.tiff", dpi=320, bbox_inches="tight")
    fig.savefig(FIG / f"{stem}.svg", bbox_inches="tight")
    fig.savefig(FIG / f"{stem}.pdf", bbox_inches="tight")
    plt.close(fig)


def main() -> None:
    plt.rcParams.update({
        "font.family": "sans-serif",
        "font.size": 10,
        "axes.axisbelow": True,
        "svg.fonttype": "none",
        "pdf.fonttype": 42,
        "ps.fonttype": 42,
    })
    rows = read_rows()
    summary = write_summary(rows)

    fig, ax = plt.subplots(figsize=(8.2, 5.1), constrained_layout=True)
    z = [float(r["z_sphere_mm"]) for r in summary]
    fmax = [float(r["Fx_max_mN"]) for r in summary]
    ax.plot(z, fmax, "o-", color="#1f77b4", linewidth=1.8, label="Sampled Fx total maximum")
    ax.axhline(0, color="black", linewidth=0.8)
    ax.set_xlabel("z_sphere (mm)")
    ax.set_ylabel("Fx_total_corr maximum (mN)")
    ax.set_title("Model B: corrected cycle maximum versus height")
    ax.grid(True, alpha=0.25)
    ax.legend()
    save(fig, "modelB_lower_z_Fxmax_vs_z")

    fig, ax = plt.subplots(figsize=(8.2, 5.1), constrained_layout=True)
    colors = {110.0: "#1f77b4", 100.0: "#d62728", 90.0: "#2ca02c", 80.0: "#9467bd"}
    for zval in sorted({float(r["z_sphere_mm"]) for r in rows}, reverse=True):
        group = sorted(
            (r for r in rows if float(r["z_sphere_mm"]) == zval and r["mode"] == "B2"),
            key=lambda r: float(r["phi_deg"]),
        )
        ax.plot(
            [float(r["phi_deg"]) for r in group],
            [float(r["Fx_total_corr_mN"]) for r in group],
            "o-", linewidth=1.6, color=colors[zval], label=f"z={zval:g} mm",
        )
    ax.axhline(0, color="black", linewidth=0.8)
    ax.set_xlabel("phi (deg)")
    ax.set_ylabel("Fx_total_corr (mN)")
    ax.set_title("Model B: corrected force versus phase")
    ax.set_xticks(range(0, 361, 45))
    ax.grid(True, alpha=0.25)
    ax.legend()
    save(fig, "modelB_lower_z_Fx_vs_phi")
    print(SUMMARY)


if __name__ == "__main__":
    main()
