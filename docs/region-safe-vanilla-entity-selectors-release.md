---
version: 0.1.3
title: Regionssichere Vanilla-Entity-Selektoren
description: Regionsrouting für Vanilla-Entity-Selektoren aus Serverkonsole und RCON
date: 2026-08-26
minecraftVersion: "26.2"
status: stable
breaking: false
tags:
  - Tessera
  - Commands
  - Entity-Selektoren
  - Region-Threading
releaseUrl: https://github.com/Hackii1432/Tessera
downloadUrl: https://home.mosaikdev.com
---

## Geändert

- Vanilla-Befehle mit `execute in <dimension> positioned <x> <y> <z>` und räumlich begrenzten Entity-Selektoren werden auf der zuständigen Region ausgeführt.
- Regionsfremde Aufrufe von `Level#getEntities` werden verhindert.
- Regionsübergreifende und unbeschränkte Entity-Suchen werden mit einer verständlichen Fehlermeldung abgelehnt.
- Befehle für ungeladene Zielregionen werden sicher und ohne internen Thread-Check abgelehnt.
- RCON wartet auf regionsgebundene Befehle, ohne den Global-Region-Thread zu blockieren.
- Console- und RCON-Regressionstests für Overworld, Nether und End wurden ergänzt.
- `if entity`, `as`, `at`, `data get entity` und `kill` werden durch die neuen Regressionstests abgedeckt.
