# Native data command acceptance

Use the normal root build with Java 25; it also builds the test plugin in its
compiled phase. Do not append a second outer test-plugin build to that task graph.
Then run:

```powershell
.\gradlew.bat buildTessera --console=plain --max-workers=2 --no-parallel
node smoke-tests/data-command/run.mjs "C:\Program Files\Java\jdk-25.0.3\bin\java.exe" build/libs/tessera-server-26.3.build.017-beta.jar
```

The runner creates a fresh disposable fixture under `build/data-command-*`,
reserves loopback Minecraft/RCON ports, generates an ephemeral RCON credential,
and connects two offline protocol clients. It never reuses worlds or player
stores from another fixture. Only the launcher download cache may be copied.

The first server phase verifies actual Brigadier and NMS execution:

- two independently owned regions in a runtime dimension, with connected players;
- real socket RCON block/entity/storage reads, block/storage writes and replies;
- block get/path/scale/merge/remove/modify/from while gameplay is frozen;
- entity reads through a bounded selector and explicit UUID owner routing;
- storage list/compound/from/string/compute operations and actual execute callbacks;
- 300 command mutations from parallel region owners without overwritten fields;
- foreign/unloaded/unbounded-NBT selectors, earlier execute NBT filters, entity mutations and stale accessors rejected;
- connected player name/`@s` reads, a real Nether transfer and foreign player rejection;
- bounded player-NBT selection on its owner and foreign `@s` filters rejected before inspecting NBT;
- actual level-root snapshot, saved runtime unload and block-NBT reload.

The second server phase verifies that the actual saved CommandStorage is loaded
after a clean shutdown/restart, including removed fields and parallel mutations.
Each phase requires two logins, zero kicks and zero disconnects before its pass
marker. The intentional restart naturally ends the first phase's connections.

Assertions require the exact final values, successful command callbacks and all
check markers. Timeouts, server/thread failures, unexpected command exceptions
or missing RCON replies fail the run. `result.json` records the tested JAR's
SHA-256, ports, replies, checks and client observations without the RCON password.
These clients confirm protocol continuity, not visual Vanilla rendering or the
production MCC/MVE/TAB/LuckPerms stack.
