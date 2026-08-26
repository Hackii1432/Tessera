---
version: 0.1.4
title: Vanilla-Entity-Selektoren für Enderdrachen korrigiert
description: Regionssichere globale Entity-Selektoren für Vanilla-Befehle
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
releaseUrl: https://github.com/Hackii1432/Tessera
downloadUrl: https://home.mosaikdev.com
---

## Geändert

- Unbeschränkte Vanilla-Entity-Selektoren verwenden wieder den nebenläufigen Entity-Index von Moonrise.
- Befehle wie `kill @e[type=minecraft:ender_dragon]` funktionieren wieder wie in Vanilla.
- Ausgewählte Entities werden weiterhin ausschließlich auf ihrem zuständigen Region-Thread verändert.
- Die direkte Auswahl einer geladenen Entity über ihre UUID bleibt unterstützt.
- Der Schutz vor räumlichen Abfragen über fremde oder mehrere Regionen bleibt erhalten.
- Regressionstests für unbeschränkte Selektoren, direkte UUID-Auswahl und regionsübergreifende Suchbereiche wurden ergänzt.
- Der Enderdrache wurde in einer laufenden Tessera-Testinstanz erfolgreich über den Vanilla-Typ-Selektor getötet, ohne Thread-Check-Exception oder Stacktrace.
