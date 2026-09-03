---
version: 0.1.6
title: Regionsichere Block-Nachbearbeitung für Laufzeit-Strukturen
description: Vanilla-konforme Shape- und Connection-Nachbearbeitung für zur Laufzeit erzeugte Strukturen
date: 2026-09-03
minecraftVersion: "26.2"
status: stable
breaking: false
tags:
  - Tessera
  - Strukturen
  - Blockzustände
  - Region-Threading
  - Strongholds
  - World Generation
releaseUrl: https://github.com/Hackii1432/Tessera
downloadUrl: https://home.mosaikdev.com
---

## Geändert

- Bereits geladene `LevelChunk`s unterstützen nun die von Vanilla vorgesehene Nachbearbeitungsmarkierung für zur Laufzeit erzeugte Strukturen.
- Aufrufe von `StructurePiece#postProcess(...)` können Zäune, Iron Bars, Türen, Treppen und vergleichbare zustandsabhängige Blöcke wieder zur Shape- und Connection-Nachbearbeitung vormerken.
- Markierungen werden ausschließlich auf dem Regions-Thread angenommen, dem der betroffene Chunk gehört.
- Nach dem Platzieren einer Struktur wird pro Chunk höchstens ein zusammengefasster Regions-Task für die Block-Nachbearbeitung eingeplant.
- Die eigentliche Aktualisierung verwendet weiterhin Vanillas `Block.updateFromNeighbourShapes(...)` sowie das originale Verhalten für Flüssigkeiten und `LiquidBlock`s.
- Benachbarte Chunks werden vor der Aktualisierung auf Ladezustand und Regionsbesitz geprüft. Es werden keine fremden Regionsdaten verändert und keine Chunks synchron geladen.
- Kann eine Position noch nicht sicher verarbeitet werden, bleibt sie für einen späteren Nachbearbeitungsdurchlauf im Chunk gespeichert.
- Unsichere Aufrufe vom Global-Thread, aus einer fremden Region oder für einen nicht mehr geladenen Chunk werden abgelehnt. Die Diagnose ist auf höchstens eine zusammengefasste Warnung innerhalb von 30 Sekunden gedrosselt.
- Die bisherige Warnungsflut `Trying to mark a block for post processing ... but this operation is not supported` bei legitimen Aufrufen auf dem zuständigen Regions-Thread entfällt.
- Das Verhalten der normalen Weltgenerierung sowie die bestehende Paper-/Bukkit-kompatible Runtime-World-API bleiben unverändert.
- MCC-Code wurde für diese Änderung nicht angepasst.

## Validierung

- Der vollständige Minecraft-Patchsatz mit 25 Tessera-Patches wurde ohne Konflikte neu angewendet.
- Der Tessera-Server wurde erfolgreich mit Java 25 kompiliert.
- Die Server-Test-Suite wurde mit 9.199 Tests ohne Fehler abgeschlossen; 22 Tests wurden übersprungen.
- Der Paperclip-/Bundler-Build wurde erfolgreich erzeugt.
