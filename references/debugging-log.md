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

## 2026-09-11：同网格 ON/OFF 与原生 ForceCalculation Probe

- 症状：对已保存大网格模型先删除 studies/solutions 再 `createAutoSequences("all")`，程序长时间无 `SOLVE_START`；删除结果数据集后复用 `sol1` 也会阻塞。
- 原因：COMSOL 6.3 大网格的求解序列重建和结果树重映射可能长时间停滞；另一个逻辑错误是 OFF 后没有恢复 mfcs2 的全部活动属性，导致 ON/OFF 被错误地当成相同物理配置。
- 可用方案：读取源模型的锁定网格和已有 `sol1`，保留 Study/Solution/Dataset，只逐项恢复 `ConstitutiveRelationBH`、`mur_mat`、`mur`、`normBr_crel_BH_RemanentFluxDensity_mat`、`normBr_crel_BH_RemanentFluxDensity`，直接 `sol1.runAll()`。gap=0.30 mm 的 h003/h002 ON-OFF Fx 差值分别为 -0.128360/-0.128802 mN。
- 症状：在已 Form Union 的模型中新增 `PartitionDomains` 后，向 `selection("domain")` 写最终域编号会停滞。
- 原因：geometry-operation selection 引用输入几何对象；且 `PartitionDomains` 使用 object 工具时必须先设置 `partitionwith="objects"`。
- 可用方案：在 `fin` 前插入 Block 与 PartitionDomains，设置 `partitionwith="objects"`，对输入对象选择使用 `selection("domain").all()`，再运行几何；新增空气域后必须重新核验网格质量和物理选择。COMSOL 6.3/Windows 11。

## 2026-09-11：Force Calculation 自动生成表达式审计

- 症状：需要展开 `mfnc/fcal1` 的 Maxwell 应力积分，核对 `Forcex_force_magnet` 的边界贡献。
- 原因/限制：COMSOL 6.3 Java API 可读取 `ForceCalculation` 的公开属性和域选择，但当前模型的 `model.variable()`、`component.variable()` 均为空，`result().numerical()` 也为空；自动生成的应力变量没有作为可枚举模型树变量暴露。
- 可用方案：只记录已实际读取的 `ForceName=force_magnet`、`selection domain=[3]`、`useAverage=1` 和 StudyStep；用官方 Application Library 的 Force Calculation 示例确认变量可通过 GUI Expression autocomplete 查找，但不把示例中的 `nToutx_FEM_rod` 等名称套到本模型。未取得当前模型的确切应力变量前，不做边界分解或自写 Maxwell 张量。
- 结果：`force_boundary_contributions.csv` 明确标记 `API_LIMITATION`；闭合空气包络面因本模型 0.28 mm 负 x 间隙及 PartitionDomains 布尔测试停滞，标记 `NOT_BUILT_GEOMETRY_LIMITATION`，没有填零或伪造独立力。
- 版本：COMSOL 6.3，Windows 11。

## 2026-09-11：同网格差分与 Model B 静态分解

- Model A：每个 gap 只运行一次 `geom.run()` 和 `mesh.run()`，随后在同一网格上
  计算钢片 OFF/ON。0.25–0.30 mm 六个点在 h≈0.03 mm 与 h≈0.02 mm 下全部成功；
  两档网格的最大相对差异约 0.552%，0.30 mm 差异约 0.000442 mN。
- Model B：初次静态尝试失败的两个实际原因是源 ON 属性在 normalize 后才捕获，
  以及 `zsp1` 没有有效点参考。修复为先 capture 源物理属性、核验/设置 point=20，
  并为每个 constitutive state/pose 生成独立 Stationary/PARDISO 序列，同时保留
  同一高度的几何和网格。
- Model B 稀疏结果：z=120、140、150 mm 共 33 行全部 SUCCESS；用 B1−B0 得到
  的修正 holding Fx 在三高度为 -0.124852717、-0.124888879、-0.124611939 mN，
  跨高度变化约 0.000277 mN。B2−B0 在 0/45/…/360°均为负；0/360 闭合误差约
  1e-10 mN。原始 B0 仍随 z 变化，因此后续解释必须使用明确的同网格差分量。

## 2026-09-11：Model B 降低 z 的同网格稀疏扫描

- 沿用 `RunModelBSameMeshDifferential.java`，只新增 z=110、100、90、80 mm，
  每个高度一次几何和网格，B0/B1/B2 全部状态复用该高度同一网格。
- 四个高度、44 行数据全部 `SUCCESS`；`Fx_total_corr` 最大值依次为
  -0.095744、-0.111991、-0.099860、-0.162555 mN，均为负且采样最大值均在
  phi=90°，没有发现负到正的首次跨零区间。
- `Fx_hold_corr` 在四高度为 -0.124493、-0.124380、-0.125093、-0.124214 mN，
  仍保持稳定；原始 B0 self-force 继续明显漂移，不能替代差分力。
- 0°/360°独立闭合误差约 1e-10 mN；没有进行角度加密或继续向下扩展。
- 版本：COMSOL 6.3，Windows 11。

## 2026-09-11：Model B z 局部细扫与候选 h002 资源失败

- 13 个高度 z=110–140 mm（步长2.5 mm）只计算 phi=90°的 B0/B1/B2，全部
  `SUCCESS`；直接实算最大值为 z=120 mm，`Fx_total_corr=-0.085210451 mN`。
