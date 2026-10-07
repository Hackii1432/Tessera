---
title: "Regionale TPS abfragen"
description: "TPS-Abfragen, Messfenster und Grenzen öffentlicher Regionsdiagnostik."
navTitle: "Regionale TPS"
order: 110
updated: 2026-10-07
minecraftVersion: "26.3"
badge: "Referenz"
---

`getRegionTPS` gehört zur Folia-Schicht und steht auf `Bukkit` sowie `Server` bereit. Es ist keine API zur Auflistung aller Regionen.

Tessera kann die Tickrate einer konkreten Region liefern:

```java
@Nullable double[] getRegionTPS(Location location);
@Nullable double[] getRegionTPS(Chunk chunk);
@Nullable double[] getRegionTPS(World world, int chunkX, int chunkZ);
```

Die Signaturen beschreiben Instanzmethoden auf `Server` und entsprechende
statische Methoden auf `Bukkit`. Beispiele:

```java
double[] atLocation = Bukkit.getRegionTPS(location);
double[] atChunk = Bukkit.getRegionTPS(chunk);
double[] atCoordinates = Bukkit.getRegionTPS(world, chunkX, chunkZ);
```

Existiert für den angegebenen Ort momentan keine Region, ist das Ergebnis
`null`.

Das Array enthält:

| Index | Zeitraum |
| --- | --- |
| `0` | 5 Sekunden |
| `1` | 15 Sekunden |
| `2` | 1 Minute |
| `3` | 5 Minuten |
| `4` | 15 Minuten |

```java
double[] tps = Bukkit.getRegionTPS(player.getLocation());
if (tps != null) {
    player.sendMessage(Component.text("Region TPS: " + tps[0]));
}
```

Rufe bei einer Entity den Code zuerst auf ihrem Entity-Scheduler auf, bevor du
ihre Position liest.

## Eingaben und Lebenszyklus

Locations/Chunks/Welten dürfen nicht `null` sein; sonst `IllegalArgumentException`. Die Koordinatenvariante verwendet Chunkkoordinaten. Die Abfrage sucht synchronisiert die aktuelle Region ohne Chunk-Nachladen und liefert ein neues Array. Messfenster können während der Aufwärmphase unvollständig sein. Split/Merge/Transfer ändern Zuordnungen; vorherige Ergebnisse sind nicht dauerhaft gültig.

Die Abfrage selbst ist von jedem Thread erlaubt: Regionssuche und Reportzugriff
sind synchronisiert. Das beschafft keine Berechtigung, ihre Eingaben durch Lesen
fremder Entity-Positionen zu erzeugen. Vorab bekannte Welt-/Chunkkoordinaten oder
auf dem Entity-Owner erfasste Werte verwenden; eine veränderliche `Location`
nicht gleichzeitig auf einem anderen Thread umschreiben.

Das Array enthält TPS, keine MSPT oder individuelle Spielerlast. Der reichhaltigere `/tps`-Befehl verwendet interne Strukturen, die keine öffentliche Region-ID-/MSPT-/Spielerzuordnungs-API sind. Fehlende Werte nicht mit erfundenen 20 TPS ersetzen; keine zusätzliche Plugin-Tickmessung ist nötig.

## Utilisation in der Befehlsübersicht

`/tps`, `/tps list` und `/tps server [count]` zeigen die vorhandenen
15-Sekunden-Auslastungswerte aller aktuellen Regionen plus Global-Tick als Summe
und die maximal verfügbare Kapazität des Tick-Threadpools. Beispielwerte:

```text
Utilisation (15 s): 180.00% / 400.00% max · Tick threads: 4 · Partial samples
```

100 % entsprechen einem vollständig belegten Tick-Thread; vier verfügbare
Tick-Threads ergeben 400 % Kapazität. Dafür zählt der Scheduler seine aktuell
lebenden Tick-Threads, nicht die CPU-Kerne, die Anzahl der Regionen oder die
Chunk-Worker. Dies ist eine zeitbasierte Tick-Diagnose, keine Prozess-CPU-Messung
und keine Garantie, dass eine einzelne Region freie Kapazität anderer Threads
nutzen kann.

`Partial samples` bedeutet, dass mindestens ein Regions- oder Global-Report fehlt
oder ungültig ist. Ohne verwertbare Reports steht `No measurements yet` statt
einer erfundenen Nullauslastung; die bekannte Poolkapazität bleibt sichtbar.
Die angezeigte Regionsanzahl zu begrenzen ändert weder Summe noch Kapazität.
Freeze und Sprint behalten die vorhandenen Messwerte und ihre bisherigen
Statushinweise. Die Anzeige verwendet nur den bestehenden Befehls-Snapshot,
ohne zusätzliche Tasks, Tickmessungen oder fremde Entity-/Chunk-Zugriffe.
