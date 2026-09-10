# COMSOL 6.3 磁场仿真经验总结

## 1. 本次任务的核心结论

- 使用 COMSOL 6.3 的 `mfnc`（Magnetic Fields, No Currents）接口时，磁力应优先通过物理场内置的 `Force Calculation` 特征读取。
- 当前模型中已经验证有效的力表达式是：

  ```text
  mfnc.Forcex_force_magnet
  ```

- 不要用单个手动边界上的 Maxwell 应力积分代表固体总力。手动积分必须是完整闭合曲面，实际工程中优先使用 `Force Calculation`。
- Java 读取全局力的可靠方式是：临时创建 `Global` 数值求值特征，调用 `getData()`，读取首个标量后立即删除该特征。
- 网格 1 是本次最终验收网格。网格 2 只能作为诊断或对照，不能混入网格 1 的最终数据。
- 本次最有效的困难点修复方式是参数连续初始化：先成功求解邻近点，再保留收敛场切换到失败点。2.7 mm → 2.8 mm 的网格 1 连续求解最终成功。

## 2. 建模前必须完成的检查

在写扫描代码前，先用 Java 读取并打印：

1. `.mph` 文件是否存在、路径是否可访问；
2. 参数名及单位，例如 `z_sphere`、`x_gap`、`h_cyl3`；
3. 几何标签和几何特征；
4. 物理接口标签、Force Calculation 标签和选择域；
5. 研究、求解器、结果数值特征和已有力表达式；
6. 当前网格等级和网格特征。

不要凭记忆猜测标签、表达式、域编号或边界编号。

本次模型实际检查到的关键对象包括：

```text
component: comp1
geometry:  geom1
mesh:     mesh1
physics:  mfnc / MagnetostaticsNoCurrents
force:    fcal1 / Force Calculation
study:    std1
solution: sol1
```

## 3. Force Calculation 和力读取

### 推荐方法

```java
String tag = "tmp_force_" + System.nanoTime();
try {
    model.result().numerical().create(tag, "Global");
    model.result().numerical(tag).set("expr",
        new String[]{"mfnc.Forcex_force_magnet"});
    model.result().numerical(tag).set("unit", new String[]{"mN"});
    double[][][] data = model.result().numerical(tag).getData();
    double force = data[0][0][0];
} finally {
    try { model.result().numerical().remove(tag); }
    catch (Exception ignored) {}
}
```

注意：

- 使用 GUI 中实际验证过的完整变量名，不要把 `mfnc.Forcex_force_magnet` 简写成猜测的 `.Fx` 或 `.Fz`。
- 求解器必须确实输出反作用力，否则全局力变量可能未定义。
- 不使用 `setResult()` 读取数值；本次验证中 `getData()` 更可靠。
- 临时数值特征必须删除，否则长时间扫描会积累大量后处理对象。
- 读取失败要抛出并记录，不要把 Undefined、空数据或 NaN 当成 0。

### 力的物理解释

先明确坐标正方向和力的作用对象，再解释正负号。磁球位于结构下方时，不能直接套用“上方磁球”的符号逻辑。参数扫描中应同时记录：

```text
参数值、力值、单位、网格等级、求解状态、耗时、错误信息
```

## 4. 几何和网格的关键经验

参数改变几何位置时，每个扫描点都必须执行完整闭环：

```java
model.param().set("x_gap", value + "[mm]");
model.component("comp1").geom("geom1").run();
model.component("comp1").mesh("mesh1").autoMeshSize(meshSize);
model.component("comp1").mesh("mesh1").run();
model.study("std1").run();
```

原因是 `x_gap` 改变了钢片位置，不能复用大间隙点的旧几何或旧网格。

必须特别注意：

- 每个点显式设置 `autoMeshSize(1)`，避免前一点的网格 2 状态泄漏到下一点。
- 薄气隙、磁体边缘、Force Calculation 包络面应有足够近场分辨率。
- 几何接触、穿透、零厚度和极薄空气层可能导致网格质量失效或多物理场编译错误。
- 先检查几何和网格能否生成，再运行求解器。
- 网格等级不是纯粹的“速度选项”：本次 2.5 mm 的网格 1 结果约为 `+0.0275 mN`，网格 2 对照结果约为 `−11.0654 mN`，差异足以改变物理结论。因此最终数据必须统一网格等级。

## 5. 困难点的求解策略

### 普通点

每个点使用全新模型或清空旧解后求解，适合独立验证和重试。

### 难收敛点

如果某一点反复出现：

