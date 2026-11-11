# Developer Documentation & Javadocs

> 🌐 **Language / Sprache:** [Deutsch](Entwickler-Javadocs) | **English**

This page outlines the Java class architecture of **Ardania Info HUD** and explains how to generate HTML Javadocs using Gradle.

---

## 📦 Generating Javadocs

The mod maintains two separate release trains (`mc1.21.x` and `mc26.x`). Both targets have UTF-8 Javadoc options configured in `build.gradle`.

**Requirement:** JDK **25 or newer**.

### Generate Javadocs for Minecraft 26.x (`src/main/java`)

```powershell
.\gradlew.bat javadoc --no-daemon `
  -Pminecraft_version=26.2 `
  -Pfabric_api_version=0.161.0+26.2 `
  -Pmodmenu_version=20.0.3 `
  -Parchives_base_name=ardania-info-hud-mc26.x
```

### Generate Javadocs for Minecraft 1.21.8 – 1.21.11 (`src/main/java` + `src/mc121/java`)

```powershell
.\gradlew.bat javadoc --no-daemon `
  -Pminecraft_version=1.21.8 `
  -Pfabric_api_version=0.136.1+1.21.8 `
  -Pmodmenu_version=15.0.0 `
  -Parchives_base_name=ardania-info-hud-mc1.21.x
```

The generated HTML API documentation is written to:

```text
build/docs/javadoc/index.html
```

---

## 🧩 Class Overview (`de.ardania.hud`)

### Shared Core Logic (`src/main/java`)

| Class / Record | Responsibility |
| :--- | :--- |
| `ArdaniaConfig` | Persistent JSON configuration (`config/ardania_hud.json`), option enums (`HudPosition`, `HudLanguage`, `HudRow`, `JobProgressStyle`, `ServerVisibility`, `ServerOverride`), validation & clamping |
| `ArdaniaState` | Thread-safe state store for Euronen (`BigDecimal`), ArdaPoints, Jobs (`JobEntry`), per-world playtimes (`ServerPlaytimeEntry`), session profit, and JSON cache (`ardania_hud_cache.json`) |
| `ArdaniaMessageParser` | Regex parser for chat & action-bar messages (`[ArdaBank]`, ArdaPoints, `[ArdaniaJobs]`, `/ontime`, unknown command errors) with foreign-player query isolation |
| `ArdaniaServerDetector` | Pure sub-server detection logic (`Header - Tag` delta, explicit header hints, same-world tags, Creative game mode, command tree signals, `Source` & `Detection`) |
| `ArdaniaSyncManager` | Passive rate-limited command queue (≥ 1,500 ms delay), `/afk` & 10-block protection, `[F]` Fallback safety, moderator command compatibility, and periodic sync timers |
| `HudLayout` | Ergonomic layout engine per DIN EN ISO 9241-110 (6 anchors, custom coordinates, viewport clamping, smoothstep slide easing, 2px progress bars) |
| `ArdaniaI18n` | Client-side localization loader (`de_de`, `en_us`, `es_es`, `fr_fr`) supporting live language switching independently of the Minecraft client language |
| `ArdaniaChatListener` | Fabric chat/command listener, silent auto-sync response suppression, and `/ardaniahud` client command registration |
| `TabListSnapshot` | Snapshot holder for the latest captured tab-list header and footer text |
| `ArdaniaModMenuApiImpl` | Entrypoint for optional ModMenu integration |
| `mixin.ClientPacketListenerMixin` | Non-destructive `@Inject(at = @At("TAIL"))` mixin capturing `ClientboundTabListPacket` header/footer updates |

### Version-Specific Client & UI Classes

Both **MC 26.2 – 26.3** (`src/main/java`) and **MC 1.21.8 – 1.21.11** (`src/mc121/java`) provide tailored implementations of these four classes:

| Class | MC 26.x (`src/main/java`) | MC 1.21.8 – 1.21.11 (`src/mc121/java`) |
| :--- | :--- | :--- |
| `ArdaniaClientMod` | Fabric client entrypoint using `KeyMapping.Category` | Fabric client entrypoint with reflective category support for 1.21.8–1.21.11 |
| `ArdaniaHudRenderer` | `HudElement` + `GuiGraphicsExtractor` | `HudElement` + `GuiGraphics#nextStratum()` & `Matrix3x2fStack` |
| `ArdaniaConfigScreen` | 3-tab settings screen (`GuiGraphicsExtractor`) | 3-tab settings screen (`GuiGraphics`) |
| `ArdaniaHudEditorScreen` | Dual-layer input editor (GLFW + SDL3 `Screen` events) | Pure GLFW input editor (`nextStratum()`, `drawString`) compatible across 1.21.8–1.21.11 |
