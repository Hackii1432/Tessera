# Native pregeneration acceptance fixture

Run from the repository root after building the server and `:test-plugin:jar`:

```powershell
node smoke-tests/native-pregeneration/run.mjs 'C:\Program Files\Java\jdk-25.0.3\bin\java.exe' build/libs/tessera-server-26.3.build.015-beta.jar
```

The runner creates its own marked disposable directory under `build/`, selects a
free loopback port and copies only launcher caches and the test plugin. Existing
worlds, player stores and production plugins are not used. It starts two real
offline protocol clients using packet IDs from the current native sources.

Two starts of the same fixture exercise permission/default-OP behavior, separate
player dimension selections and command aliases, dimension-key/standard-alias
selection and tab completion, rejection of folder names/unloaded dimensions,
unchanged targets after selection changes, native generation across regions/dimensions
while frozen, existing-FULL skip, pause/resume/cancel, custom dimension selection/generation,
runtime unload, snapshot coordination and paused checkpoint recovery/revalidation.
It verifies holder retirement during freeze, injects a native per-chunk no-save
failure (which must fail the job after cleanup), resumes/revalidates that area,
and stops a server while a Nether job has admitted work. Restart must find that
job paused with saved progress, not orphaned admitted work. Recovery also exercises
the higher `fast` admission budget. A separately blocked checkpoint path must
reject resume without admitting any new chunks, then allow successful recovery.
Only these specifically asserted injected failures are allowed; unexpected job
failures still fail the runner.
Assertions inspect the native service's immutable results through reflection,
not a newly advertised plugin API. The second run loads saved chunks with
generation disabled. Both phases require two logins and zero pre-result quits/kicks.

`result.json` contains the exact JAR SHA-256, modes, checks and checkpoints. Logs
remain alongside it. Results are evidence only if the runner exits successfully;
neither a start log nor a printed status alone is a passing result.

The clients acknowledge transfers and keepalives but do not render Minecraft.
This fixture is not a large-world throughput benchmark or an acceptance test of
MCC/MVE/Chunky/other production plugins, disk-full recovery or hard power loss.
