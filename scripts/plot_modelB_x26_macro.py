from pathlib import Path
import pandas as pd
import matplotlib.pyplot as plt

ROOT = Path(r"H:\\comsolcc\\comsol_magnet")
DATA = ROOT / "data" / "modelB_x26_macro_Fmax_summary.csv"
FIG = ROOT / "figures"
FIG.mkdir(parents=True, exist_ok=True)

df = pd.read_csv(DATA)
df = df.sort_values("z_sphere_mm")

def save(name, ylabel, ycol, zero=False):
    fig, ax = plt.subplots(figsize=(7.2, 4.4), dpi=180)
    ax.plot(df["z_sphere_mm"], df[ycol], "o-", lw=1.6, ms=5)
    if zero:
        ax.axhline(0, color="k", lw=0.9, ls="--")
    ax.set_xlabel("z_sphere (mm)")
    ax.set_ylabel(ylabel)
    ax.grid(True, alpha=0.25)
    fig.tight_layout()
    fig.savefig(FIG / f"{name}.png")
    fig.savefig(FIG / f"{name}.svg")
    plt.close(fig)

save("modelB_x26_macro_Fmax_vs_z", "Fmax (mN)", "Fmax_mN", True)
save("modelB_x26_macro_DeltaFx_ball_at_Fmax_vs_z", "DeltaFx_ball at Fmax (mN)", "DeltaFx_ball_at_Fmax_mN")
save("modelB_x26_macro_phi_at_Fmax_vs_z", "phi at Fmax (deg)", "phi_at_Fmax_deg")
print(f"Wrote figures to {FIG}")
