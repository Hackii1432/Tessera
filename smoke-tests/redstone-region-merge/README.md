# Live redstone / region merge test

Local measured results: [16 September 2026 report](RESULTS.md).

This Windows test compiles a small plugin against an already assembled Tessera
bundler and launches that exact server on loopback with a fresh, disposable
world. It does not rebuild or modify the server, existing worlds, or production
configuration. JAR dependencies are extracted from the bundler locally.

```powershell
.\smoke-tests\redstone-region-merge\run.ps1 `
    -JavaHome 'C:\Program Files\Java\jdk-25.0.3' `
    -Repetitions 3
```

The default artifact is
`folia-server/build/libs/folia-bundler-26.3.build.005-alpha.jar`.
Use `-BundlerPath` for another compatible build. This plugin uses internal
server classes for read-only instrumentation, so compilation also checks
whether those APIs still match. Java needs normal local filesystem access;
Windows restricted-token sandboxes may reject Java's `toRealPath` calls.

## Scenario

Three independent pairs of circuits run in parallel, using the default region
grid exponent of 4 and four region threads:

| Circuit length | Repeaters per circuit | Merge requested at local test tick | Extra delay |
| --- | --- | --- | --- |
| 256 blocks | 64 | 12 | none |
| 512 blocks | 128 | 47 | none |
| 1,024 blocks | 256 | 95 | 65 ms each tick in circuit B's region |

Each straight circuit contains a repeater at delay setting 1 followed by three
redstone dust blocks. A and B are 2,048 blocks apart. Each pair runs five
different-width input pulses first without a merge, then again while chunks
are loaded along a connecting corridor. The server's normal regionizer
performs the merge; the test never invokes merge internals or edits clocks.
The delayed region exercises merges between clocks advancing at different
speeds. Artificial delay is also present in its baseline.

**A fully loaded, connected redstone line already belongs to one ticking
region.** Circuit length does not force separate regions. This test joins two
previously independent regions while both contain running long circuits. It
does not claim to send redstone through unloaded chunks or between regions
that remain independent.

## Assertions and evidence

- Distinct region IDs before the merge; identical IDs afterwards.
- An observed circuit ownership change with pending block updates, and a
  nonzero redstone-clock offset during ownership changes.
- Every circuit chunk stays owned, loaded, and block-ticking at each sampled
  tick throughout both runs.
- Every repeater produces exactly five rising and five falling edges.
- Complete repeater event traces match the corresponding baseline, including
  order, local tick, polarity, and pulse duration.
- Baseline timing also matches the independently calculated repeater delay.
  The input task runs before the world's time increment, so task-based
  timestamps show the first repeater at input+1, subsequent stages +2 each.
- No repeater remains powered after settling; no pending block ticks remain.
- Successful server exit, clean shutdown, and no unexpected error, thread-check,
  or watchdog messages in the log. One specifically identified OSHI startup
  diagnostic about unavailable Windows performance counters is recorded
  separately in `log-validation.json`; other errors still fail the test.

`build/runs/<timestamp>-<iteration>/redstone-merge-result.json` contains the
full traces, output edges, region changes, observed clock offsets, pending
update counts, durations, and server version. `invocation.json` identifies the
bundler, plugin, and source hashes. Logs and worlds are retained for inspection.
Every invocation creates new run directories. Overall execution is bounded
by `-TimeoutSeconds` (default 600 seconds per server process), with a separate
eight-minute plugin timeout.

## Scope

This verifies dust and unlocked repeaters under natural merges, including a
slow source or destination region. It does not cover comparator feedback,
repeater locking, observers, pistons, torch burnout, chunk unloading, region
splits, player activity, or all possible event-order races. Tick timing and
wall-clock timing are reported separately: merging with a slow region can
make the circuit slower in real seconds without changing its logical delays.
