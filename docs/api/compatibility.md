---
title: "Paper, Folia, Sinopia und Tessera unterscheiden"
description: "Öffentliche Verträge, Plattformadapter und Grenzen von Binär- sowie Threadkompatibilität."
navTitle: "Kompatibilität"
order: 150
updated: 2026-09-28
minecraftVersion: "26.3"
badge: "Hinweise"
---

Tessera behält Paper-/Folia-Paketnamen für Kompatibilität bei, führt aber nicht
sämtliche API-Aufrufe auf einem gemeinsamen Hauptthread aus. Ein Plugin braucht
passende API-Version **und** passende Ownership-Regeln.

## Erweiterungen getrennt anbinden

| Plattformpfad | Verhalten des Plugins |
| --- | --- |
| Tessera-Capability vorhanden | Zugehörigen Tessera-Adapter laden und Ergebnisse prüfen |
| Keine Tessera-Capability | Keine Tessera-Klasse verlinken; betreffendes Feature deaktivieren oder explizit geprüften Plattformadapter verwenden |
| Klassischer Paper-Adapter | Dynamische Weltverwaltung im dort erlaubten Serverkontext; nicht ungeprüft auf Tessera übertragen |
| Folia ohne Runtime-Erweiterung | Kein Tessera-Runtime-Lifecycle; gegebenenfalls vorbereitete/in-place Welten verwenden |

Eine fehlende Sidebar-Capability muss nicht BossBars, ActionBars oder die gesamte
Pluginlogik deaktivieren. Jede dieser geerbten Funktionen benötigt aber weiterhin
ihren eigenen zulässigen Threadkontext. Die reine Existenz von Scheduler- oder
`RegionizedServerInitEvent`-Klassen unterscheidet Paper und Folia nicht zuverlässig.

## Versionen und interne Zugriffe

`api-version: '26.3'` ist die Metadatenversion, `26.3.build.011-beta` die aktuelle
Tessera-Maven-Version. Beides ersetzt keine Laufzeit-Capability. Sinopia ist in
demselben Server enthalten; es gibt keinen separaten Sinopia-Plugin-Lader.

NMS, Reflection in CraftBukkit, konkrete Netzwerkpakete und private Scheduler-
Implementierungen sind keine stabile öffentliche Schnittstelle. Beispielsweise
dürfen frühere Partikel-Paketgetter aus einem RC-Build nicht als vorhandene
Methoden einer anderen Version angenommen werden. Öffentliche Partikel-APIs
verwenden oder den konkreten intern gebundenen Adapter separat testen.

## Kein allgemeines Synchronitätsversprechen

Ein geerbtes `void` kann in Tessera eine explizit dokumentierte Owner-Weiterleitung
verwenden, etwa `Player#setScoreboard`. Daraus folgt keine allgemeine automatische
Weiterleitung aller Bukkit-Methoden. `PlayerPostEffects` ist synchron ownergebunden,
Welt-Lifecycle liefert Futures, Events haben jeweils ihren tatsächlichen Kontext.
Siehe [Ownership](ownership.md), [Scoreboards](scoreboards.md) und
[Spieler-Effekte](player-effects.md).

Die Wiederherstellung mit Vertrag 1 benötigt keinen neuen MCC-API-Pfad, aber einen
gemeinsamen Test des tatsächlichen Plugin-Stacks. Ein erzwungener Reconnect ist
kein erfolgreicher Seamless-Load.
