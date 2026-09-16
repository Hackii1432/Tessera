package dev.tessera.smoke;

import com.google.gson.GsonBuilder;
import io.papermc.paper.threadedregions.TickRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Repeater;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Runs against the assembled server, with no replacement of the regionizer or tick queues. */
public final class RedstoneRegionMergeSmoke extends JavaPlugin implements Listener {
    private static final int Y = 80;
    private static final int[] PULSE_STARTS = {10, 42, 82, 138, 220};
    private static final int[] PULSE_WIDTHS = {4, 8, 12, 16, 20};
    private final List<Pair> pairs = new CopyOnWriteArrayList<>();
    private final AtomicBoolean finished = new AtomicBoolean();
    private World world;
    private ServerLevel level;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getGlobalRegionScheduler().runDelayed(this, task -> this.start(), 20);
    }

    private void start() {
        this.world = Bukkit.getWorlds().getFirst();
        this.level = ((CraftWorld) this.world).getHandle();
        this.pairs.add(new Pair("256-blocks", 8192, 64, 12, false));
        this.pairs.add(new Pair("512-blocks", 12288, 128, 47, false));
        this.pairs.add(new Pair("1024-blocks-slow-region", 16384, 256, 95, true));
        this.getLogger().info("REDSTONE_MERGE_START gridShift=" + this.level.regioniser.sectionChunkShift);
        CompletableFuture.allOf(this.pairs.stream().map(Pair::prepare).toArray(CompletableFuture[]::new))
            .thenCompose(ignored -> CompletableFuture.allOf(this.pairs.stream()
                .map(Pair::baseline).toArray(CompletableFuture[]::new)))
            .thenCompose(ignored -> CompletableFuture.allOf(this.pairs.stream()
                .map(Pair::mergeRun).toArray(CompletableFuture[]::new)))
            .orTimeout(8, TimeUnit.MINUTES)
            .whenComplete((ignored, failure) -> this.finish(failure));
    }

    private CompletableFuture<Void> load(final int cx, final int cz) {
        this.world.addPluginChunkTicket(cx, cz, this);
        return this.world.getChunkAtAsync(cx, cz, true).thenApply(chunk -> null);
    }

    private CompletableFuture<Void> onRegion(final Circuit circuit, final Runnable operation) {
        final CompletableFuture<Void> future = new CompletableFuture<>();
        Bukkit.getRegionScheduler().execute(this, this.world, circuit.x >> 4, circuit.z >> 4, () -> {
            try {
                operation.run();
                future.complete(null);
            } catch (final Throwable failure) {
                future.completeExceptionally(failure);
            }
        });
        return future;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRedstone(final BlockRedstoneEvent event) {
        if (event.getBlock().getWorld() != this.world || event.getBlock().getY() != Y
            || event.getOldCurrent() == event.getNewCurrent()) {
            return;
        }
        for (final Pair pair : this.pairs) {
            for (final Circuit circuit : List.of(pair.a, pair.b)) {
                final int offset = event.getBlock().getX() - circuit.x - 1;
                if (event.getBlock().getZ() == circuit.z && offset >= 0 && offset % 4 == 0
                    && offset / 4 < circuit.repeaters && circuit.active != null) {
                    final Run run = circuit.active;
                    run.edges.add(new Edge(offset / 4, run.tick, event.getOldCurrent(), event.getNewCurrent()));
                }
            }
        }
    }

    private static long regionId() {
        return TickRegionScheduler.getCurrentRegion().id;
    }

    private static long redstoneTime() {
        return TickRegionScheduler.getCurrentRegionizedWorldData().getRedstoneGameTime();
    }

    private static void require(final boolean condition, final String detail) {
        if (!condition) {
            throw new AssertionError(detail);
        }
    }

    private final class Pair {
        final String name;
        final Circuit a;
        final Circuit b;
        final int mergeAt;
        final AtomicBoolean bridgeStarted = new AtomicBoolean();
        final CompletableFuture<Void> bridgeDone = new CompletableFuture<>();
        volatile long beforeA;
        volatile long beforeB;
        volatile boolean verified;

        Pair(final String name, final int x, final int repeaters, final int mergeAt, final boolean slow) {
            this.name = name;
            this.mergeAt = mergeAt;
            this.a = new Circuit(this, "A", x, 8192, repeaters, false);
            this.b = new Circuit(this, "B", x, 10240, repeaters, slow);
        }

        CompletableFuture<Void> prepare() {
            // Prepare A first: even the normal case has different region clock origins.
            return this.a.prepare().thenCompose(ignored -> this.b.prepare());
        }

        CompletableFuture<Void> baseline() {
            return CompletableFuture.allOf(this.a.run(false), this.b.run(false)).thenRun(() -> {
                this.beforeA = this.a.baseline.lastRegion;
                this.beforeB = this.b.baseline.lastRegion;
                require(this.beforeA != this.beforeB, this.name + ": fixture regions are already merged");
                getLogger().info("REDSTONE_BASELINE_PASS " + this.name + " regions=" + this.beforeA + "," + this.beforeB);
            });
        }

        void bridge() {
            if (!this.bridgeStarted.compareAndSet(false, true)) {
                return;
            }
            getLogger().info("REDSTONE_BRIDGE_REQUEST " + this.name + " atLogicalTick=" + this.a.active.tick);
            CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
            for (int cz = (this.a.z >> 4) + 8; cz < (this.b.z >> 4); cz += 8) {
                final int bridgeZ = cz;
                chain = chain.thenCompose(ignored -> load(this.a.x >> 4, bridgeZ));
            }
            chain.whenComplete((ignored, failure) -> {
                if (failure == null) {
                    this.bridgeDone.complete(null);
                } else {
                    this.bridgeDone.completeExceptionally(failure);
                }
            });
        }

        CompletableFuture<Void> mergeRun() {
            return CompletableFuture.allOf(this.a.run(true), this.b.run(true), this.bridgeDone).thenRun(() -> {
                final Run ar = this.a.merged;
                final Run br = this.b.merged;
                require(ar.lastRegion == br.lastRegion, this.name + ": no proven merge: " + ar.lastRegion + "," + br.lastRegion);
                require(ar.transitions.stream().anyMatch(t -> t.pendingBefore > 0)
                        || br.transitions.stream().anyMatch(t -> t.pendingBefore > 0),
                    this.name + ": no ownership change with pending updates observed");
                require(ar.transitions.stream().anyMatch(t -> t.clockOffset != 0)
                        || br.transitions.stream().anyMatch(t -> t.clockOffset != 0),
                    this.name + ": no nonzero region clock offset exercised");
                this.a.compare();
                this.b.compare();
                this.verified = true;
                getLogger().info("REDSTONE_MERGE_PASS " + this.name + " targetRegion=" + ar.lastRegion);
            });
        }

        Map<String, Object> report() {
            return Map.of("name", this.name, "verified", this.verified, "mergeRequestedAtTick", this.mergeAt,
                "initialRegions", List.of(this.beforeA, this.beforeB), "circuits", List.of(this.a.report(), this.b.report()));
        }
    }

    private final class Circuit {
        final Pair pair;
        final String name;
        final int x;
        final int z;
        final int repeaters;
        final boolean slow;
        volatile Run active;
        volatile Run baseline;
        volatile Run merged;

        Circuit(final Pair pair, final String name, final int x, final int z, final int repeaters, final boolean slow) {
            this.pair = pair;
            this.name = name;
            this.x = x;
            this.z = z;
            this.repeaters = repeaters;
            this.slow = slow;
        }

        CompletableFuture<Void> prepare() {
            CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
            for (int cx = (this.x - 1) >> 4; cx <= (this.x + 4 * this.repeaters + 1) >> 4; cx++) {
                final int chunkX = cx;
                chain = chain.thenCompose(ignored -> load(chunkX, this.z >> 4));
            }
            return chain.thenCompose(ignored -> onRegion(this, () -> {
                for (int dx = -1; dx <= this.repeaters * 4 + 1; dx++) {
                    require(Bukkit.isOwnedByCurrentRegion(world, (this.x + dx) >> 4, this.z >> 4), "Fixture crosses ownership");
                    world.getBlockAt(this.x + dx, Y - 1, this.z).setType(Material.STONE, false);
                    world.getBlockAt(this.x + dx, Y, this.z).setType(Material.AIR, false);
                }
                final Repeater repeater = (Repeater) Material.REPEATER.createBlockData();
                repeater.setFacing(BlockFace.WEST);
                repeater.setDelay(1);
                for (int dx = 1; dx <= this.repeaters * 4; dx++) {
                    if (dx % 4 == 1) {
                        world.getBlockAt(this.x + dx, Y, this.z).setBlockData(repeater, true);
                    } else {
                        world.getBlockAt(this.x + dx, Y, this.z).setType(Material.REDSTONE_WIRE, true);
                    }
                }
                getLogger().info("REDSTONE_FIXTURE_READY " + this.pair.name + "/" + this.name + " region=" + regionId());
            }));
        }

        CompletableFuture<Void> run(final boolean merge) {
            final Run run = new Run(merge, this.repeaters * 2 + 280);
            if (merge) {
                this.merged = run;
            } else {
                this.baseline = run;
            }
            Bukkit.getRegionScheduler().runAtFixedRate(RedstoneRegionMergeSmoke.this, world, this.x >> 4, this.z >> 4,
                task -> {
                    try {
                        if (finished.get()) {
                            task.cancel();
                            return;
                        }
                        if (run.tick == 0) {
                            this.active = run;
                            run.startedNanos = System.nanoTime();
                            run.lastRegion = regionId();
                        }
                        this.step(run, task);
                    } catch (final Throwable failure) {
                        this.active = null;
                        task.cancel();
                        run.done.completeExceptionally(failure);
                    }
                }, 20, 1);
            return run.done;
        }

        void step(final Run run, final ScheduledTask task) {
            run.tick++;
            final long currentRegion = regionId();
            final long clock = redstoneTime();
            for (int cx = this.x >> 4; cx <= (this.x + 4 * this.repeaters) >> 4; cx++) {
                require(Bukkit.isOwnedByCurrentRegion(world, cx, this.z >> 4), this.pair.name + ": ownership lost");
                require(level.isPositionTickingWithEntitiesLoaded(ChunkPos.pack(cx, this.z >> 4)),
                    this.pair.name + ": circuit chunk stopped ticking: " + cx);
            }
            if (currentRegion != run.lastRegion) {
                final Transition transition = new Transition(run.tick, run.lastRegion, currentRegion,
                    run.lastClock, clock, clock - run.lastClock - 1, run.lastPending);
                run.transitions.add(transition);
                getLogger().info("REDSTONE_REGION_CHANGED " + this.pair.name + "/" + this.name + " " + transition);
            } else if (run.tick > 1) {
                require(clock == run.lastClock + 1, this.pair.name + ": logical tick counter diverged from redstone clock");
            }
            run.lastRegion = currentRegion;
            run.lastClock = clock;
            for (int i = 0; i < PULSE_STARTS.length; i++) {
                if (run.tick == PULSE_STARTS[i]) {
                    world.getBlockAt(this.x, Y, this.z).setType(Material.REDSTONE_BLOCK, true);
                } else if (run.tick == PULSE_STARTS[i] + PULSE_WIDTHS[i]) {
                    world.getBlockAt(this.x, Y, this.z).setType(Material.AIR, true);
                }
            }
            run.lastPending = level.getBlockTicks().count();
            if (run.merge && this.name.equals("A") && run.tick == this.pair.mergeAt) {
                this.pair.bridge();
            }
            if (run.tick >= run.endTick) {
                for (int i = 0; i < this.repeaters; i++) {
                    require(!((Repeater) world.getBlockAt(this.x + 1 + i * 4, Y, this.z).getBlockData()).isPowered(),
                        this.pair.name + ": stuck powered repeater " + i);
                    final int stage = i;
                    require(run.edges.stream().filter(e -> e.stage == stage).count() == PULSE_STARTS.length * 2,
                        this.pair.name + "/" + this.name + ": missing/duplicate edges at stage " + i);
                }
                require(run.lastPending == 0, this.pair.name + ": pending ticks after settling: " + run.lastPending);
                run.elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - run.startedNanos);
                this.active = null;
                task.cancel();
                run.done.complete(null);
                return;
            }
            if (this.slow) {
                // Intentional bounded delay gives this region fewer ticks per wall-clock second.
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(65));
            }
        }

        void compare() {
            require(this.baseline.transitions.isEmpty(), this.pair.name + ": baseline changed regions");
            require(this.baseline.edges.equals(this.merged.edges), this.pair.name + "/" + this.name
                + ": event trace differs; first difference=" + firstDifference(this.baseline.edges, this.merged.edges));
            for (int stage = 0; stage < this.repeaters; stage++) {
                final int expectedStage = stage;
                final List<Edge> edges = this.baseline.edges.stream().filter(e -> e.stage == expectedStage).toList();
                for (int pulse = 0; pulse < PULSE_STARTS.length; pulse++) {
                    final Edge rise = edges.get(pulse * 2);
                    final Edge fall = edges.get(pulse * 2 + 1);
                    require(rise.oldCurrent == 0 && rise.newCurrent == 15 && fall.oldCurrent == 15 && fall.newCurrent == 0,
                        "Unexpected edge polarity");
                    require(fall.tick - rise.tick == PULSE_WIDTHS[pulse], "Wrong pulse width at stage " + stage);
                    // The input task runs before ServerLevel.tickTime increments redstone time.
                    // Thus a two-game-tick repeater fires at task tick input+1; each next stage adds two.
                    require(rise.tick - PULSE_STARTS[pulse] == 2 * (stage + 1) - 1,
                        "Unexpected repeater latency at stage " + stage + ": " + rise);
                }
            }
        }

        Map<String, Object> report() {
            final Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("name", this.name);
            result.put("x", this.x);
            result.put("z", this.z);
            result.put("wireLengthBlocks", this.repeaters * 4);
            result.put("repeaters", this.repeaters);
            result.put("delaySetting", 1);
            result.put("slowRegionMillisPerTick", this.slow ? 65 : 0);
            result.put("baseline", this.baseline == null ? null : this.baseline.report(this.repeaters));
            result.put("merged", this.merged == null ? null : this.merged.report(this.repeaters));
            return result;
        }
    }

    private static String firstDifference(final List<Edge> a, final List<Edge> b) {
        for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
            if (!a.get(i).equals(b.get(i))) {
                return "index=" + i + " baseline=" + a.get(i) + " merged=" + b.get(i);
            }
        }
        return "size=" + a.size() + "/" + b.size();
    }

    private static final class Run {
        final boolean merge;
        final int endTick;
        final CompletableFuture<Void> done = new CompletableFuture<>();
        final List<Edge> edges = new CopyOnWriteArrayList<>();
        final List<Transition> transitions = new CopyOnWriteArrayList<>();
        int tick;
        long lastRegion;
        long lastClock;
        int lastPending;
        long startedNanos;
        long elapsedMillis;

        Run(final boolean merge, final int endTick) {
            this.merge = merge;
            this.endTick = endTick;
        }

        Map<String, Object> report(final int repeaters) {
            return Map.of("logicalTicks", this.tick, "elapsedMillis", this.elapsedMillis,
                "finalRegion", this.lastRegion, "transitions", this.transitions,
                "edgeCount", this.edges.size(), "outputEdges", this.edges.stream().filter(e -> e.stage == repeaters - 1).toList(),
                "allRepeaterEdges", this.edges);
        }
    }

    private record Edge(int stage, int tick, int oldCurrent, int newCurrent) {}
    private record Transition(int tick, long from, long into, long previousClock, long currentClock,
                              long clockOffset, int pendingBefore) {}

    private void finish(final Throwable failure) {
        if (!this.finished.compareAndSet(false, true)) {
            return;
        }
        final Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("status", failure == null ? "PASS" : "FAIL");
        result.put("timestamp", Instant.now().toString());
        result.put("serverVersion", Bukkit.getVersion());
        result.put("gridShift", this.level.regioniser.sectionChunkShift);
        result.put("pulseStarts", PULSE_STARTS);
        result.put("pulseWidths", PULSE_WIDTHS);
        result.put("failure", failure == null ? null : failure.toString());
        result.put("cases", this.pairs.stream().map(Pair::report).toList());
        try {
            Files.writeString(Path.of("redstone-merge-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result));
        } catch (final Exception writeFailure) {
            getLogger().log(java.util.logging.Level.SEVERE, "Could not write result", writeFailure);
        }
        getLogger().info("REDSTONE_MERGE_RESULT " + result.get("status"));
        if (failure != null) {
            getLogger().log(java.util.logging.Level.SEVERE, "Redstone merge smoke failed", failure);
        }
        Bukkit.getGlobalRegionScheduler().execute(this, Bukkit::shutdown);
    }
}
