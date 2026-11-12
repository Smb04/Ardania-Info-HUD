# Ardania Info HUD 2.0

<p align="center">
  <img src="https://cdn.modrinth.com/data/cached_images/cd174cfa981a0ef994671f1cf9344b4a198a0d23.png" alt="Ardania Info HUD Logo" width="128" height="128" />
</p>

<p align="center">
  <strong>A clean, lightweight client-side HUD overlay for the German Minecraft server Ardania.de.</strong><br>
  Built for <strong>Fabric</strong> supporting Minecraft <strong>1.21.X (1.21 – 1.21.11)</strong>, and <strong>26.2 / 26.3</strong> via one shared 26.x JAR.
</p>

---

## 📌 Overview

**Ardania Info HUD** is a client-side Fabric mod designed for players on the German multiplayer server **[Ardania.de](https://ardania.de)**. It automatically tracks and displays your in-game currency, reward points, playtime, and job statistics in a clean, unobtrusive Hypixel-style scoreboard box on your screen.

Starting with **Version 2.0**, you have complete freedom to place the HUD anywhere on your screen via an interactive in-game drag & drop editor (with mouse and arrow keys), backed by intelligent **AFK protection** (`/afk` + 10-block distance check), hardened **Fallback server `[F]` resilience**, full **Moderator command compatibility**, ergonomic 6-anchor presets adhering to **DIN EN ISO 9241**, and sleek **subtle job progress bars**.

---

## ✨ Key Features (Version 2.0)

* 🖱️ **Visual In-Game HUD Editor (Free Mouse Drag & Drop in all versions)**:
  * Move the HUD freely with mouse drag & drop or keyboard arrow keys (`/ardaniahud edit` or from ModMenu).
  * **Dual-Layer Input Engine**: Flawless compatibility across GLFW (MC 1.21.x & 26.2) and SDL3 (MC 26.3).
  * **Magnetic Snap Guides (*Snap-to-Edge & Center*)**: Snaps seamlessly to screen borders and centerlines with clean Cyan guidelines.
  * **Arrow Key Nudging**: Shift by 1px (or 5px holding `Shift`) for pixel-perfect alignment.
  * **Auto-Dodge Toolbar**: Toolbar buttons automatically move to the top when dragging the HUD to the bottom.
* 📐 **Ergonomic 6 Anchor Presets (DIN EN ISO 9241-110)**:
  * 6 preset positions: `Middle Right` (classic Hypixel style), `Top Right`, `Bottom Right`, `Top Left`, `Middle Left`, and `Bottom Left`.
  * Cycle scale from 75% to 125% directly inside ModMenu.
  * Real-time viewport boundary clamping ensures the overlay never clips off-screen.
* 🛡️ **Intelligent AFK Protection (`/afk` + 10-Block Movement Threshold)**:
  * Running `/afk` halts all automated queries (`geld`, `ardapoints me`, `jobs stats`, `ontime`).
  * Your server-side AFK state is preserved until you move at least **10 blocks** away, re-type `/afk`, or an AFK-end chat notification is received.
* ⚖️ **Full Moderator & Staff Command Compatibility**:
  * Strict auto-sync suppression windows ensure moderator commands (`/whois`, `/seen`, `/check`, `/history`, separator lines, and command syntax hints) are never swallowed, and inspecting other players never overwrites your own HUD values.
* 🌐 **Rock-Solid Fallback Server Detection `[F]`**:
  * Prevents queries when waiting on the Fallback server `[F]` after server restarts.
  * Silently suppresses syntax errors (`Unknown or incomplete command...`), keeping your chat completely clean.
* ⛏️ **Multi-Job & Minimalist XP Progress Bar (Anti-Clutter)**:
  * Automatically detects active jobs via `/jobs stats`.
  * **Two Progress Modes**: Choose between clean text percentage badges (`Mobhunter 80% [24]`) or an elegant **2-pixel progress bar** under each job line.
  * Highlights maxed jobs (Level 30) in gold as `[MAX]`.
* 🏛️ **Smart Sub-Server Visibility Profiles**:
  * Set visibility rules per Ardania world:
    * **Creative Visibility**: `Hide` *(Default)* / `Tab Only` / `Show`.
    * **Event Visibility**: `Tab Only` *(Default)* / `Show` / `Hide`.
    * **Main & Mine**: `Show` *(Default)*.
  * Automatically hides on Creative to keep your view completely unobstructed.
* 💰 **Euronen (Currency / Bank & Live Profit)**:
  * Silently checks and tracks your real bank balance via **ArdaBank**.
  * Detects earnings in real-time from action-bar income (`+0.15$`, `+0.02 Euronen`).
  * **Session Profit & Hourly Rate**: Computes live session net profit and projected hourly wage (e.g. `+1,234.50 € (+3,400 €/h)`).
* 💠 **ArdaPoints & Vote Goal (`Voteziel`) Recognition**:
  * Displays your current ArdaPoints balance in real-time.
  * Captures daily rewards, monthly claim rewards, vote reward credits, and **Vote Goal (`Voteziel`)** bonuses (`+25 ArdaPoints`).
* ⏳ **Multi-Server Playtime Tracking (Ontime) & Session Timer**:
  * Tracks playtime across all server worlds (`Hauptserver`, `Creative`, `Eventserver`, `Mine`).
  * Customizable in ModMenu: Combine (sum) or display worlds separately.
  * Optional session timer (`Xh Ym Zs`).
* 🔢 **Compact Numbers & Background Opacity**:
  * **Compact Numbers**: Format large amounts with K/M abbreviations (`30.2k €`, `1.5M €`).
  * **Opacity Slider**: Adjust background transparency from 0% (fully transparent) to 100% (solid).

---

## ⚙️ In-Game Configuration (ModMenu)

Access the graphical settings screen across 3 tabs via **ModMenu** (or edit `.minecraft/config/ardania_hud.json` directly):

* **Display & Layout**:
  * Move HUD freely ...: Open the visual drag & drop editor.
  * Position: 6 preset anchors or `Custom Position`.
  * HUD Scale: 75%, 90%, 100%, 110%, 125%.
  * Job XP: `Percent` / `Bar` / `Off`.
  * Background Opacity: 0%, 25%, 50%, 75%, 100%.
  * Compact Numbers: `ON` / `OFF`.
  * Row Toggles: Euronen, ArdaPoints, Jobs, Session Profit.
* **Playtime & Worlds**:
  * Playtime Display & Combine Playtime (`ON`/`OFF`).
  * Session Timer (`ON`/`OFF`).
  * Creative, Event, and Mine world visibility modes (`Hide`, `Tab Only`, `Show`).
  * World Selection: `Hauptserver`, `Creative`, `Eventserver`, `Mine`.
* **Sync & Slide**:
  * Auto-Hide (Slide) & Display Duration (3s – 15s).
  * Show on Tab (`ON`/`OFF`).
  * Cooldowns for Money, Points, and Playtime + Sync on Join.

---

## 🎮 Controls

* **`J`** *(Default)*: Instantly forces a silent full sync with the server (`/geld`, `/ontime`, `/ardapoints me`, `/jobs stats`).
* **Toggle HUD** *(Unbound by default)*: Press to toggle HUD visibility.
* **Commands**:
  * `/ardaniahud edit`: Open the visual drag & drop editor.
  * `/ardaniahud config`: Open settings screen.
  * `/ardaniahud debug`: View status in chat.
  * `/ardaniahud debug copy`: Copy report to clipboard.

---

## 📦 Requirements & Installation (2.0.1)

1. Install **[Fabric Loader](https://fabricmc.net/use/)** for your Minecraft version.
2. Download **[Fabric API](https://modrinth.com/mod/fabric-api)** and place it into your `mods` folder.
3. *(Recommended)* Download **[ModMenu](https://modrinth.com/mod/modmenu)** for in-game configuration (or use `/ardaniahud config` in-game).
4. Download the appropriate JAR file for your version:
   * **Minecraft 1.21.8 – 1.21.11**: `ardania-info-hud-mc1.21.x-2.0.1.jar`
   * **Minecraft 26.2 and 26.3**: `ardania-info-hud-mc26.x-2.0.1.jar`
