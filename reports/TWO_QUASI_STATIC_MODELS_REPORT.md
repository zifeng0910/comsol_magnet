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

## gap=0.30 mm：表面力与体积力变量交叉核验

本次新增验证没有修改原六点扫描 CSV 或既有 MPH。脚本对两个独立模型副本分别重新求解，并用最终几何包围盒核验圆柱域为域 3：

| mesh level | h (mm) | Fx_surface (mN) | Fx_volume (mN) | relative_diff | elements | DOF | status |
|---|---:|---:|---:|---:|---:|---:|---|
| M_h003 | 0.030 | -0.375970317180 | unavailable | unavailable | 194131 | 259754 | NO_VALID_VOLUME_FORCE_VARIABLE |
| M_h002 | 0.020 | -0.225873547613 | unavailable | unavailable | 423088 | 565385 | NO_VALID_VOLUME_FORCE_VARIABLE |

实际试探并失败的候选变量包括：`mfnc.FLtzx/y/z`、`mfnc.ForceDensityx/y/z`、`mfnc.fex/y/z`、`mfnc.fLx/y/z`。COMSOL 6.3 文档将 `FLtz` 定义为 `J×B` 洛伦兹力密度，适用于载流导体；同时说明对于永磁体和磁性材料，精确体积力分布通常不可直接获得，通用总力应使用 Maxwell 应力或虚功法。因此不能把这些不存在或无法求值的变量填成 0，也不能把 Kelvin 表达式未经材料本构验证就作为独立总力。

本次结论是：两种方法没有得到可比较的 `Fx_volume`，所以 `relative_diff` 不适用，不能据此判断表面法和体积法吻合或不吻合。已确认的数值事实仍是表面法端点加密后变化约 40%，下一步应采用合法空气闭合包络面的 COMSOL 原生 Maxwell 积分或经过本构确认的虚功/能量法，而不是继续猜测体积力变量。

交叉核验文件：[gap030_surface_vs_volume_crosscheck.csv](../data/gap030_surface_vs_volume_crosscheck.csv)；脚本：[RunGap030SurfaceVolumeCrosscheck.java](../scripts/RunGap030SurfaceVolumeCrosscheck.java)。

## gap=0.30 mm：三路交叉验证执行记录（2026-09-11）

本轮首先用 `ProbeMfncStressEnergyVariables.java` 对当前 COMSOL 6.3 模型做了变量自省，确认：

- `mfnc.nToutx_force_magnet` 可作为已配置 Force Calculation 的表面输出；
- 通用单侧表面应力变量 `mfnc.unTmx`、`mfnc.dnTmx` 可求值；
- `mfnc.Tmx`、`mfnc.nTmx` 不可直接求值；
- `mfnc.Wm` 可求值；此前尝试的 `FLtz`、`ForceDensity`、`fex/fLx` 等永磁体体积力密度变量不可用。

直接法读取了已保存的两档 Model A 解，结果与既有报告一致：

| mesh | h (mm) | direct surface Fx (mN) | cancellation ratio | elements | DOF | 状态 |
|---|---:|---:|---:|---:|---:|---|
| M_h003 | 0.030 | -0.375970317180 | 287.70 | 194131 | 259754 | 已从保存模型复核 |
| M_h002 | 0.020 | -0.225873547613 | 486.096747 | 423088 | 565385 | 已从保存模型复核 |

本轮新增的包络面脚本 `RunGap030ThreeWayCrosscheck.java` 已编译，但在首个 `mesh.run()` 阶段长时间高负载未返回；为避免把未完成的包络面结果写成有效数据，已停止自己启动的测试进程。包络面行在新 CSV 中明确标记为 `NOT_COMPLETED_MESH_TIMEOUT`，没有填 0 或复用直接法结果。

`mfnc.Wm` 的快速探针已在 M_h003 的 gap=0.29/0.30 两个已保存解上求值，但只覆盖圆柱域3，不能替代全域、0.295/0.305 两个中心差分点。因此虚功法目前只记录为 `ONE_SIDED_DOMAIN3_PROBE_ONLY`，不能据此给出最终 `Fx_virtual_work`。严格的虚功法仍需补齐两个独立 gap 点及匹配网格。

本轮没有得到三种方法的同网格可比表，因此不能声称 `Fx_hold≈-0.3 mN` 已通过独立方法验证。当前最可靠的结论仍是：直接表面法的净力由大幅相反表面贡献相减形成，且 M_h003→M_h002 仍有约 40% 幅值变化；下一步应优先优化包络面副本的网格重建流程，再完成两档包络面和中心差分虚功法，不能继续把直接法净余量用于精细物理结论。

新增记录：

- 三路交叉验证 CSV：[gap030_threeway_crosscheck.csv](../data/gap030_threeway_crosscheck.csv)
- 三路验证脚本：[RunGap030ThreeWayCrosscheck.java](../scripts/RunGap030ThreeWayCrosscheck.java)
- 变量探针：[ProbeMfncStressEnergyVariables.java](../scripts/ProbeMfncStressEnergyVariables.java)
- 已保存模型只读探针：[ProbeModelAForceEnergy.java](../scripts/ProbeModelAForceEnergy.java)
