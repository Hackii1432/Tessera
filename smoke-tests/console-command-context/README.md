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
- clean shutdown.

Run with Java 25:

```powershell
.\smoke-tests\console-command-context\run.ps1 `
    -JarPath .\folia-server\build\libs\tessera-server-26.2.build-010-stable.jar `
    -SkipBuild
```

Without `-SkipBuild`, the script builds the test plugin and Paperclip JAR first.
Startup is bounded to 120 seconds, command responses to 10 seconds, and shutdown
to 30 seconds. Progress is emitted every five seconds and timeout failures
include the latest server log.
