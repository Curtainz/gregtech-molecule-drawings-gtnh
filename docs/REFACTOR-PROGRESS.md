# 稳定性重构进度（develop / 0.4.1-dev）

基线为 v0.4.0；本轮从整理后的 origin/develop（6930488）继续。遵循 main/develop 约定，不修改已发布标签，不新增 Release。苯环画法与名称+化学式生成结构功能留待后续。

## 本轮完成

- **纯计算与绘制提交分离**：PixelRaster 保留原 Bresenham 线段和扫描填圆算法；PixelBatch 在没有 Minecraft/GL 的环境中计算坐标、筛选和颜色，再交给 GraphicalUtils 提交。资源/颜色回调异常发生在 Tessellator.startDrawingQuads 之前，避免留下共享批次。
- **提交生命周期**：成功开始的 Tessellator 批次在 finally 中结束，GL 属性也在 finally 中恢复；不捕获 native 错误或 VM Error。
- **输入边界**：线段跨度不超过 8192 像素，圆半径 0..256（现有饼图配置上限64），位置绝对值不超过 1,000,000。超限数据进入 Java 错误路径，避免整数溢出或无界分配。
- **绘制异常隔离**：TooltipDrawPass 捕获 RuntimeException，记录一次并暂停相应 drawable；弱引用记录随对象释放，资源/配置重载后清除。矩阵明确使用 MODELVIEW，结束后恢复原矩阵模式。fallback 待绘制状态在 finally 中清理。
- **NEI 公开接口**：固定 compileOnly CodeChickenCore 1.4.22。用具体 ITooltipLineHandler 实现、GuiDraw.getTipLineId / getTipLine 注册和判断所属 token。移除动态代理、私有字段反射、手动修改全局列表及跨帧 handler 缓存。由 CodeChicken 自身管理列表生命周期。
- **线程边界**：配置事件只设置待更新标记；配置同步与缓存失效集中到客户端 tick。资源读取、缓存失效及 GL 提交检查客户端线程。
- **构建边界**：check 增加产物检查，拒绝意外打入 LWJGL、Minecraft、CodeChicken、FastUtil、native 库及回归测试类；核实 CI 已覆盖 main/develop。

## 验证与实测

运行 `gradlew.bat spotlessApply build --console=plain`。原有化学式13场景、分子集合6场景，以及新增121个方向终点/长度检查、6种圆半径对称性、筛选/颜色回调、只读批次和超限检查。纯计算测试不创建 GL 上下文。

需要游戏验证：多次冷启动；普通库存/NEI物品及流体配方提示；ChromaticTooltips配合；Shift切换；饼图和已有分子结构；F3+T及配置修改；关闭世界再进入；`/gtmoldraw audit`。重点确认移除私有列表修改后，NEI/Chromatic缓存提示框仍保留正确的图形行。

此前 native 故障发生在早期加载画面，尚无 Java 调用栈证明具体根因。本轮消除了已定位的线程、回调、状态和集成风险，不能据构建成功声称 SDL3 DLL 崩溃已根治。

## 后续

实机通过后继续分离完整分子/饼图布局，建立不可变展示数据与缓存版本；随后实现材料注册 API、外部脚本前端及事务式热重载。结构生成与苯环美观放在同一阶段处理。组成权重、材料桥接及结构推断仍未完成。
