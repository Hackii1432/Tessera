package dev.tessera.smoke;

import io.papermc.paper.world.WorldUnloadOptions;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.plugin.java.JavaPlugin;

/** Isolated native-backend tests. Reflection accesses server internals, not a published API. */
final class NativePregenerationSmoke implements Listener {
    private final JavaPlugin plugin;
    private final boolean recovery;
    private final List<String> checks = new CopyOnWriteArrayList<>();
    private Object service;
    private Class<?> areaType;
    private Class<?> shapeType;
    private Class<?> modeType;
    private volatile boolean finished;
    private volatile UUID failSaveWorld;
    private final java.util.concurrent.atomic.AtomicInteger joins = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger quits = new java.util.concurrent.atomic.AtomicInteger();

    NativePregenerationSmoke(JavaPlugin plugin, boolean recovery) {
        this.plugin = plugin;
        this.recovery = recovery;
    }

    void start() {
        try {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
            Class<?> serviceType = Class.forName("io.papermc.paper.pregeneration.WorldPregenerator");
            for (Method method : serviceType.getMethods()) {
                if (method.getName().equals("get") && method.getParameterCount() == 1) {
                    service = method.invoke(null, server);
                }
            }
            require(service != null, "native service exists");
            areaType = Class.forName("io.papermc.paper.pregeneration.PregenerationArea");
            shapeType = Class.forName(areaType.getName() + "$Shape");
            modeType = Class.forName(serviceType.getName() + "$Mode");
            plugin.getLogger().info("PREGEN_READY_CLIENTS");
            awaitPlayers().thenCompose(players -> recovery ? recover(players) : exercise(players))
                .orTimeout(300, TimeUnit.SECONDS).whenComplete((ignored, failure) -> {
                    if (failure != null) fail(failure);
                    else {
                        try {
                            require(joins.get() == 2 && quits.get() == 0, "same connected clients: joins=" + joins + " quits=" + quits);
                            checks.add("clients: joins=" + joins + " quits=" + quits);
                            Files.write(Path.of(recovery ? "pregen-recovery-checks.txt" : "pregen-checks.txt"), checks);
                            finished = true;
                            plugin.getLogger().info(recovery ? "PREGEN_RECOVERY_PASS" : "PREGEN_PASS");
                            global(() -> { Bukkit.shutdown(); return null; });
                        } catch (Throwable error) { fail(error); }
                    }
                });
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    @EventHandler public void join(PlayerJoinEvent event) { joins.incrementAndGet(); }
    @EventHandler public void quit(PlayerQuitEvent event) {
        quits.incrementAndGet();
        if (!finished) fail(new AssertionError("Client disconnected during pregeneration"));
    }

    @EventHandler public void injectSaveFailure(ChunkLoadEvent event) {
        if (event.getWorld().getUID().equals(failSaveWorld) && event.getChunk().getX() == 320 && event.getChunk().getZ() == 320) {
            try {
                require(Bukkit.isOwnedByCurrentRegion(event.getWorld(), 320, 320), "fault injection is owner-bound");
                Object holder = nativeHolder(event.getWorld(), 320, 320);
                Object chunk = holder.getClass().getMethod("getCurrentChunk").invoke(holder);
                chunk.getClass().getField("mustNotSave").setBoolean(chunk, true);
            } catch (Exception failure) { throw new RuntimeException(failure); }
        }
    }

    private CompletableFuture<List<Player>> awaitPlayers() {
        return poll(() -> {
            List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
            return players.size() == 2 ? players : null;
        });
    }

    private CompletableFuture<Void> exercise(List<Player> players) {
        World world = Bukkit.getWorld("world");
        World nether = Bukkit.getWorld("world_nether");
        require(world != null && nether != null, "default dimensions exist");
        Player one = players.getFirst();
        Player two = players.getLast();
        return owner(one, () -> {
            require(!one.hasPermission("tessera.command.pregen"), "permission is denied for a non-OP");
            one.performCommand("pregen dimension overworld");
            require(pregenerationCommand().tabComplete(one, "pregen", new String[]{"dimension", ""}).isEmpty(), "dimension completion respects permission");
            one.setOp(true);
            require(one.hasPermission("tessera.command.pregen"), "permission defaults to OP");
            one.performCommand("pregen dimension nether");
            one.performCommand("pregen dimension world_nether");
            require(selectedDimension(one).equals(nether.getKey()), "world names are rejected without changing the dimension selection");
            one.performCommand("pregen dimension tessera:missing");
            require(selectedDimension(one).equals(nether.getKey()), "unknown dimension does not change the selection");
            List<String> suggestions = pregenerationCommand().tabComplete(one, "pregen", new String[]{"dimension", ""});
            require(suggestions.containsAll(List.of("overworld", "nether", "end", "minecraft:the_nether"))
                && !suggestions.contains("world_nether"), "dimension completion offers aliases and keys, not folder names");
            one.performCommand("pregen dimension");
            return null;
        }).thenCompose(ignored -> owner(two, () -> {
            two.setOp(true);
            two.performCommand("pregen dimension minecraft:overworld");
            two.performCommand("tessera:pregen radius 48");
            two.performCommand("pregen center 8192 8192");
            two.performCommand("pregen shape square");
            two.performCommand("pregen start");
            two.performCommand("pregen dimension end");
            require(selectedDimension(two).equals(NamespacedKey.minecraft("the_end")), "end alias resolves uniquely");
            require(selectedDimension(one).equals(nether.getKey()), "administrator dimension selections are isolated");
            return null;
        })).thenCompose(ignored -> awaitJob(1, "COMPLETED"))
            .thenCompose(view -> {
                require(number(view, "generated") == 49 && number(view, "inFlight") == 0, "command generates the captured dimension");
                require(text(view, "world").equals("world") && text(view, "dimension").equals("minecraft:overworld"), "selection changes do not retarget a started job");
                checks.add("command-permission-selection: passed; aliases/keys/completion/rejections/isolation; dimension=minecraft:overworld targets=49; retarget prevented");
                return one.teleportAsync(new Location(nether, 2048.5, 100, 2048.5)).thenApply(success -> {
                    require(success, "dimension transfer"); return null;
                });
            }).thenCompose(ignored -> two.teleportAsync(new Location(world, -2048.5, 100, -2048.5)).thenApply(success -> {
                require(success, "separate player region"); return null;
            })).thenCompose(ignored -> global(() -> { command("tick freeze"); return null; }))
            .thenCompose(ignored -> CompletableFuture.allOf(
                startJob(world, 16384, 16384, 64).thenCompose(id -> awaitJob(id, "COMPLETED")),
                startJob(nether, -8192, -8192, 1).thenCompose(id -> awaitJob(id, "COMPLETED"))))
            .thenCompose(ignored -> {
                checks.add("freeze-parallel-dimensions: passed; overworld=81 nether=4; connected players");
                return assertRetired(world, 1024, 1024).thenCompose(nothing -> assertSaved(world, 1024, 1024));
            }).thenCompose(ignored -> startJob(world, 16384, 16384, 64))
            .thenCompose(id -> awaitJob(id, "COMPLETED"))
            .thenCompose(view -> {
                require(number(view, "generated") == 0 && number(view, "existing") == 81, "existing FULL chunks are preserved and counted");
                require(Boolean.TRUE.equals(value(view, "frozen")), "world remained frozen");
                checks.add("existing-full-skip: passed; generated=0 existing=81");
                return startJob(world, 32768, 32768, 256);
            }).thenCompose(id -> operation(id, "mode", enumValue(modeType, "FAST")).thenCompose(ignored -> operation(id, "pause", null))
                .thenCompose(view -> {
                    require(text(view, "state").equals("PAUSED") && number(view, "inFlight") == 0, "pause drains all native work");
                    return operation(id, "resume", null);
                }).thenCompose(ignored -> operation(id, "cancel", null)))
            .thenCompose(view -> {
                require(text(view, "state").equals("CANCELLED") && number(view, "inFlight") == 0, "cancel drains native work without deleting chunks");
                checks.add("mode-pause-resume-cancel: passed");
                return assertStatusAndHistory(number(view, "id")).thenCompose(ignored -> createCustomWorld());
            }).thenCompose(custom -> owner(one, () -> {
                    one.performCommand("pregen dimension " + custom.getKey());
                    require(selectedDimension(one).equals(custom.getKey()), "custom dimension key selects its exact runtime world");
                    one.performCommand("pregen center 4096 4096");
                    one.performCommand("pregen radius 16");
                    one.performCommand("pregen shape square");
                    one.performCommand("pregen start");
                    return null;
                }).thenCompose(ignored -> awaitJob(6, "COMPLETED"))
                .thenApply(view -> {
                    require(text(view, "dimension").equals(custom.getKey().toString()), "custom job exposes the dimension key");
                    return null;
                })
                .thenCompose(ignored -> custom.getChunkAtAsync(256, 256, false))
                .thenCompose(chunk -> region(custom, 256, 256, () -> {
                    require(chunk != null && chunk.getBlock(0, 60, 0).getType() == Material.DIAMOND_BLOCK, "target world's custom generator is preserved");
                    checks.add("custom-world-generator: passed; selected by dimension key; diamond marker at y=60");
                    return null;
                })).thenCompose(ignored -> {
                    failSaveWorld = custom.getUID();
                    return startJob(custom, 5120, 5120, 1).thenCompose(id -> awaitFailedJob(id)
                        .thenCompose(view -> {
                            require(number(view, "inFlight") == 0, "failed save still drains tickets/tasks");
                            require(text(view, "detail").contains("saving"), "native no-save error is reported");
                            failSaveWorld = null;
                            checks.add("expected-save-failure: id=" + id);
                            return operation(id, "resume", null);
                        }).thenCompose(view -> awaitJob(id, "COMPLETED"))
                        .thenAccept(view -> {
                            require(number(view, "generated") + number(view, "existing") == 4, "failed area is fully revalidated");
                            checks.add("save-failure-resume: passed; total=4");
                        }));
                }).thenCompose(ignored -> checkpointFailure(custom))
                .thenCompose(ignored -> startJob(custom, 8192, 8192, 256))
                .thenCompose(id -> Bukkit.getRuntimeWorldManager().unloadWorldAsync(custom,
                    WorldUnloadOptions.builder().timeout(Duration.ofSeconds(90)).build()).toCompletableFuture()
                    .thenCompose(result -> {
                        require(result.successful(), "runtime unload drains pregeneration: " + result);
                        return operation(id, "cancel", null);
                    })).thenAccept(ignored -> checks.add("runtime-unload-with-active-job: passed")))
            .thenCompose(ignored -> startJob(world, 65536, 65536, 256))
            .thenCompose(id -> Bukkit.getRuntimeWorldManager().snapshotWorldsAsync(List.of(world), Path.of("pregen-snapshot").toAbsolutePath())
                .toCompletableFuture().thenCompose(snapshot -> {
                    require(snapshot.successful(), "snapshot coordinates active generation: " + snapshot);
                    checks.add("snapshot-with-active-job: passed");
                    return operation(id, "pause", null);
                }).thenAccept(view -> {
                    require(number(view, "inFlight") == 0, "checkpoint is quiescent");
                    try { Files.writeString(Path.of("pregen-recovery-id.txt"), Long.toString(id)); }
                    catch (Exception failure) { throw new RuntimeException(failure); }
                    checks.add("restart-checkpoint-prepared: id=" + id);
                })).thenCompose(ignored -> startJob(nether, 32768, 32768, 256))
                .thenCompose(id -> pollAsync(() -> call("views").thenApply(raw -> ((List<?>) raw).stream()
                    .filter(view -> number(view, "id") == id && number(view, "inFlight") > 0).findFirst().orElse(null)))
                    .thenAccept(view -> {
                        try { Files.writeString(Path.of("pregen-stop-id.txt"), Long.toString(id)); }
                        catch (Exception failure) { throw new RuntimeException(failure); }
                        checks.add("shutdown-with-active-job: id=" + id);
                    }));
    }

    private CompletableFuture<Void> recover(List<Player> players) {
        try {
            long id = Long.parseLong(Files.readString(Path.of("pregen-recovery-id.txt")));
            long stoppedId = Long.parseLong(Files.readString(Path.of("pregen-stop-id.txt")));
            return awaitJob(stoppedId, "PAUSED").thenCompose(view -> {
                require(number(view, "inFlight") == 0 && number(view, "generated") > 0, "shutdown drained and saved admitted generation");
                checks.add("shutdown-active-drain: passed; generated=" + number(view, "generated"));
                return operation(stoppedId, "cancel", null);
            }).thenCompose(ignored -> awaitJob(id, "PAUSED")).thenCompose(view -> {
                require(number(view, "inFlight") == 0, "recovered job does not auto-start");
                checks.add("restart-paused: passed");
                return global(() -> { command("tick freeze"); return null; });
            }).thenCompose(ignored -> operation(id, "mode", enumValue(modeType, "FAST")))
                .thenCompose(ignored -> operation(id, "resume", null))
                .thenCompose(ignored -> awaitJob(id, "COMPLETED"))
                .thenCompose(view -> {
                    require(number(view, "generated") + number(view, "existing") == 1089, "recovered area is fully revalidated");
                    require(number(view, "inFlight") == 0, "recovery completion cleanup");
                    checks.add("restart-resume-revalidate: passed; total=1089 existing=" + number(view, "existing"));
                    return assertSaved(Bukkit.getWorld("world"), 4096, 4096);
                });
        } catch (Exception failure) { return CompletableFuture.failedFuture(failure); }
    }

    private CompletableFuture<World> createCustomWorld() {
        ChunkGenerator generator = new ChunkGenerator() {
            @Override public boolean isParallelCapable() { return true; }
            @Override public boolean shouldGenerateNoise() { return false; }
            @Override public boolean shouldGenerateSurface() { return false; }
            @Override public boolean shouldGenerateCaves() { return false; }
            @Override public boolean shouldGenerateDecorations() { return false; }
            @Override public boolean shouldGenerateStructures() { return false; }
            @Override public void generateNoise(WorldInfo info, Random random, int x, int z, ChunkData data) {
                data.setBlock(0, 60, 0, Material.DIAMOND_BLOCK);
            }
        };
        return Bukkit.getRuntimeWorldManager().createWorldAsync(WorldCreator.ofKey(new NamespacedKey("tessera_pregen_smoke", "custom"))
            .generator(generator).generateStructures(false)).toCompletableFuture().thenApply(result -> {
                require(result.successful(), "custom runtime create: " + result);
                return java.util.Objects.requireNonNull(result.world());
            });
    }

    private CompletableFuture<Void> assertSaved(World world, int x, int z) {
        return world.getChunkAtAsync(x, z, false).thenCompose(chunk -> region(world, x, z, () -> {
            require(chunk != null && chunk.isGenerated(), "FULL chunk is saved/loadable without generation");
            checks.add("saved-full: " + world.getName() + " " + x + "," + z);
            return null;
        }));
    }

    private CompletableFuture<Void> assertRetired(World world, int x, int z) {
        return poll(() -> {
            try {
                // getChunkHolder reads the concurrent holder table only, not a live chunk.
                Object holder = nativeHolder(world, x, z);
                return holder == null ? Boolean.TRUE : null;
            } catch (Exception failure) { throw new RuntimeException(failure); }
        }).thenAccept(ignored -> checks.add("freeze-ticket-retirement: passed; holder unloaded at " + x + "," + z));
    }

    private static Object nativeHolder(World world, int x, int z) throws Exception {
        Object handle = world.getClass().getMethod("getHandle").invoke(world);
        Object scheduler = handle.getClass().getMethod("moonrise$getChunkTaskScheduler").invoke(handle);
        Object holders = scheduler.getClass().getField("chunkHolderManager").get(scheduler);
        return holders.getClass().getMethod("getChunkHolder", int.class, int.class).invoke(holders, x, z);
    }

    private CompletableFuture<Object> awaitFailedJob(long id) {
        return pollAsync(() -> call("views").thenApply(raw -> ((List<?>) raw).stream()
            .filter(view -> number(view, "id") == id && text(view, "state").equals("FAILED") && number(view, "inFlight") == 0)
            .findFirst().orElse(null)));
    }

    private CompletableFuture<Void> checkpointFailure(World world) {
        return startJob(world, 6144, 6144, 64).thenCompose(id -> operation(id, "pause", null).thenCompose(paused -> {
            Path target = Path.of(".tessera", "pregeneration", id + ".json").toAbsolutePath();
            Path saved = target.resolveSibling(id + ".json.fixture-backup");
            try {
                Files.move(target, saved);
                Files.createDirectory(target);
            } catch (Exception failure) { return CompletableFuture.failedFuture(failure); }
            return operation(id, "resume", null).handle((view, failure) -> {
                require(failure != null, "resume reports rejected checkpoint publication");
                return null;
            }).thenCompose(ignored -> awaitFailedJob(id)).thenCompose(failed -> {
                require(number(failed, "generated") == number(paused, "generated")
                    && number(failed, "existing") == number(paused, "existing") && number(failed, "inFlight") == 0,
                    "failed resume publishes no new native work");
                checks.add("expected-checkpoint-failure: id=" + id);
                try {
                    // Only the empty directory and fixture-owned backup created above are changed.
                    Files.delete(target);
                    Files.move(saved, target);
                } catch (Exception failure) { return CompletableFuture.failedFuture(failure); }
                return operation(id, "resume", null).thenCompose(view -> awaitJob(id, "COMPLETED"));
            }).thenAccept(view -> {
                require(number(view, "generated") + number(view, "existing") == 81, "resume after publication recovery revalidates all targets");
                checks.add("checkpoint-failure-resume: passed; total=81");
            });
        }));
    }

    private CompletableFuture<Long> startJob(World world, int x, int z, int radius) {
        try {
            Object area = areaType.getMethod("centered", shapeType, int.class, int.class, int.class)
                .invoke(null, enumValue(shapeType, "SQUARE"), x, z, radius);
            return call("plan", world.getUID(), area, enumValue(modeType, "BALANCED"))
                .thenCompose(plan -> call("start", plan)).thenApply(view -> number(view, "id"));
        } catch (Exception failure) { return CompletableFuture.failedFuture(failure); }
    }

    private CompletableFuture<Object> operation(long id, String action, Object mode) { return call("control", id, action, mode); }

    private CompletableFuture<Object> awaitJob(long id, String state) {
        return pollAsync(() -> call("views").thenApply(raw -> {
            for (Object view : (List<?>) raw) {
                if (number(view, "id") == id) {
                    if (text(view, "state").equals("FAILED")) throw new AssertionError("Native job failed: " + view);
                    if (text(view, "state").equals(state)) return view;
                }
            }
            return null;
        }));
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<Object> call(String name, Object... arguments) {
        try {
            for (Method method : service.getClass().getMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == arguments.length) {
                    return (CompletableFuture<Object>) method.invoke(service, arguments);
                }
            }
            throw new NoSuchMethodException(name);
        } catch (Exception failure) { return CompletableFuture.failedFuture(failure); }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumValue(Class<?> type, String name) { return Enum.valueOf((Class) type, name); }
    private static Object value(Object record, String name) {
        try { return record.getClass().getMethod(name).invoke(record); }
        catch (Exception failure) { throw new RuntimeException(failure); }
    }
    private static long number(Object record, String name) { return ((Number) value(record, name)).longValue(); }
    private static String text(Object record, String name) { return (String) value(record, name); }

    private <T> CompletableFuture<T> poll(Supplier<T> check) { return pollAsync(() -> global(check)); }
    private <T> CompletableFuture<T> pollAsync(Supplier<CompletableFuture<T>> check) {
        CompletableFuture<T> result = new CompletableFuture<>();
        class Attempt implements Runnable {
            @Override public void run() {
                if (result.isDone()) return;
                try { check.get().whenComplete((value, failure) -> {
                    if (failure != null) result.completeExceptionally(failure);
                    else if (value != null) result.complete(value);
                    else CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS).execute(this);
                }); } catch (Throwable failure) { result.completeExceptionally(failure); }
            }
        }
        new Attempt().run();
        return result.orTimeout(120, TimeUnit.SECONDS);
    }

    private <T> CompletableFuture<T> global(Supplier<T> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> complete(result, action));
        return result;
    }
    private <T> CompletableFuture<T> owner(Player player, Supplier<T> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        if (!player.getScheduler().execute(plugin, () -> complete(result, action),
            () -> result.completeExceptionally(new AssertionError("Player retired")), 1)) {
            result.completeExceptionally(new AssertionError("Entity task refused"));
        }
        return result;
    }
    private <T> CompletableFuture<T> region(World world, int x, int z, Supplier<T> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Bukkit.getRegionScheduler().execute(plugin, world, x, z, () -> complete(result, action));
        return result;
    }
    private static <T> void complete(CompletableFuture<T> result, Supplier<T> action) {
        try { result.complete(action.get()); } catch (Throwable failure) { result.completeExceptionally(failure); }
    }
    private static org.bukkit.command.Command pregenerationCommand() {
        return java.util.Objects.requireNonNull(Bukkit.getCommandMap().getCommand("tessera:pregen"));
    }

    private CompletableFuture<Void> assertStatusAndHistory(long id) {
        return capture("tessera:pregen status", 5).thenCompose(lines -> {
            require(lines.getFirst().contains("Pregeneration #" + id), "status selects newest cancelled job");
            require(lines.get(1).contains("[CANCELLED]") && lines.get(1).contains("FROZEN"), "cancelled status and freeze are explicit");
            require(lines.get(2).contains("Progress:") && lines.get(2).contains(" chunks"), "compact progress line");
            require(lines.get(3).contains("ETA: unavailable"), "cancelled job has no invented ETA");
            require(lines.getLast().equals("[Refresh]"), "finished job has no invalid pause/resume/cancel controls");
            return capture("tessera:pregen status all", 5);
        }).thenCompose(lines -> {
            require(lines.getFirst().contains("Pregeneration #" + id), "status all aliases the newest job, not history");
            return capture("tessera:pregen status 1", 1);
        }).thenCompose(lines -> {
            require(lines.getFirst().contains("No matching current or unfinished"), "older completed history is not displayed");
            checks.add("status-latest-compact: passed; id=" + id + " cancelled; status/all/old-id; 5 lines; applicable controls only");
            return CompletableFuture.runAsync(() -> {
                try (var files = Files.list(Path.of(".tessera", "pregeneration"))) {
                    List<String> checkpoints = files.filter(path -> path.getFileName().toString().endsWith(".json"))
                        .map(path -> path.getFileName().toString()).sorted().toList();
                    require(checkpoints.equals(List.of(id + ".json")), "only newest finished checkpoint remains: " + checkpoints);
                    checks.add("history-retention: passed; only " + id + ".json; generated world chunks preserved");
                } catch (Exception failure) { throw new RuntimeException(failure); }
            });
        }).thenCompose(ignored -> capture("paper:tps server 1", 4)).thenAccept(lines -> {
            require(lines.stream().anyMatch(line -> line.contains("Simulation frozen")), "actual TPS overview preserves freeze label");
            require(lines.stream().anyMatch(line -> line.contains("Utilisation (15 s):") && line.contains(" / 400.00% max")
                && line.contains("Tick threads: 4")), "actual overview exposes the scheduler's four-thread capacity");
            require(lines.stream().noneMatch(line -> line.contains("NaN") || line.contains("Infinity")), "actual TPS totals are finite");
            checks.add("tps-utilisation-capacity: passed; actual command/collector; 400% for four scheduler threads during freeze");
        });
    }

    private CompletableFuture<List<String>> capture(String command, int expectedLines) {
        List<String> lines = new CopyOnWriteArrayList<>();
        CompletableFuture<List<String>> response = new CompletableFuture<>();
        // The real Folia dispatcher accepts this native feedback sender on the global owner.
        var sender = new io.papermc.paper.commands.FeedbackForwardingSender(component -> {
            lines.add(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component));
            if (lines.size() >= expectedLines) response.complete(List.copyOf(lines));
        }, (org.bukkit.craftbukkit.CraftServer) Bukkit.getServer());
        return global(() -> {
            require(Bukkit.dispatchCommand(sender, command), "dispatch " + command);
            return null;
        }).thenCompose(ignored -> response.orTimeout(15, TimeUnit.SECONDS));
    }

    private static NamespacedKey selectedDimension(Player player) {
        try {
            Object command = pregenerationCommand();
            java.lang.reflect.Field players = command.getClass().getDeclaredField("players");
            players.setAccessible(true);
            Object selection = ((java.util.Map<?, ?>) players.get(command)).get(player.getUniqueId());
            require(selection != null, "command selection exists");
            java.lang.reflect.Field world = selection.getClass().getDeclaredField("world");
            world.setAccessible(true);
            synchronized (selection) {
                return java.util.Objects.requireNonNull(Bukkit.getWorld((UUID) world.get(selection))).getKey();
            }
        } catch (ReflectiveOperationException failure) {
            throw new RuntimeException(failure);
        }
    }

    private static void command(String command) { require(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command), "dispatch " + command); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private void fail(Throwable failure) {
        if (finished) return;
        finished = true;
        plugin.getLogger().log(java.util.logging.Level.SEVERE, "PREGEN_FAIL", failure);
        global(() -> { Bukkit.shutdown(); return null; });
    }
}
