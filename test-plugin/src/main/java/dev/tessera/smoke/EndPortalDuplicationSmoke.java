package dev.tessera.smoke;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/** Uses real collisions, end portal blocks, native transfers and region threads. */
final class EndPortalDuplicationSmoke implements Listener {
    private static final NamespacedKey CASE = new NamespacedKey("tessera_smoke", "dupe_case");
    private final JavaPlugin plugin;
    private final boolean enabled = Boolean.parseBoolean(System.getenv("TESSERA_DUPE_ENABLED"));
    private final Map<String, Scenario> cases = new ConcurrentHashMap<>();
    private final List<String> checks = new CopyOnWriteArrayList<>();
    private final List<Throwable> failures = new CopyOnWriteArrayList<>();
    private final java.util.Set<Integer> owners = ConcurrentHashMap.newKeySet();
    private World source, end;

    private static final class Scenario {
        final String id, mode;
        final Material material;
        final Location source, target;
        final AtomicInteger entries = new AtomicInteger(), portals = new AtomicInteger(), sourcePlacements = new AtomicInteger(), targetPlacements = new AtomicInteger(), drops = new AtomicInteger();
        volatile FallingBlock original;
        volatile UUID continuation;
        Scenario(String id, String mode, Material material, Location source, Location target) {
            this.id = id; this.mode = mode; this.material = material; this.source = source; this.target = target;
        }
    }

    EndPortalDuplicationSmoke(JavaPlugin plugin) { this.plugin = plugin; }

    void start() {
        Bukkit.getPluginManager().registerEvents(this, this.plugin);
        Thread.ofVirtual().name("End portal duplication fixture").start(() -> {
            try {
                await(global(() -> {
                    this.source = Bukkit.getWorld("world"); this.end = Bukkit.getWorld("world_the_end");
                    require(this.source != null && this.end != null, "fixture dimensions loaded");
                    Object config = NativeRestoreSmoke.call(NativeRestoreSmoke.type("io.papermc.paper.configuration.GlobalConfiguration"), "get");
                    Object settings = config.getClass().getField("unsupportedSettings").get(config);
                    require(settings.getClass().getField("allowUnsafeEndPortalTeleportation").getBoolean(settings) == this.enabled, "real YAML toggle applied");
                }));
                // Two widely separated source regions exercise the same native path concurrently.
                List<Material> materials = List.of(Material.SAND, Material.RED_SAND, Material.GRAVEL, Material.WHITE_CONCRETE_POWDER, Material.ANVIL);
                for (int round = 0; round < 2; round++) {
                    List<CompletableFuture<Void>> runs = new java.util.ArrayList<>();
                    for (int region = 0; region < 2; region++) {
                        for (int i = 0; i < materials.size(); i++) {
                            int number = round * 10 + region * 5 + i;
                            runs.add(scenario("landing-" + number, "landing", materials.get(i), region * 2048 + 8, 8 + (round * 5 + i) * 32, number));
                        }
                    }
                    await(CompletableFuture.allOf(runs.toArray(CompletableFuture[]::new)));
                }
                for (String mode : List.of("airborne", "veto", "placement-veto", "redirect", "retire", "enter-veto", "continuation-retire", "drop", "drop-veto")) {
                    await(scenario(mode, mode, Material.SAND, 8, 800 + this.cases.size() * 32, this.cases.size()));
                }
                List<CompletableFuture<Void>> reverseRuns = new java.util.ArrayList<>();
                for (int i = 0; i < materials.size(); i++) {
                    reverseRuns.add(scenario("return-" + i, "landing", materials.get(i), 30008, 8 + i * 32, this.cases.size(), true));
                }
                await(CompletableFuture.allOf(reverseRuns.toArray(CompletableFuture[]::new)));
                List<CompletableFuture<Void>> eggRuns = new java.util.ArrayList<>();
                for (boolean reverse : List.of(false, true)) {
                    for (int region = 0; region < 2; region++) {
                        for (String mode : List.of("landing", "airborne", "drop")) {
                            String id = "egg-" + mode + "-" + (reverse ? "return" : "entry") + "-" + region;
                            eggRuns.add(scenario(id, mode, Material.DRAGON_EGG, (reverse ? 30008 : 8) + region * 2048,
                                1800 + this.cases.size() * 32, this.cases.size(), reverse));
                        }
                    }
                }
                await(CompletableFuture.allOf(eggRuns.toArray(CompletableFuture[]::new)));
                require(this.owners.size() >= 2, "multiple source region identities observed");
                if (!this.failures.isEmpty()) throw new AssertionError("Event/ownership assertion", this.failures.getFirst());
                Files.writeString(Path.of("end-portal-duplication-checks.txt"), String.join("\n", this.checks));
                this.plugin.getLogger().info("END_PORTAL_DUPLICATION_PASS enabled=" + this.enabled + " cases=" + this.cases.size());
            } catch (Throwable failure) {
                this.plugin.getLogger().log(java.util.logging.Level.SEVERE, "END_PORTAL_DUPLICATION_FAIL", failure);
            } finally {
                Bukkit.getGlobalRegionScheduler().execute(this.plugin, Bukkit::shutdown);
            }
        });
    }

