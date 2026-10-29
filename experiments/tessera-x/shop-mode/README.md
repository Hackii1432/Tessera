# Automatic 1x1 shop experiment

The feature is `experimental-options.automatic-shop` in `config/tessera-x.yml`.
It defaults to false; with the master switch and this option enabled, selection
of individual villagers is automatic. Only a proven one-block horizontal cell
qualifies. Native Brain ticks, working and restocking continue unchanged.

The implementation memoizes empty `AcquirePoi` results only if no position
predicate ran. It checks all 49 POI chunk identities/revisions at the next native
search attempt. It preserves retry scheduling, RNG calls, candidate selection,
positive searches, ticket reservations, memories and events. No asynchronous
access or global village/block watcher is introduced.
Only native HOME and MEETING type predicates opt in; public/custom `AcquirePoi`
factories retain their original behavior. Snapshots contain only primitive IDs
and revisions, retaining no worlds, entities, chunks or POI records.

The geometry proof supports full collision cubes and actual native trapdoor
cuboids, including an upper trapdoor over an otherwise open entrance. It checks
both walking and the swept height range of an ordinary villager jump. OPEN or
CLOSED alone is not a decision rule. Uncertain geometry, 2x1 rooms, children,
vehicles, water and modified jump/gravity use the original search.

## Native test laboratory

Sources and scripts are copied into an isolated workspace under
`work/shop-live-2026-10-29`. The laboratory uses an official checksum-verified
Vanilla 26.3 bundler and a newly built Tessera-X bundler. Instrumentation adds
scene inputs and observers; it does not replace Brain/entity ticks, RNG, clocks,
working, restocks or watchdog behavior.
The local Tessera test configuration sets villager activation range to zero;
each spawned villager is asserted to be fully active. This makes the no-player
laboratory comparable to Vanilla rather than measuring inactivity savings.
No production configuration is changed.

Functional fixtures include a solid 1x1 cell, an open-front cell with an upper
trapdoor and a workstation at floor level, workstation/profession replacement,
POI ticket changes without block changes, and escape after removing an entrance
barrier. Restocks run on autonomous native ticks. Offers are exhausted by
changing the native offer use counter; this is not a connected-player trading
GUI test. World time is changed through the native command as a next-day input;
game ticks and restock cooldowns are not replaced or advanced artificially.

Stress scenarios use 256, 1,024 and 4,096 villagers, followed by a POI/Redstone
churn workload and 1,024 villagers in enclosed 2x1 rooms. Every scenario warms
for ten wall seconds, then measures at least fifteen seconds and ten actual
autonomous world ticks. Actual sample counts are recorded; the overload case
is not a steady-state or reliable p99 measurement. Report both total process CPU and elapsed world-tick time,
including detection, version checks and invalidation. Profile sampling or fewer
queries alone does not establish a server performance improvement.

See [the report](RESULTS-2026-10-29.md) and `evidence-2026-10-29/` for native
results, hashes, measured costs and limitations. The supplied normal bundler
contains no laboratory instrumentation. This experiment stays on the owning
region thread; it does not move entity ticks to a worker pool.

## Reproduce

Use JDK 25 and Python 3 with an official Vanilla 26.3 server bundler and a freshly
built Tessera-X bundler. The Vanilla SHA-1 is checked before launching the lab.
The wrapper requires a new work directory and never connects to production.

```powershell
.\Run-Shop-Lab.ps1 -TesseraBundler C:\artifacts\tessera-x.jar `
  -VanillaBundler C:\artifacts\vanilla-26.3.jar -JavaHome C:\jdk-25 `
  -Python C:\python\python.exe -WorkDirectory C:\lab\shop-comparison
```

The serial matrix includes full native functional tests for Vanilla, disabled
Tessera-X and enabled Tessera-X, plus separate stress repeats for each variant.
The trade GUI, arbitrary plugins and all possible geometries are outside this
fixture's coverage. See `REVIEW-PROMPT-DE.txt` for the handoff to the Tessera AI.
