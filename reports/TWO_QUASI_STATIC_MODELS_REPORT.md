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

## gap=0.30 mm：钢片 ON/OFF 同网格控制与原生 Force Probe（2026-09-11）

本轮没有启动 Model B，也没有做高度/角度扫描。首先在已经保存的两档 Model A MPH 上复用原有锁定网格，只切换钢片的磁性配置：ON 恢复源模型 `mfcs2` 的全部属性，OFF 保留钢片几何但切换为 `RelativePermeability`、`mur=1` 且关闭剩磁源。两种状态均复用源 `sol1`，没有删除源 Solution 数据集。

### 同网格钢片控制结果

| mesh | h (mm) | elements | DOF | min quality | Fx OFF (mN) | Fx ON (mN) | ON-OFF Fx (mN) | 同网格 | 状态 |
|---|---:|---:|---:|---:|---:|---:|---:|---|---|
| M_h003 | 0.030 | 194131 | 259754 | 0.1606 | -0.247610304802 | -0.375970317168 | -0.128360012366 | yes | SUCCESS |
| M_h002 | 0.020 | 423088 | 565385 | 0.1446 | -0.097071472523 | -0.225873547608 | -0.128802075085 | yes | SUCCESS |

ON 与 OFF 的横向力差在两档网格下分别为 `-0.128360` 和 `-0.128802 mN`，变化只有约 `0.000442 mN`（约 0.34%）。这说明同一锁定网格下，钢片磁性配置对本模型 Fx 的贡献是可重复的；但它不等于直接表面净力已经完成空间网格收敛。钢片审计的实际标签为 `UNS S30415 [solid]`，源 `mfcs2` 为 `RelativePermeability`、`mur_mat=from_mat`、`mur=1`、`Br_mat=from_mat`、`Br=0`，因此本轮记录的是当前 MPH 的真实配置，不宣称它已经代表目标实验钢材。

### 原生第二个 Force Calculation Probe

为避免手写 `unTmx` 汇总，脚本在空气中加入小型 `PartitionDomains` 包络域，并创建第二个原生 `ForceCalculation`（`fcal_probe`，`ForceName=force_probe`，选择 `[cylinder domain 3, probe air domain 4]`）。P1 包络为 `x[-1.08,1.20] mm, y/z[-0.55,0.55] mm`，P2 为 `x[-1.10,1.30] mm, y/z[-0.65,0.65] mm`。

| mesh | probe | elements | DOF | min quality | direct fcal1 Fx (mN) | native probe Fx (mN) | abs relative diff |
|---|---|---:|---:|---:|---:|---:|---:|
| M_h003 | P1 | 158142 | 212291 | 0.01530 | -8.36013565201 | -9.54257532068 | 14.14% |
| M_h003 | P2 | 177611 | 237956 | 0.01265 | -11.0472319345 | -12.4214869049 | 12.44% |
| M_h002 | P1 | 381580 | 510747 | 0.01124 | -10.2869015466 | -11.0680543216 | 7.59% |
| M_h002 | P2 | 400475 | 535656 | 0.01354 | -17.2672539379 | -18.2096504229 | 5.46% |

四组 Probe 均可编译、建几何、重网格、求解并保存 MPH，但 P1/P2 不满足包络面位置不敏感条件，且包络副本最小单元质量明显低于原始网格。加入包络分区后网格拓扑发生变化，因此这些 `-8~-18 mN` 数值不能直接与原始未分区 `-0.376/-0.226 mN` 作物理幅值比较；它们只能说明该原生 Probe 分支已经运行，并暴露出当前包络几何/网格/Force Calculation 选择仍未形成独立可信力验证。

### 当前判定与代码修复

1. 之前同网格 ON=OFF 的中间结果无效，原因是 OFF 后只恢复材料选择，没有恢复 `mfcs2` 的 `mur_mat/mur/Br_mat/Br`；现已逐项捕获并恢复，最终 ON 与既有直接法结果一致。
2. 删除源 Solution 数据集或重新 `createAutoSequences("all")` 会在大网格上造成长时间阻塞；现改为保留源 `sol1`，直接 `runAll()`。
3. `PartitionDomains` 必须设置 `partitionwith="objects"`；其 geometry-operation selection 应使用 `selection("domain").all()`，不能把最终域编号当作输入几何对象标签。
4. 目前钢片 ON/OFF 控制已通过；原生包络 Probe 已成功执行但位置稳定性未通过。不能据此宣称表面力已经收敛，也不能用 Probe 数值替换正式 Model A 结果。

