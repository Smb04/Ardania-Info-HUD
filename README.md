# Ardania Info HUD

<p align="center">
  <img src="src/main/resources/assets/ardania_hud/icon.png" alt="Ardania Info HUD Logo" width="128" height="128"/>
</p>

<p align="center">
  <strong>Ein kompaktes, elegantes Client-Side Informations-Overlay für den Minecraft-Server Ardania.de.</strong><br>
  Kompatibel mit <strong>Fabric</strong> für Minecraft <strong>26.2</strong> und <strong>26.3</strong>.
</p>

<p align="center">
  <a href="#features">Features</a> •
  <a href="#installation">Installation</a> •
  <a href="#konfiguration--modmenu">Konfiguration</a> •
  <a href="#steuerung">Steuerung</a> •
  <a href="#technische-architektur">Architektur</a> •
  <a href="#bauen">Bauen</a> •
  <a href="#lizenz">Lizenz</a>
</p>

---

## ✨ Features

* 💰 **Euronen (Geldanzeige)**:
  * Ruft deinen tatsächlichen Kontostand direkt über die `[ArdaBank]` ab.
  * Reagiert auf Job-Einnahmen (`+0.15$`, `+0,02 Euronen`) und passt das Guthaben automatisch an.
  * Erfasst direkte Buchungsmitteilungen der ArdaBank (Überweisungen, Käufe, Zinsen) ohne Verzögerung.
* 💠 **ArdaPoints**:
  * Zeigt deine aktuellen ArdaPoints / ArdaniaPoints in Echtzeit an.
  * Unterstützt automatische Erfassung von Daily-Rewards, monatlichen Belohnungen und Server-Vote-Gutschriften (`+25 ArdaPoints`).
* ⏳ **Multi-Server Spielzeittracking (Ontime)**:
  * Summiert die Spielzeiten aller Ardania-Welten (`Hauptserver`, `Mine` / Nether, `Creative`, `Eventserver`) zu einer exakten Gesamtrechenzeit zusammen.
  * Zählt die Online-Minuten lokal mit und gleicht sie stündlich im Hintergrund ab.
* ⛏️ **Multi-Job & Joblevel**:
  * Erkennt automatisch alle deine aktiven Berufe aus `/jobs stats` (inklusive Fortschrittsbalken).
  * Zeigt Berufsbezeichnung und Level übersichtlich an (z. B. `Mobhunter [24]`, `Farmer [12]`, `Miner [21]`).
* 🔕 **Intelligente Silent-Synchronisation & Chat-Filterung**:
  * Alle automatischen Abfragen (`/geld`, `/ardapoints me`, `/jobs stats`, `/ontime`) werden **vollständig aus dem Chat ausgeblendet**.
  * Fehlermeldungen bei Weltenwechseln (z. B. nicht vorhandene Befehle im Nether) werden automatisch abgefangen.
  * **Tippst du selbst einen Befehl im Chat ein**, wird die Antwort wie gewohnt ganz normal angezeigt!
* 🛡️ **Anti-Flag & Rate-Limiting**:
  * Befehle werden niemals gleichzeitig im selben Server-Tick gesendet, sondern gestaffelt mit Pausen abgearbeitet.
  * Intelligentes Farming-Debounce verhindert Spam-Flags beim schnellen Abbauen von Erzen oder Weizen.
* 🎨 **Kompaktes Hypixel-Scoreboard-Design**:
  * Halbtransparenter, abgedunkelter Hintergrundkasten für optimale Lesbarkeit bei jedem Untergrund (Wüste, Schnee, Höhlen).
  * Wird bei **F1** automatisch ausgeblendet.
  * Ist ausschließlich aktiv, wenn du tatsächlich auf `ardania.de` verbunden bist.

---

## 📥 Installation

