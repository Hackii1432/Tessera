# Tessera-X experimental validation

The following measurements describe features through 0074. Feature 0075 adds
startup YAML control; its additional regression evidence is recorded in
`tessera-x-config-validation.json` and `TESSERA-X-EXPERIMENTAL-OPTIONS.md`.

This branch contains opt-in local experiments based on Tessera 019 at
`0e31a2cab94f1f8e06921a7872b962bd027d25f7`. It is not a production release.
Feature 0066 handles nearest-living-entity distance ordering; feature 0067
advances safe dead-section cleanup and the existing connectivity search.
Features 0068–0074 add adaptive preparation, bounded buffer reuse, diagnostics,
running-behavior snapshots, covered-row connectivity search and independent
sensor targeting ranges, primitive POI search scratch and a final reservation
guard. See `TESSERA-X-WORK-PILOTS.md` for contracts and
reproducible local test commands.

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
build reported 10901 tests (89 skipped) across its build tasks. The new
`:folia-server:test` run reports 10369 server tests, 87 skipped, zero failures
and zero errors, with experimental switches at their default values. These
counts describe different task scopes. Enabled features are additionally
covered by the native fixtures. Native logs had only previously known Windows
startup diagnostics. The live community server was not contacted.

## Additional native correctness checks

- Covered-row search: 9063 differential assertions, including exact component
  and BFS discovery order, negative coordinates, integer wrapping, fragmented
  layouts and multiple radii; tested against the built candidate classes.
- Actual regionizer: eight cases, 4507 assertions with covered search enabled.
- Real chunk tickets, region data migration and repeater circuits: 3008
  assertions each with covered search enabled and disabled, on the same build.
  The split is observed at about 1.50 seconds in both runs; covered search
  reduces search work, not the ticket-release delay.
- Brain callbacks: captured snapshots survive behavior-map mutations and
  stopping callbacks; recursive calls preserve order; exceptions clear the
  private buffer; 300-entry snapshots are not retained. Public snapshots remain
  independent. Enabled and disabled traces match.
- Sensor targeting ranges: deterministic interleaving on two different native
  region threads reproduces the shared-range bug with the feature disabled.
  With the fix enabled, a 64-block body detects its target at 32 blocks while
  the 16-block body does not. A same-thread query after another body's sensor
  also uses the querying body's range correctly.
- Sorting QA: dense and deliberately changed candidates match all native
  stable-sort oracles. The adaptive pilot submits no jobs in the unused-NoAI
  fixture while preserving native entity ticks.

The supplied i9 laboratory has no community clients and does not establish
dual-Xeon VMware performance or compatibility with all plugins and contraptions.

## POI follow-up

The final candidate includes features through 0074 and again passes the complete
server JUnit suites with default switches. A native POI fixture checks 200 seeded
queries with identical returned positions and predicate invocation order in both
modes, recursion, predicate exceptions, removal, oversized-buffer eviction and a
real reachable bed. There are 485 assertions with the features off and 486 with
scratch and reservation checks on. Both normal assignment and release succeed.
A callback claims the selected bed before final acquisition: the legacy mode
still saves HOME, while the guarded mode does not.

Empty-query allocation drops from about 72.7 kB to 7.0 kB. An additional separate
diagnostic pair uses native mixed-mob AI on the same final JAR. Mean query
allocation is 72544 versus 6856 bytes; query wall time is 0.192 versus 0.129 ms.
Entity-phase allocation is 1.87 versus 1.15 MB/tick. This is one instrumented
pair, not another uninstrumented general server-speed claim. Search counts differ
slightly (1963 versus 1949), so query figures are normalized per call.

The original performance matrix uses the candidate through 0072; final POI/native
checks and JUnit use the candidate through 0074. `tessera-x-work-results.json`
records the measurements, scopes and artifact/patch hashes. Early unsuccessful
POI-fixture attempts were corrected for missing reachable ground and duplicate
registration by actual bed placement; they are excluded from passing evidence.

Fixtures are saved in `experiments/tessera-x/` for independent code review and
adaptation to another local harness. Local measurement artifacts and runs stay
outside this source repository. Publishing a code experiment does not establish
production readiness.
