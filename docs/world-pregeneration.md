# Native world pregeneration

Tessera 26.3-015-beta contains a native administrative pregenerator. It uses the
existing region-aware chunk scheduler, generation workers and region-file I/O.
No Chunky installation or plugin-driven generation loop is required.

## Quick start

```text
/pregen dimension overworld
/pregen shape circle
/pregen center 0 0
/pregen radius 10000
/pregen plan
/pregen start
```

Coordinates and radius are **blocks**, not chunks. The default selection is a
circle, center `0,0`, radius `1000`, mode `balanced`; a dimension must be explicitly
selected before planning or starting. Existing chunks are not regenerated.

`/pregen dimension` displays your selection and all loaded dimension keys.
Use `overworld`, `nether` or `end` for the standard dimensions, or an exact
namespaced key such as `minecraft:overworld` or a loaded runtime dimension's key.
Tab completion offers only loaded keys and their available standard aliases.
World/folder names such as `world_nether` are not accepted. The selection matches
the actual dimension key, not its `NORMAL`/`NETHER`/`THE_END` environment: multiple
runtime dimensions with the same environment remain distinct. Unknown/unloaded
or ambiguous keys are rejected without changing your existing selection. This
command does not create or load dimensions. Planning/start requires an active
world instance, not one currently unloading or snapshotting.

Each player has a separate selection, keyed by UUID. Console/other senders have
their own selection. Selection settings are kept for the current server process;
**job** checkpoints, not player command preferences, survive a restart.
Both `/pregen` and `/tessera:pregen` use the same selection. The namespaced command
is available if another plugin claims the short command.

A started job captures its dimension's world identity and area. Changing your dimension, shape,
center, radius or mode selection does not retarget an existing job.

## Permission

`tessera.command.pregen` defaults to `OP`. Non-OP players require an explicit
permission grant. Console/RCON use their normal command-sender permissions.
This is a grantable permission, not the additional hard OP restriction used by
Tessera's `/tick` command.

## Commands

| Command | Effect |
| --- | --- |
| `/pregen dimension [overworld\|nether\|end\|namespace:key]` | Show or change the selected loaded dimension |
| `/pregen shape circle` / `square` | Choose a centered shape |
| `/pregen center <x> <z>` | Set the center in block coordinates |
| `/pregen center here` | Use your current location; player-owner context required |
| `/pregen radius <blocks>` | Set a positive radius |
| `/pregen corners <x1> <z1> <x2> <z2>` | Capture an inclusive rectangle |
| `/pregen border` | Capture the current selected world's square worldborder, clipped only to Minecraft's valid coordinate range |
| `/pregen mode balanced` / `fast` | Select the mode for the next job |
| `/pregen plan` | Count target chunks without generating them |
| `/pregen start` | Start a job using your captured selection |
| `/pregen start confirm` | Confirm a job above 10 million target chunks within 60 seconds |
| `/pregen status [id\|all]` | Inspect jobs, progress, rate, estimated remaining time and simulation state |
| `/pregen pause <id>` | Stop new admission and drain admitted generation, writes and owner cleanup |
| `/pregen resume <id>` | Validate world identity and resume a paused/failed job |
| `/pregen cancel <id>` | Drain and cancel; generated world data is **not deleted** |
| `/pregen mode <id> balanced\|fast` | Change an unfinished job's resource mode |

There is one unfinished job per world; different worlds share a bounded global
budget. Finish/cancel an old job before starting another for that world. There
is no arbitrary radius/chunk-count cap; invalid Minecraft coordinates are
rejected. Manual areas are not silently clipped to a worldborder. `border`
captures a selection, not a continuously changing constraint; recapture it after
changing the selected dimension or border. Plans and job status display dimension
keys. The underlying world UUID and checkpoint identity remain unchanged;
previously saved jobs still target their original dimension.