- z=120 mm 的 0–360°、45°间隔复核全部 `SUCCESS`，最大值仍在90°，为
  -0.085210451 mN；最小值在270°，为 -0.219998493 mN；360−0 闭合误差约
  1.9e-10 mN。
- h002 候选尝试中，源模型原生 `mesh1` 为 `hauto=1`，约1.24M单元。若继续
  叠加局部 Size，会导致 Force Calculation 报 `Source selection not in mesh
  partition`；去掉重复局部 Size 后该错误消失，但 B0 的 PARDISO 求解在矩阵
  分解阶段约90%处长期无实质 CPU 进展，内存日志达到约30 GB私有占用，最终
  仅保留失败日志，不接受任何 h002 力值。
- 版本：COMSOL 6.3，Windows 11。

## 2026-09-11：Model B 局部空气网格收敛与 alpha 扫描

- 直接用 Box/相邻域推导局部边界会误选外部空气边界，导致局部细网格扩散到大空气域；
  最终改用 `GeomMeasureFinal` 的实体 bbox，选择圆柱全部边界、钢片前表面和中心孔边缘，
  得到 11 个真实近场边界。
- `CURRENT_SOURCE_MESH`、`LOCAL_M03`、`LOCAL_M02` 均成功；M03→M02 的
  `Fx_total_corr` 变化 0.001529 mN，`DeltaFx_ball` 变化 0.001092 mN，符号稳定。
- COMSOL 6.3 `comsolcompile` 不可靠地解析跨源文件默认包类路径；alpha 脚本最初依赖
  `RunModelBLocalMeshConvergence`，编译器返回代码0但实际报 unresolved。修复方式是把
  alpha 执行分支内联到已编译的本地网格类，并显式编译后检查输出。
- 固定 LOCAL_M03 网格后，alpha=0–90°、10°步长 10 点全部成功；最佳点 alpha=0°，
  `Fx_total_corr=-0.053913373 mN`，仍未翻转为正。

## 2026-09-11：alpha=0 空间粗扫与释放候选

- 复用 LOCAL_M02 z=120 检查点，仅新增 alpha=0 的 B2；B0/B1 确认来自同一张
  1,936,465 单元网格。M02 结果为 `Fx_total_corr=-0.054707939 mN`，与 M03
  alpha=0 同号且差异 0.000795 mN。
- alpha=0、LOCAL_M03 从 z=60 开始粗扫；z=60 首次得到 `Fx_total_corr=+0.152767952 mN`，
  `DeltaFx_ball=+0.281045949 mN`，按停止规则不再继续 z=70 等点。
- 边界补点 z=50 得 `Fx_total_corr=-0.249865256 mN`，说明该方向力不是随 z 单调增强，
  不能仅凭 z=60 一个点宣称连续 z 最优。
- z=60 的 phi=60–120°、10°步长均成功，最大值在 phi=90°，为 `+0.152767952 mN`。
- 版本：COMSOL 6.3，Windows 11。

## 2026-09-11：alpha=0、z=60 的 LOCAL_M02 候选复核

- 保持 alpha=0、phi=90°、x_sphere=26 mm、gap=0.30 mm、Stationary、PARDISO、
  stol=1e-6 和同网格 B0/B1/B2 差分定义，使用 LOCAL_M02（局部 hmax=0.02 mm、
  hmin=0.0066667 mm）。
- B0/B1/B2 均成功，1,935,733 个单元、2,582,448 DOF、最小单元质量 0.2004；
  PARDISO 最后一步 LinErr=4.2e-11、LinRes=1.6e-14，求解时间约251 s。
- 实际结果：B0=0.041144019 mN，B1=-0.087484161 mN，B2=0.174175819 mN；
  `Fx_hold_corr=-0.128628179 mN`，`DeltaFx_ball=+0.261659979 mN`，
  `Fx_total_corr=+0.133031800 mN`，标记为 `MAGNETIC_RELEASE_CANDIDATE`。
- 与 LOCAL_M03 z=60 的 `+0.152767952 mN` 相比绝对差为0.019736152 mN，
  正号保持，但幅值变化不可忽略；该点是可复现候选，不是最终空间收敛证明。

## 2026-09-11：固定 z=60 的 x_sphere 横向位置扫描

- 新增 `xscan` 分支，固定 z=60 mm、alpha=0°、phi=90°、gap=0.30 mm，
  对 x_sphere=26/28/30/32/34/36 mm 分别重建几何和 LOCAL_M03 网格，
  每个位置的 B0/B1/B2 在同一张网格上求解。
- 6/6 成功。`Fx_hold_corr` 分别为 -0.128278、-0.128189、-0.128200、
  -0.128203、-0.128200、-0.128141 mN，说明钢片保持力基线稳定。
- `Fx_total_corr` 分别为 +0.152768、+0.011645、-0.127726、-0.219621、
  -0.335244、-0.414777 mN；实算跨零区间为 x=28–30 mm。
- x=26 的几何身份 bbox 为球 x=1–51 mm，与圆柱右端相切；x 增大后球 bbox
  依次平移到 3–53、5–55、7–57、9–59、11–61 mm。x=28 的正裕量过小，
  低于此前 z=60 的 M03→M02 幅值差，暂不视为稳健正值。
