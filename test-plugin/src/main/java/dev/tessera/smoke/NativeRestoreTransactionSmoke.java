package dev.tessera.smoke;

import io.papermc.paper.world.PlayerRestoreResult;
import io.papermc.paper.world.PlayerRestoreStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import static dev.tessera.smoke.NativeRestoreSmoke.call;
import static dev.tessera.smoke.NativeRestoreSmoke.type;

/** Public transaction integration, on new disposable worlds with two actual network clients. */
final class NativeRestoreTransactionSmoke implements Listener {
    private final JavaPlugin plugin;
    private final List<String> checks = new ArrayList<>();
    private volatile UUID operation;
    private volatile boolean veto;
    private final java.util.concurrent.atomic.AtomicInteger scopedTeleports = new java.util.concurrent.atomic.AtomicInteger();

    NativeRestoreTransactionSmoke(JavaPlugin plugin) { this.plugin = plugin; }

    void start() {
        Bukkit.getPluginManager().registerEvents(this, this.plugin);
        this.plugin.getLogger().info("NATIVE_RESTORE_READY");
        Thread.ofVirtual().name("Native restore transaction fixture").start(() -> {
            try {
                run();
                Files.createDirectories(Path.of("native-restore-evidence"));
                Files.writeString(Path.of("native-restore-evidence/checks.txt"), String.join("\n", this.checks));
                this.plugin.getLogger().info("NATIVE_RESTORE_COMPONENTS_PASS");
            } catch (Throwable failure) {
                this.plugin.getLogger().log(java.util.logging.Level.SEVERE, "NATIVE_RESTORE_COMPONENTS_FAIL", failure);
                global(() -> { Bukkit.shutdown(); return null; });
            }
        });
    }

    @EventHandler
    public void teleport(PlayerTeleportEvent event) {
        if (this.operation != null && Bukkit.getPlayerRestoreService().isRestoreTeleport(event, this.operation)) {
            require(Bukkit.isOwnedByCurrentRegion(event.getPlayer()), "teleport event must run on player owner");
            this.scopedTeleports.incrementAndGet();
            if (this.veto) event.setCancelled(true);
        }
    }

