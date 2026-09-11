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

