---
title: "Tessera API für Pluginentwickler"
description: "Öffentliche Erweiterungen und regionsichere Nutzung der integrierten Paper-/Folia-Basis unter Minecraft 26.3."
navTitle: "Einstieg"
order: 0
updated: 2026-09-28
minecraftVersion: "26.3"
badge: "Referenz"
---

Diese Referenz beschreibt den geprüften Quellstand von Tessera **26.3 Build
011-beta**, Java **25**, auf `ver/26.3.x`. Sie ergänzt die geerbte Bukkit-,
Paper-, Folia- und Adventure-API, ersetzt aber nicht deren gesamte Javadoc.

## Herkunft der Schnittstellen

**Sinopia** ist die integrierte, Paper-abgeleitete Basis, keine separate
Plugin-Abhängigkeit oder zweite Laufzeit. Darauf liegen Folias Regionsmodell
und Tesseras eigene Patches. Beibehaltene `io.papermc.paper`-/`org.bukkit`-Pakete
beweisen deshalb allein weder öffentliche Tessera-Erweiterung noch allgemeine
Thread-Sicherheit.

| Oberfläche | Herkunft und Rolle |
| --- | --- |
| `TesseraCapabilities`, `RuntimeWorldManager`, Snapshot- und Restore-Verträge | Öffentliche Tessera-Erweiterungen |
| Region-/Entity-/Global-/AsyncScheduler, Ownership-Prüfungen, regionale TPS | Geerbte Paper-/Folia-Schnittstellen und Folia-Schicht |
| Scoreboards | Bukkit-Oberfläche mit Tesseras regionsicherem Modell |
| `GameRules.ALLOW_EYES_OF_ENDER_USE` | Tessera-Erweiterung der Bukkit-Gamerules |
| Portal-/Respawn-Events | Geerbte Events; Tessera bindet sie in regionsichere Transferpfade ein |
| `PlayerPostEffects` | Paper-API über Sinopia, mit Tessera-Ownership-Vertrag |

CraftBukkit-/NMS-Klassen, interne Save-Queues, Region-IDs und Restore-Schreibschranken
sind keine zusätzliche öffentliche Plugin-API. Der native Restore wird nur durch
explizite Transaktionsaufrufe gestartet, nicht automatisch für jeden SMP.

## Themen

- [Projekt-Setup](project-setup.md) und [Capability-Erkennung](capabilities.md)
- [Thread- und Ownership-Modell](ownership.md) sowie [Scheduler](scheduler.md)
- [Runtime-Welten](runtime-worlds.md), [Templates](world-cloning.md),
  [Snapshots](world-snapshots.md) und [Unload](world-unloading.md)
- [Nativer Spieler-Restore](player-restore.md)
- [Scoreboards und Entity-Tags](scoreboards.md)
- [Events](events.md), [regionale TPS](region-tps.md), [Gamerules](gamerules.md)
- [Spieler-Post-Effects](player-effects.md) und [Konsole/RCON](commands.md)
- [Mob-, SulfurCube- und Poplar-Kompatibilität](entity-and-tree-compatibility.md)
- [Plattformkompatibilität](compatibility.md) und [Fehlerbehandlung](error-handling.md)

## Grundregeln für Beispiele

Welt-/Blockarbeit gehört zum Ort, Entity-Arbeit zum aktuellen Entity-Besitzer,
globale Einstellungen zum Global-Thread und blockierende I/O auf einen eigenen
Executor oder AsyncScheduler. Kein `get()`, `join()` oder `sleep()` auf Tick- oder
Netzwerkthreads. Ein Future-Callback besitzt nicht automatisch eine Region.

Signaturblöcke beschreiben existierende Methoden; kürzere Codeblöcke sind Ausschnitte
mit den im Text genannten Variablen. Vollständige Beispielklassen sind gesondert
gekennzeichnet und werden gegen die gebaute Tessera-API kompiliert. Sie sind
Plugin-Beispiele, keine neuen Server-APIs. Fehler- und Disable-Regeln bleiben auch
bei der Übernahme einzelner Ausschnitte verbindlich.

## Stand und Prüfgrenzen

Seit Build 010 besteht der native Spieler-Restore-Vertrag **1**. Server-/Protokolltests
belegen dessen native Abläufe; die gemeinsame MCC-/MVE-/TAB-/LuckPerms-Abnahme
bleibt separat. Die Dokumentationsprüfung startet keine neuen Spielserver und
verspricht keine allgemeine Plugin- oder Vanilla-Kompatibilität.

Ausführliche, datierte Nachweise bleiben in der
[Repository-Dokumentation](https://github.com/Hackii1432/Tessera/tree/ver/26.3.x/docs).
