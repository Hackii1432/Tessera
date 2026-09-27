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
import org.bukkit.plugin.java.JavaPlugin;
import static dev.tessera.smoke.NativeRestoreSmoke.call;
import static dev.tessera.smoke.NativeRestoreSmoke.type;

/** Exercises the real shutdown boundary. The fixture runner represents the caller's offline journal recovery. */
final class NativeRestoreRecoverySmoke {
    private final JavaPlugin plugin;
    NativeRestoreRecoverySmoke(JavaPlugin plugin) { this.plugin = plugin; }

    void start() {
        this.plugin.getLogger().info("NATIVE_RESTORE_READY");
        Thread.ofVirtual().name("Native restore recovery fixture").start(() -> {
            try { run(); }
            catch (Throwable failure) {
                this.plugin.getLogger().log(java.util.logging.Level.SEVERE, "NATIVE_RESTORE_COMPONENTS_FAIL", failure);
                global(() -> { Bukkit.shutdown(); return null; });
            }
        });
    }

    private void run() throws Exception {
        require(Bukkit.getPlayerRestoreService().contractVersion() == 1, "accepted native contract exactly 1");
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
        while (await(global(() -> Bukkit.getOnlinePlayers().size())) != 2 && System.nanoTime() < until) Thread.sleep(20);
        List<Player> players = await(global(() -> List.copyOf(Bukkit.getOnlinePlayers())));
        require(players.size() == 2, "two recovery clients");
        String phase = System.getenv("NATIVE_RESTORE_RECOVERY_STAGE");
        boolean restarted = "1".equals(System.getenv("NATIVE_RESTORE_RECOVERY_RESTART"));
        Path evidence = Path.of("native-restore-evidence").toAbsolutePath();
        Files.createDirectories(evidence);
        Path source = evidence.resolve("recovery-source/players");
        Path rollback = evidence.resolve("recovery-rollback");
        var service = Bukkit.getPlayerRestoreService();
        var world = await(global(() -> Bukkit.getWorlds().getFirst()));
        await(global(() -> { Bukkit.getServerTickManager().setFrozen(true); return null; }));
        if (restarted) {
            int expected = phase.equals("committed") ? 137 : phase.equals("login") ? 111 : 999;
            for (Player player : players) require(await(owner(player, player::getTotalExperience)) == expected, "boot follows caller recovery decision, not stale native transaction");
            Object fence = type("org.bukkit.craftbukkit.world.PlayerStoreFence").getField("INSTANCE").get(null);
            require(!(boolean)call(fence, "isClosed"), "restart has no stale native fence");
            Thread.sleep(300);
            for (Player player : players) require(await(owner(player, player::getTotalExperience)) == expected, "no delayed native replay after boot");
            UUID next = UUID.randomUUID();
            check(await(service.prepareAsync(next, source, evidence.resolve("restart-rollback"), world)), PlayerRestoreStatus.PREPARED);
            check(await(service.applyAsync(next, true)), PlayerRestoreStatus.APPLIED);
            check(await(service.completeAsync(next, true)), PlayerRestoreStatus.COMPLETED);
            for (Player player : players) require(await(owner(player, player::getTotalExperience)) == 137, "new restore succeeds after recovery");
            Path resave = evidence.resolve("restart-resave");
            require(await(await(global(() -> Bukkit.getRuntimeWorldManager().snapshotWorldsAsync(List.copyOf(Bukkit.getWorlds()), resave)))).successful(), "new snapshot after recovery");
            Files.writeString(evidence.resolve("recovery-restart-checks.txt"), "Stage " + phase + ": caller-selected store loaded; no native replay/fence; new full prepare/apply/complete and resave succeeded with connected clients.\n");
            this.plugin.getLogger().info("NATIVE_RESTORE_RECOVERY_PASS");
            return;
        }
        for (Player player : players) await(owner(player, () -> { player.setGameMode(org.bukkit.GameMode.CREATIVE); player.setTotalExperience(137); return null; }));
        require(await(await(global(() -> Bukkit.getRuntimeWorldManager().snapshotWorldsAsync(List.copyOf(Bukkit.getWorlds()), source.getParent())))).successful(), "recovery source saved");
        for (Player player : players) await(owner(player, () -> { player.setTotalExperience(999); return null; }));
        UUID operation = UUID.randomUUID();
        if (phase.equals("login")) {
            for (Player player : players) await(owner(player, () -> { player.saveData(); player.setTotalExperience(111); return null; }));
            this.plugin.getLogger().info("NATIVE_RESTORE_RACE_LOGIN_HOLD");
            until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!Files.exists(evidence.resolve("runner-LOGIN_HOLD")) && System.nanoTime() < until) Thread.sleep(10);
            require(Files.exists(evidence.resolve("runner-LOGIN_HOLD")), "actual admitted configuration client paused");
            CompletionStage<PlayerRestoreResult> preparing = service.prepareAsync(operation, source, rollback, world);
            preparing.whenComplete((result, failure) -> {
                try { Files.writeString(evidence.resolve("shutdown-prepare-result.txt"), result == null ? String.valueOf(failure) : result.toString()); }
                catch (Exception error) { this.plugin.getLogger().log(java.util.logging.Level.SEVERE, "Cannot record prepare shutdown drain", error); }
            });
            Object fence = type("org.bukkit.craftbukkit.world.PlayerStoreFence").getField("INSTANCE").get(null);
            until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!(boolean)call(fence, "isClosed") && System.nanoTime() < until) Thread.sleep(10);
            require((boolean)call(fence, "isClosed") && !preparing.toCompletableFuture().isDone() && !Files.exists(rollback), "prepare waits for the admitted login before making backup");
        } else check(await(service.prepareAsync(operation, source, rollback, world)), PlayerRestoreStatus.PREPARED);
        if (!phase.equals("prepared") && !phase.equals("login")) {
            if (phase.equals("inflight")) {
                this.plugin.getLogger().info("NATIVE_RESTORE_RACE_ACK_HOLD");
                until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (!Files.exists(evidence.resolve("runner-ACK_HOLD")) && System.nanoTime() < until) Thread.sleep(10);
                require(Files.exists(evidence.resolve("runner-ACK_HOLD")), "runner withheld acknowledgement");
                CompletionStage<PlayerRestoreResult> applying = service.applyAsync(operation, true);
                applying.whenComplete((result, failure) -> {
                    try { Files.writeString(evidence.resolve("shutdown-apply-result.txt"), result == null ? String.valueOf(failure) : result.toString()); }
                    catch (Exception error) { this.plugin.getLogger().log(java.util.logging.Level.SEVERE, "Cannot record shutdown drain", error); }
                });
                until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (await(owner(players.getFirst(), players.getFirst()::getTotalExperience)) != 137 && System.nanoTime() < until) Thread.sleep(20);
                require(!applying.toCompletableFuture().isDone(), "shutdown catches actual in-flight native client acknowledgement");
            } else {
                check(await(service.applyAsync(operation, true)), PlayerRestoreStatus.APPLIED);
                if (phase.equals("committed")) check(await(service.completeAsync(operation, true)), PlayerRestoreStatus.COMPLETED);
            }
        }
        Object playerList = call(call(Bukkit.getServer(), "getServer"), "getPlayerList");
        Object playerIo = playerList.getClass().getField("playerIo").get(playerList);
        Path active = ((java.io.File)call(playerIo, "getPlayerDir")).toPath().toAbsolutePath().getParent();
        Files.writeString(evidence.resolve("recovery-active-path.txt"), active.toString());
        Files.writeString(evidence.resolve("recovery-operation.txt"), operation.toString());
        this.plugin.getLogger().info("NATIVE_RESTORE_RECOVERY_STOP");
    }

    @FunctionalInterface private interface Action<T> { T get() throws Exception; }
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
