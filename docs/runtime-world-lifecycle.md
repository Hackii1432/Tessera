# Tessera Runtime World Lifecycle

Tessera exposes an asynchronous, region-safe lifecycle for worlds that are
created, loaded, cloned, and unloaded while the server is running. The API is
implemented by `io.papermc.paper.world.RuntimeWorldManager` and is available
through `Bukkit.getRuntimeWorldManager()`.

The old synchronous Bukkit methods remain deliberately restricted. Calling
`Bukkit.createWorld(...)` or `Bukkit.unloadWorld(...)` from a region or entity
tick thread throws an exception that points to this API. Tessera does not
disable Folia ownership checks and never waits for lifecycle I/O on a region
tick thread.

## Capability detection

Code compiled directly against the Tessera API can use:

```java
if (Bukkit.getTesseraCapabilities().supportsRuntimeWorldLifecycle()) {
    RuntimeWorldManager worlds = Bukkit.getRuntimeWorldManager();
}
```

A single JAR that must also load on Paper and unmodified Folia should isolate
Tessera-linked code in a separate adapter class and select the adapter by
reflection before that class is loaded:

```java
enum WorldMode { TESSERA, PAPER, FOLIA_FALLBACK }

static WorldMode detectWorldMode() {
    try {
        Class<?> type = Class.forName(
            "io.papermc.paper.threadedregions.TesseraCapabilities",
            false,
            Bukkit.class.getClassLoader()
        );
        Object capabilities = Bukkit.class
            .getMethod("getTesseraCapabilities")
            .invoke(null);
        boolean supported = (boolean) type
            .getMethod("supportsRuntimeWorldLifecycle")
            .invoke(capabilities);
        if (supported) {
            return WorldMode.TESSERA;
        }
    } catch (ReflectiveOperationException ignored) {
        // Not Tessera.
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

This gives the intended routing:

- Paper: use Paper's dynamic synchronous world support from a safe server
  context.
- Tessera: use `RuntimeWorldManager`.
- unmodified Folia: do not create runtime worlds; use the Canvas/In-Place
  fallback.

No server name or version string participates in detection.

## Thread and state model

Every API method may be invoked from an asynchronous plugin task, an entity
thread, a region thread, or the global-region thread. It returns immediately.
The operation passes through the following contexts:

1. a dedicated lifecycle executor validates paths and acquires the per-world
   operation lock;
2. the global-region scheduler performs registrations, lifecycle transitions,
   and Bukkit world events;
3. chunk futures and the existing chunk-system workers prepare spawn chunks;
4. dedicated snapshot/close workers copy, flush, and close storage;
5. ordinary independent tick regions own the world after activation.

The state machine is:

```text
INITIALIZING -> ACTIVE -> QUIESCING -> UNLOADING -> CLOSED
                    ^          |
                    +----------+  reversible failure before close
```

`INITIALIZING` admits only internal chunk preparation. `QUIESCING` rejects new
plugin region tasks, teleports into the world, force-loaded chunks, plugin
chunk tickets, and new chunk API requests. `UNLOADING` is irreversible.
`CLOSED` is required before registrations are removed.

Operations using the same normalized world name are serialized. Clone holds a
read lock for the template and a write lock for the target, acquired in sorted
order. It is therefore possible to prepare several targets concurrently
without modifying the same files or deadlocking.

`WorldInitEvent`, `WorldLoadEvent`, `WorldUnloadEvent`, and the lifecycle
`WorldSaveEvent` run on the global-region thread. A successful create/load
future is completed only after spawn-chunk preparation, activation, and
`WorldLoadEvent`. A successful unload future is completed only after all
storage workers are flushed and closed and all registries no longer contain
the world.

Once active, a runtime world has no dedicated global tick loop. Its loaded
chunks enter the normal Tessera regioniser and can tick in parallel with
distant arenas and other worlds on the region tick-thread pool.

## Create and load

Use `createWorldAsync` only for new storage and `loadWorldAsync` only for an
existing complete world. Names are limited to 1–64 characters from
`A-Z`, `a-z`, `0-9`, `.`, `_`, and `-`; Windows device names are rejected.
The world name, dimension key, target folder, and current server registrations
must all be unique.

Paper 26.2 stores API-created worlds as independent dimension roots below the
primary level (`<level>/dimensions/<namespace>/<key>`). Each root owns its
`region`, `entities`, `poi`, and per-dimension `data` trees. The primary
storage access owns the shared `level.dat` and session lock. Tessera validates
the per-dimension world-generation and UUID metadata before loading. A legacy
Bukkit side-world with its own `level.dat` is also accepted and handed to
Paper's normal migration before registration. `World#getWorldPath()` is the
authoritative deletable path for both layouts.

Create/load opens the normal level storage, chunk, entity, and POI stores and
constructs a normal `ServerLevel`. Registration covers `MinecraftServer`,
`CraftServer`, and `RegionizedServer`; vanilla/Paper initialization still
initializes world border, gamerules, saved data, entity lookup, chunk distance
management, and the regioniser. A 5×5 spawn area is prepared by default without
waiting for chunk I/O on a tick thread.

Any failure after partial registration runs the same region drain and
storage-close path as unload, then removes every registration. A rollback
failure is reported explicitly as `ROLLBACK_FAILED`.

## Validated arena-template clone

`cloneWorldAsync` is a snapshot operation, not a recursive copy of a live
folder. Conservative defaults require the source to be declared read-only and
contain no players. Tessera then:

- changes the loaded source to `QUIESCING`;
- waits for all known source-region tasks to reach their barriers;
- fires `WorldSaveEvent` and flushes chunks, entities, POIs, level data, and
  storage workers;
