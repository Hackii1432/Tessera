package dev.tessera.smoke;

import io.papermc.paper.world.WorldUnloadOptions;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.ShelfBlockEntity;
import org.bukkit.Bukkit;
import org.bukkit.Instrument;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.BlockType;
import org.bukkit.block.Chest;
import org.bukkit.block.Shelf;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.plugin.java.JavaPlugin;

/** Tests the final JAR on real owners and real saved chunks, not just codecs in isolation. */
final class PaperOctoberSmoke {
    private final JavaPlugin plugin;
    private final List<String> checks = new CopyOnWriteArrayList<>();
    private final Set<Object> owners = ConcurrentHashMap.newKeySet();
    private final Map<Integer, List<UUID>> spawned = new ConcurrentHashMap<>();
    private World world;

    PaperOctoberSmoke(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    void start() {
        this.plugin.getLogger().info("OCTOBER_READY_CLIENTS");
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(this.plugin, task -> {
            if (Bukkit.getOnlinePlayers().size() < 2) return;
            task.cancel();
            begin();
        }, 1, 10);
    }

    private void begin() {
        NamespacedKey key = new NamespacedKey("tessera_smoke", "october");
        Bukkit.getRuntimeWorldManager().createWorldAsync(WorldCreator.ofKey(key).type(WorldType.FLAT).generateStructures(false))
            .thenCompose(result -> {
                require(result.successful(), "create: " + result);
                this.world = java.util.Objects.requireNonNull(result.world());
                return CompletableFuture.allOf(prepare(0), prepare(128));
            })
            .thenCompose(ignored -> CompletableFuture.allOf(onRegion(0, this::verifySpawns), onRegion(128, this::verifySpawns)))
            .thenCompose(ignored -> {
                require(this.owners.size() == 2, "two distinct region owners");
                this.checks.add("parallel-regions: two distinct owners");
                return CompletableFuture.allOf(onRegion(0, x -> this.world.getChunkAt(x, 0).removePluginChunkTicket(this.plugin)),
                    onRegion(128, x -> this.world.getChunkAt(x, 0).removePluginChunkTicket(this.plugin)));
            })
            .thenCompose(ignored -> Bukkit.getRuntimeWorldManager().unloadWorldAsync(this.world, WorldUnloadOptions.defaults()))
            .thenCompose(result -> {
                require(result.successful(), "saved unload: " + result);
                return Bukkit.getRuntimeWorldManager().loadWorldAsync(key);
            })
            .thenCompose(result -> {
                require(result.successful(), "reload: " + result);
                this.world = java.util.Objects.requireNonNull(result.world());
                return CompletableFuture.allOf(reload(0), reload(128));
            })
            .toCompletableFuture().orTimeout(180, TimeUnit.SECONDS)
            .whenComplete((ignored, failure) -> {
                if (failure != null) {
                    this.plugin.getLogger().log(java.util.logging.Level.SEVERE, "OCTOBER_FAIL", failure);
                } else {
                    this.checks.add("clients: two connected protocol clients");
                    try {
                        Files.writeString(Path.of("october-checks.txt"), String.join("\n", this.checks));
                        this.plugin.getLogger().info("OCTOBER_PASS " + this.checks);
                    } catch (Exception error) {
                        this.plugin.getLogger().log(java.util.logging.Level.SEVERE, "OCTOBER_FAIL", error);
                    }
                }
                Bukkit.getGlobalRegionScheduler().execute(this.plugin, Bukkit::shutdown);
            });
    }

    private CompletableFuture<Void> prepare(int x) {
        CompletableFuture<?>[] chunks = new CompletableFuture<?>[9];
        int i = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) chunks[i++] = this.world.getChunkAtAsync(x + dx, dz, true);
        }
        return CompletableFuture.allOf(chunks).thenCompose(ignored -> onRegion(x, region -> {
            require(Bukkit.isOwnedByCurrentRegion(this.world, x, 0), "correct region owner");
            this.owners.add(io.papermc.paper.threadedregions.TickRegionScheduler.getCurrentRegion());
            this.world.getChunkAt(x, 0).addPluginChunkTicket(this.plugin);
            var chestBlock = this.world.getBlockAt(x * 16 + 8, 70, 8);
            chestBlock.setType(Material.CHEST);
            Chest snapshot = (Chest) chestBlock.getState(true);
            Chest live = (Chest) chestBlock.getState(false);
            require(snapshot.isSnapshot() && !live.isSnapshot(), "explicit snapshot flag");
            live.getBlockInventory().setItem(0, new ItemStack(Material.DIAMOND, 7));
            require(snapshot.getSnapshotInventory().getItem(0) == null, "snapshot isolated from live inventory");
            require(((Chest) chestBlock.getState(false)).getBlockInventory().getItem(0).getAmount() == 7, "live state observes mutation");
            require(BlockType.GOLD_BLOCK.getInstrument() == Instrument.BELL, "BlockType instrument");
            this.checks.add("block-snapshots: " + x + " explicit live/snapshot plus instrument");

            var armorStand = this.world.spawn(new Location(this.world, x * 16 + 6, 72, 8), org.bukkit.entity.ArmorStand.class);
            var nativeStand = ((org.bukkit.craftbukkit.entity.CraftArmorStand) armorStand).getHandle();
            var nativeLevel = ((CraftWorld) this.world).getHandle();
            var damage = nativeLevel.damageSources().genericKill();
            nativeStand.kill(nativeLevel, damage, false);
            require(nativeStand.isRemoved() && nativeStand.getLastDamageSource() == damage, "death damage uses region-local timestamp");
            this.checks.add("death-attribution: " + x + " armor stand source remains available after death");

            var shelfBlock = this.world.getBlockAt(x * 16 + 10, 70, 8);
            shelfBlock.setType(Material.OAK_SHELF);
            ((Shelf) shelfBlock.getState(false)).getInventory().setItem(0, bundle());
            ShelfBlockEntity shelf = (ShelfBlockEntity) ((CraftWorld) this.world).getHandle().getBlockEntity(new BlockPos(x * 16 + 10, 70, 8));
            var registries = ((CraftWorld) this.world).getHandle().registryAccess();
            if (x == 128) shelf.saveWithFullMetadata(registries); // Exercise disk-first as well as network-first cache order.
            Tag network = shelf.getUpdateTag(registries);
            require(network.toString().contains("minecraft:paper"), "network sanitization really executed");
            Tag persisted = shelf.saveWithFullMetadata(registries);
            require(persisted.toString().contains("minecraft:diamond"), "disk encoding retains real contents after network encoding");
            require(!persisted.equals(network), "disk and network data are distinct");
            this.checks.add("shelf-network-disk: " + x + " network sanitized, persistent nested bundle intact");

            Location at = new Location(this.world, x * 16 + 8, 72, 8);
            Chicken existing = this.world.spawn(at, Chicken.class, chicken -> { chicken.setAI(false); chicken.setGravity(false); });
            net.minecraft.world.entity.animal.pig.Pig natural = net.minecraft.world.entity.EntityTypes.PIG.create(
                ((CraftWorld) this.world).getHandle(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            require(natural != null, "create unregistered natural passenger");
            natural.snapTo(at.getX(), at.getY(), at.getZ(), 0, 0);
            natural.setNoAi(true);
            natural.setNoGravity(true);
            Zombie root = this.world.spawn(at, Zombie.class, zombie -> {
                zombie.setAI(false); zombie.setGravity(false); zombie.setPersistent(true); zombie.setInvulnerable(true);
                require(zombie.addPassenger(existing), "existing passenger in pre-spawn consumer");
                require(existing.addPassenger(natural.getBukkitEntity()), "unregistered natural nested passenger");
            });
            this.spawned.put(x, List.of(root.getUniqueId(), existing.getUniqueId(), natural.getUUID()));
            require(root.isValid() && existing.isValid() && natural.valid, "valid passenger must not prevent root and new passenger spawn");
            this.checks.add("passenger-spawn: " + x + " existing and unregistered passengers plus root valid");
        }));
    }

    private void verifySpawns(int x) {
        for (UUID id : this.spawned.get(x)) {
            require(Arrays.stream(this.world.getChunkAt(x, 0).getEntities()).filter(e -> e.getUniqueId().equals(id)).count() == 1,
                "exactly one tracked entity " + id);
        }
    }

    private CompletableFuture<Void> reload(int x) {
        return this.world.getChunkAtAsync(x, 0, true).thenCompose(ignored -> onRegion(x, region -> {
            ItemStack loaded = ((Shelf) this.world.getBlockAt(x * 16 + 10, 70, 8).getState(false)).getInventory().getItem(0);
            require(bundle().equals(loaded), "exact nested bundle contents after actual chunk persistence");
            verifySpawns(x);
            this.checks.add("shelf-reload: " + x + " exact nested contents and no entity duplication");
        }));
    }

    private static ItemStack bundle() {
        ItemStack inner = new ItemStack(Material.BUNDLE);
        BundleMeta innerMeta = (BundleMeta) inner.getItemMeta();
        innerMeta.setItems(List.of(new ItemStack(Material.EMERALD, 3)));
        inner.setItemMeta(innerMeta);
        ItemStack outer = new ItemStack(Material.BUNDLE);
        BundleMeta meta = (BundleMeta) outer.getItemMeta();
        meta.setItems(List.of(new ItemStack(Material.DIAMOND, 5), inner));
        outer.setItemMeta(meta);
        return outer;
    }

    private CompletableFuture<Void> onRegion(int x, java.util.function.IntConsumer action) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        Bukkit.getRegionScheduler().runDelayed(this.plugin, this.world, x, 0, task -> {
            try { action.accept(x); result.complete(null); } catch (Throwable failure) { result.completeExceptionally(failure); }
        }, 1);
        return result;
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
