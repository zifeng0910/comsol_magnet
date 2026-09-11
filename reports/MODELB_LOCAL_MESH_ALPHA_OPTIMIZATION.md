# Model B：局部网格收敛与 alpha 粗扫

日期：2026-09-11

## 结论

在固定 `z_sphere=120 mm`、`phi=90°`、`gap=0.30 mm`、`x_sphere=26 mm`、
`PARDISO`、`stol=1e-6` 的条件下，采用“远场 hauto=6 + 固体域2/3/4 hauto=1 +
机器人近场边界局部 Size”的工作流后，三档网格均成功完成 B0/B1/B2 同网格差分。

`LOCAL_M03` 与 `LOCAL_M02` 的 `Fx_total_corr` 差值为 0.001529 mN，
相对 `LOCAL_M03` 约 1.55%；`DeltaFx_ball` 差值为 0.001092 mN，约 3.70%。
两者均保持负号，因此本轮工程判据（绝对变化小于 0.01 mN、符号稳定）通过。

alpha 粗扫 0–90°（10°步长）10 个点全部成功。最佳实算点为：

| 指标 | 结果 |
|---|---:|
| z | 120 mm |
| phi | 90° |
| 最佳 alpha | 0° |
| Fx_total_corr | -0.053913373 mN |
| DeltaFx_ball | +0.074230661 mN |
| Fx_hold_corr | -0.128144033 mN |

alpha=0° 是本轮最接近释放阈值的点，但仍小于 0；本轮没有得到
`MAGNETIC_RELEASE_CANDIDATE`。alpha 增大后 `Fx_total_corr` 单调变得更负，
在 alpha=90° 为 -0.197643424 mN。因此在当前 z、位置、间隙和材料设置下，
调整 alpha 不能克服 holding force，下一阶段应优先考虑经过物理审查的空间位置参数，
而不是继续盲目加密 alpha 或扩大 z 扫描。

## 网格策略与实际结果

局部边界选择来自几何 bbox 审计，不使用外部空气域边界：圆柱全部外边界、钢片面向
圆柱的前表面及孔边缘，共 11 个边界。这样避免把 0.02–0.03 mm 的局部尺寸扩散到
整个 4 mm 级钢片表面。

| mesh_level | 局部 hmax (mm) | elements | DOF | min quality | Fx_hold (mN) | DeltaFx_ball (mN) | Fx_total_corr (mN) | status |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| CURRENT_SOURCE_MESH | — | 60,881 | 81,790 | 0.2226 | -0.124852717 | 0.039642266 | -0.085210451 | SUCCESS |
| LOCAL_M03 | 0.030 | 871,685 | 1,163,440 | 0.2123 | -0.128144033 | 0.029535612 | -0.098608421 | SUCCESS |
| LOCAL_M02 | 0.020 | 1,936,465 | 2,583,434 | 0.2048 | -0.128580748 | 0.028443309 | -0.100137439 | SUCCESS |

所有 B0/B1/B2 状态均复用同一份几何和网格；`same_mesh_verified=true`。
LOCAL_M02 的 PARDISO 求解峰值私有内存约 30 GB，虽成功但资源余量较小；
因此 alpha 阶段采用已通过收敛门槛、资源更可控的 LOCAL_M03。

## alpha 粗扫结果

| alpha (deg) | DeltaFx_ball (mN) | Fx_total_corr (mN) |
|---:|---:|---:|
| 0 | 0.074230661 | -0.053913373 |
| 10 | 0.061034501 | -0.067109532 |
| 20 | 0.045983718 | -0.082160315 |
| 30 | 0.029535612 | -0.098608421 |
| 40 | 0.012189983 | -0.115954051 |
| 50 | -0.005526059 | -0.133670092 |
| 60 | -0.023074117 | -0.151218150 |
| 70 | -0.039920881 | -0.168064915 |
| 80 | -0.055554349 | -0.183698382 |
| 90 | -0.069499391 | -0.197643424 |

`Fx_hold_corr` 在所有 alpha 点保持 `-0.128144033 mN`，因为 B0/B1 只求解一次并在
同一网格上复用；这也是本轮差分逻辑的稳定性检查。`Fy_total_corr` 和
`Fz_total_corr` 随 alpha 变化，但没有改变本轮的 Fx 判定。

## 解释边界

- 这是固定姿态的静态、同网格差分验证，不是完整动态周期结论。
- alpha 粗扫只覆盖 0–90°，不代表完整角度空间已经证明没有其他候选。
- 网格收敛门槛是工程诊断尺度，不是严格误差上界。
- 未修改磁球尺寸、磁化极性、材料、间隙、受力对象或外部边界。
- 源模型原生全局 hauto=1 约 1.24M 单元，曾导致资源压力；本轮没有采用该路线。

## 文件

- `data/modelB_z120_local_mesh_convergence.csv`
- `data/modelB_z120_phi90_alpha_coarse.csv`
- `figures/modelB_local_mesh_alpha.png` / `.svg`
- `figures/modelB_z120_phi90_alpha_coarse.png` / `.svg`
- `scripts/RunModelBLocalMeshConvergence.java`
- `scripts/RunModelBLocalMeshConvergence.ps1`
- `scripts/RunModelBAlphaCoarse.java`（可编译反射启动器；实际逻辑在本地网格类的 alpha 分支）
- `scripts/RunModelBAlphaCoarse.ps1`

运行后的大型 `.mph` 检查点保存在本地 `work/`，按仓库规则不提交。
