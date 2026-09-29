package dev.tessera.smoke;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Hopper;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/** Real default End entry, cardinal flight, wall buttons and actual hopper insertion. */
final class EndPortalFlightSmoke implements Listener {
    private static final NamespacedKey CASE = new NamespacedKey("tessera_smoke", "end_flight");
    private final JavaPlugin plugin;
    private final Map<String, Flight> flights = new ConcurrentHashMap<>();
    private final List<Location> hoppers = new ArrayList<>();
    private final List<String> checks = new ArrayList<>();
    private World source, end;
    private Location exit;

    private static final class Flight {
        final String id;
        final Material material;
        final Vector direction;
        final Vector exitDirection;
        final CompletableFuture<Void> done = new CompletableFuture<>();
        final AtomicInteger portals = new AtomicInteger(), arrivals = new AtomicInteger(), sourceBlocks = new AtomicInteger(), drops = new AtomicInteger(), pickups = new AtomicInteger();
        volatile FallingBlock original;
        volatile Vector entryVelocity, arrivalVelocity;
        volatile Location dropPosition;
        volatile double maxDistance;
        Flight(String id, Material material, Vector direction) {
            this.id = id; this.material = material; this.direction = direction;
            // Real Vanilla portal rotation: source yaw 0 -> End exit yaw 90.
            this.exitDirection = new Vector(-direction.getZ(), 0, direction.getX());
        }
    }

    EndPortalFlightSmoke(JavaPlugin plugin) { this.plugin = plugin; }

