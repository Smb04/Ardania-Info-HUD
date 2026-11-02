# Ardania Info HUD 1.7

<p align="center">
  <img src="https://cdn.modrinth.com/data/cached_images/cd174cfa981a0ef994671f1cf9344b4a198a0d23.png" alt="Ardania Info HUD Logo" width="128" height="128" />
</p>

<p align="center">
  <strong>A clean, lightweight client-side HUD overlay for the Minecraft server Ardania.de.</strong><br>
  Built for <strong>Fabric</strong> supporting Minecraft <strong>1.21.X (1.21 – 1.21.11)</strong>, <strong>26.2</strong>, and <strong>26.3</strong>.
</p>

---

## 📌 Overview

**Ardania Info HUD** is a client-side Fabric mod designed for players on the German multiplayer server **[Ardania.de](https://ardania.de)**. It automatically tracks and displays your in-game currency, reward points, playtime, and job statistics in a clean, unobtrusive Hypixel-style scoreboard box on your screen.

No more manually typing `/geld`, `/ardapoints me`, or `/jobs stats` every few minutes!

---

## ✨ Key Features (Version 1.7)

* 💰 **Euronen (Currency / Bank)**:
  * Silently checks and tracks your real bank balance via **ArdaBank**.
  * Detects earnings in real-time from action-bar income (`+0.15$`, `+0.02 Euronen`).
  * Seamlessly processes direct transaction notifications (payments, transfers, interest).
* 💠 **ArdaPoints & Vote Goal (`Voteziel`) Recognition**:
  * Displays your current ArdaPoints balance in real-time.
  * Captures daily rewards, monthly claim rewards, vote reward credits, and **Vote Goal (`Voteziel`)** bonuses (`+25 ArdaPoints` + silent background verification).
* ⏳ **Multi-Server Playtime Tracking (Ontime) & Session Timer**:
  * Tracks playtime across all server worlds (`Hauptserver`, `Creative`, `Eventserver`, `Mine` / Nether).
  * **Customizable in ModMenu**: Select which worlds to include and choose whether to **combine (sum)** them or display each world **separately**.
  * **Optional Session Timer** *(off by default)*: Tracks your active session time (`Xh Ym Zs`) since connecting.
* ⛏️ **Multi-Job, Right-Aligned Levels & MAX Rank Detection**:
  * Automatically detects all active jobs via `/jobs stats` (including progress bars).
  * Aligns all job level badges (`[24]`, `[12]`, `[MAX]`) **cleanly flush-right along the HUD box border**.
  * Renders Level 30 maxed jobs prominently in gold as `[MAX]` (e.g. `Mobhunter [MAX]`).
  * Automatically refreshes upon job level-ups or joining new jobs (`[ArdaniaJobs]`).
* 📍 **Left & Right HUD Placement**:
  * Choose between `Middle Right (Hypixel)`, `Top Right`, `Bottom Right`, `Middle Left`, `Top Left`, and `Bottom Left`.
  * Fluid smooth slide animations tailored to whichever screen border you choose.
* 🌐 **Tablist & Command-Tree Server Detection + Sub-Server Guard**:
  * Detects your sub-server type via tablist tags (`[H]` for Main, `[C]` for Creative, `[M]` for Mine, `[E]` for Event, `[F]` for Fallback) alongside the server's Brigadier command tree (`ClientPacketListener.getCommands()`), differentiating server tags from player guild tags (`[Bier]`, `[ARDA]`).
  * Verifies whether commands exist in the server's command tree before dispatching them, avoiding unsupported commands on `Fallback`, `Mine`, and `Creative`.
* 💾 **Local State Caching (Offline & Sub-Server Fallback)**:
  * Saves the last known balances, playtime, and jobs to `.minecraft/config/ardania_hud_cache.json` upon disconnect and stat updates.
  * If a command is disabled on a sub-server (such as `Fallback`, `Mine` / Nether, or `Creative`), the HUD smoothly falls back to the cached values instead of displaying `?`.
* 🌍 **Full Localization (DE / EN / ES / FR)**:
  * Native translations for **German (`de_de`)**, **English (`en_us`)**, **Spanish (`es_es`)**, and **French (`fr_fr`)**.
  * Automatically matches your Minecraft client language, or can be forced in the in-game settings screen.
* 🎬 **Smooth Slide Animation & Tab Integration**:
  * Optional auto-hide feature that slides out smoothly when idle and slides back in on stat changes.
  * Configurable auto-hide display timer (`3s`, `5s`, `8s`, `10s`, `15s`).
  * Hold or tap **Tab** (player list) to slide the HUD into view.
* 🔕 **Silent Background Sync & Chat Filtering**:
  * All background queries are completely hidden from the chat window.
  * Sub-server and Fallback error messages (including *"Du kannst diesen Befehl in dieser Welt nicht benutzen"* as well as multi-line Brigadier errors like *"Unknown or incomplete command. See below for error"* / `geld<--[HERE]`) are cleanly suppressed across both `ALLOW_GAME` and `ALLOW_CHAT` channels.
  * Manual player commands typed into chat remain visible as usual.
* 🛡️ **Anti-Spam & Rate Limiting**:
  * Background commands are staggered with safe delays to prevent server spam flags.
  * Debounced triggers protect against spam while mining ores or harvesting crops quickly.
* 🎨 **Clean & Configurable Design**:
  * Semi-transparent dark background for optimal contrast across biomes.
  * Automatically hides when pressing **F1**.
  * Only active while connected to `ardania.de` (or local testing).

---

## ⚙️ In-Game Configuration (ModMenu)

Install **ModMenu** to access the graphical settings screen in-game (or edit `.minecraft/config/ardania_hud.json` directly):

* **HUD Enabled**: Turn the overlay on or off.
* **Language**: `Auto (Client)` / `Deutsch` / `English` / `Español` / `Français`.
* **Position (Right & Left)**: `Middle Right (Hypixel)` *(Default)*, `Top Right`, `Bottom Right`, `Middle Left`, `Top Left`, `Bottom Left`.
* **Module Toggles**: Toggle Euronen, ArdaPoints, Playtime, Session Timer *(Default: OFF)*, and Jobs individually.
* **Playtime & Worlds**: Toggle **Combine Playtime** (`ON`/`OFF`) and individually include/exclude `Hauptserver`, `Creative`, `Eventserver`, and `Mine`.
* **Auto-Hide (Slide)**: Enable smooth slide-out when idle.
* **Display Duration**: 3s, 5s *(Default)*, 8s, 10s, 15s.
* **Show on Tab**: Show HUD while holding/tapping the Tab key.
* **Sync Cooldowns**:
  * Money Cooldown: 15s / 30s *(Default)* / 60s / 120s
  * Points Interval: 30s / 60s *(Default)* / 120s / 300s
  * Playtime Interval: 15m / 30m / 60m *(Default)* / 120m
  * Sync on Join: Query stats upon joining the server.

---

## 🎮 Controls

* **`J`** *(Default)*: Instantly and silently forces a full synchronization with the server (`/geld`, `/ardapoints me`, `/jobs stats`, `/ontime`).
* **Toggle HUD** *(Unbound by default)*: Press to slide the HUD in or out immediately.
* Both keybindings can be customized under **Options → Controls → Key Binds** in the *Ardania Info HUD* category.

---

## 📦 Requirements & Installation (1.7)

1. Install **[Fabric Loader](https://fabricmc.net/use/)** for your Minecraft version.
2. Place **[Fabric API](https://curseforge.com/minecraft/mc-mods/fabric-api)** in your `.minecraft/mods` folder (*Required!*).
3. *(Recommended)* Install **[ModMenu](https://curseforge.com/minecraft/mc-mods/modmenu)** for in-game configuration.
4. Download the corresponding mod `.jar` file for your Minecraft version and place it into `.minecraft/mods`:
   * **Minecraft 1.21.X (1.21 – 1.21.11)**: `ardania-info-hud-mc1.21.x-1.7.0.jar`
   * **Minecraft 26.3**: `ardania-info-hud-mc26.3-1.7.0.jar`
   * **Minecraft 26.2**: `ardania-info-hud-mc26.2-1.7.0.jar`

> ⚠️ **Note for LabyMod / CurseForge App users**: Ensure **Fabric API** is placed inside your profile's `mods` folder, as LabyMod's Fabric Loader addon does not include the Fabric API by default.

---

## 📄 License

This project is released under the **[CC0 1.0 Universal (Public Domain)](LICENSE)** license. You are free to use, modify, and redistribute it.
