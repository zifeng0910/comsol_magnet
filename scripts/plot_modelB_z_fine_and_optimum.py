"""Plot the direct z-fine and local-optimum Model B exports."""
from __future__ import annotations

import csv
from pathlib import Path

import matplotlib.pyplot as plt


ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "data"
FIG = ROOT / "figures"


def rows(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8-sig") as handle:
        return list(csv.DictReader(handle))


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
    fine = rows(DATA / "modelB_z110_140_phi90_fine.csv")
    fine_b2 = [r for r in fine if r["mode"] == "B2" and r["status"] == "SUCCESS"]
    fine_b2.sort(key=lambda r: float(r["z_sphere_mm"]))

    fig, ax = plt.subplots(figsize=(8.2, 5.1), constrained_layout=True)
    z = [float(r["z_sphere_mm"]) for r in fine_b2]
    total = [float(r["Fx_total_corr_mN"]) for r in fine_b2]
    ball = [float(r["DeltaFx_ball_mN"]) for r in fine_b2]
    ax.plot(z, total, "o-", linewidth=1.8, label="Fx total corrected")
    ax.axhline(0, color="black", linewidth=0.8)
    ax.set(xlabel="z_sphere (mm)", ylabel="Fx_total_corr at phi=90° (mN)",
           title="Model B: corrected total force near z=120 mm")
    ax.grid(True, alpha=0.25)
    ax.legend()
    save(fig, "modelB_z110_140_Fx_total_corr_phi90")

    fig, ax = plt.subplots(figsize=(8.2, 5.1), constrained_layout=True)
    ax.plot(z, ball, "o-", color="#d62728", linewidth=1.8, label="DeltaFx ball = B2−B1")
    ax.axhline(0, color="black", linewidth=0.8)
    ax.set(xlabel="z_sphere (mm)", ylabel="DeltaFx_ball at phi=90° (mN)",
           title="Model B: ball contribution near z=120 mm")
    ax.grid(True, alpha=0.25)
    ax.legend()
    save(fig, "modelB_z110_140_DeltaFx_ball_phi90")

    opt = rows(DATA / "modelB_local_optimum_phi_scan.csv")
    opt = [r for r in opt if r["mode"] == "B2" and r["status"] == "SUCCESS"]
    opt.sort(key=lambda r: float(r["phi_deg"]))
    fig, ax = plt.subplots(figsize=(8.2, 5.1), constrained_layout=True)
    ax.plot([float(r["phi_deg"]) for r in opt],
            [float(r["Fx_total_corr_mN"]) for r in opt],
            "o-", color="#1f77b4", linewidth=1.8, label="z=120 mm")
    ax.axhline(0, color="black", linewidth=0.8)
    ax.set(xlabel="phi (deg)", ylabel="Fx_total_corr (mN)",
           title="Model B: local optimum full sparse phase check")
    ax.set_xticks(range(0, 361, 45))
    ax.grid(True, alpha=0.25)
    ax.legend()
    save(fig, "modelB_local_optimum_z120_Fx_vs_phi")


if __name__ == "__main__":
    main()
