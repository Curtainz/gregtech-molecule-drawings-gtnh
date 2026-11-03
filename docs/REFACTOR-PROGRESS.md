# 绘制隔离重构进度

基线：v0.4.0（GitHub 归档预发布）；开发分支：develop，版本 0.4.1-dev。

已完成：NEI 行绘制和 fallback 提示框统一调用 TooltipDrawPass，统一客户端线程检查、混合设置以及 try/finally GL 状态恢复；分子骨架内部属性栈也补上 finally。构建及 19 个现有回归场景通过，GL 路径尚需游戏验证。

下一步：将 GraphicalUtils 的像素回调求值与 Tessellator 提交分离，修复中途回调异常时的 Tessellator/属性栈恢复；核实 NEI/CodeChicken 公开 API 后替换内部列表反射与代理；再分离纯布局计算。当前提交仅完成重构的第一步，不代表所有绘制路径已隔离。

归档版与开发版不可同时装入游戏。外部脚本注册、事务式热重载、材料桥接和组成权重仍待后续阶段。
