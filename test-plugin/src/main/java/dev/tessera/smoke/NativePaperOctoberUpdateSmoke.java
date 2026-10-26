package dev.tessera.smoke;

import io.papermc.paper.persistence.PersistentDataKey;
import io.papermc.paper.threadedregions.TickRegionScheduler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.key.Key;
import net.minecraft.world.level.block.Block;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.entity.SulfurCube;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Final-JAR native owners, real NORMAL/Nether generation, freeze and connected clients. */
final class NativePaperOctoberUpdateSmoke implements Listener {
    private final JavaPlugin plugin;
    private final List<String> checks = new CopyOnWriteArrayList<>();
    private final AtomicInteger cancelledExplosions = new AtomicInteger();
    private final AtomicInteger actualExplosions = new AtomicInteger();
    private volatile SulfurCube cube;
    private volatile String cubeAction = "cancel";
    private volatile CompletableFuture<Void> cubeCallback;
    private volatile CompletableFuture<Void> destinationPrime;
    private volatile CompletableFuture<Boolean> cubeTransfer;
    private volatile int callbackTickCount;
    private World normal;
    private World mirror;
    private World nether;

    NativePaperOctoberUpdateSmoke(JavaPlugin plugin) { this.plugin = plugin; }

    void start() {
        Bukkit.getPluginManager().registerEvents(this, this.plugin);
        this.plugin.getLogger().info("UPDATE_READY_CLIENTS");
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(this.plugin, task -> {
            if (Bukkit.getOnlinePlayers().size() < 2) return;
            task.cancel();
            Bukkit.getAsyncScheduler().runNow(this.plugin, ignored -> run());
        }, 1, 10);
    }

