# Ardania Info HUD

<center>
<img src="https://cdn.modrinth.com/data/cached_images/cd174cfa981a0ef994671f1cf9344b4a198a0d23.png" alt="Ardania Info HUD Logo" width="128" height="128" />
</center>

A compact, sleek client-side info overlay for the Minecraft server **Ardania.de**.  
Compatible with **Fabric** for Minecraft **1.21.X (1.21 – 1.21.11)**, **26.2**, and **26.3**.

---

## ✨ Features

* 💰 **Euronen (Currency Display)**:
  * Automatically fetches your real account balance directly via `[ArdaBank]`.
  * Instantly reacts to job earnings shown in the action bar (`+0.15$`, `+0.02 Euronen`) and updates your balance on the fly.
  * Tracks direct bank transaction notices from ArdaBank (transfers, purchases, interest) without delay.

* 💠 **ArdaPoints**:
  * Displays your current ArdaPoints in real-time.
  * Automatically detects daily rewards, monthly claim bonuses, and server vote credits (`+25 ArdaPoints`).

* ⏳ **Multi-Server Playtime Tracking (Ontime)**:
  * Aggregates your playtime across all Ardania worlds (`Main Server`, `Mine` / Nether, `Creative`, `Event Server`) into one accurate total playtime figure.
  * Keeps track of active minutes locally and syncs with the server in the background.

* ⛏️ **Multi-Job, Job Levels & MAX Rank**:
  * Automatically detects all of your active professions via `/jobs stats` (including progress bars).
  * Cleanly displays job titles and current levels (e.g., `Mobhunter [24]`, `Farmer [12]`, `Miner [21]`).
  * Highlights max-level jobs (Level 30) as `[MAX]` in gold (`Mobhunter [MAX]`).
  * Automatically refreshes jobs in the background on `[ArdaniaJobs]` level-up messages.

* 💾 **Local State Cache (Sub-Server Fallback)**:
  * Automatically saves your last known balances, playtime, and jobs upon leaving the server or receiving updates (`.minecraft/config/ardania_hud_cache.json`).
  * When switching to sub-servers where certain commands are unavailable (such as `/jobs stats` or `/ardapoints me` in the Nether/Mine), the HUD seamlessly falls back to the cached values for those sections instead of showing `?`.

* 🌍 **Multi-Language Support (DE, EN, ES, FR)**:
  * Full localization for **German**, **English**, **Spanish**, and **French** – automatically following your Minecraft client language or selectable directly in the ModMenu settings screen.

* 🎬 **Smooth Slide Auto-Hide & Tab Integration**:
  * Optional auto-hide mode with smooth slide-in/slide-out animations when stats update (excluding playtime) or when holding/tapping `Tab`.
  * Configurable display duration (`3s`, `5s`, `8s`, `10s`, `15s`) in ModMenu.

* 🔕 **Intelligent Silent Sync & Chat Filtering**:
  * All automated background queries (`/geld`, `/ardapoints me`, `/jobs stats`, `/ontime`) are **completely hidden from the chat**.
  * World-switch error messages (such as commands not existing in the Nether sub-server) are suppressed automatically.
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

The mod offers a complete in-game settings screen via **ModMenu** (or directly in `.minecraft/config/ardania_hud.json`).

### Config Options:
* **Enable HUD**: Toggle the entire overlay on or off.
* **Position**:
  * `Middle Right (Hypixel)` *(Default - centered on the right edge)*
  * `Top Right`
  * `Bottom Right`
* **Modular Row Visibility**:
  * Show Euronen (`true`/`false`)
  * Show ArdaPoints (`true`/`false`)
  * Show Playtime (`true`/`false`)
  * Show Jobs (`true`/`false`)
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

## 📄 License

This project is licensed under the [CC0 1.0 Universal License](LICENSE) – you are free to use, modify, and distribute it.
