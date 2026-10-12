# Tessera-X: additional work and connectivity pilots

These experiments build on Tessera 019 (`0e31a2c`) and patches 0066/0067.
Every new switch defaults to `false`. Enable individual switches explicitly
when testing. Production deployment has not been requested.

## Implemented features

| Patch | Switch | Contract and expected benefit |
| --- | --- | --- |
| 0068 | `-Dtessera-x.sensor-adaptive=true` | Prepare nearest-living sensors only after an actual consumer has been observed. After three unsuccessful speculative jobs, pause preparation for 16 actual sensor calls. Native sensor calls and their cadence continue normally. |
| 0068 | `-Dtessera-x.sensor-reuse=true` | Reuse bounded merge-sort scratch arrays and validated owner-side candidate arrays. Reduce allocation without retaining world/entity references on workers. Requires the sensor pilot for native sensor sorting. |
| 0069 | `-Dtessera-x.work-timings=true` | Inclusive process-wide wall-time counters for brain phases, goals, path search, navigation, visibility, POI search, spawning, entity tracking, hoppers and redstone wire updates; optional entity-phase allocation totals. Diagnostics, not an optimization. |
| 0070 | `-Dtessera-x.brain-scratch=true` | Reuse the internal running-behavior snapshot. Preserve captured order and callback semantics, retain fresh public lists, fall back on recursive calls, clear on exceptions and discard snapshots above 256 entries. |
| 0071 | `-Dtessera-x.region-covered-search=true` | Avoid repeatedly probing already searched coordinates during region connectivity BFS. Preserve component and node discovery order; retain native search at radius one and for small components. |
| 0072 | `-Dtessera-x.sensor-local-conditions=true` | Use private thread-local copies of the six sensor targeting conditions, with range taken from the querying body. Fix concurrent regions overwriting each other's range and same-thread queries inheriting another body's range. |
| 0073 | `-Dtessera-x.poi-scratch=true` | Reuse primitive BFS queues and visited-coordinate sets during nearest-POI queries, with reentrancy fallback, exception cleanup and eviction above 8192 visited coordinates. Query order, chunk/POI loading, filters and reservations remain native. |
| 0074 | `-Dtessera-x.poi-reservation-check=true` | Store acquired POI memory only after the final reservation succeeds. Prevent stale acquisition after a callback has already claimed the selected POI. |

Sensor flags normally accompany `-Dtessera-x.sensors=true` and an explicitly
chosen `-Dtessera-x.sensor-workers=N` (0–8). Adaptive preparation is relevant
when workers are enabled. No worker reads or writes the world, Bukkit entities,
brain memories or targeting conditions. Only immutable distance arrays are
sorted off the owning region thread. A changed or unfinished result falls back
within the original sensor call, without waiting or delaying gameplay.

The targeting-condition fix intentionally corrects behavior in the previously
incorrect range cases. It does not make world queries safe on arbitrary threads.

## Region search versus earlier splitting

Patch 0067's `-Dtessera-x.region-recalc-max-delay-ms=1000` permits safe cleanup
after continuously dead sections have aged for one second, at a subsequent safe
region release. Patch 0071 reduces the connectivity search work performed during
that cleanup. These are separate changes. Neither expires an active chunk
ticket nor splits across live connected sections. Pending merges and the end of
an in-progress tick still constrain when cleanup can run.

Covered-row search activates only after a component reaches 64 discovered
sections and the search radius exceeds one. It stores searched intervals per
row, preserving native BFS traversal order. Some fragmented layouts allocate
more temporary interval data; measure topology/churn on the target machine.
Cleanup and search still run under the native regionizer lock; region-data
migration is not included in the search timing counter.

## Diagnostics semantics

`dev.tessera.x.WorkTimings.metrics()` returns cumulative
`profile.<phase>.calls`, `.nanos` and `.allocatedBytes` values. Take differences
between snapshots. These are process-wide counters, not per-region samples,
CPU usage or a new command. Nested measurements are inclusive: do not add
`brain.running` to `brain.snapshot`, or goal selection to its nested running
goal time. Entity allocation is available only with a supported, enabled
HotSpot thread-allocation counter. Disabled diagnostics perform no clock reads.
The POI query counter also records query allocation directly, allowing comparison
per query even when the number of naturally scheduled searches differs.

Keep diagnostic runs separate from uninstrumented performance comparisons.
Sensor verification also adds oracle work and must not be used for speed claims.

## Scope of further parallelism

- **Full mob ticks / normal AI:** shared entity state, collisions, combat,
  memories, inventories, block changes and callbacks retain owner-thread
  semantics. Randomly assigning mobs to workers violates that contract.
- **Path search:** the current node evaluator depends on a live mob and world.
  A future worker implementation needs immutable block/collision snapshots,
  captured movement rules and owner-thread validation/commit. Measure path
  search first; the present mixed-mob fixture did not exercise it sufficiently.
