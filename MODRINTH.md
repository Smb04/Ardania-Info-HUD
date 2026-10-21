# Ardania Info HUD

**Ein kompaktes, elegantes Client-Side Informations-Overlay für den Minecraft-Server Ardania.de.**

Nie wieder `/money`, `/ardapoints me` oder `/jobs stats` per Hand eingeben müssen! Das **Ardania Info HUD** zeigt dir alle wichtigen Server-Informationen in einem aufgeräumten, unaufdringlichen Scoreboard-Overlay am rechten Bildschirmrand an – ganz im Stil klassischer Minigame-Server.

---

### ✨ Features im Überblick

* 💰 **Euronen (Geldanzeige)**:
  * Ruft deinen tatsächlichen Kontostand automatisch und lautlos über die **ArdaBank** ab.
  * Reagiert auf Job-Vergütungen in der Action-Bar (`+0.15$`, `+0,02 Euronen`) und gleicht den Betrag verlässlich ab.
  * Erfasst direkte Buchungsmitteilungen (Überweisungen, Käufe, Zinsen) ohne Verzögerung.
* 💠 **ArdaPoints**:
  * Zeigt deine aktuellen Punkte in Echtzeit an.
  * Unterstützt automatische Erfassung von Daily-Rewards, monatlichen Belohnungen und Server-Vote-Gutschriften (`+25 ArdaPoints`).
* ⏳ **Multi-Server Spielzeittracking (Ontime)**:
  * Summiert die Spielzeiten aller Welten (`Hauptserver`, `Mine` / Nether, `Creative`, `Eventserver`) zu deiner tatsächlichen Gesamtspielzeit zusammen.
  * Zählt die Online-Minuten lokal mit und gleicht sie stündlich im Hintergrund ab.
* ⛏️ **Multi-Job & Joblevel**:
  * Erkennt automatisch alle deine aktiven Berufe aus `/jobs stats`.
  * Zeigt Berufsbezeichnungen und Level übersichtlich an (z. B. `Mobhunter [24]`, `Farmer [12]`, `Miner [21]`).
* 🔕 **Intelligente Silent-Synchronisation & Chat-Filter**:
  * Alle Hintergrund-Abfragen der Mod werden **vollständig aus dem Chat ausgeblendet** – dein Chatfenster bleibt komplett sauber!
  * Fehlermeldungen bei Weltenwechseln (z. B. nicht vorhandene Befehle im Nether) werden automatisch abgefangen.
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

Über **ModMenu** (oder in der Datei `.minecraft/config/ardania_hud.json`) kannst du das Overlay frei nach deinen Wünschen anpassen:

* **Position**:
  * `Rechts Mitte (Hypixel)` *(Standard)*
  * `Oben Rechts`
  * `Unten Rechts`
* **Modulare Anzeigen**: Blende beliebige Zeilen ein oder aus (Euronen, ArdaPoints, Spielzeit, Jobs).
* **Synchronisierungszeiten**:
  * Geld-Cooldown (15s / 30s / 60s / 120s)
  * Punkte-Intervall (30s / 60s / 120s / 300s)
  * Spielzeit-Intervall (15m / 30m / 60m / 120m)
  * Sync beim Serverbeitritt an-/ausschalten

---

### 🎮 Steuerung

* **Taste `J`** *(Standard)*: Führt einen sofortigen, leisen Komplettabgleich aller Werte mit dem Server durch (`/geld`, `/ardapoints me`, `/jobs stats`, `/ontime`).
* **Taste ändern**: Unter *Optionen → Steuerung → Tastenbelegung* in der Kategorie *Ardania Info HUD* frei belegbar.

---

### 📥 Installation & Kompatibilität

1. Installiere den **Fabric Loader** für deine Minecraft-Version (**26.2** oder **26.3**).
2. Platziere die **Fabric API** in deinem `mods`-Ordner.
3. *(Empfohlen)* Installiere **ModMenu**, um auf das Einstellungsmenü zugreifen zu können.
4. Lade die passende Mod-Version herunter und lege sie in den `mods`-Ordner.
