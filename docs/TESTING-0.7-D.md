# 0.7.0-test.D：原生提示框、安装检测和MOL导入

本包继续0.7覆盖与0.8离线结构工具路线，集中交付以下成果。

## 提示框

ChromaticTooltips安装时注册原生gtmoldraw section，通过其FluidStack/ItemStack目标读取身份，直接提供可测量、可分页的绘图组件。此路径不再生成瞬时CodeChicken §x令牌；Shift使用提示框自身modifier状态，资源／配置刷新使旧组件失效，下一次原生提示框重建时读取新快照，不修改其它模组的提示框缓存。无Chromatic时保留NEI／普通提示框适配，并清理缓存提示行，失效／换材料／松开Shift时撤销旧图形，防止重复或残留。统一流体提取支持Forge IFluidContainerItem，因此覆盖NEI ItemFluidDisplay及一般流体容器。

/gtmoldraw status显示chromaticNative、builds、draws和最近subject/error；audit快照元数据也记录tooltipAdapter，便于一次集中反馈。已通过独立Java进程加载实际ChromaticTooltips 1.0.36 API的测量、分页、绘制回调和过期抑制测试；该测试不执行GL提交，不能代替储罐GUI实测。

## 安装检测

所有版本保持modid gtmoldraw。新增早期安装检查，类名独立于普通mod入口，不注册ASM或访问转换器，不加载材料或渲染。检查mods和mods/1.7.10中的JAR/ZIP，依据负载类／metadata识别，包括改名JAR及历史moldraw ID。发现冲突中止启动并列出路径。disabled文件、backup文件夹和其它MC版本子目录不误报。

移除旧JAR后解压install包即可；不需要同时保留旧版本。还可使用离线工具 `install-check <mods目录>` 预检。

## MOL V2000导入（0.8第一步）

`java -jar gtmoldraw-pack-tool-0.7.0-test.D.jar import-mol <input.mol> <new-output.jsonc> --id custom:molecule`

仅导入单条二维V2000。保留原子坐标、显式氢、M CHG及M ISO属性、单／双／三键、实／虚楔形键；D/T转为H同位素。不自动绑定材料、不推测隐式氢、不联网。输出必须不存在，schema及几何校验成功后写入，并在注释中记录源文件SHA-256。

V3000、三维坐标、芳香键类型4、查询原子／键、自由基、相对同位素质量、原子奇偶／全局chiral标志、聚合物S组、反应字段、多个SDF记录及未知属性均明确拒绝。芳香分子请导出明确交替单双键。错误包含来源行号。带电／同位素／显式氢碳不会被骨架省略规则隐藏。

## 一次集中验收

1. 冷启动，status显示chromaticNative=true且两包已载入。
2. 在GT储罐和NEI分别检视熔融钢、双酚A、聚苯并咪唑、二甲苯，连续按下／松开Shift；确认提示和图形切换、背景尺寸正确、无重复图形。观察status的builds/draws应增加。
3. 测试普通物品、切换材料、F3+T、配置／语言切换及重复进出世界。
4. 重新audit核对名称、公式；旧audit和preview保留中文查询功能。
5. MOL导入及重复安装自动回归已覆盖；不要为测试重复安装而覆盖存档或更改配置。

本轮未宣称完成全部第三方GUI和数据库批量重绘。之后继续离线身份核验、代表结构重绘与其它材料适配。

## 已执行的真实客户端子进程测试

在独立的Prism实例GTMolDraw-AutoTest-0.7D中，用GTNH 2.9.0 / Java25 / ChromaticTooltips 1.0.36进行两次冷启动；仅复制模组、配置和启动依赖，没有复制存档，也未替换正常实例的模组。客户端成功加载2个数据包、1053条定义。

第二次启动在真实GUI绘制事件中构造原生Chromatic提示框，覆盖water、molten.steel、molten.bisphenol_a、molten.polybenzimidazole及1,3dimethylbenzene；每种流体分别通过FluidStack和文本身份识别，按NONE→SHIFT→NONE→SHIFT切换，共40个场景。所有组件尺寸有效，20次图形绘制回调实际执行，未记录GL错误或绘制异常，随后测试客户端自动退出。

记录见[test-results/0.7.0-test.D-client-smoke.json](test-results/0.7.0-test.D-client-smoke.json)，其中gameJarSha256绑定本次实际运行的游戏JAR。可从项目根目录执行：

```text
python tools/testing/validate_client_smoke.py docs/test-results/0.7.0-test.D-client-smoke.json --jar build/libs/gtmoldraw-0.7.0-test.D.jar
```

复现时仅在隔离测试实例的.minecraft根目录创建空文件gtmoldraw-smoke-test.enabled；保留onlyShowOnShift=true、上述注册流体及匹配数据包。客户端进入主菜单绘制阶段后执行测试，写出gtmoldraw-smoke-results.json并退出。删除该标记即可正常启动；安装包不包含标记。该过程不模拟鼠标悬停、实际键盘事件或进入世界，也不进行像素美观判定，不能代替GT储罐／NEI真实界面的交互验收。

离线MOL导入另有23项回归检查，并以合成的凯库勒苯环MOL夹具跑通import-mol→validate→preview；此夹具只验证工具链，不是数据库结构核验或正式重绘数据。
