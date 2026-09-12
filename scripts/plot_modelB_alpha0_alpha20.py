from pathlib import Path

import matplotlib as mpl
import matplotlib.pyplot as plt
import pandas as pd


ROOT = Path(r"H:\comsolcc\comsol_magnet")
DATA = ROOT / "data"
FIG = ROOT / "figures"
FIG.mkdir(parents=True, exist_ok=True)

mpl.rcParams.update(
    {
        "font.family": "sans-serif",
        "font.sans-serif": ["Arial", "Helvetica", "DejaVu Sans", "sans-serif"],
        "svg.fonttype": "none",
        "pdf.fonttype": 42,
        "font.size": 8,
        "axes.labelsize": 9,
        "axes.linewidth": 0.8,
        "axes.spines.right": False,
        "axes.spines.top": False,
        "legend.frameon": False,
        "xtick.direction": "out",
        "ytick.direction": "out",
    }
)

alpha0_macro = pd.read_csv(DATA / "modelB_x26_macro_Fmax_summary.csv")
alpha0_transition = pd.read_csv(DATA / "modelB_alpha0_transition_Fmax_summary.csv")
alpha0 = pd.concat([alpha0_macro, alpha0_transition], ignore_index=True)
alpha0 = alpha0[alpha0["z_sphere_mm"].isin([60, 80, 100, 105, 110, 115, 120, 140, 160])]
alpha0 = alpha0.sort_values("z_sphere_mm")

alpha20 = pd.read_csv(DATA / "modelB_alpha20_Fmax_summary.csv").sort_values("z_sphere_mm")
expected20 = [60, 70, 75, 80, 85, 90, 100, 120, 140, 160]
if alpha20["z_sphere_mm"].tolist() != expected20:
    raise ValueError(f"Unexpected alpha=20 heights: {alpha20['z_sphere_mm'].tolist()}")

COLORS = {"alpha0": "#176D82", "alpha20": "#C85238", "zero": "#303030"}


def export(fig: plt.Figure, stem: str) -> None:
    fig.savefig(FIG / f"{stem}.svg", bbox_inches="tight")
    fig.savefig(FIG / f"{stem}.pdf", bbox_inches="tight")
    fig.savefig(FIG / f"{stem}.png", dpi=600, bbox_inches="tight")


def plot_metric(column: str, ylabel: str, stem: str, zero_line: bool) -> None:
    fig, ax = plt.subplots(figsize=(5.7, 3.6))
    ax.plot(
        alpha0["z_sphere_mm"], alpha0[column], "o-", color=COLORS["alpha0"],
        linewidth=1.5, markersize=4.4, label=r"$\alpha=0^\circ$",
    )
    ax.plot(
        alpha20["z_sphere_mm"], alpha20[column], "s-", color=COLORS["alpha20"],
        linewidth=1.5, markersize=4.2, label=r"$\alpha=20^\circ$",
    )
    if zero_line:
        ax.axhline(0, color=COLORS["zero"], linewidth=0.9, linestyle="--", zorder=0)
    ax.set_xlabel(r"Sphere height, $z_\mathrm{sphere}$ (mm)")
    ax.set_ylabel(ylabel)
    ax.set_xlim(56, 164)
    ax.set_xticks([60, 80, 100, 120, 140, 160])
    ax.grid(axis="y", color="#D9D9D9", linewidth=0.55)
    ax.legend(loc="best", handlelength=2.1)
    fig.tight_layout()
    export(fig, stem)
    plt.close(fig)


plot_metric("Fmax_mN", r"$F_{x,\max}$ (mN)", "modelB_alpha0_alpha20_Fmax_vs_z", True)
plot_metric(
    "DeltaFx_ball_at_Fmax_mN",
    r"$\Delta F_{x,\mathrm{ball}}$ at $F_{x,\max}$ (mN)",
    "modelB_alpha0_alpha20_ball_contribution_vs_z",
    True,
)

print(f"Wrote alpha comparison figures to {FIG}")