    private CompletableFuture<Void> scenario(String id, String mode, Material material, int x, int z, int number) {
        return scenario(id, mode, material, x, z, number, false);
    }

    private CompletableFuture<Void> scenario(String id, String mode, Material material, int x, int z, int number, boolean reverse) {
        World origin = reverse ? this.end : this.source;
        World destination = reverse ? this.source : this.end;
        Location at = new Location(origin, x + (mode.startsWith("drop") ? 0.5 : 1.05), 80, z + 0.5);
        Location target = new Location(destination, 10000 + number * 64 + 0.5, 90, 8.5);
        Scenario scenario = new Scenario(id, mode, material, at, target);
        this.cases.put(id, scenario);
        // The disabled (ordinary Folia) path does not suspend falling during portal
        // preparation. Give that control case a prepared target and a real air gap.
        CompletableFuture<?> targetReady = mode.equals("airborne") || reverse
            ? destination.getChunkAtAsync(target).thenCompose(chunk -> region(target, 1, () -> {
                chunk.addPluginChunkTicket(this.plugin);
                if (reverse) {
                    destination.getBlockAt(target.clone().subtract(0, 1, 0)).setType(Material.STONE, false);
                    destination.getBlockAt(target).setType(Material.AIR, false);
                }
            }))
            : CompletableFuture.completedFuture(null);
        return targetReady.thenCompose(ignored -> origin.getChunkAtAsync(at)).thenCompose(chunk -> region(at, 1, () -> {
            chunk.addPluginChunkTicket(this.plugin);
            if (origin == this.source) this.owners.add(System.identityHashCode(NativeRestoreSmoke.call(NativeRestoreSmoke.type("io.papermc.paper.threadedregions.TickRegionScheduler"), "getCurrentRegion")));
            for (int dx = -2; dx <= 3; dx++) for (int dz = -2; dz <= 2; dz++) {
                origin.getBlockAt(x + dx, 79, z + dz).setType(mode.equals("airborne") ? Material.AIR : Material.STONE, false);
                origin.getBlockAt(x + dx, 80, z + dz).setType(Material.AIR, false);
                origin.getBlockAt(x + dx, 78, z + dz).setType(mode.equals("airborne") ? Material.AIR : Material.STONE, false);
            }
            origin.getBlockAt(x, 80, z).setType(Material.END_PORTAL, false);
            scenario.original = origin.spawnFallingBlock(at, material.createBlockData());
            scenario.original.setVelocity(new Vector(0, -0.25, 0));
            scenario.original.getPersistentDataContainer().set(CASE, PersistentDataType.STRING, id);
        })).thenCompose(ignored -> region(at, 100, () -> {
            this.plugin.getLogger().info("Portal fixture " + id + " entries=" + scenario.entries + " transfers=" + scenario.portals + " source=" + scenario.sourcePlacements + " destination=" + scenario.targetPlacements);
            if (!this.failures.isEmpty()) throw new AssertionError("Event failure", this.failures.getFirst());
            boolean duplicates = this.enabled && material != Material.DRAGON_EGG;
            boolean transferred = mode.equals("airborne") || duplicates && (mode.equals("landing") || mode.equals("placement-veto") || mode.equals("continuation-retire") || mode.startsWith("drop"));
            int expectedSource = mode.startsWith("drop") || ((mode.equals("retire") || mode.equals("continuation-retire")) && duplicates) ? 0 : mode.equals("airborne") || mode.equals("placement-veto") ? 0 : 1;
            require(scenario.sourcePlacements.get() == expectedSource, id + " source placements expected " + expectedSource + ", got " + scenario.sourcePlacements);
            require(scenario.targetPlacements.get() == (transferred ? 1 : 0), id + " target placements: " + scenario.targetPlacements);
            require(scenario.portals.get() == (!mode.equals("enter-veto") && (duplicates || mode.equals("airborne")) ? 1 : 0), id + " exactly one/no portal event: " + scenario.portals);
            require(scenario.entries.get() > 0, id + " real End portal collision observed");
            require(origin.getNearbyEntities(at, 3, 5, 3).stream().noneMatch(FallingBlock.class::isInstance), id + " no live continuation remains");
            List<Item> items = origin.getNearbyEntities(at, 3, 5, 3).stream().filter(Item.class::isInstance).map(Item.class::cast).toList();
            require(items.size() == (mode.equals("drop") ? 1 : 0), id + " exact source item count: " + items.size());
            require(scenario.drops.get() == (mode.startsWith("drop") ? 1 : 0), id + " exact drop event count: " + scenario.drops);
            for (Item item : items) {
                require(item.getItemStack().getType() == material && item.getItemStack().getAmount() == 1, id + " exact source drop contents");
                item.remove(); // Fixture cleanup after proving the actual emitted item.
            }
            Material placedSource = origin.getBlockAt(at).getType();
            require(placedSource == (mode.startsWith("drop") ? Material.END_PORTAL : expectedSource == 1 ? material : Material.AIR), id + " actual source block: " + placedSource);
            if (duplicates && transferred && !mode.equals("airborne")) require(scenario.continuation != null, id + " independent source UUID");
            if (material == Material.DRAGON_EGG) {
                require(scenario.continuation == null, id + " no dragon egg source continuation");
                require(scenario.sourcePlacements.get() + scenario.targetPlacements.get() + scenario.drops.get() == 1, id + " exactly one egg survives as a block or item");
            }
            this.checks.add(id + ": " + material + ", source=" + scenario.sourcePlacements + ", target=" + scenario.targetPlacements + ", portalEvents=" + scenario.portals + ", dropEvents=" + scenario.drops + ", no leaked entities/items");
        })).thenCompose(ignored -> destination.getChunkAtAsync(target).thenCompose(chunk -> region(target, 1, () -> {
            require(destination.getNearbyEntities(target, 3, 5, 3).stream().noneMatch(FallingBlock.class::isInstance), id + " no leaked destination entity");
            require(destination.getNearbyEntities(target, 3, 5, 3).stream().noneMatch(Item.class::isInstance), id + " no extra destination item");
            if (scenario.targetPlacements.get() == 1) require(destination.getBlockAt(target).getType() == material, id + " destination block really placed");
        })));
    }

