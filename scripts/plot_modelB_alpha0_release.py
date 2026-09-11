from pathlib import Path
import pandas as pd
import matplotlib.pyplot as plt

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "data"
FIG = ROOT / "figures"
FIG.mkdir(exist_ok=True)

z = pd.read_csv(DATA / "modelB_alpha0_z_coarse_scan.csv")
zlocal = pd.read_csv(DATA / "modelB_alpha0_z_local_refinement.csv")
phi = pd.read_csv(DATA / "modelB_alpha0_z60_phi_scan.csv")

fig, ax = plt.subplots(figsize=(6.5, 4.2), constrained_layout=True)
ax.plot(z["z_sphere_mm"], z["Fx_total_corr_mN"], "o", label="z=60 coarse result")
ax.plot(zlocal["z_sphere_mm"], zlocal["Fx_total_corr_mN"], "s", label="z=50 local check")
ax.axhline(0, color="0.35", lw=0.8)
ax.set_xlabel("z_sphere (mm)")
ax.set_ylabel("Fx_total_corr (mN)")
ax.set_title("alpha=0, phi=90: release-candidate checks")
ax.legend()
ax.grid(alpha=0.25)
for ext in ("png", "svg"):
    fig.savefig(FIG / f"modelB_alpha0_z_release_checks.{ext}", dpi=220 if ext == "png" else None)
plt.close(fig)

fig, ax = plt.subplots(figsize=(6.5, 4.2), constrained_layout=True)
ax.plot(phi["phi_deg"], phi["Fx_total_corr_mN"], "o-", label="Fx_total_corr")
ax.plot(phi["phi_deg"], phi["DeltaFx_ball_mN"], "s--", label="DeltaFx_ball")
ax.axhline(0, color="0.35", lw=0.8)
ax.set_xlabel("phi (deg)")
ax.set_ylabel("Force (mN)")
ax.set_title("z=60 mm, alpha=0, LOCAL_M03")
ax.legend()
ax.grid(alpha=0.25)
for ext in ("png", "svg"):
    fig.savefig(FIG / f"modelB_alpha0_z60_phi_scan.{ext}", dpi=220 if ext == "png" else None)
plt.close(fig)
