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

An interactive molecular structure diagram and dynamic alloy/compound composition chart mod for **Minecraft 1.7.10** and **GregTech: New Horizons (GTNH)**.

This repository is a backport and adaptation of [RubenVerg/gregtech-molecule-drawings](https://github.com/RubenVerg/gregtech-molecule-drawings) for 1.7.10 Forge, GregTech 5 Unofficial, and NotEnoughItems (NEI).

### 🔮 Vibe Coding & AI Attribution

> [!NOTE]
> **Vibe Coding Notice**
> 
> This Minecraft 1.7.10 GTNH port was developed and refined in **Vibe Coding** mode using **Google Antigravity** (powered by Gemini).
> 
> * **Original Mod Author & Concept**: [RubenVerg](https://github.com/RubenVerg) — Creator of the modern GregTech Molecule Drawings mod.
> * **1.7.10 GTNH Port & AI Pair Programming**: Ported, diagnosed, and repaired from initial crash logs to a playable state through human-AI collaborative vibe coding.
> * **Key Modifications & Additions**:
>   1. Replaced crashing Mixins/ASM in favor of Forge event hooks and CodeChickenLib `GuiDraw$ITooltipLineHandler` reflection hooks.
>   2. Implemented a chemical formula parser (`FormulaParser`) supporting GTNH fictional elements, magical elements, and Greek-letter unknown phases.
>   3. Adjusted double bond and aromatic ring geometry math to resolve line overlapping.
>   4. Addressed NEI tooltip edge collision de-synchronization and flickering.
>   5. Added JSON molecular models for the silane family.

### ✨ Features in 1.7.10 GTNH

* **Molecular Structure Diagrams**: Renders structural chemical formulas and molecular bond diagrams directly inside item tooltips. Includes built-in models for Silanes (SiH₄, Si₂H₆, SiCl₄, SiF₄, SiHCl₃, SiO₂), ice, polymers, etc.
* **Chemical Rendering Improvements**:
  * Double bonds maintain a constant 2.4px offset with 2px corner indentation to prevent lines from merging.
  * Polymer brackets feature safe margins (±6px horizontal, ±4px vertical) with anchored subscript *n* that stays in place.
* **Dynamic Alloy & Compound Pie Charts**:
  * Automatically calculates mass-weighted or atom-count component breakdowns.
  * Supports GTNH mythical elements (`D*` Awakened Draconium, `If*` Infinity, `Nq*` Naquadah, `Nt` Neutronium, `Sh` Shadow Metal, `Ma` Magic, etc.), restoring charts for **Void Metal** and **UEV Superconductor Base**.
  * Configurable folding thresholds to collapse minor elements into an "Other" category.
* **Non-Invasive Implementation**: Minimal bytecode interaction, designed for stability and compatibility with NEI and tooltip mods.

### ⚙️ Configuration (`config/moldraw.cfg`)

| Category | Key | Default | Description |
| :--- | :--- | :--- | :--- |
| `general` | `enabled` | `true` | Master switch for the mod |
| `general` | `onlyShowOnShift` | `false` | Only render drawings when holding <kbd>Shift</kbd> |
| `molecule` | `showMolecules` | `true` | Enable molecular structure diagrams |
| `molecule` | `moleculeScale` | `16` | Scale factor for molecule diagrams |
| `alloy` | `showAlloys` | `true` | Enable alloy / compound pie charts |
| `alloy` | `pieChartRadius` | `24` | Radius of the pie chart in pixels |
| `alloy` | `partsByMass` | `true` | Calculate percentages by mass rather than atomic count |
| `alloy` | `maxComponentsDisplayed`| `8` | Maximum slices before folding into "Other" |
| `alloy` | `minPercentageThreshold`| `1.0` | Slices below this % are folded into "Other" |

### 🛠️ Building from Source

```bash
git clone https://github.com/Curtainz/gregtech-molecule-drawings-gtnh.git
cd gregtech-molecule-drawings-gtnh
./gradlew build
```

Compiled jar will be located at `build/libs/`.

---

<a name="-简体中文"></a>
## 📖 简体中文

为 **Minecraft 1.7.10** 与 **GregTech: New Horizons (GTNH)** 提供的物品提示框交互式分子结构式与合金/化合物成分图表展示模组。

本项目是 [RubenVerg/gregtech-molecule-drawings](https://github.com/RubenVerg/gregtech-molecule-drawings) 面向 1.7.10 Forge、GT5-Unofficial 与 NEI 的移植与适配版本。

### 🔮 Vibe Coding 说明与致谢

> [!NOTE]
> **Vibe Coding 说明**
> 
> 本项目的 1.7.10 GTNH 移植与修复适配工作基于 **Google Antigravity**（Gemini 驱动）以 **Vibe Coding**（结对编程 / 人机协同）模式进行。
> 
> * **原模组作者与构想**：[RubenVerg](https://github.com/RubenVerg) — 现代版本 GregTech Molecule Drawings 模组的创造者。
> * **1.7.10 GTNH 移植与 AI 协同开发**：从最初的启动日志分析，到各功能移植与渲染算法调整，均由人机协同以 Vibe Coding 模式迭代排查完成。
> * **主要改进与适配点**：
>   1. 移除引发崩溃的 Mixin/ASM 注入，改用 Forge 事件与 CodeChickenLib `GuiDraw$ITooltipLineHandler` 反射接口。
>   2. 构建了支持 GTNH 虚构元素、魔法元素及特殊希腊字母的化学式解析引擎（`FormulaParser`）。
>   3. 调整多重键与芳香环的法向量平移计算，改善双键与主键重叠合并不显示的问题。
>   4. 缓解并修正 NEI 提示框屏幕防遮挡位移不同步及图表闪烁问题。
>   5. 为硅烷全族分子补齐并建立了分子结构 JSON。

### ✨ 1.7.10 GTNH 版本特性

* **分子结构式与化学键图表展示**：在物品提示框（Tooltip）内直观展示分子结构式与化学键连接。
  * **硅烷族模型补齐**：为甲硅烷（SiH₄）、乙硅烷（Si₂H₆）、四氯化硅（SiCl₄）、四氟化硅（SiF₄）、三氯硅烷（SiHCl₃）及二氧化硅（SiO₂）建立了专属分子结构模型。
* **化学结构渲染优化**：
  * **碳碳双键及芳香环修补**：采用双线恒定 2.4px 法向平移与拐角 2px 缩进，减少双键重合遮挡现象。
  * **高分子方括号与下标固定**：设置了适宜的包围盒留白（水平 ±6px，垂直 ±4px），下标 *n* 锚定在右括号 `]` 底部，保持稳定不漂移。
* **动态合金与化合物饼状图**：
  * 支持质量占比（Mass）与原子数占比（Count）两种计算模式。
  * **支持 GTNH 虚构与魔法元素**：解析并高亮展示 `D*`（觉醒龙）、`If*`（无尽）、`Nq*`（超能硅岩）、`Nt`（中子素）、`Sh`（暗影金属）、`Ma`（魔法组分）等，恢复**虚空金属**与 **UEV 超导粗胚**等材料的饼图展示。
  * **折叠与阈值过滤**：成分较多或占比微小的元素可折叠并入“其他”，使图表更整洁。
* **非侵入式实现**：减少底层渲染侵入，提升运行稳定性，兼容 NEI 与常见提示框模组。

### ⚙️ 配置文件说明 (`config/moldraw.cfg`)

| 配置分类 | 配置键 | 默认值 | 功能说明 |
| :--- | :--- | :--- | :--- |
| `general` | `enabled` | `true` | 模组总开关 |
| `general` | `onlyShowOnShift` | `false` | 是否仅在按住 <kbd>Shift</kbd> 键时才展示图表 |
| `molecule` | `showMolecules` | `true` | 是否启用分子结构式图表展示 |
| `molecule` | `moleculeScale` | `16` | 分子模型缩放比例 |
| `alloy` | `showAlloys` | `true` | 是否启用合金与化合物成分饼状图 |
| `alloy` | `pieChartRadius` | `24` | 饼状图渲染半径（像素） |
| `alloy` | `partsByMass` | `true` | 按原子质量折算百分比（若为 false 则按原子数量） |
| `alloy` | `maxComponentsDisplayed`| `8` | 饼图最多单独展示的元素数量（超出部分折叠为“其他”） |
| `alloy` | `minPercentageThreshold`| `1.0` | 低于此百分比的微量元素自动折叠为“其他” |

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
