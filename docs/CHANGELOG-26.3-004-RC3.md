---
version: 0.0.4
title: "26.3-rc-3 – Regionsichere Tab-Vervollständigung und Generator-Korrekturen"
description: "Tessera Build 004 – Tab-Parser abgesichert, Plugin-Generatoren an die Terrain-Pipeline angepasst und Bettregeln in Plugin-Events korrigiert"
date: 2026-09-15
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

### Regionsichere Tab-Vervollständigung

- Parserprüfung für zu tief verschachtelte Tag-Eingaben korrigiert. Betroffene Tab-Anfragen werden mit dem vorgesehenen Spam-Kick-Grund zurückgewiesen; gewöhnliche unvollständige Befehle lösen keinen Kick aus.
- Zusätzliche Absicherung gegen Stackoverflows beim Parsen ergänzt, falls der Java-Stack bereits vor der bestehenden 512-Ebenen-Grenze erschöpft ist. Das allgemeine NBT-Tiefenlimit bleibt unverändert.
- Parserprüfung auch für Vorschläge eingebunden, die Plugins über `AsyncTabCompleteEvent` bereitstellen. Zusätzliche Suggestion-Provider werden dabei nicht erneut aufgerufen.
- Verarbeitung auf den zuständigen Regions-Thread des aktuellen Spielers gelegt. Der Entity-Scheduler berücksichtigt Regions- und Dimensionswechsel; der Spielerbezug wird beim Tick aktualisiert.
- Überholte Anfragen, beendete Entity-Scheduler und geschlossene Verbindungen abgesichert. Ausstehende Requests werden abgebrochen, ohne auf fremde Weltzustände zuzugreifen.
- Asynchrone Plugin-Events beibehalten, Vorschlagslisten vor der Weiterleitung kopiert und den Verbindungszustand vor dem Senden erneut geprüft.

### Benutzerdefinierte Welt- und Chunk-Generatoren

- Plugin-Callbacks für Noise, Surface, Bedrock und Caves wieder in die Terrain-Pipeline von Minecraft 26.3 eingebunden.
- Reihenfolge bei Noise-Generatoren korrigiert: Der jeweilige Plugin-Callback folgt direkt auf die zugehörige Vanilla-Phase.
- `shouldGenerateNoise`, `shouldGenerateSurface` und `shouldGenerateCaves` wieder mit den entsprechenden Vanilla-Phasen verknüpft. Die Plugin-Callbacks bleiben auch bei deaktivierter Vanilla-Phase erhalten.
- Callback-Verarbeitung für andere Generatoren und den Sonderfall ohne Vanilla-Terrainhöhe ergänzt.
- Folia-/Moonrise-Ausführung im bestehenden Chunk-Generierungsauftrag erhalten. Für Chunk-Änderungen werden keine zusätzlichen Global- oder Regions-Tasks und keine neuen Hintergrundexecutor verwendet.
- Zufallszustand historischer Generator-Callbacks pro Chunk getrennt, damit parallele Generierungsaufträge sich nicht gegenseitig beeinflussen. Die bisherige Seed-Formel und die Serialisierung nicht parallelfähiger Legacy-Generatoren bleiben erhalten.
- Temporäre `CraftChunkData`-Ansichten werden auch bei Plugin-Ausnahmen freigegeben.

### Bettregeln und Plugin-Events

- `PlayerBedEnterEvent` und `PlayerBedFailEnterEvent` verwenden die bereits ermittelte Regel des tatsächlich benutzten Betts statt pauschal der normalen Bettenregel der Dimension.
- Regeln für Straw Beds und positionsabhängige Bettattribute in der Event-Ausgabe berücksichtigt, einschließlich Schlaf- und Spawnpunkt-Erlaubnis.
- Bisherige interne Event-Brücken erhalten und ebenfalls auf die Regel des konkreten Betttyps angepasst.
- Bestehende Korrekturen für erzwungenes Schlafen, Plugin-Abbrüche, Schlaf-Rekursion und die Darstellung bei anderen Spielern beibehalten. Die öffentliche Bukkit-API bleibt unverändert.

### Build und Patch-Verwaltung

- Buildnummer von `003-alpha` auf `004-alpha` erhöht; Minecraft `26.3-rc-3`, API-Version `26.3` und Java-25-Toolchain beibehalten.
- Ausgewählte Laufzeitkorrekturen aus dem Paper-Stand `2307f8f` übernommen. Die ursprüngliche `paperRef` bleibt als Importreferenz erhalten; es wurde kein vollständiger Paper-Rebase durchgeführt.
- Minecraft-Backports in den eigenen Sinopia-Patches `0041`, `0042` und `0043` gesichert.
- Folia-spezifische Anpassungen getrennt in Tesseras Minecraft-Patch `0038` und Implementierungs-Patch `0028` hinterlegt; zugehörige Folia-Regressionstests in Patch `0029` ergänzt.
- Erneute Anwendung sämtlicher Patchschichten ohne Konflikte geprüft. Die Änderungen bleiben dadurch auch nach dem Neuerzeugen der Java-Quellen erhalten.

### Automatisierte Prüfung

- 25 neue Regressionstestfälle für Tab-Parser, Request-Verarbeitung, Regionsbesitzer, Generator-Callbacks, parallele Chunk-Schreibzugriffe und Bettregeln erfolgreich abgeschlossen.
- Zusammen mit zwölf bestehenden Bett-Tests alle 37 gezielten Testfälle ohne Fehler oder übersprungene Fälle ausgeführt.
- 9.948 Server-Testfälle und 529 API-Testfälle ohne Fehler oder Fehlschläge ausgewertet; insgesamt 89 Fälle wurden übersprungen.
- Den Änderungsstand vor der Anhebung auf Build `004` unter Build `003` mit einem vollständigen `buildTessera`-Durchlauf einschließlich Patch-Anwendung, Build-Prüfungen und JAR-Erstellung erfolgreich geprüft.
- Die dabei erzeugte JAR mit Java `25.0.3` und `--version` erfolgreich gestartet; Exit-Code `0`. Dabei wurde keine Spielwelt gestartet.
