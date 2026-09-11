# 两个准静态磁力模型：阶段报告

日期：2026-09-11  
软件：COMSOL Multiphysics 6.3  物理接口：Magnetic Fields, No Currents (`mfnc`)

## 结论

本轮先完成了 Model A：钢片 + 圆柱磁铁 + Air，远处 50 mm 磁球从几何和物理中移除。六个 gap 点在 `Stationary + PARDISO + stol=1e-6` 下均成功求解，实际测得的 gap 与目标值一致。

但 Model A 未通过数值可信度门槛，因此按任务约束没有启动 Model B 的旋转磁球计算。原因不是求解失败，而是净保持力对局部网格仍明显敏感：端点从 `h≈0.03 mm` 加密到 `h≈0.02 mm` 后，gap=0.25 mm 的 Fx 从 -0.433319 mN 变为 -0.294791 mN，gap=0.30 mm 的 Fx 从 -0.375970 mN 变为 -0.225874 mN。当前结果可称为“求解成功”，不能称为“保持力已收敛”。

## Model A 配置

- 几何：圆柱磁铁、钢片、外部 Air；远处球体 `sph1` 从几何链删除，`mfc2` 同步删除。
- 圆柱受力对象：最终域 3，Force Calculation 保持 `force_magnet`。
- 读取变量：`mfnc.Forcex_force_magnet`、`Forcey_force_magnet`、`Forcez_force_magnet`。
- 表面审计变量：`mfnc.nToutx_force_magnet`。
- 钢片沿 x 方向移动，圆柱位置和尺寸不变。
- 固定磁标势参考：`zsp1` 点 20，坐标为模型单位 `[-1, 0, 0.4]`；每次求解前核验。
- 求解器：实际启用 PARDISO，Fully Coupled 指向 `dDef`，Stationary `stol=1e-6`。
- 局部网格：全局 `hauto=6`，圆柱相邻边界和钢片近端面 Size 特征，`hmax≈0.03 mm`，`hmin≈0.01 mm`。

## 六点结果（h≈0.03 mm）

| gap (mm) | measured gap (mm) | Fx (mN) | Fy (mN) | Fz (mN) | lateral (mN) | cancellation ratio | elements | DOF | min quality | status |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| 0.25 | 0.25000000 | -0.433319 | -0.494385 | -0.320220 | 0.589031 | 251.08 | 194203 | 259858 | 0.1662 | SUCCESS |
| 0.26 | 0.25999999 | -0.131474 | -0.546188 | -0.047775 | 0.548273 | 823.68 | 194308 | 259965 | 0.1620 | SUCCESS |
| 0.27 | 0.26999998 | -0.165530 | -0.280540 | 0.253383 | 0.378028 | 653.03 | 193474 | 258861 | 0.08825 | SUCCESS |
| 0.28 | 0.27999997 | -0.467183 | -0.129352 | 0.290209 | 0.317731 | 233.26 | 194017 | 259614 | 0.1772 | SUCCESS |
| 0.29 | 0.28999996 | -0.112064 | 0.536228 | -0.384461 | 0.659812 | 962.52 | 193872 | 259391 | 0.1951 | SUCCESS |
| 0.30 | 0.29999995 | -0.375970 | -0.192057 | 0.034121 | 0.195065 | 287.70 | 194131 | 259754 | 0.1606 | SUCCESS |

表面项的单位已统一为 mN。以 0.25 mm 为例，x-minus 面约 -54.443 mN，x-plus 面约 +54.183 mN，侧壁约 -0.174 mN，三者总和约 -0.433 mN，与内置 Force Calculation 一致。这里的三个面只用于抵消审计，不能分别解释成钢片力或磁球力。

## 端点网格复核（h≈0.02 mm）

| gap (mm) | Fx at h≈0.03 (mN) | Fx at h≈0.02 (mN) | change (mN) | h≈0.02 elements | DOF | min quality |
|---:|---:|---:|---:|---:|---:|---:|
| 0.25 | -0.433319 | -0.294791 | +0.138528 | 423286 | 565671 | 0.2148 |
| 0.30 | -0.375970 | -0.225874 | +0.150097 | 423088 | 565385 | 0.1446 |

端点加密没有改变符号，但改变了净力幅值约 32–40%。因此当前 `Fx_hold` 仍处于“强正负表面贡献抵消后的未收敛余量”状态。

## 表面抵消诊断

六点的 cancellation ratio 为约 233–963。比值越大，说明净力相对于各表面贡献越小，局部场离散误差越容易放大到净力中。六点中 gap=0.29 mm 的抵消最严重；这也解释了为什么相邻 gap 点的净力没有表现出可靠的平滑单调关系。

## Model B 状态

Model B 尚未启动。它必须在 Model A 的 gap=0.30 mm Holding baseline 通过网格/力积分可信度检查后再运行。当前如果直接运行 Model B，会把一个尚未收敛的 `Fx_hold_030` 当成释放基线，不能满足本轮判据。

## 文件

- Java 源码：[RunTwoQuasiStaticModels.java](../scripts/RunTwoQuasiStaticModels.java)
- Model A 六点 CSV：[holding_force_vs_gap_025_030_step001.csv](../data/holding_force_vs_gap_025_030_step001.csv)
- 端点 h≈0.02 mm CSV：[holding_force_endpoints_h002.csv](../data/holding_force_endpoints_h002.csv)
- 结果图：[modelA_holding_force_audit.png](../figures/modelA_holding_force_audit.png)
- h≈0.03 mm 模型：[modelA_final.mph](../work/modelA_holding_025_030_20260911_fixed/modelA_final.mph)
- h≈0.02 mm 端点模型：[modelA_final.mph](../work/modelA_endpoint_h002_20260911_fixed/modelA_final.mph)

## 下一步建议

先不要启动旋转磁球高度/角度扫描。应在 gap=0.30 mm 上固定绝对近场网格和合法闭合力积分，对 `h≈0.03` 与 `h≈0.02` 的净力继续做独立力方法核对；只有净力变化明显小于目标量级后，才把该值用于 Model B 的 `DeltaFx_drive` 和释放候选判定。
