from pathlib import Path
import pandas as pd
import matplotlib.pyplot as plt

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "data"
FIG = ROOT / "figures"
FIG.mkdir(exist_ok=True)

mesh = pd.read_csv(DATA / "modelB_z120_local_mesh_convergence.csv")
alpha = pd.read_csv(DATA / "modelB_z120_phi90_alpha_coarse.csv")

fig, axes = plt.subplots(1, 2, figsize=(10, 4.2), constrained_layout=True)
axes[0].plot(mesh["mesh_level"], mesh["Fx_total_corr_mN"], "o-", label="Fx total corrected")
axes[0].plot(mesh["mesh_level"], mesh["DeltaFx_ball_mN"], "s--", label="DeltaFx ball")
axes[0].axhline(0, color="0.35", lw=0.8)
axes[0].set_ylabel("Force (mN)")
axes[0].set_xlabel("Mesh level")
axes[0].set_title("z=120 mm, phi=90 deg")
axes[0].legend(fontsize=8)
axes[0].grid(alpha=0.25)

axes[1].plot(alpha["alpha_deg"], alpha["Fx_total_corr_mN"], "o-", label="Fx total corrected")
axes[1].plot(alpha["alpha_deg"], alpha["DeltaFx_ball_mN"], "s--", label="DeltaFx ball")
axes[1].axhline(0, color="0.35", lw=0.8)
axes[1].set_xlabel("alpha (deg)")
axes[1].set_ylabel("Force (mN)")
axes[1].set_title("LOCAL_M03 alpha coarse scan")
axes[1].legend(fontsize=8)
axes[1].grid(alpha=0.25)

for ext in ("png", "svg"):
    fig.savefig(FIG / f"modelB_local_mesh_alpha.{ext}", dpi=220 if ext == "png" else None)
plt.close(fig)

fig, ax = plt.subplots(figsize=(6.5, 4.2), constrained_layout=True)
ax.plot(alpha["alpha_deg"], alpha["Fx_total_corr_mN"], "o-", label="Fx_total_corr")
ax.plot(alpha["alpha_deg"], alpha["Fx_hold_corr_mN"], "^-", label="Fx_hold_corr")
ax.plot(alpha["alpha_deg"], alpha["DeltaFx_ball_mN"], "s-", label="DeltaFx_ball")
ax.axhline(0, color="0.35", lw=0.8)
ax.set_xlabel("alpha (deg)")
ax.set_ylabel("Force (mN)")
ax.set_title("Model B alpha scan at z=120 mm, phi=90 deg")
ax.legend()
ax.grid(alpha=0.25)
for ext in ("png", "svg"):
    fig.savefig(FIG / f"modelB_z120_phi90_alpha_coarse.{ext}", dpi=220 if ext == "png" else None)
plt.close(fig)
