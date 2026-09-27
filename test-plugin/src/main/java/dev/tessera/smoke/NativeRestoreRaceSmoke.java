package dev.tessera.smoke;

import io.papermc.paper.world.PlayerRestoreResult;
import io.papermc.paper.world.PlayerRestoreStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import static dev.tessera.smoke.NativeRestoreSmoke.call;
import static dev.tessera.smoke.NativeRestoreSmoke.type;

/** Explicit failure fixtures, separate from the zero-disconnect seamless acceptance run. */
final class NativeRestoreRaceSmoke implements Listener {
    private final JavaPlugin plugin;
    private volatile UUID operation;
    private volatile boolean disconnect;

    NativeRestoreRaceSmoke(JavaPlugin plugin) { this.plugin = plugin; }

    void start() {
        Bukkit.getPluginManager().registerEvents(this, this.plugin);
        this.plugin.getLogger().info("NATIVE_RESTORE_READY");
        Thread.ofVirtual().name("Native restore races").start(() -> {
            try {
                run();
                this.plugin.getLogger().info("NATIVE_RESTORE_RACES_PASS");
            } catch (Throwable failure) {
                this.plugin.getLogger().log(java.util.logging.Level.SEVERE, "NATIVE_RESTORE_COMPONENTS_FAIL", failure);
                global(() -> { Bukkit.shutdown(); return null; });
            }
        });
    }

    @EventHandler
    public void teleport(PlayerTeleportEvent event) {
        if (this.disconnect && event.getPlayer().getName().equals("RestoreTwo") && Bukkit.getPlayerRestoreService().isRestoreTeleport(event, this.operation)) {
            this.disconnect = false;
            event.getPlayer().kick(net.kyori.adventure.text.Component.text("Intentional native restore disconnect fixture"));
        }
    }

