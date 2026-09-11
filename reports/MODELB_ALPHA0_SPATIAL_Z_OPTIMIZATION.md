# Model B：alpha=0 的空间 z 粗扫与释放候选复核

日期：2026-09-11

## 执行范围

本轮基于 commit `19b5acc`，固定 `x_sphere=26 mm`、`gap=0.30 mm`、
`alpha=0°`、`phi=90°`，使用 COMSOL 6.3、Stationary、PARDISO、`stol=1e-6`。
局部网格采用已验证的 LOCAL_M03：远场 `hauto=6`，钢片/圆柱/磁球固体域
`hauto=1`，机器人及钢片近场 11 个真实边界使用 `hmax=0.03 mm`、
`hmin=0.01 mm`、`hgrad=1.3`。没有使用全域 `hauto=1`。

每个实际高度均独立完成几何和网格构建，随后在同一网格上求解 B0/B1/B2：

`Fx_hold_corr=B1-B0`，`DeltaFx_ball=B2-B1`，
`Fx_total_corr=B2-B0`。

## alpha=0 的 M02 复核

复用此前 LOCAL_M02 z=120 检查点，确认 B0/B1 来自同一张 1,936,465 单元网格，
仅新增 B2(alpha=0)。

| mesh | Fx_hold_corr (mN) | DeltaFx_ball (mN) | Fx_total_corr (mN) | elements | DOF | status |
|---|---:|---:|---:|---:|---:|---|
| LOCAL_M03 | -0.128144033 | +0.074230661 | -0.053913373 | 871,685 | 1,163,440 | SUCCESS |
| LOCAL_M02 | -0.128580748 | +0.073872809 | -0.054707939 | 1,936,465 | 2,583,434 | SUCCESS |

M03→M02 的 `Fx_total_corr` 绝对变化为 `0.000794566 mN`，
`DeltaFx_ball` 绝对变化为 `0.000357852 mN`，均保持同号；因此 alpha=0 的
释放趋势通过本轮工程筛选。M02 PARDISO 峰值内存约 30 GB，作为复核有效，
但不用于整组 z 扫描。

## alpha=0 的 z 粗扫

按规则在第一次出现正值后立即停止，因此九个计划高度中只完成并保留 z=60 mm；
z=70 mm 已开始网格构建但未求解，未作为结果使用。z=60 的球体 bbox 为
`z=35–85 mm`，几何身份检查通过。

| z (mm) | Fx_hold_corr (mN) | DeltaFx_ball (mN) | Fx_total_corr (mN) | elements | DOF | status |
|---:|---:|---:|---:|---:|---:|---|
| 60 | -0.128277997 | +0.281045949 | +0.152767952 | 871,414 | 1,163,067 | MAGNETIC_RELEASE_CANDIDATE |

该点是当前 alpha=0 条件下的首次实算释放候选。这里的“首次”仅表示按本轮
从 z=60 开始的停止规则捕获的第一个正值点，不表示 60 mm 是连续 z 区间的数学边界。

随后按边界规则补算 z=50 mm：

| z (mm) | Fx_hold_corr (mN) | DeltaFx_ball (mN) | Fx_total_corr (mN) | elements | DOF | status |
|---:|---:|---:|---:|---:|---:|---|
| 50 | -0.128182242 | -0.121683015 | -0.249865256 | 872,085 | 1,163,932 | MAGNETICALLY_HELD |

z=50 比 z=60 明显更负，说明该方向的释放力不是随 z 单调增强；不能据此把
z=60 解释为连续最优点。z=40 没有启动。

## z=60 的 phi 复核

固定 z=60、alpha=0，使用一张 LOCAL_M03 网格，B0/B1 各求解一次，只对 B2
扫描 phi=60–120°。

| phi (deg) | DeltaFx_ball (mN) | Fx_total_corr (mN) |
|---:|---:|---:|
| 60 | +0.242837575 | +0.114559578 |
| 70 | +0.263734068 | +0.135456071 |
| 80 | +0.276604609 | +0.148326612 |
| 90 | +0.281045949 | +0.152767952 |
| 100 | +0.276918750 | +0.148640753 |
| 110 | +0.264352347 | +0.136074351 |
| 120 | +0.243740350 | +0.115462353 |

最大值仍在 phi=90°，且全部 7 个相位均为释放候选；本轮没有必要做 5°细化。

## 回答本轮问题

1. alpha=0 的 LOCAL_M02 结果：`Fx_total_corr=-0.054707939 mN`，
   `DeltaFx_ball=+0.073872809 mN`。
2. alpha=0 下 z 粗扫的当前实算最佳点：z=60 mm，
   `Fx_total_corr=+0.152767952 mN`，`DeltaFx_ball=+0.281045949 mN`。
