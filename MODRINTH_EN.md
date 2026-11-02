# Ardania Info HUD 1.7

<center>
<img src="https://cdn.modrinth.com/data/cached_images/cd174cfa981a0ef994671f1cf9344b4a198a0d23.png" alt="Ardania Info HUD Logo" width="128" height="128" />
</center>

A compact, sleek client-side info overlay for the Minecraft server **Ardania.de**.  
Compatible with **Fabric** for Minecraft **1.21.X (1.21 – 1.21.11)**, **26.2**, and **26.3**.

---

## ✨ Features (Version 1.7)

* 💰 **Euronen (Currency Display)**:
  * Automatically fetches your real account balance directly via `[ArdaBank]`.
  * Instantly reacts to job earnings shown in the action bar (`+0.15$`, `+0.02 Euronen`) and updates your balance on the fly.
  * Tracks direct bank transaction notices from ArdaBank (transfers, purchases, interest) without delay.

* 💠 **ArdaPoints & Vote Goal (`Voteziel`) Recognition**:
  * Displays your current ArdaPoints in real-time.
  * Automatically detects daily rewards, monthly claim bonuses, server vote credits, and **Vote Goal (`Voteziel`)** rewards (`+25 ArdaPoints` + automatic background verification).

* ⏳ **Multi-Server Playtime Tracking (Ontime) & Session Timer**:
  * Tracks your playtime across all Ardania worlds (`Hauptserver`, `Creative`, `Eventserver`, `Mine` / Nether).
  * **Configurable in ModMenu**: Choose which worlds to include and whether to **combine (sum)** them into one total or list each selected world **individually**.
  * **Optional Session Timer** *(off by default)*: Displays your current session duration (`Xh Ym Zs`) since joining the server.

* ⛏️ **Multi-Job, Right-Aligned Job Levels & MAX Rank**:
  * Automatically detects all of your active professions via `/jobs stats` (including progress bars).
  * Displays job titles on the left and aligns all level badges (`[24]`, `[12]`, `[MAX]`) **cleanly flush-right along the box edge**.
  * Highlights max-level jobs (Level 30) as `[MAX]` in gold (`Mobhunter [MAX]`).
  * Automatically refreshes jobs in the background on `[ArdaniaJobs]` level-up messages.

* 📍 **Left & Right HUD Placement**:
  * Position the HUD anywhere: `Middle Right (Hypixel)`, `Top Right`, `Bottom Right`, or `Middle Left`, `Top Left`, `Bottom Left`.
  * Seamless smooth slide animations matched to the screen edge.

* 🌐 **Tablist & Command-Tree Server Detection + Sub-Server Guard**:
  * Reads server abbreviations from the tablist (`[H]` for Main, `[C]` for Creative, `[M]` for Mine, `[E]` for Event, `[F]` for Fallback) alongside the server's Brigadier command tree (`ClientPacketListener.getCommands()`), cleanly distinguishing server tags from player guild tags (`[Bier]`, `[ARDA]`).
  * Verifies whether commands exist in the server's command tree before dispatching them, preventing unsupported queries on `Fallback`, `Mine`, and `Creative`.

* 💾 **Local State Cache (Sub-Server Fallback)**:
  * Automatically saves your last known balances, playtime, and jobs upon leaving the server or receiving updates (`.minecraft/config/ardania_hud_cache.json`).
  * When switching to sub-servers where certain commands are unavailable (such as `Fallback`, `Mine` / Nether, or `Creative`), the HUD seamlessly falls back to the cached values for those sections instead of showing `?`.

* 🌍 **Multi-Language Support (DE, EN, ES, FR)**:
  * Full localization for **German**, **English**, **Spanish**, and **French** – automatically following your Minecraft client language or selectable directly in the ModMenu settings screen.

* 🎬 **Smooth Slide Auto-Hide & Tab Integration**:
  * Optional auto-hide mode with smooth slide-in/slide-out animations when stats update (excluding playtime) or when holding/tapping `Tab`.
  * Configurable display duration (`3s`, `5s`, `8s`, `10s`, `15s`) in ModMenu.

