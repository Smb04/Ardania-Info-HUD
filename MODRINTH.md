# Ardania Info HUD 1.7

**Ein kompaktes, elegantes Client-Side Informations-Overlay für den Minecraft-Server Ardania.de.**  
Kompatibel mit **Fabric** für **Minecraft 1.21.X (1.21 bis 1.21.11)**, **26.2** und **26.3**.

Nie wieder `/money`, `/ardapoints me` oder `/jobs stats` per Hand eingeben müssen! Das **Ardania Info HUD** zeigt dir alle wichtigen Server-Informationen in einem aufgeräumten, unaufdringlichen Scoreboard-Overlay an – ganz im Stil klassischer Minigame-Server, wahlweise am rechten oder linken Bildschirmrand!

---

### ✨ Features im Überblick (Version 1.7)

* 💰 **Euronen (Geldanzeige)**:
  * Ruft deinen tatsächlichen Kontostand automatisch und lautlos über die **ArdaBank** ab.
  * Reagiert auf Job-Vergütungen in der Action-Bar (`+0.15$`, `+0,02 Euronen`) und gleicht den Betrag verlässlich ab.
  * Erfasst direkte Buchungsmitteilungen (Überweisungen, Käufe, Zinsen) ohne Verzögerung.
* 💠 **ArdaPoints & Voteziel-Erkennung**:
  * Zeigt deine aktuellen Punkte in Echtzeit an.
  * Unterstützt automatische Erfassung von Daily-Rewards, monatlichen Belohnungen, Server-Votes sowie dem **Voteziel** (`+25 ArdaPoints`, `Voteziel erreicht` inkl. automatischem Hintergrund-Abgleich).
* ⏳ **Multi-Server Spielzeittracking (Ontime) & Session-Timer**:
  * Erfasst die Spielzeiten aller Welten (`Hauptserver`, `Creative`, `Eventserver`, `Mine` / Nether).
  * **Frei konfigurierbar im ModMenu**: Wähle aus, welche Welten berücksichtigt werden und ob diese **zusammengerechnet** oder **einzeln** angezeigt werden sollen.
  * **Optionaler Session-Timer** *(standardmäßig aus)*: Zeigt dir auf Wunsch deine aktuelle Sitzungsdauer (`Xh Ym Zs`) seit dem Serverbeitritt an.
* ⛏️ **Multi-Job, rechtsbündige Joblevel & MAX-Stufe**:
  * Erkennt automatisch alle deine aktiven Berufe aus `/jobs stats`.
  * Zeigt Berufsbezeichnungen links und Level (`[24]`, `[12]`, `[MAX]`) **sauber rechtsbündig am Boxrand untereinander ausgerichtet** an.
  * Berufe auf Maximalstufe (Level 30) werden als `[MAX]` in Gold hervorgehoben (`Mobhunter [MAX]`).
  * Aktualisiert sich automatisch im Hintergrund bei `[ArdaniaJobs]`-Aufstiegen.
* 📍 **Freie Positionswahl (Rechts & Links)**:
  * Wähle zwischen `Rechts Mitte`, `Oben Rechts`, `Unten Rechts` oder `Links Mitte`, `Oben Links`, `Unten Links`.
  * Sanfte Slide-Animation angepasst an die jeweilige Bildschirmseite.
* 🌐 **Server-Erkennung (Tabliste & Command-Tree) & Subserver-Schutz**:
  * Erkennt über Tablisten-Kürzel (`[H]` für Hauptserver, `[C]` für Creative, `[M]` für Mine, `[E]` für Event, `[F]` für Fallback) sowie den Brigadier-Befehlsbaum des Servers automatisch, auf welchem Server du bist, und unterscheidet Server-Kürzel von Gildentags (`[Bier]`, `[ARDA]`).
  * Prüft vor jedem Befehl im Befehlsbaum, ob dieser auf dem aktuellen Unterserver existiert, und pausiert nicht verfügbare Befehle auf `Fallback`, `Mine` und `Creative`.
* 💾 **Lokaler Zustands-Cache (Fallback auf Unterservern)**:
  * Speichert den letzten bekannten Stand beim Verlassen des Servers sowie bei Updates in einer Cache-Datei (`ardania_hud_cache.json`).
  * Sind auf bestimmten Welten (z. B. `Fallback`, `Mine` / Nether oder `Creative`) einzelne Befehle nicht verfügbar, zeigt das HUD für diese Werte nahtlos den zuletzt gespeicherten Stand an.
