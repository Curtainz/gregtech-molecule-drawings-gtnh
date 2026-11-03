# JSONC 数据包格式 v1 — 第①阶段冻结候选

状态：供审阅的格式合同。0.6.0-stage.1 **不加载本格式**，仍沿用已验证的0.5加载路径。新JSONC样例仅在独立ZIP中交付；本阶段不创建数据目录、不解包、不热重载、不导入MOL、不迁移旧资源。审阅通过后才实现第②阶段解析/展开工具。

## 1. 文件与基础语法

安装根目录为 `.minecraft/config/gtmoldraw/packs/<pack-id>/`，每包一个 `pack.jsonc`。其余文件只从 `materials/`、`molecules/`、`fragments/`、`bindings/` 递归读取，一个文件一个定义，type须与目录一致（material/molecule/fragment/bindings）。文件名小写蛇形，扩展名.jsonc；目录路径不构成定义ID。拒绝符号链接、目录穿越及跨包文件引用；跨包只用ID。

编码UTF-8（允许文件头BOM）；允许 `//`、`/* ... */` 注释和尾随逗号。其余遵循JSON：双引号、无重复属性、无NaN/Infinity、无单引号/无引号键、无函数和表达式。JSONC规范化不得把大整数先转成double。格式化使用两空格、LF、结尾换行；保留注释并规范化缩进，按Schema字段顺序输出，定义文件按ID排序。原子/实例/键数组顺序保留，不改变用户绘图顺序。格式化必须幂等。

每文件 `schemaVersion: 1`。规范字段见 `schema/pack.schema.json` 和 `schema/definition.schema.json`，拒绝未知字段。Schema只验证形状；引用、拓扑、共享和覆盖语义由第②阶段编译器验证。`tools/contract/emit_schema.py` 是Schema源，`check.py` 检查源与生成文件一致并验证样例；这是开发用检查，不是游戏加载器。

每文件最多2MiB、JSON嵌套64；总计最多32个包/8192个定义文件；片段嵌套32、图处理预算10000次、几何元素16384、布局栅格像素262144。达到上限报错，不截断定义。材质/质量缺失沿用0.5的诊断与精确组成语义，不产生虚构质量。

## 2. 包清单、ID和覆盖

清单字段：schemaVersion、type="pack"、id、version、modVersion、dependencies、providers；description可选。包ID小写，core/gt/fluid/name为保留名称，不能作为数据包ID；版本使用三段SemVer（可带预发布标识，不带build metadata）。modVersion为min含端点、maxExclusive不含端点；不做字符串大小比较。dependencies是**确切包版本**的直接依赖列表，禁止重复、自依赖和循环。providers首版仅允许gregtech；启用包的provider取并集。

定义ID为 `pack:lower_snake_name`，可使用分层路径，如 `official:gtceu/water`。GT原始材料身份仍为 `gt:<mName>`，大小写保留，不以翻译名代替。fluid/name绑定也保留原始文本。定义ID与GT材料身份是不同概念。

非覆盖定义必须使用所属包的命名空间。同一个包内定义ID唯一，molecule/fragment/material/bindings共用ID空间。覆盖时保留被覆盖定义的**原始ID和type**，并声明 `overrides: { "pack": "当前提供者包ID", "id": "原始定义ID" }`；覆盖者必须直接依赖该提供者。

覆盖是**整条定义替换，不是字段合并**。例如material覆盖者必须重新声明bindings；未带入的旧字段不会继承。指向原始ID的引用解析到最终覆盖结果。每个ID的覆盖链必须为单链；两个独立扩展同时覆盖同一提供者属于歧义，即使文件排序能决定先后，也必须报错。后续包可显式依赖并覆盖上一扩展，形成单链。

重复绑定、冲突别名、重复元素映射也必须显式解决，不允许“最后加载者获胜”。新定义抢占已有绑定时应覆盖其持有定义，不提供隐式抢占开关。禁用material使用disabled=true并保留bindings，禁止同时声明formula/structure/composition/display。

GT适配器是动态数据来源；数据包定义覆盖对应身份的显示与组成。未知外部GT绑定保留为未解析诊断，不导致无关材料失效；本次环境实际激活的定义若缺少必需GT组成引用，则作为校验错误。包内ID引用缺失始终报错。