```text
找不到一致的初始值
最后一个时步不收敛
```

不要无限重复同一个“清空解 → 直接求解”流程。优先使用参数连续初始化：

1. 选择一个相邻且已成功的参数点；
2. 使用相同物理场和相同最终网格等级求解该点；
3. 不清空已收敛解；
4. 更新目标参数；
5. 重建几何和网格；
6. 用邻近点解作为目标点的初始场；
7. 成功后再读取 Force Calculation。

本次 `x_gap=2.8 mm` 的成功路径就是：

```text
mesh 1: x_gap=2.7 mm 求解成功
→ 保留收敛场
→ 更新 x_gap=2.8 mm
→ 重建 geom1 和 mesh1
→ mesh 1 求解成功
→ F_x=-0.05721791 mN
```

### 重试和超时

- 每点至少记录两次独立尝试；困难点可增加一次连续初始化尝试。
- 独立重试最好使用全新 Java/COMSOL JVM，避免模型状态残留。
- 给单点设置合理超时，并将“超时”“求解器错误”“网格错误”“变量读取错误”区分记录。
- 超时不等于物理上无解；必须在日志中明确写出原因。

## 6. Java 和 Windows 自动化注意事项

- 使用 COMSOL 自带 Java 和 `comsolcompile.exe`，编译通过后再运行。
- Windows 类路径含空格时必须正确引用，否则会出现：

  ```text
  Could not find or load main class Files\COMSOL...
  ```

- Java 源文件中的中文路径容易发生编码转换。更稳妥的做法是：
  - 使用 ASCII 文件名副本；或
  - 确认源文件 UTF-8、编译器编码和实际文件名一致；
  - 在 `ModelUtil.load()` 前先验证文件存在。
- PowerShell 不要使用 `$error` 作为普通变量，因为它是内置只读错误变量；使用 `$errorCount` 等名称。
- 每个后台任务都要有独立的 stdout、stderr 和运行日志。
- 大模型可能超过数 GB，避免在同一 JVM 内反复加载、删除、保存大量模型；困难点优先采用独立进程。

## 7. CSV 和结果管理

推荐固定列：

```text
z_sphere_mm,x_gap_mm,force_x_mN,mesh_mode,mesh_size,status,elapsed_s,message
```

结果管理规则：

1. 原始扫描表不覆盖；修正版使用新文件名；
2. 合并前删除空行；
3. 按参数数值排序；
4. 检查每个参数是否恰好一行；
5. 检查总点数是否等于理论点数；
6. 检查 `mesh_size` 是否符合本次验收要求；
7. 检查 `good`、`ERROR` 数量；
8. 只用 `good` 行做曲线、临界点或物理结论；
9. 不用插值或邻近值伪造失败点；
10. 对替换点保存原值、替换值和替换原因。

## 8. 下一次 COMSOL 磁仿真的推荐流程

### 阶段 A：环境和模型检查

- 验证 Java、编译器、类路径；
- 运行最小 smoke test；
- 读取模型参数、几何、物理、网格、研究和结果树；
- 确认力特征和完整变量名。

### 阶段 B：单点基准

- 固定参数运行一个已知可收敛点；
- 确认磁场 `B` 非零；
- 确认 Force Calculation 输出非零且单位正确；
- 保存单点结果和日志。

### 阶段 C：网格验证

- 先确定最终验收网格等级；
- 对代表性点比较网格收敛性；
- 一旦确认最终等级，所有扫描点统一使用该等级；
- 不把不同网格的数据混为一条物理曲线。

### 阶段 D：参数扫描

- 先粗扫趋势，再对临界区间细扫；
- 每点显式更新参数、重建几何、重建网格、求解、读取力；
- 难点使用邻近收敛点连续初始化；
- 每点独立记录状态和耗时。

### 阶段 E：结果审计

- 检查点数、重复点、空行、单位、网格等级和状态；
- 检查异常尖峰和相邻点跳变；
- 对关键点用同一网格等级重新验证；
- 最终 `.mph`、CSV、Java 源码和日志一起归档。

## 9. 可复用的最小原则

```text
先检查真实模型对象，再写 API。
先单点跑通，再扫描。
几何变了，geom 和 mesh 都要重建。
Force Calculation 优先于单面 Maxwell 应力积分。
全局力使用临时 Global + getData() + 立即删除。
困难点用邻近收敛解连续初始化。
最终验收只使用统一网格等级的数据。
错误、超时、空值、异常值必须分开记录。
CSV 合并后必须做点数和重复项审计。
```
