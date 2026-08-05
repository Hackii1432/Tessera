---
title: Tessera API
description: Vollständige Plugin-Referenz für Tessera 26.2.x
---

# Tessera API

Diese Referenz beschreibt die öffentlichen Tessera-Erweiterungen für
Minecraft `26.2` sowie die für regionsichere Plugins relevanten Folia- und
Bukkit-Schnittstellen.

Tessera basiert auf Paper und Folia. Die normale Bukkit-, Paper- und
Adventure-API bleibt verfügbar, wird hier aber nicht vollständig wiederholt.
Diese Seite konzentriert sich auf Funktionen, deren Verwendung oder
Threadkontext sich unter Tessera von einem klassischen Single-Thread-Server
unterscheidet.

## Inhalt

- [Grundprinzipien](#grundprinzipien)
- [Projekt einrichten](#projekt-einrichten)
- [Capability-Erkennung](#capability-erkennung)
- [Thread- und Ownership-Modell](#thread--und-ownership-modell)
- [Scheduler](#scheduler)
- [Regionale TPS](#regionale-tps)
- [Runtime-World-API](#runtime-world-api)
- [Regionsichere Scoreboards](#regionsichere-scoreboards)
- [Entity-Scoreboard-Tags](#entity-scoreboard-tags)
- [Tessera-Gamerule](#tessera-gamerule)
- [Konsole und RCON](#konsole-und-rcon)
- [Paper-, Tessera- und Folia-Kompatibilität](#paper--tessera--und-folia-kompatibilität)
- [Fehlerbehandlung und Best Practices](#fehlerbehandlung-und-best-practices)
- [Paketübersicht](#paketübersicht)

## Grundprinzipien

Tessera besitzt keinen universellen Bukkit-Main-Thread für alle Welten.
Stattdessen werden weit voneinander entfernte Chunks in unabhängigen Regionen
auf mehreren Tick-Threads verarbeitet.

Für Plugins gelten deshalb vier Grundregeln:

1. Welt- und Blockzugriffe gehören auf den zuständigen Region-Thread.
2. Entity-Zugriffe gehören auf den Scheduler der jeweiligen Entity.
3. Globale Aufgaben gehören auf den Global Region Scheduler.
4. Datei-, Datenbank- und andere blockierende Arbeit gehört auf einen
   asynchronen Executor.

Ein Region- oder Entity-Thread darf niemals blockierend auf ein Future, Chunk-I/O
oder eine Weltoperation warten. Verwende `thenCompose`, `thenCombine`,
`whenComplete` oder vergleichbare nicht blockierende Verkettungen.

```java
// Falsch auf einem Tick-Thread:
World world = stage.toCompletableFuture().join();

// Richtig:
stage.thenAccept(world -> {
    // Der Callback-Kontext ist Bestandteil des jeweiligen API-Vertrags.
});
```

## Projekt einrichten

Tessera verwendet die Folia-API-Koordinaten:

```kotlin
repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("dev.folia:folia-api:26.2.build.<build>-stable")
}
```

Verwende die exakte API-Version deines Tessera-Servers beziehungsweise des
Repositories, aus dem du Tessera beziehst. Die API-Abhängigkeit wird nicht in
das Plugin-JAR eingebettet.

Ein klassisches `plugin.yml` muss Folia-Unterstützung ausdrücklich aktivieren:

```yaml
name: ExamplePlugin
version: 1.0.0
main: dev.example.ExamplePlugin
api-version: '26.2'
folia-supported: true
```

Ohne `folia-supported: true` darf ein Plugin nicht davon ausgehen, unter
Tessera geladen zu werden.

Der gelesene Metadatenwert ist auch programmatisch verfügbar:

```java
boolean supported = plugin.getPluginMeta().isFoliaSupported();
```

## Capability-Erkennung

Tessera stellt eine echte, implementierungsbasierte Capability-API bereit.
Servername und Versionsstring sind kein verlässlicher Feature-Nachweis.

### Einstiegspunkte

```java
TesseraCapabilities capabilities = Bukkit.getTesseraCapabilities();
RuntimeWorldManager worlds = Bukkit.getRuntimeWorldManager();
```

Die entsprechenden Methoden existieren ebenfalls auf `Server`:

```java
Bukkit.getServer().getTesseraCapabilities();
Bukkit.getServer().getRuntimeWorldManager();
```

### `TesseraCapabilities`

Package:

```text
io.papermc.paper.threadedregions.TesseraCapabilities
```

Methoden:

| Methode | Bedeutung |
| --- | --- |
| `supportsRuntimeWorldLifecycle()` | Runtime-Welten können über die asynchrone Tessera-API verwaltet werden. |
| `supportsRegionSafeScoreboards()` | Die benötigte Bukkit-Scoreboard-API ist regionsicher implementiert. |

Direkte Verwendung in einem Tessera-Plugin:

```java
TesseraCapabilities capabilities = Bukkit.getTesseraCapabilities();

if (capabilities.supportsRuntimeWorldLifecycle()) {
    RuntimeWorldManager worlds = Bukkit.getRuntimeWorldManager();
}

if (capabilities.supportsRegionSafeScoreboards()) {
    ScoreboardManager scoreboards = Bukkit.getScoreboardManager();
}
```

### Gemeinsames Plugin-JAR für Paper, Tessera und Folia

Wenn dasselbe JAR auch auf Paper oder unverändertem Folia geladen werden soll,
darf die JVM keine Tessera-Klasse verlinken, bevor ihre Existenz geprüft wurde.
Isoliere Tessera-Code deshalb in einer eigenen Adapterklasse und wähle den
Adapter vorher per Reflection:

```java
enum WorldMode {
    TESSERA,
    PAPER,
    FOLIA_FALLBACK
}

static WorldMode detectWorldMode() {
    try {
        Class<?> capabilityType = Class.forName(
            "io.papermc.paper.threadedregions.TesseraCapabilities",
            false,
            Bukkit.class.getClassLoader()
        );
        Object capabilities = Bukkit.class
            .getMethod("getTesseraCapabilities")
            .invoke(null);
        boolean supported = (boolean) capabilityType
            .getMethod("supportsRuntimeWorldLifecycle")
            .invoke(capabilities);
        if (supported) {
            return WorldMode.TESSERA;
        }
    } catch (ReflectiveOperationException ignored) {
        // Keine Tessera-Capability vorhanden.
    }

    try {
        Class.forName(
            "io.papermc.paper.threadedregions.RegionizedServerInitEvent",
            false,
            Bukkit.class.getClassLoader()
        );
        return WorldMode.FOLIA_FALLBACK;
    } catch (ClassNotFoundException ignored) {
        return WorldMode.PAPER;
    }
}
```

Empfohlenes Verhalten:

| Plattform | Weltstrategie |
| --- | --- |
| Paper | Paper-Weltverwaltung aus einem sicheren Serverkontext |
| Tessera | `RuntimeWorldManager` |
| Unverändertes Folia | Canvas-/In-Place-Fallback ohne Runtime-Welten |

## Thread- und Ownership-Modell

### Geeigneter Kontext nach Aufgabe

| Aufgabe | Richtiger Kontext |
| --- | --- |
| Block, Chunk oder ortsgebundene Weltlogik | `RegionScheduler` |
| Spieler oder bewegliche Entity | `Entity#getScheduler()` |
| Zeit, Wetter, globale Serverdaten | `GlobalRegionScheduler` |
| Datei-, HTTP- oder Datenbank-I/O | `AsyncScheduler` oder eigener Executor |
| Runtime-Welt erstellen, laden, klonen, entladen | `RuntimeWorldManager` von jedem Thread aus |
| Spieler teleportieren | `Entity#teleportAsync(...)` |

Ein Objekt aus einer fremden Region darf nicht nur deshalb gelesen werden, weil
eine Java-Referenz darauf vorhanden ist. Besonders bei Entities ist bereits das
Auslesen einer Position außerhalb des Owners nicht zulässig.

### Ownership prüfen

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

### `RegionizedServerInitEvent`

Package:

```text
io.papermc.paper.threadedregions.RegionizedServerInitEvent
```

Dieses Event läuft nach der Serverinitialisierung, aber bevor Regionen parallel
zu ticken beginnen. Es eignet sich für einmalige Post-Initialisierungslogik, die
vor dem Start des regionalen Parallelbetriebs abgeschlossen sein muss.

```java
@EventHandler
public void onRegionizedServerInit(RegionizedServerInitEvent event) {
    plugin.getLogger().info("Regionisierter Server ist initialisiert");
}
```

## Scheduler

Die Scheduler befinden sich in:

```text
io.papermc.paper.threadedregions.scheduler
```

### `RegionScheduler`

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

### `EntityScheduler`

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

Der Scheduler folgt der Entity bei Regions- und Weltwechseln. Wird die Entity
vor der Ausführung entfernt, läuft der optionale `retired`-Callback.

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

### `GlobalRegionScheduler`

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

### `AsyncScheduler`

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

### `ScheduledTask`

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

## Regionale TPS

Tessera kann die Tickrate einer konkreten Region liefern:

```java
double[] tps = Bukkit.getRegionTPS(location);
double[] tps = Bukkit.getRegionTPS(chunk);
double[] tps = Bukkit.getRegionTPS(world, chunkX, chunkZ);
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

## Runtime-World-API

Package:

```text
io.papermc.paper.world
```

Manager:

```java
RuntimeWorldManager worlds = Bukkit.getRuntimeWorldManager();
```

Alle Lifecycle-Methoden sind nicht blockierend und dürfen von Plugin-,
Async-, Entity-, Region- oder Global-Threads aufgerufen werden. Das
Ergebnis-Future wird erst abgeschlossen, wenn die Welt vollständig aktiv oder
vollständig geschlossen ist.

### Methoden

```java
boolean supportsRuntimeWorldLifecycle();

CompletionStage<WorldLoadResult> createWorldAsync(
    WorldCreator creator
);

CompletionStage<WorldLoadResult> loadWorldAsync(
    WorldCreator creator
);

CompletionStage<WorldLoadResult> loadWorldAsync(
    NamespacedKey key
);

CompletionStage<WorldCloneResult> cloneWorldAsync(
    World source,
    NamespacedKey target,
    WorldCloneOptions options
);

CompletionStage<WorldUnloadResult> unloadWorldAsync(
    World world,
    WorldUnloadOptions options
);
```

Die alten synchronen Bukkit-Methoden sind auf Region- und Entity-Threads kein
sicherer Ersatz:

```java
Bukkit.createWorld(...);
Bukkit.unloadWorld(...);
```

Verwende für dynamische Tessera-Welten ausschließlich den
`RuntimeWorldManager`.

### Thread- und Eventkontext

Intern verteilt Tessera die Arbeit auf mehrere Kontexte:

- Validierung und per-Welt-Serialisierung auf dem Lifecycle-Executor
- Registrierung und Bukkit-Weltevents auf dem Global Region Thread
- Chunkvorbereitung über die vorhandenen Chunk-Futures und Worker
- Snapshot-, Kopier- und Close-Arbeit auf dedizierten I/O-Workern
- normales Ticken der aktiven Welt über unabhängige Tickregionen

Folgende Events laufen im Global-Region-Kontext:

- `WorldInitEvent`
- `WorldLoadEvent`
- `WorldSaveEvent` während Lifecycle-Speicherungen
- `WorldUnloadEvent`

Nach erfolgreichem Laden läuft die Welt nicht dauerhaft auf dem Global Region
Thread. Ihre Chunks werden wie alle anderen Tessera-Chunks regionalisiert.

### Stabile Weltidentität

Eine Runtime-Welt besitzt drei unterschiedliche Werte:

| Wert | Beispiel | Verwendung |
| --- | --- | --- |
| `World#getKey()` | `mosaikchallenges:mcc_arena` | Stabile Identität zum Speichern und erneuten Laden |
| `World#getName()` | `mosaikchallenges_mcc_arena` | Sichtbarer Bukkit-Kompatibilitätsname |
| `World#getWorldPath()` | `<level>/dimensions/mosaikchallenges/mcc_arena` | Tatsächlicher Speicherpfad |

Speichere immer:

```java
config.set("arena-world-key", world.getKey().toString());
```

Lade nach einem Neustart:

```java
String serialized = config.getString("arena-world-key");
NamespacedKey key = NamespacedKey.fromString(serialized);

if (key == null) {
    throw new IllegalStateException("Ungültiger gespeicherter World-Key");
}

Bukkit.getRuntimeWorldManager()
    .loadWorldAsync(key)
    .thenAccept(result -> {
        if (!result.successful()) {
            plugin.getLogger().severe(
                result.status() + ": " + result.message()
            );
            return;
        }

        World world = result.world();
        NamespacedKey stableKey = result.worldKey();
        Path actualPath = result.worldPath();
    });
```

Leite niemals einen Key aus den Unterstrichen von `World#getName()` ab. Eine
solche Umwandlung ist nicht eindeutig.

Wenn Tessera genau eine gespeicherte Welt erkennt, deren sichtbarer Name zum
versehentlich übergebenen Namen passt, liefert der Load-Vorgang
`IDENTITY_MISMATCH`. Tessera lädt niemals stillschweigend eine andere
Identität.

### Welt erstellen

```java
NamespacedKey key = new NamespacedKey(plugin, "farming");
WorldCreator creator = WorldCreator.ofKey(key);

worlds.createWorldAsync(creator).thenAccept(result -> {
    if (!result.successful()) {
        plugin.getLogger().severe(
            "Welt konnte nicht erstellt werden: "
                + result.status() + " / " + result.message()
        );
        return;
    }

    World farming = result.world();
});
```

Flatworld:

```java
WorldCreator creator = WorldCreator
    .ofKey(new NamespacedKey(plugin, "flat_arena"))
    .type(WorldType.FLAT);

CompletionStage<WorldLoadResult> result =
    worlds.createWorldAsync(creator);
```

`createWorldAsync` lehnt bereits vorhandene Weltdaten ab.
`loadWorldAsync` verlangt dagegen eine vorhandene und vollständige Welt.

Gültige Weltnamen bestehen aus 1 bis 64 Zeichen:

```text
A-Z a-z 0-9 . _ -
```

Ungültige Windows-Gerätenamen, doppelte Registrierungen, kollidierende Keys und
unsichere Zielpfade werden abgelehnt.

### `WorldLoadResult`

Felder und Methoden:

```java
Status status();
@Nullable World world();
String message();
@Nullable Throwable cause();
boolean successful();
@Nullable NamespacedKey worldKey();
@Nullable Path worldPath();
```

`world`, `worldKey()` und `worldPath()` sind nur bei erfolgreicher
Registrierung verfügbar.

Statuswerte:

| Status | Bedeutung |
| --- | --- |
| `SUCCESS` | Welt vollständig aktiv |
| `UNSUPPORTED` | Capability nicht verfügbar |
| `SERVER_STOPPING` | Server nimmt keine neue Operation mehr an |
| `INVALID_REQUEST` | Allgemein ungültige Anfrage |
| `INVALID_NAME` | Ungültiger Weltname |
| `INVALID_KEY` | Ungültiger oder kollidierender Dimension-Key |
| `ALREADY_EXISTS` | Zielstorage existiert bereits |
| `ALREADY_LOADED` | Welt ist bereits registriert |
| `NOT_FOUND` | Vorhandene Welt wurde nicht gefunden |
| `IDENTITY_MISMATCH` | Sichtbarer Name wurde mit stabilem Key verwechselt |
| `INCOMPLETE_WORLD` | Weltordner oder Metadaten sind unvollständig |
| `CORRUPT_WORLD` | Weltdaten oder Regiondateien sind beschädigt |
| `INITIALIZATION_FAILED` | ServerLevel oder Teilsysteme konnten nicht initialisiert werden |
| `CHUNK_PREPARATION_FAILED` | Startchunks konnten nicht vorbereitet werden |
| `REGISTRATION_FAILED` | Registrierung im Server schlug fehl |
| `ROLLBACK_FAILED` | Fehlerbereinigung konnte nicht vollständig abgeschlossen werden |
| `CANCELLED` | Operation wurde vor Abschluss abgebrochen |

### Arena-Template klonen

```java
NamespacedKey target = new NamespacedKey(plugin, "arena_team_red");

worlds.cloneWorldAsync(
    templateWorld,
    target,
    WorldCloneOptions.defaults()
).thenAccept(result -> {
    if (!result.successful()) {
        plugin.getLogger().severe(
            "Clone fehlgeschlagen: "
                + result.status() + " / " + result.message()
        );
        return;
    }

    World arena = result.world();
});
```

Ein Clone ist kein ungeprüftes rekursives Kopieren eines Live-Ordners.
Tessera quiesziert eine geladene Vorlage, flushst ihre Speicher und validiert
die Regiondateien.

Kopiert werden wiederverwendbare Weltdaten:

- `region`
- `entities`
- `poi`
- optional `data`

Nicht übernommen werden unter anderem:

- Spieler- und Statistikdaten
- Advancements
- `uid.dat`
- `session.lock`
- `level.dat` und `level.dat_old`
- offene Storage-Handles
- UUID und Dimension-Identität der Vorlage

Die Zielwelt erhält einen eigenen Key, eine eigene UUID, eigene Metadaten und
eigene Storage-Worker.

#### `WorldCloneOptions`

```java
WorldCloneOptions options = WorldCloneOptions.builder()
    .requireReadOnlyTemplate(true)
    .flushSource(true)
    .copyDataDirectory(true)
    .spawnChunkRadius(2)
    .build();
```

Standardwerte:

| Option | Standard | Bedeutung |
| --- | --- | --- |
| `requireReadOnlyTemplate` | `true` | Vorlage muss als statisches Template behandelt werden |
| `flushSource` | `true` | Vor dem Snapshot werden Quelldaten gespeichert und geflusht |
| `copyDataDirectory` | `true` | Wiederverwendbare Dimensionsdaten werden kopiert |
| `spawnChunkRadius` | `2` | 5 × 5 Chunks um den Spawn werden vorbereitet |

`spawnChunkRadius` darf zwischen `0` und `8` liegen.

Während des Snapshots darf die Template-Welt nicht als spielbare Arena
verwendet oder extern verändert werden. Mehrere Zielwelten können parallel aus
demselben unveränderten Snapshot installiert werden.

#### `WorldCloneResult`

Methoden:

```java
Status status();
@Nullable World world();
String message();
@Nullable Throwable cause();
boolean successful();
@Nullable NamespacedKey worldKey();
@Nullable Path worldPath();
```

Statuswerte:

| Status | Bedeutung |
| --- | --- |
| `SUCCESS` | Zielwelt vollständig geklont und aktiv |
| `UNSUPPORTED` | Capability nicht verfügbar |
| `SERVER_STOPPING` | Server fährt herunter |
| `INVALID_REQUEST` | Ungültige Anfrage |
| `INVALID_SOURCE` | Quelle ist keine geeignete Welt |
| `SOURCE_NOT_READ_ONLY` | Read-only-Anforderung wurde verletzt |
| `SOURCE_BUSY` | Quelle befindet sich in einer inkompatiblen Operation |
| `TARGET_EXISTS` | Zielstorage existiert bereits |
| `SNAPSHOT_FAILED` | Snapshot oder Flush schlug fehl |
| `CORRUPT_TEMPLATE` | Template-Regiondateien sind beschädigt |
| `COPY_FAILED` | Zielkopie konnte nicht installiert werden |
| `LOAD_FAILED` | Kopie wurde erstellt, aber nicht erfolgreich geladen |
| `CLEANUP_FAILED` | Partielle Zieldaten konnten nicht vollständig bereinigt werden |
| `CANCELLED` | Operation wurde abgebrochen |

### Welt entladen

Standardverhalten:

```java
WorldUnloadOptions options = WorldUnloadOptions.defaults();
```

Standardwerte:

| Option | Standard |
| --- | --- |
| Speichern | `true` |
| Spielerbehandlung | `FAIL` |
| Teleportziel | keines |
| Timeout | 90 Sekunden |

Ohne Speichern entladen:

```java
WorldUnloadOptions options = WorldUnloadOptions.builder()
    .save(false)
    .failWhenPlayersPresent()
    .timeout(Duration.ofSeconds(45))
    .build();
```

Spieler vor dem Unload asynchron teleportieren:

```java
WorldUnloadOptions options = WorldUnloadOptions.builder()
    .save(false)
    .teleportPlayers(lobbySpawn)
    .timeout(Duration.ofSeconds(45))
    .build();
```

Vollständiger Ablauf mit sicherer Ordnerlöschung:

```java
Path worldPath = arena.getWorldPath();

worlds.unloadWorldAsync(arena, options)
    .thenCompose(result -> {
        if (!result.successful()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException(
                    result.status() + ": " + result.message(),
                    result.cause()
                )
            );
        }

        // Erst jetzt sind Registrierungen und Storage-Handles geschlossen.
        return CompletableFuture.runAsync(
            () -> deleteRecursively(worldPath),
            pluginIoExecutor
        );
    });
```

Der Ordner darf niemals vor einem erfolgreichen Unload-Ergebnis gelöscht
werden. `successful()` ist insbesondere unter Windows das Gate für die
Dateihandle-Freigabe.

Beim Unload:

1. wird `WorldUnloadEvent` ausgelöst und eine Abbrechung respektiert,
2. werden neue Teleports, Tickets und Regionsaufgaben gesperrt,
3. werden Spieler geprüft oder per `teleportAsync` versetzt,
4. laufen ausschließlich die Regionen der Zielwelt aus,
5. werden Speicher abhängig von `save` geschrieben,
6. werden Chunk-, Entity-, POI- und Storage-Worker geschlossen,
7. wird die Welt aus allen Serverregistrierungen entfernt,
8. wird erst danach das Future abgeschlossen.

Primäre Serverwelten sind geschützt.

#### `WorldUnloadResult`

Methoden:

```java
Status status();
String message();
@Nullable Throwable cause();
boolean successful();
```

Statuswerte:

| Status | Bedeutung |
| --- | --- |
| `SUCCESS` | Welt vollständig geschlossen und deregistriert |
| `UNSUPPORTED` | Capability nicht verfügbar |
| `SERVER_STOPPING` | Server nimmt keine neue Operation mehr an |
| `INVALID_REQUEST` | Ungültige Anfrage |
| `NOT_LOADED` | Welt ist nicht registriert |
| `ALREADY_UNLOADING` | Ein Unload läuft bereits |
| `PROTECTED_WORLD` | Primäre oder geschützte Welt |
| `EVENT_CANCELLED` | `WorldUnloadEvent` wurde abgebrochen |
| `PLAYERS_PRESENT` | Spieler befinden sich noch in der Welt |
| `INVALID_TELEPORT_TARGET` | Teleportziel ist ungültig |
| `PLAYER_TELEPORT_FAILED` | Mindestens ein Spieler konnte nicht versetzt werden |
| `QUIESCE_FAILED` | Regionen oder Admission konnten nicht kontrolliert angehalten werden |
| `SAVE_FAILED` | Speichern schlug fehl |
| `STORAGE_CLOSE_FAILED` | Storage-Worker oder Dateihandles konnten nicht geschlossen werden |
| `UNREGISTRATION_FAILED` | Serverregistrierungen konnten nicht vollständig entfernt werden |
| `CLEANUP_FAILED` | Nacharbeiten oder Bereinigung schlugen fehl |
| `TIMEOUT` | Reversible Phase überschritt den konfigurierten Timeout |
| `CANCELLED` | Operation wurde abgebrochen |

Der Timeout ist in der reversiblen Phase wirksam. Hat das irreversible
Schließen bereits begonnen, beendet Tessera die Dateihandle-Freigabe
kontrolliert, bevor das Ergebnis abgeschlossen wird.

### Parallel mehrere Arenen erstellen

```java
CompletionStage<WorldCloneResult> red = worlds.cloneWorldAsync(
    template,
    new NamespacedKey(plugin, "arena_red"),
    WorldCloneOptions.defaults()
);

CompletionStage<WorldCloneResult> blue = worlds.cloneWorldAsync(
    template,
    new NamespacedKey(plugin, "arena_blue"),
    WorldCloneOptions.defaults()
);

CompletionStage<Void> ready = red.thenCombine(blue, (redResult, blueResult) -> {
    if (!redResult.successful() || !blueResult.successful()) {
        throw new IllegalStateException(
            "Arena-Clone fehlgeschlagen: "
                + redResult + " / " + blueResult
        );
    }

    World redArena = redResult.world();
    World blueArena = blueResult.world();
    return null;
});
```

Die Clone-Vorbereitung darf parallel laufen. Nach der Aktivierung ticken die
Welten über ihre normalen unabhängigen Tickregionen und nicht auf einem
gemeinsamen Arena-Thread.

### Serverstopp und Plugin-Disable

Während des Serverstopps werden neue Operationen mit `SERVER_STOPPING`
abgelehnt. Bereits gestartete Operationen werden nur in reversiblen Phasen
abgebrochen; serverseitige Close-Arbeit bleibt Eigentum von Tessera.

Beim Plugin-Disable:

- keine neuen Lifecycle-Operationen starten,
- eigene Callback-Folgen als inaktiv markieren,
- keine tick-thread-blockierenden Warteaufrufe verwenden,
- Tessera die bereits begonnene serverseitige Bereinigung beenden lassen.

## Regionsichere Scoreboards

Capability:

```java
boolean supported = Bukkit.getTesseraCapabilities()
    .supportsRegionSafeScoreboards();
```

Ist die Capability nicht vorhanden, kann ein Plugin nur seine Sidebar
deaktivieren. BossBars, ActionBars und sonstige Spiellogik sind davon
unabhängig.

### Unterstützte Bukkit-Oberfläche

- `Bukkit.getScoreboardManager()`
- `ScoreboardManager#getMainScoreboard()`
- `ScoreboardManager#getNewScoreboard()`
- `Player#getScoreboard()`
- `Player#setScoreboard(Scoreboard)`
- `Scoreboard#registerNewObjective(...)`
- Adventure-Komponenten als Objective-Anzeigename
- `Objective#setDisplaySlot(DisplaySlot.SIDEBAR)`
- `Objective#getScore(...).setScore(...)`
- `Scoreboard#registerNewTeam(...)`
- `Team#addEntry(...)` und `removeEntry(...)`
- Adventure-Komponenten für Team-Präfix und -Suffix
- `Scoreboard#resetScores(...)`
- Entfernen von Objectives und Teams
- Wiederherstellen des vorherigen oder Main-Scoreboards

`getNewScoreboard()` erzeugt ein unabhängiges Modell. Scoreboards verschiedener
Spieler oder Arenen teilen ihre Objectives, Teams und Scores nicht.

### Threadmodell

Objective-, Team-, Score- und Display-Slot-Änderungen werden innerhalb des
jeweiligen Scoreboard-Modells serialisiert. Das Modell erzeugt unveränderliche,
versionierte Paketänderungen.

Die Anwendung auf einen Spieler erfolgt ausschließlich über dessen
Entity-Owner. Vor dem Senden prüft Tessera:

- ob der Spieler noch online ist,
- ob er weiterhin dasselbe Scoreboard verwendet,
- ob der aktuelle Entity-Owner zuständig ist,
- ob der Snapshot noch zur aktuellen Revision gehört.

Mehrere Änderungen innerhalb eines Update-Bursts werden bis zum nächsten
Spielertick zusammengefasst. Es existiert keine zusätzliche öffentliche
Tessera-Transaktionsmethode; normale Bukkit-Aufrufe werden intern gebündelt.

### Sidebar-Beispiel

```java
ScoreboardManager manager = Bukkit.getScoreboardManager();
Scoreboard previous = player.getScoreboard();
Scoreboard sidebar = manager.getNewScoreboard();

Objective objective = sidebar.registerNewObjective(
    "mcc",
    Criteria.DUMMY,
    Component.text("Monster Army Battle")
);
objective.setDisplaySlot(DisplaySlot.SIDEBAR);

for (int line = 0; line < 15; ++line) {
    String entry = "mcc_line_" + line;
    Team team = sidebar.registerNewTeam("mcc_" + line);

    team.addEntry(entry);
    team.prefix(Component.text(renderLine(line)));
    objective.getScore(entry).setScore(15 - line);
}

player.setScoreboard(sidebar);
```

Mehrere Zeilen aktualisieren:

```java
for (int line = 0; line < 15; ++line) {
    Team team = sidebar.getTeam("mcc_" + line);
    if (team != null) {
        team.prefix(Component.text(renderLine(line)));
    }
}

sidebar.resetScores("obsolete_entry");
```

Vorheriges Scoreboard wiederherstellen:

```java
Scoreboard target = previous != null
    ? previous
    : manager.getMainScoreboard();

player.setScoreboard(target);
```

Bei Plugin-Disable sollten Update-Producer beendet und die vorherigen
Scoreboards aller noch aktiven Spieler wiederhergestellt werden. Verlässt ein
Spieler während der Übergabe den Server, verwirft Tessera die veraltete
Publikation.

## Entity-Scoreboard-Tags

Entity-Scoreboard-Tags sind kein Sidebar-Scoreboard. Sie gehören direkt zum
Entity-Zustand:

```java
Set<String> getScoreboardTags();
boolean addScoreboardTag(String tag);
boolean removeScoreboardTag(String tag);
```

Diese Methoden müssen auf dem jeweiligen Entity-Owner ausgeführt werden:

```java
entity.getScheduler().execute(
    plugin,
    () -> entity.addScoreboardTag("mcc-active"),
    null,
    1L
);
```

`getScoreboardTags()` liefert unter Tessera eine unveränderliche Kopie. Eine
Entity kann maximal 1024 Tags besitzen.

## Tessera-Gamerule

Tessera ergänzt:

```java
GameRules.ALLOW_EYES_OF_ENDER_USE
```

Die Regel kontrolliert:

- das Verwenden von Enderaugen als Projektil,
- das Einsetzen von Enderaugen in Portalrahmen,
- das Reisen durch Endportale.

Regionsicheres Beispiel:

```java
Bukkit.getRegionScheduler().execute(plugin, location, () -> {
    location.getWorld().setGameRule(
        GameRules.ALLOW_EYES_OF_ENDER_USE,
        false
    );
});
```

Der alte Alias ist seit `26.2` veraltet und zur Entfernung vorgesehen:

```java
GameRule.ALLOW_EYES_OF_ENDER_USE
```

Verwende für neuen Code ausschließlich `GameRules.ALLOW_EYES_OF_ENDER_USE`.

## Konsole und RCON

Konsolen- und RCON-Befehle besitzen unter Tessera einen gültigen
Standard-Weltkontext.

Befehle, die bereits während des Serverstarts über stdin eintreffen:

- bleiben in ursprünglicher Reihenfolge gepuffert,
- werden erst nach vollständiger Initialisierung ausgeführt,
- erhalten dann den gültigen Standardwelt-Kontext,
- behalten einen ausdrücklich gesetzten Dimensionskontext.

Das gilt unter anderem für:

- `/stop`
- Vanilla-Befehle
- Pluginbefehle
- `execute in <dimension> ...`
- RCON

Plugins müssen keinen eigenen künstlichen `CommandSourceStack` oder
Konsolenwelt-Workaround erzeugen.

## Paper-, Tessera- und Folia-Kompatibilität

| Funktion | Paper | Tessera | Unverändertes Folia |
| --- | --- | --- | --- |
| Dynamische Welten | Paper-API | `RuntimeWorldManager` | nicht verfügbar |
| Regionale Scheduler | eingeschränkt relevant | erforderlich | erforderlich |
| Regionsichere Sidebar | klassische Bukkit-Semantik | Capability prüfen | abhängig vom Folia-Stand |
| Mehrere unabhängige Arenen | möglich | möglich und regional parallel | ohne Runtime-Welten nur In-Place |
| Capability-Erkennung | Tessera-Klasse fehlt | echte Capability | Tessera-Klasse fehlt |

Eine gemeinsame Plugin-JAR sollte Plattformadapter verwenden. Lade
Tessera-spezifische Klassen erst, nachdem die Capability per Reflection erkannt
wurde.

## Fehlerbehandlung und Best Practices

### Lifecycle-Ergebnisse vollständig auswerten

Prüfe nicht nur `successful()`:

```java
operation.whenComplete((result, thrown) -> {
    if (thrown != null) {
        plugin.getLogger().log(
            Level.SEVERE,
            "Future außergewöhnlich beendet",
            thrown
        );
        return;
    }

    if (!result.successful()) {
        plugin.getLogger().warning(
            result.status() + ": " + result.message()
        );
        if (result.cause() != null) {
            plugin.getLogger().log(
                Level.WARNING,
                "Lifecycle-Ursache",
                result.cause()
            );
        }
    }
});
```

Ein normaler fachlicher Fehler wird in der Regel als Ergebnisstatus geliefert.
Ein außergewöhnlich abgeschlossenes Future sollte trotzdem separat behandelt
werden.

### Niemals auf Tick-Threads blockieren

Vermeide auf Region-, Entity- und Global-Threads:

```java
future.get();
future.join();
Thread.sleep(...);
```

Verwende stattdessen asynchrone Verkettungen und übergib abschließende
Entity-Arbeit erneut an den EntityScheduler.

### Keine Cross-Region-Entity-Zugriffe

```java
player.getScheduler().execute(
    plugin,
    () -> {
        Location current = player.getLocation();
        // Entity- und Positionszugriff gehören hierher.
    },
    null,
    1L
);
```

### Teleports

Verwende für Spieler- und Cross-World-Teleports:

```java
player.teleportAsync(target).thenAccept(success -> {
    if (!success) {
        plugin.getLogger().warning("Teleport fehlgeschlagen");
    }
});
```

### Weltordner löschen

Die sichere Reihenfolge lautet:

1. tatsächlichen `World#getWorldPath()` erfassen,
2. Spieler kontrolliert entfernen,
3. `unloadWorldAsync` aufrufen,
4. `WorldUnloadResult#successful()` prüfen,
5. Ordner auf einem I/O-Executor löschen.

### Welt-Keys persistieren

Persistiere:

```java
world.getKey().toString();
```

Persistiere nicht:

```java
world.getName();
```

### Plugin-Disable

- Scheduler-Aufgaben des Plugins abbrechen,
- keine neuen Weltoperationen starten,
- Scoreboards wiederherstellen,
- späte eigene Callbacks ignorieren,
- keine serverseitige Lifecycle-Bereinigung durch Thread-Unterbrechung stören.

## Paketübersicht

| Package/Klasse | Zweck |
| --- | --- |
| `io.papermc.paper.threadedregions.TesseraCapabilities` | Tessera-Featureerkennung |
| `io.papermc.paper.threadedregions.RegionizedServerInitEvent` | Hook vor dem parallelen Regionbetrieb |
| `io.papermc.paper.threadedregions.scheduler.RegionScheduler` | Ortsgebundene Regionsaufgaben |
| `io.papermc.paper.threadedregions.scheduler.EntityScheduler` | Aufgaben für bewegliche Entities |
| `io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler` | Globale Tickaufgaben |
| `io.papermc.paper.threadedregions.scheduler.AsyncScheduler` | Tick-unabhängige asynchrone Aufgaben |
| `io.papermc.paper.threadedregions.scheduler.ScheduledTask` | Taskstatus und Abbruch |
| `io.papermc.paper.world.RuntimeWorldManager` | Runtime-Welt-Lifecycle |
| `io.papermc.paper.world.WorldLoadResult` | Create-/Load-Ergebnis |
| `io.papermc.paper.world.WorldCloneResult` | Clone-Ergebnis |
| `io.papermc.paper.world.WorldUnloadResult` | Unload-Ergebnis |
| `io.papermc.paper.world.WorldCloneOptions` | Clone-Konfiguration |
| `io.papermc.paper.world.WorldUnloadOptions` | Unload-Konfiguration |
| `org.bukkit.GameRules` | Bukkit-Gamerules einschließlich Tessera-Erweiterung |
| `org.bukkit.scoreboard` | Regionsicher unterstützte Bukkit-Scoreboards |

## Weiterführende Architekturdetails

- [Runtime-World-Lifecycle](runtime-world-lifecycle.md)
- [Regionsichere Scoreboards](region-safe-scoreboards.md)
- [Konsolen- und RCON-Weltkontext](console-command-context.md)
- [Windows-Paperclip-Langpfade](windows-paperclip-long-paths.md)

---

Diese Dokumentation bezieht sich auf die Tessera-API-Linie `26.2.x`.
Capabilities sollten auch innerhalb derselben Versionslinie immer zur Laufzeit
geprüft werden.