    @EventHandler
    public void enter(EntityPortalEnterEvent event) {
        if (!(event.getEntity() instanceof FallingBlock block)) return;
        String id = block.getPersistentDataContainer().get(CASE, PersistentDataType.STRING);
        Scenario scenario = id == null ? null : this.cases.get(id);
        if (scenario != null) {
            scenario.entries.incrementAndGet();
            if (scenario.mode.equals("enter-veto")) event.setCancelled(true);
        }
    }

    @EventHandler
    public void portal(EntityPortalEvent event) {
        if (!(event.getEntity() instanceof FallingBlock block)) return;
        String id = block.getPersistentDataContainer().get(CASE, PersistentDataType.STRING);
        Scenario scenario = id == null ? null : this.cases.get(id);
        if (scenario == null) return;
        try {
            require(Bukkit.isOwnedByCurrentRegion(block), "portal event owns source entity");
            require(block == scenario.original, "portal retained original wrapper");
            scenario.portals.incrementAndGet();
            switch (scenario.mode) {
                case "veto" -> event.setCancelled(true);
                case "retire" -> { block.remove(); event.setCancelled(true); }
                case "redirect" -> event.setTo(scenario.source.clone().add(2, 0, 0));
                default -> event.setTo(scenario.target.clone()); // Native End platform creation also covers initially unloaded destination chunks.
            }
        } catch (Throwable failure) { this.failures.add(failure); event.setCancelled(true); }
    }

