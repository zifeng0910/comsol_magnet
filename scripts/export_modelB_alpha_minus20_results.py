from pathlib import Path

import numpy as np
import pandas as pd


ROOT = Path(r"H:\comsolcc\comsol_magnet")
DATA = ROOT / "data"
WORK = ROOT / "work"
MACRO = WORK / "modelB_alpha_minus20_sparse_20260913"
REFINE = WORK / "modelB_alpha_minus20_refinement_20260913"
EXPECTED_PHI = np.arange(0.0, 361.0, 45.0)


def load_cycles(base: Path) -> pd.DataFrame:
    files = sorted(base.rglob("modelB_alpha_minus20_phi_sparse.csv"))
    if not files:
        raise FileNotFoundError(f"No alpha=-20 cycle CSVs under {base}")
    rows = pd.concat((pd.read_csv(path) for path in files), ignore_index=True)
    rows.insert(1, "alpha_deg", -20.0)
    numeric = [
        "z_sphere_mm", "alpha_deg", "phi_deg", "Fx_B0_raw_mN",
        "Fx_B1_raw_mN", "Fx_B2_raw_mN", "Fx_hold_corr_mN",
        "DeltaFx_ball_mN", "Fx_total_corr_mN", "Fy_total_corr_mN",
        "Fz_total_corr_mN", "elements", "DOF", "min_quality",
    ]
    rows[numeric] = rows[numeric].apply(pd.to_numeric)
    return rows.sort_values(["z_sphere_mm", "phi_deg"]).reset_index(drop=True)


def summarize(rows: pd.DataFrame) -> pd.DataFrame:
    records = []
    for z, group in rows.groupby("z_sphere_mm", sort=True):
        group = group.sort_values("phi_deg")
        if not np.array_equal(group["phi_deg"].to_numpy(), EXPECTED_PHI):
            raise ValueError(f"Incomplete phase cycle at z={z}: {group['phi_deg'].tolist()}")
        if not group["status"].eq("SUCCESS").all():
            raise ValueError(f"Non-success row at z={z}")
        maximum = group.loc[group["Fx_total_corr_mN"].idxmax()]
        minimum = group.loc[group["Fx_total_corr_mN"].idxmin()]
        fmax = float(maximum["Fx_total_corr_mN"])
        records.append(
            {
                "z_sphere_mm": z,
                "alpha_deg": -20.0,
                "Fmin_mN": float(minimum["Fx_total_corr_mN"]),
                "Fmax_mN": fmax,
                "phi_at_Fmax_deg": float(maximum["phi_deg"]),
                "phi_at_Fmin_deg": float(minimum["phi_deg"]),
                "DeltaFx_ball_at_Fmax_mN": float(maximum["DeltaFx_ball_mN"]),
                "Fx_hold_corr_mN": float(maximum["Fx_hold_corr_mN"]),
                "all_sampled_phi_negative": fmax < 0.0,
                "release_candidate": fmax > 0.0,
                "elements": int(maximum["elements"]),
                "DOF": int(maximum["DOF"]),
                "min_quality": float(maximum["min_quality"]),
                "zero_360_closure_error_mN": abs(
                    float(group.iloc[0]["Fx_total_corr_mN"])
                    - float(group.iloc[-1]["Fx_total_corr_mN"])
                ),
                "status": "SUCCESS",
            }
        )
    return pd.DataFrame.from_records(records)


macro = load_cycles(MACRO)
refine = load_cycles(REFINE)
if macro.shape[0] != 54 or refine.shape[0] != 27:
    raise ValueError(f"Unexpected row counts: macro={len(macro)}, refinement={len(refine)}")

macro.to_csv(DATA / "modelB_alpha_minus20_sparse.csv", index=False)
refine.to_csv(DATA / "modelB_alpha_minus20_refinement.csv", index=False)
minus20_summary = summarize(pd.concat([macro, refine], ignore_index=True))
minus20_summary.to_csv(DATA / "modelB_alpha_minus20_Fmax_summary.csv", index=False)

alpha0 = pd.read_csv(DATA / "modelB_x26_macro_Fmax_summary.csv")
alpha20 = pd.read_csv(DATA / "modelB_alpha20_Fmax_summary.csv")
common_z = [60, 80, 100, 120, 140, 160]
comparison = []
for z in common_z:
    mn = minus20_summary.loc[np.isclose(minus20_summary["z_sphere_mm"], z)].iloc[0]
    a0 = alpha0.loc[np.isclose(alpha0["z_sphere_mm"], z)].iloc[0]
    p20 = alpha20.loc[np.isclose(alpha20["z_sphere_mm"], z)].iloc[0]
    comparison.append(
        {
            "z_sphere_mm": z,
            "Fmax_minus20_mN": mn["Fmax_mN"],
            "phi_Fmax_minus20_deg": mn["phi_at_Fmax_deg"],
            "DeltaFx_ball_minus20_mN": mn["DeltaFx_ball_at_Fmax_mN"],
            "Fmax_alpha0_mN": a0["Fmax_mN"],
            "phi_Fmax_alpha0_deg": a0["phi_at_Fmax_deg"],
            "DeltaFx_ball_alpha0_mN": a0["DeltaFx_ball_at_Fmax_mN"],
            "Fmax_plus20_mN": p20["Fmax_mN"],
            "phi_Fmax_plus20_deg": p20["phi_at_Fmax_deg"],
            "DeltaFx_ball_plus20_mN": p20["DeltaFx_ball_at_Fmax_mN"],
            "Fx_hold_corr_mN": mn["Fx_hold_corr_mN"],
        }
    )
pd.DataFrame(comparison).to_csv(
    DATA / "modelB_alpha_minus20_0_plus20_comparison.csv", index=False
)

m02_files = [
    WORK / "modelB_alpha_minus20_M02_z60_phi90_20260913" / "modelB_alpha_minus20_M02_validation.csv",
    WORK / "modelB_alpha_minus20_M02_z115_phi90_20260913" / "modelB_alpha_minus20_M02_validation.csv",
]
m02 = pd.concat((pd.read_csv(path) for path in m02_files), ignore_index=True)
if len(m02) != 2 or not m02["status"].eq("SUCCESS").all():
    raise ValueError("Expected two successful alpha=-20 M02 rows")
m02.to_csv(DATA / "modelB_alpha_minus20_M02_validation.csv", index=False)

print(minus20_summary.to_string(index=False))
print("\nM02 validation:\n", m02.to_string(index=False))