新增文件：

- [gap030_same_mesh_steel_on_off.csv](../data/gap030_same_mesh_steel_on_off.csv)
- [modelA_gap030_steel_material_audit.txt](../data/modelA_gap030_steel_material_audit.txt)
- [gap030_force_probe_native.csv](../data/gap030_force_probe_native.csv)
- [RunGap030SameMeshControl.java](../scripts/RunGap030SameMeshControl.java)
- [RunGap030NativeForceProbe.java](../scripts/RunGap030NativeForceProbe.java)

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

## 2026-09-11：同网格差分力验证与 Model B 稀疏姿态分解

本轮采用同网格差分作为主要诊断量。Model A 对每个 gap 只重建一次
几何和网格，然后在完全相同的网格上计算钢片 OFF/ON；修正保持力定义为
`Fx_hold = Fx_ON - Fx_OFF`。h≈0.03 mm 和 h≈0.02 mm 两套网格的 0.25–0.30
mm 六个 gap 点均成功，最大相对差异约 0.552%，低于临时 2% 工程门槛。
这说明钢片差分贡献在该设置下稳定，但不等于原始表面净力已经完成空间收敛。

Model B 在 gap=0.30 mm、x_sphere=26 mm、50 mm 球、alpha=30°下完成
z=120/140/150 mm 的稀疏固定姿态计算。每个高度只建一次网格，状态定义为
B0（球/钢片 OFF）、B1（球 OFF/钢片 ON）、B2（球 ON/钢片 ON），并使用
`Fx_total_corr=B2-B0`。33 个数据行全部成功；9 个相位采样下三种高度的
`Fx_total_corr` 都为负，360°独立求解与0°闭合误差约为 1e-10 mN 量级。

需要特别区分原始值和修正值：B0 的原始 Fx 随球位置明显变化，但 B1−B0
在三种高度间仅变化约 0.000277 mN。这表明当前 Force Calculation 存在
位置相关的共同数值自力/背景项；同网格差分在本受控比较中有效降低了该项，
但不能把它用于不同网格、不同物理配置或动态问题的任意校正。

本轮保留了此前无效体积力变量、包络 Probe 和直接原始净力的失败证据，
没有覆盖旧结果。新增完整数据、源码、日志和图表见独立报告
`reports/SAME_MESH_DIFFERENTIAL_20260911.md`。

## 2026-09-11：z=110–140 mm 局部细扫与 z=120 mm 角度复核

在保持现有物理配置的前提下，新增 110、112.5、115、117.5、120、122.5、
125、127.5、130、132.5、135、137.5、140 mm，并且每个高度只计算
phi=90°的 B0/B1/B2 同网格差分。13个高度全部成功，最大实算值为
z=120 mm 的 `Fx_total_corr=-0.085210451 mN`，因此没有生成正向释放候选。
随后对 z=120 mm 做0–360°、45°间隔的独立角度复核，最小值
`-0.219998493 mN`（270°），最大值 `-0.085210451 mN`（90°），全部采样点
为负，360°−0°闭合误差约 `1.9e-10 mN`。

按要求尝试了最终 h≈0.02 mm 复核，但源模型原生 hauto=1 约1.24M单元，
B0 的 PARDISO 求解耗尽系统内存，未能取得可读力值。该分支已停止并保留
日志，不能用 h≈0.03 mm 结果替代；因此当前局部最优结论仍仅在 h≈0.03 mm
上成立。完整结果见 `reports/Z_FINE_LOCAL_OPTIMUM_20260911.md`。

## 2026-09-11：降低 z 的四高度同网格稀疏扫描

沿用同一份 Model B B0/B1/B2 差分脚本，在 z=110、100、90、80 mm 各完成
0–360°、45°间隔的9个独立固定姿态。四个高度共44行全部 SUCCESS，
`Fx_total_corr` 的最大值分别为 -0.095744、-0.111991、-0.099860、
-0.162555 mN，均未跨过零；四个高度的最大值均出现在 phi=90°。

因此 80–110 mm 采样范围内没有发现首次负到正的候选区间，按本轮停止规则
不进行角度加密。`Fx_hold_corr=B1-B0` 保持在 -0.124214 至 -0.125093 mN
之间，说明差分保持力仍稳定；原始 B0 self-force 则随 z 明显漂移，不能
作为物理保持力解释。完整表格和趋势图见
`reports/LOWER_Z_SPARSE_SCAN_20260911.md`。