    @EventHandler
    public void drop(EntityDropItemEvent event) {
        if (!(event.getEntity() instanceof FallingBlock block)) return;
        String id = block.getPersistentDataContainer().get(CASE, PersistentDataType.STRING);
        Scenario scenario = id == null ? null : this.cases.get(id);
        if (scenario == null) return;
        try {
            require(Bukkit.isOwnedByCurrentRegion(block), "drop event owns its source");
            require(event.getItemDrop().getWorld() == scenario.source.getWorld(), "drop stays in origin");
            require(scenario.mode.startsWith("drop"), "no unexpected item drop");
            require(event.getItemDrop().getItemStack().getType() == scenario.material, "drop material unchanged");
            scenario.drops.incrementAndGet();
            if (this.enabled && scenario.material != Material.DRAGON_EGG) {
                require(block != scenario.original && !block.getUniqueId().equals(scenario.original.getUniqueId()), "drop continuation is independent");
                require(scenario.continuation == null, "drop continuation runs once");
                scenario.continuation = block.getUniqueId();
            }
            if (scenario.mode.equals("drop-veto")) event.setCancelled(true);
            // Keep the emitted item at the fixture: do not test its own subsequent
            // ordinary item-portal transfer instead of the falling-block drop here.
            event.getItemDrop().setPortalCooldown(1000);
        } catch (Throwable failure) { this.failures.add(failure); event.setCancelled(true); }
    }

    @EventHandler
    public void change(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock block)) return;
        String id = block.getPersistentDataContainer().get(CASE, PersistentDataType.STRING);
        Scenario scenario = id == null ? null : this.cases.get(id);
        if (scenario == null) return;
        try {
            require(Bukkit.isOwnedByCurrentRegion(block) && Bukkit.isOwnedByCurrentRegion(event.getBlock().getLocation()), "placement event is wholly owned");
            require(event.getTo() == scenario.material, "material unchanged");
            if (event.getBlock().getWorld() == scenario.source.getWorld()) {
                boolean continuation = !block.getUniqueId().equals(scenario.original.getUniqueId());
                if (continuation) {
                    require(block != scenario.original && block.isValid(), "continuation has distinct registered wrapper");
                    require(scenario.continuation == null, "continuation runs at most once");
                    scenario.continuation = block.getUniqueId();
                    if (scenario.mode.equals("continuation-retire")) { block.remove(); return; }
                }
                if (scenario.mode.equals("placement-veto")) { event.setCancelled(true); return; }
                scenario.sourcePlacements.incrementAndGet();
            } else {
                require(event.getBlock().getWorld() == scenario.target.getWorld() && block == scenario.original, "transfer keeps Bukkit wrapper and destination");
                require(block.getUniqueId().equals(scenario.original.getUniqueId()), "destination identity unchanged");
                scenario.targetPlacements.incrementAndGet();
            }
        } catch (Throwable failure) { this.failures.add(failure); event.setCancelled(true); }
    }

    @FunctionalInterface private interface Action { void run() throws Exception; }
    private CompletableFuture<Void> global(Action action) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        Bukkit.getGlobalRegionScheduler().execute(this.plugin, () -> execute(action, result)); return result;
    }
    private CompletableFuture<Void> region(Location at, long delay, Action action) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        Bukkit.getRegionScheduler().runDelayed(this.plugin, at, ignored -> execute(action, result), delay); return result;
    }
    private static void execute(Action action, CompletableFuture<Void> result) {
        try { action.run(); result.complete(null); } catch (Throwable failure) { result.completeExceptionally(failure); }
    }
    private static void await(CompletableFuture<?> future) throws Exception { future.get(120, TimeUnit.SECONDS); }
    private static void require(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
}