## 3. 材料和组成

material：bindings必填；formula、structure、composition、display可选。结构引用必须指向molecule，不能指向fragment。display.mode为auto/molecule/composition/text，默认auto；auto优先有效结构，其次有效组成，最后文本。化学式可以作为显示文本（含聚合物n）；只有用于composition.source=formula时，才要求可由计数语法解析，不自动改写材料化学式。

composition.source三种互斥形式：

- gregtech：动态读取绑定GT材料的组成/密度。无唯一GT目标时报错；不通过翻译名猜测。
- formula：解析material.formula；未知符号分别保留，不合并到同一虚拟材料。
- explicit：basis必填mixture_parts/stoichiometric；components各含material和amount。material可引用GT身份或包内material定义，不可引用结构。

amount为正JSON整数或正整数字符串/整数比字符串，例如3、"999999999999999999999999"、"1/3"。禁止浮点比例、0、负数及分母0。内部使用BigInteger/约分Fraction。mixture_parts在各节点按相对份数处理；stoichiometric是该节点的计量数。两者均先按当前数量/质量模式归一化，再分配父份额；不得把展开后的原子数直接乘上父份数。GT已知组成保留密度修正；缺少质量时停止整个质量结果。显式片段/材料引用环校验失败；GT自身组成循环继续沿用受限终止和诊断。

若material绑定唯一GT身份且未写composition，使用该GT数据；仅有name/fluid且无法确定GT目标时，没有隐含组成。GT绑定多个不同材料且需要推导组成/质量时必须拆成独立material定义。官方迁移不把整个GT注册表固化为快照。

### 绑定规则

bindings定义的rules只允许alias、strip_prefix、composition_candidate、element_binding。alias从规范化名称指向明确的molecule ID；strip_prefix只剥离一次，并在已有名称绑定/别名中寻找候选，不从文件名猜测ID、不递归删除前缀。composition_candidate为GT材料白名单或规范化名称containsAny词项的并集，不构造组成比例。element_binding把符号映射到明确GT身份，不提供伪造质量。

GT身份区分大小写，fluid使用注册名精确匹配；name绑定先去Minecraft格式码/首尾空白，再按Locale.ROOT不区分大小写比较。兼容alias/prefix/candidate规则使用现有MaterialNames.normalize的蛇形名称约定。精确材料/流体绑定优先于名称回退；回退得到多个不同定义时不显示图形，记录AMBIGUOUS_BINDING，不选择第一个。重复alias键或element_binding符号必须覆盖其持有定义解决；布尔composition_candidate选择器可以合并为集合，不赋予绑定优先权。

## 4. 几何模块ABI

坐标二维、X向右、Y向上，普通键长基准1，角度单位度。文本/像素大小仍由客户端字体与全局显示配置决定。分子与fragment都可声明atoms、bonds、instances、annotations、ports；至少含一个原子或实例。fragment不绑定GT材料。

内建操作固定在代码中：原子、键、环、苯环、括号、文字/电荷及几何变换；材料专用结构/官能团放在外部。内联atoms/bonds/annotations是基础操作的声明形式，不要求为每条键创建模块实例。

- atom：id、element、position必填。element可为null（仅视觉锚点）；普通符号及幻想元素通过数据绑定识别。label可覆盖可见文本；labelMode默认symbol，skeletal仅隐藏碳符号，hidden隐藏中心符号。hydrogens省略表示未声明；显式值0..32，在symbol模式合并显示H计数，skeletal/hidden模式不自动显示。不会根据价态推算或补写隐式氢。isotope、charge默认无同位素/0电荷。labels.above/right/below/left保留旧图的周围文本表达；非零hydrogens与显式right标签同时出现时报错，避免双重氢标注。
- 键：from/to为原子或端口引用。order=1/2/3；style默认solid，可dotted/thick/inward/outward，楔形仅允许单键。或者使用strokes数组精确保留旧特殊线型，不能同时声明order/style。centered默认false。自连接、非法端点和冲突重复键报错。
- 注解：bracket引用原子集合，sub/sup默认空；text使用位置与文字；ellipse用引用原子集合的质心及2×2矩阵保留旧CircleTransformation。后者是兼容显示操作，不改变苯环默认画法。
- 颜色为#RRGGBB或#AARRGGBB，缺省使用既有配置/元素配色；标签覆盖不更改化学身份。

