---
version: 0.0.6
title: "26.3 – Paper-Backports und Regionskorrekturen"
description: "Tessera Build 006 – ausgewählte Paper-Korrekturen für Blockabbau, Plugin-Events, Schildtexte und API-Adapter mit eigenen Folia-/Tessera-Regionsanpassungen"
date: 2026-09-21
minecraftVersion: "26.3"
status: alpha
breaking: false
tags:
  - Tessera
  - Sinopia
  - Paper-Backports
  - Region-Threading
  - Bugfixes
releaseUrl: "https://github.com/Hackii1432/Tessera/"
downloadUrl: "https://home.mosaikdev.com"
---

## Beschreibung

### Paper-Korrekturen und Blockabbau

- Ausgewählte Korrekturen aus Papers `dev/26.3` bis zum geprüften Stand `c13e3c9` in die lokale Sinopia-Basis übernommen. Die ursprüngliche Paper-Importreferenz und bestehende Tessera-Anpassungen beibehalten; kein vollständiger Paper-Rebase durchgeführt.
- Verzögerten Blockabbau korrigiert: Der Fortschritt verwendet die seit dem Abbaustart vergangenen Ticks statt des absoluten Startwerts.
- Die Zeitbasis der Lag-Kompensation für Welten, Spieler und Regionen vereinheitlicht. Die Initialisierung beim Login benötigt keinen Zugriff auf eine noch nicht zugewiesene Spielerregion; während des Tickens bleibt der regionale Zeitstand maßgeblich.
- Euklidische Chunk-Suchreihenfolge, IntegerProperty-Grenzprüfung bei einem Minimum größer null und die Z-Koordinate in der Chunk-Unload-Diagnose korrigiert.
- Unnötiges Verdrängen bereits geöffneter Regiondateien aus einem vollen Cache beim Lesen einer nicht vorhandenen Datei verhindert.

### Plugin-Events und regionsichere Interaktionen

- Portalinteraktionen im Zuschauermodus auch ohne Inventarmenü zugelassen und an Tesseras asynchronen Portalpfad angebunden. Bestehende Portal-Events und die Verarbeitung in der Zielregion erhalten.
- Nach Kamera- und Plugin-Events Welt, Entfernungsstatus und Regionszuständigkeit erneut geprüft. Abgebrochene Kamerawechsel führen nicht zur anschließenden Portalteleportation.
- Beim Platzieren von Kissen ein abbrechbares `EntityPlaceEvent` vor Spawn und Itemverbrauch ergänzt. Nach dem Event wird die Zuständigkeit für den Zielchunk erneut geprüft.
- Beim Schieben von Schwefelwürfeln durch Spieler das `EntityCollideWithEntityEvent` berücksichtigt. Ein Abbruch verhindert die anschließende Bewegung und den Kontaktschaden dieses Pfads.
- Regionsbesitz und Weltzugehörigkeit von Schwefelwürfel, Spieler und dessen Fahrzeug vor der Verarbeitung und nach dem Plugin-Event abgesichert, ohne synchrone Cross-Region-Aufrufe einzuführen.
- Den Zielauswahlgrund alarmierter Bienen und Pandas auf `TARGET_ATTACKED_NEARBY_ENTITY` korrigiert.

### Schildtexte und API-Kompatibilität

- Schildtext-Builder auf veränderbare, unabhängige Listen umgestellt und beim Erzeugen einer Komponente auf vier gültige Zeilen aufgefüllt.
- Gegenseitige Änderungen zwischen Buildern, bestehenden Schildtexten und bereits erzeugten Komponenten verhindert. Ersetzte Zeilen aktualisieren auch ihre gefilterte Darstellung; unveränderte gefilterte Zeilen bleiben erhalten.
- Bestehende `SignText`-Methodensignaturen einschließlich `signText(List)`, `addLine`, `filteredLines` und vorhandener Bridge-Methoden beibehalten.
- Vanilla-Biomprovider ohne gemeinsam genutzten veränderlichen Sampler-Cache angebunden, damit parallele Generatorabfragen diesen Cache nicht teilen.
- Brenndauer- und Kompostierabfragen der ItemType-API vom Zugriff auf eine beliebige geladene Welt entkoppelt. Konstante Datenpaket-Werte erhalten und die Wahrscheinlichkeitsberechnung gewichteter Kompostierverteilungen korrigiert.
- Material-Tags für Pilze und Erze an die Vanilla-Tags angebunden sowie färbbare Woll- und Betonvarianten und Kissen ergänzt. Bisher enthaltene normale Betonblöcke bleiben weiterhin enthalten.

### Patchpflege und Buildstand

- Allgemeine Minecraft-Korrekturen im Sinopia-Patch `0044` und die ergänzenden Tessera-Regionsanpassungen in eigenen Minecraft-Patches `0040` bis `0042` abgelegt.
- Den Folia-Basispatch an die aktualisierte Sinopia-Basis angepasst und zusätzliche Regionsprüfungen als Testpatch `0031` aufgenommen.
- Buildnummer auf `006-alpha` angehoben und README, Build-Workflow sowie Backport-Dokumentation aktualisiert.
- Minecraft `26.3`, Mache `26.3+build.1`, Java 25, Gradle `9.4.1` und Plugin-API-Version `26.3` beibehalten.

### Automatisierte Prüfung

- Alle Patchschichten gemeinsam erfolgreich angewendet und den vollständigen `buildTessera`-Durchlauf einschließlich Kompilierung, Tests, Checkstyle, Bad-Call-Prüfungen und Paperclip-JAR-Erstellung erfolgreich abgeschlossen.
- 47 neue Regressionstestfälle für Metadaten, Schildtexte, Events, Blockabbau, Portalverarbeitung, Regionszuständigkeit, Suchreihenfolge und Dateicache ergänzt.
- Server-Testsuite mit `10.012` erfassten Testfällen ohne Fehler oder Fehlschläge geprüft; `87` Fälle wurden übersprungen. API-Tests mit `529` erfassten Fällen ohne Fehler oder Fehlschläge geprüft; `2` Fälle wurden übersprungen.

Der erfolgreiche Prüf- und Buildlauf enthielt bereits diese Codeänderungen und
erfolgte noch unter `005-alpha`, vor der anschließenden Anhebung der Buildnummer.
Für die Dokumentationsanpassung wurde kein erneuter Build ausgeführt. Die
automatisierten Regionsprüfungen verwenden kontrollierte Mocks; ein Live-Test
mit Spielern, Plugins und Dimensionswechseln war nicht Bestandteil der Prüfung.
Tessera bleibt ein Alpha-Build.
