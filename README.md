# GTMolDraw — GTNH 材料结构与组成展示

正式存档版本为 **0.6.0**，当前 develop 测试版为 **0.7.0-test.A**。目标环境：Minecraft 1.7.10 / GTNH 2.9.0 / Java25；modid 为 `gtmoldraw`。

在物品及 NEI 提示框显示二维结构与组成饼图，支持数量／质量、递归及 Shift 显示。定义从外部 JSONC 数据包加载，`/gtmoldraw reload` 成功后原子替换，失败保留旧快照。游戏 JAR 不含旧定义回退，必须同时安装匹配的数据包。

推荐使用安装 ZIP，移除旧版 JAR 后解压至 `.minecraft`。配置为 `config/gtmoldraw.cfg`，数据目录为 `config/gtmoldraw/packs`。F3+T 刷新资源及显示快照，编辑磁盘 JSONC 后使用 reload。

本阶段新增材料／流体分表、中文名称快照及可搜索 HTML，离线 preview 支持 `--audit snapshot.json`。安装、命令与验收见 [0.7-A 测试说明](docs/TESTING-0.7-A.md)。直接流体 GUI 扩充与虚构物种修复仍按后续阶段推进。

## 构建

使用 JDK25 执行 `./gradlew build`，产物位于 build/libs 和 build/distributions。check 包含纯 Java 计算、迁移、数据包、审核导出回归及游戏包／离线工具依赖边界检查。

## 文档与版本

[JSONC 格式](docs/FORMAT-V1.md)、[0.6 安装说明](docs/STAGE-4-TESTING.md)、[后续路线](docs/ROADMAP-0.7.md)。main 发布，develop 开发，历史 Release 和标签保留。

原版：[RubenVerg/gregtech-molecule-drawings](https://github.com/RubenVerg/gregtech-molecule-drawings)。继承代码与资源遵循上游 LGPL-3.0；模板 MIT 不替代上游许可。参见 LICENSE-UPSTREAM 和 NOTICE.md。
