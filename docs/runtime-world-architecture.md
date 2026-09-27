# Runtime world architecture notes

These are implementation notes for the Tessera 26.3 build 010 source, checked on
2026-09-28. They preserve the architectural context from the former combined
lifecycle reference. They are **not an additional public plugin API**. Plugin
contracts are maintained only in [the API reference](api/runtime-worlds.md).

## Execution domains

The lifecycle executor validates paths and coordinates operations. Global-region
work performs registry and lifecycle transitions and dispatches the corresponding
world events. Chunk-system futures prepare spawn areas; snapshot/close workers
perform file I/O. Activated worlds use the ordinary regioniser and region tick
pool, not one global tick loop for every arena.

Normalized world names identify operation locks. Clone obtains a read lock for
the template and a write lock for its target in sorted order. This allows
independent target preparation without concurrent writes to one world store.
Locks are implementation details; plugins must not acquire them through NMS.

## Admission and lifecycle

```text
INITIALIZING -> ACTIVE -> QUIESCING -> UNLOADING -> CLOSED
                 ^           |
                 +-----------+  reversible failure before close

               ACTIVE -> SNAPSHOTTING -> ACTIVE
```

`INITIALIZING` admits internal chunk preparation. `QUIESCING` rejects new
ordinary region tasks, incoming teleports, force-loaded chunks, plugin chunk
tickets and chunk API work. `UNLOADING` is irreversible; storage reaches `CLOSED`
before removal from region, Minecraft and Bukkit registries. Partial load
registration uses the same drain/close machinery for cleanup.

`SNAPSHOTTING` is a reversible group barrier. All loaded worlds participate
because player and level-root stores are shared. Only requested worlds are
copied. Owner-bound player save work is drained before region barriers; a gate
then waits for running ownership sections, including merge/split release, and
seals ordinary region/global chunk work. Internal save/barrier queues can make
progress during gameplay freeze. This does not grant ordinary plugin tasks a
freeze bypass or allow arbitrary gameplay packets through the gate.

## Storage boundaries

Dimensions normally have independent chunk/entity/POI/data trees, while the
primary storage owns the common `level.dat` and session lock. Create/load use
normal server storage and initialize normal border, gamerule, saved-data,
entity-lookup and distance-management machinery. The public world path is the
authoritative storage path, not a directory guessed from the Bukkit display name.

Static template clones and fresh group snapshots deliberately differ: the former
may reuse a validated immutable source snapshot, while the latter always saves
again. Global saved data is serialized on its owner, outstanding asynchronous
writes are drained, and competing saves are deferred until snapshot cleanup.
Native restore and snapshots coordinate through the player-store fence. None
of these internal coordination primitives is exposed as a plugin lock API.

The public [snapshot](api/world-snapshots.md) and [unload](api/world-unloading.md)
articles define the actual completion, cancellation, timeout and cleanup
semantics. In particular, several published directories are not one crash-atomic
filesystem transaction, and irreversible close is not interrupted merely because
an advisory timeout elapsed.

## Historical evidence

- [Online-snapshot diagnosis and tests](runtime-snapshot-online-fix-26.2-017.md)
- [Level-root snapshot extension](runtime-snapshot-level-root-26.2-017.md)
- [Native build-010 restore status](mcc-player-restore-status.md)

These reports remain dated evidence, not a second maintained API specification.
