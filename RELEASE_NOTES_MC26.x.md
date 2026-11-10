# Ardania Info HUD – v2.0.0 für Minecraft 26.x

**Release-Train:** 26.x · **Minecraft:** 26.2 – 26.3 · **Loader:** Fabric · **Java:** 25+
**Datei:** `ardania-info-hud-mc26.x-2.0.0.jar`

> Dieses Release wird unabhängig vom 1.21.x-Release veröffentlicht.
> Der 1.21.x-Build hat eigene Hinweise: [`RELEASE_NOTES_MC1.21.x.md`](https://github.com/Smb04/Ardania-Info-HUD/blob/master/RELEASE_NOTES_MC1.21.x.md).

Version **2.0.0** ist der bisher größte Meilenstein für das **Ardania Info HUD**: freies Verschieben per Maus und Pfeiltasten, sechs feste Positionen, AFK-Schutz, Fallback-Resilienz, volle Moderator-Kompatibilität, ein dezenter Job-XP-Balken und unterserver-bezogene Sichtbarkeitsprofile.

---

## 🇩🇪 Was ist neu in 2.0.0 (26.x)?

### 🖱️ Visueller HUD-Editor

* HUD frei per **Drag & Drop** oder mit den **Pfeiltasten** verschieben (`/ardaniahud edit` oder ModMenu → *HUD frei verschieben ...*).
* **Dual-Layer Input Engine**: GLFW unter 26.2, SDL3 unter 26.3.
* **Magnetisches Einrasten**: Ränder und Bildschirmmitten rasten mit dezenten Cyan-Hilfslinien ein.
* **Feinjustierung**: 1 px pro Tastendruck, 5 px mit gehaltener `Shift`-Taste.
* **Auto-Dodge-Toolbar**: Die Editor-Buttons weichen automatisch nach oben aus, wenn das HUD unten liegt.

### 📐 Sechs feste Positionen & Skalierung

* `Rechts Mitte` (Hypixel-Stil), `Rechts Oben`, `Rechts Unten`, `Links Mitte`, `Links Oben`, `Links Unten`.
* Skalierung von 75 % bis 125 % mit dynamischem Viewport-Clamping.
* *Standard-Preset* stellt die festen Positionen jederzeit wieder her.

### 🛡️ Intelligenter AFK-Schutz

* `/afk` pausiert sofort alle automatisierten Abfragen (`geld`, `ardapoints me`, `jobs stats`, `ontime`).
* Der serverseitige AFK-Status bleibt unberührt, bis du dich mindestens **10 Blöcke** bewegst, erneut `/afk` eingibst oder eine Chat-Meldung erscheint.

### ⚖️ Moderator- & Team-Kompatibilität

* Chat-Antworten werden nur im kurzen Zeitfenster nach einer echten Auto-Sync-Abfrage stummgeschaltet.
* Trennlinien, `Name:`-Felder und Statusboxen aus `/whois`, `/seen`, `/check`, `/history`, `/baninfo` usw. bleiben sichtbar.
* Abfragen fremder Spieler überschreiben niemals die eigenen HUD-Werte.
* Während der Chat geöffnet ist oder direkt nach eigenen Befehlen pausiert der Hintergrund-Sync.

### 🌐 Fallback-Server-Erkennung

* Auf dem Fallback-Server werden nach Serverneustarts keine Hintergrundabfragen ausgeführt.
* Unvollständige Servermeldungen (`Unknown or incomplete command...`) werden lautlos abgefangen.

### 📊 Job-XP-Fortschritt

* Dezenter 2-Pixel-Fortschrittsbalken unter jedem aktiven Job.
* Grün für laufenden Fortschritt, Gold für Maximalstufe 30 (`[MAX]`).
* Umschaltbar: `Prozent` → `Balken` → `Aus`.

### 🏛️ Unterserver-Sichtbarkeitsprofile

* Creative und Event-Games lassen sich automatisch ausblenden (`Ausblenden`, `Bei Tab`, `Anzeigen`).
* Nahtlose Wiederanzeige auf Hauptserver und Mine.

### 💰 Live-Gewinn, Voteziel & Spielzeit

* Session-Gewinn inklusive aktuellem Stundensatz (`+1.234,50 € (+3.400 €/h)`).
* Automatische Erfassung von ArdaPoints-Votezielen (`+25 ArdaPoints`).
* BungeeCord-kompatible `/ontime`-Erfassung beim Serverbeitritt über alle Welten.
* **Spielzeit-Fix:** Fremde `/ontime <Spieler>`-Antworten werden nicht mehr dem eigenen HUD zugeordnet. Komplette `/ontime`-Blöcke ersetzen die gespeicherten Werte, unbekannte Welten fließen nicht in die Summe ein und veraltete Cache-Einträge werden entfernt.

---

## 🇬🇧 What's New in 2.0.0 (26.x)?

### 🖱️ Visual HUD Editor

* Move the HUD freely with **mouse drag & drop** or the **arrow keys** (`/ardaniahud edit` or ModMenu → *Move HUD freely ...*).
* **Dual-Layer Input Engine**: GLFW on 26.2, SDL3 on 26.3.
* **Magnetic snap guides** for screen edges and centerlines.
* **Fine adjustment**: 1 px per key press, 5 px while holding `Shift`.
* **Auto-dodge toolbar** moves the buttons to the top when the HUD sits at the bottom.

### 📐 Six Anchor Presets & Scaling

* `Middle Right` (Hypixel style), `Top Right`, `Bottom Right`, `Middle Left`, `Top Left`, `Bottom Left`.
* 75 %–125 % scaling with dynamic viewport clamping.
* *Reset to preset* restores the fixed anchors at any time.

### 🛡️ Intelligent AFK Protection

* `/afk` immediately pauses all automated queries (`geld`, `ardapoints me`, `jobs stats`, `ontime`).
* Your server-side AFK state is preserved until you move at least **10 blocks**, retype `/afk`, or receive a chat notification.

### ⚖️ Moderator & Staff Compatibility

* Chat responses are suppressed only within the short window after a real auto-sync query.
* Separator lines, `Name:` fields and status boxes from `/whois`, `/seen`, `/check`, `/history`, `/baninfo` etc. remain visible.
* Querying other players never overwrites your own HUD values.
* Background sync pauses while chat is open or right after manual commands.

### 🌐 Fallback Server Detection

* No background queries on the Fallback server after restarts.
* Incomplete server messages (`Unknown or incomplete command...`) are caught silently.

### 📊 Job XP Progress

* Subtle 2-pixel progress bar below each active job.
* Green for active progress, gold for max level 30 (`[MAX]`).
* Switchable: `Percent` → `Bar` → `Off`.

### 🏛️ Sub-Server Visibility Profiles

* Automatically hide the HUD on Creative and Event games (`Hide`, `Tab Only`, `Show`).
* Seamless return on the Main server and in the Mine world.

### 💰 Live Profit, Vote Goal & Playtime

* Session profit including the current hourly rate (`+1,234.50 € (+3,400 €/h)`).
* Automatic ArdaPoints vote-goal tracking (`+25 ArdaPoints`).
* BungeeCord-compatible `/ontime` collection on join across all worlds.
* **Playtime fix:** Foreign `/ontime <player>` responses are no longer assigned to your own HUD. Full `/ontime` blocks replace the stored values, unknown worlds are excluded from the total, and stale cache entries are pruned.

---

## 📦 Installation & Kompatibilität

| Anforderung | Wert |
| --- | --- |
| Minecraft | **26.2 – 26.3** |
| Mod-Loader | **Fabric Loader ≥ 0.19.5** |
| Java | **Java 25 oder neuer** |
| Pflicht | **Fabric API** |
| Empfohlen | **ModMenu** |
| Server | ausschließlich **Ardania.de** |

1. Fabric Loader für **26.2** oder **26.3** installieren.
2. Fabric API in den `mods`-Ordner legen.
3. *(Empfohlen)* ModMenu installieren.
4. `ardania-info-hud-mc26.x-2.0.0.jar` nach `.minecraft/mods` kopieren.

**Enthaltene Datei:** `ardania-info-hud-mc26.x-2.0.0.jar` *(Java 25+)*

---

## ✅ Release-Metadaten

* **Version:** `2.0.0`
* **Release-Train:** `mc26.x`
* **Minecraft:** `26.2`, `26.3`
* **Loader:** Fabric
* **Java:** 25+
* **Build:** separat gebaut und getestet, 80 Unit-Tests grün.
