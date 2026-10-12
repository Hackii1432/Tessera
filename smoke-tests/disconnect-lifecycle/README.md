# Native disconnect lifecycle regression

This loopback-only fixture exercises the actual executable Tessera server with
three connected gameplay clients and a fourth client held in configuration.
It only creates marked disposable worlds below `build/`.

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-25.0.3'
.\gradlew.bat :test-plugin:jar
node smoke-tests/disconnect-lifecycle/run.mjs "$env:JAVA_HOME/bin/java.exe" build/libs/tessera-server-26.3.build.018-beta.jar
```

The test plugin checks two different native region owners, then freezes gameplay.
It cancels a real `PlayerKickEvent`, accepts a later plugin kick, and delays that
disconnect's actual Netty write future for 2.2 seconds. An expired keepalive and
additional disconnect requests are injected only in this isolated case to prove
that an accepted disconnect cannot trigger retries or replace its original reason.

The other region client subsequently stops answering real keepalives. A separate
configuration client withholds its known-packs response and does the same on the
global tick owner, before the terminal finish-configuration packet. Both native timeout
packets are delayed too. The runner requires exactly one timeout log and one
disconnect packet per timeout client. A fourth case closes the normal client's
TCP connection without a kick and requires `Disconnected`, not `Timed out`.

The shortened keepalive limit (`-Dpaper.playerconnection.keepalive=3`) applies
only to the disposable JVM; production defaults and configuration are unchanged.
The runner fails on duplicate kicks/disconnects, missing assertions, ownership
errors, an unexpected timeout log, a stalled cleanup, or a nonzero exit.
`result.json` records the executable JAR SHA-256, loopback port, per-client
login/kick/disconnect counters and actual server assertions. These are protocol
clients, not rendering Vanilla clients. External gameplay plugins and real
network saturation are not simulated.

Acceptance on 2026-10-11 passed twice on the final 018-beta JAR, with 37 native
assertions per run. See the [build addendum](../../docs/BUILD-26.3-018.md#nachtrag-11102026--disconnect-lücke)
and [compact evidence](../../docs/test-evidence/26.3-018/disconnect-lifecycle.json)
for source/patch/JAR hashes, actual counters, preliminary fixture failures and
test limits. Only marked test worlds were removed afterwards; original logs,
results and checks remain archived under `build/reports/native-fixtures/`.