    private void run() {
        try {
            Player one = Bukkit.getPlayerExact("UpdateAlpha");
            Player two = Bukkit.getPlayerExact("UpdateBeta");
            require(one != null && two != null, "clients connected");
            global(() -> { server().tickRateManager().setFrozen(true); return null; }).get(10, TimeUnit.SECONDS);
            this.normal = world("update_normal", World.Environment.NORMAL, 78219);
            this.mirror = world("update_mirror", World.Environment.NORMAL, 78219);
            this.nether = world("update_nether", World.Environment.NETHER, 88117);
            CompletableFuture.allOf(prepare(this.normal, 0), prepare(this.normal, 128), prepare(this.mirror, 0),
                prepare(this.mirror, 128), prepare(this.nether, 0)).get(90, TimeUnit.SECONDS);
            for (int x : new int[] {0, 128}) {
                long hash = onRegion(this.normal, x, () -> hash(this.normal, x)).get(15, TimeUnit.SECONDS);
                long other = onRegion(this.mirror, x, () -> hash(this.mirror, x)).get(15, TimeUnit.SECONDS);
                require(hash == other, "deterministic complete block palettes and biomes in region " + x);
            }
            global(() -> { server().tickRateManager().setFrozen(false); return null; }).get(10, TimeUnit.SECONDS);
            onPlayer(one, () -> one.teleportAsync(new Location(this.normal, 8.5, 200, 8.5))).get(10, TimeUnit.SECONDS).get(30, TimeUnit.SECONDS);
            onPlayer(two, () -> two.teleportAsync(new Location(this.normal, 2056.5, 200, 8.5))).get(10, TimeUnit.SECONDS).get(30, TimeUnit.SECONDS);
            Object first = onPlayer(one, TickRegionScheduler::getCurrentRegion).get(10, TimeUnit.SECONDS);
            Object second = onPlayer(two, TickRegionScheduler::getCurrentRegion).get(10, TimeUnit.SECONDS);
            require(first != second, "two distinct native player regions");
            CompletableFuture.allOf(onPlayer(one, () -> { playerApis(one); return null; }),
                onPlayer(two, () -> { playerApis(two); return null; })).get(15, TimeUnit.SECONDS);
            testLatestApis(one, two);
            onRegion(this.normal, 0, this::spawnExplosiveCube).get(10, TimeUnit.SECONDS);
            waitCubeReady(this.normal);
            onRegion(this.normal, 0, () -> {
                require(this.cube.canExplode(), "cube explosive equipment ready");
                require(this.cube.ignite(), "cube ignition accepted");
                this.cube.setFuseTicks(1);
                return null;
            }).get(10, TimeUnit.SECONDS);
            Thread.sleep(800); // Isolated async fixture worker, never a tick thread.
            onRegion(this.normal, 0, () -> {
                require(this.cancelledExplosions.get() == 1 && this.cube.isValid() && !this.cube.isInvulnerable(),
                    "cancelled SulfurCube explosion restored damageability");
                this.cube.remove();
                return null;
            }).get(10, TimeUnit.SECONDS);

            testCubeCallbackOwnership();

            NamespacedKey a = new NamespacedKey("tessera_smoke", "update_a");
            NamespacedKey b = new NamespacedKey("tessera_smoke", "update_b");
            global(() -> {
                require(Bukkit.addRecipes(List.of(recipe(a), recipe(b))), "batch recipes published");
                require(Bukkit.hasRecipe(a) && Bukkit.hasRecipe(b), "batch recipes queryable");
                return null;
            }).get(10, TimeUnit.SECONDS);
            for (Player player : List.of(one, two)) onPlayer(player, () -> {
                require(player.discoverRecipe(a) && player.discoverRecipe(b), "player recipe discovery on owner");
                try { Bukkit.addRecipes(List.of(recipe(new NamespacedKey("tessera_smoke", "wrong_owner"))));
                    throw new AssertionError("region writer accepted");
                } catch (IllegalStateException expected) { require(true, "recipe region writer rejected without mutation"); }
                return null;
            }).get(10, TimeUnit.SECONDS);
            global(() -> { server().tickRateManager().setFrozen(true); return null; }).get(10, TimeUnit.SECONDS);
            global(() -> {
                require(Bukkit.removeRecipes(List.of(a)), "batch remove during tick freeze");
                require(!Bukkit.hasRecipe(a) && Bukkit.hasRecipe(b), "catalog removal published during freeze");
                var duplicate = new NamespacedKey("tessera_smoke", "duplicate_batch");
                try { Bukkit.addRecipes(List.of(recipe(duplicate), recipe(duplicate))); throw new AssertionError("duplicate accepted"); }
                catch (IllegalStateException expected) { require(!Bukkit.hasRecipe(duplicate), "duplicate batch left no partial recipe"); }
                Bukkit.getServer().updateResources();
                return null;
            }).get(10, TimeUnit.SECONDS);
            for (Player player : List.of(one, two)) onPlayer(player, () -> {
                require(server().tickRateManager().isFrozen() && player.isOnline(), "native owner resource work ran during freeze");
                player.postEffects().update();
                return null;
            }).get(10, TimeUnit.SECONDS);
            onPlayer(two, () -> two.teleportAsync(new Location(this.nether, 8.5, 200, 8.5))).get(10, TimeUnit.SECONDS).get(30, TimeUnit.SECONDS);
            global(() -> { Bukkit.getServer().updateRecipes(); return null; }).get(10, TimeUnit.SECONDS);
            onPlayer(two, () -> { require(two.getWorld() == this.nether, "recipe refresh followed a dimension transfer"); playerApis(two); return null; }).get(10, TimeUnit.SECONDS);
            global(() -> { server().tickRateManager().setFrozen(false); return null; }).get(10, TimeUnit.SECONDS);
            testMetadataUnload();
            Thread.sleep(1000); // Let connected protocol clients observe final reset packets.
            require(Bukkit.getOnlinePlayers().size() == 2, "clients remained connected throughout native update");
            Files.writeString(Path.of("update-checks.txt"), String.join("\n", this.checks));
            this.plugin.getLogger().info("UPDATE_PASS " + this.checks.size() + " checks");
        } catch (Throwable failure) {
            this.plugin.getLogger().log(java.util.logging.Level.SEVERE, "UPDATE_FAIL", failure);
        } finally {
            Bukkit.getGlobalRegionScheduler().execute(this.plugin, () -> { server().tickRateManager().setFrozen(false); Bukkit.shutdown(); });
        }
    }