`balanced` gives player work priority and reduces admission under high measured
region tick cost or heap pressure. `fast` increases the CPU-dependent admission
budget but retains memory/backpressure bounds and low-priority native work.
Above 90% measured heap use, admission stops (`WAITING_FOR_MEMORY`) until pressure
falls; admitted work still drains. This is not a substitute for adequate heap or
disk capacity, nor a guarantee against external plugin memory leaks.
Currently the global in-flight ceiling is at most 64 requests; it is **not** a
limit on the job size. Generation/serialization/I/O completion retains an
in-flight slot, so slow disk writes throttle new requests too.
Job history keeps selection/progress metadata, not strong references to completed
or unloaded world instances. Unfinished jobs in an unloaded/replaced world must
be cancelled and replanned before using a replacement in the same process.

## Tick freeze and regions

Pregeneration continues during `/tick freeze`. It uses maintenance tasks and
real elapsed time; it does not issue `/tick step`, unfreeze the server, or change
`unloadFrozenChunks`. The job requests fully generated `FULL` chunks, not
additional entity-ticking tickets. Existing gameplay remains subject to its
normal freeze rules. Generation and chunk-loading/plugin callbacks still occur
as part of the regular pipeline: freeze does not suppress every plugin callback.

Only newly created generation holders without foreign tickets may use the
pregenerator's maintenance-time unload retry. It expires its own retry tickets
on their current owning region, including after region merges/splits. Ordinary
cooldowns and player/plugin tickets are not expired or removed by this feature.
Foreign ticket access restores the ordinary unload policy.

Areas are traversed in MCA-local order without storing every target in memory.
Native neighbour dependencies can generate adjacent partial chunks outside the
selection; this is required for Vanilla terrain/structures/light consistency.
The target count does not include those dependency chunks.

## Saves, checkpoints and restart

Current-version persisted `FULL` chunks are counted as already complete without
activating them. A file/header alone is not enough; native NBT status is checked.
Missing, partial and older-version chunks follow the regular datafix/generation
pipeline, using the target world's existing generator and biome bindings.

Starting/resuming does not admit new chunks before its checkpoint publication
succeeds. Publication errors fail closed instead of leaving a job silently running.

Newly finalized chunks are saved on their owner. The job awaits actual native
chunk/entity/POI write completion, not only serialization, and releases its own
load ticket and task reference before recording completion. `COMPLETED` means
all targets and required receipts/cleanup finished and final job metadata was
published; `COMPLETING` is not the final confirmation.

Metadata lives under the server's world container:

```text
.tessera/pregeneration/<job-id>.json
```

Checkpoints use forced staging-file writes and atomic replacement when the
filesystem supports it, with a replacement fallback. They are **not** an atomic
transaction with Minecraft's region files or a guarantee against all power-loss
damage. After restart/error, jobs resume only on explicit request and revalidate
the whole selection's persisted status. Already complete chunks are read/skipped,
not regenerated. In-process pause/resume retains its confirmed cursor.

Recovery checks UUID, dimension key, folder, seed and generator/biome-source
class identity. Custom plugin-generator configuration is not fully serializable
by this fingerprint: after changing it, cancel and create a new job. A live
world-instance replacement requires a new job even if its name/UUID is reused.
Never manually edit/delete checkpoint files while jobs are running.

## Runtime worlds, snapshots and shutdown

Runtime snapshots, template cloning and world unload acquire a pregeneration
admission lease and drain admitted native work before changing world lifecycle
state. Snapshot completion reopens admission only after the world resumes.
Unload/replacement does not move an old job onto another world. This differs
from ordinary `/tick freeze`, which does not close pregeneration admission.

Clean server shutdown pauses admission, drains writes/tickets and saves job
metadata while region owners still run. The shutdown thread, not a region/global
tick thread, waits for that drain. Native generation already in flight is not
hard-interrupted: cancelled chunks are shared native tasks and can be needed by
players/other jobs. If a native worker or region is genuinely stuck, a timeout is
reported, not a false successful completion.

No public Bukkit pregeneration API was added. The server implementation classes
used by the isolated smoke fixture are internal and not a stable plugin contract.
Chunky checkpoints are not imported automatically.

## Verification

The geometry/checkpoint/dimension-selection suite is `io.papermc.paper.pregeneration.PregenerationTestSuite`.
The connected-client, freeze, persistence and runtime-world fixture is documented
in [the native smoke runner](../smoke-tests/native-pregeneration/README.md).
Actual final-JAR results and limits belong in [Build 015's report](BUILD-26.3-015.md).
