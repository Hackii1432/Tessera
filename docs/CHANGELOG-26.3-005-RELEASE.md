---
version: 0.0.5
title: "26.3 – Minecraft-Release"
description: "Tessera Build 005 – Umstellung auf das offizielle Minecraft-26.3-Release mit Mache 26.3+build.1 und erhaltenen Folia-/Tessera-Anpassungen"
date: 2026-09-15
minecraftVersion: "26.3"
status: alpha
breaking: false
tags:
  - Tessera
  - Sinopia
  - Minecraft-Update
  - Release
releaseUrl: "https://github.com/Hackii1432/Tessera/"
downloadUrl: "https://home.mosaikdev.com"
---

## Beschreibung

### Minecraft 26.3 Release

- Tessera und die integrierte Sinopia-Basis von Minecraft `26.3-rc-3` auf das offizielle Release `26.3` umgestellt.
- Mache auf `io.papermc:mache:26.3+build.1` aktualisiert. Grundlage ist der offizielle [Mache-Release-Commit `56bbc35`](https://github.com/PaperMC/mache/commit/56bbc351c83dd071f69a1a0f79c3ccfad8ca618e) aus `release/26.3`.
- Minecraft-Datenversion von `5022` auf `5023` angehoben und das Snapshot-Netzwerkprotokoll durch das Release-Protokoll `777` ersetzt.
- Offizielle Release-Versionsdaten und Ressourcen übernommen. Ressourcenpaketformat `97.1` und Datenpaketformat `121.0` bleiben unverändert.
- Buildnummer von `004-alpha` auf `005-alpha` erhöht. Das neue Server-Artefakt heißt `tessera-server-26.3.build.005-alpha.jar`.

### Bestehende Folia-/Tessera-Anpassungen erhalten

- Alle Sinopia-, Folia- und Tessera-Patchschichten ohne Konflikte auf die Release-Basis angewendet.
- Den bestehenden Tessera-Gradle-Patch auf dieselbe Mache-Version wie Sinopia angepasst. Keine Umschreibung oder Entfernung bestehender Gameplay- und Regionspatches erforderlich.
- Die zuletzt übernommenen Paper-Backports für Tab-Vervollständigung, benutzerdefinierte Chunk-Generatoren und Bettregeln sowie die bisherigen Regionskorrekturen beibehalten.
- Den vollständigen erzeugten Minecraft-Quellstand mit dem gesicherten RC3-Stand verglichen: Von `5.518` Dateien unterscheiden sich ausschließlich `DetectedVersion.java` und `SharedConstants.java` durch die erwarteten Versionsänderungen.
- Die offiziellen Vanilla-Server-JARs verglichen: Die abweichenden `1.511` Strukturdateien enthalten nach dem Entpacken identische Daten; nur ihre Komprimierung hat sich geändert. Keine zusätzlichen Struktur- oder Gameplay-Änderungen eingeführt.
- Die ursprüngliche Paper-Importreferenz erhalten. Das Release-Update wurde auf der lokalen Sinopia-Basis durchgeführt, nicht als vollständiger Paper-Rebase.

### Build und Versionsprüfung

- Plugin-API-Version `26.3`, Java-25-Toolchain und Gradle `9.4.1` beibehalten. Die bestehende paperweight-Kompatibilität bleibt damit erhalten.
- Den bisherigen RC3-Versionstest durch `MinecraftReleaseVersionTest` ersetzt und um Prüfungen der Versionsserie, des Release-Protokolls, der Paketformate und des Stable-/Snapshot-Status erweitert.
- Versionsdaten direkt an Minecraft-Laufzeitmetadaten und Konstanten geprüft, nicht nur am Dateinamen der gebauten JAR.
- README, Sinopia-Basisdokumentation und Build-Workflow auf den Release-Stand aktualisiert. Vorhandene RC3-Server-Artefakte bleiben erhalten.

### Automatisierte Prüfung

- Vollständigen `buildTessera`-Durchlauf mit erneuter Patch-Anwendung, Kompilierung, Tests, Build-Prüfungen und JAR-Erstellung erfolgreich abgeschlossen: `BUILD SUCCESSFUL in 18m 13s`.
- Server-Testsuite mit `9.948` Testfällen ohne Fehler oder Fehlschläge durchlaufen; `87` Fälle wurden übersprungen. Der neue Release-Versionstest wurde erfolgreich ausgeführt und nicht übersprungen.
- Die unveränderten API-Tests von Gradle als aktuell bestätigt: `529` Fälle, davon `2` übersprungen, ohne Fehler oder Fehlschläge. Die drei Checkstyle-Testfälle wurden erfolgreich aus dem Build-Cache übernommen.
- Build-Prüfungen einschließlich `scanJarForBadCalls` erfolgreich abgeschlossen. Die erzeugten Git-Arbeitsverzeichnisse sind nach dem Build sauber.
- Die fertige Server-JAR in einem isolierten Verzeichnis mit Java `25.0.3` und `--version` geprüft. Ausgabe: `26.3-005-4ac2731`, Exit-Code `0`.

Minecraft verwendet jetzt das Release-Protokoll für Clients mit Version `26.3`.
Tessera bleibt weiterhin ein Alpha-Build. Die Prüfung umfasste keinen gestarteten
Spielserver mit Welt und keinen Live-Mehrspielertest mit Plugins.
