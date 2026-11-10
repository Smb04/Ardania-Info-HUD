# Ardania Info HUD 2.0
<center>
<img src="https://cdn.modrinth.com/data/cached_images/cd174cfa981a0ef994671f1cf9344b4a198a0d23.png" alt="Ardania Info HUD Logo" width="128" height="128" />
</center>

A compact, sleek client-side info overlay for the German Minecraft server **Ardania.de**.  
Compatible with **Fabric** for Minecraft **1.21.X (1.21 – 1.21.11)**, and **26.2 / 26.3** via one shared 26.x JAR.
---

## ✨ Features (Version 2.0)
* 🖱️ **Visual In-Game HUD Editor (Free Mouse Drag & Drop in all versions)**:
  * Move your HUD freely anywhere across the screen using mouse drag & drop or arrow keys (`/ardaniahud edit` or from ModMenu).
  * **Dual-Layer Input Engine**: Flawless compatibility across GLFW (MC 1.21.x & 26.2) and SDL3 (MC 26.3).
  * **Magnetic Snap Guides (*Snap-to-Edge & Center*)**: Automatic snap alignment to borders and centerlines with clean Cyan guidelines.
  * **Keyboard Nudging**: Pixel-precise positioning via arrow keys (5px steps with `Shift`).
  * **Auto-Dodge Toolbar**: Toolbar buttons automatically move to the top when the HUD is placed at the bottom.
  
* 📐 **Ergonomic 6 Anchor Presets (DIN EN ISO 9241-110)**:
  * 6 preset positions: `Middle Right` (classic Hypixel style), `Top Right`, `Bottom Right`, `Top Left`, `Middle Left`, and `Bottom Left`.
  * Cycle scale from 75% to 125% directly in ModMenu.
  * Real-time viewport boundary clamping prevents off-screen clipping at any scale.

* 🛡️ **Intelligent AFK Protection (`/afk` + 10-Block Movement Threshold)**:
  * Typing `/afk` instantly pauses all background queries (`geld`, `ardapoints me`, `jobs stats`, `ontime`).
  * Your server-side AFK idle state is preserved until you move at least **10 blocks** away, re-type `/afk`, or receive a chat notification.

* 🌐 **Rock-Solid Fallback Server Detection `[F]`**:
  * Automatic detection of the Fallback server `[F]` prevents background query spam after server restarts.
  * Syntax errors (`Unknown or incomplete command...`) are caught silently—no chat clutter.
* ⛏️ **Multi-Job & Subtle Mini Progress Bar (Anti-Clutter)**:
  * Automatically detects all active professions via `/jobs stats`.
  * **Two Progress Modes**: Choose between clean percentage badges (`Mobhunter 80% [24]`) or a **subtle 2-pixel progress bar** rendered directly beneath each job title.
  * Maxed jobs (Level 30) stand out prominently in gold as `[MAX]`.
* 🏛️ **Smart Sub-Server Visibility Profiles**:
  * Automatically adapt HUD behavior per world:
    * **Creative Visibility**: `Hide` *(Default)* / `Tab Only` / `Show`.
    * **Event Visibility**: `Tab Only` *(Default)* / `Show` / `Hide`.
    * **Main & Mine Worlds**: `Show` *(Default)*.
  * Entering Creative automatically hides the HUD to keep your view clear.
* 💰 **Euronen (Currency & Live Profit Tracking)**:
  * Automatically fetches your real account balance directly via `[ArdaBank]`.
  * Instantly reacts to job earnings shown in the action bar (`+0.15$`, `+0.02 Euronen`) and updates your balance on the fly.
