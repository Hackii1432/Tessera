---
title: "Threads und Regionsbesitz"
description: "Threadkontexte, Ownership-Prüfungen und Grenzen bei Regionswechseln."
navTitle: "Ownership"
order: 20
updated: 2026-09-28
minecraftVersion: "26.3"
badge: "Referenz"
---

Tessera verwendet Folias Regionsmodell. Eine Objekt-Referenz ist keine Erlaubnis, ihren veränderlichen Zustand von einem fremden Thread zu lesen.

## Geeigneter Kontext nach Aufgabe

| Aufgabe | Richtiger Kontext |
| --- | --- |
| Block, Chunk oder ortsgebundene Weltlogik | `RegionScheduler` |
| Spieler oder bewegliche Entity | `Entity#getScheduler()` |
| Zeit, Wetter, Gamerules, globale Serverdaten | `GlobalRegionScheduler` |
| Datei-, HTTP- oder Datenbank-I/O | `AsyncScheduler` oder eigener Executor |
| Runtime-Welt erstellen, laden, klonen, entladen | `RuntimeWorldManager` von jedem Thread aus |
| Spieler teleportieren | `Entity#teleportAsync(...)` |

Ein Objekt aus einer fremden Region darf nicht nur deshalb gelesen werden, weil
eine Java-Referenz darauf vorhanden ist. Besonders bei Entities ist bereits das
Auslesen einer Position außerhalb des Owners nicht zulässig.

## Ownership prüfen

`Bukkit` und `Server` stellen folgende Prüfungen bereit:

```java
boolean isOwnedByCurrentRegion(World world, Position position);
boolean isOwnedByCurrentRegion(World world, Position position, int radius);
boolean isOwnedByCurrentRegion(Location location);
boolean isOwnedByCurrentRegion(Location location, int radius);
boolean isOwnedByCurrentRegion(Block block);
boolean isOwnedByCurrentRegion(World world, int chunkX, int chunkZ);
boolean isOwnedByCurrentRegion(
    World world,
    int chunkX,
    int chunkZ,
    int radius
);
boolean isOwnedByCurrentRegion(
    World world,
    int minChunkX,
    int minChunkZ,
    int maxChunkX,
    int maxChunkZ
);
boolean isOwnedByCurrentRegion(Entity entity);
boolean isGlobalTickThread();
```

Der Radius ist eine quadratische Chunkreichweite beziehungsweise
Chebyshev-Distanz und muss mindestens `0` sein.

Für Entities ist ausschließlich `isOwnedByCurrentRegion(Entity)` geeignet.
Die Entity-Position darf nicht zuerst von einem fremden Thread gelesen werden,
um anschließend eine ortsbasierte Prüfung durchzuführen.



## Lebensdauer einer Prüfung

Die Prüfungen liefern `boolean`, übertragen keine Arbeit und laden keine Chunks. `Position` stammt aus `io.papermc.paper.math`; Chunkkoordinaten sind nicht Blockkoordinaten. Locations benötigen eine Welt. Ungültige Argumente können unmittelbar Ausnahmen auslösen. `true` ist keine dauerhafte Sperre: Nach asynchronen Schritten oder Plugin-Callbacks Welt, Entity und Zuständigkeit erneut prüfen. Auch der Global-Thread besitzt nicht automatisch fremde Entities.

Future-Callbacks haben keinen allgemein sicheren Bukkit-Kontext. Folgearbeit an den [EntityScheduler](scheduler.md#entityscheduler) oder den Scheduler der Zielposition senden. Über Regionsgrenzen nur unveränderliche Daten austauschen; keine gegenseitigen `join()`-/`get()`-Warteketten.

## Initialisierung

`RegionizedServerInitEvent` ist eine geerbte API vor parallelem Regionsticken, keine Dauerfreigabe für Weltzugriffe oder ein verlässlicher Plattformdetektor. Siehe [Events](events.md#serverinitialisierung) und [Capabilities](capabilities.md).

## Teleportation

`Entity#teleportAsync(Location)` liefert `CompletableFuture<Boolean>`; auch der Overload mit `PlayerTeleportEvent.TeleportCause` existiert. `true` meldet erfolgreichen Transfer, `false` eine abgelehnte oder nicht mehr durchführbare Übertragung. Ungültige/null/nichtendliche Ziele und gesperrte Zielwelten können synchron abgewiesen werden. In Tessera wird ein Off-Owner-Aufruf intern eingeplant. Ein Ziel trotzdem nicht durch fremdes `player.getLocation()` ermitteln. Ziel-Locations kopieren und Entity-Arbeit nach Abschluss erneut über den EntityScheduler ausführen. Synchrones `teleport(...)` ist kein Cross-Region-Ersatz.
