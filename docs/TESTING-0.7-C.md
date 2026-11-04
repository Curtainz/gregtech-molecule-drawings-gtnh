# 0.7.0-test.C：中文对照、可读公式和流体入口

移除旧JAR，安装install包中的mods与config，保留用户扩展包。本包沿用两个外部包，安装ZIP不含文档。

## 本轮修复

GT名称改用 getLocalizedNameForItem 而非只返回默认英文的 getDefaultLocalizedNameForItem。只有确有汉字的中文名称才标为game_zh_CN；没有真实翻译时使用明确标注的reference_zh_CN，仍无参考则记录missing_translation。localizedName优先实际游戏翻译，没有时可显示带参考标记的中文；gameLocalizedNameRaw单独保留游戏返回原名，不假称所有材料都存在汉化。

公式列将私用区下标零转换为₀，上标数字转换为标准Unicode。魔法字形使用中文标记，未知字形用U+代码表示；formulaRawEscaped保存原始编码，所有替换只影响审核展示，不据此编造组成。

现有354条材料定义全部提供中文查询参考，preview默认加载名称参考，无需额外audit参数。实机audit可以补充实际关联名；names-report.json列出仍缺名称的结构／片段。参考名称不用于材料或异构体绑定，不确定名称保留位次／类型提示。

## 流体展示验收

NEI流体显示栈和ModularUI 1流体槽优先读取对象身份；其它界面继续名称回退。结构优先，缺结构的可靠组成默认显示饼图；缺质量时降级数量图并显示说明。ModularUI 2及其它储罐不能因本次适配而视为已全部支持。

请分别悬停熔融钢、熔融双酚A、液态聚苯并咪唑、二甲苯的NEI配方区域及储罐流体槽。检查物品和流体不重复插图、Shift、配置切换、F3+T、语言切换及反复进出世界。

## 导出与图册

在中文环境重新 /gtmoldraw audit，完成后检查新index.html的钢、龙、聚苯并咪唑名称及formula列。已有旧报告可离线生成派生审核：

`java -jar gtmoldraw-pack-tool-0.7.0-test.C.jar review-audit <snapshot.json> <新目录>`

派生审核保留原始版本与原游戏名称，使用参考译名，不能当成C版实测。图册支持 --audit；构建支持 -PauditSnapshot=绝对路径。
