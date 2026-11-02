# GregTech Molecule Drawings (1.7.10 GTNH Port)

<div align="center">

[![GitHub](https://img.shields.io/badge/Forked%20From-RubenVerg%2Fgregtech--molecule--drawings-blue.svg)](https://github.com/RubenVerg/gregtech-molecule-drawings)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.7.10-green.svg)](https://minecraft.net/)
[![GTNH](https://img.shields.io/badge/GTNH-2.9.0-orange.svg)](https://gtnewhorizons.com/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Vibe Coded](https://img.shields.io/badge/Vibe%20Coded%20with-Google%20Antigravity-8A2BE2.svg)](#-vibe-coding--ai-attribution--vibe-coding-%E8%AF%B4%E6%98%8E)

**[English](#english) | [简体中文](#-简体中文)**

</div>

---

<a name="english"></a>
## 📖 English

A lightweight, non-invasive item tooltip enhancement mod for **Minecraft 1.7.10** and **GregTech: New Horizons (GTNH)** that displays clear 2D chemical molecular structure diagrams and alloy/compound elemental composition pie charts directly inside item tooltips and NEI recipe views.

This repository is a backport and adaptation of [RubenVerg/gregtech-molecule-drawings](https://github.com/RubenVerg/gregtech-molecule-drawings) for 1.7.10 Forge, GregTech 5 Unofficial, and NotEnoughItems (NEI).

### 🔮 Vibe Coding & Attribution

> [!NOTE]
> **Vibe Coding Notice**
> 
> This Minecraft 1.7.10 GTNH port was developed and refined in **Vibe Coding** mode using **Google Antigravity** (powered by Gemini).
> 
> * **Original Concept & Author**: [RubenVerg](https://github.com/RubenVerg) — Creator of the modern GregTech Molecule Drawings mod.
> * **1.7.10 GTNH Port & AI Pair Programming**: Ported, stabilized, and refactored from initial crash logs to a stable release through human-AI collaborative pair programming.

### ✨ Implemented Features (v0.3.0)

* **2D Vector Molecular Structure Diagrams**:
  * Renders 2D structural formulas with atomic symbols and chemical bonds directly in tooltips.
  * Supports single, double, and triple bonds with geometric offset calculations.
  * Supports aromatic rings (benzene rings in both circle and Kekulé modes) and polymer brackets `[` `]` with clean subscript *n*.
  * Built-in models for common organic molecules, polymers, and the silane family (SiH₄, Si₂H₆, SiCl₄, SiF₄, SiHCl₃).
  * Atom labels feature background masking, preventing bond lines from overlapping or cutting through text.
* **Alloy & Compound Composition Pie Charts**:
  * 2D scanline-rasterized circular pie charts for alloys, mineral networks, and compounds.
  * Recursive decomposition: Breaks down complex compounds (e.g. Redstone in Red Alloy) into elemental constituent slices.
  * Leader lines connecting each slice to its percentage and component name.
  * Configurable component limits and percentage thresholds to group minor traces into "Other".
* **GregTech Item & Fluid Recognition**:
  * Recognizes GT materials, fluids, fluid display items, IC2 universal fluid cells, Forestry capsules, and tanks via GT internal registry and OreDictionary (`cell...`, `fluid...`, `liquid...`, `gas...`).
* **Zero-Lag & Non-Invasive Tooltip Integration**:
  * Uses dedicated CodeChickenLib / NEI tooltip line handlers without injecting dummy string placeholders (eliminating jittering numbers and "0" artifact bugs).
  * Strict OpenGL pipeline state isolation (`glPushAttrib` / `glPushMatrix`), preventing GUI visual corruption, flickering, or FPS drops.
* **Full In-Game Configuration**:
  * Automatically generates `config/moldraw.cfg` on first launch.
  * Supports Forge In-Game Mod Options configuration menu with instant color and cache reloading.

### ⚙️ Configuration (`config/moldraw.cfg`)

| Category | Key | Default | Description |
| :--- | :--- | :--- | :--- |
| `general` | `enabled` | `true` | Master switch for the mod |
| `general` | `onlyShowOnShift` | `true` | Only show drawings when holding <kbd>Shift</kbd> |
| `molecule` | `showMolecules` | `true` | Display molecule structure diagrams |
| `molecule` | `moleculeScale` | `20` | Scale factor for molecule diagrams (10 - 50) |
| `molecule` | `benzeneCircle` | `CIRCLE` | Benzene ring mode: `CIRCLE` or `DOUBLE_BONDS` |
| `alloy` | `showAlloys` | `true` | Display alloy composition pie charts |
| `alloy` | `pieChartRadius` | `32` | Radius of alloy pie chart in pixels (20 - 64) |
| `alloy` | `partsByMass` | `true` | Calculate proportions by mass rather than molar count |
| `alloy` | `maxComponentsDisplayed` | `8` | Maximum slices before grouping into "Other" (1 - 30) |
| `alloy` | `minPercentageThreshold` | `1.0` | Minimum percentage for a component before folding into "Other" |
| `alloy` | `recursive` | `true` | Recursively decompose sub-alloys into pure elements |
| `color` | `colors` | `true` | Enable coloring |
| `color` | `useMaterialColors` | `true` | Use GregTech material colors for elements |

### 🛠️ Building from Source

Requires **Java 17 - 21**:

```bash
git clone https://github.com/Curtainz/gregtech-molecule-drawings-gtnh.git
cd gregtech-molecule-drawings-gtnh
./gradlew build
```

Compiled jar will be located at `build/libs/`.

---

<a name="-简体中文"></a>
## 📖 简体中文

为 **Minecraft 1.7.10** 与 **GregTech: New Horizons (GTNH)** 提供的轻量级物品提示框增强模组。在物品悬浮窗（Tooltip）与 NEI 配方界面中直观呈现清晰的 2D 分子结构式与合金/化合物元素成分饼状图。

本项目是 [RubenVerg/gregtech-molecule-drawings](https://github.com/RubenVerg/gregtech-molecule-drawings) 面向 1.7.10 Forge、GT5-Unofficial 与 NEI 的移植与适配版本。

### 🔮 Vibe Coding 说明与致谢

> [!NOTE]
> **Vibe Coding 说明**
> 
> 本项目的 1.7.10 GTNH 移植与修复适配工作基于 **Google Antigravity**（Gemini 驱动）以 **Vibe Coding**（结对编程 / 人机协同）模式进行。
> 
> * **原模组作者与构想**：[RubenVerg](https://github.com/RubenVerg) — 现代版本 GregTech Molecule Drawings 模组的创造者。
> * **1.7.10 GTNH 移植与 AI 协同开发**：从早期的启动崩溃与死锁排查，到渲染管线隔离、NEI 防闪烁重构以及饼图光栅化对齐，均由人机协同以 Vibe Coding 模式迭代完成。

### ✨ 当前实现的功能 (v0.3.0)

* **2D 纯向量分子结构式展示**：
  * 在物品悬浮窗内以 2D 平面投影呈现化学分子结构式与原子符号。
  * 完整支持单键、双键与三键几何偏移渲染。
  * 支持芳香环（苯环环形与凯库勒式双键模式）及聚合物括号 `[` `]`（下标 *n* 规范坐落于安全区域）。
  * 内置常见有机小分子、聚合物及硅烷族模型（SiH₄, Si₂H₆, SiCl₄, SiF₄, SiHCl₃）。
  * 原子文字位于顶层且自带背景底色遮罩，彻底杜绝底层骨架连线穿透文字。
* **合金与化合物成分饼状图**：
  * 采用 2D 扫描线光栅化绘制圆形成分饼图，颜色完整填充，零深度冲突。
  * 递归解离：自动将复合材料（例如红色合金中的红石基团）深度拆解为纯元素单质。
  * 清晰的白色引出折线与成分百分比文字。
  * 支持自定义最大扇区数与微量成分阈值，自动将过细碎片合并为“其他”。
* **格雷科技物品与流体容器全面识别**：
  * 智能识别 GT 材料、流体、流体显示槽、IC2 通用流体单元、Forestry 胶囊及各型储罐（基于 GT 内部注册表与矿物词典 `cell...`, `fluid...`, `liquid...`, `gas...`）。
* **零卡顿与非侵入式提示框集成**：
  * 采用 CodeChickenLib / NEI 原生提示行句柄，不注入虚拟占位字符串（彻底解决数字跳动与数字“0”的显示 Bug）。
  * 严格的 OpenGL 状态隔离（`glPushAttrib` / `glPushMatrix`），彻底杜绝渲染导致的画面闪烁、GUI 变色或帧率骤降。
* **完整的配置系统与游戏内选项**：
  * 首次启动自动生成 `config/moldraw.cfg` 配置文件。
  * 深度支持 Forge 游戏内 Mod Options 配置界面，修改颜色与参数后免重启即时刷新。

### ⚙️ 配置文件说明 (`config/moldraw.cfg`)

| 配置分类 | 配置键 | 默认值 | 功能说明 |
| :--- | :--- | :--- | :--- |
| `general` | `enabled` | `true` | 模组总开关 |
| `general` | `onlyShowOnShift` | `true` | 是否仅在按住 <kbd>Shift</kbd> 键时才展示图表 |
| `molecule` | `showMolecules` | `true` | 是否启用分子结构式图表展示 |
| `molecule` | `moleculeScale` | `20` | 分子模型缩放比例（10 - 50） |
| `molecule` | `benzeneCircle` | `CIRCLE` | 苯环显示模式：`CIRCLE`（圆环）或 `DOUBLE_BONDS`（双键） |
| `alloy` | `showAlloys` | `true` | 是否启用合金与化合物成分饼状图 |
| `alloy` | `pieChartRadius` | `32` | 饼状图渲染半径像素（20 - 64） |
| `alloy` | `partsByMass` | `true` | 按原子质量折算百分比（若为 false 则按摩尔原子数） |
| `alloy` | `maxComponentsDisplayed` | `8` | 饼图最多单独展示的元素数（超出折叠为“其他”） |
| `alloy` | `minPercentageThreshold` | `1.0` | 低于此百分比的微量元素自动折叠为“其他” |
| `alloy` | `recursive` | `true` | 是否递归拆解合金内的子复合材料为单质元素 |
| `color` | `colors` | `true` | 是否启用文字与图表颜色 |
| `color` | `useMaterialColors` | `true` | 是否使用格雷科技材质原生颜色渲染元素 |

### 🛠️ 从源码编译

需要 **Java 17 - 21** 环境：

```bash
git clone https://github.com/Curtainz/gregtech-molecule-drawings-gtnh.git
cd gregtech-molecule-drawings-gtnh
./gradlew build
```

编译产物位于 `build/libs/` 目录下。

---

## 📄 License & Credits / 开源协议与致谢

* 原模组项目：[RubenVerg/gregtech-molecule-drawings](https://github.com/RubenVerg/gregtech-molecule-drawings)（MIT License）
* 1.7.10 GTNH 移植版本：遵循 [MIT License](LICENSE) 开源。
