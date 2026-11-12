# Ardania Info HUD 2.0

**Ein kompaktes, elegantes Client-Side Informations-Overlay für den Minecraft-Server Ardania.de.**  
Kompatibel mit **Fabric** für **Minecraft 1.21.X (1.21 bis 1.21.11)** sowie **26.2** und **26.3** (ein gemeinsames 26.x-JAR).

Nie wieder `/money`, `/ardapoints me` oder `/jobs stats` per Hand eingeben müssen! Das **Ardania Info HUD** zeigt dir alle wichtigen Server-Informationen in einem aufgeräumten, unaufdringlichen Scoreboard-Overlay an – ganz im Stil klassischer Minigame-Server, ergonomisch optimiert nach **DIN EN ISO 9241**, ab Version 2.0 **völlig frei per Maus und Tastatur auf dem Bildschirm verschiebbar**!

---

### ✨ Features im Überblick (Version 2.0)

* 🖱️ **Visueller In-Game HUD-Editor (Freies Verschieben mit der Maus in allen Versionen)**:
  * Platziere dein HUD ab sofort völlig frei auf dem Bildschirm per Maus (Drag & Drop) oder mit den Pfeiltasten (`/ardaniahud edit` oder über ModMenu).
  * **Dual-Layer Input Engine**: Garantiert reibungslose Erkennung in allen Versionen (GLFW & SDL3).
  * **Magnetisches Einrasten (*Snap-to-Edge & Center*)**: Ränder und Bildschirmmitten rasten mit feinen Cyan-Hilfslinien automatisch ein.
  * **Pfeiltasten-Feinjustierung**: Verschiebe das HUD pixelweise (mit `Shift` in 5px-Schritten).
  * **Auto-Dodge Toolbar**: Die Steuerungs-Buttons weichen automatisch nach oben aus, wenn das HUD im unteren Bildschirmbereich liegt.
* 📐 **Ergonomische 6 feste Positionen (DIN EN ISO 9241-110)**:
  * 6 feste Bildschirm-Positionen: `Rechts Oben`, `Rechts Mitte` (Hypixel-Stil), `Rechts Unten`, `Links Oben`, `Links Mitte`, `Links Unten`.
  * Direktes Umschalten von Position und Skalierung (75% bis 125%) im Konfigurationsmenü.
  * Automatisches Viewport-Clamping verhindert jegliches Herausrutschen des Overlays bei Auflösungs- oder GUI-Scale-Wechseln.
* 🛡️ **Intelligenter AFK-Schutz (`/afk` + 10-Block-Distanzprüfung)**:
  * Nach Eingabe von `/afk` pausiert das Info HUD sofort alle automatisierten Abfragen (`geld`, `ardapoints me`, `jobs stats`, `ontime`).
  * Dein serverseitiger AFK-Status bleibt unberührt, bis du dich um mindestens **10 Blöcke** bewegst, erneut `/afk` eingibst oder die Chat-Meldung erscheint.
* ⚖️ **Moderator- & Team-Befehls-Kompatibilität**:
  * Keine Beeinträchtigung von Moderations-Befehlen (`/whois`, `/seen`, `/check`, `/history` etc.): Chat-Antworten werden ausschließlich im engen Zeitfenster nach echten Auto-Sync-Abfragen gefiltert; eigene HUD-Werte werden bei Abfragen fremder Spieler nicht überschrieben.
* 🌐 **Robuste Fallback-Server-Erkennung `[F]`**:
  * Auf dem Fallback-Server nach Serverneustarts werden keine Befehle gesendet.
  * Syntaxfehler (`Unknown or incomplete command...`) werden lautlos abgefangen – der Chat bleibt vollkommen fehlerfrei.
* ⛏️ **Multi-Job & dezenter Mini-XP-Fortschrittsbalken (Anti-Clutter)**:
  * Erkennt automatisch alle Berufe aus `/jobs stats`.
  * **Zwei Fortschrittsmodi**: Wähle zwischen kompakter Prozentanzeige (`Mobhunter 80% [24]`) oder einem **subtilen 2-Pixel-Fortschrittsbalken** direkt unter dem Job.
  * Berufe auf Maximalstufe (Level 30) werden edel als `[MAX]` in Gold hervorgehoben.
* 🏛️ **Smarte Unterserver-Sichtbarkeitsprofile**:
  * Auf dem **Creative-Server** oder bei **Event-Games** wird das HUD auf Wunsch automatisch ausgeblendet (`Ausblenden`, `Bei Tab`, `Anzeigen`).
  * Nahtlose Wiederanzeige, sobald du auf den Hauptserver oder in die Mine zurückkehrst.
