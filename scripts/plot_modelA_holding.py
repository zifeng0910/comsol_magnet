"""Plot Model A holding-force and cancellation audit from the exported CSV."""
from pathlib import Path
import csv
import matplotlib.pyplot as plt

ROOT = Path(__file__).resolve().parents[1]
csv_path = ROOT / "data" / "holding_force_vs_gap_025_030_step001.csv"
fig_path = ROOT / "figures" / "modelA_holding_force_audit.png"

with csv_path.open(newline="", encoding="utf-8-sig") as f:
    rows = list(csv.DictReader(f))

gap = [float(r["gap_mm"]) for r in rows if r["solver_status"] == "SUCCESS"]
fx = [float(r["Fx_hold_mN"]) for r in rows if r["solver_status"] == "SUCCESS"]
ratio = [float(r["cancellation_ratio"]) for r in rows if r["solver_status"] == "SUCCESS"]
face15 = [float(r["Fx_face15_mN"]) for r in rows if r["solver_status"] == "SUCCESS"]
face24 = [float(r["Fx_face24_mN"]) for r in rows if r["solver_status"] == "SUCCESS"]
side = [float(r["Fx_side_mN"]) for r in rows if r["solver_status"] == "SUCCESS"]

plt.rcParams.update({"font.size": 10, "axes.grid": True, "grid.alpha": 0.25})
fig, ax = plt.subplots(2, 2, figsize=(10, 7), constrained_layout=True)
ax[0, 0].plot(gap, fx, "o-")
ax[0, 0].set(xlabel="gap (mm)", ylabel="Fx_hold (mN)", title="Holding force")
ax[0, 1].plot(gap, [abs(v) for v in fx], "o-")
ax[0, 1].set(xlabel="gap (mm)", ylabel="|Fx_hold| (mN)", title="Holding-force magnitude")
ax[1, 0].plot(gap, ratio, "o-")
ax[1, 0].set(xlabel="gap (mm)", ylabel="cancellation ratio", title="Surface cancellation")
ax[1, 1].plot(gap, face15, "o-", label="x-minus face")
ax[1, 1].plot(gap, face24, "o-", label="x-plus face")
ax[1, 1].plot(gap, side, "o-", label="side")
ax[1, 1].set(xlabel="gap (mm)", ylabel="surface Fx (mN)", title="Boundary contributions")
ax[1, 1].legend(fontsize=8)
fig.suptitle("Model A: steel + cylinder, COMSOL 6.3, local h≈0.03 mm")
fig.savefig(fig_path, dpi=180)
print(fig_path)
