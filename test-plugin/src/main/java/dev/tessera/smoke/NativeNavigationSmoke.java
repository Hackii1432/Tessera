package dev.tessera.smoke;

import ca.spottedleaf.moonrise.common.util.TickThread;
import io.papermc.paper.threadedregions.TickRegionScheduler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Real server ownership, navigation, entity exception guard and connected riders. */
final class NativeNavigationSmoke {
    private final JavaPlugin plugin;
    private final List<String> checks = new CopyOnWriteArrayList<>();
    private final boolean baseline = Boolean.parseBoolean(System.getenv("TESSERA_NAV_EXPECT_BUG"));

    NativeNavigationSmoke(JavaPlugin plugin) { this.plugin = plugin; }

    void start() {
        this.plugin.getLogger().info("NAV_READY_CLIENTS");
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(this.plugin, task -> {
            if (Bukkit.getOnlinePlayers().size() != 2) return;
            task.cancel();
            Bukkit.getAsyncScheduler().runNow(this.plugin, ignored -> this.run());
        }, 1, 10);
    }

    private void run() {
        try {
            Player one = Bukkit.getPlayerExact("NavAlpha");
            Player two = Bukkit.getPlayerExact("NavBeta");
            require(one != null && two != null, "two protocol clients connected");
            World nether = global(() -> Bukkit.getWorlds().stream().filter(w -> w.getEnvironment() == World.Environment.NETHER)
                .findFirst().orElseThrow()).get(15, TimeUnit.SECONDS);
            World overworld = global(() -> Bukkit.getWorlds().stream().filter(w -> w.getEnvironment() == World.Environment.NORMAL)
                .findFirst().orElseThrow()).get(15, TimeUnit.SECONDS);
            require(owner(one, () -> one.teleportAsync(new Location(nether, -361.5, 128, -1429.5)))
                .get(15, TimeUnit.SECONDS).get(60, TimeUnit.SECONDS), "first client transferred to Nether roof");
            require(owner(two, () -> two.teleportAsync(new Location(nether, 2056.5, 128, 8.5)))
                .get(15, TimeUnit.SECONDS).get(60, TimeUnit.SECONDS), "second client transferred to distant Nether region");
            Horse first = owner(one, () -> spawn(one)).get(15, TimeUnit.SECONDS);
            Horse second = owner(two, () -> spawn(two)).get(15, TimeUnit.SECONDS);
            long firstRegion = owner(one, () -> TickRegionScheduler.getCurrentRegion().id).get(15, TimeUnit.SECONDS);
            long secondRegion = owner(two, () -> TickRegionScheduler.getCurrentRegion().id).get(15, TimeUnit.SECONDS);
            require(firstRegion != secondRegion, "two mounted riders are on distinct native regions");
            UUID firstId = owner(first, first::getUniqueId).get(15, TimeUnit.SECONDS);
            UUID secondId = owner(second, second::getUniqueId).get(15, TimeUnit.SECONDS);

            owner(first, () -> {
                var horse = nativeHorse(first);
                ServerLevel level = (ServerLevel)horse.level();
                BlockPos stale = new BlockPos(-362, 128, -270);
                require(!TickThread.isTickThreadFor(level, stale), "reported distant waypoint is not owned");
                require(level.getChunkSource().getChunkAtIfLoadedImmediately(stale.getX() >> 4, stale.getZ() >> 4) == null,
                    "reported distant waypoint is initially unloaded");
                PathNavigation navigation = horse.getNavigation();
                var path = localPath(horse, false);
                require(navigation.moveTo(path, 1.0), "original ground navigation accepts a valid local path");
                path.replaceNode(0, node(stale));
                horse.setOnGround(true);
                // Deliberately use the production exception guard: the old JAR
                // discards this horse here, rather than merely failing a helper.
                level.guardEntityTick(entity -> ((Mob)entity).getNavigation().tick(), horse);
                if (this.baseline) {
                    require(horse.isRemoved(), "baseline reproduces horse removal through real entity exception guard");
                    return null;
                }
                require(!horse.isRemoved() && navigation.isDone(), "reported unloaded stale path is cleared without discarding horse");
                verifyHorse(first, one, firstId);
                require(level.getChunkSource().getChunkAtIfLoadedImmediately(stale.getX() >> 4, stale.getZ() >> 4) == null,
                    "reported stale path does not load its distant chunk");
                return null;
            }).get(20, TimeUnit.SECONDS);
            if (this.baseline) {
                Files.write(Path.of("navigation-checks.txt"), this.checks);
                this.plugin.getLogger().info("NAV_BASELINE_BUG_REPRODUCED");
                return;
            }

            BlockPos foreignForFirst = owner(second, () -> nativeHorse(second).blockPosition()).get(15, TimeUnit.SECONDS);
            BlockPos foreignForSecond = owner(first, () -> nativeHorse(first).blockPosition()).get(15, TimeUnit.SECONDS);
            // All mutable accesses below remain on the respective entity owner.
            CompletableFuture.allOf(
                owner(first, () -> { invalidCase(first, one, firstId, foreignForFirst, false, "first parallel foreign loaded path"); return null; }),
                owner(second, () -> { invalidCase(second, two, secondId, foreignForSecond, false, "second parallel foreign loaded path"); return null; })
            ).get(20, TimeUnit.SECONDS);
            owner(first, () -> {
                invalidCase(first, one, firstId, foreignForFirst, true, "foreign waypoint after native advancement");
                var horse = nativeHorse(first);
                ServerLevel level = (ServerLevel)horse.level();
                BlockPos missing = ownedUnloaded(level, horse.blockPosition());
                require(missing != null, "owned but unloaded waypoint available in native region footprint");
                invalidCase(first, one, firstId, missing, false, "owned unloaded path");
                require(level.getChunkSource().getChunkAtIfLoadedImmediately(missing.getX() >> 4, missing.getZ() >> 4) == null,
                    "owned unloaded path does not synchronously load a chunk");
                var cache = new PathNavigationRegion(level, foreignForFirst, foreignForFirst);
                require(cache.getBlockStateIfLoaded(foreignForFirst) == null, "native recomputation cache omits foreign loaded chunk");
                var navigation = installGuardedNavigation(horse);
                require(!navigation.moveTo(path(foreignForFirst), 1.0), "supplied foreign path rejected before trimming");
                require(navigation.groundReads == 0, "supplied foreign path never reads ground");
                validCase(first, one, firstId);
                return null;
            }).get(20, TimeUnit.SECONDS);

            require(owner(first, () -> first.teleportAsync(new Location(nether, 4104.5, 128, 8.5)))
                .get(15, TimeUnit.SECONDS).get(60, TimeUnit.SECONDS), "mounted horse transfers across native regions");
            owner(one, () -> {
                require(TickRegionScheduler.getCurrentRegion().id != firstRegion, "rider and horse reached a new native region");
                verifyHorse(first, one, firstId);
                invalidCase(first, one, firstId, foreignForSecond, false, "old waypoint after region transfer");
                validCase(first, one, firstId);
                return null;
            }).get(20, TimeUnit.SECONDS);

            owner(two, () -> { require(two.leaveVehicle(), "second rider dismounts explicitly before dimension transfer"); return null; })
                .get(15, TimeUnit.SECONDS);
            Location destination = new Location(overworld, 2056.5, 70, 8.5);
            require(owner(second, () -> second.teleportAsync(destination)).get(15, TimeUnit.SECONDS).get(60, TimeUnit.SECONDS),
                "horse transfers to another dimension");
            require(owner(two, () -> two.teleportAsync(destination)).get(15, TimeUnit.SECONDS).get(60, TimeUnit.SECONDS),
                "second client transfers without reconnect");
            owner(second, () -> {
                for (int x = 2054; x <= 2058; x++) for (int z = 6; z <= 10; z++) overworld.getBlockAt(x, 69, z).setType(Material.STONE, false);
                require(second.addPassenger(two), "rider remounts preserved horse in target dimension");
                verifyHorse(second, two, secondId);
                require(second.getWorld() == overworld, "navigation uses destination ServerLevel after dimension transfer");
                invalidCase(second, two, secondId, foreignForSecond, false, "stale coordinates in destination dimension");
                validCase(second, two, secondId);
                return null;
            }).get(20, TimeUnit.SECONDS);

            Thread.sleep(2500);
            owner(first, () -> { verifyHorse(first, one, firstId); return null; }).get(15, TimeUnit.SECONDS);
            owner(second, () -> { verifyHorse(second, two, secondId); return null; }).get(15, TimeUnit.SECONDS);
            require(Bukkit.getOnlinePlayers().size() == 2, "both riders remain connected after natural entity ticks");
            Files.write(Path.of("navigation-checks.txt"), this.checks);
            this.plugin.getLogger().info("NAV_PASS");
        } catch (Throwable failure) {
            this.plugin.getLogger().log(Level.SEVERE, "NAV_FAIL", failure);
            try { Files.write(Path.of("navigation-checks.txt"), this.checks); } catch (Exception ignored) { }
        } finally {
            Bukkit.shutdown();
        }
    }

