# Runtime-world server smoke test

This test launches the actually assembled Mojang-mapped bundler with the
`test-plugin` artifact. The default matrix runs:

1. the full create/flat/load/save-reload/2-4-16-clone/player-if-present/
   no-save-unload/Windows-delete/20-cycle suite;
2. server stop during create;
3. server stop during clone;
4. server stop during unload.

From the repository root on Windows:

```powershell
.\smoke-tests\runtime-world-lifecycle\run.ps1
```

Use `-FullOnly` for the functional suite only and `-KeepRuns` to retain prior
run directories. Every run writes `tessera-smoke-result.json` next to its
server log. The script refuses to delete paths outside its own
`smoke-tests/runtime-world-lifecycle/build/runs` directory.

When a player is online during the full run, the plugin additionally verifies
per-player scoreboard assignment/restoration, entity scoreboard tags,
cross-world `teleportAsync`, and the controlled `PLAYERS_PRESENT` unload
failure. Without a player, that interactive portion is reported as skipped;
the model-level 50-player concurrency test remains part of the automated
server unit suite.
