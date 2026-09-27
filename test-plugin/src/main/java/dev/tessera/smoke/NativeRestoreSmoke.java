package dev.tessera.smoke;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Real native-path characterization. NOT acceptance of the unfinished public store transaction. */
final class NativeRestoreSmoke implements Listener {
    private final JavaPlugin plugin;
    private final List<String> checks = new ArrayList<>();
    private volatile UUID operation;
    private volatile boolean veto;
    private volatile boolean observedScope;
    private Object storeFence;
    private UUID storeOwner;

    NativeRestoreSmoke(JavaPlugin plugin) { this.plugin = plugin; }

    void start() {
        Bukkit.getPluginManager().registerEvents(this, this.plugin);
        this.plugin.getLogger().info("NATIVE_RESTORE_READY");
        Thread.ofVirtual().name("Native restore fixture").start(() -> {
            try {
                run();
                Files.createDirectories(Path.of("native-restore-evidence"));
                Files.writeString(Path.of("native-restore-evidence/checks.txt"), String.join("\n", this.checks));
                this.plugin.getLogger().info("NATIVE_RESTORE_COMPONENTS_PASS");
                // Runner captures socket counters before stopping this owned server.
            } catch (Throwable failure) {
                this.plugin.getLogger().log(java.util.logging.Level.SEVERE, "NATIVE_RESTORE_COMPONENTS_FAIL", failure);
                global(() -> { Bukkit.shutdown(); return null; });
            }
        });
    }

    @EventHandler
    public void teleport(PlayerTeleportEvent event) {
        UUID expected = this.operation;
        if (expected != null && Bukkit.getPlayerRestoreService().isRestoreTeleport(event, expected)) {
            require(Bukkit.isOwnedByCurrentRegion(event.getPlayer()), "restore event owner");
            require(!Bukkit.getPlayerRestoreService().isRestoreTeleport(event, UUID.randomUUID()), "operation scope separation");
            this.observedScope = true;
            if (this.veto) event.setCancelled(true);
        }
    }