    private Horse spawn(Player rider) {
        var horse = rider.getWorld().spawn(rider.getLocation(), Horse.class, h -> {
            h.setAdult(); h.setTamed(true); h.setOwner(rider); h.setPersistent(true); h.setRemoveWhenFarAway(false);
            h.getInventory().setSaddle(new ItemStack(Material.SADDLE));
            h.getInventory().setArmor(new ItemStack(Material.DIAMOND_HORSE_ARMOR));
        });
        require(horse.addPassenger(rider), "player mounted a tamed equipped horse on its owner");
        return horse;
    }

    private void invalidCase(Horse bukkit, Player rider, UUID id, BlockPos target, boolean advance, String label) throws Exception {
        var horse = nativeHorse(bukkit);
        var navigation = installGuardedNavigation(horse);
        var route = localPath(horse, advance);
        require(navigation.moveTo(route, 1.0), label + ": local route installed before invalidation");
        route.replaceNode(advance ? 1 : 0, node(target));
        horse.setOnGround(true);
        ((ServerLevel)horse.level()).guardEntityTick(entity -> ((Mob)entity).getNavigation().tick(), horse);
        require(!horse.isRemoved() && navigation.isDone() && navigation.groundReads == 0, label + ": cleared before any ground read");
        require(!horse.getMoveControl().hasWanted(), label + ": stale AI steering cancelled");
        verifyHorse(bukkit, rider, id);
    }