    @EventHandler
    public void emptySulfurBucket(org.bukkit.event.player.PlayerBucketEmptyEvent event) {
        if (event.getBucket() != Material.SULFUR_CUBE_BUCKET || event.getPlayer().getWorld() != this.normal) return;
        try (var nested = io.papermc.paper.event.BucketResultScope.openPlacement()) {
            nested.setResult(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLD_INGOT));
            nested.completePlacement(true);
        }
        event.setItemStack(new ItemStack(Material.EMERALD));
    }

    @EventHandler
    public void prepareHopper(io.papermc.paper.event.inventory.PrepareHopperTransferEvent event) {
        if (event.getSource().getLocation() == null || event.getSource().getLocation().getWorld() != this.normal) return;
        for (int slot = 0; slot < event.getSource().getSize(); ++slot) {
            ItemStack item = event.getSource().getItem(slot);
            if (item != null && item.getType() == Material.DIRT) event.setSlotAllowed(slot, false);
        }
    }

    private void testLatestApis(Player one, Player two) throws Exception {
        require(this.normal.getChunkGenerationStateAsync(40000, 40000).get(10, TimeUnit.SECONDS)
            == io.papermc.paper.world.chunk.ChunkGenerationState.NOT_GENERATED, "unloaded metadata query without generation");
        require(!this.normal.isChunkLoaded(40000, 40000), "metadata query did not load chunk");
        CompletableFuture.allOf(onPlayer(one, () -> { latestOwnerApis(one); return null; }),
            onPlayer(two, () -> { latestOwnerApis(two); return null; })).get(20, TimeUnit.SECONDS);
        final var futures = new java.util.ArrayList<CompletableFuture<io.papermc.paper.threadedregions.scheduler.ScheduledTask>>();
        var globalTask = new CompletableFuture<io.papermc.paper.threadedregions.scheduler.ScheduledTask>();
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(this.plugin, task -> { task.cancel(); globalTask.complete(task); }, 1, 1);
        futures.add(globalTask);
        var regionTask = new CompletableFuture<io.papermc.paper.threadedregions.scheduler.ScheduledTask>();
        Bukkit.getRegionScheduler().runAtFixedRate(this.plugin, this.normal, 0, 0, task -> { task.cancel(); regionTask.complete(task); }, 1, 1);
        futures.add(regionTask);
        var entityTask = new CompletableFuture<io.papermc.paper.threadedregions.scheduler.ScheduledTask>();
        one.getScheduler().runAtFixedRate(this.plugin, task -> { task.cancel(); entityTask.complete(task); },
            () -> entityTask.completeExceptionally(new AssertionError("client retired")), 1, 1);
        futures.add(entityTask);
        var asyncTask = new CompletableFuture<io.papermc.paper.threadedregions.scheduler.ScheduledTask>();
        Bukkit.getAsyncScheduler().runAtFixedRate(this.plugin, task -> { task.cancel(); asyncTask.complete(task); }, 1, 1, TimeUnit.MILLISECONDS);
        futures.add(asyncTask);
        for (int i = 0; i < futures.size(); ++i) {
            final var scheduled = futures.get(i).get(10, TimeUnit.SECONDS);
            final long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (scheduled.getExecutionState() == io.papermc.paper.threadedregions.scheduler.ScheduledTask.ExecutionState.CANCELLED_RUNNING
                && System.nanoTime() < until) Thread.sleep(2);
            require(scheduled.getExecutionState() == io.papermc.paper.threadedregions.scheduler.ScheduledTask.ExecutionState.CANCELLED,
                "scheduler callback cancellation final state " + i);
        }
        global(() -> { server().tickRateManager().setFrozen(true); return null; }).get(10, TimeUnit.SECONDS);
        require(this.normal.getChunkGenerationStateAsync(40001, 40001).get(10, TimeUnit.SECONDS)
            == io.papermc.paper.world.chunk.ChunkGenerationState.NOT_GENERATED, "metadata I/O completes while gameplay frozen");
        global(() -> { server().tickRateManager().setFrozen(false); return null; }).get(10, TimeUnit.SECONDS);
    }

    private void testMetadataUnload() throws Exception {
        var queries = new java.util.ArrayList<CompletableFuture<io.papermc.paper.world.chunk.ChunkGenerationState>>();
        for (int i = 0; i < 128; ++i) queries.add(this.mirror.getChunkGenerationStateAsync(40000 + i * 32, 40000));
        var unloaded = Bukkit.getRuntimeWorldManager().unloadWorldAsync(this.mirror,
            io.papermc.paper.world.WorldUnloadOptions.defaults()).toCompletableFuture().get(60, TimeUnit.SECONDS);
        require(unloaded.successful(), "metadata query world unloaded through real lifecycle");
        for (var query : queries) {
            try {
                require(query.get(10, TimeUnit.SECONDS) == io.papermc.paper.world.chunk.ChunkGenerationState.NOT_GENERATED,
                    "accepted metadata read completed without generation");
            } catch (java.util.concurrent.ExecutionException failure) {
                require(failure.getCause() instanceof IllegalStateException, "inflight metadata read rejected inactive lifecycle explicitly");
            }
        }
        require(queries.stream().allMatch(CompletableFuture::isDone), "no pending metadata future after world unload");
        try { this.mirror.getChunkGenerationStateAsync(9, 9).get(10, TimeUnit.SECONDS); throw new AssertionError("closed world accepted query"); }
        catch (java.util.concurrent.ExecutionException failure) {
            require(failure.getCause() instanceof IllegalStateException, "closed world async metadata admission rejected");
        }
        try { this.mirror.getChunkGenerationState(9, 9); throw new AssertionError("closed world accepted sync query"); }
        catch (IllegalStateException expected) { require(true, "closed world sync metadata admission rejected"); }
    }

    private void latestOwnerApis(Player player) {
        final Location at = player.getLocation();
        require(this.normal.getChunkGenerationState(at.getBlockX() >> 4, at.getBlockZ() >> 4)
            == io.papermc.paper.world.chunk.ChunkGenerationState.GENERATED, "owner loaded generation snapshot");
        try { this.normal.getChunkGenerationState(40000, 40000); throw new AssertionError("blocking I/O accepted"); }
        catch (IllegalStateException expected) { require(true, "region disk query rejected before blocking"); }
        var stand = this.normal.spawn(at.clone().add(1, 0, 0), org.bukkit.entity.ArmorStand.class);
        var key = Key.key("tessera_smoke", "allies");
        player.addAdditionalAlliedRule(key, entity -> entity.getUniqueId().equals(stand.getUniqueId()));
        require(player.isAlliedTo(stand) && player.getAdditionalAlliedRules().containsKey(key), "native additional alliance rule");
        player.removeAdditionalAlliedRule(key);
        require(!player.getAdditionalAlliedRules().containsKey(key), "alliance rule removal");
        stand.setInvulnerable(true);
        require(stand.isMarkedInvulnerable(), "marked invulnerability getter");
        stand.setInvulnerable(false);
        require(!stand.isInvulnerableTo(org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build()),
            "source-specific invulnerability getter");
        require(player.canCriticalAttack(stand) == (((CraftPlayer)player).getHandle().canCriticalAttack(
            ((org.bukkit.craftbukkit.entity.CraftArmorStand)stand).getHandle())
            && !((CraftWorld)this.normal).getHandle().paperConfig().entities.behavior.disablePlayerCrits), "critical query matches native predicate");
        player.swingHand(org.bukkit.inventory.EquipmentSlot.HAND);
        stand.remove();
        var offers = org.bukkit.enchantments.EnchantmentOffer.rollEnchantmentOffers(new ItemStack(Material.DIAMOND_SWORD), 42, 15, 3);
        require(offers != null && offers.length == 3 && java.util.Arrays.stream(offers).anyMatch(java.util.Objects::nonNull), "native enchantment offers");
        var bucket = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SULFUR_CUBE_BUCKET);
        var bucketPos = new net.minecraft.core.BlockPos(at.getBlockX(), at.getBlockY(), at.getBlockZ());
        try (var scope = io.papermc.paper.event.BucketResultScope.openUse()) {
            require(((net.minecraft.world.item.MobBucketItem)net.minecraft.world.item.Items.SULFUR_CUBE_BUCKET).emptyContents(
                ((CraftPlayer)player).getHandle(), ((CraftWorld)this.normal).getHandle(), bucketPos, null,
                net.minecraft.core.Direction.UP, bucketPos, bucket, net.minecraft.world.InteractionHand.MAIN_HAND), "fluidless native bucket event accepted");
            var result = io.papermc.paper.event.BucketResultScope.takeUseResult();
            require(result != null && result.is(net.minecraft.world.item.Items.EMERALD), "fluidless bucket nested callback result remains invocation scoped");
        }
        final var position = at.clone().add(0, -3, 0);
        final var hopperBlock = position.getBlock();
        final var chestBlock = position.clone().add(0, 1, 0).getBlock();
        hopperBlock.setType(Material.HOPPER, false);
        chestBlock.setType(Material.CHEST, false);
        var chest = (org.bukkit.block.Chest)chestBlock.getState();
        chest.getBlockInventory().setItem(0, new ItemStack(Material.DIRT, 2));
        chest.getBlockInventory().setItem(1, new ItemStack(Material.STONE, 2));
        var nativeLevel = ((CraftWorld)this.normal).getHandle();
        var hopper = (net.minecraft.world.level.block.entity.HopperBlockEntity)nativeLevel.getBlockEntity(
            new net.minecraft.core.BlockPos(position.getBlockX(), position.getBlockY(), position.getBlockZ()));
        require(net.minecraft.world.level.block.entity.HopperBlockEntity.suckInItems(nativeLevel, hopper), "native hopper transfer selected allowed slot");
        require(chest.getBlockInventory().getItem(0).getAmount() == 2 && !hopper.isEmpty()
            && hopper.getItem(0).is(net.minecraft.world.item.Items.STONE), "hopper callback excluded dirt and moved stone");
        hopperBlock.setType(Material.AIR, false);
        chestBlock.setType(Material.AIR, false);
    }

    @EventHandler
    public void prime(ExplosionPrimeEvent event) {
        if (event.getEntity() != this.cube) return;
        if (this.cubeAction.equals("remove-prime")) {
            this.callbackTickCount = ((org.bukkit.craftbukkit.entity.CraftSulfurCube)this.cube).getHandle().tickCount;
            this.cube.remove();
            this.cubeCallback.complete(null);
        } else if (this.cubeAction.equals("transfer-prime")) {
            this.callbackTickCount = ((org.bukkit.craftbukkit.entity.CraftSulfurCube)this.cube).getHandle().tickCount;
            this.cubeAction = "cancel-destination";
            this.cubeTransfer = this.cube.teleportAsync(new Location(this.nether, 10, 200, 10));
            this.cubeCallback.complete(null);
        } else {
            event.setCancelled(true);
            this.cancelledExplosions.incrementAndGet();
            if (this.cubeAction.equals("cancel-destination")) this.destinationPrime.complete(null);
        }
    }

    @EventHandler
    public void ignite(io.papermc.paper.event.entity.EntityIgniteEvent event) {
        if (event.getEntity() == this.cube && this.cubeAction.equals("ignite-remove")) this.cube.remove();
    }

    @EventHandler
    public void explode(org.bukkit.event.entity.EntityExplodeEvent event) {
        if (event.getEntity() == this.cube) this.actualExplosions.incrementAndGet();
    }

    private void testCubeCallbackOwnership() throws Exception {
        var igniteRemoved = onRegion(this.normal, 0, () -> {
            this.cubeAction = "ignite-remove";
            return spawnExplosiveCube();
        }).get(10, TimeUnit.SECONDS);
        waitCubeReady(this.normal);
        onRegion(this.normal, 0, () -> {
            int fuse = igniteRemoved.getFuse();
            require(!this.cube.ignite() && igniteRemoved.isRemoved() && igniteRemoved.getFuse() == fuse,
                "ignite callback retirement left fuse unchanged");
            return null;
        }).get(10, TimeUnit.SECONDS);

        this.cubeCallback = new CompletableFuture<>();
        var removed = onRegion(this.normal, 0, () -> {
            this.cubeAction = "remove-prime";
            return spawnExplosiveCube();
        }).get(10, TimeUnit.SECONDS);
        waitCubeReady(this.normal);
        onRegion(this.normal, 0, () -> {
            require(this.cube.ignite(), "retirement cube ignition accepted");
            this.cube.setFuseTicks(1);
            return null;
        }).get(10, TimeUnit.SECONDS);
        this.cubeCallback.get(10, TimeUnit.SECONDS);
        onRegion(this.normal, 0, () -> {
            require(removed.isRemoved() && removed.tickCount == this.callbackTickCount && this.actualExplosions.get() == 0,
                "explosion callback retirement stopped further ticking");
            return null;
        }).get(10, TimeUnit.SECONDS);

        this.cubeCallback = new CompletableFuture<>();
        this.destinationPrime = new CompletableFuture<>();
        var transferred = onRegion(this.normal, 0, () -> {
            this.cubeAction = "transfer-prime";
            return spawnExplosiveCube();
        }).get(10, TimeUnit.SECONDS);
        waitCubeReady(this.normal);
        onRegion(this.normal, 0, () -> {
            require(this.cube.ignite(), "transfer cube ignition accepted");
            this.cube.setFuseTicks(1);
            return null;
        }).get(10, TimeUnit.SECONDS);
        this.cubeCallback.get(10, TimeUnit.SECONDS);
        require(this.cubeTransfer.get(20, TimeUnit.SECONDS), "explosion callback dimension transfer completed");
        waitCubeReady(this.nether);
        onRegion(this.nether, 0, () -> {
            if (!this.destinationPrime.isDone()) {
                require(this.cube.ignite(), "destination cube ignition accepted");
                this.cube.setFuseTicks(1);
            }
            return null;
        }).get(10, TimeUnit.SECONDS);
        this.destinationPrime.get(10, TimeUnit.SECONDS);
        onRegion(this.normal, 0, () -> {
            require(transferred.isRemoved() && transferred.tickCount == this.callbackTickCount,
                "explosion callback transfer stopped old-owner ticking");
            return null;
        }).get(10, TimeUnit.SECONDS);
        onRegion(this.nether, 0, () -> {
            require(this.cube.getWorld() == this.nether && this.cube.isValid() && !this.cube.isInvulnerable()
                && this.actualExplosions.get() == 0, "transferred cube cancellation on destination owner");
            this.cube.remove();
            return null;
        }).get(10, TimeUnit.SECONDS);
        this.cube = null;
        this.cubeAction = "cancel";
    }

    private net.minecraft.world.entity.monster.cubemob.SulfurCube spawnExplosiveCube() {
        this.cube = this.normal.spawn(new Location(this.normal, 10, 200, 10), SulfurCube.class, entity -> {
            entity.setAdult(); entity.setAI(false); entity.setGravity(false); entity.setPersistent(true);
        });
        require(this.cube.swallow(new ItemStack(Material.TNT)), "callback cube equipment accepted");
        return ((org.bukkit.craftbukkit.entity.CraftSulfurCube)this.cube).getHandle();
    }

    private void waitCubeReady(World world) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!onRegion(world, 0, this.cube::canExplode).get(10, TimeUnit.SECONDS)) {
            if (System.nanoTime() >= deadline) throw new AssertionError("native equipment update did not enable cube explosion");
            Thread.sleep(50); // Async fixture worker: equipment-derived archetypes update on an ordinary owner tick.
        }
    }

    private void playerApis(Player player) {
        require(Bukkit.isOwnedByCurrentRegion(player), "entity API current owner");
        var key = PersistentDataKey.of(Key.key("tessera_smoke:typed"), PersistentDataType.INTEGER);
        var pdc = player.getPersistentDataContainer();
        pdc.set(key, 41);
        require(pdc.has(key) && pdc.get(key) == 41, "typed player PDC store");
        pdc.remove(key);
        require(!pdc.has(key) && pdc.getOrDefault(key, 9) == 9, "typed player PDC remove and default");
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
        ItemStack weapon = player.getWeapon();
        require(weapon != null && weapon.getType() == Material.DIAMOND_SWORD, "entity weapon getter");
        weapon.setAmount(3);
        require(player.getInventory().getItemInMainHand().getAmount() == 1, "weapon getter returns a copy");
        require(player.getDefaultGravity() > 0 && player.getAirDrag() > 0, "gravity and drag getters");
        player.setGravity(false);
        require(player.getGravity() == 0, "effective gravity respects disabled gravity");
        player.setGravity(true);
        player.postEffects().clear();
        player.postEffects().update();
    }

    private World world(String name, World.Environment environment, long seed) throws Exception {
        var result = Bukkit.getRuntimeWorldManager().createWorldAsync(WorldCreator.ofKey(new NamespacedKey("tessera_smoke", name))
            .environment(environment).seed(seed).generateStructures(false)
            .forcedSpawnPosition(io.papermc.paper.math.Position.block(0, 200, 0), 0, 0)
            .generator(environment == World.Environment.NORMAL ? new TerrainReferenceGenerator() : null))
            .toCompletableFuture().get(90, TimeUnit.SECONDS);
        require(result.successful() && result.world() != null, "native noise world created " + name);
        return result.world();
    }

    private CompletableFuture<Void> prepare(World world, int x) {
        var chunks = new java.util.ArrayList<CompletableFuture<?>>();
        for (int dx = -1; dx <= 1; ++dx) for (int dz = -1; dz <= 1; ++dz) chunks.add(world.getChunkAtAsync(x + dx, dz, true));
        return CompletableFuture.allOf(chunks.toArray(CompletableFuture[]::new)).thenCompose(ignored -> onRegion(world, x, () -> {
            world.getChunkAt(x, 0).addPluginChunkTicket(this.plugin);
            return null;
        }));
    }

    private static long hash(World world, int x) {
        if (!((CraftWorld)world).getHandle().tickRateManager().isFrozen()) throw new AssertionError("terrain comparison must precede gameplay ticks");
        var chunk = java.util.Objects.requireNonNull(((CraftWorld)world).getHandle().getChunkIfLoadedImmediately(x, 0), "prepared chunk");
        long hash = 1125899906842597L;
        for (var section : chunk.getSections()) {
            for (int index = 0; index < 4096; ++index) hash = hash * 31 + Block.getId(section.getStates().get(index));
            for (int index = 0; index < 64; ++index) hash = hash * 31 + section.getBiomes().get(index & 3, index >> 4, (index >> 2) & 3).unwrapKey().orElseThrow().identifier().hashCode();
        }
        return hash;
    }

    private static ShapelessRecipe recipe(NamespacedKey key) {
        return new ShapelessRecipe(key, new ItemStack(Material.DIAMOND)).addIngredient(Material.DIRT);
    }

    // Exercise native noise, surfaces, caves and palette storage without
    // neighbouring decoration writes whose request order also varies in 018.
    private static final class TerrainReferenceGenerator extends org.bukkit.generator.ChunkGenerator {
        @SuppressWarnings("deprecation")
        @Override public boolean isParallelCapable() { return true; }
        @Override public boolean shouldGenerateNoise() { return true; }
        @Override public boolean shouldGenerateSurface() { return true; }
        @Override public boolean shouldGenerateCaves() { return true; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }
    }

    private <T> CompletableFuture<T> onRegion(World world, int x, Callable<T> action) {
        var result = new CompletableFuture<T>();
        var nativeTask = io.papermc.paper.threadedregions.RegionizedServer.getInstance().taskQueue.tessera$queueChunkTask(
            ((CraftWorld)world).getHandle(), x, 0, () -> {
            try { result.complete(action.call()); } catch (Throwable failure) { result.completeExceptionally(failure); }
        }, ca.spottedleaf.concurrentutil.util.Priority.NORMAL);
        return nativeTask.thenCompose(ignored -> result);
    }

    private <T> CompletableFuture<T> onPlayer(Player player, Callable<T> action) {
        return ((CraftPlayer)player).taskScheduler.scheduleRestore(entity -> {
            try { return CompletableFuture.completedFuture(action.call()); }
            catch (Throwable failure) { return CompletableFuture.failedFuture(failure); }
        }).toCompletableFuture();
    }

    private <T> CompletableFuture<T> global(Callable<T> action) {
        var result = new CompletableFuture<T>();
        Bukkit.getGlobalRegionScheduler().execute(this.plugin, () -> {
            try { result.complete(action.call()); } catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        return result;
    }

    private static net.minecraft.server.MinecraftServer server() { return net.minecraft.server.MinecraftServer.getServer(); }

    private void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        this.checks.add(message);
    }
}
