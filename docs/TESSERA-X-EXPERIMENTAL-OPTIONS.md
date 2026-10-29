# Tessera-X experimental options

The server creates `<paper-settings-directory>/tessera-x.yml` at startup.
The default location is `config/tessera-x.yml`; `--paper-dir` also applies.
All changes require a complete server restart. `/reload` does not apply these
options. The snapshot loads before Minecraft bootstrap and plugin initialization,
so the existing constant feature switches remain available to the JIT.

## Disable a problematic feature

Set its key under `experimental-options` to `false` and restart. To disable
everything while retaining individual selections:

```yaml
experimental-options:
  enabled: false
```

An explicit YAML value overrides the corresponding old `-Dtessera-x.*`
property. Missing values inherit the old startup properties and are saved into
the file for compatibility with existing laboratory launchers. Without legacy
properties, a newly generated file has every individual feature disabled.
`enabled: true` only permits selected features; it does not enable them all.

Malformed YAML, unsupported versions, wrong types and out-of-range numeric
settings fail startup instead of silently enabling features. Invalid files are
not overwritten. A second initialization after feature access is rejected.

## Suggested local validation preset

Copy `experiments/tessera-x/config/tessera-x-recommended.yml` to the server's
configuration directory. It enables the proposed next-patch candidates:

```yaml
_version: 1
experimental-options:
  enabled: true
  sensor-sorting: false
  sensor-workers: 2
  sensor-adaptive: false
  sensor-reuse: false
  verify-sensors: false
  region-recalculation: true
  region-recalc-max-delay-ms: 1000
  region-covered-search: true
  brain-scratch: false
  sensor-local-conditions: true
  poi-scratch: true
  poi-reservation-check: true
  automatic-shop: false
  work-timings: false
```

The preset is an explicit supplied file. It does not activate itself or change a
production server. Keep sensor workers experimental; if testing them, also enable
adaptive preparation and reuse. Brain scratch has no established general tick-time
benefit. Verification and work timings are diagnostic features.

`sensor-workers` accepts 0–8. Zero disables the worker pool while retaining direct
experimental sorting if selected. A false master switch starts no sensor pool.
The region interval accepts 100–60000 ms and is used only when
`region-recalculation` is enabled. The previous zero JVM delay still means disabled.

Earlier cleanup does not expire active tickets or separate live connected
sections. POI scratch does not cache beds, occupancy or query results. The
reservation guard protects the final state change after a reentrant callback.

## Automatic 1x1 shop cells

`automatic-shop: true` enables automatic per-villager recognition; names, tags and
manual assignments are not required. The master switch must also be enabled.
The option defaults to false and requires a restart when changed.

This experiment leaves the entire native Brain, work, trades, restock timing,
profession changes, pathfinding decisions and POI retry scheduling running.
It avoids repeating an empty `AcquirePoi` search only when that search did not
evaluate any position predicate and its POI chunk identities and revisions are
unchanged. Searches with available candidates or retry predicates remain native.

Recognition is conservative: an ordinary adult must be on a full floor, contained
in one block column, with a job-site memory and collision barriers on all
four sides. Full blocks and native trapdoor collision cuboids are supported.
An open-front cell with an upper trapdoor and a workstation under the villager
is included; OPEN is not itself the criterion. Closed and open trapdoors count
only where their actual shape blocks the exit. A genuinely larger or passable
room is not classified as a 1x1 cell. Nonstandard jump/gravity, babies, vehicles,
water and uncertain shapes use the native path.

Cell geometry and region ownership are checked at the next regular POI attempt,
not by an extra per-tick cage scan. No Brain is put to sleep. Block/piston/trapdoor
changes are therefore observed before skipping a subsequent search. Every POI
mutation, including ticket acquisition/release, changes its chunk revision;
unloaded/replaced chunks invalidate by identity. No global block-event broadcast
or scan over all villagers is added. All search chunks must belong to the current
region and be loaded before a result can be reused.

See the native laboratory and evidence in `experiments/tessera-x/shop-mode/`.

## Validation

`Run-Experimental-Options-Check.ps1` exercises generated defaults, roundtrips,
legacy precedence, all individual false values, master disable, partial configs,
unknown-entry preservation, validation and restart-only snapshots. Native
`Run-Experimental-Options-Smoke.ps1` checks actual feature-class constants,
worker-thread creation and region deadlines across:

- `recommended`: selected features on, conflicting JVM booleans off, custom config directory;
- `all-off`: all YAML booleans false despite true JVM properties;
- `master-off`: all individual settings true but the common switch false;
- `all-on`: every YAML feature true despite false JVM properties;
- `default-generated`: no experimental config file or JVM overrides.

All native tests use fresh isolated loopback worlds. Existing native fixtures
read the effective configuration when available, with fallback to the older JVM
properties for reference artifacts.

```powershell
.\experiments\tessera-x\tools\Run-Experimental-Options-Check.ps1 `
  -BundlerPath C:\artifacts\tessera-x-bundler.jar -JavaHome C:\jdk-25
.\experiments\tessera-x\tools\Run-Experimental-Options-Smoke.ps1 `
  -Mode master-off -BundlerPath C:\artifacts\tessera-x-bundler.jar -JavaHome C:\jdk-25
```
