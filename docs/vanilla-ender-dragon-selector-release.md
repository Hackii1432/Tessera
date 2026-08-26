---
version: 0.1.4
title: Regionssichere Entity-, Redstone- und Game-Event-Verarbeitung
description: Verbesserte Regionsprüfung für Vanilla-Befehle, Enderdrachen, Redstone und Vibrationen
date: 2026-08-26
minecraftVersion: "26.2"
status: stable
breaking: false
tags:
  - Tessera
  - Commands
  - Entity-Selektoren
  - Region-Threading
  - Enderdrache
  - Redstone
  - Game Events
  - Sculk
releaseUrl: https://github.com/Hackii1432/Tessera
downloadUrl: https://home.mosaikdev.com
---

## Geändert

- Unbeschränkte Vanilla-Entity-Selektoren verwenden wieder den nebenläufigen Entity-Index von Moonrise.
- Befehle wie `kill @e[type=minecraft:ender_dragon]` funktionieren wieder wie in Vanilla.
- Ausgewählte Entities werden weiterhin ausschließlich auf ihrem zuständigen Region-Thread verändert.
- Die direkte Auswahl einer geladenen Entity über ihre UUID bleibt unterstützt.
- Der Schutz vor räumlichen Abfragen über fremde oder mehrere Regionen bleibt erhalten.
- Die Kollisionsabfragen des Enderdrachens berücksichtigen ausschließlich Entities der aktuell zuständigen Region.
- Veraltete Multipart-Hitboxen des Enderdrachens am Weltursprung verursachen beim ersten Tick keine Thread-Check-Exception mehr.
- Eigencraft-Redstone führt keine Blockaktualisierungen mehr außerhalb der aktuell zuständigen Region aus.
- Game Events und Vibrationen werden nicht mehr direkt an Listener einer fremden Region übergeben.
- Regionsfremde Redstone- und Game-Event-Aktualisierungen werden sicher übersprungen, anstatt parallele Änderungen an fremden Regionsdaten vorzunehmen.
- Regressionstests für unbeschränkte Selektoren, direkte UUID-Auswahl und regionsübergreifende Suchbereiche wurden ergänzt.
- Der Enderdrache wurde in einer laufenden Tessera-Testinstanz erfolgreich über den Vanilla-Typ-Selektor getötet, ohne Thread-Check-Exception oder Stacktrace.
- Der vollständige Tessera-Build einschließlich Checkstyle und Tests wurde erfolgreich ausgeführt.
