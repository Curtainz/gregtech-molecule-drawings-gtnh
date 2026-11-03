# 第①阶段审阅包

这是JSONC格式v1的审阅样例，不是完整官方材料包，也不是已启用的运行时数据。

- packs/official：水、苯、苯酚、稠环、聚合物和组成样例。
- packs/user：必须显式声明覆盖与依赖的示例。
- schema/：JSON Schema 2020-12，可供支持JSON Schema的编辑器使用；JSONC语义仍须后续工具验证。
- FORMAT-V1.md：字段、模块、坐标、共享及覆盖的规范。

0.6.0-stage.1仍使用0.5的加载行为；此ZIP不应作为完整数据包安装。解压到任意审阅目录查看即可。

Java/GTNH游戏测试使用同版本游戏JAR；进世界运行 /gtmoldraw format-info，应明确提示stage1及external loading not enabled。检查原有水、苯、聚合物、饼图、Shift与NEI等仍正常。新JSONC实例暂时不会显示在游戏中；对应功能从后续阶段实现。
