# Force Calculation 审计（z=140 mm, phi=90°, L1）

日期：2026-09-11  
模型：`H:\comsolcc\local_force_audit_140_phi90_20260911\L1\solved.mph`  
审计源码：`scripts/ForceCalculationAudit.java`  
审计性质：只读，不求解、不修改 MPH。

源码验证：COMSOL 6.3 `comsolcompile.exe` 编译退出码 `0`；使用 COMSOL 自带 Java 重新加载同一 L1 模型，审计退出码 `0`，输出见 `data/forcecal_audit_140_l1_20260911.txt`。

## 结论

当前 L1 工况的 `mfnc/fcal1` 配置可以从 COMSOL 6.3 Java API 直接核验；但该 API 没有把 Force Calculation 自动生成的 Maxwell 应力展开式暴露为可枚举的模型变量、组件变量或结果数值特征。因此本轮不能在没有 GUI 表达式自动完成核验的情况下，编造 `Tx/Ty/Tz` 或边界积分表达式。

已有数值证据仍然有效，但只说明当前力读数对离散设置敏感：

| 对照 | Fx (mN) |
|---|---:|
| L0, hgap=0.070 mm | -0.486298993 |
| L1, hgap=0.047 mm | -0.072940878 |
| L2, hgap=0.035 mm | +0.210720222 |
| R=200 mm, L1 | -0.072940878 |
| R=300 mm, L1 | +0.048568685 |
| R=400 mm, L1 | -0.025123716 |

局部网格 L0→L2 的变化约 0.697019 mN，并翻号；外域 R=200→400 mm 也有约 0.047817 mN 的端点差异。固定空气探针的 B 场变化远小于 Fx，符合“应力积分中较大正负贡献抵消后，净值对边界通量误差敏感”的可能机制，但不能仅凭探针证明积分实现错误。

## Force Calculation 实际配置

来自 `forcecal_audit_140_l1_20260911.txt` 的原始输出：

```text
FCAL tag=fcal1 type=ForceCalculation dom=[3] bnd=[]
FCAL_PROPERTY ForceName=[force_magnet]
FCAL_PROPERTY TorqueAxis=[1, 0, 0]
FCAL_PROPERTY TorqueRotationPoint=[0, 0, 0]
FCAL_PROPERTY useAverage=[1]
FCAL_PROPERTY StudyStep=[st_audit_l1_z140_p90/stat]
FCAL_PROPERTY showPhysicsSymbols=[1]
```

因此当前可确认：

- 特征标签：`fcal1`；
- 特征类型：`ForceCalculation`；
- 域选择：最终域 `[3]`，即已核验的圆柱受力对象；
- ForceName：`force_magnet`；
- 力读取表达式：`mfnc.Forcex_force_magnet`；
- `useAverage=1` 已实际设置；
- 该特征绑定到 `st_audit_l1_z140_p90/stat`；
- 特征本身没有边界选择，边界由所选域的外部边界及 COMSOL 内部实现处理。

## Java API 暴露范围

审计得到：

```text
MODEL_VARIABLE_COLLECTIONS=[]
COMP_VARIABLE_COLLECTIONS=[]
ALL_RESULT_NUMERICAL=[]
ALL_RESULT_DATASET=[dset1]
```

这表示通过当前 Java API 入口没有读到包含 `force_magnet`、`Forcex`、`Maxwell` 或 `stress` 的用户变量集合，也没有现成 numerical 节点可供展开。可读取的是 Force Calculation 节点的公开属性，而不是它编译后内部生成的完整积分树。

COMSOL 6.3 本地 Application Library 示例：

`I:\Program Files\COMSOL\COMSOL63\Multiphysics\doc\help\wtpwebapps\ROOT\doc\com.comsol.help.models.acdc.force_calculation_03_magnetic_torque_bem\force_calculation_03_magnetic_torque_bem.html`

该官方示例说明：Force Calculation 会在所选域的外部边界上积分 Maxwell surface stress tensor；当表面场在极点附近高度集中时，辅助的空气 force-probe surface 可以改善精度。示例还说明自动生成的变量可通过 GUI 表达式自动完成查找，例如示例自己的 `mfnc.nToutx_FEM_rod`。这些示例变量属于示例特征命名，不能直接套用于本模型的 `force_magnet`。

## 边界贡献分解状态

本轮没有生成 `Fx_minus_x_face`、`Fx_plus_x_face`、`Fx_side` 数字，因为当前模型中尚未获得经 GUI 自动完成核验的应力分量变量。用单边界 `mfnc.Tz` 或自行写 Maxwell 张量会重新引入此前已经否定的做法：局部面不闭合，且表达式未证明与 `fcal1` 的 `useAverage=1` 实现一致。

结果文件 `data/force_boundary_contributions.csv` 使用空数值字段和 `API_LIMITATION` 状态，明确表示未计算，不用 0 伪装结果。

## 合法闭合空气包络面状态

结果文件 `data/closed_surface_force.csv` 标记为 `NOT_BUILT_GEOMETRY_LIMITATION`。原因是当前圆柱负 x 侧到钢片只有约 0.28 mm 空气间隙，前一次用 `PartitionDomains` 切出机器人附近空气盒时，几何布尔阶段长时间无进展且没有生成可验证模型；该尝试已停止，没有将其当成成功。

当前没有可证明满足以下条件的 S1/S2：完整闭合、只包围圆柱、全在空气中、不穿过钢片/磁球/圆柱、内部界面连续且未施加错误 Magnetic Insulation。因此没有输出闭合面积分数字。

## 零净力/虚功分支

零净力副本曾停在配置阶段，未获得有效解；不能把它当作 `Fx=0`。本轮也没有使用未经确认的永磁体能量关系生成虚功力，因此没有 `virtual_work_force.csv`。

## 当前判断与下一步门槛

当前最可靠的判断是：L1 的 `fcal1` 结果与局部近场离散和外域截断均未收敛；净力对边界场误差敏感是合理怀疑，但“抵消比 C”尚未能由真实 fcal1 应力项定量计算。

要完成独立力验证，下一步必须在 COMSOL Desktop 中对 `mfnc.Forcex_force_magnet` 打开表达式自动完成/变量列表，记录当前特征实际生成的 `nTout*` 或等价变量全名；随后在 GUI 或导出的原生 Java 中建立最小空气 force-probe surface。若自动生成变量仍不导出，应把限制保留为 `API_LIMITATION`，不能用相似命名替代。

在上述门槛通过前，停止 L3/L4、全周期、高度扫描和基于符号的物理结论。
