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

alpha0 = pd.concat(
    [
        pd.read_csv(DATA / "modelB_x26_macro_Fmax_summary.csv"),
        pd.read_csv(DATA / "modelB_alpha0_transition_Fmax_summary.csv"),
    ],
    ignore_index=True,
).query("60 <= z_sphere_mm <= 160").drop_duplicates("z_sphere_mm").sort_values("z_sphere_mm")
minus20 = pd.read_csv(DATA / "modelB_alpha_minus20_Fmax_summary.csv").sort_values("z_sphere_mm")
plus20 = pd.read_csv(DATA / "modelB_alpha20_Fmax_summary.csv").sort_values("z_sphere_mm")

SERIES = [
    (minus20, "Alpha = -20$^\\circ$", "#0072B2", "o"),
    (alpha0, "Alpha = 0$^\\circ$", "#4D4D4D", "D"),
    (plus20, "Alpha = +20$^\\circ$", "#D55E00", "s"),
]


def export(fig: plt.Figure, stem: str) -> None:
    fig.savefig(FIG / f"{stem}.svg", bbox_inches="tight")
    fig.savefig(FIG / f"{stem}.pdf", bbox_inches="tight")
    fig.savefig(FIG / f"{stem}.png", dpi=600, bbox_inches="tight")
    fig.savefig(FIG / f"{stem}.tiff", dpi=600, bbox_inches="tight")


def plot_metric(column: str, ylabel: str, stem: str, zero_line: bool = False) -> None:
    fig, ax = plt.subplots(figsize=(7.2, 4.2))
    for frame, label, color, marker in SERIES:
        ax.plot(
            frame["z_sphere_mm"], frame[column], marker=marker, color=color,
            linewidth=1.4, markersize=4.5, label=label,
        )
    if zero_line:
        ax.axhline(0, color="#222222", linewidth=0.8, linestyle="--", zorder=0)
    ax.set_xlabel("Sphere height, z (mm)")
    ax.set_ylabel(ylabel)
    ax.set_xlim(56, 164)
    ax.set_xticks([60, 80, 100, 120, 140, 160])
    ax.grid(axis="y", color="#D9D9D9", linewidth=0.55)
    ax.legend(loc="best", handlelength=2.1)
    fig.tight_layout()
    export(fig, stem)
    plt.close(fig)


plot_metric(
    "Fmax_mN", "Maximum corrected x-force (mN)",
    "modelB_alpha_minus20_0_plus20_Fmax_vs_z", True,
)
plot_metric(
    "DeltaFx_ball_at_Fmax_mN", "Ball contribution at maximum (mN)",
    "modelB_alpha_minus20_0_plus20_ball_contribution_vs_z", True,
)
plot_metric(
    "phi_at_Fmax_deg", "Phase at maximum (deg)",
    "modelB_alpha_minus20_0_plus20_phi_at_Fmax_vs_z",
)

print(f"Wrote three alpha-comparison figures to {FIG}")
