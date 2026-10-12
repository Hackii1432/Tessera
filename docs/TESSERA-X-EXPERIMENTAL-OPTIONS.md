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
