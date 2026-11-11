# Ardania Info HUD – v2.0.0 für Minecraft 1.21.x

**Release-Train:** 1.21.x · **Minecraft:** 1.21.8 – 1.21.11 · **Loader:** Fabric ≥ 0.16.13 · **Java:** 21+
**Datei:** `ardania-info-hud-mc1.21.x-2.0.0.jar`

> Dieses Release wird unabhängig vom 26.x-Release veröffentlicht.
> Der 26.x-Build hat eigene Hinweise: [`RELEASE_NOTES_MC26.x.md`](https://github.com/Smb04/Ardania-Info-HUD/blob/master/RELEASE_NOTES_MC26.x.md).

Version **2.0.0** bringt den visuellen HUD-Editor, sechs feste Positionen, AFK-Schutz, Fallback-Resilienz, volle Moderator-Kompatibilität, den Job-XP-Balken und unterserver-bezogene Sichtbarkeitsprofile auf **1.21.8 bis 1.21.11** (ältere 1.21-Versionen vor 1.21.8 werden nicht mehr unterstützt) – inklusive nativer 1.21.8-Rendering-Pipeline (`HudElement` + `Matrix3x2fStack`) sowie Editor-Fix für **1.21.9 bis 1.21.11**.

---

## 🇩🇪 Wichtig in 2.0.0 (1.21.x)

### 🖱️ Editor-Fix für 1.21.9 – 1.21.11

Ab Minecraft 1.21.9 liefert vanilla bei geöffnetem Screen keinen zuverlässigen Mausstatus mehr, und die alten `mouseClicked`-/`keyPressed`-Signaturen werden nicht mehr aufgerufen. Der Editor fragt die Eingabe deshalb jetzt direkt über **GLFW** ab:

* **Drag & Drop funktioniert wieder** auf 1.21.9, 1.21.10 und 1.21.11.
* **Pfeiltasten-Feinjustierung funktioniert wieder** (1 px, mit `Shift` 5 px).
* **Kein Blur mehr über dem HUD**: Der Menü-Hintergrund wird zuerst gezeichnet, die HUD-Vorschau liegt sauber darüber.
* **Speichern erst am Ende**: Die Position wird beim Loslassen der Maus bzw. beim Loslassen der Pfeiltasten geschrieben, nicht bei jedem Frame.
* Buttons und `ESC` funktionieren weiterhin über die normalen Vanilla-Widgets.

### ⏳ Spielzeit-Fix (`/ontime`)

* Fremde `/ontime <Spieler>`-Antworten werden nicht mehr dem eigenen HUD zugeordnet (Erkennung über die `Name:`-Zeile).
* Komplette `/ontime`-Blöcke **ersetzen** die gespeicherten Werte; einzelne Zeilen werden nur ergänzt.
* Unbekannte Welten fließen nicht in die Gesamtspielzeit ein, veraltete Cache-Einträge werden entfernt.
* Das Zeitfenster für fremde Spielerabfragen wurde auf 15 Sekunden erweitert, damit auch langsame Antworten sicher erkannt werden.

### 🖱️ Visueller HUD-Editor

* HUD frei per **Drag & Drop** oder mit den **Pfeiltasten** verschieben (`/ardaniahud edit` oder ModMenu → *HUD frei verschieben ...*).
* **Magnetisches Einrasten**: Ränder und Bildschirmmitten rasten mit dezenten Cyan-Hilfslinien ein.
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

---

## 🇬🇧 Important in 2.0.0 (1.21.x)

### 🖱️ Editor Fix for 1.21.9 – 1.21.11

Starting with Minecraft 1.21.9, vanilla no longer exposes reliable mouse state while a screen is open, and the old `mouseClicked`/`keyPressed` signatures are no longer invoked. The editor now reads input directly through **GLFW**:

* **Drag & drop works again** on 1.21.9, 1.21.10 and 1.21.11.
* **Arrow-key nudging works again** (1 px, 5 px with `Shift`).
* **No more blur over the HUD**: the menu background is drawn first and the HUD preview sits cleanly on top.
* **Saving happens at the end** of a drag or key hold instead of every frame.
* Buttons and `ESC` keep using the regular vanilla widgets.

### ⏳ Playtime Fix (`/ontime`)

* Foreign `/ontime <player>` responses are no longer assigned to your own HUD (detected via the `Name:` line).
* Full `/ontime` blocks now **replace** the stored values; single lines are only merged.
* Unknown worlds are excluded from the total and stale cache entries are pruned.
* The foreign-query window was extended to 15 seconds so slower responses are still classified correctly.

### 🖱️ Visual HUD Editor

* Move the HUD freely with **mouse drag & drop** or the **arrow keys** (`/ardaniahud edit` or ModMenu → *Move HUD freely ...*).
* **Magnetic snap guides** for screen edges and centerlines.
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

---

## 📦 Installation & Kompatibilität

| Anforderung | Wert |
| --- | --- |
| Minecraft | **1.21.8 – 1.21.11** (eine universelle JAR) |
| Mod-Loader | **Fabric Loader ≥ 0.16.13** |
| Java | **Java 21 oder neuer** |
| Pflicht | **Fabric API** (passend zur Minecraft-Version) |
| Empfohlen | **ModMenu** |
| Server | ausschließlich **Ardania.de** |

1. Fabric Loader für deine 1.21-Version (≥ 0.16.13) installieren.
2. Fabric API in den `mods`-Ordner legen.
3. *(Empfohlen)* ModMenu installieren.
4. `ardania-info-hud-mc1.21.x-2.0.0.jar` nach `.minecraft/mods` kopieren.

**Enthaltene Datei:** `ardania-info-hud-mc1.21.x-2.0.0.jar` *(Java 21+)*

> **Hinweis:** Die JAR wird nativ gegen **1.21.8** (`HudElement`, `Matrix3x2fStack`, Strata-Trennung) gebaut und ist für `>=1.21.8 <=1.21.11` freigegeben. Der Editor-Input nutzt ausschließlich GLFW und verhält sich damit auf 1.21.8 bis 1.21.11 identisch.

---

## ✅ Release-Metadaten

* **Version:** `2.0.0`
* **Release-Train:** `mc1.21.x`
* **Minecraft:** `1.21.8`, `1.21.9`, `1.21.10`, `1.21.11`
* **Loader:** Fabric
* **Java:** 21+
* **Build:** separat gebaut und getestet, 80 Unit-Tests grün.
