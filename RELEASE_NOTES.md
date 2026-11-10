# Ardania Info HUD – Releases v2.0.0

Ab Version **2.0.0** werden **1.21.x** und **26.x** wieder als **zwei getrennte Release-Trains** veröffentlicht. Beide enthalten denselben Funktionsumfang, unterscheiden sich aber in Zielversion, Java-Anforderung und Build-Datei.

| Release-Train | Minecraft | Java | Loader | Datei | Release Notes |
| --- | --- | --- | --- | --- | --- |
| **1.21.x** | 1.21 – 1.21.11 | 21+ | Fabric ≥ 0.15.0 | `ardania-info-hud-mc1.21.x-2.0.0.jar` | [RELEASE_NOTES_MC1.21.x.md](RELEASE_NOTES_MC1.21.x.md) |
| **26.x** | 26.2 – 26.3 | 25+ | Fabric ≥ 0.19.5 | `ardania-info-hud-mc26.x-2.0.0.jar` | [RELEASE_NOTES_MC26.x.md](RELEASE_NOTES_MC26.x.md) |

---

## 📦 Downloads

Beide JARs liegen nach dem Build unter:

```text
build/distributions/mc1.21.x/ardania-info-hud-mc1.21.x-2.0.0.jar
build/distributions/mc26.x/ardania-info-hud-mc26.x-2.0.0.jar
```

## 🚀 Veröffentlichungsreihenfolge

1. **26.x kann sofort veröffentlicht werden.** Der Build ist unabhängig vom 1.21.x-Editor und wurde separat gebaut und getestet.
2. **1.21.x** erst nach dem In-Game-Check auf **1.21.11** veröffentlichen: Editor öffnen, HUD mit der Maus ziehen und mit den Pfeiltasten verschieben. Der Build selbst ist grün; geprüft wird nur noch das reale Eingabeverhalten.

## 🛠️ Build

```powershell
# 26.x bauen und testen
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build-all.ps1 `
  -JavaHome "C:\Program Files\Java\jdk-26" -Targets mc26.x

# 1.21.x bauen und testen
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build-all.ps1 `
  -JavaHome "C:\Program Files\Java\jdk-26" -Targets mc1.21.x
```

Jeder Lauf führt die 80 Unit-Tests aus (Parser 39, Detector 22, Sync 12, Layout/Config 7).
