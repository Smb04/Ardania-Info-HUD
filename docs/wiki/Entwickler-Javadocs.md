# Entwickler-Dokumentation & Javadocs

> 🌐 **Sprache / Language:** **Deutsch** | [English Version](Developer-Javadocs)

Diese Seite beschreibt den Aufbau der Java-Klassen von **Ardania Info HUD** sowie die Erzeugung der vollständigen HTML-Javadocs über Gradle.

---

## 📦 Javadocs erzeugen

Die Mod unterstützt zwei getrennte Release-Trains (`mc1.21.x` und `mc26.x`). In beiden Targets ist die Javadoc-Generierung in UTF-8 vorkonfiguriert und kompiliert fehler- und warnungsfrei.

**Voraussetzung:** JDK **25 oder neuer**.

### Javadocs für Minecraft 26.x (`src/main/java`) generieren

```powershell
.\gradlew.bat javadoc --no-daemon `
  -Pminecraft_version=26.2 `
  -Pfabric_api_version=0.161.0+26.2 `
  -Pmodmenu_version=20.0.3 `
  -Parchives_base_name=ardania-info-hud-mc26.x
```

### Javadocs für Minecraft 1.21.8 – 1.21.11 (`src/main/java` + `src/mc121/java`) generieren

```powershell
.\gradlew.bat javadoc --no-daemon `
  -Pminecraft_version=1.21.8 `
  -Pfabric_api_version=0.136.1+1.21.8 `
  -Pmodmenu_version=15.0.0 `
  -Parchives_base_name=ardania-info-hud-mc1.21.x
```

Die erzeugte HTML-Dokumentation liegt anschließend unter:

```text
build/docs/javadoc/index.html
```

---

## 🧩 Klassenübersicht (`de.ardania.hud`)

### Gemeinsame Kernlogik (`src/main/java`)

| Klasse / Record | Aufgabe |
| :--- | :--- |
| `ArdaniaConfig` | Persistente JSON-Konfiguration (`config/ardania_hud.json`), Enums (`HudPosition`, `HudLanguage`, `HudRow`, `JobProgressStyle`, `ServerVisibility`, `ServerOverride`), Validierung & Clamping |
| `ArdaniaState` | Thread-sicherer Zustandsspeicher für Euronen (`BigDecimal`), ArdaPoints, Jobs (`JobEntry`), Spielzeiten (`ServerPlaytimeEntry`), Session-Verdienst und JSON-Cache (`ardania_hud_cache.json`) |
| `ArdaniaMessageParser` | Regex-Parser für Chat- und Actionbar-Meldungen (`[ArdaBank]`, ArdaPoints, `[ArdaniaJobs]`, `/ontime`, unbekannte Befehle) inklusive Schutz vor Fremdspieler-Abfragen |
| `ArdaniaServerDetector` | Reine Erkennungslogik für Ardania-Unterserver (`Header - Tag` Delta, explizite Header-Hinweise, Same-World-Tags, Creative-Modus, Befehlsbaum, `Source` & `Detection`) |
| `ArdaniaSyncManager` | Passive Befehls-Warteschlange (≥ 1.500 ms Abstand), `/afk`- & 10-Block-Schutz, Fallback-`[F]`-Schutz, Moderator-Kompatibilität und periodische Sync-Timer |
| `HudLayout` | Ergonomische Layout-Berechnung nach DIN EN ISO 9241-110 (6 Ankerpunkte, benutzerdefinierte Position, Viewport-Clamping, Smoothstep-Slide, 2-px-Fortschrittsbalken) |
| `ArdaniaI18n` | Clientseitiger Sprachmanager (`de_de`, `en_us`, `es_es`, `fr_fr`) unabhängig von der eingestellten Minecraft-Sprache |
| `ArdaniaChatListener` | Fabric-Chat-/Command-Event-Listener, Unterdrückung reiner Auto-Sync-Antworten und Registrierung von `/ardaniahud` |
| `TabListSnapshot` | Zwischenspeicher für den zuletzt empfangenen Tablist-Header und -Footer |
| `ArdaniaModMenuApiImpl` | Einstiegspunkt für die optionale ModMenu-Integration |
| `mixin.ClientPacketListenerMixin` | Nicht-destruktiver `@Inject(at = @At("TAIL"))`-Mixin zum Mitschneiden von `ClientboundTabListPacket` |

### Versionsspezifische UI- & Client-Klassen

Sowohl für **MC 26.2 – 26.3** (`src/main/java`) als auch für **MC 1.21.8 – 1.21.11** (`src/mc121/java`) existieren angepasste Varianten dieser vier Klassen:

| Klasse | MC 26.x (`src/main/java`) | MC 1.21.8 – 1.21.11 (`src/mc121/java`) |
| :--- | :--- | :--- |
| `ArdaniaClientMod` | Fabric-Einstiegspunkt, `KeyMapping.Category` | Fabric-Einstiegspunkt, Reflection-Kategorie-Erkennung für 1.21.8–1.21.11 |
| `ArdaniaHudRenderer` | `HudElement` + `GuiGraphicsExtractor` | `HudElement` + `GuiGraphics#nextStratum()` & `Matrix3x2fStack` |
| `ArdaniaConfigScreen` | 3-Tab-Konfigurationsmenü (`GuiGraphicsExtractor`) | 3-Tab-Konfigurationsmenü (`GuiGraphics`) |
| `ArdaniaHudEditorScreen` | Dual-Layer-Editor (GLFW + SDL3 `Screen`-Events) | Reiner GLFW-Editor (`nextStratum()`, `drawString`), kompatibel bis 1.21.11 |
