---
version: 0.0.1
title: "26.3-rc-3 – Minecraft-Update"
description: "Tessera Build 001 – Update auf Minecraft 26.3-rc-3 mit offizieller Mache-Basis und erhaltenen Tessera-Korrekturen"
date: 2026-09-14
minecraftVersion: "26.3"
status: alpha
breaking: false
tags:
  - Tessera
  - Sinopia
  - Minecraft-Update
  - RC3
releaseUrl: "https://github.com/Hackii1432/Tessera/"
downloadUrl: "https://home.mosaikdev.com"
---

## Beschreibung

### Minecraft 26.3-rc-3

- Tessera und die integrierte Sinopia-Basis von Minecraft `26.3-rc-2` auf `26.3-rc-3` umgestellt.
- Offizielle Mache-Buildbasis auf `26.3-rc-3+build.1` aktualisiert und den zugehörigen Gradle-Patch angepasst.
- Minecraft-Datenversion `5022` und Snapshot-Protokoll `338` beziehungsweise Netzwerk-Protokollwert `1073742162` übernommen.
- Plugin-API-Version `26.3`, Java-25-Toolchain und Build-Kennung `001-alpha` beibehalten.

### Bestehende Korrekturen erhalten

- Sämtliche bestehenden Sinopia-, Folia- und Tessera-Minecraft-Patches konfliktfrei auf die RC3-Basis angewendet.
- Die zuvor übernommenen Paper-Backports sowie Schild-, Bett-, Drachen-Respawn- und Regionskorrekturen unverändert erhalten.
- Den vollständigen erzeugten Tessera-Minecraft-Quellstand mit RC2 verglichen: ausschließlich die erwarteten Versionsänderungen in `DetectedVersion` und `SharedConstants` festgestellt.
- API und Server-Implementierung gegenüber dem zuvor geprüften Stand unverändert beibehalten. Keine zusätzlichen Gameplay-Änderungen eingeführt.
- Die ursprüngliche Paper-Importreferenz erhalten; das Minecraft-Update direkt auf der lokalen Sinopia-Basis durchgeführt.

### Automatisierte Prüfung

- Regressionstest für die tatsächlich geladenen RC3-Versionsdaten, Datenversion und Protokollkonstanten ergänzt.
- Vollständigen `buildTessera`-Durchlauf einschließlich erneuter Patch-Anwendung, Tests, Prüftasks und JAR-Erstellung erfolgreich abgeschlossen.
- 9.893 Server-Testfälle und 529 API-Testfälle ohne Fehler oder Fehlschläge ausgewertet; insgesamt 89 Fälle wurden übersprungen. Der neue RC3-Versions-Test ist erfolgreich und wurde nicht übersprungen.
- Identischen Minecraft-Quellbaum nach erneuter Patch-Anwendung bestätigt. Der neue Regressionstest ist in der versionierten Sinopia-Basis gespeichert und wird daraus reproduzierbar übernommen.
- Ausführbare `tessera-server-26.3-rc-3.build.001-alpha.jar` erstellt. Die vorherige RC2-JAR separat erhalten.
- Die neue JAR in einem isolierten Build-Verzeichnis mit Java `25.0.3` und `--version` erfolgreich gestartet; Ausgabe: `26.3-rc-3-001-60a278b`, Exit-Code `0`. Dabei wurde keine Spielwelt gestartet.