    private void validCase(Horse bukkit, Player rider, UUID id) throws Exception {
        var horse = nativeHorse(bukkit);
        var navigation = installGuardedNavigation(horse);
        require(navigation.moveTo(localPath(horse, false), 1.0), "valid local navigation accepted");
        horse.setOnGround(true);
        ((ServerLevel)horse.level()).guardEntityTick(entity -> ((Mob)entity).getNavigation().tick(), horse);
        require(!horse.isRemoved() && navigation.groundReads == 1 && navigation.isInProgress(), "valid local navigation retains Vanilla ground read and live path");
        require(horse.getMoveControl().hasWanted(), "valid local navigation retains Vanilla movement control");
        navigation.stop(); horse.getMoveControl().setWait();
        verifyHorse(bukkit, rider, id);
    }

    private void verifyHorse(Horse horse, Player rider, UUID id) {
        require(horse.isValid() && !horse.isDead() && horse.getUniqueId().equals(id), "horse identity and live state preserved");
        require(horse.getPassengers().contains(rider) && rider.getVehicle() == horse, "rider remains on the same horse");
        require(horse.isTamed() && horse.getOwner() != null && horse.getOwner().getUniqueId().equals(rider.getUniqueId()), "horse ownership preserved");
        require(horse.getInventory().getSaddle() != null && horse.getInventory().getSaddle().getType() == Material.SADDLE
            && horse.getInventory().getArmor() != null && horse.getInventory().getArmor().getType() == Material.DIAMOND_HORSE_ARMOR,
            "saddle and horse armor preserved");
    }

