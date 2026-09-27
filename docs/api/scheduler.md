---
title: "Scheduler und Threadkontexte"
description: "Aufgaben, Verzögerungen, Retirement und Plugin-Disable unter Folias Regionsmodell."
navTitle: "Scheduler"
order: 30
updated: 2026-09-28
minecraftVersion: "26.3"
badge: "Referenz"
---

Die Scheduler sind geerbte Paper-/Folia-APIs. Tessera ergänzt Lifecycle-Zulassungsprüfungen; sie sind keine Umgehung von Snapshot- oder Restore-Schranken.

Die Scheduler befinden sich in:

```text
io.papermc.paper.threadedregions.scheduler
```

## RegionScheduler

Zugriff:

```java
RegionScheduler scheduler = Bukkit.getRegionScheduler();
```

Wichtige Methoden:

```java
void execute(Plugin plugin, Location location, Runnable run);

ScheduledTask run(
    Plugin plugin,
    Location location,
    Consumer<ScheduledTask> task
);

ScheduledTask runDelayed(
    Plugin plugin,
    Location location,
    Consumer<ScheduledTask> task,
    long delayTicks
);

ScheduledTask runAtFixedRate(
    Plugin plugin,
    Location location,
    Consumer<ScheduledTask> task,
    long initialDelayTicks,
    long periodTicks
);
```

Alle Methoden existieren außerdem mit `World`, `chunkX` und `chunkZ`.

Beispiel:

```java
Location location = new Location(world, 100, 64, 200);

Bukkit.getRegionScheduler().execute(plugin, location, () -> {
    location.getBlock().setType(Material.DIAMOND_BLOCK);
});
```

Verwende den RegionScheduler nicht für Spieler oder andere bewegliche
Entities. Eine Entity kann die Region wechseln, bevor die Aufgabe ausgeführt
wird.

## EntityScheduler

Zugriff:

```java
EntityScheduler scheduler = entity.getScheduler();
```

Wichtige Methoden:

```java
boolean execute(
    Plugin plugin,
    Runnable run,
    @Nullable Runnable retired,
    long delayTicks
);

@Nullable ScheduledTask run(
    Plugin plugin,
    Consumer<ScheduledTask> task,
    @Nullable Runnable retired
);

@Nullable ScheduledTask runDelayed(
    Plugin plugin,
    Consumer<ScheduledTask> task,
    @Nullable Runnable retired,
    long delayTicks
);

@Nullable ScheduledTask runAtFixedRate(
    Plugin plugin,
    Consumer<ScheduledTask> task,
    @Nullable Runnable retired,
    long initialDelayTicks,
    long periodTicks
);
```

Der Scheduler folgt der Entity bei Regions- und Weltwechseln. Bei Retirement nach erfolgreicher Einplanung kann der optionale `retired`-Callback laufen. War der Scheduler bereits stillgelegt, liefern `execute` **false** beziehungsweise die `run`-Varianten **null**; dann läuft keiner der Callbacks. Bei Plugin-Disable oder explizitem Abbruch gibt es ebenfalls keine Garantie eines abschließenden Callbacks.

```java
player.getScheduler().execute(
    plugin,
    () -> player.sendMessage(Component.text("Regionsicher ausgeführt")),
    () -> plugin.getLogger().fine("Spieler war nicht mehr verfügbar"),
    1L
);
```

Der `retired`-Callback läuft in einem kritischen Kontext. Er darf keine Chunks
laden, Entities entfernen oder andere umfangreiche Weltoperationen starten.

## GlobalRegionScheduler

Zugriff:

```java
GlobalRegionScheduler scheduler = Bukkit.getGlobalRegionScheduler();
```

Methoden:

```java
void execute(Plugin plugin, Runnable run);
ScheduledTask run(Plugin plugin, Consumer<ScheduledTask> task);
ScheduledTask runDelayed(
    Plugin plugin,
    Consumer<ScheduledTask> task,
    long delayTicks
);
ScheduledTask runAtFixedRate(
    Plugin plugin,
    Consumer<ScheduledTask> task,
    long initialDelayTicks,
    long periodTicks
);
void cancelTasks(Plugin plugin);
```

