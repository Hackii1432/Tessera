# Windows console shutdown regression

This test uses the executable server, an isolated disposable world and the
`console-shutdown` mode of the existing test plugin. It does not install MCC,
MVE or a replacement terminal appender. The normal `stop` command emits 512
numbered records at plugin disable and completes the native world/player/I/O
shutdown. A status listener remains active after plugin disable so it also
captures Log4j appender failures that are missing from `latest.log`.

## Run

Build `buildTessera` first. Create a fresh fixture under `build/`, mark it with
`.tessera-disposable-fixture`, accept the Minecraft EULA only for that test,
and use offline mode, loopback and a free local port. Copy the test plugin from
`test-plugin/build/libs/tessera-runtime-world-smoke.jar` into its `plugins/`.
Never reuse production worlds or player stores.

Run the server in a real Windows console/ConPTY, without piping Java stdout.
PowerShell JVM options containing periods must be quoted. The explicit JLine
type is important if the automation environment inherits `TERM=dumb`:

```powershell
$env:TESSERA_SMOKE_MODE='console-shutdown'
& 'C:/Program Files/Java/jdk-25.0.3/bin/java.exe' '--enable-native-access=ALL-UNNAMED' '-Dterminal.jline=true' '-Dorg.jline.terminal.type=windows' '-Dorg.jline.terminal.provider=ffm' '-XX:ActiveProcessorCount=4' '-Xms512M' '-Xmx2G' '-jar' 'C:/path/to/tessera-server-26.3.build.017-beta.jar' '--nogui'
$consoleExitCode = $LASTEXITCODE
```

Wait for `CONSOLE_SHUTDOWN_READY`, confirming `windows=true` and `reader=true`,
then enter `stop`. Do not use a forced process termination. Run the verifier
from the repository afterwards, passing the actual observed process exit code:

```powershell
./smoke-tests/console-shutdown/verify.ps1 -Fixture build/console-fixture -Jar build/libs/tessera-server-26.3.build.017-beta.jar -ExitCode $consoleExitCode
```

The verifier requires the real Windows terminal/reader, all 512 unique numbered
records, completed world/player/I/O saving, exit 0, no region failure and no
terminal/appender error. A DumbTerminal-only run does **not** pass this check.
For a pre-fix executable use `-ExpectFailure` as a negative control: the test
then requires a reproduced `Terminal has been closed` appender failure, not
merely a successful server start. Archive both logs and check/status files
before restarting the fixture, since the plugin resets its diagnostic files.

These options are for the acceptance fixture, not a required production JVM
workaround. This test does not guarantee correct saving after force-kill,
power loss or an external/plugin component closing the terminal prematurely.