    void start() {
        Bukkit.getPluginManager().registerEvents(this, this.plugin);
        Thread.ofVirtual().name("Default End flight fixture").start(() -> {
            try {
                CompletableFuture<Void> ready = new CompletableFuture<>();
                Bukkit.getGlobalRegionScheduler().execute(this.plugin, () -> run(() -> {
                    this.source = Bukkit.getWorld("world"); this.end = Bukkit.getWorld("world_the_end");
                    require(this.source != null && this.end != null, "fixture dimensions available");
                    this.exit = new Location(this.end, 100.5, 50, 0.5);
                    Object config = NativeRestoreSmoke.call(NativeRestoreSmoke.type("io.papermc.paper.configuration.GlobalConfiguration"), "get");
                    Object settings = config.getClass().getField("unsupportedSettings").get(config);
                    require(settings.getClass().getField("allowUnsafeEndPortalTeleportation").getBoolean(settings), "real dupe option enabled");
                }, ready));
                await(ready);
                await(prepareCollector());
                List<Material> materials = List.of(Material.SAND, Material.RED_SAND, Material.GRAVEL, Material.WHITE_CONCRETE_POWDER, Material.ANVIL);
                List<Vector> directions = List.of(new Vector(1, 0, 0), new Vector(-1, 0, 0), new Vector(0, 0, 1), new Vector(0, 0, -1));
                List<String> names = List.of("east", "west", "south", "north");
                int index = 0;
                for (Material material : materials) for (int direction = 0; direction < directions.size(); direction++) {
                    Flight flight = new Flight(material.name().toLowerCase(java.util.Locale.ROOT) + "-" + names.get(direction), material, directions.get(direction));
                    this.flights.put(flight.id, flight);
                    await(region(this.exit, 1, () -> {
                        for (Location at : this.hoppers) ((Hopper) at.getBlock().getState()).getInventory().clear();
                    }));
                    int x = index % 2 * 2048 + 8, z = 8 + index * 32;
                    Location at = new Location(this.source, x + 0.5, 80, z + 0.5);
                    await(this.source.getChunkAtAsync(at).thenCompose(chunk -> region(at, 1, () -> {
                        chunk.addPluginChunkTicket(this.plugin);
                        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                            this.source.getBlockAt(x + dx, 79, z + dz).setType(Material.STONE, false);
                            this.source.getBlockAt(x + dx, 80, z + dz).setType(Material.AIR, false);
                        }
                        this.source.getBlockAt(x, 80, z).setType(Material.END_PORTAL, false);
                        flight.original = this.source.spawnFallingBlock(at, material.createBlockData());
                        flight.original.getPersistentDataContainer().set(CASE, PersistentDataType.STRING, flight.id);
                        flight.original.setVelocity(flight.direction.clone().setY(-0.25));
                    })));
                    await(flight.done);
                    this.checks.add(flight.id + ": entryVelocity=" + flight.entryVelocity + ", arrivalVelocity=" + flight.arrivalVelocity
                        + ", distance=" + flight.maxDistance + ", drop=" + flight.dropPosition.toVector() + ", hopperItems=1, targetBlocks=0");
                    index++;
                }
                Files.writeString(Path.of("end-portal-flight-checks.txt"), String.join("\n", this.checks));
                this.plugin.getLogger().info("END_PORTAL_FLIGHT_PASS cases=" + this.checks.size());
            } catch (Throwable failure) {
                this.plugin.getLogger().log(java.util.logging.Level.SEVERE, "END_PORTAL_FLIGHT_FAIL", failure);
            } finally {
                Bukkit.getGlobalRegionScheduler().execute(this.plugin, Bukkit::shutdown);
            }
        });
    }

    private CompletableFuture<Void> prepareCollector() {
        List<CompletableFuture<Void>> chunks = new ArrayList<>();
        for (int cx = 5; cx <= 7; cx++) for (int cz = -1; cz <= 1; cz++) {
            final int chunkX = cx, chunkZ = cz;
            Location at = new Location(this.end, cx * 16 + 8, 50, cz * 16 + 8);
            chunks.add(this.end.getChunkAtAsync(at).thenCompose(chunk -> region(at, 1, () -> {
                chunk.addPluginChunkTicket(this.plugin);
                for (int x = Math.max(84, chunkX * 16); x <= Math.min(116, chunkX * 16 + 15); x++) {
                    for (int z = Math.max(-16, chunkZ * 16); z <= Math.min(16, chunkZ * 16 + 15); z++) {
                        for (int y = 46; y <= 64; y++) this.end.getBlockAt(x, y, z).setType(Material.AIR, false);
                    }
                }
            })));
        }
        return CompletableFuture.allOf(chunks.toArray(CompletableFuture[]::new)).thenCompose(ignored -> region(this.exit, 5, () -> {
            for (Vector direction : List.of(new Vector(1, 0, 0), new Vector(-1, 0, 0), new Vector(0, 0, 1), new Vector(0, 0, -1))) {
                for (int distance = 4; distance <= 14; distance++) {
                    int x = 100 + direction.getBlockX() * distance, z = direction.getBlockZ() * distance;
                    Location hopper = new Location(this.end, x, 47, z);
                    require(Bukkit.isOwnedByCurrentRegion(hopper), "collector construction owns hopper");
                    this.end.getBlockAt(x, 46, z).setType(Material.STONE, false);
                    hopper.getBlock().setType(Material.HOPPER, false);
                    // Wall-mounted buttons have real solid support; hoppers need
                    // not pretend to have a solid upper face for a floor button.
                    boolean alongX = direction.getX() != 0;
                    this.end.getBlockAt(x + (alongX ? 0 : 1), 48, z + (alongX ? 1 : 0)).setType(Material.STONE, false);
                    this.end.getBlockAt(x, 48, z).setBlockData(Material.STONE_BUTTON.createBlockData("[face=wall,facing=" + (alongX ? "north" : "west") + "]"), false);
                    this.hoppers.add(hopper);
                }
            }
        }));
    }

    private Flight flight(org.bukkit.entity.Entity entity) {
        String id = entity.getPersistentDataContainer().get(CASE, PersistentDataType.STRING);
        return id == null ? null : this.flights.get(id);
    }

    @EventHandler public void portal(EntityPortalEvent event) {
        Flight flight = flight(event.getEntity());
        if (flight == null) return;
        try {
            require(Bukkit.isOwnedByCurrentRegion(event.getEntity()), "source portal owner");
            require(event.getTo() != null && event.getTo().getWorld() == this.end && event.getTo().toVector().equals(this.exit.toVector()),
                "Vanilla default End arrival expected " + this.exit.toVector() + ", got " + event.getTo());
            flight.entryVelocity = event.getEntity().getVelocity().clone();
            require(flight.entryVelocity.distance(flight.direction) < 1.0E-8, "source collision retains cardinal horizontal velocity");
            flight.portals.incrementAndGet();
            // Deliberately no setTo or velocity mutation: exercise the true default.
        } catch (Throwable failure) { event.setCancelled(true); flight.done.completeExceptionally(failure); }
    }

    @EventHandler public void added(EntityAddToWorldEvent event) {
        if (!(event.getEntity() instanceof FallingBlock block) || event.getWorld() != this.end) return;
        Flight flight = flight(block);
        if (flight == null) return;
        try {
            require(Bukkit.isOwnedByCurrentRegion(block), "arrival owner");
            require(block == flight.original, "arrival retained original wrapper");
            require(block.getLocation().toVector().distance(this.exit.toVector()) < 1.0E-8, "exact Vanilla arrival height");
            require(this.end.getBlockAt(100, 48, 0).getType() == Material.OBSIDIAN && this.end.getBlockAt(100, 49, 0).getType().isAir(), "Vanilla platform floor and clearance");
            flight.arrivalVelocity = block.getVelocity().clone();
            require(flight.arrivalVelocity.distance(flight.exitDirection) < 1.0E-6, "native portal rotates existing velocity exactly as Vanilla, without extra impulse");
            flight.arrivals.incrementAndGet();
            require(block.getScheduler().runAtFixedRate(this.plugin, task -> {
                try {
                    require(Bukkit.isOwnedByCurrentRegion(block), "trajectory stays on entity owner");
                    Vector offset = block.getLocation().toVector().subtract(this.exit.toVector());
                    double distance = offset.dot(flight.exitDirection);
                    require(distance >= -1.0E-8 && offset.clone().setY(0).subtract(flight.exitDirection.clone().multiply(distance)).length() < 1.0E-5, "Vanilla cardinal exit direction");
                    flight.maxDistance = Math.max(flight.maxDistance, distance);
                } catch (Throwable failure) { flight.done.completeExceptionally(failure); task.cancel(); }
            }, () -> {}, 1, 1) != null, "trajectory scheduler admitted");
        } catch (Throwable failure) { flight.done.completeExceptionally(failure); }
    }

    @EventHandler public void change(EntityChangeBlockEvent event) {
        Flight flight = flight(event.getEntity());
        if (flight == null) return;
        if (event.getBlock().getWorld() == this.source) { flight.sourceBlocks.incrementAndGet(); return; }
        event.setCancelled(true);
        flight.done.completeExceptionally(new AssertionError("Falling block placed at target instead of flying to buttons: " + event.getBlock().getLocation()));
    }

    @EventHandler public void drop(EntityDropItemEvent event) {
        Flight flight = flight(event.getEntity());
        if (flight == null) return;
        try {
            require(Bukkit.isOwnedByCurrentRegion(event.getEntity()), "drop owner");
            Location at = event.getEntity().getLocation();
            require(at.getWorld() == this.end && at.getBlock().getType() == Material.STONE_BUTTON, "native drop occurs at a button in End");
            require(event.getItemDrop().getItemStack().getType() == flight.material && event.getItemDrop().getItemStack().getAmount() == 1, "exact native item drop");
            require(flight.maxDistance > 3, "entity really flew beyond platform");
            flight.dropPosition = at;
            flight.drops.incrementAndGet();
            event.getItemDrop().getPersistentDataContainer().set(CASE, PersistentDataType.STRING, flight.id);
        } catch (Throwable failure) { event.setCancelled(true); flight.done.completeExceptionally(failure); }
    }

    @EventHandler public void pickup(InventoryPickupItemEvent event) {
        Flight flight = flight(event.getItem());
        if (flight == null) return;
        flight.pickups.incrementAndGet();
        region(this.exit, 2, () -> {
            require(flight.portals.get() == 1 && flight.arrivals.get() == 1 && flight.sourceBlocks.get() == 1 && flight.drops.get() == 1 && flight.pickups.get() == 1, "one portal, source block, arrival, drop and pickup");
            int amount = 0;
            for (Location at : this.hoppers) {
                for (var stack : ((Hopper) at.getBlock().getState()).getInventory().getContents()) if (stack != null) {
                    require(stack.getType() == flight.material, "hopper contains only the expected material"); amount += stack.getAmount();
                }
            }
            require(amount == 1, "real hopper insertion exactly once, got " + amount);
            require(this.end.getNearbyEntities(this.exit, 16, 16, 16).stream().noneMatch(entity -> (entity instanceof FallingBlock || entity instanceof Item) && flight(entity) == flight), "no leftover target entity/item");
            this.plugin.getLogger().info("END_PORTAL_FLIGHT_CASE " + flight.id + " distance=" + flight.maxDistance + " hopper=1");
        }).whenComplete((ignored, error) -> { if (error == null) flight.done.complete(null); else flight.done.completeExceptionally(error); });
    }

    @FunctionalInterface private interface Action { void run() throws Exception; }
    private CompletableFuture<Void> region(Location at, long delay, Action action) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        Bukkit.getRegionScheduler().runDelayed(this.plugin, at, ignored -> run(action, result), delay); return result;
    }
    private static void run(Action action, CompletableFuture<Void> result) {
        try { action.run(); result.complete(null); } catch (Throwable failure) { result.completeExceptionally(failure); }
    }
    private static void await(CompletableFuture<?> future) throws Exception { future.get(40, TimeUnit.SECONDS); }
    private static void require(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
}