3. 已出现 `Fx_total_corr>0`，标记为 `MAGNETIC_RELEASE_CANDIDATE`。
4. z=60 的 phi=60–120°复核仍以 phi=90°最大，峰值为 `+0.152767952 mN`。
5. z=60 的 LOCAL_M02 三状态复核已完成：
   `Fx_hold_corr=-0.128628179 mN`、`DeltaFx_ball=+0.261659979 mN`、
   `Fx_total_corr=+0.133031800 mN`，1,935,733 个单元、2,582,448 DOF、
   最小质量 0.2004，状态为 `MAGNETIC_RELEASE_CANDIDATE`。与 LOCAL_M03 的
   `+0.152767952 mN` 同号，绝对差为 `0.019736152 mN`；因此 z=60 的正号
   在两档网格下可复现，但该差异仍大于 z=120 的 M03→M02 差异，不能称为
   完整网格收敛证明。
6. 当前不再扩大 z 扫描。下一设计变量按计划应是 `x_sphere`，但需另行设计扫描，
   不能与 z 同时变化。

## 限制与文件

本轮是固定姿态准静态差分结果，不是完整动态周期结论。z=60、alpha=0、phi=90°
的 `Fx_total_corr` 正号已在 LOCAL_M03 与 LOCAL_M02 两档网格下得到，当前可称为
“两档局部网格下可复现的释放候选”；但 M03→M02 的约 0.0197 mN 变化说明仍需
更严格的局部误差审计，不能把它表述为最终精确力值。

- [alpha=0 M02 复核](../data/modelB_z120_alpha0_M02_validation.csv)
- [z 粗扫结果](../data/modelB_alpha0_z_coarse_scan.csv)
- [z=50 局部复核](../data/modelB_alpha0_z_local_refinement.csv)
- [z=60 phi 扫描](../data/modelB_alpha0_z60_phi_scan.csv)
- [z=60 M02 候选复核](../data/modelB_alpha0_z60_M02_validation.csv)
- [z/phi 图](../figures/modelB_alpha0_z60_phi_scan.png)
- [执行源码](../scripts/RunModelBLocalMeshConvergence.java)
- [z 粗扫启动脚本](../scripts/RunModelBAlpha0ZCoarse.ps1)
- [释放候选复核脚本](../scripts/RunModelBReleaseRefinement.ps1)
- [M02 候选复核脚本](../scripts/RunModelBFinalCandidateM02.ps1)

大型求解检查点保存在本地 `work/`，不提交到 GitHub。

## 后续：固定 z=60 的 x_sphere 横向位置探索

在 z=60 mm、alpha=0°、phi=90°、gap=0.30 mm 下，继续保持
Stationary、PARDISO、stol=1e-6 和 B0/B1/B2 同网格差分。由于 x=26 mm
时球体左端与圆柱右端相切，本次只向 x+ 方向测试 26–36 mm，避免几何穿透。
每个位置均独立重建几何和 LOCAL_M03 网格，6/6 个位置成功。

| x_sphere (mm) | DeltaFx_ball (mN) | Fx_hold_corr (mN) | Fx_total_corr (mN) | elements | status |
|---:|---:|---:|---:|---:|---|
| 26 | +0.281045949 | -0.128277997 | +0.152767952 | 871,414 | MAGNETIC_RELEASE_CANDIDATE |
| 28 | +0.139834277 | -0.128189449 | +0.011644828 | 872,230 | MAGNETIC_RELEASE_CANDIDATE |
| 30 | +0.000473344 | -0.128199686 | -0.127726342 | 872,693 | MAGNETICALLY_HELD |
| 32 | -0.091417353 | -0.128203293 | -0.219620646 | 872,506 | MAGNETICALLY_HELD |
| 34 | -0.207044200 | -0.128200137 | -0.335244336 | 872,284 | MAGNETICALLY_HELD |
| 36 | -0.286636018 | -0.128140533 | -0.414776550 | 872,054 | MAGNETICALLY_HELD |

横向位置结果显示，`Fx_hold_corr` 在全组保持约 -0.1282 mN，说明 B1-B0
基底保持力没有随球位置明显漂移；变化主要来自 `DeltaFx_ball`。在当前
固定姿态下，释放候选从 x=26 的 +0.1528 mN 快速下降到 x=28 的 +0.0116 mN，
并在 x=28–30 mm 之间跨过零；x=30 以后已经明显为负。由于 x=28 的正裕量
小于 z=60 的 M03→M02 幅值变化约 0.0197 mN，x=28 不能视为数值上稳健的
正值，优先候选仍是 x=26 mm。

本次 x 扫描是固定姿态的准静态探索，不是完整旋转周期结论。下一步应优先
对 x=26 mm 做需要的角度或更严格空间验证；若目标是定位横向临界位置，
再仅在 x=28–30 mm 之间做少量 0.25–0.5 mm 的局部复核，不必继续扫描更远位置。

- [x 位置扫描](../data/modelB_alpha0_x60_scan.csv)
- [x 扫描脚本](../scripts/RunModelBAlpha0XScan.ps1)
