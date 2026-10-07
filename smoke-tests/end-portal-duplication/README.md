# End portal falling-block continuation

This fixture starts two disposable Tessera servers, one with
`unsupported-settings.allow-unsafe-end-portal-teleportation: false` and one
with `true`. Only marked directories below `build/` are used. It binds a free
loopback port and never opens a production world.

From the repository root, with an already patched/built workspace:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-25.0.3'
.\gradlew.bat :test-plugin:jar
node smoke-tests/end-portal-duplication/run.mjs "$env:JAVA_HOME/bin/java.exe" build/libs/tessera-server-26.3.build.018-beta.jar
```

The test plugin spawns native falling blocks beside real End portal blocks.
No mocked portal or manually forced `onGround` flag is used. It counts portal
collision, transfer and block-placement events, checks actual world blocks,
entity ownership, wrapper/UUID identity and absence of leaked entities/items.
Sand, red sand, gravel, concrete powder and anvils are exercised in two distant
source regions and two rounds, with initially unloaded End destinations.
Additional cases cover ordinary airborne transfer, an entry veto, a portal veto,
a placement veto, same-world redirection, removal by a portal listener and removal
of the source continuation by a placement listener. Five cases exercise the
return direction from the End. Two further cases check an actual emitted item
and `EntityDropItemEvent` cancellation. Only that emitted item's portal cooldown
is increased so its subsequent ordinary item-portal transfer cannot interfere
with the drop count; after checking its material and amount the fixture removes
it. Another twelve cases exercise dragon eggs in both directions, in two distant
source regions: landing, ordinary airborne transfer and item drops. Even with
the option enabled, each case must leave exactly one egg (block or item), no
source continuation, and normal airborne transfer must retain the original
wrapper/UUID. There are 46 cases per setting, 92 in total; the original 68
non-egg assertions remain unchanged.

The ordinary airborne control prepares its destination beforehand: the disabled
Folia path keeps falling while portal preparation runs. A block reaching the
floor before preparation finishes is not an ordinary completed portal transfer.
The duplication cases deliberately retain initially unloaded destinations.

Each fixture records the exact executable JAR SHA-256, settings, port, assertions
and server output in `result.json`, `end-portal-duplication-checks.txt` and
`runner.log`. The runner fails on a missing success marker, assertion failure,
timeout, nonzero exit or region/thread exception.

This is a native server integration test, not a client-rendering test or proof
that every player-built piston/slime/honey contraption has identical timing to
a single-threaded Vanilla server. It does not load external gameplay plugins.

## Default End arrival and button collectors

The separate flight mode runs one disposable server with the option enabled:

```powershell
$env:TESSERA_SMOKE_MODE = 'end-portal-flight'
node smoke-tests/end-portal-duplication/run.mjs "$env:JAVA_HOME/bin/java.exe" build/libs/tessera-server-26.3.build.018-beta.jar
Remove-Item Env:TESSERA_SMOKE_MODE
```

Unlike the original 68-case suite, this mode does not redirect the portal to
prepared custom coordinates. It exercises the real default End arrival at
`(100.5, 50, 0.5)` with the obsidian floor at Y=48. It spawns each of five
materials with four cardinal source velocities on real portal/landing pads in
two distant source regions. Initial source velocity is supplied by the fixture;
no velocity, position or `onGround` flag is changed during/after teleportation.

For all 20 cases it checks the exact arrival height, unchanged platform height,
original Bukkit wrapper, Vanilla rotation of the existing velocity (source yaw
0 to exit yaw 90), continued flight beyond the platform, a native item drop at
a supported wall button and actual insertion into a hopper. Target block
placement is a test failure. Source block, portal, arrival, drop and pickup must
each occur exactly once; no tagged falling block or item may remain afterwards.
The buttons and hoppers use ordinary physics: no forced item conversion or
synthetic pickup event. Direction labels in case IDs refer to source velocity.

Results are `end-portal-flight-checks.txt`, `result.json` and `runner.log` in
the marked fixture directory. The same runner validates all 20 individual case
IDs and fails on exceptions, timeouts or missing evidence. This is not a test
of a complete piston/slime/honey machine or a connected client's rendering.
