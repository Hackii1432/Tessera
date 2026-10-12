# Native October Paper-/Moonrise regression

This fixture starts the actual executable Tessera JAR in a marked disposable
directory below `build/`, on a reserved loopback port. No production saves or
MCC/MVE product code are used. Two protocol clients remain connected throughout.

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-25.0.3'
.\gradlew.bat :test-plugin:jar --console=plain
node smoke-tests/paper-october-update/run.mjs "$env:JAVA_HOME/bin/java.exe" build/libs/tessera-server-26.3.build.019-beta.jar
```

The plugin creates two equal-seed NORMAL noise worlds and a Nether noise world,
generates separate chunk groups, and compares every block-state palette entry
and biome in selected equal-seed chunks before gameplay ticks are allowed. The
fixture uses native owner chunk tasks during this initial freeze; unrelated
random/block ticks must not change one reference world before comparison.
Both NORMAL references retain native noise, surfaces and caves but disable
decoration/mob/structure writes with a world-local test generator. An ordinary
Nether uses the unwrapped native generator. Independently requested decorated
worlds failed in-memory equality checks in both 018 and 019 diagnostic runs;
one saved 019 comparison also differed at a boundary ore block. Those
order-sensitive/transient observations are not a noise/palette parity oracle,
nor evidence of identical failures in both builds. No production
generator setting is changed. All logical block positions and biomes in the
controlled reference chunks must still match exactly.
It checks two distinct live native
player owners, typed player PDC, weapon-copy semantics, gravity/drag getters,
and a real cancelled SulfurCube explosion. Ignite/removal and explosion/removal
callbacks must stop mutation; an explosion callback transfers the live cube to
the Nether, waits for native equipment readiness and cancels a prime there,
then verifies that the old native
instance did not continue ticking or explode after giving up its owner.
The fixture reignites on the destination owner only if needed; preservation
of the old instance's transient fuse/cache is not inferred from this test.

Runtime recipes are registered globally, discovered on player owners and
removed during tick freeze. A rejected region writer and duplicate batch must
leave no partial mutation. Real resource/advancement/recipe resets run on owners
during freeze. A player transfers to the Nether during freeze and receives the
current catalog there. The clients count actual recipe reset and post-effect
packets, logins, kicks and disconnects before the server's assertions finish.

The runner fails on missing assertions/packets, unexpected disconnects, tick
failures, ownership violations, concurrent map mutation, timeout or nonzero
server exit. `result.json` includes JAR SHA-256 and per-client counters;
`update-checks.txt` and `runner.log` preserve the actual native results.

These clients acknowledge transfers and decode selected packets but do not
render a Vanilla UI. Equal-seed determinism is not a complete comparison with
all official Vanilla terrain; optimized-versus-retained-Vanilla noise parity
is tested independently by the Java regression suite. No quantitative speedup
or complete acceptance of arbitrary production plugins is inferred.
