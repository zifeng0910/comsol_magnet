# Force Calculation 变量 GUI 查找记录

状态：`GUI_AUTOMATION_UNAVAILABLE_IN_CURRENT_SESSION`  
日期：2026-09-11  
目标模型：`H:\comsolcc\local_force_audit_140_phi90_20260911\L1\solved.mph`

## 当前已完成

- 已用 COMSOL Desktop 打开目标模型，窗口标题为 `solved.mph - COMSOL Multiphysics`。
- Java 只读审计已确认：`mfnc/fcal1`、`ForceName=force_magnet`、域选择 `[3]`、`useAverage=1`、StudyStep 为 `st_audit_l1_z140_p90/stat`。
- Java API 的变量集合仍为空；现有证据见 `data/forcecal_audit_140_l1_20260911.txt`。
- 本文件不宣称 GUI 已经显示或未显示变量。当前阻塞是本次代理环境没有 COMSOL 原生窗口的可读/可点击控制接口。

## 需要在 COMSOL Desktop 中完成的最小取证

1. 在 Model Builder 中选中 `Results`，新建 `Derived Values > Surface Integration`；如果该节点要求选择，先选圆柱域 3 的外部边界。
2. 在 Expression 输入框点击 `Replace Expression` 或右上角表达式自动完成按钮，搜索：
   `force_magnet`、`nTout`、`Tout`、`Maxwell`、`stress`、`mfnc.`。
3. 记录所有明确属于当前 `fcal1/force_magnet` 的变量全名、描述、单位和定义维度。不要套用其他模型的 `FEM_rod` 或 `BEM_probe` 后缀。
4. 在 `Physics > Magnetic Fields, No Currents > Force Calculation 1` 核对 `ForceName=force_magnet`、域 `[3]` 和 `useAverage`。
5. 若自动完成能显示 `nTout*` 或等价变量，保存变量全名并导出最小 Java 片段到 `references/force_probe_gui_export.java`。
6. 若变量仍不可见，打开 `Equation View`，检查 `fcal1` 的生成变量/弱式；将截图或原始文本保存到本目录，并把状态改为 `GUI_EXPRESSION_NOT_EXPOSED`。

## 取证后的分支

- 找到真实变量：再对圆柱 `-x` 端面、`+x` 端面和侧壁做 Surface Integration；只使用当前模型 GUI 给出的变量。
- 成功建立空气中的完整闭合 force-probe surface：导出 GUI 原生 Java，并单独记录 S1/S2。
- 两者都不可行：保留 `API_LIMITATION` 和 `NOT_BUILT_GEOMETRY_LIMITATION`，不要用相似变量名或自写 Maxwell 张量填充结果。

## 当前禁止结论

本文件没有提供 `nTout*` 变量名、边界贡献数值或闭合面力数值；这些项目仍待 COMSOL Desktop GUI 取证，不能作为已完成的独立力验证。