实例只允许core:benzene、core:ring或外部fragment引用。id是实例前缀；每层用点连接，如ring.a0、group.ring.a0。ports把公开端口名映射到实际原子，成键时可用instance.attach；端口不是新原子。

| 模块 | 参数及输出 |
|---|---|
| core:benzene | bondLength默认1；doubleBondStart默认1；输出a0..a5，都是skeletal C；第i条边连接ai和a(i+1)%6，(i+doubleBondStart)%2=0为双键，其余单键。 |
| core:ring | elements和bondOrders必填、长度一致、3..64项；输出a0..a(n-1)。bondLength默认1；按明确指定的键序生成，不推测芳香性。C默认skeletal，其他元素默认symbol。 |
| 外部fragment | 根据定义展开，暴露其原子及ports。禁止循环引用和从molecule作为fragment实例化。 |

正n边形原点为中心，a0位于正X轴，逆时针排列；外接圆半径为bondLength/(2*sin(pi/n))。核心benzene与ring使用同一坐标约定，不隐式移动到首原子。变换顺序固定为 `origin + R(rotation) × mirror × scale × localPosition`，origin默认[0,0]、rotation默认0、scale默认1、mirror默认none。mirror=x表示X取负，mirror=y表示Y取负；镜像同时交换inward/outward以保留立体表达。

## 5. 共享原子

atomMap的键必须是实例内部真实原子ID（可嵌套），值必须是该定义中的其他真实原子引用，不允许端口别名。原子/实例允许前向引用，先建立完整符号表再解析；自映射、循环别名、无效源/目标报错。

共享组在变换后检查位置误差<=1e-6模型单位；默认值展开后的元素、同位素、电荷、氢声明状态、可见标签/附属标签和颜色策略必须一致，不修改冲突值来强行拼接。canonical原子选完整引用按ASCII字典序最小者，整个组的坐标均与其比较，避免加载顺序引入结果差异。

共享后普通重复键仅在键序、线型及centered一致时去重；有方向的楔形键还要求from/to方向一致。映射形成自键或不同键序重叠则报错。稠环样例显式指定两环交替键的起点，使公共边同为双键；编译结果应为10原子、11条唯一键、5条双键，不进行自动凯库勒化。

## 6. 加载和工具（后续阶段实现）

候选快照完整校验后，仅在客户端线程一次替换，缓存版本随之更新。任一启用包错误，拒绝整次reload并保留上一有效快照；首次加载失败或没有包时，图形功能停用并显示安装/诊断信息，游戏继续启动。删除定义/包后重载重新计算整个快照，不保留旧绑定。resource pack重载不成为读取旧JAR目录的暗中回退。

第②阶段提供独立Java工具validate/format与模块展开校验；第③阶段migrate全量转换368份结构、317份组成和Java专用映射；第④阶段才切换游戏加载及/gtmoldraw reload；第⑤阶段提供import-mol。工具不启动Minecraft，不新增游戏native依赖。

MOL首版严格为单个V2000二维结构，支持普通原子、1/2/3键及可明确转换的电荷、同位素和楔形标记；保留明确提供的氢计数，不推测未提供的隐式氢。保留坐标关系，只统一平移与键长尺度；不进行自动布局。查询原子/键、非零Z、复杂SGroup和需要推测键序的芳香type4明确报错，不静默丢弃。无普通键或键长全0时不能可靠归一化：单原子结构归到原点，其余报错。导入后不自动绑定GT材料。

## 7. 本阶段审阅文件

`data-packs/review`有2份清单和15份定义：水、苯、羟基片段/苯酚、共享稠环、聚乙烯、普通/嵌套合金、绑定规则，以及user显式覆盖water为文本模式。它们是小型样例，不是完整官方数据包；不要用它们替换现有0.5资源。

审阅重点：是否接受GT身份与定义ID分离、整条覆盖、实例/端口/atomMap写法及显式二维坐标。阶段1的format-info命令仅展示版本/格式/路径，**不会加载或验证外部包**。