* 💰 **Euronen (Geldanzeige & Live-Gewinn)**:
  * Ruft deinen tatsächlichen Kontostand automatisch und lautlos über die **ArdaBank** ab.
  * Reagiert auf Job-Vergütungen in der Action-Bar (`+0.15$`, `+0,02 Euronen`) und gleicht den Betrag verlässlich ab.
  * **Session-Gewinn & Verdienst**: Erfasst live deinen Netto-Verdienst seit Serverbeitritt inklusive aktuellem Stundensatz (z. B. `+1.234,50 € (+3.400 €/h)`).
* 💠 **ArdaPoints & Voteziel-Erkennung**:
  * Zeigt deine aktuellen Punkte in Echtzeit an.
  * Unterstützt automatische Erfassung von Daily-Rewards, monatlichen Belohnungen, Server-Votes sowie dem **Voteziel** (`+25 ArdaPoints`).
* ⏳ **Multi-Server Spielzeittracking (Ontime) & Session-Timer**:
  * Erfasst die Spielzeiten aller Welten (`Hauptserver`, `Creative`, `Eventserver`, `Mine`) automatisch beim Serverbeitritt.
  * Frei konfigurierbar im ModMenu: Welten zusammenrechnen oder einzeln auflisten.
  * Optionaler Session-Timer (`Xh Ym Zs`).
* 🔢 **Kompakte Zahlen & einstellbare Deckkraft**:
  * **Kompakte Zahlen**: Optionale K/M-Formatierung (`30,2k €`, `1,5M €`).
  * **Deckkraft**: Hintergrund-Transparenz in 25%-Schritten von 0% (unsichtbar) bis 100% (voll deckend) frei einstellbar.

---

### ⚙️ ModMenu & Ingame-Einstellungen

Über **ModMenu** (oder in `.minecraft/config/ardania_hud.json`) kannst du das Overlay über 3 übersichtliche Tabs frei anpassen:

* **Tab 1: Anzeige & Layout**:
  * **HUD frei verschieben ...**: Öffnet den visuellen Drag & Drop Editor.
  * **Position**: 6 vordefinierte Anker oder `Frei platziert`.
  * **HUD-Skalierung**: 75%, 90%, 100%, 110%, 125%.
  * **Job-XP**: `Prozent` / `Balken` / `Aus`.
  * **Deckkraft**: 0%, 25%, 50%, 75%, 100%.
  * **Kompakte Zahlen**: `An` / `Aus`.
  * **Anzeigefelder**: Euronen, ArdaPoints, Jobs, Session-Gewinn.
* **Tab 2: Spielzeit & Welten**:
  * Spielzeit anzeigen (`An`/`Aus`) & Zusammenrechnen (`An`/`Aus`).
  * Session-Timer (`An`/`Aus`).
  * Creative-, Event- & Mine-Sichtbarkeit (`Ausblenden`, `Bei Tab`, `Anzeigen`).
  * Einzelne Welten ein-/ausschalten (`Hauptserver`, `Creative`, `Eventserver`, `Mine`).
* **Tab 3: Sync & Slide**:
  * Auto-Ausblenden (Slide) an-/ausschalten & Anzeigedauer (3s – 15s).
  * Bei Tab anzeigen an-/ausschalten.
  * Cooldowns und Sync-Intervalle.

---

### 🎮 Steuerung

* **Taste `J`** *(Standard)*: Führt einen sofortigen, leisen Komplettabgleich aller Werte mit dem Server durch (`/geld`, `/ontime`, `/ardapoints me`, `/jobs stats`).
* **HUD ein-/ausblenden** *(Optional)*: Frei belegbare Taste zum direkten Ein- und Ausfahren des HUDs.
* **Befehle**:
  * `/ardaniahud edit`: Öffnet direkt den visuellen Editor zum freien Verschieben.
  * `/ardaniahud config`: Öffnet das Einstellungsmenü.
  * `/ardaniahud debug`: Statusprüfung im Chat.
  * `/ardaniahud debug copy`: Kopiert den Bericht in die Zwischenablage.

---

### 📥 Installation & Kompatibilität (2.0.1)

1. Installiere den **Fabric Loader** für deine Minecraft-Version (**1.21.8 – 1.21.11**, **26.2** oder **26.3**).
2. Platziere die **Fabric API** in deinem `mods`-Ordner.
3. *(Empfohlen)* Installiere **ModMenu**, um auf das Einstellungsmenü zugreifen zu können (alternativ im Spiel `/ardaniahud config`).
4. Lade die passende Mod-Version herunter:
   * **Minecraft 1.21.8 – 1.21.11**: `ardania-info-hud-mc1.21.x-2.0.1.jar`
   * **Minecraft 26.2 und 26.3**: `ardania-info-hud-mc26.x-2.0.1.jar`

**Getrennte Releases:** 1.21.x und 26.x werden als zwei eigenständige Versionen veröffentlicht. Die passenden Changelogs stehen in `RELEASE_NOTES_MC1.21.x.md` und `RELEASE_NOTES_MC26.x.md`.
