# COMSOL 调试记录

## 2026-09-11：固定姿态稀疏高度扫描

- 症状：Windows `Start-Process` 后立即退出，日志报 `ClassNotFoundException: Files\\COMSOL\\...\\plugins\\*`。
- 原因：COMSOL 插件类路径包含空格，PowerShell `-ArgumentList` 数组形式没有保留完整引号，导致 `-cp` 参数在 `Program Files` 处分裂。
- 可用方案：将完整参数作为一个带嵌入引号的参数字符串传给 `Start-Process -ArgumentList`，并用独立 stdout/stderr 文件验证 JVM 已进入 COMSOL。
- 版本：COMSOL 6.3，Windows 11。

## 2026-09-11：稀疏周期与 AIR 诊断

- 症状：需要同一高度复用网格，但 AIR 不能对每个角度重复计算。
- 方案：在已验证的静态脚本中增加 `SPARSE` 和 `CANDIDATE` 入口；每个高度调用一次 `geom.run()` 与 `mesh.run()`，ON 姿态按角度求解，AIR 只在 phi=0 求解；每个结果立即追加并 flush 到 CSV。
- 约束：360°独立求解，只用于闭合检查，不重复计入周期均值；插值另存并标记 `INTERPOLATED`。
- 版本：COMSOL 6.3，Windows 11。

## 2026-09-11：最终几何测量与局部磁力网格审计

- 症状：用 `component.measure()` 或在 `GeomMeasureFinal.selection()` 尚未初始化时调用 `clear()`，会返回 `No_entity_dimension_specified`；直接用 `comp.geom("geom1").measureFinal()` 后再清空同一个 measurement 对象也可能复现该错误。
- 原因：COMSOL 6.3 的最终几何测量选择需要先明确 `geom("geom1",3)` 再 `set(int[])`；对未初始化的 selection 调用 `clear()` 不安全。
- 可用方案：每个待测域新建一个 `geom("geom1").measureFinal()` 对象，按 `selection().geom("geom1",3); selection().set(new int[]{domain}); getVolume()/getBoundingBox()` 顺序读取；不要在第一次测量前调用 `clear()`。
- 新发现：组件空间选择的类型是 `Box`，属性为 `entitydim,xmin,xmax,ymin,ymax,zmin,zmax,condition`。用边界维度 `entitydim=2`、`condition=intersects` 可稳定选中近场边界；网格 `Size` 用 `selection().named("sel")` 绑定该选择。该方法不改变几何拓扑，只细化边界邻接的空气单元。
- 验证：z=140 mm、phi=90 deg，局部边界 `hmax=0.070/0.047/0.035 mm` 的单元数为 `263920/557996/1036190`，但 Fx 为 `-0.486299/-0.072941/+0.210720 mN`，因此力积分尚未网格收敛。
- 失败边界：用 `PartitionDomains` 切分包含钢片/圆柱的外空气域，在本模型上长时间停留于几何布尔阶段且无输出；未将其用于正式模型。清空固体物理选择的零净力副本也未取得有效解，不能将失败分支作为物理结果。
- 版本：COMSOL 6.3，Windows 11。

## 2026-09-11：Force Calculation 自动生成表达式审计

- 症状：需要展开 `mfnc/fcal1` 的 Maxwell 应力积分，核对 `Forcex_force_magnet` 的边界贡献。
- 原因/限制：COMSOL 6.3 Java API 可读取 `ForceCalculation` 的公开属性和域选择，但当前模型的 `model.variable()`、`component.variable()` 均为空，`result().numerical()` 也为空；自动生成的应力变量没有作为可枚举模型树变量暴露。
- 可用方案：只记录已实际读取的 `ForceName=force_magnet`、`selection domain=[3]`、`useAverage=1` 和 StudyStep；用官方 Application Library 的 Force Calculation 示例确认变量可通过 GUI Expression autocomplete 查找，但不把示例中的 `nToutx_FEM_rod` 等名称套到本模型。未取得当前模型的确切应力变量前，不做边界分解或自写 Maxwell 张量。
- 结果：`force_boundary_contributions.csv` 明确标记 `API_LIMITATION`；闭合空气包络面因本模型 0.28 mm 负 x 间隙及 PartitionDomains 布尔测试停滞，标记 `NOT_BUILT_GEOMETRY_LIMITATION`，没有填零或伪造独立力。
- 版本：COMSOL 6.3，Windows 11。
