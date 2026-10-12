# Tessera-X region recalculation pilot

Feature patch 0067 adds an opt-in age trigger for an existing batch of dead
region sections. Enable with `-Dtessera-x.region-recalc-max-delay-ms=1000`.
The default is zero (disabled); positive values are clamped to 100–60,000 ms.
This internal experiment does not add a Bukkit API or a live split command.

The original merge, all-dead and dead-percentage cleanup triggers remain.
When a non-empty dead-section batch reaches its age threshold, the next safe
region release may also perform cleanup and the original connectivity search.
This executes under the regionizer write lock after pending merges are resolved.
An active lease or unresolved merge can extend the actual waiting time.

The first dead section timestamps a new batch. An emptied or fully revived
batch starts a fresh timestamp when another section dies. Merge transfers
preserve the older source/target batch age. Only sections already classified as
empty and without non-empty neighbours are removed. Safety radii, active chunk
ownership, split callbacks and clock/queue migration remain native.

At grid exponent 1 the normal cleanup gate requires 512 owned sections and
at least one sixth dead sections. The pilot bypasses this batching gate; it
does not expire valid tickets or divide a connected remaining graph.

Diagnostics are process-wide counters for cleanup passes, extra deadline
passes, split passes, examined/removed sections and cleanup/BFS time. That time
does not include later region-data migration. The native lab run observed
a maximum of about 13.14 ms under the write lock on the local CPU. Simultaneous
deadlines on many large regions need further budgeting and testing.

The lab validates the actual regionizer using eight controlled ownership cases,
including an active lease, revival, negative coordinates and pending merge age
transfer. A real-server fixture releases normal plugin chunk tickets and checks
actual region splits, two repeater circuits, pulse traces, update latency and
region clocks. Disabled/reference and enabled builds use identical runtime
fixture contents. Sensor processing stays disabled in this split comparison.

The native comparison observes the reference still unsplit at 45 seconds after
ticket release; the pilot has split by the poll at roughly 1.50 seconds. These
are finite observation windows, with five global ticks between polls. Production
has not been contacted. This prototype has not received a new full JUnit run or
large-world/community-plugin validation. It remains disabled by default.

The lab report is `../outputs/Tessera-X-Regionssplits-und-Ausbau.md` relative to
the source checkout, with machine-readable evidence in
`../outputs/Tessera-X-Regionssplits.json`. Export generated-source changes as
feature patches before reapplying the patch stack; follow `SINOPIA-WORKFLOW.md`.
