# GTMolDraw — GregTech Molecule Drawings for GTNH

Minecraft 1.7.10 / GTNH 2.9.0 / Java 25. Fork of [RubenVerg/gregtech-molecule-drawings](https://github.com/RubenVerg/gregtech-molecule-drawings).

## 简体中文

在物品及 NEI 提示框中显示材料的二维分子结构式、组成饼图。当前存档版本 **0.4.0**，modid 为 **`gtmoldraw`**。苯环统一采用凯库勒式（交替单双键）。

### 安装与升级

- 将 Release 中的 `gtmoldraw-0.4.0.jar` 放入 `mods`，删除或禁用旧 MolDraw，避免同时加载。
- 使用 GTNH 2.9.0 与 Java 25；开发依赖固定为 GT5-Unofficial 5.09.54.265、NEI 2.8.171-GTNH。
- 配置文件为 `config/gtmoldraw.cfg`。旧 `config/moldraw.cfg` 不会自动迁移；可将旧设置手动复制到新文件，`benzeneCircle` 已移除。
- 本模组的资源路径为 `assets/gtmoldraw/`，语言键为 `tooltip.gtmoldraw.*` / `config.gtmoldraw.*`。已有资源包须相应更新；继承的 gtceu 等数据命名空间继续保留。
- 默认按住 Shift 显示。`/gtmoldraw audit` 导出 GT 材料覆盖情况至 `.minecraft/gtmoldraw-audit/materials.csv`。
- F3+T 可重新读取资源包数据；配置界面修改后刷新缓存。

### 当前能力与限制

包含已有分子结构资源、苯环与聚合物括号绘制、材料及流体识别、递归组成饼图、颜色和显示配置。启动集成延迟到首次客户端 tick，缓存失效集中在客户端线程。已移除整包 FastUtil 依赖。

结构覆盖取决于材料身份与已有模型，并非每种材料都能显示。化学式解析不会据同分子式猜测异构体。外部脚本注册与热重载、更多材料桥接、结构推断和完整组成权重重构仍在计划中。测试源码中的化学式是回归样例，不是运行时白名单。

test.2 的正常启动和显示已由用户确认；0.4.0 的苯环与 modid 改动经过自动构建检查，新增画面效果仍需游戏实测。完整重构顺序见 [重构说明](docs/STARTUP-REFACTOR-0.4.0-test.2.md)。

### 构建

安装 JDK 25，在项目根目录运行：

```powershell
.\gradlew.bat build
```

产物位于 `build/libs/`。`check` 包含化学式及分子集合回归程序。不要将 `-dev` 或 `-sources` JAR 放入游戏。

## English

GTMolDraw adds 2D molecular structures and composition pie charts to GTNH tooltips. Version 0.4.0 uses modid `gtmoldraw` and Kekulé benzene rings. Target: GTNH 2.9.0, Minecraft 1.7.10, Java 25.

Install the release JAR in `mods`, disabling previous MolDraw versions. Configuration: `config/gtmoldraw.cfg`; resource namespace: `assets/gtmoldraw/`; audit command: `/gtmoldraw audit`. Old configuration and resource-pack paths require manual migration. The obsolete `benzeneCircle` setting is removed.

Bundled models determine structure coverage. Formula parsing is general, but does not infer an isomer from its formula. External scripting, transactional hot reload and wider material integration remain planned. Build with JDK 25 using `./gradlew build`.

## 致谢 / Attribution

Original concept, molecular drawing code and resources: RubenVerg and upstream contributors. The GTNH port was initially developed with Google Antigravity / Gemini; subsequent analysis, stabilization and refactoring were performed with Codex in collaboration with the maintainer. See repository license files and upstream for licensing information.