    private static net.minecraft.world.entity.animal.equine.Horse nativeHorse(Horse horse) {
        return (net.minecraft.world.entity.animal.equine.Horse)((CraftEntity)horse).getHandle();
    }

    private static GuardedGroundNavigation installGuardedNavigation(Mob mob) throws Exception {
        var navigation = new GuardedGroundNavigation(mob, (ServerLevel)mob.level());
        var field = Mob.class.getDeclaredField("navigation"); field.setAccessible(true); field.set(mob, navigation);
        return navigation;
    }

    private static net.minecraft.world.level.pathfinder.Path localPath(Mob mob, boolean advance) {
        BlockPos p = mob.blockPosition();
        return advance ? path(p, p.offset(3, 0, 0)) : path(p.offset(3, 0, 0));
    }

    private static Node node(BlockPos p) { return new Node(p.getX(), p.getY(), p.getZ()); }

    private static net.minecraft.world.level.pathfinder.Path path(BlockPos... positions) {
        var nodes = new ArrayList<Node>(); for (BlockPos p : positions) nodes.add(node(p));
        return new net.minecraft.world.level.pathfinder.Path(nodes, positions[positions.length - 1], true);
    }

    private static BlockPos ownedUnloaded(ServerLevel level, BlockPos origin) {
        for (int x = -15; x <= 15; x++) for (int z = -15; z <= 15; z++) {
            int cx = (origin.getX() >> 4) + x, cz = (origin.getZ() >> 4) + z;
            if (TickThread.isTickThreadFor(level, cx, cz) && level.getChunkSource().getChunkAtIfLoadedImmediately(cx, cz) == null)
                return new BlockPos(cx * 16 + 4, 128, cz * 16 + 4);
        }
        return null;
    }

    private <T> CompletableFuture<T> owner(Entity entity, Callable<T> action) {
        var future = new CompletableFuture<T>();
        boolean scheduled = entity.getScheduler().execute(this.plugin, () -> {
            try { future.complete(action.call()); } catch (Throwable failure) { future.completeExceptionally(failure); }
        }, () -> future.completeExceptionally(new IllegalStateException("Entity retired before navigation fixture task")), 1);
        if (!scheduled) future.completeExceptionally(new IllegalStateException("Entity scheduler rejected navigation fixture task"));
        return future;
    }

    private <T> CompletableFuture<T> global(Callable<T> action) {
        var future = new CompletableFuture<T>();
        Bukkit.getGlobalRegionScheduler().run(this.plugin, task -> {
            try { future.complete(action.call()); } catch (Throwable failure) { future.completeExceptionally(failure); }
        });
        return future;
    }

    private void require(boolean condition, String check) {
        if (!condition) throw new AssertionError(check);
        this.checks.add(check); this.plugin.getLogger().info("NAV_CHECK " + check);
    }

    /** Remaining negative cases fail before touching foreign world state even on a regression. */
    private static final class GuardedGroundNavigation extends GroundPathNavigation {
        int groundReads;
        GuardedGroundNavigation(Mob mob, ServerLevel level) { super(mob, level); }
        @Override
        protected double getGroundY(Vec3 target) {
            BlockPos pos = BlockPos.containing(target);
            ServerLevel level = (ServerLevel)this.level;
            if (!TickThread.isTickThreadFor(level, pos)
                || level.getChunkSource().getChunkAtIfLoadedImmediately(pos.getX() >> 4, pos.getZ() >> 4) == null)
                throw new AssertionError("Navigation attempted a forbidden ground read");
            this.groundReads++;
            return super.getGroundY(target);
        }
    }
}