1. Installiere den [Fabric Loader](https://fabricmc.net/use/) für deine Minecraft-Version (**26.2** oder **26.3**).
2. Installiere die [Fabric API](https://modrinth.com/mod/fabric-api) in deinen `mods`-Ordner.
3. *(Empfohlen)* Installiere [ModMenu](https://modrinth.com/mod/modmenu), um alle Einstellungen bequem im Spiel anpassen zu können.
4. Lade die passende `ardania-info-hud-*.jar` herunter und lege sie in deinen `.minecraft/mods`-Ordner:
   * **Minecraft 26.3**: `ardania-info-hud-mc26.3-1.0.0.jar`
   * **Minecraft 26.2**: `ardania-info-hud-mc26.2-1.0.0.jar`

---

## ⚙️ Konfiguration & ModMenu

Die Mod bietet ein vollständiges Ingame-Einstellungsmenü über **ModMenu** (oder per Datei unter `.minecraft/config/ardania_hud.json`).

### Einstellungsoptionen:
* **HUD aktivieren**: Schaltet das Overlay an oder aus.
* **Position**:
  * `Rechts Mitte (Hypixel)` *(Standard - mittig am rechten Rand)*
  * `Oben Rechts`
  * `Unten Rechts`
* **Modulare Zeilenanzeige**:
  * Euronen anzeigen (`true`/`false`)
  * ArdaPoints anzeigen (`true`/`false`)
  * Spielzeit anzeigen (`true`/`false`)
  * Jobs anzeigen (`true`/`false`)
* **Synchronisierungszeiten**:
  * **Geld Cooldown**: 15s / 30s *(Standard)* / 60s / 120s
  * **Punkte Intervall**: 30s / 60s *(Standard)* / 120s / 300s
  * **Spielzeit Intervall**: 15m / 30m / 60m *(Standard)* / 120m
  * **Sync beim Beitreten**: Automatische Datenabfrage beim Betreten des Servers.

---

## 🎮 Steuerung

* **Taste `J`** *(Standard)*: Führt einen sofortigen, leisen Komplettabgleich aller Werte mit dem Server durch (`/geld`, `/ardapoints me`, `/jobs stats`, `/ontime`).
* **Taste anpassen**: Die Taste kann im Vanilla-Menü unter **Optionen $\rightarrow$ Steuerung $\rightarrow$ Tastenbelegung** in der Kategorie *Ardania Info HUD* frei auf jede beliebige Taste gelegt werden.

---

## 🏗️ Technische Architektur

Das Projekt ist strikt modular nach Clean-Code-Prinzipien aufgebaut:

```text
de.ardania.hud/
├── ArdaniaClientMod.java       # Mod-Einstiegspunkt, Event-Registrierungen
├── ArdaniaConfig.java          # Persistent JSON-Konfigurationsverwaltung
├── ArdaniaConfigScreen.java    # Interaktives Ingame-Screen-UI für ModMenu
├── ArdaniaModMenuApiImpl.java  # ModMenu API Integration
├── ArdaniaState.java           # Thread-sicherer Zustands- und Datenspeicher
├── ArdaniaMessageParser.java   # Regex-Parsing von Chat, Actionbar & Systemnachrichten
├── ArdaniaChatListener.java    # Fabric ALLOW_GAME & Message Interceptors
├── ArdaniaSyncManager.java     # Anti-Spam Queue, Debounce & Request-Scheduler
└── ArdaniaHudRenderer.java     # Fabric HudElement, Layout & Text-Rendering
```

---

## 🔨 Projekt selbst bauen

Voraussetzung: **JDK 25 oder neuer**.

```powershell
# Repository klonen
git clone https://github.com/DEIN_BENUTZERNAME/ardania-info-hud.git
cd ardania-info-hud

# Bauen & Unit-Tests ausführen (für Minecraft 26.3)
.\gradlew build

# Test-Client starten
.\gradlew runClient
```

Die fertigen JAR-Dateien liegen nach dem Build unter:
* `build/distributions/mc26.2/`
* `build/distributions/mc26.3/`

---

## 📄 Lizenz

Dieses Projekt steht unter der [CC0 1.0 Universal Lizenz](LICENSE) und kann frei verwendet, modifiziert und geteilt werden.