Der Global Region Scheduler ist für globale Serverzustände zuständig, unter
anderem Weltzeit, Wetter, Schlaflogik und Konsolenbefehle. Er ist kein Ersatz
für Regions- oder Entity-Scheduler.

## AsyncScheduler

Zugriff:

```java
AsyncScheduler scheduler = Bukkit.getAsyncScheduler();
```

Methoden:

```java
ScheduledTask runNow(
    Plugin plugin,
    Consumer<ScheduledTask> task
);

ScheduledTask runDelayed(
    Plugin plugin,
    Consumer<ScheduledTask> task,
    long delay,
    TimeUnit unit
);

ScheduledTask runAtFixedRate(
    Plugin plugin,
    Consumer<ScheduledTask> task,
    long initialDelay,
    long period,
    TimeUnit unit
);

void cancelTasks(Plugin plugin);
```

Asynchrone Tasks dürfen keine regionsgebundenen Bukkit-Objekte ohne Übergabe an
den richtigen Owner verändern.

## ScheduledTask

Ein `ScheduledTask` bietet:

```java
Plugin getOwningPlugin();
boolean isRepeatingTask();
CancelledState cancel();
ExecutionState getExecutionState();
boolean isCancelled();
```

Ausführungszustände:

```text
IDLE
RUNNING
FINISHED
CANCELLED
CANCELLED_RUNNING
```

Das Abbrechen einer gerade laufenden Aufgabe unterbricht deren Java-Code nicht.
Es verhindert nur noch nicht begonnene beziehungsweise zukünftige
Wiederholungen.

## Parameter und Fehler

`plugin` besitzt die Aufgabe; `task`/`run` ist die kurze auszuführende Arbeit. Region-/Entity-/Global-Verzögerungen sind Ticks ihres Schedulers, keine garantierte Wanduhrzeit. `runDelayed` benötigt positive Tick-Verzögerungen, `runAtFixedRate` zusätzlich eine positive Periode. Nur `EntityScheduler.execute` behandelt Werte unter 1 als 1. Async-Verzögerungen sind nichtnegativ, Async-Perioden positiv und verwenden die angegebene `TimeUnit`.

`null`, ungültige Intervalle, ein deaktiviertes Plugin oder eine durch Lifecycle-Arbeit gesperrte Welt können die Einplanung synchron ablehnen. `IllegalPluginAccessException`, `IllegalArgumentException`, `NullPointerException` und Zustandsfehler nicht mit erfolgreicher Einplanung verwechseln. Task-Ausnahmen werden protokolliert; sie schließen eigene Plugin-Futures nicht automatisch ab.

## Abbruch und Plugin-Disable

`cancel()` liefert `CANCELLED_BY_CALLER`, `CANCELLED_ALREADY`, `RUNNING`, `ALREADY_EXECUTED`, `NEXT_RUNS_CANCELLED` oder `NEXT_RUNS_CANCELLED_ALREADY`. Bereits laufender Java-Code wird nicht unterbrochen. Region-/Entity-Scheduler besitzen keine öffentliche `cancelTasks(Plugin)`-Methode: ihre `ScheduledTask`-Handles selbst verwalten. Async und Global bieten diesen Einstieg.

Beim Disable Produzenten stoppen, Aktivitätsflag setzen, Handles abbrechen und eigene wartende Futures selbst abschließen. Nicht erst aus `onDisable()` neue Entity-Tasks mit dem deaktivierten Plugin einplanen. `retired` ist kein zuverlässiger Disable-Finalizer. Ein vollständiges Muster steht unter [Fehlerbehandlung](error-handling.md#owner-uebergabe).

## Region-Lebenszyklus

Ortsaufgaben bleiben beim jeweiligen Chunk und folgen bei Split/Merge dessen Besitzer, nicht einer Entity. Der Scheduler ist kein Chunk-/World-Load-Future. Tickpausen und Tick-Freeze können normale Plugin-Ticktasks verzögern. Die internen Restore-Queues sind **keine** öffentliche Plugin-Scheduler-API.
