# Tessera-X Sensor Pilot

The first experimental Tessera-X build derives from Tessera 019 at
`0e31a2cab94f1f8e06921a7872b962bd027d25f7`. The server manifest identifies
Tessera-X; the preserved reference artifact retains the Tessera brand.
This pilot optimizes nearest-living-entity sensor distance ordering. It does
not introduce concurrent entity ticks or redstone machine ownership.

Enable explicitly with `-Dtessera-x.sensors=true`. Configure the bounded
compute pool with `-Dtessera-x.sensor-workers=0`, `1`, `2`, `4`, or `8`.
Zero uses cached distance keys and stable primitive sorting directly on the
owner. Positive counts also prepare due sensor sorts before the entity phase.
The feature remains disabled by default.

Workers receive primitive distance arrays only. Entity references remain in
a table scoped to the current owner thread and entity phase. At the original
sensor call, the normal native candidate query is repeated. The owner validates
candidate identity, enumeration order and every current distance before
applying a completed permutation. A changed or unavailable result uses direct
sorting immediately. Memory publication stays at the original call point;
sensor frequency and region tick rate are unchanged.

Each phase has at most 128 prepared jobs and 262,144 distance keys. The shared
pool has a bounded queue and rejects excess work. No worker reads mutable world
or entity state. Phase cleanup cancels outstanding results without waiting;
already running pure computations may finish. Merge or split cannot occur
during the acquired tick lease; the owner table is removed before that lease
ends. No result is retained for a later tick.

The durable Minecraft feature patch is `0066`. Build changes live in the
existing `folia-server/build.gradle.kts.patch`. Export future generated-source
edits using the workflow in `SINOPIA-WORKFLOW.md` before reapplying patches.

The comparison fixture outside this checkout uses the actual native sensor
and native region scheduler. Two controlled phases amplify the sensor
workload; a third phase runs normal mixed mob AI. There are no protocol clients
or community plugins. Validation checks native ordering, stable ties and
changed snapshots. Whole-window worker thread CPU counters are necessary on
Windows because individual short jobs can fall below CPU timer resolution.

The comparison report and raw evidence are saved under the lab's `outputs`
directory. Results from the local CPU cannot establish the exact speedup on
the production Xeon VMware guest. Productive deployment is outside this
experiment's authorization.