* 🌍 **Mehrsprachigkeit (DE, EN, ES, FR)**:
  * Vollständige Lokalisierung für **Deutsch**, **Englisch**, **Spanisch** und **Französisch** – automatisch über die Minecraft-Sprache oder direkt umschaltbar im ModMenu-Einstellungsbildschirm.
* 🎬 **Auto-Hide mit Slide-Animation & Tab-Integration**:
  * Optionales automatisches Ein- und Ausblenden mit sanfter Slide-Animation bei Status-Updates sowie beim Drücken/Halten von `Tab`.
  * Einstellbare Anzeigedauer (`3s`, `5s`, `8s`, `10s`, `15s`).
* 🔕 **Intelligente Silent-Synchronisation & Chat-Filter**:
  * Alle Hintergrund-Abfragen der Mod werden **vollständig aus dem Chat ausgeblendet** – dein Chatfenster bleibt komplett sauber!
  * Fehlermeldungen bei Weltenwechseln oder auf dem Fallback-Server (sowohl *"Du kannst diesen Befehl in dieser Welt nicht benutzen"* als auch Brigadier-Meldungen wie *"Unknown or incomplete command"* / `geld<--[HERE]`) werden automatisch abgefangen.
  * **Tippst du selbst einen Befehl im Chat ein**, siehst du die Antwort wie gewohnt ganz normal!
* 🛡️ **Anti-Flag & Rate-Limiting**:
  * Schonend für den Server: Befehle werden gestaffelt mit Pausen gesendet, niemals im selben Tick.
  * Farming-Debounce verhindert Spam-Flags beim schnellen Abbauen von Erzen oder Feldern.
* 🎨 **Kompaktes Hypixel-Design**:
  * Halbtransparenter Kasten für perfekte Lesbarkeit vor jedem Hintergrund.
  * Wird bei **F1** automatisch ausgeblendet.
  * Nur aktiv, wenn du tatsächlich auf `ardania.de` spielst.

---

### ⚙️ ModMenu & Ingame-Einstellungen

Über **ModMenu** (oder in der Datei `.minecraft/config/ardania_hud.json`) kannst du das Overlay über 3 übersichtliche Tabs frei nach deinen Wünschen anpassen:

* **Position (Rechts & Links)**:
  * `Rechts Mitte (Hypixel)` *(Standard)*, `Oben Rechts`, `Unten Rechts`
  * `Links Mitte`, `Oben Links`, `Unten Links`
* **Modulare Anzeigen**: Blende beliebige Zeilen ein oder aus (Euronen, ArdaPoints, Spielzeit, Session-Timer, Jobs).
* **Spielzeit & Welten**:
  * Spielzeit zusammenrechnen (`An`/`Aus`)
  * Einzelne Welten ein-/ausschalten (`Hauptserver`, `Creative`, `Eventserver`, `Mine`)
* **Auto-Ausblenden & Tab**:
  * Auto-Ausblenden (Slide) an-/ausschalten
  * Anzeigedauer (3s / 5s / 8s / 10s / 15s)
  * Bei Tab anzeigen an-/ausschalten
* **Synchronisierungszeiten**:
  * Geld-Cooldown (15s / 30s / 60s / 120s)
  * Punkte-Intervall (30s / 60s / 120s / 300s)
  * Spielzeit-Intervall (15m / 30m / 60m / 120m)
  * Sync beim Serverbeitritt an-/ausschalten

---

### 🎮 Steuerung

* **Taste `J`** *(Standard)*: Führt einen sofortigen, leisen Komplettabgleich aller Werte mit dem Server durch (`/geld`, `/ardapoints me`, `/jobs stats`, `/ontime`).
* **HUD ein-/ausblenden** *(Optional)*: Frei belegbare Taste zum direkten Ein- und Ausfahren des HUDs.
* **Tasten ändern**: Unter *Optionen → Steuerung → Tastenbelegung* in der Kategorie *Ardania Info HUD* frei belegbar.

---

### 📥 Installation & Kompatibilität (1.7)

1. Installiere den **Fabric Loader** für deine Minecraft-Version (**1.21.X**, **26.2** oder **26.3**).
2. Platziere die **Fabric API** in deinem `mods`-Ordner.
3. *(Empfohlen)* Installiere **ModMenu**, um auf das Einstellungsmenü zugreifen zu können.
4. Lade die passende Mod-Version herunter und lege sie in den `mods`-Ordner:
   * **Minecraft 1.21.X (1.21 bis 1.21.11)**: `ardania-info-hud-mc1.21.x-1.7.0.jar`
   * **Minecraft 26.3**: `ardania-info-hud-mc26.3-1.7.0.jar`
   * **Minecraft 26.2**: `ardania-info-hud-mc26.2-1.7.0.jar`
