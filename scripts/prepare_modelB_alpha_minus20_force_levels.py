"""Assemble the real phi=90 force-level screen and select measured heights."""
from __future__ import annotations

import argparse
from pathlib import Path

import pandas as pd

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "data"
WORK = ROOT / "work" / "modelB_alpha_minus20_force_levels_20260915"
NEW = WORK / "force_level_prescan_new.csv"
OUTPUT = DATA / "modelB_alpha_minus20_force_level_prescan.csv"
SELECTED = DATA / "modelB_alpha_minus20_force_level_selected_targets.csv"
MACRO = DATA / "modelB_alpha_minus20_macro_phi.csv"
REUSED_Z = [100.0, 90.0, 80.0, 70.0, 60.0]
SCAN_ORDER = [106, 104, 102, 98, 96, 94, 92, 88, 86, 84, 82, 78, 76, 74, 72, 68, 66, 64, 62]
TARGETS = [0.10, 0.20, 0.40]
COLUMNS = [
    "z_sphere_mm", "alpha_deg", "phi_deg", "Fx_B0_raw_mN", "Fx_B1_raw_mN", "Fx_B2_raw_mN",
    "Fx_hold_corr_mN", "DeltaFx_ball_mN", "Fx_total_corr_mN", "elements", "DOF", "min_quality",
    "same_mesh_verified", "status",
]


def validate_rows(df: pd.DataFrame, label: str) -> pd.DataFrame:
    df = df[COLUMNS].copy()
    for col in ["z_sphere_mm", "alpha_deg", "phi_deg", "Fx_total_corr_mN"]:
        df[col] = pd.to_numeric(df[col], errors="raise")
    assert (df.alpha_deg == -20).all(), f"{label}: wrong alpha"
    assert (df.phi_deg == 90).all(), f"{label}: wrong phase"
    assert df.status.eq("SUCCESS").all(), f"{label}: non-success row"
    assert df.same_mesh_verified.astype(str).str.lower().eq("true").all(), f"{label}: mesh mismatch"
    assert df.z_sphere_mm.is_unique, f"{label}: duplicate z"
    return df


def prepare(partial: bool) -> pd.DataFrame:
    old = pd.read_csv(MACRO)
    old = old[(old.alpha_deg.astype(float) == -20) & (old.phi_deg.astype(float) == 90) & old.z_sphere_mm.astype(float).isin(REUSED_Z)]
    old = validate_rows(old, "reused macro points")
    if not NEW.exists():
        raise FileNotFoundError(NEW)
    new = validate_rows(pd.read_csv(NEW), "new prescan points")
    assert set(new.z_sphere_mm.astype(float)).issubset(set(map(float, SCAN_ORDER)))
    assert not set(new.z_sphere_mm.astype(float)) & set(REUSED_Z)
    merged = pd.concat([new, old], ignore_index=True).sort_values("z_sphere_mm", ascending=False).reset_index(drop=True)
    assert merged.z_sphere_mm.is_unique
    OUTPUT.parent.mkdir(exist_ok=True)
    merged.to_csv(OUTPUT, index=False, float_format="%.12g")
    if not partial:
        measured = set(new.z_sphere_mm.astype(float))
        completed = [z for z in SCAN_ORDER if float(z) in measured]
        assert completed == SCAN_ORDER[: len(completed)], (completed, SCAN_ORDER)
        hit = [z for z in completed if float(new.loc[new.z_sphere_mm == z, "Fx_total_corr_mN"].iloc[0]) >= 0.45]
        assert hit and hit[0] == completed[-1], (completed, hit)
        assert len(hit) == 1
        rows = []
        pool = merged[merged.z_sphere_mm.astype(float).between(60, 106)].copy()
        for target in TARGETS:
            idx = (pool.Fx_total_corr_mN.astype(float) - target).abs().idxmin()
            row = pool.loc[idx]
            rows.append({
                "target_force_mN": target,
                "selected_z_mm": float(row.z_sphere_mm),
                "actual_Fx_phi90_mN": float(row.Fx_total_corr_mN),
                "absolute_error_mN": abs(float(row.Fx_total_corr_mN) - target),
            })
        selected = pd.DataFrame(rows)
        assert selected.selected_z_mm.nunique() == 3, selected
        selected.to_csv(SELECTED, index=False, float_format="%.12g")
        print(f"PRESCAN_VALID rows={len(merged)} new={len(new)} threshold_z={completed[-1]} selected_z={selected.selected_z_mm.tolist()}")
    else:
        print(f"PARTIAL_PRESCAN_VALID rows={len(merged)} new={len(new)}")
    return merged


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--partial", action="store_true", help="allow the unfinished scan and skip target selection")
    args = parser.parse_args()
    prepare(args.partial)
