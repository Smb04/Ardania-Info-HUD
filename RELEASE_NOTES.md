# Ardania Info HUD – Release v1.5.0

🎉 **Release v1.5.0 für Minecraft 1.21.X (1.21 – 1.21.11), 26.2 und 26.3 (Fabric)**

Version **1.5.0** erweitert das **Ardania Info HUD** um eine universelle Unterstützung für alle **Minecraft 1.21.X**-Versionen sowie einen **lokalen Zustands-Cache** (`ardania_hud_cache.json`), der auf Unterservern ohne Befehlsunterstützung automatisch die zuletzt bekannten Werte bereitstellt.

---

### ✨ Was ist neu in v1.5.0

* 💾 **Lokaler Zustands-Cache (`ardania_hud_cache.json`)**:
  * Speichert den jeweils letzten bekannten Stand für Euronen, ArdaPoints, Spielzeit und Berufe beim Verlassen des Servers sowie bei Aktualisierungen lokal ab.
  * Sind auf bestimmten Unterservern (z. B. im Nether / `Mine`, `Creative` oder `Eventserver`) einzelne Befehle wie `/jobs stats` oder `/ardapoints me` nicht ausführbar, greift das HUD für genau diese Teilbereiche nahtlos auf den gespeicherten Stand zurück, statt `?` anzuzeigen.
* 🌍 **Mehrsprachigkeit / Lokalisierung (DE, EN, ES, FR)**:
  * Vollständige Lokalisierung für **Deutsch (`de_de`)**, **Englisch (`en_us`)**, **Spanisch (`es_es`)** und **Französisch (`fr_fr`)** für HUD-Labels, Meldungen, Tastenbelegungen und ModMenu-Einstellungen – inklusive direkter Sprachauswahl im Ingame-Menü.
* 🎮 **Volle Minecraft 1.21.X Unterstützung (`1.21` – `1.21.11`)**:
  * Eine einzige universelle JAR-Datei (`ardania-info-hud-mc1.21.x-1.5.0.jar`) für alle Versionen von `1.21` bis `1.21.11` mit 100 % Feature-Parität zu `26.2` und `26.3`.
* ⛏️ **MAX-Joblevel & Unicode-Balken-Support**:
  * Berufe auf Maximalstufe (Level 30) werden im HUD als `[MAX]` in Gold dargestellt (`Mobhunter [MAX]`).
  * Unterstützt das spezielle Server-Format `Max   Level 30 for <Beruf>` sowie Unicode-Fortschrittsbalken (`▏`, `│`) inklusive vollständiger Chat-Unterdrückung beim stillen Sync.
* 🎬 **Auto-Hide mit Slide-Animation, Tab-Integration & Toggle-Taste**:
  * Sanfte Smoothstep-Slide-Animation mit einstellbarer Anzeigedauer (`3s`, `5s`, `8s`, `10s`, `15s`), Einblendung bei `Tab` sowie weichem Raus-Sliden beim Deaktivieren.
  * Intelligente Toggle-Taste blendet ein bereits automatisch ausgefahrenes HUD beim ersten Tastendruck sofort wieder ein.

---

### 📦 Enthaltene Downloads

Wähle die für deine Minecraft-Version passende Datei:
* `ardania-info-hud-mc1.21.x-1.5.0.jar` *(für Minecraft 1.21 bis 1.21.11, Java 21+)*
* `ardania-info-hud-mc26.3-1.5.0.jar` *(für Minecraft 26.3, Java 25+)*
* `ardania-info-hud-mc26.2-1.5.0.jar` *(für Minecraft 26.2, Java 25+)*
