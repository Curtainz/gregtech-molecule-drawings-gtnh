# 0.7.0-test.A：材料、流体与中文审核基线

本阶段交付 audit 和 preview 名称对照。虚构物种修复在 0.7-B，直接流体 GUI 适配在 0.7-C，本阶段不宣称修复这些显示入口。

## 安装与实机验收

1. 备份原 JAR 和官方数据包，将本次 install ZIP 解压到 .minecraft，删除旧 gtmoldraw JAR，保留用户扩展包。
2. 使用简体中文进入世界，等待 `/gtmoldraw status` 显示加载完成，然后运行 `/gtmoldraw audit`。
3. 命令排队后可继续游戏；日志出现 Audit completed 后打开 gtmoldraw-audit/index.html。
4. 查询钢、双酚A、聚苯并咪唑、二甲苯，核对名称与 fluid: 注册ID。materials.csv 与 fluids.csv 分开，snapshot.json 包含两者及本次版本、配置、来源哈希。
5. 检查普通 tooltip、Shift、NEI、F3+T 及失败热重载仍正常。

nameZhCN 仅在 zh_CN 环境捕获。其它语言导出会明确缺中文，选择中文重新导出即可；不会把英文默认名称伪装成中文。本阶段中文取实际当前翻译，不独立加载另一套汉化。

## 名称对照图册

用 Java25 运行：

```powershell
java -jar gtmoldraw-pack-tool-0.7.0-test.A.jar preview <packs目录> <新图册目录> --audit <gtmoldraw-audit/snapshot.json>
```

图册卡片显示中文、原名、身份；索引和卡片支持本页搜索。旧定义无实际材料绑定时可能缺名称，不猜测翻译。图册字体与游戏字体可能不同。

## 数据含义

quantityValid/massValid 是解析器是否返回份额；保留终止节点也可能为 true。quantityResolved/massResolved 采用多成分且无影响对应模式的未知／循环等阻断诊断的保守条件，knownMass 单独标记根材料质量。它们都不是化学审核结论。displayAssessment 标记 data_only_not_GUI_verified，不将后台数据检查当成 GUI 实测。

导出采用固定运行时快照，不生成字体布局、不触碰 GL；当前语言名称和流体关联在客户端捕获。完成与失败记录于日志，同一时刻仅一个导出。CSV 使用 UTF-8 BOM，表格文本防公式注入，HTML 转义。

每个文件通过临时文件原子替换；文件组不是跨文件事务。以 snapshot.json 为本次元数据权威来源。旧版 CSV 字段有所变化，分析脚本应读取 formatVersion=1 的 snapshot.json。
