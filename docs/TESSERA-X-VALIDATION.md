# Tessera-X experimental validation

This branch contains opt-in local experiments based on Tessera 019 at
`0e31a2cab94f1f8e06921a7872b962bd027d25f7`. It is not a production release.
Feature 0066 handles nearest-living-entity distance ordering; feature 0067
advances safe dead-section cleanup and the existing connectivity search.

## Sensor pilot

Twelve native local measurement runs covered reference Tessera and Tessera-X
with 0, 1, 2, 4 and 8 compute workers (two runs per variant). Each workload used
120 warmup and 200 measured ticks. Fixed seeds, fixtures and other thread budgets
were identical. With two workers, dense controlled sensor tick time changed from
32.03 to 11.86 ms. With deliberately changed snapshots, the corresponding values
were 32.15 and 19.37 ms; direct cached sorting without workers was faster in that
case at 15.69 ms. Normal mixed AI changed from 5.43 to 5.34 ms, which does not
establish a general server speedup. Snapshot preparation, allocation and unused
or rejected speculative work require further optimization.

Native sorting oracles, stable ties/edge values, changed-input fallbacks and the
existing 256/512/1024-block redstone merge fixture were exercised. Read
`TESSERA-X-SENSOR-PILOT.md` for the ownership contract and defaults.

## Region recalculation pilot

Eight controlled actual-regionizer cases cover delayed cleanup below the size
and dead-ratio gates, connected bridges, negative coordinates, held leases,
revival, all-dead retirement, no topology changes and pending merges. Disabled
Tessera-X matches the reference region counts. Enabled cleanup split the two
delayed cases in approximately 1.03 seconds.

A real local server fixture removes ordinary plugin chunk tickets, with natural
holder release and native region data migration. The reference keeps 6160
sections, 396 of them dead (6.43%), until the last observation 45 seconds after
ticket removal. Removing the dead sections would already give two components.
Tessera-X has split by the observation at approximately 1.50 seconds. Sampling
uses five global ticks; this is not the exact internal split timestamp.

Each server run checks two 16-repeater circuits, ten pulses, all 320 edges per
circuit, update latency, pulse widths and continuous redstone clocks; 3008
assertions pass per run. Comparator, observer, piston, hopper, sculk and community
plugin compatibility are not established by these circuits. Maximum measured
cleanup/BFS time was about 13.14 ms under the regionizer write lock on the local
i9 CPU, excluding later region-data migration. Larger topology/churn tests and
the slower dual-Xeon VMware environment remain to be measured.

These pilots remain disabled by default. The preserved original reference
passed 10901 JUnit tests (89 skipped), but a new full JUnit run has not yet been
claimed for this experimental revision. Native logs had only previously known
Windows startup diagnostics. The live community server was not contacted.

Fixtures are saved in `experiments/tessera-x/` for independent code review and
adaptation to another local harness. Local measurement artifacts and runs stay
outside this source repository. Publishing a code experiment does not establish
production readiness.
