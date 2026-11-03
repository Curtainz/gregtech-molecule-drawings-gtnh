# 第①阶段交付：JSONC格式与样例

版本：0.6.0-stage.1。基于已由用户实测的0.5.0-test.1继续；modid、GTNH/Java目标、绘制算法、已有图形与缓存行为保持原基线。

## 本阶段新增

- 格式v1冻结候选与模块ABI：`docs/FORMAT-V1.md`。
- Schema 2020-12：pack与definition，含材料、组成、结构、片段、共享映射、标注和绑定规则。
- 2个包清单、15个定义样例：水、苯、羟基/苯酚、稠环、聚乙烯、普通/嵌套组成、绑定规则和显式覆盖。
- 开发用合同检查：JSONC词法、重复键、Schema合法性/反例、样例引用/包依赖及稠环样例的坐标和键序。它不等同于后续通用组装编译器。
- 游戏只读命令 `/gtmoldraw format-info`；没有加载器、解包、目录创建或新脚本执行。
- 独立审阅ZIP：`build/distributions/gtmoldraw-jsonc-v1-stage-1-review.zip`。样例不装入JAR；**旧资源仍在JAR内并使用，是本阶段明确保留的过渡行为**，将在第④阶段切换后移除回退。

## 自动检查

1. `gradlew.bat spotlessApply build --console=plain`：现有公式、集合、栅格与架构回归及发布包边界检查通过，生成游戏JAR和审阅ZIP。
2. 开发用Schema检查（独立于游戏/JDK构建）：安装Python3.12，`python -m pip install -r tools/contract/requirements.txt`，然后 `python tools/contract/check.py`。本地已通过；CI新增同样步骤。Python及校验依赖不打入游戏JAR。
3. Schema源需要改动时，修改emit_schema.py并运行 `python tools/contract/emit_schema.py`；check.py会拒绝源/生成文件不一致。

## 请手动测试与审阅

1. 禁用旧GTMolDraw，只安装 `gtmoldraw-0.6.0-stage.1.jar`。继续使用原config，无需迁移。
2. 进入世界运行 `/gtmoldraw format-info`，应显示0.6.0-stage.1、schema1、stage1、未来目录config/gtmoldraw/packs，并明确external loading NOT enabled。
3. 检查原有水、苯、聚合物和饼图；确认Shift、NEI/ChromaticTooltips、F3+T及语言切换仍正常。
4. 解压审阅ZIP到任意临时目录。**不要把这份小型审阅包当作完整官方数据包安装**，当前JAR也不会加载它。重点查看phenol片段连接、naphthalene共享映射、两种组成，以及user覆盖water的完整记录写法。
5. 审阅并提出格式调整；本阶段无法在游戏中展示新JSONC样例。新格式加载、自动格式化、全量迁移、原子热重载和MOL导入均尚未实现。

阶段验收后再实现第②阶段解析/展开/校验/格式化工具。保持develop开发，不改main/历史标签/Release；不将native DLL根因未明的问题描述为已经根治。
