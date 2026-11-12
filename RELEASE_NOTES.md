# Ardania Info HUD – Releases v2.0.1

Ab Version **2.0.0** werden **1.21.x** und **26.x** wieder als **zwei getrennte Release-Trains** veröffentlicht. Beide enthalten denselben Funktionsumfang, unterscheiden sich aber in Zielversion, Java-Anforderung und Build-Datei.

| Release-Train | Minecraft | Java | Loader | Datei | Release Notes |
| --- | --- | --- | --- | --- | --- |
| **1.21.x** | 1.21.8 – 1.21.11 | 21+ | Fabric ≥ 0.16.13 | `ardania-info-hud-mc1.21.x-2.0.1.jar` | [RELEASE_NOTES_MC1.21.x.md](RELEASE_NOTES_MC1.21.x.md) |
| **26.x** | 26.2 – 26.3 | 25+ | Fabric ≥ 0.19.5 | `ardania-info-hud-mc26.x-2.0.1.jar` | [RELEASE_NOTES_MC26.x.md](RELEASE_NOTES_MC26.x.md) |

---

## 🔧 Hotfix in v2.0.1

* **LabyClient- & Screen-Pause:** Die Sync-Queue pausiert nun bei *jedem* geöffneten Screen (inkl. LabyMods `LabyScreenRenderer`, Vanilla-Chat, Inventar/Kisten) plus **2,5 s Nachlaufzeit** nach dem Schließen bzw. Absenden einer Chat-Nachricht.
* **`J`-Taste im Chat geschützt:** `syncKey` und `toggleKey` lösen nicht mehr aus, solange ein Screen/Chat geöffnet ist.
* **Keine ungewollten `/jobs stats`-Ausgaben im Chat:** `/jobs quests` (u. a. `browse`/`top`/`info`) schaltet die Unterdrückung nicht mehr ab; Tagesquest-Meldungen (`[ArdaniaJobs] ... tägliche Aufgaben ...`) triggern keinen automatischen Job-Sync mehr; doppelte Queue-Ausführung verhindert.
* **`/ontime`-Fix nach Stillstand:** Längeres Unterdrückungsfenster (**8 s**) für den BungeeCord-/Proxy-Befehl, automatische Mit-Unterdrückung der zugehörigen `§m`-Trenn- und Leerzeilen, und aktives Chatten zählt nun als Aktivität.

---

## 📦 Downloads

Beide JARs liegen nach dem Build unter:

```text
build/distributions/mc1.21.x/ardania-info-hud-mc1.21.x-2.0.1.jar
build/distributions/mc26.x/ardania-info-hud-mc26.x-2.0.1.jar
```

## 🚀 Veröffentlichung

Beide Builds (**26.x** für `26.2–26.3` und **1.21.x** für `1.21.8–1.21.11`) wurden separat gebaut, automatisiert getestet und im Spiel auf **26.3** sowie **1.21.8** erfolgreich verifiziert.

## 🛠️ Build

```powershell
# Beide Targets bauen und testen
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build-all.ps1 `
  -JavaHome "C:\Users\Sven Biller\.jdks\openjdk-25.0.2"
```

Jeder Lauf führt die 86 Unit-Tests aus (Parser 41, Detector 22, Sync 16, Layout/Config 7).