    private void run() throws Exception {
        require(Bukkit.getPlayerRestoreService().contractVersion() == 1, "accepted native contract exactly 1");
        waitFor(() -> await(global(() -> Bukkit.getOnlinePlayers().size())) == 2, 90, "initial two clients");
        Player one = await(global(() -> Bukkit.getPlayerExact("RestoreOne")));
        Player two = await(global(() -> Bukkit.getPlayerExact("RestoreTwo")));
        var world = await(global(() -> Bukkit.getWorlds().getFirst()));
        await(global(() -> { Bukkit.getServerTickManager().setFrozen(true); return null; }));
        for (Player player : List.of(one, two)) await(owner(player, () -> {
            player.setGameMode(org.bukkit.GameMode.CREATIVE);
            player.setTotalExperience(137);
            player.getWorld().spawn(player.getLocation().add(0, 5, 0), org.bukkit.entity.EnderPearl.class, pearl -> {
                pearl.setGravity(false); pearl.setShooter(player); pearl.setVelocity(new org.bukkit.util.Vector());
            });
            return null;
        }));
        Path evidence = Path.of("native-restore-evidence").toAbsolutePath();
        Files.createDirectories(evidence);
        Path snapshot = evidence.resolve("race-source");
        require(await(await(global(() -> Bukkit.getRuntimeWorldManager().snapshotWorldsAsync(List.copyOf(Bukkit.getWorlds()), snapshot)))).successful(), "real race source snapshot");
        Path source = snapshot.resolve("players");
        var service = Bukkit.getPlayerRestoreService();
        for (Player player : List.of(one, two)) await(owner(player, () -> { player.setTotalExperience(999); return null; }));

        this.operation = UUID.randomUUID();
        UUID id = this.operation;
        check(await(service.prepareAsync(id, source, evidence.resolve("race-rollback"), world)), PlayerRestoreStatus.PREPARED);
        signal("DISCONNECT", evidence);
        this.disconnect = true;
        PlayerRestoreResult applied = await(service.applyAsync(id, true));
        require(applied.successful(), "disconnect apply settles with durable canonical offline state: " + applied);
        require(await(global(() -> Bukkit.getPlayerExact("RestoreTwo"))) == null, "intentional disconnected participant has retired");
        require(await(owner(one, one::getTotalExperience)) == 137, "other region completed restoration");
        signal("LOGIN", evidence);
        Thread.sleep(250);
        require(await(global(() -> Bukkit.getPlayerExact("RestoreTwo"))) == null, "new login cannot pass closed native admission");
        check(await(service.completeAsync(id, true)), PlayerRestoreStatus.COMPLETED);
        waitFor(() -> await(global(() -> Bukkit.getPlayerExact("RestoreTwo"))) != null, 40, "queued login resumes after completion");
        Player rejoined = await(global(() -> Bukkit.getPlayerExact("RestoreTwo")));
        require(await(owner(rejoined, rejoined::getTotalExperience)) == 137, "queued login reads new canonical store, not old generation");
        require(await(owner(rejoined, () -> ((java.util.Set<?>)call(call(rejoined, "getHandle"), "getEnderPearls")).size())) == 1, "login pearl children drained without duplicates");

        // Do not acknowledge one real network transfer; connection keepalive
        // remains active while the native wall-clock timeout drains the phase.
        this.operation = UUID.randomUUID();
        UUID timeout = this.operation;
        check(await(service.prepareAsync(timeout, source, evidence.resolve("timeout-rollback"), world)), PlayerRestoreStatus.PREPARED);
        signal("ACK_HOLD", evidence);
        long start = System.nanoTime();
        PlayerRestoreResult timedOut = await(service.applyAsync(timeout, true));
        require(!timedOut.successful() && timedOut.status() == PlayerRestoreStatus.TRANSFER_FAILED, "missing client acknowledgement fails native apply: " + timedOut);
        require(System.nanoTime() - start >= TimeUnit.SECONDS.toNanos(25), "real acknowledgement timeout was exercised");
        require(await(global(() -> Bukkit.getOnlinePlayers().size())) == 2, "timeout did not kick either client");
        signal("ACK_RELEASE", evidence);
        check(await(service.applyAsync(timeout, false)), PlayerRestoreStatus.ROLLED_BACK);
        check(await(service.completeAsync(timeout, false)), PlayerRestoreStatus.COMPLETED);
        require(await(await(global(() -> Bukkit.getRuntimeWorldManager().snapshotWorldsAsync(List.copyOf(Bukkit.getWorlds()), evidence.resolve("race-final-save"))))).successful(), "save works after disconnect and timeout recovery");
        Object fence = type("org.bukkit.craftbukkit.world.PlayerStoreFence").getField("INSTANCE").get(null);
        require(!(boolean)call(fence, "isClosed"), "no remaining login fence");
        Files.writeString(evidence.resolve("race-checks.txt"), "Actual restore-event disconnect; canonical offline state; queued login blocked until complete and then admitted; no duplicate pearl after rejoin; real missing-client-ACK timeout under freeze; rollback; successful resave; no remaining fence. The one intentional kick/rejoin is a failure fixture, not seamless success.\n");
    }

    private void signal(String signal, Path evidence) throws Exception {
        this.plugin.getLogger().info("NATIVE_RESTORE_RACE_" + signal);
        waitFor(() -> Files.exists(evidence.resolve("runner-" + signal)), 10, "runner signal " + signal);
    }

    @FunctionalInterface private interface Action<T> { T get() throws Exception; }
    @FunctionalInterface private interface Check { boolean get() throws Exception; }
    private static void waitFor(Check check, int seconds, String reason) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        do { if (check.get()) return; Thread.sleep(20); } while (System.nanoTime() < until);
        throw new AssertionError("Timed out: " + reason);
    }
    private <T> CompletionStage<T> owner(Player player, Action<T> action) throws Exception {
        Object scheduler = player.getClass().getField("taskScheduler").get(player);
        Function<Object, CompletionStage<T>> run = ignored -> {
            try { return CompletableFuture.completedStage(action.get()); }
            catch (Throwable failure) { return CompletableFuture.failedStage(failure); }
        };
        return (CompletionStage<T>)call(scheduler, "scheduleRestore", run);
    }
    private <T> CompletionStage<T> global(Supplier<T> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Bukkit.getGlobalRegionScheduler().execute(this.plugin, () -> {
            try { result.complete(action.get()); } catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        return result;
    }
    private static <T> T await(CompletionStage<T> stage) throws Exception { return stage.toCompletableFuture().get(90, TimeUnit.SECONDS); }
    private static void check(PlayerRestoreResult result, PlayerRestoreStatus status) { require(result.successful() && result.status() == status, "transaction result: " + result); }
    private static void require(boolean condition, String reason) { if (!condition) throw new AssertionError(reason); }
}
