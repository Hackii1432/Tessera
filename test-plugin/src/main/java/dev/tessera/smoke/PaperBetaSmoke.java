package dev.tessera.smoke;

import io.papermc.paper.world.WorldUnloadOptions;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.util.TriState;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.SulfurCube;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Real region/world/entity persistence checks, separate from production plugins. */
final class PaperBetaSmoke implements Listener {
    private final JavaPlugin plugin;
    private final Map<Integer, UUID> zombies = new ConcurrentHashMap<>();
    private final Map<Integer, UUID> cubes = new ConcurrentHashMap<>();
    private final Map<Integer, SulfurCube> retireOnDrop = new ConcurrentHashMap<>();
    private final List<String> checks = new java.util.concurrent.CopyOnWriteArrayList<>();
    private World world;

    PaperBetaSmoke(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    void start() {
        Bukkit.getPluginManager().registerEvents(this, this.plugin);
        var key = new NamespacedKey("tessera_smoke", "paper_beta");
        Bukkit.getRuntimeWorldManager().createWorldAsync(WorldCreator.ofKey(key).type(WorldType.FLAT).generateStructures(false))
            .thenCompose(result -> {
                require(result.successful(), "runtime create: " + result);
                this.world = java.util.Objects.requireNonNull(result.world());
                return global(() -> this.world.setDifficulty(Difficulty.PEACEFUL));
            })
            .thenCompose(ignored -> CompletableFuture.allOf(prepareRegion(0), prepareRegion(128)))
            .thenCompose(ignored -> CompletableFuture.allOf(onRegion(0, 10, () -> verifyLive(0)), onRegion(128, 10, () -> verifyLive(128))))
            .thenCompose(ignored -> CompletableFuture.allOf(onRegion(0, 1, () -> this.world.getChunkAt(0, 0).removePluginChunkTicket(this.plugin)),
                onRegion(128, 1, () -> this.world.getChunkAt(128, 0).removePluginChunkTicket(this.plugin))))
            .thenCompose(ignored -> Bukkit.getRuntimeWorldManager().unloadWorldAsync(this.world, WorldUnloadOptions.defaults()))
            .thenCompose(result -> {
                require(result.successful(), "saved unload: " + result);
                return Bukkit.getRuntimeWorldManager().loadWorldAsync(key);
            })
            .thenCompose(result -> {
                require(result.successful(), "reload: " + result);
                this.world = java.util.Objects.requireNonNull(result.world());
                return CompletableFuture.allOf(reloadRegion(0), reloadRegion(128));
            })
            .toCompletableFuture().orTimeout(180, TimeUnit.SECONDS)
            .whenComplete((ignored, failure) -> {
                if (failure != null) {
                    this.plugin.getLogger().log(java.util.logging.Level.SEVERE, "PAPER_BETA_FAIL", failure);
                } else {
                    this.plugin.getLogger().info("PAPER_BETA_PASS " + this.checks);
                }
                Bukkit.getGlobalRegionScheduler().execute(this.plugin, Bukkit::shutdown);
            });
    }

    private CompletableFuture<Void> prepareRegion(int chunkX) {
        CompletableFuture<?>[] chunks = new CompletableFuture<?>[9];
        int index = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) chunks[index++] = this.world.getChunkAtAsync(chunkX + dx, dz, true);
        }
        return CompletableFuture.allOf(chunks).thenCompose(ignored -> onRegion(chunkX, 1, () -> {
            require(Bukkit.isOwnedByCurrentRegion(this.world, chunkX, 0), "region owner");
            this.world.getChunkAt(chunkX, 0).addPluginChunkTicket(this.plugin);
            require(this.world.isChunkGenerated(chunkX, 0), "generated chunk query");
            Location at = new Location(this.world, chunkX * 16 + 8, 70, 8);
            Zombie kept = this.world.spawn(at, Zombie.class, mob -> {
                mob.setAI(false);
                mob.setGravity(false);
                mob.setPersistent(true);
                mob.setDespawnInPeacefulOverride(TriState.FALSE);
            });
            require(kept.getDespawnInPeacefulOverride() == TriState.FALSE, "peaceful setter/getter");
            this.zombies.put(chunkX, kept.getUniqueId());
            Zombie removed = this.world.spawn(at.clone().add(2, 0, 0), Zombie.class, mob -> {
                mob.setAI(false);
                mob.setGravity(false);
                mob.setPersistent(true);
                mob.setDespawnInPeacefulOverride(TriState.TRUE);
            });
            removed.customName(net.kyori.adventure.text.Component.text("must-despawn"));
            SulfurCube cube = this.world.spawn(at.clone().add(0, 0, 3), SulfurCube.class, mob -> {
                mob.setAdult();
                mob.setAI(false);
                mob.setGravity(false);
                mob.setPersistent(true);
            });
            cube.setBaby();
            require(!cube.swallow(new ItemStack(Material.STONE)), "baby refuses swallow");
            require(cube.getEquipped().isEmpty(), "baby unchanged");
            cube.setAdult();
            ItemStack input = new ItemStack(Material.STONE, 64);
            require(cube.swallow(input), "adult swallow");
            require(input.getAmount() == 64 && cube.getEquipped().getAmount() == 1, "copied one item");
            require(!cube.swallow(input), "same item is no change");
            require(cube.swallow(new ItemStack(Material.COBBLESTONE)), "replace swallowed item");
            long drops = Arrays.stream(this.world.getChunkAt(chunkX, 0).getEntities()).filter(entity -> entity instanceof Item item && item.getItemStack().getType() == Material.STONE).count();
            require(drops == 1, "exactly one old item dropped");
            cube.setEquipped(new ItemStack(Material.DIAMOND, 3));
            ItemStack copy = cube.getEquipped();
            copy.setAmount(1);
            require(cube.getEquipped().getAmount() == 3, "getEquipped copy isolation");
            cube.setEquipped(ItemStack.empty());
            require(cube.getEquipped().isEmpty(), "clear equipment");
            cube.setEquipped(new ItemStack(Material.STONE));
            this.cubes.put(chunkX, cube.getUniqueId());

            SulfurCube retired = this.world.spawn(at.clone().add(3, 0, 3), SulfurCube.class, mob -> {
                mob.setAdult(); mob.setAI(false); mob.setGravity(false);
            });
            retired.setEquipped(new ItemStack(Material.STONE));
            this.retireOnDrop.put(chunkX, retired);
            require(!retired.swallow(new ItemStack(Material.COBBLESTONE)), "drop callback retirement aborts mutation");
            require(!retired.isValid(), "callback retired cube");
            this.retireOnDrop.remove(chunkX);

            Location tree = new Location(this.world, chunkX * 16 + 8, 65, 0);
            for (TreeType type : List.of(TreeType.POPLAR, TreeType.RED_POPLAR, TreeType.ORANGE_POPLAR, TreeType.YELLOW_POPLAR)) {
                // Delegate to the real generator but capture blocks instead of placing four overlapping trees.
                this.world.getBlockAt(tree.clone().add(0, -1, 0)).setType(Material.GRASS_BLOCK);
                java.util.concurrent.atomic.AtomicInteger blocks = new java.util.concurrent.atomic.AtomicInteger();
                boolean grown = this.world.generateTree(tree, new java.util.Random(123), type, state -> { blocks.incrementAndGet(); return false; });
                require(grown && blocks.get() > 0, "tree generator " + type);
            }
            this.checks.add("region " + chunkX + ": APIs, four tree types, drop retirement");
        }));
    }

    private void verifyLive(int chunkX) {
        Entity[] entities = this.world.getChunkAt(chunkX, 0).getEntities();
        require(Arrays.stream(entities).anyMatch(entity -> entity.getUniqueId().equals(this.zombies.get(chunkX))), "peaceful FALSE survives ticks");
        require(Arrays.stream(entities).filter(Zombie.class::isInstance).count() == 1, "peaceful TRUE despawns");
    }

    private CompletableFuture<Void> reloadRegion(int chunkX) {
        return this.world.getChunkAtAsync(chunkX, 0, true).thenCompose(ignored -> onRegion(chunkX, 1, () -> {
            Entity[] entities = this.world.getChunkAt(chunkX, 0).getEntities();
            Zombie zombie = (Zombie) Arrays.stream(entities).filter(entity -> entity.getUniqueId().equals(this.zombies.get(chunkX))).findFirst().orElseThrow();
            SulfurCube cube = (SulfurCube) Arrays.stream(entities).filter(entity -> entity.getUniqueId().equals(this.cubes.get(chunkX))).findFirst().orElseThrow();
            require(zombie.getDespawnInPeacefulOverride() == TriState.FALSE, "peaceful override NBT roundtrip");
            require(cube.getEquipped().getType() == Material.STONE, "sulfur equipment NBT roundtrip");
            require(Arrays.stream(entities).filter(Zombie.class::isInstance).count() == 1, "no duplicated persisted zombies");
            require(Arrays.stream(entities).filter(SulfurCube.class::isInstance).count() == 1, "no duplicated persisted cubes");
            this.checks.add("region " + chunkX + ": saved unload/reload, exact entity UUIDs and state");
        }));
    }

    @EventHandler
    public void entityDropping(EntityDropItemEvent event) {
        if (event.getEntity() instanceof SulfurCube cube && cube.getWorld() == this.world) {
            SulfurCube retire = this.retireOnDrop.get(cube.getLocation().getBlockX() >> 4);
            // This event runs before the new Item's tracking-status update lock.
            if (retire != null && retire.getUniqueId().equals(cube.getUniqueId())) retire.remove();
        }
    }

    private CompletableFuture<Void> global(Runnable task) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        Bukkit.getGlobalRegionScheduler().execute(this.plugin, () -> run(task, result));
        return result;
    }

    private CompletableFuture<Void> onRegion(int chunkX, long delay, Runnable task) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        Bukkit.getRegionScheduler().runDelayed(this.plugin, this.world, chunkX, 0, ignored -> run(task, result), delay);
        return result;
    }

    private static void run(Runnable task, CompletableFuture<Void> result) {
        try { task.run(); result.complete(null); } catch (Throwable failure) { result.completeExceptionally(failure); }
    }

    private static void require(boolean value, String check) {
        if (!value) throw new AssertionError(check);
    }
}
