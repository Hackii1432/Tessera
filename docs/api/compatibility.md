---
title: "Paper, Folia, Sinopia und Tessera unterscheiden"
description: "Öffentliche Verträge, Plattformadapter und Grenzen von Binär- sowie Threadkompatibilität."
navTitle: "Kompatibilität"
order: 150
updated: 2026-10-08
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

`api-version: '26.3'` ist die Metadatenversion, `26.3.build.018-beta` die aktuelle
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

## Blocktyp-Instrument und Blockzustände

Die geerbte Paper-Ergänzung `org.bukkit.block.BlockType#getInstrument(): Instrument`
liefert ab Build 016 das Instrument des nativen Default-Blockzustands. Die Methode
hat keine Parameter, liefert einen nicht-null `org.bukkit.Instrument` und mutiert
keine Welt. Sie ist eine statische Typabfrage, keine Abfrage eines platzierten
Notenblocks oder seiner Nachbarn. Das gespeicherte Instrument eines Notenblocks
liefert `org.bukkit.block.data.type.NoteBlock#getInstrument()`; Block- und
Nachbarabfragen bleiben ownergebunden. Der Wert benötigt keinen Scheduler,
keine Lebenszyklus-Bereinigung und bleibt durch Plugin-Disable unverändert.

```java
org.bukkit.Instrument instrument = org.bukkit.block.BlockType.GOLD_BLOCK.getInstrument();
// BELL; this does not read a placed world block.
```

`Block#getState(boolean useSnapshot)` behält seine öffentliche Signatur. `true`
erzeugt bei Blockentities eine isolierte Zustandskopie, `false` einen lebenden
Zustand. Intern wird die Auswahl nun explizit durchgereicht, ohne globalen oder
ThreadLocal-Snapshot-Schalter. Lesen und Mutieren lebender Weltzustände bleiben
an den Regionsbesitzer gebunden; eine Snapshot-Kopie erlaubt keinen beliebigen
Off-thread-Zugriff auf Welt oder Blockinventory. `update` gehört wieder zum Owner.

## Chat-Renderer

`ChatRenderer.defaultRenderer()` liefert den gemeinsamen, zustandslosen
Paper-Standardrenderer. Explizites erneutes Setzen dieses Renderers verändert
das Chatformat nicht. `ChatRenderer.viewerUnaware(...)` speichert keine fertige
Nachricht mehr im Renderer: Ein wiederverwendeter Renderer muss für jede Nachricht
mit deren aktuellem Spieler, Anzeigenamen und Inhalt rendern. Asynchrone
Chat-Callbacks berechtigen weiterhin nicht zum Zugriff auf fremde Entity-Zustände.
