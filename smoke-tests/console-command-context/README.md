# Console and RCON world-context smoke test

This Windows PowerShell test launches the actually assembled Tessera Paperclip
JAR twice.

The first launch writes a plugin command, an explicit-overworld command, and
`stop` to stdin immediately after process creation. It verifies that all three
commands remain buffered until after `Done`, retain FIFO order, and execute
without a null world.

The second launch verifies:

- interactive stdin after `Done`;
- a command without an explicit dimension;
- `execute in minecraft:overworld ...`;
- authenticated RCON;
- plugin commands through RCON;
- region-bound entity selectors in the Overworld, Nether, and End;
- empty, single-result, and multiple-result selectors with radii 8 and 128;
- `if entity`, `as`, `at`, and `kill` selector paths;
- safe unbounded entity selectors and clear rejection of unsafe cross-region
  and unloaded-region selector queries;
- `if block`, `unless block`, and block-tag predicates from Console and RCON;
- loaded block reads in the Overworld, Nether, and End;
- the read-only `data get block` path and clear rejection of unloaded block
  regions;
- absence of entity-query thread checks, block-query thread checks, and the
  null `captureTreeGeneration` world-data failure;
- clean shutdown.

Run with Java 25:

```powershell
.\smoke-tests\console-command-context\run.ps1 `
    -JarPath .\build\libs\tessera-server-26.2.build.014-stable.jar `
    -SkipBuild
```

Without `-SkipBuild`, the script builds the test plugin and Paperclip JAR first.
Startup is bounded to 120 seconds, command responses to 10 seconds, and shutdown
to 30 seconds. Progress is emitted every five seconds and timeout failures
include the latest server log.