- **Visibility, POI and spawning:** potentially useful immutable calculations,
  but candidate collection, reservations, randomness, events and final state
  changes must preserve timing and order. The present changes add measurement.
- **Hoppers:** wake conditions must include inventory mutations, item entities,
  redstone and cooldowns before sleep/wake optimization can preserve behavior.
- **Redstone machines:** pistons, observers, comparators, fluids, inventories
  and immediate neighbor updates cross any proposed machine boundary. A separate
  machine thread requires an explicit ownership/interaction protocol. Existing
  wire implementations already optimize propagation. The tested improvement
  here concerns safe regional separation and search cost, not parallel mutation
  of a connected circuit.

No full AI, pathfinding, hopper, spawn, tracking or redstone tick has been moved
to arbitrary worker threads. These need measured evidence and a larger ownership
design before implementation is justified.

## POI query scratch and bed reservations

The scratch buffer stores only temporary primitive coordinates. It does not
cache beds, query results, occupancy or a one-second snapshot. Native searches
continue at their original times. A future shared candidate index must track
POI creation/removal, chunk loading/unloading and reservation changes, while
retaining per-body range, reachability, retry-filter side effects and tie order.

An unassigned villager's generic `AcquirePoi` behavior schedules another search
after 20–39 region ticks. HOME acquisition is a core activity, including daytime;
the memory must be absent. Beds use one POI reservation ticket, independent of
the bed block's `OCCUPIED` sleeping state. Workstations and meeting points have
their own acquisitions. Baby villagers additionally have a nearest-bed sensor
whose default period is 20 ticks. Existing configuration and inactive-AI/tick
scheduling can affect actual wall-clock execution.

The reservation test creates a real reachable bed and uses a validation callback
to claim it between candidate search and the original acquisition. The old code
ignores the failed `take()` result and saves HOME anyway. The optional guard
prevents that assignment. Normal successful acquisition, exclusion from free
candidate results and release are tested separately.

## Reproduce the local checks

Use JDK 25 and **bundler** JARs, not Paperclip launcher JARs. The scripts under
`experiments/tessera-x/tools/` accept `-JavaHome`, `-BundlerPath`, and optionally
`-ReferenceBundlerPath`. Generated labs live in ignored `build/tessera-x-lab/`.
All native servers bind `127.0.0.1`, request an ephemeral port and disable RCON.
Never put these fixture plugins into a production server.

```powershell
$jdk = 'C:\path\to\jdk-25'
$candidate = 'C:\artifacts\tessera-x-bundler.jar'
$reference = 'C:\artifacts\tessera-019-bundler.jar'

# Differential algorithm test: 3000 seeded layouts plus fixed/benchmark cases.
.\experiments\tessera-x\tools\Run-Connectivity-Comparison.ps1 `
  -JavaHome $jdk -BundlerPath $candidate

# Native controlled regionizer and real chunk-ticket/redstone checks.
.\experiments\tessera-x\tools\Run-Region-Split-Comparison.ps1 `
  -JavaHome $jdk -Variant covered -BundlerPath $candidate -DelayMs 1000 -CoveredSearch
.\experiments\tessera-x\tools\Run-Native-Region-Split.ps1 `
  -JavaHome $jdk -Variant covered -BundlerPath $candidate `
  -ReferenceBundlerPath $reference -DelayMs 1000 -CoveredSearch

# Brain callback/snapshot checks and deterministic inter-region range interleaving.
.\experiments\tessera-x\tools\Run-Brain-Safety.ps1 `
  -JavaHome $jdk -Variant fixed -BundlerPath $candidate `
  -ReferenceBundlerPath $reference -BrainScratch -LocalConditions

# Sorting oracles and adaptive preparation, including unused NoAI sensors.
.\experiments\tessera-x\tools\Run-Sensor-Comparison.ps1 `
  -JavaHome $jdk -Variant qa -BundlerPath $candidate `
  -ReferenceBundlerPath $reference -ComputeWorkers 2 -WarmupTicks 30 -MeasuredTicks 60 `
  -VerifyAllSensors -Adaptive -Reuse -BrainScratch -LocalConditions

.\experiments\tessera-x\tools\Run-Poi-Safety.ps1 `
  -JavaHome $jdk -Variant fixed -BundlerPath $candidate `
  -ReferenceBundlerPath $reference -PoiScratch -ReservationCheck
```

For baseline range reproduction, repeat the brain fixture without
`-BrainScratch -LocalConditions`. Its PASS then means the expected legacy
failure was reproduced; inspect the two `wideRange...Seen` fields, which become
`true` in the fixed run. Performance comparisons need identical fixtures,
warmup and budgets, sequential runs, reverse ordering, and no verification or
diagnostics. Compare on the same host; local i9 measurements do not predict
dual-Xeon VMware gains.
