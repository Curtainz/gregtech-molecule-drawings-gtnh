# GTMolDraw — GTNH 材料结构与组成展示

正式版本为 **0.7.0**。目标环境：Minecraft 1.7.10 / GTNH 2.9.0 / Java25；modid 为 `gtmoldraw`。下载 [0.7.0 Release](https://github.com/Curtainz/gregtech-molecule-drawings-gtnh/releases/tag/v0.7.0)。

在物品及 NEI 提示框显示二维结构与组成饼图，支持数量／质量、递归及 Shift 显示。定义从外部 JSONC 数据包加载，`/gtmoldraw reload` 成功后原子替换，失败保留旧快照。游戏 JAR 不含旧定义回退，必须同时安装匹配的数据包。

推荐使用 `gtmoldraw-0.7.0-install.zip`，退出游戏，移除 `mods` 及 `mods/1.7.10` 中所有旧版 JAR 后解压至 `.minecraft`。安装包仅包含游戏 JAR 和匹配数据包。配置为 `config/gtmoldraw.cfg`，数据目录为 `config/gtmoldraw/packs`。F3+T 刷新资源及显示快照，编辑磁盘 JSONC 后使用 reload。

本版新增外部 GTNH 物种、材料／流体审核分表、中文名称快照及可搜索 HTML，离线 preview 支持 `--audit snapshot.json`。安装、命令与验证边界见 [0.7.0 说明](docs/RELEASE-0.7.0.md)。修复中文名称与可读公式导出，图册内置查询参考；增加 NEI 流体显示栈及 ModularUI 1 流体槽入口，并支持缺图饼图与明确标注的数量回退。ChromaticTooltips 使用原生组件；提供早期重复安装检测及离线 MOL V2000 导入。其它流体 GUI 仍需逐项适配与实测。缺少有效翻译或定义的材料仍会报告缺失；声明数量不代表实际材料覆盖率。

## 构建

使用 JDK25 执行 `./gradlew build`，产物位于 build/libs 和 build/distributions。check 包含纯 Java 计算、迁移、数据包、审核、提示框接口、MOL 导入回归及游戏包／离线工具依赖边界、安装包内容／时间戳／JAR 一致性检查。隔离客户端测试验证实际 GL 上下文中的原生流体提示框，不代替真实储罐／NEI 鼠标悬停或图形美观验收。

## 文档与版本

[JSONC 格式](docs/FORMAT-V1.md)、[0.6 安装说明](docs/STAGE-4-TESTING.md)、[后续路线](docs/ROADMAP-0.7.md)。main 发布，develop 开发，历史 Release 和标签保留。

原版：[RubenVerg/gregtech-molecule-drawings](https://github.com/RubenVerg/gregtech-molecule-drawings)。继承代码与资源遵循上游 LGPL-3.0；模板 MIT 不替代上游许可。参见 LICENSE-UPSTREAM 和 NOTICE.md。