* 🔕 **Intelligent Silent Sync & Chat Filtering**:
  * All automated background queries (`/geld`, `/ardapoints me`, `/jobs stats`, `/ontime`) are **completely hidden from the chat**.
  * Sub-server and Fallback error messages (including *"Du kannst diesen Befehl in dieser Welt nicht benutzen"* as well as multi-line Brigadier errors like *"Unknown or incomplete command. See below for error"* / `geld<--[HERE]`) are cleanly suppressed across all chat channels (`ALLOW_GAME` and `ALLOW_CHAT`).
  * **Typing commands manually into chat?** Responses will be displayed normally just like vanilla!

* 🛡️ **Anti-Flag & Rate-Limiting**:
  * Server-friendly: Commands are never dispatched in the same server tick, but spaced out with safe delays.
  * Smart farming debounce prevents spam flags when mining ores or harvesting wheat in quick succession.

* 🎨 **Compact Hypixel-Style Scoreboard Design**:
  * Semi-transparent, darkened background box for optimal readability in any biome (desert, snow, caves).
  * Automatically hides when pressing **F1**.
  * Only active while connected to `ardania.de`.

---

## ⚙️ Configuration & ModMenu

The mod offers a complete in-game settings screen across 3 tabs via **ModMenu** (or directly in `.minecraft/config/ardania_hud.json`).

### Config Options:
* **Enable HUD**: Toggle the entire overlay on or off.
* **Position (Right & Left)**:
  * `Middle Right (Hypixel)` *(Default)*, `Top Right`, `Bottom Right`
  * `Middle Left`, `Top Left`, `Bottom Left`
* **Modular Row Visibility**:
  * Show Euronen (`true`/`false`)
  * Show ArdaPoints (`true`/`false`)
  * Show Playtime (`true`/`false`)
  * Show Session Timer (`false` *(Default)* / `true`)
  * Show Jobs (`true`/`false`)
* **Playtime & Worlds**:
  * **Combine Playtime**: Sum selected worlds into one line (`true` *(Default)*) or list each selected world separately (`false`).
  * **World Selection**: Individually toggle `Hauptserver`, `Creative`, `Eventserver`, and `Mine`.
* **Slide Auto-Hide & Tab**:
  * **Auto-Hide (Slide)**: Automatically slide the HUD off-screen when idle (`true`/`false`).
  * **Display Duration**: 3s / 5s *(Default)* / 8s / 10s / 15s.
  * **Show on Tab**: Slide the HUD in when holding or tapping `Tab` (`true`/`false`).
* **Sync Intervals**:
  * **Money Cooldown**: 15s / 30s *(Default)* / 60s / 120s
  * **Points Interval**: 30s / 60s *(Default)* / 120s / 300s
  * **Playtime Interval**: 15m / 30m / 60m *(Default)* / 120m
  * **Sync on Join**: Trigger an automatic balance & stats check upon joining the server.

---

## 🎮 Controls

* **Key `J`** *(Default)*: Instantly and silently refreshes all values from the server (`/geld`, `/ardapoints me`, `/jobs stats`, `/ontime`).
* **Toggle HUD** *(Optional)*: Unbound by default; allows quickly sliding the HUD in or out with a single key press.
* **Custom Keybinds**: Both keys can be rebound anytime in the vanilla menu under **Options → Controls → Key Binds** in the *Ardania Info HUD* category.

---

## 📥 Installation (1.7)

1. Install **[Fabric Loader](https://fabricmc.net/use/)** for your Minecraft version (**1.21.X**, **26.2**, or **26.3**).
2. Place **[Fabric API](https://modrinth.com/mod/fabric-api)** in your `mods` folder.
3. *(Recommended)* Install **[ModMenu](https://modrinth.com/mod/modmenu)** for in-game configuration.
4. Download the corresponding mod `.jar` file for version **1.7** and place it into `.minecraft/mods`:
   * **Minecraft 1.21.X (1.21 – 1.21.11)**: `ardania-info-hud-mc1.21.x-1.7.0.jar`
   * **Minecraft 26.3**: `ardania-info-hud-mc26.3-1.7.0.jar`
   * **Minecraft 26.2**: `ardania-info-hud-mc26.2-1.7.0.jar`

---

## 📄 License

This project is licensed under the [CC0 1.0 Universal License](LICENSE) – you are free to use, modify, and distribute it.
