---
version: 0.0.3
title: "26.3-rc-3 – Entity- und Plugin-Korrekturen"
description: "Tessera Build 003 – Cushion-Crash behoben, Erfahrungsorb-Events korrigiert und Paper-Kompatibilität für Spawning, Karten und Kick-Gründe verbessert"
date: 2026-09-14
minecraftVersion: "26.3"
status: alpha
breaking: false
tags:
  - Tessera
  - Sinopia
  - Bugfix
  - Paper-Backports
  - RC3
releaseUrl: "https://github.com/Hackii1432/Tessera/"
downloadUrl: "https://home.mosaikdev.com"
---

## Beschreibung

### Cushions und blockgebundene Entities

- Fehlerhaften `Hanging`-Cast bei der Bewegung von Cushions behoben. Der betroffene Bewegungspfad verursacht keine `ClassCastException` mehr.
- Bestehende `HangingBreakEvent`-Behandlung für Item Frames und andere Hanging-Entities einschließlich Plugin-Abbruch beibehalten.
- Break-Event-Ursachen an Paper angepasst: Nicht-Luft-Blöcke an der geprüften Entity-Position werden als `OBSTRUCTION`, Luft als `PHYSICS` eingeordnet.
- Bestehende Weitergabe des Abbruchzustands vom allgemeinen Entity-Break-Event an das Hanging-Event erhalten.

### Erfahrungsorbs und Plugin-Spawning

- Doppelten `EntitySpawnEvent`-Aufruf für Erfahrungsorbs entfernt. Das Event wird einmal vor dem Zusammenführen der Orbs ausgelöst.
- Abgebrochene Spawn-Events verhindern weiterhin das Hinzufügen und Zusammenführen der betroffenen Erfahrungsorbs.
- Explizite Entity-Erzeugung durch Plugins auf der Schwierigkeit „Friedlich“ an Paper angepasst: Die bisherige pauschale Monster-Sperre im Bukkit-Erzeugungspfad entfernt und den zugehörigen internen Spawn-Aufruf angepasst.
- Feature-Prüfung und natürliche Spawn-Regeln erhalten. Deaktivierte Entity-Features bleiben gesperrt; natürliches Monster-Spawning auf „Friedlich“ wird nicht aktiviert.

### Spieler und Karten

- Fehlenden Kick-Grund `INVALID_PLAYER_MOVEMENT` beim wiederholten Positionspaket innerhalb eines Ticks ergänzt. Die bestehende Paket- und Thread-Prüfung bleibt erhalten.
- Aktualisierung der Spielerzuordnung beim Wegwerfen auf alle `MapItem`-Typen erweitert, einschließlich der neuen Monument-, Mansion- und weiteren Erkundungskarten.
- Bestehende Behandlung abgebrochener Drops und fehlender Kartendaten erhalten.

### Build und bestehende Korrekturen

- Buildnummer von `002-alpha` auf `003-alpha` erhöht; Minecraft `26.3-rc-3`, API-Version `26.3` und Java-25-Toolchain beibehalten.
- Ausgewählte Fehlerkorrekturen aus den Paper-Commits `09d1b0a` und `2a84295` übernommen und die Minecraft-Änderungen im neuen Sinopia-Patch `0040` gesichert.
- Bestehende Strohbett-, Schild- und Regionskorrekturen erhalten. Keine Konfigurationsoptionen entfernt und keine Access-Transformer-Bereinigung übernommen.
- Regressionstests für Cushion-Bewegungen, Break-Event-Ursachen, Plugin-Abbrüche, Erfahrungsorbs, Spawning, Bewegungspakete und Karten-Drops ergänzt.

### Automatisierte Prüfung

- Alle 29 neuen Regressionstestfälle erfolgreich und ohne übersprungene Fälle abgeschlossen.
- 9.923 Server-Testfälle und 529 API-Testfälle ohne Fehler oder Fehlschläge ausgewertet; insgesamt 89 Fälle wurden übersprungen.
- Vollständigen `buildTessera`-Durchlauf einschließlich Patch-Anwendung, Build-Prüfungen und JAR-Erstellung erfolgreich abgeschlossen.
- Ausführbare `tessera-server-26.3-rc-3.build.003-alpha.jar` erstellt.
- Die neue JAR in einem isolierten Build-Verzeichnis mit Java `25.0.3` und `--version` erfolgreich geprüft; Ausgabe: `26.3-rc-3-003-f91ff9c`, Exit-Code `0`. Dabei wurde keine Spielwelt gestartet.
