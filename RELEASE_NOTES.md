# Ardania Info HUD – Release v1.0.0

🎉 **Initialer Release für Minecraft 26.2 und 26.3 (Fabric)**

Das **Ardania Info HUD** ist ein schlankes, client-seitiges Informations-Overlay im klassischen Scoreboard-Design für den Minecraft-Server **Ardania.de**. Es blendet alle wichtigen Account-Daten unaufdringlich am Bildschirmrand ein, ohne dass Befehle manuell getippt werden müssen.

---

### ✨ Highlights & Funktionen

* 💰 **Euronen (Geldanzeige)**:
  * Ruft den tatsächlichen Kontostand automatisch und lautlos über die `[ArdaBank]` ab.
  * Live-Erfassung von Job-Vergütungen in der Action-Bar (`+0.15$`, `+0,02 Euronen`) mit intelligentem Anti-Spam-Debounce.
  * Registriert direkte Buchungsmeldungen der ArdaBank (Überweisungen, Käufe, Zinsen) ohne Verzögerung.

* 💠 **ArdaPoints**:
  * Zeigt deine aktuellen Punkte in Echtzeit an.
  * Erkennt Daily-Rewards, monatliche Belohnungen und Server-Vote-Gutschriften (`+25 ArdaPoints`).

* ⏳ **Multi-Server Spielzeittracking (Ontime)**:
  * Summiert die Spielzeiten aller Ardania-Welten (`Hauptserver`, `Mine` / Nether, `Creative`, `Eventserver`) zu deiner tatsächlichen Gesamtspielzeit zusammen.
  * Zählt die Online-Minuten lokal mit und gleicht sie periodisch im Hintergrund ab.

* ⛏️ **Multi-Job & Joblevel**:
  * Erkennt automatisch alle deine aktiven Berufe aus `/jobs stats`.
  * Übersichtliche Anzeige von Berufsbezeichnung und Level (z. B. `Mobhunter [24]`, `Farmer [12]`, `Miner [21]`).

* 🔕 **Stille Synchronisation & intelligenter Chat-Filter**:
  * Automatische Hintergrund-Abfragen der Mod (`/geld`, `/ardapoints me`, `/jobs stats`, `/ontime`) werden **vollständig aus dem Chat ausgeblendet**.
  * Fehlermeldungen bei Weltenwechseln (z. B. nicht vorhandene Befehle im Nether) werden automatisch unterdrückt.
  * **Tippst du selbst einen Befehl im Chat ein**, wird die Antwort wie gewohnt ganz normal angezeigt!

* ⚙️ **Vollständige ModMenu-Integration**:
  * Ingame-Menü zum Ein-/Ausschalten einzelner Zeilen (Euronen, Punkte, Spielzeit, Jobs).
  * Einstellbare Position: `Rechts Mitte (Hypixel)`, `Oben Rechts`, `Unten Rechts`.
  * Frei konfigurierbare Synchronisierungs-Intervalle für Geld, Punkte und Spielzeit.

* 🎮 **Steuerung & Keybinds**:
  * Taste **`J`** *(Standard)* für sofortigen leisen Komplettabgleich aller Werte.
  * Frei anpassbar im Vanilla-Menü unter *Optionen → Steuerung → Tastenbelegung*.

---

### 📦 Enthaltene Downloads

Wähle die für deine Minecraft-Version passende Datei:
* `ardania-info-hud-mc26.3-1.0.0.jar` *(für Minecraft 26.3)*
* `ardania-info-hud-mc26.2-1.0.0.jar` *(für Minecraft 26.2)*

**Voraussetzungen:**
* Fabric Loader (>= 0.19.5)
* Fabric API
* Java 25
* *(Empfohlen)* ModMenu zur einfachen Konfiguration im Spiel
