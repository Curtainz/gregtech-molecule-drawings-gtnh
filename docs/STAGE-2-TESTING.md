# 第②阶段：离线组装、校验与可视化

版本：0.6.0-stage.2。第①阶段游戏JAR已由用户实测正常。本阶段交付独立工具；游戏仍使用已验证的旧资源加载路径，尚未启用JSONC挂载或reload。

## 先看图，无需阅读JSON

解压 `gtmoldraw-stage-2-preview.zip`，用浏览器打开 `preview/index.html`。不要直接在压缩包中打开，否则关联SVG可能无法显示。

图册分为结构组装结果与覆盖后的材料显示意图：

- 水、苯、羟基片段、苯酚、共享原子的稠环及聚合物括号。
- 稠环应有10个原子、11条唯一键、5条双键；没有自动推测或凯库勒化。
- 普通合金数量份额为铜3/4、锌1/4；嵌套合金为铜3/8、锌1/8、锡1/2。
- 用户包将水材料整条覆盖为文本模式，故水的结构定义仍可预览，而该材料不生成图形。

图形使用游戏同一套纯布局与像素几何代码，字体为近似测量；离线配色不是GT材料颜色。饼图仅展示能确定的数量份额，未连接GT注册表，不生成虚构质量。苯环美观与自动布局按原计划延后。

## 游戏JAR验收

仅将 `gtmoldraw-0.6.0-stage.2.jar` 放进mods，禁用旧版本。**工具JAR不放进mods**；预览包也无需安装到游戏目录。

1. 冷启动并运行 `/gtmoldraw format-info`，应显示stage 2，并明确External loading is NOT enabled。
2. 检查原有分子图、饼图、库存/NEI、ChromaticTooltips、Shift和数量/质量切换。
3. 检查F3+T、语言/配置切换及反复进出世界；若出现异常，保留当次日志和native崩溃报告。

新增工具没有接入启动、tooltip或渲染事件。本阶段不是DLL崩溃根因的证明；历史故障继续作为回归场景。

## 可选：自己修改样例并重新生成图册

工具仅需Java 25，不启动Minecraft、不联网。以下命令在解压目录执行；`java`需指向Java 25：

```text
java -jar tools/gtmoldraw-pack-tool-stage-2.jar validate packs
java -jar tools/gtmoldraw-pack-tool-stage-2.jar format --check packs
java -jar tools/gtmoldraw-pack-tool-stage-2.jar format --write packs
java -jar tools/gtmoldraw-pack-tool-stage-2.jar preview packs my-preview
java -jar tools/gtmoldraw-pack-tool-stage-2.jar preview packs one-preview --id official:naphthalene_structure
```

请先备份再格式化自己的文件。格式化保留注释与值、统一字段顺序/两空格缩进/LF；全部输入校验、值一致性和幂等检查完成后才写入，每个文件原子替换。多个文件遇到磁盘写入故障时不保证整体回滚。

返回码0成功、1需要格式化、2输入/IO错误。诊断包含文件与行列/字段位置。GT材料身份和质量必须留待游戏适配器验证，离线提示不代表游戏覆盖率。

预览输出必须位于包目录外；已有非空目录必须是本工具生成的目录。输出先完成所有布局，再逐文件原子写入；目录不是事务式发布，重新生成失败时不要以旧页面判断成功。可以指定 `--id` 缩小预览。

## 开发验证与边界

执行 `gradlew build` 运行现有布局、组成、栅格及包回归，检查游戏发布包边界，同时构建独立工具与预览ZIP。另执行 `build/contract-env/Scripts/python.exe tools/contract/check.py`，使用标准JSON Schema实现交叉验证样例（依赖安装见tools/contract）。

未交付：全部旧资源转换、游戏外部加载、事务式热重载、MOL导入。下一步是第③阶段全量迁移；通过本阶段手动验收后再推进。
