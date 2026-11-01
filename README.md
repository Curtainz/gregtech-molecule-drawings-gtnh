# GregTech Molecule Drawings (1.7.10 GTNH Port)

<div align="center">

[![GitHub](https://img.shields.io/badge/Forked%20From-RubenVerg%2Fgregtech--molecule--drawings-blue.svg)](https://github.com/RubenVerg/gregtech-molecule-drawings)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.7.10-green.svg)](https://minecraft.net/)
[![GTNH](https://img.shields.io/badge/GregTech-New%20Horizons%202.9.x-orange.svg)](https://gtnewhorizons.com/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Vibe Coded](https://img.shields.io/badge/Vibe%20Coded%20with-Google%20Antigravity-8A2BE2.svg)](#-vibe-coding--ai-attribution--vibe-coding-%E8%AF%B4%E6%98%8E)

**[English](#english) | [简体中文](#-简体中文)**

</div>

---

<a name="english"></a>
## 📖 English

An interactive molecular structure diagram and dynamic alloy/compound composition chart mod for **Minecraft 1.7.10** and **GregTech: New Horizons (GTNH)**.

This repository is a backport and substantial architectural overhaul of [RubenVerg/gregtech-molecule-drawings](https://github.com/RubenVerg/gregtech-molecule-drawings) for 1.7.10 Forge, GregTech 5 Unofficial, and NotEnoughItems (NEI).

### 🔮 Vibe Coding & AI Attribution

> [!NOTE]
> **Vibe Coding Notice**
> 
> This Minecraft 1.7.10 GTNH port was developed and debugged in **Vibe Coding** mode using **Google Antigravity** (powered by Gemini).
> 
> * **Original Mod Author & Concept**: [RubenVerg](https://github.com/RubenVerg) — Creator of the modern GregTech Molecule Drawings mod.
> * **1.7.10 GTNH Port & AI Pair Programming**: Ported, diagnosed, and repaired from initial crash logs to a stable release through human-AI collaborative vibe coding.
> * **Major AI-driven Fixes & Additions**:
>   1. Completely eliminated crashing Mixins/ASM in favor of clean Forge event hooks and CodeChickenLib `GuiDraw$ITooltipLineHandler` reflection hooks.
>   2. Built a powerful chemical formula parser (`FormulaParser`) with support for GTNH fictional, magical elements, and Greek-letter unknown phases.
>   3. Re-engineered double bond and aromatic ring geometry math to eliminate line overlapping and disappearing bonds.
>   4. Solved NEI tooltip flickering and edge collision de-synchronization.
>   5. Synthesized missing JSON models for the entire silane family.

### ✨ Features in 1.7.10 GTNH

* **Molecular Structure Diagrams**: Renders structural chemical formulas and molecular bond diagrams directly inside item tooltips. Includes built-in models for Silanes ($\text{SiH}_4$, $\text{Si}_2\text{H}_6$, $\text{SiCl}_4$, $\text{SiF}_4$, $\text{SiHCl}_3$, $\text{SiO}_2$), ice, polymers, etc.
* **Precise Chemical Rendering**:
  * Double bonds maintain a constant 2.4px offset with 2px corner indentation, preventing lines from merging.
  * Polymer brackets have safe margins ($\pm 6\text{px}$ horizontal, $\pm 4\text{px}$ vertical) with statically anchored subscript $n$ that never drifts.
* **Dynamic Alloy & Compound Pie Charts**:
  * Automatically calculates mass-weighted or atom-count component breakdowns.
  * Full support for GTNH mythical elements (`D*` Awakened Draconium, `If*` Infinity, `Nq*` Naquadah, `Nt` Neutronium, `Sh` Shadow Metal, `Ma` Magic, etc.), restoring charts for **Void Metal** and **UEV Superconductor Base**.
  * Configurable folding thresholds to collapse minor elements into an "Other" category.
* **Non-Invasive Architecture**: Zero bytecode modifications, 100% crash-free, full compatibility with NEI and Chromatic Tooltips.

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

本项目是 [RubenVerg/gregtech-molecule-drawings](https://github.com/RubenVerg/gregtech-molecule-drawings) 面向 1.7.10 Forge、GT5-Unofficial 与 NEI 的深度重构与移植版本。

### 🔮 Vibe Coding 说明与致谢

> [!NOTE]
> **Vibe Coding 说明**
> 
> 本项目的 1.7.10 GTNH 移植与全面修复工作全程基于 **Google Antigravity**（Gemini 驱动）以 **Vibe Coding**（结对编程 / 人机协同）模式完成。
> 
> * **原模组作者与构想**：[RubenVerg](https://github.com/RubenVerg) — 现代版本 GregTech Molecule Drawings 模组的创造者。
> * **1.7.10 GTNH 移植与 AI 协同开发**：从最初的启动崩溃分析，到全功能无侵入式移植与渲染算法重构，均由人机协同以 Vibe Coding 模式快速迭代排查完成。
> * **主要 AI 攻坚与重构点**：
>   1. 彻底移除会导致崩溃的 Mixin/ASM 字节码注入，改用 Forge 原生事件与 CodeChickenLib `GuiDraw$ITooltipLineHandler` 动态反射接口。
>   2. 构建了支持 GTNH 虚构元素、魔法元素及特殊希腊字母的化学式解析引擎（`FormulaParser`）。
>   3. 重写多重键与芳香环的法向量平移算法，彻底解决双键与主键重叠合并不显示的数学缺陷。
>   4. 攻克 NEI 提示框屏幕防遮挡位移不同步及图表高频闪烁问题。
>   5. 为硅烷全族分子补齐并建立了原生的分子结构 JSON。

### ✨ 1.7.10 GTNH 版本特性

* **分子结构式与化学键图表展示**：在物品提示框（Tooltip）内直观展示分子结构式与化学键连接。
  * **硅烷族模型补齐**：为甲硅烷（$\text{SiH}_4$）、乙硅烷（$\text{Si}_2\text{H}_6$）、四氯化硅（$\text{SiCl}_4$）、四氟化硅（$\text{SiF}_4$）、三氯硅烷（$\text{SiHCl}_3$）及二氧化硅（$\text{SiO}_2$）建立了专属分子结构模型。
* **精准化学结构渲染**：
  * **碳碳双键及芳香环修补**：采用双线恒定 2.4px 法向平移与拐角 2px 缩进，彻底杜绝双键重合失效。
  * **高分子方括号与下标固定**：设置了安全的包围盒留白（水平 $\pm 6\text{px}$，垂直 $\pm 4\text{px}$），下标 $n$ 采用静态包围盒绝对锚定在右括号 `]` 底部，绝不发生位移与晃动。
* **动态合金与化合物饼状图**：
  * 支持质量占比（Mass）与原子数占比（Count）两种计算模式。
  * **完美兼容 GTNH 虚构与魔法元素**：完整解析并高亮展示 `D*`（觉醒龙）、`If*`（无尽）、`Nq*`（超能硅岩）、`Nt`（中子素）、`Sh`（暗影金属）、`Ma`（魔法组分）等，**虚空金属**与 **UEV 超导粗胚**等终极材料饼图全面恢复。
  * **智能折叠与阈值过滤**：成分过多或占比微小的稀有元素自动折叠并入“其他”，避免图表错位臃肿。
* **纯净非侵入式架构**：不侵入游戏底层渲染循环，杜绝崩溃风险，完美兼容 NEI 与各类提示框美化模组。

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