    private void run() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
        List<Player> players;
        do {
            players = await(global(() -> List.copyOf(Bukkit.getOnlinePlayers())));
            if (players.size() == 2) break;
            Thread.sleep(100);
        } while (System.nanoTime() < deadline);
        require(players.size() == 2, "two real players connected");
        World target = await(await(global(() -> Bukkit.getRuntimeWorldManager().createWorldAsync(
            new WorldCreator("restore_target_flat").type(WorldType.FLAT)
        )))).world();
        require(target != null, "prepared flat target world");
        World nether = await(global(() -> Bukkit.getWorlds().stream().filter(w -> w.getEnvironment() == World.Environment.NETHER).findFirst().orElseThrow()));
        Player first = players.get(0), second = players.get(1);
        require(await(await(owner(first, () -> first.teleportAsync(new Location(first.getWorld(), 4096.5, 90, 4096.5))))), "first region setup");
        require(await(await(owner(second, () -> second.teleportAsync(new Location(nether, 32.5, 90, 32.5))))), "second dimension setup");
        await(owner(first, () -> { require(!Bukkit.isOwnedByCurrentRegion(second), "separate native owners"); return null; }));
        await(owner(first, () -> { first.setTotalExperience(101); return null; }));
        await(owner(second, () -> { second.setTotalExperience(202); return null; }));
        saveSnapshot("before-restore", players, List.of(101, 202));
        await(global(() -> { Bukkit.getServerTickManager().setFrozen(true); return null; }));
        Object fence = type("org.bukkit.craftbukkit.world.PlayerStoreFence").getField("INSTANCE").get(null);
        UUID fenceOwner = UUID.randomUUID();
        this.storeFence = fence;
        this.storeOwner = fenceOwner;
        await((CompletionStage<?>)call(fence, "close", fenceOwner));
        try {
            var busy = await(await(global(() -> Bukkit.getRuntimeWorldManager().snapshotWorldsAsync(
                List.copyOf(Bukkit.getWorlds()), Path.of("native-restore-evidence", "blocked-snapshot")))));
            require(busy.status() == io.papermc.paper.world.WorldSnapshotResult.Status.SOURCE_BUSY, "snapshot respects closed player-store fence");
            for (Player player : players) {
                verify(player, player == first ? new Location(target, 8.5, 20, 8.5) : new Location(nether, 4128.5, 90, 32.5));
            }
            this.checks.add("Real connected players restored repeatedly across separate regions/dimensions while game ticks were frozen");
            require(Bukkit.getPlayerRestoreService().contractVersion() == 1, "server advertises the accepted native transaction contract");
            this.checks.add("Component fixture only; public contract 1 is separately tested by the transaction/race/recovery fixtures");
        } finally {
            call(fence, "open", fenceOwner);
            await(global(() -> { Bukkit.getServerTickManager().setFrozen(false); return null; }));
        }
        saveSnapshot("after-restore", players, List.of(137, 137));
        this.checks.add("Actual runtime-world snapshots before and after native replacements, compressed NBT XP values and both JSON stores verified; clients stayed connected");
    }

    private void saveSnapshot(String label, List<Player> players, List<Integer> expectedXp) throws Exception {
        Path path = Path.of("native-restore-evidence", label).toAbsolutePath();
        var result = await(await(global(() -> Bukkit.getRuntimeWorldManager().snapshotWorldsAsync(List.copyOf(Bukkit.getWorlds()), path))));
        require(result.successful(), "runtime snapshot: " + result);
        for (int i = 0; i < players.size(); i++) {
            UUID id = players.get(i).getUniqueId();
            Object quota = call(type("net.minecraft.nbt.NbtAccounter"), "create", 16L * 1024 * 1024);
            Object saved = call(type("net.minecraft.nbt.NbtIo"), "readCompressed", path.resolve("players/data/" + id + ".dat"), quota);
            require(((java.util.Optional<?>)call(saved, "getInt", "XpTotal")).orElseThrow().equals(expectedXp.get(i)), "fresh on-disk snapshot XP");
            require(Files.isRegularFile(path.resolve("players/stats/" + id + ".json")), "snapshot statistics present");
            require(Files.isRegularFile(path.resolve("players/advancements/" + id + ".json")), "snapshot advancements present");
        }
    }

    private void verify(Player player, Location target) throws Exception {
        NamespacedKey marker = new NamespacedKey(this.plugin, "saved");
        NamespacedKey extra = new NamespacedKey(this.plugin, "removed");
        NamespacedKey savedRecipe = NamespacedKey.minecraft("oak_planks");
        NamespacedKey extraRecipe = NamespacedKey.minecraft("crafting_table");
        Object[] baseline = await(owner(player, () -> {
            player.setGameMode(GameMode.CREATIVE);
            player.setFlying(false);
            player.getInventory().clear();
            player.getEnderChest().clear();
            player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 7));
            player.getEnderChest().setItem(0, new ItemStack(Material.EMERALD, 5));
            player.setTotalExperience(137);
            player.setLevel(8);
            player.setExp(0.25f);
            player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20);
            player.setHealth(14);
            player.setFoodLevel(17);
            player.getPersistentDataContainer().set(marker, PersistentDataType.STRING, "original");
            player.getPersistentDataContainer().remove(extra);
            player.setStatistic(Statistic.JUMP, 23);
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.NIGHT_VISION, 10000, 0));
            player.undiscoverRecipe(extraRecipe);
            player.discoverRecipe(savedRecipe);
            require(player.hasDiscoveredRecipe(savedRecipe) && !player.hasDiscoveredRecipe(extraRecipe), "recipe baseline is distinct");
            player.getWorld().spawn(player.getLocation().add(0, 4, 0), org.bukkit.entity.EnderPearl.class, pearl -> {
                pearl.setGravity(false);
                pearl.setVelocity(new org.bukkit.util.Vector());
                pearl.setShooter(player);
            });
            Object handle = call(player, "getHandle");
            Object server = call(Bukkit.getServer(), "getServer");
            Object reporter = type("net.minecraft.util.ProblemReporter").getField("DISCARDING").get(null);
            Object output = call(type("net.minecraft.world.level.storage.TagValueOutput"), "createWithContext", reporter, call(server, "registryAccess"));
            call(handle, "saveWithoutId", output);
            Object tag = call(output, "buildResult");
            call(type("net.minecraft.nbt.NbtUtils"), "addCurrentDataVersion", tag);
            call(tag, "putLong", "WorldUUIDMost", target.getWorld().getUID().getMostSignificantBits());
            call(tag, "putLong", "WorldUUIDLeast", target.getWorld().getUID().getLeastSignificantBits());
            call(tag, "putString", "Dimension", target.getWorld().getKey().toString());
            for (Object pearl : (Iterable<?>)call(tag, "getListOrEmpty", "ender_pearls")) {
                call(pearl, "putString", "ender_pearl_dimension", target.getWorld().getKey().toString());
            }
            return new Object[]{server, call(handle, "getGameProfile"), call(handle, "clientInformation"), tag,
                player.getInventory(), player.getEnderChest(), handle};
        }));
        // Force the destination to tick before scheduling preflight on that region.
        await(target.getWorld().getChunkAtAsync(target));
        int version = (int)((java.util.Optional<?>)call(baseline[3], "getInt", "DataVersion")).orElseThrow();
        Object stats = json("{\"DataVersion\":" + version + ",\"stats\":{\"minecraft:custom\":{\"minecraft:jump\":23}}}");
        Object advancements = json("{}");
        String sourceBefore = baseline[3].toString();
        Object prepared = await(region(target, () -> call(type("org.bukkit.craftbukkit.world.NativePlayerRestoreState"), "prepare",
            baseline[0], baseline[1], baseline[2], target, baseline[3], stats, advancements)));
        require(sourceBefore.equals(baseline[3].toString()), "preflight leaves source NBT unchanged");
        for (int cycle = 0; cycle < 2; ++cycle) {
            await(owner(player, () -> {
                player.getInventory().setItem(0, new ItemStack(Material.DIRT, 64));
                player.getEnderChest().setItem(1, new ItemStack(Material.GOLD_INGOT, 11));
                player.setTotalExperience(999);
                player.setLevel(24);
                player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(40);
                player.setHealth(32);
                player.setFoodLevel(4);
                player.getPersistentDataContainer().set(marker, PersistentDataType.STRING, "mutated");
                player.getPersistentDataContainer().set(extra, PersistentDataType.STRING, "must disappear");
                player.setStatistic(Statistic.JUMP, 98);
                player.setStatistic(Statistic.DROP_COUNT, 33);
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, 10000, 1));
                player.setGameMode(GameMode.SPECTATOR);
                player.discoverRecipe(extraRecipe);
                return null;
            }));
            this.operation = UUID.randomUUID();
            this.observedScope = false;
            await((CompletionStage<?>)call(prepared, "restore", baseline[6], this.operation));
            require(this.observedScope, "operation-scoped native teleport event");
            await(owner(player, () -> {
                require(player.getWorld().equals(target.getWorld()), "target world preserved");
                require(player.getLocation().distanceSquared(target) < 0.0001, "target position and client acknowledgement");
                require(player.getInventory() == baseline[4] && player.getEnderChest() == baseline[5], "inventory wrappers preserved");
                require(call(player, "getHandle") == baseline[6], "native player and connection preserved");
                require(player.getInventory().getItem(0).getType() == Material.DIAMOND && player.getInventory().getItem(0).getAmount() == 7, "inventory replaced");
                require(player.getEnderChest().getItem(1) == null, "removed ender-chest slot cleared");
                require(player.getTotalExperience() == 137 && player.getLevel() == 8, "experience replaced");
                require(player.getHealth() == 14 && player.getFoodLevel() == 17, "health and hunger replaced");
                require(player.getAttribute(Attribute.MAX_HEALTH).getBaseValue() == 20, "attribute replaced");
                require(player.getGameMode() == GameMode.CREATIVE && !player.isFlying(), "game mode and abilities replaced");
                require(player.hasPotionEffect(org.bukkit.potion.PotionEffectType.NIGHT_VISION) && !player.hasPotionEffect(org.bukkit.potion.PotionEffectType.SPEED), "effects replaced");
                require("original".equals(player.getPersistentDataContainer().get(marker, PersistentDataType.STRING)), "PDC value restored");
                require(!player.getPersistentDataContainer().has(extra), "removed PDC cleared");
                require(player.hasDiscoveredRecipe(savedRecipe) && !player.hasDiscoveredRecipe(extraRecipe), "recipes replaced: saved=" + player.hasDiscoveredRecipe(savedRecipe) + ", extra=" + player.hasDiscoveredRecipe(extraRecipe));
                require(player.getStatistic(Statistic.JUMP) == 23 && player.getStatistic(Statistic.DROP_COUNT) == 0, "stats replaced including removed zero: jump=" + player.getStatistic(Statistic.JUMP) + ", drops=" + player.getStatistic(Statistic.DROP_COUNT));
                require(((java.util.Set<?>)call(baseline[6], "getEnderPearls")).size() == 1, "one owned pearl, child task completed");
                return null;
            }));
        }
        this.veto = true;
        this.operation = UUID.randomUUID();
        try {
            await((CompletionStage<?>)call(prepared, "restore", baseline[6], this.operation));
            throw new AssertionError("Restore ignored teleport veto");
        } catch (java.util.concurrent.ExecutionException expected) {
            require(expected.toString().contains("vetoed"), "explicit veto failure");
        } finally {
            this.veto = false;
        }
        Object fresh = await(region(target, () -> call(type("org.bukkit.craftbukkit.world.NativePlayerRestoreState"), "prepare",
            baseline[0], baseline[1], baseline[2], target, null, json("{}"), json("{}"))));
        this.operation = UUID.randomUUID();
        await((CompletionStage<?>)call(fresh, "restore", baseline[6], this.operation));
        await(owner(player, () -> {
            require(player.getInventory().isEmpty() && player.getEnderChest().isEmpty(), "fresh inventory defaults");
            require(player.getTotalExperience() == 0 && player.getLevel() == 0, "fresh XP defaults");
            require(player.getHealth() == 20 && player.getFoodLevel() == 20, "fresh health/food defaults");
            require(player.getPersistentDataContainer().isEmpty(), "fresh PDC defaults");
            require(player.getActivePotionEffects().isEmpty(), "fresh effect defaults");
            require(player.getDiscoveredRecipes().isEmpty(), "fresh recipes default");
            require(player.getStatistic(Statistic.JUMP) == 0, "fresh stats default");
            require(((java.util.Set<?>)call(baseline[6], "getEnderPearls")).isEmpty(), "fresh state removes previous owned pearls");
            return null;
        }));
        this.operation = UUID.randomUUID();
        await((CompletionStage<?>)call(prepared, "restore", baseline[6], this.operation));
        await(owner(player, () -> {
            require(player.getInventory().getItem(0).getType() == Material.DIAMOND && player.getStatistic(Statistic.JUMP) == 23, "repeat original restore after fresh state");
            // A privileged native snapshot must reach all three actual writers
            // while ordinary Player.saveData remains fenced.
            Object playerList = call(baseline[0], "getPlayerList");
            try (AutoCloseable access = (AutoCloseable)call(this.storeFence, "writeOwned", this.storeOwner);
                 AutoCloseable scope = (AutoCloseable)call(this.storeFence, "enter", access)) {
                call(playerList, "tessera$savePlayerSnapshot", baseline[6]);
            }
            Path root = ((java.io.File)call(playerList.getClass().getField("playerIo").get(playerList), "getPlayerDir")).toPath().getParent();
            Path[] stores = {root.resolve("data/" + player.getUniqueId() + ".dat"), root.resolve("stats/" + player.getUniqueId() + ".json"), root.resolve("advancements/" + player.getUniqueId() + ".json")};
            byte[][] saved = new byte[stores.length][];
            for (int i = 0; i < stores.length; i++) saved[i] = Files.readAllBytes(stores[i]);
            player.setLevel(99);
            player.setStatistic(Statistic.JUMP, 909);
            player.saveData();
            for (int i = 0; i < stores.length; i++) require(java.util.Arrays.equals(saved[i], Files.readAllBytes(stores[i])), "native writer remained fenced: " + stores[i]);
            return null;
        }));
        this.operation = UUID.randomUUID();
        await((CompletionStage<?>)call(prepared, "restore", baseline[6], this.operation));
        this.checks.add(player.getName() + ": two replacements, exact inventory/ender chest/XP/health/hunger/attributes/PDC/recipes/stats, stable wrappers and teleport veto");
        this.checks.add(player.getName() + ": fresh defaults, original state restored again, actual three-store snapshot writer and fenced saveData verified");
        this.checks.add(player.getName() + ": owned no-gravity ender pearl restored on destination owner, replaced rather than duplicated and removed for fresh state");
    }

    private static Object json(String value) throws Exception { return call(type("com.google.gson.JsonParser"), "parseString", value); }
    static Class<?> type(String name) throws Exception { return Class.forName(name, true, Bukkit.class.getClassLoader()); }
    static Object call(Object owner, String name, Object... args) throws Exception {
        Class<?> type = owner instanceof Class<?> clazz ? clazz : owner.getClass();
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length || method.isBridge()) continue;
            Class<?>[] parameters = method.getParameterTypes();
            boolean match = true;
            for (int i = 0; i < args.length; ++i) {
                Class<?> parameter = parameters[i];
                if (parameter.isPrimitive()) parameter = Map.of(int.class, Integer.class, long.class, Long.class, boolean.class, Boolean.class,
                    float.class, Float.class, double.class, Double.class, byte.class, Byte.class, short.class, Short.class).get(parameter);
                if (args[i] != null && !parameter.isInstance(args[i])) { match = false; break; }
            }
            if (match) return method.invoke(owner instanceof Class<?> ? null : owner, args);
        }
        throw new NoSuchMethodException(type.getName() + "#" + name);
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
            try { result.complete(action.get()); } catch (Throwable error) { result.completeExceptionally(error); }
        });
        return result;
    }
    private <T> CompletionStage<T> region(Location location, Action<T> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Bukkit.getRegionScheduler().execute(this.plugin, location, () -> {
            try { result.complete(action.get()); } catch (Throwable error) { result.completeExceptionally(error); }
        });
        return result;
    }
    private static <T> T await(CompletionStage<T> stage) throws Exception { return stage.toCompletableFuture().get(45, TimeUnit.SECONDS); }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
