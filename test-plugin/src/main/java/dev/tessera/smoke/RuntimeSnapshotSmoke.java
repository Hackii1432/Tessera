package dev.tessera.smoke;

import io.papermc.paper.world.WorldSnapshotResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Real sockets join via smoke-tests/runtime-snapshot/client.mjs; no fake ServerPlayers. */
final class RuntimeSnapshotSmoke implements Listener {
    private final JavaPlugin plugin;
    private final Path output = Path.of("snapshot-smoke").toAbsolutePath();
    private volatile boolean rejectObserved;
    private volatile boolean regionRejectObserved;
    private volatile boolean snapshotEventExpected;
    private volatile Path breakCopy;

    RuntimeSnapshotSmoke(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    void start() {
        Bukkit.getPluginManager().registerEvents(this, this.plugin);
        global(() -> Bukkit.getWorlds().getFirst())
            .thenCompose(world -> snapshot("zero", List.of(world), List.of()))
            .thenRun(() -> this.plugin.getLogger().info("SNAPSHOT_READY_ONE"))
            .thenCompose(ignored -> awaitPlayers(1))
            .thenCompose(players -> {
                final Player player = players.getFirst();
                return owner(player, () -> {
                    player.setGameMode(GameMode.CREATIVE);
                    player.setTotalExperience(73);
                    player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 7));
                }).thenCompose(ignored -> ownerLocation(player))
                    .thenCompose(location -> snapshot("one", List.of(location.getWorld()), players))
                    .thenCompose(ignored -> owner(player, () -> player.setTotalExperience(147)))
                    .thenCompose(ignored -> ownerLocation(player))
                    .thenCompose(location -> snapshot("repeat", List.of(location.getWorld()), players));
            })
            .thenRun(() -> this.plugin.getLogger().info("SNAPSHOT_READY_MANY"))
            .thenCompose(ignored -> awaitPlayers(3))
            .thenCompose(players -> global(Bukkit::getWorlds).thenCompose(worlds -> {
                final World overworld = worlds.getFirst();
                final World nether = worlds.stream().filter(w -> w.getEnvironment() == World.Environment.NETHER).findFirst().orElseThrow();
                return teleport(players.get(1), new Location(overworld, 4096.5, 90, 4096.5))
                    .thenCompose(ignored -> teleport(players.get(2), new Location(nether, 32.5, 90, 32.5)))
                    .thenCompose(ignored -> snapshot("many", List.of(overworld, nether), players))
                    .thenCompose(ignored -> {
                        this.breakCopy = this.output.resolve("copy-failure");
                        this.snapshotEventExpected = true;
                        return global(() -> Bukkit.getRuntimeWorldManager().snapshotWorldsAsync(List.of(overworld), this.breakCopy))
                            .thenCompose(stage -> stage).thenAccept(result -> {
                                this.snapshotEventExpected = false;
                                require(result.status() == WorldSnapshotResult.Status.COPY_FAILED, result.toString());
                                require(result.message().contains("SNAPSHOT_COPY_FAILED"), result.message());
                                this.plugin.getLogger().info("SNAPSHOT_EXPECTED_COPY_FAILURE " + result.message());
                            });
                    })
                    .thenCompose(ignored -> snapshot("after-failure", List.of(overworld, nether), players))
                    .thenCompose(ignored -> owner(players.get(2), () -> players.get(2).kick()))
                    .thenCompose(ignored -> snapshot("after-disconnect", List.of(overworld), players.subList(0, 2)))
                    .thenCompose(ignored -> owner(players.getFirst(), () -> {
                        require(this.rejectObserved, "External scheduler was not rejected during snapshot");
                        require(this.regionRejectObserved, "External region scheduler was not rejected during snapshot");
                        this.plugin.getLogger().info("SNAPSHOT_OWNER_TICK_RESUMED");
                    }));
            }))
            .toCompletableFuture().orTimeout(180, TimeUnit.SECONDS)
            .whenComplete((ignored, failure) -> {
                final String result = failure == null ? "PASS" : "FAIL " + failure;
                this.plugin.getLogger().info("SNAPSHOT_SMOKE_" + result);
                if (failure != null) failure.printStackTrace();
                CompletableFuture.runAsync(() -> {
                    try {
                        Files.createDirectories(this.output);
                        Files.writeString(this.output.resolve("result.txt"), result);
                    } catch (Exception error) { throw new RuntimeException(error); }
                }).whenComplete((done, error) -> Bukkit.getGlobalRegionScheduler().execute(this.plugin, Bukkit::shutdown));
            });
    }

    @EventHandler
    public void saving(final WorldSaveEvent event) {
        // The snapshot's save event runs after seal: public tasks must stay rejected.
        if (!this.snapshotEventExpected || Bukkit.getOnlinePlayers().isEmpty()) return;
        try {
            Bukkit.getOnlinePlayers().iterator().next().getScheduler().run(this.plugin, task -> {
                throw new AssertionError("External task entered frozen snapshot");
            }, null);
        } catch (IllegalStateException expected) {
            require(expected.getMessage().contains("snapshotting"), expected.toString());
            this.rejectObserved = true;
        }
        try {
            Bukkit.getRegionScheduler().run(this.plugin, event.getWorld(), 0, 0, task -> {
                throw new AssertionError("External region task entered frozen snapshot");
            });
        } catch (IllegalStateException expected) {
            require(expected.getMessage().contains("snapshotting"), expected.toString());
            this.regionRejectObserved = true;
        }
        if (this.breakCopy != null) {
            final Path target = this.breakCopy;
            this.breakCopy = null;
            try {
                // Deliberately obstruct only our new test target after validation.
                Files.createDirectories(target.getParent());
                Files.writeString(target, "injected copy failure");
            } catch (Exception failure) { throw new RuntimeException(failure); }
        }
    }

    private CompletionStage<Void> snapshot(final String label, final List<World> worlds, final List<Player> players) {
        final long start = System.nanoTime();
        final Path target = this.output.resolve(label);
        this.snapshotEventExpected = true;
        return global(() -> Bukkit.getRuntimeWorldManager().snapshotWorldsAsync(worlds, target))
            .thenCompose(stage -> stage).thenAcceptAsync(result -> {
                this.snapshotEventExpected = false;
                require(result.successful(), result.toString());
                for (final Player player : players) {
                    require(Files.isRegularFile(target.resolve("players/data/" + player.getUniqueId() + ".dat")), "Missing player " + player.getUniqueId());
                }
                this.plugin.getLogger().info("SNAPSHOT_OK " + label + " players=" + players.size()
                    + " elapsedMs=" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
            });
    }

    private CompletionStage<List<Player>> awaitPlayers(final int count) {
        final CompletableFuture<List<Player>> result = new CompletableFuture<>();
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(this.plugin, task -> {
            final List<Player> players = Bukkit.getOnlinePlayers().stream()
                .sorted(java.util.Comparator.comparing(Player::getName)).map(player -> (Player)player).toList();
            if (players.size() == count) { task.cancel(); result.complete(players); }
        }, 1, 1);
        return result;
    }

    private CompletionStage<Void> teleport(final Player player, final Location location) {
        return owner(player, () -> player.setGameMode(GameMode.CREATIVE))
            .thenCompose(ignored -> player.teleportAsync(location)).thenAccept(success -> require(success, "Teleport failed"));
    }

    private CompletionStage<Location> ownerLocation(final Player player) {
        final CompletableFuture<Location> result = new CompletableFuture<>();
        owner(player, () -> result.complete(player.getLocation())).exceptionally(error -> { result.completeExceptionally(error); return null; });
        return result;
    }

    private CompletionStage<Void> owner(final Player player, final Runnable operation) {
        final CompletableFuture<Void> result = new CompletableFuture<>();
        try {
            final var task = player.getScheduler().run(this.plugin, ignored -> {
                try { operation.run(); result.complete(null); }
                catch (Throwable error) { result.completeExceptionally(error); }
            }, () -> result.completeExceptionally(new IllegalStateException("retired " + player.getName())));
            if (task == null) result.completeExceptionally(new IllegalStateException("rejected owner task"));
        } catch (Throwable error) { result.completeExceptionally(error); }
        return result;
    }

    private <T> CompletionStage<T> global(final Supplier<T> operation) {
        final CompletableFuture<T> result = new CompletableFuture<>();
        Bukkit.getGlobalRegionScheduler().execute(this.plugin, () -> {
            try { result.complete(operation.get()); }
            catch (Throwable error) { result.completeExceptionally(error); }
        });
        return result;
    }

    private static void require(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