    private void run() throws Exception {
        require(Bukkit.getPlayerRestoreService().contractVersion() == 1, "accepted native contract exactly 1");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
        List<Player> players;
        do {
            players = await(global(() -> List.copyOf(Bukkit.getOnlinePlayers())));
            if (players.size() == 2) break;
            Thread.sleep(100);
        } while (System.nanoTime() < deadline);
        require(players.size() == 2, "two connected clients");
        List<Player> participants = List.copyOf(players);
        String desertFlat = "{\"biome\":\"minecraft:desert\",\"layers\":[{\"block\":\"minecraft:bedrock\",\"height\":1},{\"block\":\"minecraft:red_sand\",\"height\":3}],\"structure_overrides\":[]}";
        World target = await(await(global(() -> Bukkit.getRuntimeWorldManager().createWorldAsync(new WorldCreator("transaction_target").type(WorldType.FLAT).generatorSettings(desertFlat))))).world();
        require(target != null, "prepared replacement world");
        World nether = await(global(() -> Bukkit.getWorlds().stream().filter(w -> w.getEnvironment() == World.Environment.NETHER).findFirst().orElseThrow()));
        Player first = participants.get(0), second = participants.get(1);
        require(await(await(owner(first, () -> first.teleportAsync(new Location(first.getWorld(), 4096.5, 90, 4096.5))))), "first owner setup");
        require(await(await(owner(second, () -> second.teleportAsync(new Location(nether, 32.5, 90, 32.5))))), "second owner setup");
        await(owner(first, () -> { require(!Bukkit.isOwnedByCurrentRegion(second), "distinct player regions"); return null; }));
        await(global(() -> { Bukkit.getServerTickManager().setFrozen(true); return null; }));
        NamespacedKey marker = new NamespacedKey(this.plugin, "transaction_saved");
        NamespacedKey extra = new NamespacedKey(this.plugin, "transaction_removed");
        java.util.Map<UUID, Object[]> handles = new java.util.HashMap<>();
        for (Player player : participants) {
            handles.put(player.getUniqueId(), await(owner(player, () -> {
                player.setGameMode(GameMode.CREATIVE);
                player.getInventory().clear();
                player.getEnderChest().clear();
                player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 7));
                player.getEnderChest().setItem(0, new ItemStack(Material.EMERALD, 5));
                player.setTotalExperience(137);
                player.setLevel(8);
                player.setHealth(14);
                player.setFoodLevel(17);
                player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(20);
                player.discoverRecipe(NamespacedKey.minecraft("oak_planks"));
                player.undiscoverRecipe(NamespacedKey.minecraft("crafting_table"));
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.NIGHT_VISION, 10000, 0));
                var advancement = Bukkit.getAdvancement(NamespacedKey.minecraft("story/mine_stone"));
                for (String criterion : player.getAdvancementProgress(advancement).getRemainingCriteria()) player.getAdvancementProgress(advancement).awardCriteria(criterion);
                var later = Bukkit.getAdvancement(NamespacedKey.minecraft("story/upgrade_tools"));
                for (String criterion : player.getAdvancementProgress(later).getAwardedCriteria()) player.getAdvancementProgress(later).revokeCriteria(criterion);
                var boat = player.getWorld().spawn(player.getLocation(), org.bukkit.entity.boat.OakChestBoat.class);
                boat.setGravity(false);
                boat.getInventory().setItem(0, new ItemStack(Material.AMETHYST_SHARD, 13));
                require(boat.addPassenger(player), "initial saved boat attachment");
                var parrot = player.getWorld().spawn(player.getLocation(), org.bukkit.entity.Parrot.class);
                parrot.setOwner(player);
                Object shoulder = call(type("net.minecraft.world.level.storage.TagValueOutput"), "createWithContext", type("net.minecraft.util.ProblemReporter").getField("DISCARDING").get(null), call(call(player, "getHandle"), "registryAccess"));
                call(call(parrot, "getHandle"), "save", shoulder);
                call(call(player, "getHandle"), "setShoulderEntityLeft", call(shoulder, "buildResult"));
                parrot.remove();
                player.getWorld().spawn(player.getLocation().add(0, 5, 0), org.bukkit.entity.EnderPearl.class, pearl -> {
                    pearl.setGravity(false);
                    pearl.setVelocity(new org.bukkit.util.Vector());
                    pearl.setShooter(player);
                });
                player.setStatistic(org.bukkit.Statistic.JUMP, 23);
                player.setStatistic(org.bukkit.Statistic.DROP_COUNT, 0);
                player.getPersistentDataContainer().set(marker, PersistentDataType.STRING, "saved");
                return new Object[]{call(player, "getHandle"), player.getInventory(), player.getEnderChest()};
            })));
        }
        var offline = Bukkit.getOfflinePlayer(UUID.fromString("34421111-aabc-41de-b55a-010000000010"));
        offline.setStatistic(org.bukkit.Statistic.JUMP, 42);
        Path source = await(snapshot("transaction-source")).resolve("players");
        offline.setStatistic(org.bukkit.Statistic.JUMP, 98);
        var statisticMethod = offline.getClass().getDeclaredMethod("getStatisticManager");
        statisticMethod.setAccessible(true);
        Object staleOfflineCounter = statisticMethod.invoke(offline);
        for (Player player : participants) {
            Path file = source.resolve("data/" + player.getUniqueId() + ".dat");
            Object data = readNbt(file);
            World destination = player == first ? target : nether;
            call(data, "putLong", "WorldUUIDMost", destination.getUID().getMostSignificantBits());
            call(data, "putLong", "WorldUUIDLeast", destination.getUID().getLeastSignificantBits());
            call(data, "putString", "Dimension", destination.getKey().toString());
            Object pos = type("net.minecraft.world.phys.Vec3").getConstructor(double.class, double.class, double.class)
                .newInstance(player == first ? 8.5 : 4128.5, 90.0, 32.5);
            call(data, "store", "Pos", type("net.minecraft.world.phys.Vec3").getField("CODEC").get(null), pos);
            for (Object pearl : (Iterable<?>)call(data, "getListOrEmpty", "ender_pearls")) {
                call(pearl, "putString", "ender_pearl_dimension", destination.getKey().toString());
            }
            call(type("net.minecraft.nbt.NbtIo"), "writeCompressed", data, file);
        }
        await(global(() -> { Bukkit.getServerTickManager().setFrozen(true); return null; }));
        var service = Bukkit.getPlayerRestoreService();
        for (int round = 0; round < 2; round++) {
            await(mutate(participants, 999, extra));
            this.operation = UUID.randomUUID();
            UUID id = this.operation;
            Path rollback = Path.of("native-restore-evidence", "transaction-rollback-" + round).toAbsolutePath();
            var sourceBytes = bytes(source);
            checkResult(await(service.prepareAsync(id, source, rollback, target)), id, PlayerRestoreStatus.PREPARED);
            checkResult(await(service.prepareAsync(id, source, rollback, target)), id, PlayerRestoreStatus.PREPARED);
            boolean offlineDenied = false;
            try { offline.setStatistic(org.bukkit.Statistic.JUMP, 777); }
            catch (IllegalStateException expected) { offlineDenied = true; }
            require(offlineDenied, "offline statistic mutation is fenced before loading its counter");
            for (Player player : participants) require(nbtInt(readNbt(rollback.resolve("data/" + player.getUniqueId() + ".dat")), "XpTotal") == 999, "fresh rollback backup includes current XP");
            require(await(service.prepareAsync(UUID.randomUUID(), source, rollback.resolveSibling("busy"), target)).status() == PlayerRestoreStatus.BUSY, "concurrent operation rejected");
            var blocked = await(await(global(() -> Bukkit.getRuntimeWorldManager().snapshotWorldsAsync(List.copyOf(Bukkit.getWorlds()), Path.of("native-restore-evidence", "blocked-transaction")))));
            require(blocked.status() == io.papermc.paper.world.WorldSnapshotResult.Status.SOURCE_BUSY, "snapshot fenced during transaction");
            checkResult(await(service.applyAsync(id, true)), id, PlayerRestoreStatus.APPLIED);
            checkResult(await(service.applyAsync(id, true)), id, PlayerRestoreStatus.APPLIED);
            for (Player player : participants) await(owner(player, () -> {
                Object[] original = handles.get(player.getUniqueId());
                require(call(player, "getHandle") == original[0] && player.getInventory() == original[1] && player.getEnderChest() == original[2], "native and wrapper identity preserved");
                require(player.getWorld().equals(player == first ? target : nether), "resolved replacement world");
                require(player.getTotalExperience() == 137 && player.getLevel() == 8, "snapshot XP restored");
                require(player.getInventory().getItem(0).getType() == Material.DIAMOND && player.getInventory().getItem(0).getAmount() == 7, "snapshot inventory restored");
                require(player.getEnderChest().getItem(0).getType() == Material.EMERALD, "snapshot ender chest restored");
                require(player.getStatistic(org.bukkit.Statistic.JUMP) == 23 && player.getStatistic(org.bukkit.Statistic.DROP_COUNT) == 0, "statistics replaced, not merged");
                require(!player.getPersistentDataContainer().has(extra) && "saved".equals(player.getPersistentDataContainer().get(marker, PersistentDataType.STRING)), "PDC replaced");
                require(player.getHealth() == 14 && player.getFoodLevel() == 17, "health/hunger exact");
                require(player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getBaseValue() == 20 && player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getModifiers().isEmpty(), "attribute base and removed modifiers replaced");
                require(player.hasDiscoveredRecipe(NamespacedKey.minecraft("oak_planks")) && !player.hasDiscoveredRecipe(NamespacedKey.minecraft("crafting_table")), "recipes replaced");
                require(player.hasPotionEffect(org.bukkit.potion.PotionEffectType.NIGHT_VISION) && !player.hasPotionEffect(org.bukkit.potion.PotionEffectType.SPEED), "effects replaced");
                require(player.getAdvancementProgress(Bukkit.getAdvancement(NamespacedKey.minecraft("story/mine_stone"))).isDone() && !player.getAdvancementProgress(Bukkit.getAdvancement(NamespacedKey.minecraft("story/upgrade_tools"))).isDone(), "advancement progress replaced");
                require(player.getVehicle() instanceof org.bukkit.entity.boat.OakChestBoat boat && boat.getInventory().getItem(0).getType() == Material.AMETHYST_SHARD && boat.getInventory().getItem(0).getAmount() == 13, "saved vehicle graph and inventory restored");
                require(!(boolean)call(call(call(player, "getHandle"), "getShoulderEntityLeft"), "isEmpty"), "shoulder entity restored");
                require(((java.util.Set<?>)call(call(player, "getHandle"), "getEnderPearls")).size() == 1, "exactly one restored owned pearl");
                return null;
            }));
            checkResult(await(service.completeAsync(id, true)), id, PlayerRestoreStatus.COMPLETED);
            checkResult(await(service.completeAsync(id, true)), id, PlayerRestoreStatus.COMPLETED);
            call(staleOfflineCounter, "save");
            require(offline.getStatistic(org.bukkit.Statistic.JUMP) == 42, "obsolete offline counter cannot overwrite restored file generation");
            require(await(service.applyAsync(id, false)).status() == PlayerRestoreStatus.CONFLICT, "committed decision cannot be reversed");
            sameBytes(sourceBytes, bytes(source));
            source = await(snapshot("transaction-resave-" + round)).resolve("players");
            this.checks.add("Public prepare/apply/complete cycle " + round + ": fresh on-disk backup, idempotence, store generation, exact player state, immutable source and successful resave");
        }

        await(mutate(participants, 777, extra));
        this.operation = UUID.randomUUID();
        UUID rollbackId = this.operation;
        checkResult(await(service.prepareAsync(rollbackId, source, Path.of("native-restore-evidence", "real-rollback").toAbsolutePath(), target)), rollbackId, PlayerRestoreStatus.PREPARED);
        checkResult(await(service.applyAsync(rollbackId, true)), rollbackId, PlayerRestoreStatus.APPLIED);
        checkResult(await(service.applyAsync(rollbackId, false)), rollbackId, PlayerRestoreStatus.ROLLED_BACK);
        for (Player player : participants) await(owner(player, () -> { require(player.getTotalExperience() == 777 && player.getPersistentDataContainer().has(extra), "real on-disk rollback restored previous state"); return null; }));
        checkResult(await(service.completeAsync(rollbackId, false)), rollbackId, PlayerRestoreStatus.COMPLETED);
        await(snapshot("transaction-after-rollback"));
        this.checks.add("Real published-store rollback restored XP/PDC from the freshly captured rollback directory and allowed another snapshot");

        await(mutate(participants, 888, extra));
        this.operation = UUID.randomUUID();
        UUID vetoId = this.operation;
        checkResult(await(service.prepareAsync(vetoId, source, Path.of("native-restore-evidence", "veto-rollback").toAbsolutePath(), target)), vetoId, PlayerRestoreStatus.PREPARED);
        this.veto = true;
        require(!await(service.applyAsync(vetoId, true)).successful(), "public apply reports teleport veto");
        this.veto = false;
        checkResult(await(service.applyAsync(vetoId, false)), vetoId, PlayerRestoreStatus.ROLLED_BACK);
        checkResult(await(service.completeAsync(vetoId, false)), vetoId, PlayerRestoreStatus.COMPLETED);
        for (Player player : participants) await(owner(player, () -> { require(player.getTotalExperience() == 888, "veto rollback restored state"); return null; }));
        this.checks.add("Transfer veto after file publication failed explicitly; rollback and completion restored native state and reopened admission");

        Path empty = Path.of("native-restore-evidence", "empty-source").toAbsolutePath();
        for (String store : List.of("data", "stats", "advancements")) Files.createDirectories(empty.resolve(store));
        this.operation = UUID.randomUUID();
        UUID freshId = this.operation;
        checkResult(await(service.prepareAsync(freshId, empty, Path.of("native-restore-evidence", "fresh-rollback").toAbsolutePath(), target)), freshId, PlayerRestoreStatus.PREPARED);
        checkResult(await(service.applyAsync(freshId, true)), freshId, PlayerRestoreStatus.APPLIED);
        for (Player player : participants) await(owner(player, () -> {
            require(player.getWorld().equals(target) && player.getTotalExperience() == 0 && player.getInventory().isEmpty() && player.getEnderChest().isEmpty(), "absent save uses fresh fallback state");
            require(player.getPersistentDataContainer().isEmpty() && player.getStatistic(org.bukkit.Statistic.JUMP) == 0, "absent save removes PDC and stats");
            return null;
        }));
        checkResult(await(service.applyAsync(freshId, false)), freshId, PlayerRestoreStatus.ROLLED_BACK);
        checkResult(await(service.completeAsync(freshId, false)), freshId, PlayerRestoreStatus.COMPLETED);
        this.checks.add("Players without a saved entry received fresh defaults in the explicit fallback world; rollback restored their original files/state");

        // An admitted writer must drain before backup, and cancellation of a
        // caller view must not open the server-owned fence or cancel its worker.
        Object fence = type("org.bukkit.craftbukkit.world.PlayerStoreFence").getField("INSTANCE").get(null);
        AutoCloseable writer = (AutoCloseable)call(fence, "requireRead");
        this.operation = UUID.randomUUID();
        UUID cancelled = this.operation;
        Path cancelBackup = Path.of("native-restore-evidence", "cancel-rollback").toAbsolutePath();
        CompletionStage<PlayerRestoreResult> preparing = service.prepareAsync(cancelled, source, cancelBackup, target);
        long untilClosed = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!(boolean)call(fence, "isClosed") && System.nanoTime() < untilClosed) Thread.sleep(10);
        require((boolean)call(fence, "isClosed") && !preparing.toCompletableFuture().isDone() && !Files.exists(cancelBackup), "native prepare waits for admitted writer before backup");
        preparing.toCompletableFuture().cancel(false);
        var rollingBack = service.applyAsync(cancelled, false);
        writer.close();
        require(!await(service.prepareAsync(cancelled, source, cancelBackup, target)).successful(), "rollback cancels admitted prepare");
        checkResult(await(rollingBack), cancelled, PlayerRestoreStatus.ROLLED_BACK);
        checkResult(await(service.completeAsync(cancelled, false)), cancelled, PlayerRestoreStatus.COMPLETED);
        require(!(boolean)call(fence, "isClosed"), "cancelled prepare leaves no login fence after completion");
        this.checks.add("Native admission drain, cancelled caller future and concurrent rollback: no early backup/publication, all work drained, fence reopened only by completion");

        this.operation = UUID.randomUUID();
        UUID heldComplete = this.operation;
        checkResult(await(service.prepareAsync(heldComplete, source, Path.of("native-restore-evidence", "held-complete").toAbsolutePath(), target)), heldComplete, PlayerRestoreStatus.PREPARED);
        checkResult(await(service.applyAsync(heldComplete, true)), heldComplete, PlayerRestoreStatus.APPLIED);
        AutoCloseable finalWriter = (AutoCloseable)call(fence, "writeOwned", heldComplete);
        var completing = service.completeAsync(heldComplete, true);
        Thread.sleep(150);
        require(!completing.toCompletableFuture().isDone() && (boolean)call(fence, "isClosed"), "completion cannot release a live writer");
        completing.toCompletableFuture().cancel(false);
        finalWriter.close();
        checkResult(await(service.completeAsync(heldComplete, true)), heldComplete, PlayerRestoreStatus.COMPLETED);
        this.checks.add("Completion waited for native owned writer even after its caller cancelled; repeated completion returned the same committed decision");

        // Exercise real native I/O error paths, not an injected success logger.
        if (System.getProperty("os.name").startsWith("Windows")) {
            Object nativeServer = call(Bukkit.getServer(), "getServer");
            Object playerList = call(nativeServer, "getPlayerList");
            Object playerIo = playerList.getClass().getField("playerIo").get(playerList);
            Path liveFile = ((java.io.File)call(playerIo, "getPlayerDir")).toPath().resolve(first.getUniqueId() + ".dat");
            java.nio.file.OpenOption noDelete = (java.nio.file.OpenOption)Enum.valueOf((Class)Class.forName("com.sun.nio.file.ExtendedOpenOption"), "NOSHARE_DELETE");
            java.nio.file.OpenOption noWrite = (java.nio.file.OpenOption)Enum.valueOf((Class)Class.forName("com.sun.nio.file.ExtendedOpenOption"), "NOSHARE_WRITE");
            Path statFile = liveFile.getParent().getParent().resolve("stats").resolve(first.getUniqueId() + ".json");
            this.operation = UUID.randomUUID();
            UUID saveFailure = this.operation;
            try (var lockedStat = java.nio.channels.FileChannel.open(statFile, java.nio.file.StandardOpenOption.READ, noWrite)) {
                PlayerRestoreResult failedSave = await(service.prepareAsync(saveFailure, source, Path.of("native-restore-evidence", "failed-save").toAbsolutePath(), target));
                require(!failedSave.successful() && failedSave.status() == PlayerRestoreStatus.SAVE_FAILED, "real native statistic flush failure is reported: " + failedSave);
                require((boolean)call(fence, "isClosed"), "failed save retains admission for explicit recovery");
            }
            checkResult(await(service.applyAsync(saveFailure, false)), saveFailure, PlayerRestoreStatus.ROLLED_BACK);
            checkResult(await(service.completeAsync(saveFailure, false)), saveFailure, PlayerRestoreStatus.COMPLETED);
            this.checks.add("Actual native statistic-file flush failure aborted prepare and retained admission until explicit rollback/completion");
            this.operation = UUID.randomUUID();
            UUID ioFailure = this.operation;
            checkResult(await(service.prepareAsync(ioFailure, source, Path.of("native-restore-evidence", "publication-failure").toAbsolutePath(), target)), ioFailure, PlayerRestoreStatus.PREPARED);
            try (var lockedFile = java.nio.channels.FileChannel.open(liveFile, java.nio.file.StandardOpenOption.READ, noDelete)) {
                require(!await(service.applyAsync(ioFailure, true)).successful(), "locked native store causes explicit publication failure");
                require((boolean)call(fence, "isClosed"), "failed apply retains fence");
                require(!await(service.completeAsync(ioFailure, true)).successful(), "failed apply cannot commit");
            }
            checkResult(await(service.applyAsync(ioFailure, false)), ioFailure, PlayerRestoreStatus.ROLLED_BACK);
            checkResult(await(service.completeAsync(ioFailure, false)), ioFailure, PlayerRestoreStatus.COMPLETED);
            this.checks.add("Actual Windows file-sharing failure prevented native publication; failure retained admission, disallowed commit and recovered by real rollback");
        }

        // A dead current handle must be replaced without a synthetic reconnect.
        await(owner(first, () -> { first.setHealth(0); return null; }));
        this.operation = UUID.randomUUID();
        UUID dead = this.operation;
        checkResult(await(service.prepareAsync(dead, source, Path.of("native-restore-evidence", "dead-rollback").toAbsolutePath(), target)), dead, PlayerRestoreStatus.PREPARED);
        checkResult(await(service.applyAsync(dead, true)), dead, PlayerRestoreStatus.APPLIED);
        await(owner(first, () -> { require(!first.isDead() && first.getHealth() == 14, "dead player restored to saved living state"); return null; }));
        checkResult(await(service.completeAsync(dead, true)), dead, PlayerRestoreStatus.COMPLETED);
        this.checks.add("Dead connected player restored to saved health via native client respawn/reset without replacing the connection");

        await(owner(first, () -> {
            first.leaveVehicle();
            var foot = first.getLocation().getBlock();
            var head = foot.getRelative(org.bukkit.block.BlockFace.NORTH);
            var footData = (org.bukkit.block.data.type.Bed)Material.RED_BED.createBlockData();
            footData.setFacing(org.bukkit.block.BlockFace.NORTH);
            footData.setPart(org.bukkit.block.data.type.Bed.Part.FOOT);
            var headData = (org.bukkit.block.data.type.Bed)footData.clone();
            headData.setPart(org.bukkit.block.data.type.Bed.Part.HEAD);
            foot.setBlockData(footData, false);
            head.setBlockData(headData, false);
            require(first.sleep(head.getLocation(), true), "enter real sleeping state before restore");
            return null;
        }));
        this.operation = UUID.randomUUID();
        UUID sleeping = this.operation;
        checkResult(await(service.prepareAsync(sleeping, source, Path.of("native-restore-evidence", "sleeping-rollback").toAbsolutePath(), target)), sleeping, PlayerRestoreStatus.PREPARED);
        checkResult(await(service.applyAsync(sleeping, true)), sleeping, PlayerRestoreStatus.APPLIED);
        await(owner(first, () -> { require(!first.isSleeping() && first.getTotalExperience() == 137, "sleeping player restored to saved awake state"); return null; }));
        checkResult(await(service.completeAsync(sleeping, true)), sleeping, PlayerRestoreStatus.COMPLETED);
        this.checks.add("Sleeping connected player safely detached from its current bed and restored to the saved awake state");

        UUID late = UUID.randomUUID();
        checkResult(await(service.applyAsync(late, false)), late, PlayerRestoreStatus.ROLLED_BACK);
        require(await(service.prepareAsync(late, source, Path.of("native-restore-evidence", "late").toAbsolutePath(), target)).status() == PlayerRestoreStatus.CONFLICT, "late prepare cannot revive cancelled operation");
        checkResult(await(service.completeAsync(late, false)), late, PlayerRestoreStatus.COMPLETED);
        require(this.scopedTeleports.get() >= 10, "real operation-scoped teleport events observed");
        await(target.getChunkAtAsync(2048, 2048, true));
        CompletableFuture<Void> generated = new CompletableFuture<>();
        Bukkit.getRegionScheduler().execute(this.plugin, target, 2048, 2048, () -> {
            try {
                require(Bukkit.isOwnedByCurrentRegion(target, 2048, 2048), "new chunk checked on its owner");
                require(target.getBiome(32768, target.getMinHeight() + 3, 32768) == org.bukkit.block.Biome.DESERT, "new chunk retains explicit target biome instead of global plains");
                require(target.getBlockAt(32768, target.getMinHeight() + 3, 32768).getType() == Material.RED_SAND, "new chunk retains explicit target generator layers");
                generated.complete(null);
            } catch (Throwable failure) { generated.completeExceptionally(failure); }
        });
        await(generated);
        this.checks.add("Prepared desert/red-sand generator and Nether generator bindings survived repeated restores; a newly generated target chunk retained its original biome/layers instead of the server's plains settings");
        await(global(() -> { Bukkit.getServerTickManager().setFrozen(false); return null; }));
        await(snapshot("transaction-final-save"));
        this.checks.add("Cancelled identities stayed cancelled; gameplay freeze was released and the final runtime snapshot succeeded without reconnect");
    }

    private CompletionStage<Void> mutate(List<Player> players, int xp, NamespacedKey extra) throws Exception {
        List<CompletableFuture<?>> stages = new ArrayList<>();
        for (Player player : players) stages.add(owner(player, () -> {
            player.setTotalExperience(xp);
            player.setLevel(24);
            var attribute = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            attribute.setBaseValue(40);
            if (attribute.getModifier(extra) == null) attribute.addModifier(new org.bukkit.attribute.AttributeModifier(extra, 2, org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER));
            player.setHealth(32);
            player.setFoodLevel(4);
            player.discoverRecipe(NamespacedKey.minecraft("crafting_table"));
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, 10000, 1));
            var advancement = Bukkit.getAdvancement(NamespacedKey.minecraft("story/upgrade_tools"));
            for (String criterion : player.getAdvancementProgress(advancement).getRemainingCriteria()) player.getAdvancementProgress(advancement).awardCriteria(criterion);
            if (player.getVehicle() instanceof org.bukkit.entity.boat.OakChestBoat boat) boat.getInventory().setItem(0, new ItemStack(Material.DIRT, 42));
            player.getInventory().setItem(0, new ItemStack(Material.DIRT, 64));
            player.setStatistic(org.bukkit.Statistic.DROP_COUNT, 33);
            player.getPersistentDataContainer().set(extra, PersistentDataType.STRING, "added after snapshot");
            return null;
        }).toCompletableFuture());
        return CompletableFuture.allOf(stages.toArray(CompletableFuture[]::new));
    }

    private CompletionStage<Path> snapshot(String name) {
        Path path = Path.of("native-restore-evidence", name).toAbsolutePath();
        return global(() -> Bukkit.getRuntimeWorldManager().snapshotWorldsAsync(List.copyOf(Bukkit.getWorlds()), path)).thenCompose(Function.identity()).thenApply(result -> {
            require(result.successful(), "native runtime snapshot: " + result);
            return path;
        });
    }

    private static Object readNbt(Path file) throws Exception {
        return call(type("net.minecraft.nbt.NbtIo"), "readCompressed", file, call(type("net.minecraft.nbt.NbtAccounter"), "create", 16L * 1024 * 1024));
    }

    private static int nbtInt(Object tag, String key) throws Exception { return (int)((java.util.Optional<?>)call(tag, "getInt", key)).orElseThrow(); }

    private static java.util.Map<String, byte[]> bytes(Path root) throws Exception {
        java.util.Map<String, byte[]> result = new java.util.HashMap<>();
        try (var paths = Files.walk(root)) {
            for (Path file : paths.filter(Files::isRegularFile).toList()) result.put(root.relativize(file).toString(), Files.readAllBytes(file));
        }
        return result;
    }

    private static void sameBytes(java.util.Map<String, byte[]> before, java.util.Map<String, byte[]> after) {
        require(before.keySet().equals(after.keySet()), "source file set unchanged");
        before.forEach((name, bytes) -> require(Arrays.equals(bytes, after.get(name)), "source bytes unchanged: " + name));
    }

    private static void checkResult(PlayerRestoreResult result, UUID operation, PlayerRestoreStatus status) {
        require(result.operationId().equals(operation) && result.successful() && result.status() == status, "public transaction result: " + result);
    }

    @FunctionalInterface private interface Action<T> { T get() throws Exception; }
    private <T> CompletionStage<T> owner(Player player, Action<T> action) throws Exception {
        Object scheduler = player.getClass().getField("taskScheduler").get(player);
        Function<Object, CompletionStage<T>> execute = ignored -> {
            try { return CompletableFuture.completedStage(action.get()); }
            catch (Throwable failure) { return CompletableFuture.failedStage(failure); }
        };
        return (CompletionStage<T>)call(scheduler, "scheduleRestore", execute);
    }

    private <T> CompletionStage<T> global(Supplier<T> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Bukkit.getGlobalRegionScheduler().execute(this.plugin, () -> {
            try { result.complete(action.get()); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        return result;
    }

    private static <T> T await(CompletionStage<T> stage) throws Exception { return stage.toCompletableFuture().get(90, TimeUnit.SECONDS); }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
