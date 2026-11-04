# GTMolDraw 0.7.0

环境：Minecraft 1.7.10 / GTNH 2.9.0 / Java 25。modid 为 `gtmoldraw`。本版整合 0.7-A 至 D 的材料覆盖、中文审核、流体提示框修复及离线结构导入成果。

## 安装与升级

1. 退出游戏，移走 `mods` 及 `mods/1.7.10` 中所有旧 GTMolDraw 游戏 JAR，包括改名副本。
2. 备份个人数据包；解压 `gtmoldraw-0.7.0-install.zip` 到实例的 `.minecraft`，合并目录并更新官方数据文件。保留自己的独立扩展包。
3. 确认 `mods/gtmoldraw-0.7.0.jar` 和 `config/gtmoldraw/packs/official`、`config/gtmoldraw/packs/gtnh_species` 存在。配置沿用 `config/gtmoldraw.cfg`。
4. 启动后运行 `/gtmoldraw status`，检查版本与加载诊断。安装包只有 `mods` 和 `config`；文档、图册、工具及开发测试标记不进入游戏目录。

游戏 JAR 不含旧定义回退。单装 JAR 需要另装数据包；不要将离线工具 JAR 放进 `mods`。早期重复安装检查会列出冲突路径，不执行字节码转换、结构生成或绘制。

## 相对 0.6.0 的主要变化

- 外部 GTNH 物种定义支持虚构元素／终止成分；质量仅采用提供者的有效元数据，缺失时明确诊断，不伪造质量。
- 材料与流体 audit 分表、中文名称快照、可读公式导出、可搜索 HTML 和中文 preview。未知名称与解析失败仍保留可追溯状态。
- 流体身份包括 Forge IFluidContainerItem、NEI ItemFluidDisplay 和 ModularUI 1 流体槽；ChromaticTooltips 采用原生可测量、可分页的绘图组件，修复瞬时行令牌与 Shift 缓存引起的提示消失问题。
- `/gtmoldraw status` 和 audit 元数据记录原生适配器状态、构建／绘制次数及最近目标／错误。名称匹配采用版本化索引，歧义名称不任意绑定。
- 缺少结构的材料可使用可靠组成生成饼图；数量回退有明确标识。安装包精简并保留 JAR 的正常修改时间。
- 离线 MOL V2000 转换、安装预检；带电、同位素或显式氢碳不会被骨架省略规则隐藏。

官方迁移包为 1,039 条声明，GTNH 物种包为 14 条，合计 1,053 条；其中包含模块和其它声明，**不是 1,053 种已覆盖材料**。实际覆盖使用本机 audit 的材料／流体状态核算。

## 离线工具

使用 Java 25；以下命令中的路径按实际情况替换：

```text
java -jar gtmoldraw-pack-tool-0.7.0.jar --help
java -jar gtmoldraw-pack-tool-0.7.0.jar validate <packs目录>
java -jar gtmoldraw-pack-tool-0.7.0.jar preview <packs目录> <新图册目录> --audit <snapshot.json>
java -jar gtmoldraw-pack-tool-0.7.0.jar install-check <mods目录>
java -jar gtmoldraw-pack-tool-0.7.0.jar import-mol <input.mol> <新输出.jsonc> --id custom:molecule
```

MOL 导入仅接受单条二维 V2000，保留原子坐标、显式氢、M CHG／M ISO、单／双／三键及实／虚楔形键；D/T 转为氢同位素。输出必须不存在，包含源文件 SHA-256；导入不创建材料绑定。

V3000、三维、芳香键类型 4、查询结构、自由基、相对同位素质量、原子奇偶／全局 chiral 标志、聚合物 S 组、反应字段、多记录 SDF 和未知属性会被明确拒绝。芳香分子应导出交替单双键。工具不推测隐式氢、不联网、不承诺完整立体化学导入。

## 验证与边界

正式 JAR 经过完整构建、纯 Java 回归、数据契约／迁移来源检查、发布包依赖边界及安装包一致性检查。隔离 GTNH 客户端的测试记录为 [0.7.0-client-smoke.json](test-results/0.7.0-client-smoke.json)，绑定实际运行 JAR 的 SHA-256。测试覆盖 5 种流体、FluidStack／文本两种身份和 NONE→SHIFT→NONE→SHIFT，共 40 个场景、20 次真实图形绘制。测试报告在正式包验证后写入并校验。

复现方法及历史测试过程见仓库中的 [0.7-D 测试说明](https://github.com/Curtainz/gregtech-molecule-drawings-gtnh/blob/main/docs/TESTING-0.7-D.md)。测试不模拟真实鼠标悬停、键盘事件或进入世界，也不判断像素美观；GT 储罐／NEI 的实际交互和其它第三方 GUI 仍需逐项验收。历史 DLL 崩溃继续作为回归场景，启动／绘制测试通过不等于根因已经确定。

后续继续离线身份核验、数据库样板重绘及虚构分子规则推测；本版不包含批量数据库重绘或运行时联网查结构。参见仓库 [开发路线](https://github.com/Curtainz/gregtech-molecule-drawings-gtnh/blob/main/docs/ROADMAP-0.7.md)。
