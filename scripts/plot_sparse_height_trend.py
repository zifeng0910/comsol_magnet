"""绘制 COMSOL 稀疏高度粗扫与候选复核结果。"""
from pathlib import Path
import sys
import pandas as pd
import matplotlib.pyplot as plt


def main() -> None:
    if len(sys.argv) != 5:
        raise SystemExit("usage: plot_sparse_height_trend.py raw.csv summary.csv candidate.csv output_dir")
    raw_path, summary_path, candidate_path, output_dir = map(Path, sys.argv[1:])
    output_dir.mkdir(parents=True, exist_ok=True)
    raw = pd.read_csv(raw_path)
    summary = pd.read_csv(summary_path)
    candidate = pd.read_csv(candidate_path)

    on = raw[raw["mode"] == "ON"].copy()
    on = on[on["phi_deg"] < 360]
    fig, ax = plt.subplots(figsize=(8.5, 5.2), dpi=160)
    for phi, group in on.groupby("phi_deg"):
        ax.plot(group["z_mm"], group["Fx_mN"], marker="o", linewidth=1.2, label=f"{phi:g} deg")
    ax.axhline(0, color="black", linewidth=0.8)
    ax.set(xlabel="z_sphere (mm)", ylabel="Fx (mN)", title="Sparse ON force by sampled angle")
    ax.grid(True, alpha=0.25)
    ax.legend(ncol=2, fontsize=8)
    fig.tight_layout()
    fig.savefig(output_dir / "fx_by_angle_vs_z.png")
    plt.close(fig)

    fig, ax = plt.subplots(figsize=(8.5, 5.2), dpi=160)
    ax.plot(summary["z_mm"], summary["Fmax_mN"], "o-", label="Fmax")
    ax.plot(summary["z_mm"], summary["mean_Fx_mN"], "o-", label="mean Fx")
    ax.plot(summary["z_mm"], summary["Fmin_mN"], "o-", label="Fmin")
    ax.axhline(0, color="black", linewidth=0.8)
    ax.set(xlabel="z_sphere (mm)", ylabel="Fx (mN)", title="Sparse-cycle metrics")
    ax.grid(True, alpha=0.25)
    ax.legend()
    fig.tight_layout()
    fig.savefig(output_dir / "summary_metrics_vs_z.png")
    plt.close(fig)

    fig, ax = plt.subplots(figsize=(8.5, 5.2), dpi=160)
    ax.plot(summary["z_mm"], summary["Fx_AIR_0_mN"], "o-", label="AIR phi=0")
    ax.plot(summary["z_mm"], summary["Fx_ON0_minus_AIR_mN"], "o-", label="ON(0)-AIR")
    ax.axhline(0, color="black", linewidth=0.8)
    ax.set(xlabel="z_sphere (mm)", ylabel="Fx (mN)", title="AIR diagnostic trend")
    ax.grid(True, alpha=0.25)
    ax.legend()
    fig.tight_layout()
    fig.savefig(output_dir / "air_diagnostic_vs_z.png")
    plt.close(fig)

    pivot = on.pivot(index="phi_deg", columns="z_mm", values="Fx_mN").sort_index()
    fig, ax = plt.subplots(figsize=(8.5, 4.8), dpi=160)
    im = ax.imshow(pivot.values, aspect="auto", origin="lower", cmap="coolwarm")
    ax.set_xticks(range(len(pivot.columns)), [f"{z:g}" for z in pivot.columns])
    ax.set_yticks(range(len(pivot.index)), [f"{p:g}" for p in pivot.index])
    ax.set(xlabel="z_sphere (mm)", ylabel="phi (deg)", title="Sparse Fx(z, phi) heatmap")
    fig.colorbar(im, ax=ax, label="Fx (mN)")
    fig.tight_layout()
    fig.savefig(output_dir / "fx_heatmap_sparse.png")
    plt.close(fig)

    cand = candidate[candidate["mode"] == "ON"].copy()
    cand = cand[cand["phi_deg"] < 360]
    fig, ax = plt.subplots(figsize=(8.5, 5.2), dpi=160)
    for z, group in cand.groupby("z_mm"):
        ax.plot(group["phi_deg"], group["Fx_mN"], "o-", label=f"z={z:g} mm")
    ax.axhline(0, color="black", linewidth=0.8)
    ax.set(xlabel="phi (deg)", ylabel="Fx (mN)", title="Candidate 10-degree verification")
    ax.grid(True, alpha=0.25)
    ax.legend()
    fig.tight_layout()
    fig.savefig(output_dir / "candidate_10deg_verification.png")
    plt.close(fig)


if __name__ == "__main__":
    main()
