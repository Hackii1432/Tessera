---
title: "Regionale TPS abfragen"
description: "TPS-Abfragen, Messfenster und Grenzen öffentlicher Regionsdiagnostik."
navTitle: "Regionale TPS"
order: 110
updated: 2026-09-28
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
