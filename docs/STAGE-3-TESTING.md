# 第③阶段：全量迁移与可视核对

版本：0.6.0-stage.3。本阶段将旧数据及材料专用规则转换为统一JSONC，提供全量图册和可重复运行的迁移工具。游戏尚未切换外部加载；本阶段验收后才实施第④阶段挂载及事务式热重载。

## 交付物与安装

- 游戏测试JAR：`build/libs/gtmoldraw-0.6.0-stage.3.jar`，替换旧JAR放进mods。
- 全量预览包：`build/distributions/gtmoldraw-stage-3-preview.zip`，解压后用浏览器打开 `preview/index.html`。
- 独立数据包：`build/distributions/gtmoldraw-data-pack-stage-3.zip`，包含同一份数据及来源报告。当前用于审阅，无需安装进游戏。
- 独立工具：`build/distributions/gtmoldraw-pack-tool-stage-3.jar`，Java 25命令行运行，**不要放进mods**。

不需要阅读JSON。图册按结构/材料分为8页，每页最多96项；点击定义可跳到对应图形。结构定义存在不代表GTNH中已识别该材料。材料页展示当前绑定意图；离线无法取得GT动态组成或质量时不会捏造饼图。只有显式组成可以独立预览，钢保留铁39、碳5的相对材料份数。

来源报告位于 `packs/migration-report.html`，同目录有CSV与JSON。可按源文件、目标ID、状态和SHA-256核对；不以资源数量代替实际游戏覆盖率。

## 已迁移内容

| 来源 | 结果 |
|---|---|
| 368份结构 | 全部保留，353个名称按旧命名空间优先级绑定，15份同名结构保留供审阅 |
| 317份组成 | 316份derive声明转换为动态候选规则，1份钢组成保留为显式mixture_parts；其中18份旧组成来源原先被同名优先项遮蔽 |
| Java专用映射 | 64个旧别名中63个有结构目标；10个前缀、111个组成候选名称、10个contains规则、102个元素身份映射已外置 |

清单共1,039条定义。GT注册表、化学式和质量没有固化为快照；原来没有静态化学式的材料也不从文件名猜测公式。82个旧环按旧点生成方式展开为基础原子/键，保留浮点坐标及现有凯库勒画法；此轮不自动布局或美化苯环。

需注意的兼容记录：

- `poly_amic_acid` 有2条单线重复绘制已有双键的第一条线。迁移删除这两条冗余描画，避免新模型出现冲突重复键；三种缩放的栅格及文字指令集合比较保持一致。
- 10处旧 `stereo` 属性原先被旧解码器忽略；迁移保留实际生效的strokes，并逐条记录，未推测新的立体化学。
- `heavy_ice → heavy_water` 缺少目标结构，记录为未解析别名，不猜测或自动绑定为普通水。
- 原子/附属标签的旧可选颜色保留为 `optionalColor`，工具预览默认启用；禁用时由颜色适配器提供回退色。实际GT配色需第④阶段连接客户端配置。
- `composition_candidate.names` 是精确规范化名称集合，不是GT身份；只决定是否尝试图表，不声明组成、质量或显示优先权。

## 手动验收

1. 冷启动后 `/gtmoldraw format-info` 应显示stage 3和External loading is NOT enabled。
2. 检查原有分子图/饼图、库存、NEI、ChromaticTooltips、Shift、数量/质量切换、F3+T、语言/配置修改及反复进出世界。
3. 解压预览ZIP，打开全量图册，重点查看苯、聚合物、聚酰胺酸、不同线型与钢饼图；无需改动或安装JSONC。

自动比较使用近似等宽字体与统一白色，验证坐标、标签、边界、栅格/文字指令集合；不等同于游戏字体/GT配色或最终屏幕截图比较。历史native崩溃仍保留为回归场景，不宣称已确定根因。

## 工具与重复迁移

在解压后的预览包目录运行（Java需为25）：

```text
java -jar tools/gtmoldraw-pack-tool-stage-3.jar validate packs
java -jar tools/gtmoldraw-pack-tool-stage-3.jar format --check packs
java -jar tools/gtmoldraw-pack-tool-stage-3.jar preview packs my-preview
java -jar tools/gtmoldraw-pack-tool-stage-3.jar preview packs one-preview --id official:gtceu/poly_amic_acid_structure
```

开发仓库中可重新生成到一个**不存在**的目录，迁移不会覆盖现有目录：

```text
java -jar build/distributions/gtmoldraw-pack-tool-stage-3.jar migrate src/main/resources/assets tools/migration/legacy-bindings.json build/new-migrated-packs
```

候选完整写入临时目录并校验后，通过同一父目录中的原子重命名发布。失败不发布目标，临时目录可能保留供诊断。拒绝源/目标重叠、符号链接和未支持的旧字段。当前转换器针对本仓库实际使用的旧格式子集，不是任意旧mod或资源包的通用导入器。

`extract_bindings.py --check`核对4份Java来源的映射快照；改变旧映射后须显式重新生成快照并审阅差异。`gradlew build`包含迁移重复生成一致性、368份结构在三种缩放下的兼容比较、原有回归及游戏/工具包边界检查。另用 `tools/contract/check.py`执行标准JSON Schema交叉校验。