- validates every MCA location table, allocation range, allocation overlap,
  chunk length, and compression type;
- copies only `region`, `entities`, `poi`, and optionally `data`;
- excludes `playerdata`, legacy `players`, `stats`, `advancements`, `uid.dat`,
  `session.lock`, `level.dat`, `level.dat_old`, and Paper UUID metadata;
- installs the target through a private staging directory and atomic move when
  supported;
- creates new target metadata, UUID, dimension key, and storage handles before
  loading it; the shared primary storage session lock is never copied.

Several clones of the same unchanged source share one immutable validated
snapshot. They install into separate target folders concurrently.

The current contract intentionally requires a **static, read-only template
world during clone**. Tessera quiesces region admission while taking the
snapshot, but plugin code must not treat the template as a playable arena or
mutate its files externally. If a source acquires a player or changes lifecycle
state, cloning fails without exposing a partial target.

## Unload

Startup worlds are protected and return `PROTECTED_WORLD`.
`WorldUnloadEvent` runs before the world becomes quiescent and cancellation is
respected. Once quiescent, the operation either:

- returns `PLAYERS_PRESENT`, restoring `ACTIVE`; or
- uses `Player#teleportAsync` for every captured player and rechecks after the
  region barriers so a pending cross-world teleport cannot enter unnoticed.

Only the target world's region handles are descheduled. Other worlds and
regions continue ticking. The close path drains pending teleports and region
work, optionally saves the world, flushes and closes chunk/entity/POI region
files and data storage, halts its I/O workers, marks stale world admission
closed, and unregisters the world in this order:

1. `RegionizedServer`
2. `MinecraftServer`
3. `CraftServer`

The default timeout is advisory. If it expires before irreversible storage
shutdown, the operation returns `TIMEOUT` and the world is restored to
`ACTIVE`. If close has already started, Tessera must finish releasing handles;
the future then reports success even if it completed after the advisory
deadline. This distinction makes `successful()` a reliable gate for deleting a
world folder, including on Windows.

During server stop, new operations return `SERVER_STOPPING`. In-flight
operations are interrupted where still reversible. Their public futures are
settled only after the server's final region-file flush, preventing plugins
from mistaking shutdown interruption for an already deletable world. A plugin
disable should stop initiating new work and ignore its own callbacks; Tessera
still owns and finishes the server-side lifecycle operation.

## MAB-style example

The following is Tessera-specific adapter code. Never call `join()` or `get()`
on the returned stages from a tick thread.

```java
RuntimeWorldManager worlds = Bukkit.getRuntimeWorldManager();

CompletionStage<World> farming = requireLoad(
    worlds.createWorldAsync(
        WorldCreator.ofKey(new NamespacedKey(plugin, "mcc_farming"))
    )
);

CompletionStage<World> template = requireLoad(
    worlds.loadWorldAsync(
        WorldCreator.ofKey(new NamespacedKey(plugin, "mab_template"))
    )
);

CompletionStage<World> teamA = template.thenCompose(source ->
    requireClone(worlds.cloneWorldAsync(
        source,
        new NamespacedKey(plugin, "mab_team_a"),
        WorldCloneOptions.defaults()
    ))
);
CompletionStage<World> teamB = template.thenCompose(source ->
    requireClone(worlds.cloneWorldAsync(
        source,
        new NamespacedKey(plugin, "mab_team_b"),
        WorldCloneOptions.defaults()
    ))
);

CompletionStage<Void> arenasReady = CompletableFuture.allOf(
    teamA.toCompletableFuture(),
    teamB.toCompletableFuture()
);

CompletionStage<Void> playersMoved = arenasReady.thenCompose(ignored -> {
    List<CompletableFuture<Boolean>> teleports = new ArrayList<>();
    for (Player player : teamAPlayers) {
        teleports.add(player.teleportAsync(teamA.toCompletableFuture().join().getSpawnLocation()));
    }
    for (Player player : teamBPlayers) {
        teleports.add(player.teleportAsync(teamB.toCompletableFuture().join().getSpawnLocation()));
    }
    return CompletableFuture.allOf(teleports.toArray(CompletableFuture[]::new));
});

CompletionStage<Void> cleanup = playersMoved.thenCompose(ignored -> {
    World a = teamA.toCompletableFuture().join();
    World b = teamB.toCompletableFuture().join();
    Path aFolder = a.getWorldPath();
    Path bFolder = b.getWorldPath();
    Location lobby = Bukkit.getWorlds().getFirst().getSpawnLocation();
    WorldUnloadOptions options = WorldUnloadOptions.builder()
        .save(false)
        .teleportPlayers(lobby)
        .build();

    CompletionStage<WorldUnloadResult> unloadA = worlds.unloadWorldAsync(a, options);
    CompletionStage<WorldUnloadResult> unloadB = worlds.unloadWorldAsync(b, options);
    return unloadA.thenCombine(unloadB, (resultA, resultB) -> {
        if (!resultA.successful() || !resultB.successful()) {
            throw new IllegalStateException(
                "Arena unload failed: " + resultA + " / " + resultB
            );
        }
        return null;
    }).thenRunAsync(() -> {
        // Runs only after both successful unloads, on a plugin I/O executor.
        deleteRecursively(aFolder);
        deleteRecursively(bFolder);
    }, pluginIoExecutor);
});
```

The helper functions must inspect `successful()`, `status()`, `message()`, and
`cause()` and propagate a diagnostic exception. The `join()` calls above only
read stages already completed by `allOf`; they do not wait. Folder deletion
must run on an asynchronous I/O executor, never a tick thread.
