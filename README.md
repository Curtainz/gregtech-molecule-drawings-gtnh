# GregTech Molecule Drawings (1.7.10 GTNH Port)

[![GitHub](https://img.shields.io/badge/Forked%20From-RubenVerg%2Fgregtech--molecule--drawings-blue.svg)](https://github.com/RubenVerg/gregtech-molecule-drawings)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.7.10-green.svg)](https://minecraft.net/)
[![GTNH](https://img.shields.io/badge/GregTech-New%20Horizons%202.9.x-orange.svg)](https://gtnewhorizons.com/)
[![Vibe Coded](https://img.shields.io/badge/Vibe%20Coded%20with-Google%20Antigravity-8A2BE2.svg)](#-vibe-coding--ai-attribution)

An interactive molecular structure and alloy component visualizer for **Minecraft 1.7.10** and **GregTech: New Horizons (GTNH)**.

This repository is a backport and substantial rewrite of [RubenVerg/gregtech-molecule-drawings](https://github.com/RubenVerg/gregtech-molecule-drawings) for 1.7.10 Forge and GT5-Unofficial.

---

## 🔮 Vibe Coding & AI Attribution

> [!NOTE]
> **Vibe Coding Notice**
> 
> This Minecraft 1.7.10 GTNH port was developed and debugged in **Vibe Coding** mode using **Google Antigravity** (powered by Gemini).
> 
> * **Original Author & Design**: [RubenVerg](https://github.com/RubenVerg) — Creator of the modern GregTech Molecule Drawings mod.
> * **1.7.10 GTNH Port & AI Pair Programming**: Backported, diagnosed, and repaired from initial crash logs to a production-ready mod using iterative human-AI collaboration.
> * **Major AI-driven Fixes**:
>   1. Completely eliminated crashing ASM/Mixins in favor of clean Forge event hooks and CodeChickenLib `GuiDraw$ITooltipLineHandler` runtime reflection.
>   2. Designed a robust chemical formula parser (`FormulaParser`) handling GTNH-specific fictional elements, Greek symbols, and complex nested groups.
>   3. Re-engineered double bond and aromatic ring geometry math to eliminate line collapsing.
>   4. Solved NEI tooltip flickering and edge collision de-synchronization.
>   5. Synthesized missing 3D JSON models for the entire silane compound family.

---

## ✨ Features & Enhancements in 1.7.10 GTNH

### 1. 3D Molecule Visualizer
* Renders true 3D rotating molecular structures directly inside item tooltips.
* **Silane Family Support**: Added full 3D models for $\text{SiH}_4$, $\text{Si}_2\text{H}_6$, $\text{SiCl}_4$, $\text{SiF}_4$, $\text{SiHCl}_3$, and $\text{SiO}_2$.
* **Double Bonds & Aromatic Rings**: Fixed double-bond rendering math so secondary bond lines maintain a constant 2.4px offset with 2px corner indentation, preventing lines from merging.
* **Polymer Brackets**: Cleanly padded brackets with static, jitter-free subscript ($n$) positioning that does not drift during molecular rotation.

### 2. Dynamic Alloy & Compound Pie Charts
* Automatically breaks down alloys, complex materials, and non-modelled compounds into percentage pie charts with directional indicator lines.
* **GTNH Fictional & Magic Elements**:
  * Full support for endgame and magical elements: `D*` (Awakened Draconium), `If*` (Infinity), `Nq*` (Naquadah), `Nt` (Neutronium), `Sh` (Shadow Metal), `Ma` (Magic), `Sp` (SpaceTime), and unknown Greek-letter phases.
  * Correctly renders previously broken/missing items such as **Void Metal** (`ShFeMa?`) and **UEV Superconductor Base** (`D*?If*?...`).
* **Smart Folding**: Minor traces below a configurable threshold (or when exceeding maximum component count) are automatically collapsed into an "Other" category.
* **Mass vs. Atom Mode**: Choose between mass-weighted percentages or atomic count percentages.

### 3. Non-Invasive Architecture (Zero Crashes)
* **No Fragile Mixins**: Does not inject into obfuscated Minecraft GUI rendering loops, eliminating mod conflicts.
* **CodeChickenCore / NEI Integration**: Interacts seamlessly with NEI's tooltip placement, eliminating high-frequency flickering and screen-boundary tooltip separation.

---

## ⚙️ Configuration (`config/moldraw.cfg`)

| Category | Option | Default | Description |
| :--- | :--- | :--- | :--- |
| **general** | `enabled` | `true` | Master switch for the mod |
| **general** | `onlyShowOnShift` | `false` | Only render visualizations when holding <kbd>Shift</kbd> |
| **molecule** | `showMolecules` | `true` | Enable 3D molecule diagrams |
| **molecule** | `moleculeScale` | `16` | Scale factor for molecule models |
| **molecule** | `spinSpeed` | `1.0` | Rotation speed multiplier |
| **alloy** | `showAlloys` | `true` | Enable alloy / compound pie charts |
| **alloy** | `pieChartRadius` | `24` | Radius of the pie chart in pixels |
| **alloy** | `partsByMass` | `true` | Calculate percentages by atomic mass rather than count |
| **alloy** | `maxComponentsDisplayed`| `8` | Maximum slices before folding into "Other" |
| **alloy** | `minPercentageThreshold`| `1.0` | Slices below this % are folded into "Other" |

---

## 🛠️ Building from Source

Requires **Java 17-21**:

```bash
git clone https://github.com/<your-username>/gregtech-molecule-drawings.git
cd gregtech-molecule-drawings
git checkout gtnh
./gradlew build
```

The compiled mod jar will be in `build/libs/`.

---

## 📄 License & Credits

* Original Mod: [RubenVerg/gregtech-molecule-drawings](https://github.com/RubenVerg/gregtech-molecule-drawings) (MIT License)
* 1.7.10 GTNH Backport: Licensed under the [MIT License](LICENSE).
