package dev.tessera.smoke;

import io.papermc.paper.spark.RegionTickStatistics;
import io.papermc.paper.threadedregions.TickRegionScheduler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import me.lucko.spark.api.SparkProvider;
import me.lucko.spark.paper.proto.SparkSamplerProtos;
import net.minecraft.server.MinecraftServer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Isolated final-JAR test. No uploads, real owners, clients and protobuf samples. */
final class NativeSparkSmoke implements org.bukkit.event.Listener {
    private final JavaPlugin plugin;
    private final List<String> checks = new ArrayList<>();
    private final java.util.concurrent.atomic.AtomicInteger networkCommands = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicBoolean wrongCommandOwner = new java.util.concurrent.atomic.AtomicBoolean();

    NativeSparkSmoke(final JavaPlugin plugin) { this.plugin = plugin; }

    void start() {
        Bukkit.getPluginManager().registerEvents(this, this.plugin);
        final boolean external = Boolean.parseBoolean(System.getenv("TESSERA_SPARK_EXTERNAL_PROBE"));
        if (external || Boolean.parseBoolean(System.getenv("TESSERA_SPARK_EXPECT_DISABLED"))) {
            Bukkit.getGlobalRegionScheduler().runDelayed(this.plugin, task -> {
                try {
                    boolean unavailable = false;
                    try { SparkProvider.get(); } catch (IllegalStateException expected) { unavailable = true; }
                    require(unavailable, "native spark API remains unavailable when disabled or an external plugin is preferred");
                    if (external) require(Bukkit.getPluginManager().isPluginEnabled("spark"), "external spark-name probe is preferred without enabling a second profiler");
                    require(Bukkit.getCommandMap().getCommand("spark") != null, "disabled command remains available with diagnostic");
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "spark profiler info");
                    Files.write(Path.of("spark-checks.txt"), this.checks);
                    this.plugin.getLogger().info("SPARK_DISABLED_PASS");
                } catch (Throwable failure) {
                    this.plugin.getLogger().log(java.util.logging.Level.SEVERE, "SPARK_FAIL", failure);
                } finally { Bukkit.shutdown(); }
            }, 10);
            return;
        }
        this.plugin.getLogger().info("SPARK_READY_CLIENTS");
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(this.plugin, task -> {
            if (Bukkit.getOnlinePlayers().size() != 2) return;
            task.cancel();
            Bukkit.getAsyncScheduler().runNow(this.plugin, ignored -> this.run());
        }, 1, 10);
    }

    private void run() {
        try {
            final Player one = Bukkit.getPlayerExact("SparkAlpha");
            final Player two = Bukkit.getPlayerExact("SparkBeta");
            require(one != null && two != null, "two protocol clients connected");
            require(SparkProvider.get() != null, "bundled spark API registered without a spark plugin");
            require(Bukkit.getPluginManager().getPlugin("spark") == null, "no external spark plugin");
            require(Bukkit.getCommandMap().getCommand("spark") instanceof io.papermc.paper.SparksFly.CommandImpl,
                "native fallback command registered after plugins");
            owner(one, () -> { one.addAttachment(this.plugin, "spark", true); return null; }).get(10, TimeUnit.SECONDS);
            owner(two, () -> two.teleportAsync(new Location(two.getWorld(), 2056.5, 100, 8.5)))
                .get(10, TimeUnit.SECONDS).get(45, TimeUnit.SECONDS);
            final long regionOne = owner(one, () -> TickRegionScheduler.getCurrentRegion().id).get(10, TimeUnit.SECONDS);
            final long regionTwo = owner(two, () -> TickRegionScheduler.getCurrentRegion().id).get(10, TimeUnit.SECONDS);
            require(regionOne != regionTwo, "clients on distinct native regions");

            command("spark profiler start --force-java-sampler --thread * --not-combined --interval 5");
            Thread.sleep(1800);
            // This must not stop the running recording. The second player has no spark permission.
            owner(two, () -> two.performCommand("spark profiler cancel")).get(10, TimeUnit.SECONDS);
            owner(one, () -> one.performCommand("spark profiler info")).get(10, TimeUnit.SECONDS);
            final var sentinel = owner(one, () -> one.getWorld().spawn(one.getLocation(), org.bukkit.entity.ArmorStand.class,
                entity -> { entity.setGravity(false); entity.setPersistent(false); })).get(10, TimeUnit.SECONDS);
            final int initial = owner(one, () -> ((org.bukkit.craftbukkit.entity.CraftEntity)sentinel).getHandle().tickCount).get(10, TimeUnit.SECONDS);
            Thread.sleep(300);
            require(initial < owner(one, () -> ((org.bukkit.craftbukkit.entity.CraftEntity)sentinel).getHandle().tickCount).get(10, TimeUnit.SECONDS),
                "non-player sentinel ticked before freeze");
            global(() -> { MinecraftServer.getServer().tickRateManager().setFrozen(true); return null; }).get(10, TimeUnit.SECONDS);
            // Vanilla publishes runsNormally on the next global tick. Allow an
            // already-started region tick to finish before measuring quiescence.
            Thread.sleep(200);
            this.plugin.getLogger().info("SPARK_FROZEN_PHASE");
            final int before = owner(one, () -> ((org.bukkit.craftbukkit.entity.CraftEntity)sentinel).getHandle().tickCount).get(10, TimeUnit.SECONDS);
            owner(one, () -> one.performCommand("spark profiler info")).get(10, TimeUnit.SECONDS);
            // Spark 1.10.191 uses 60-second profiling windows (not its 10s TPS
            // graph interval). Cross a complete window and real play keepalives.
            final long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(
                me.lucko.spark.paper.common.sampler.window.ProfilingWindowUtils.WINDOW_SIZE_MILLIS + 2000L);
            while (System.nanoTime() < until) {
                final long remaining = Math.max(1L, TimeUnit.NANOSECONDS.toMillis(until - System.nanoTime()));
                Thread.sleep(Math.min(16000L, remaining)); // Fixture async worker only.
                require(before == owner(one, () -> ((org.bukkit.craftbukkit.entity.CraftEntity)sentinel).getHandle().tickCount).get(10, TimeUnit.SECONDS),
                    "non-player sentinel remained frozen at sample-window checkpoint");
                owner(one, () -> one.performCommand("spark profiler info")).get(10, TimeUnit.SECONDS);
            }
            require(before == owner(one, () -> ((org.bukkit.craftbukkit.entity.CraftEntity)sentinel).getHandle().tickCount).get(10, TimeUnit.SECONDS), "gameplay stayed frozen while spark replied");
            require(this.networkCommands.get() > 0 && !this.wrongCommandOwner.get(), "real client spark command reached the current player owner during freeze");
            owner(one, () -> { sentinel.remove(); return null; }).get(10, TimeUnit.SECONDS);
            final World destination = Bukkit.getWorlds().stream().filter(world -> world.getEnvironment() == World.Environment.NETHER).findFirst().orElseThrow();
            owner(one, () -> one.teleportAsync(new Location(destination, 8.5, 120, 8.5)))
                .get(10, TimeUnit.SECONDS).get(45, TimeUnit.SECONDS);
            owner(one, () -> one.performCommand("spark tps")).get(10, TimeUnit.SECONDS);
            owner(one, () -> one.performCommand("spark ping")).get(10, TimeUnit.SECONDS);
            require(owner(one, one::getWorld).get(10, TimeUnit.SECONDS) == destination, "dimension transfer completed during recording and freeze");
            Thread.sleep(1400);
            final var metadata = new RegionTickStatistics().metadata();
            require(metadata.getAsJsonArray("regions").size() >= 2, "metadata contains independent live regions");
            require(metadata.get("frozen").getAsBoolean(), "metadata reports tick freeze");
            require(metadata.get("tick-windows").getAsString().contains("10s"), "exact ten-second window published");
            final var reports = java.util.stream.StreamSupport.stream(metadata.getAsJsonArray("regions").spliterator(), false)
                .filter(value -> value.getAsJsonObject().getAsJsonObject("windows").has("10s"))
                .map(value -> value.getAsJsonObject().getAsJsonObject("windows").getAsJsonObject("10s"))
                .toList();
            require(reports.size() >= 2 && reports.stream().allMatch(value -> value.get("ticks").getAsInt() > 0 && Double.isFinite(value.get("mspt-mean").getAsDouble())),
                "published ten-second reports contain real finite native tick measurements");
            Files.writeString(Path.of("spark-regions.json"), metadata.toString());
            global(() -> { MinecraftServer.getServer().tickRateManager().setFrozen(false); return null; }).get(10, TimeUnit.SECONDS);
            this.plugin.getLogger().info("SPARK_UNFROZEN_PHASE");

            command("spark profiler stop --save-to-file");
            final Path all = waitProfile(1);
            final var profile = SparkSamplerProtos.SamplerData.parseFrom(Files.readAllBytes(all));
            final List<String> allThreads = profile.getThreadsList().stream().map(SparkSamplerProtos.ThreadNode::getName).toList();
            require(allThreads.stream().anyMatch(name -> name.contains("Netty")), "all-thread profile contains Netty samples");
            require(allThreads.stream().filter(name -> name.startsWith("Folia Region Scheduler Thread #")).count() >= 2,
                "all-thread profile contains multiple tick workers");
            require(profile.getMetadata().toString().contains("tessera-regions"), "saved protobuf contains region metadata");
            require(profile.getTimeWindowStatisticsCount() >= 2, "sampler collected multiple windows throughout tick freeze");
            require(profile.getThreadsList().stream().anyMatch(thread -> thread.getChildrenCount() > 0 || thread.getChildrenRefsCount() > 0), "saved protobuf contains actual stack samples");
            Files.writeString(Path.of("spark-all-threads.txt"), String.join("\n", allThreads));

            owner(one, () -> one.performCommand("spark profiler start --force-java-sampler --not-combined --interval 5")).get(10, TimeUnit.SECONDS);
            Thread.sleep(2800);
            owner(one, () -> one.performCommand("spark profiler stop --save-to-file")).get(10, TimeUnit.SECONDS);
            final var defaults = SparkSamplerProtos.SamplerData.parseFrom(Files.readAllBytes(waitProfile(2)));
            require(defaults.getMetadata().toString().contains("SparkAlpha"), "player identity retained in recording metadata");
            final List<String> defaultThreads = defaults.getThreadsList().stream().map(SparkSamplerProtos.ThreadNode::getName).toList();
            require(defaultThreads.stream().anyMatch(name -> name.startsWith("Folia Region Scheduler Thread #"))
                && defaultThreads.stream().allMatch(name -> name.matches("Folia Region Scheduler Thread #\\d+|Server thread")),
                "default recording selects dynamic tick workers only");
            Files.writeString(Path.of("spark-default-threads.txt"), String.join("\n", defaultThreads));
            require(Bukkit.getOnlinePlayers().size() == 2, "clients remained connected throughout profiling");
            // Shutdown while a third sampler is active must cancel it and the metrics task.
            command("spark profiler start --force-java-sampler");
            Thread.sleep(500);
            Files.write(Path.of("spark-checks.txt"), this.checks);
            this.plugin.getLogger().info("SPARK_PASS");
        } catch (final Throwable failure) {
            this.plugin.getLogger().log(java.util.logging.Level.SEVERE, "SPARK_FAIL", failure);
            try { Files.write(Path.of("spark-checks.txt"), this.checks); } catch (Exception ignored) { }
        } finally {
            Bukkit.shutdown();
        }
    }

    private void command(final String command) throws Exception {
        require(global(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)).get(10, TimeUnit.SECONDS),
            "console command dispatched: " + command);
    }

    @org.bukkit.event.EventHandler
    public void networkCommand(final org.bukkit.event.player.PlayerCommandPreprocessEvent event) {
        if (!event.getPlayer().getName().equals("SparkAlpha") || !event.getMessage().equals("/spark profiler info")) return;
        this.networkCommands.incrementAndGet();
        if (!ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(((CraftPlayer)event.getPlayer()).getHandleRaw())) {
            this.wrongCommandOwner.set(true);
        }
    }

    private Path waitProfile(final int count) throws Exception {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            try (final var files = Files.list(Path.of("plugins/spark"))) {
                final var profiles = files.filter(file -> file.toString().endsWith(".sparkprofile")).sorted().toList();
                if (profiles.size() >= count) {
                    final Path file = profiles.get(count - 1);
                    // Completion is the parsed, non-empty protobuf, not a command success log.
                    try { if (SparkSamplerProtos.SamplerData.parseFrom(Files.readAllBytes(file)).getThreadsCount() > 0) return file; }
                    catch (java.io.IOException ignored) { }
                }
            }
            Thread.sleep(100);
        }
        throw new AssertionError("No complete spark profile " + count);
    }

    private <T> CompletableFuture<T> owner(final Player player, final Callable<T> action) {
        return ((CraftPlayer)player).taskScheduler.scheduleRestore(entity -> {
            try { return CompletableFuture.completedFuture(action.call()); }
            catch (Throwable failure) { return CompletableFuture.failedFuture(failure); }
        }).toCompletableFuture();
    }

    private <T> CompletableFuture<T> global(final Callable<T> action) {
        final var future = new CompletableFuture<T>();
        Bukkit.getGlobalRegionScheduler().run(this.plugin, task -> {
            try { future.complete(action.call()); } catch (Throwable failure) { future.completeExceptionally(failure); }
        });
        return future;
    }

    private void require(final boolean condition, final String check) {
        if (!condition) throw new AssertionError(check);
        this.checks.add(check);
        this.plugin.getLogger().info("SPARK_CHECK " + check);
    }
}
