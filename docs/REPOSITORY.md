# 分支与版本约定

- `main`：发布基线，默认分支。目前基于 v0.4.0；说明文档可以在其后更新。
- `develop`：后续重构集成分支。目前为 0.6.0-stage.2，交付离线校验/组装/格式化工具和图册，等待本阶段手动验收；0.5及第①阶段游戏JAR已由用户实测通过。
- `v0.3.0`：旧 MolDraw 历史里程碑。
- `v0.4.0`：统一 gtmoldraw 标识、凯库勒式苯环的存档版本；仍标记为预发布，新增画面效果待实机确认。

临时功能分支合并后删除。仅为可交付的里程碑创建版本标签和 Release，不为每次修复创建标签。已发布标签保持不变。开发代码验证完成后通过 PR 合入 main，再创建下一个版本。

## 2026-11-03 清理记录

移除已被 main 包含的 master 分支；移除由 v0.3.0 完整标记的旧 gtnh 分支；将 refactor/render-isolation 的提交完整保留在 develop 后删除旧分支。

移除 v0.1.0-beta.*、v0.2.0-beta.* 和旧 v1.0.0 标签，以消除早期测试引用和版本编号歧义。清理前所有标签、分支及其提交已保存为完整 Git bundle，并附在 [v0.4.0 存档 Release](https://github.com/Curtainz/gregtech-molecule-drawings-gtnh/releases/tag/v0.4.0) 中：

- `repository-before-cleanup.bundle`：完整提交和引用备份。
- `refs-before-cleanup.txt`：清理前引用清单（含 annotated tag 对象及目标提交）。

恢复时先下载 bundle，在独立目录执行 `git clone repository-before-cleanup.bundle restored-history`，再用 `git bundle list-heads repository-before-cleanup.bundle` 查找所需引用。备份中的远端分支引用可以通过显式 fetch 恢复，例如 `git fetch ../repository-before-cleanup.bundle refs/remotes/origin/gtnh:refs/heads/legacy-gtnh`。请勿直接把全部旧引用重新推送到当前仓库。
