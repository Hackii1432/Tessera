package dev.tessera.smoke;

import io.papermc.paper.world.RuntimeWorldManager;
import io.papermc.paper.world.WorldCloneOptions;
import io.papermc.paper.world.WorldCloneResult;
import io.papermc.paper.world.WorldLoadResult;
import io.papermc.paper.world.WorldUnloadOptions;
import io.papermc.paper.world.WorldUnloadResult;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

/**
 * End-to-end smoke test intentionally shipped as a test plugin rather than a
 * unit test. It exercises real region threads and real region-file handles.
 */
public final class RuntimeWorldLifecycleSmokePlugin extends JavaPlugin {

    private static final int MARKER_Y = 70;
    private static volatile long workSink;

    private final ExecutorService fileExecutor = Executors.newSingleThreadExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "Tessera smoke file cleanup");
        thread.setDaemon(true);
        return thread;
    });
    private final List<String> checks = new CopyOnWriteArrayList<>();
    private final Set<String> regionThreads = ConcurrentHashMap.newKeySet();
    private final List<RegionSample> regionSamples = new CopyOnWriteArrayList<>();
    private final AtomicBoolean resultWritten = new AtomicBoolean();
    private final AtomicBoolean stopRequested = new AtomicBoolean();

    private RuntimeWorldManager worlds;
    private String mode;
    private World farming;
    private World template;
    private Path farmingPath;
    private Path templatePath;

    @Override
    public void onEnable() {
        this.mode = System.getenv().getOrDefault("TESSERA_SMOKE_MODE", "full");
        if (!Bukkit.getTesseraCapabilities().supportsRuntimeWorldLifecycle()
            || !Bukkit.getTesseraCapabilities().supportsRegionSafeScoreboards()) {
            this.failAndStop(new AssertionError("Required Tessera capabilities are unavailable"));
            return;
        }
        this.worlds = Bukkit.getRuntimeWorldManager();
        if (this.mode.equals("console-context")) {
            this.getLogger().info("TESSERA_CONSOLE_CONTEXT_PLUGIN_READY");
            return;
        }

        final CompletionStage<Void> run = switch (this.mode) {
            case "full" -> this.runFullSuite();
            case "create-only" -> this.runCreateOnly();
            case "stop-create" -> this.stopDuringCreate();
            case "stop-clone" -> this.stopDuringClone();
            case "stop-unload" -> this.stopDuringUnload();
            default -> CompletableFuture.failedFuture(
                new IllegalArgumentException("Unknown TESSERA_SMOKE_MODE: " + this.mode)
            );
        };

        // Tessera test: the full matrix normally completes in well under one
        // minute after enable. Keep enough room for slow CI disks, but fail a
        // stalled lifecycle in three minutes instead of waiting twelve.
        final long watchdogMinutes = this.mode.equals("full") ? 3L : 2L;
        run.toCompletableFuture()
            .orTimeout(watchdogMinutes, TimeUnit.MINUTES)
            .whenComplete((ignored, failure) -> {
                if (this.mode.startsWith("stop-")) {
                    return;
                }
                if (failure == null) {
                    this.writeResult("PASS", null);
                    this.requestServerStop();
                } else {
                    this.failAndStop(unwrap(failure));
                }
            });
    }

    private CompletionStage<Void> runCreateOnly() {
        return this.requireLoad(
            this.worlds.createWorldAsync(
                WorldCreator.ofKey(this.key("smoke_create_only")).type(WorldType.FLAT)
            ),
            "create-only world"
        ).thenCompose(world -> this.unloadAndDelete(world, world.getWorldPath(), false));
    }

    @Override
    public void onDisable() {
        if (this.stopRequested.get() && this.mode.startsWith("stop-")) {
            this.writeResult("STOP_REQUESTED", null);
        }
        this.fileExecutor.shutdownNow();
    }

    private CompletionStage<Void> runFullSuite() {
        return this.testProtectedWorld()
            .thenCompose(ignored -> this.testIncompleteWorldFolder())
            .thenRun(this::testScoreboardModel)
            .thenCompose(ignored -> this.createFarmingWorld())
            .thenCompose(ignored -> this.testDuplicateAndChunkGeneration())
            .thenCompose(ignored -> this.createTemplateWorld())
            .thenCompose(ignored -> this.populateTemplate())
            .thenCompose(ignored -> this.unloadTemplateForReload())
            .thenCompose(ignored -> this.rejectFlattenedTemplateIdentity())
            .thenCompose(ignored -> this.reloadTemplate())
            .thenCompose(ignored -> this.verifyTemplate(this.template))
            .thenCompose(ignored -> this.runCloneBatch(2))
            .thenCompose(worlds -> this.verifyAndDeleteCloneBatch(worlds, false))
            .thenCompose(ignored -> this.runCloneBatch(4))
            .thenCompose(worlds -> this.verifyAndDeleteCloneBatch(worlds, false))
            .thenCompose(ignored -> this.runCloneBatch(16))
            .thenCompose(this::verifyLargeCloneBatch)
            .thenCompose(ignored -> this.testOnlinePlayerLifecycle())
            .thenCompose(ignored -> this.finalTemplateUnloadAndDelete())
            .thenCompose(ignored -> this.unloadAndDelete(this.farming, this.farmingPath, true))
            .thenCompose(ignored -> this.runCycles(0));
    }

    private CompletionStage<Void> testProtectedWorld() {
        final World primary = Bukkit.getWorlds().getFirst();
        return this.worlds.unloadWorldAsync(primary, WorldUnloadOptions.defaults())
            .thenAccept(result -> this.check(
                result.status() == WorldUnloadResult.Status.PROTECTED_WORLD,
                "protected startup world",
                result.toString()
            ));
    }

    private CompletionStage<Void> testIncompleteWorldFolder() {
        final WorldCreator creator = WorldCreator.ofKey(this.key("smoke_incomplete"));
        final Path primaryDimension = Bukkit.getWorlds().getFirst().getWorldPath();
        final Path primaryRoot = primaryDimension.getParent().getParent().getParent();
        final Path folder = primaryRoot.resolve("dimensions")
            .resolve(creator.key().getNamespace())
            .resolve(creator.key().getKey());
        return CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(folder);
                Files.writeString(folder.resolve("not-level.dat"), "incomplete");
            } catch (final IOException exception) {
                throw new RuntimeException(exception);
            }
        }, this.fileExecutor).thenCompose(ignored ->
            this.worlds.loadWorldAsync(creator)
        ).thenCompose(result -> {
            this.check(
                result.status() == WorldLoadResult.Status.INCOMPLETE_WORLD,
                "incomplete folder rejected",
                result.toString()
            );
            return this.deleteWorldPath(folder);
        });
    }

    private void testScoreboardModel() {
        final ScoreboardManager manager = Objects.requireNonNull(Bukkit.getScoreboardManager());
        final Scoreboard board = manager.getNewScoreboard();
        this.check(board != manager.getMainScoreboard(), "independent scoreboard", "model was aliased");
        final Objective objective = board.registerNewObjective(
            "mcc",
            Criteria.DUMMY,
            Component.text("MAB smoke")
        );
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        for (int line = 0; line < 15; ++line) {
            final String suffix = String.format("%02d", line);
            final String entry = "smoke_line_" + suffix;
            final Team team = board.registerNewTeam("line_" + suffix);
            team.addEntry(entry);
            team.prefix(Component.text("line " + line));
            objective.getScore(entry).setScore(15 - line);
        }
        this.check(board.getTeams().size() == 15, "15-line scoreboard", "wrong team count");
        board.resetScores("smoke_line_14");
        this.check(
            board.getScores("smoke_line_14").stream().noneMatch(score -> score.isScoreSet()),
            "score reset",
            "score remained set"
        );
    }

    private CompletionStage<Void> createFarmingWorld() {
        final WorldCreator creator = WorldCreator.ofKey(this.key("smoke_farming"))
            .type(WorldType.NORMAL)
            .generateStructures(true);
        return this.requireLoad(this.worlds.createWorldAsync(creator), "create normal farming world")
            .thenAccept(world -> {
                this.farming = world;
                this.farmingPath = world.getWorldPath();
            });
    }

    private CompletionStage<Void> testDuplicateAndChunkGeneration() {
        return this.worlds.createWorldAsync(WorldCreator.ofKey(this.key("smoke_farming")))
            .thenCompose(result -> {
                this.check(
                    result.status() == WorldLoadResult.Status.ALREADY_LOADED,
                    "duplicate registration rejected",
                    result.toString()
                );
                return this.farming.getChunkAtAsync(128, 128, true);
            })
            .thenAccept(chunk -> this.check(
                chunk.getWorld() == this.farming,
                "normal chunk generation",
                "chunk belongs to another world"
            ));
    }

    private CompletionStage<Void> createTemplateWorld() {
        final WorldCreator creator = WorldCreator.ofKey(this.key("smoke_template"))
            .type(WorldType.FLAT)
            .generateStructures(true);
        return this.requireLoad(this.worlds.createWorldAsync(creator), "create flat template")
            .thenAccept(world -> {
                this.template = world;
                this.templatePath = world.getWorldPath();
            });
    }

    private CompletionStage<Void> populateTemplate() {
        return this.template.getChunkAtAsync(0, 0, true)
            .thenCompose(ignored -> this.onRegion(this.template, () -> {
                this.template.getBlockAt(0, MARKER_Y, 0).setType(Material.CHEST);
                final Chest chest = (Chest)this.template.getBlockAt(0, MARKER_Y, 0).getState();
                chest.getBlockInventory().setItem(0, new ItemStack(Material.DIAMOND, 7));
                this.template.getBlockAt(2, MARKER_Y, 0).setType(Material.LECTERN);

                this.template.spawn(
                    new Location(this.template, 4.5D, MARKER_Y + 1.0D, 0.5D),
                    Zombie.class,
                    zombie -> {
                        zombie.setAI(false);
                        zombie.setGravity(false);
                        zombie.setInvulnerable(true);
                        zombie.setPersistent(true);
                        zombie.addScoreboardTag("mab-wave");
                    }
                );
                this.template.spawn(
                    new Location(this.template, 6.5D, MARKER_Y + 1.0D, 0.5D),
                    Villager.class,
                    villager -> {
                        villager.setAI(false);
                        villager.setGravity(false);
                        villager.setInvulnerable(true);
                        villager.setPersistent(true);
                    }
                );
            }))
            .thenCompose(ignored -> this.onGlobal(() -> {
                this.template.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                this.template.getWorldBorder().setSize(512.0D);
            }));
    }

    private CompletionStage<Void> unloadTemplateForReload() {
        return this.worlds.unloadWorldAsync(
            this.template,
            WorldUnloadOptions.builder().save(true).failWhenPlayersPresent().build()
        ).thenAccept(result -> this.check(
            result.successful(),
            "template save/unload",
            result.toString()
        ));
    }

    private CompletionStage<Void> reloadTemplate() {
        return this.requireLoad(
            this.worlds.loadWorldAsync(this.key("smoke_template")),
            "load existing template"
        ).thenAccept(world -> this.template = world);
    }

    @SuppressWarnings("UnstableApiUsage")
    private CompletionStage<Void> rejectFlattenedTemplateIdentity() {
        return this.worlds.loadWorldAsync(new WorldCreator(this.template.getName()))
            .thenAccept(result -> this.check(
                result.status() == WorldLoadResult.Status.IDENTITY_MISMATCH,
                "flattened display name rejected with stable-key diagnostic",
                result.toString()
            ));
    }

    private CompletionStage<Void> verifyTemplate(final World world) {
        return world.getChunkAtAsync(0, 0, true)
            .thenCompose(ignored -> this.onRegion(world, () -> {
                this.check(
                    world.getBlockAt(0, MARKER_Y, 0).getState() instanceof Chest,
                    "block entity reload",
                    "chest missing"
                );
                final Chest chest = (Chest)world.getBlockAt(0, MARKER_Y, 0).getState();
                final ItemStack marker = chest.getBlockInventory().getItem(0);
                this.check(
                    marker != null && marker.getType() == Material.DIAMOND && marker.getAmount() == 7,
                    "block entity data reload",
                    String.valueOf(marker)
                );
                this.check(
                    world.getBlockAt(2, MARKER_Y, 0).getType() == Material.LECTERN,
                    "POI block reload",
                    "lectern missing"
                );
                final List<org.bukkit.entity.Entity> persistent = world.getNearbyEntities(
                    new Location(world, 4.5D, MARKER_Y + 1.0D, 0.5D),
                    16.0D,
                    16.0D,
                    16.0D
                ).stream().filter(entity -> entity instanceof Zombie || entity instanceof Villager).toList();
                final long zombies = persistent.stream().filter(Zombie.class::isInstance).count();
                final long villagers = persistent.stream().filter(Villager.class::isInstance).count();
                this.check(
                    zombies >= 1L && villagers >= 1L,
                    "entity storage reload",
                    "zombies=" + zombies + ", villagers=" + villagers
                );
            }));
    }

    private CompletionStage<List<World>> runCloneBatch(final int count) {
        final List<CompletionStage<WorldCloneResult>> operations = new ArrayList<>(count);
        for (int index = 0; index < count; ++index) {
            operations.add(this.worlds.cloneWorldAsync(
                this.template,
                this.key("smoke_b" + count + "_arena_" + index),
                WorldCloneOptions.defaults()
            ));
        }
        final CompletableFuture<?>[] futures = operations.stream()
            .map(CompletionStage::toCompletableFuture)
            .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures).thenApply(ignored -> {
            final List<World> clones = new ArrayList<>(count);
            for (final CompletionStage<WorldCloneResult> operation : operations) {
                final WorldCloneResult result = operation.toCompletableFuture().join();
                this.check(result.successful(), count + "-arena clone", result.toString());
                clones.add(Objects.requireNonNull(result.world()));
            }
            return clones;
        });
    }

    private CompletionStage<Void> verifyAndDeleteCloneBatch(
        final List<World> clones,
        final boolean proveParallelism
    ) {
        final CompletableFuture<?>[] verifications = clones.stream()
            .map(this::verifyTemplate)
            .map(CompletionStage::toCompletableFuture)
            .toArray(CompletableFuture[]::new);
        CompletionStage<Void> verified = CompletableFuture.allOf(verifications);
        if (proveParallelism) {
            verified = verified.thenCompose(ignored -> this.proveParallelRegionTicks(clones));
        }
        return verified.thenCompose(ignored -> {
            if (clones.size() >= 2) {
                return this.verifyArenaIsolation(clones.get(0), clones.get(1));
            }
            return CompletableFuture.completedFuture(null);
        }).thenCompose(ignored -> this.unloadAndDeleteAll(clones));
    }

    private CompletionStage<Void> verifyLargeCloneBatch(final List<World> clones) {
        return this.verifyAndDeleteCloneBatch(clones, true);
    }

    private CompletionStage<Void> verifyArenaIsolation(final World first, final World second) {
        return this.onGlobal(() -> {
            first.setChunkForceLoaded(0, 0, true);
            second.setChunkForceLoaded(0, 0, true);
            first.getWorldBorder().setSize(100.0D);
            first.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
            second.getWorldBorder().setSize(200.0D);
            second.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            this.check(
                Boolean.TRUE.equals(first.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE))
                    && Boolean.FALSE.equals(second.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE)),
                "independent gamerules",
                "first=" + first.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE)
                    + ", second=" + second.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE)
            );
            this.check(
                first.getWorldBorder().getSize() == 100.0D
                    && second.getWorldBorder().getSize() == 200.0D,
                "independent world borders",
                "first=" + first.getWorldBorder().getSize()
                    + ", second=" + second.getWorldBorder().getSize()
            );
        }).thenCompose(ignored -> this.onRegion(first, () -> {
            first.spawn(
                new Location(first, 8.5D, MARKER_Y + 1.0D, 0.5D),
                Zombie.class,
                zombie -> {
                    zombie.setAI(false);
                    zombie.setGravity(false);
                    zombie.setInvulnerable(true);
                    zombie.setPersistent(true);
                }
            );
        })).thenCompose(ignored -> second.getChunkAtAsync(0, 0, true))
        .thenCompose(ignored -> this.onRegion(second, () -> {
            final long secondZombies = this.countZombies(second);
            this.check(secondZombies == 1L, "independent entity state", "second zombies=" + secondZombies);
        })).thenCompose(ignored -> first.getChunkAtAsync(0, 0, true))
        .thenCompose(ignored -> this.onRegion(first, () -> {
            final long firstZombies = this.countZombies(first);
            this.check(firstZombies >= 2L, "independent wave state", "first zombies=" + firstZombies);
        })).thenCompose(ignored -> this.onGlobal(() -> {
            first.setChunkForceLoaded(0, 0, false);
            second.setChunkForceLoaded(0, 0, false);
        }));
    }

    private long countZombies(final World world) {
        return world.getNearbyEntities(
            new Location(world, 4.5D, MARKER_Y + 1.0D, 0.5D),
            16.0D,
            16.0D,
            16.0D
        ).stream().filter(Zombie.class::isInstance).count();
    }

    private CompletionStage<Void> proveParallelRegionTicks(final List<World> clones) {
        CompletionStage<Void> rounds = CompletableFuture.completedFuture(null);
        for (int round = 0; round < 8; ++round) {
            rounds = rounds.thenCompose(ignored -> {
                final CompletableFuture<?>[] samples = clones.stream()
                    .map(world -> this.onRegion(world, this::recordRegionSample))
                    .map(CompletionStage::toCompletableFuture)
                    .toArray(CompletableFuture[]::new);
                return CompletableFuture.allOf(samples);
            });
        }
        return rounds.thenRun(() -> {
            this.check(
                this.regionThreads.size() >= 2,
                "multiple region tick threads",
                this.regionThreads.toString()
            );
            boolean overlap = false;
            for (int left = 0; left < this.regionSamples.size() && !overlap; ++left) {
                final RegionSample first = this.regionSamples.get(left);
                for (int right = left + 1; right < this.regionSamples.size(); ++right) {
                    final RegionSample second = this.regionSamples.get(right);
                    if (!first.thread.equals(second.thread)
                        && first.started < second.finished
                        && second.started < first.finished) {
                        overlap = true;
                        break;
                    }
                }
            }
            this.check(overlap, "parallel region execution", this.regionThreads.toString());
        });
    }

    private void recordRegionSample() {
        final String thread = Thread.currentThread().getName();
        final long started = System.nanoTime();
        long value = workSink ^ started;
        for (int index = 0; index < 300_000; ++index) {
            value = (value * 6364136223846793005L) + index;
        }
        workSink = value;
        final long finished = System.nanoTime();
        this.regionThreads.add(thread);
        this.regionSamples.add(new RegionSample(thread, started, finished));
    }

    private CompletionStage<Void> testOnlinePlayerLifecycle() {
        final Player player = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (player == null) {
            this.checks.add("SKIP interactive player lifecycle (no player connected)");
            return CompletableFuture.completedFuture(null);
        }

        final Scoreboard previous = player.getScoreboard();
        final Scoreboard sidebar = Objects.requireNonNull(Bukkit.getScoreboardManager()).getNewScoreboard();
        final Objective objective = sidebar.registerNewObjective(
            "interactive",
            Criteria.DUMMY,
            Component.text("MAB interactive")
        );
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        final Team team = sidebar.registerNewTeam("timer");
        team.addEntry("timer_entry");
        team.prefix(Component.text("00:30"));
        objective.getScore("timer_entry").setScore(15);

        return this.onEntity(player, () -> {
            player.setScoreboard(sidebar);
            player.addScoreboardTag("tessera-smoke");
        }).thenCompose(ignored -> player.teleportAsync(this.template.getSpawnLocation()))
            .thenCompose(teleported -> {
                this.check(teleported, "async teleport into arena", "teleport returned false");
                return this.worlds.unloadWorldAsync(
                    this.template,
                    WorldUnloadOptions.builder().save(true).failWhenPlayersPresent().build()
                );
            })
            .thenCompose(result -> {
                this.check(
                    result.status() == WorldUnloadResult.Status.PLAYERS_PRESENT,
                    "unload with player rejected",
                    result.toString()
                );
                final World primary = Bukkit.getWorlds().getFirst();
                return player.teleportAsync(primary.getSpawnLocation());
            })
            .thenCompose(teleported -> {
                this.check(teleported, "async teleport out of arena", "teleport returned false");
                return this.onEntity(player, () -> {
                    this.check(player.getScoreboard() == sidebar, "player scoreboard applied", "wrong board");
                    this.check(
                        player.getScoreboardTags().contains("tessera-smoke"),
                        "entity scoreboard tag",
                        player.getScoreboardTags().toString()
                    );
                    player.removeScoreboardTag("tessera-smoke");
                    player.setScoreboard(previous);
                });
            });
    }

    private CompletionStage<Void> finalTemplateUnloadAndDelete() {
        final World unloaded = this.template;
        return this.worlds.unloadWorldAsync(
            unloaded,
            WorldUnloadOptions.builder().save(false).failWhenPlayersPresent().build()
        ).thenCompose(result -> {
            this.check(result.successful(), "template no-save unload", result.toString());
            return this.worlds.unloadWorldAsync(unloaded, WorldUnloadOptions.defaults());
        }).thenCompose(second -> {
            this.check(
                second.status() == WorldUnloadResult.Status.NOT_LOADED,
                "double unload rejected",
                second.toString()
            );
            return this.deleteWorldPath(this.templatePath);
        });
    }

    private CompletionStage<Void> runCycles(final int cycle) {
        if (cycle >= 20) {
            this.checks.add("PASS 20 create/load/unload cycles");
            return CompletableFuture.completedFuture(null);
        }
        final NamespacedKey key = this.key("smoke_cycle_" + cycle);
        return this.requireLoad(
            this.worlds.createWorldAsync(WorldCreator.ofKey(key).type(WorldType.FLAT)),
            "cycle " + cycle + " create"
        ).thenCompose(world -> {
            final Path path = world.getWorldPath();
            return this.unloadAndDelete(world, path, cycle % 2 == 0);
        }).thenCompose(ignored -> this.runCycles(cycle + 1));
    }

    private CompletionStage<Void> unloadAndDeleteAll(final List<World> worlds) {
        final List<Path> paths = worlds.stream().map(World::getWorldPath).toList();
        final CompletableFuture<?>[] unloads = worlds.stream()
            .map(world -> this.worlds.unloadWorldAsync(
                world,
                WorldUnloadOptions.builder().save(false).failWhenPlayersPresent().build()
            ).thenAccept(result -> this.check(
                result.successful(),
                "clone unload",
                result.toString()
            )))
            .map(CompletionStage::toCompletableFuture)
            .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(unloads).thenCompose(ignored -> {
            final CompletableFuture<?>[] deletions = paths.stream()
                .map(this::deleteWorldPath)
                .map(CompletionStage::toCompletableFuture)
                .toArray(CompletableFuture[]::new);
            return CompletableFuture.allOf(deletions);
        });
    }

    private CompletionStage<Void> unloadAndDelete(
        final World world,
        final Path path,
        final boolean save
    ) {
        return this.worlds.unloadWorldAsync(
            world,
            WorldUnloadOptions.builder()
                .save(save)
                .failWhenPlayersPresent()
                .timeout(Duration.ofSeconds(45L))
                .build()
        ).thenCompose(result -> {
            this.check(result.successful(), "world unload", result.toString());
            return this.deleteWorldPath(path);
        });
    }

    private CompletionStage<Void> deleteWorldPath(final Path path) {
        return CompletableFuture.runAsync(() -> {
            final Path container = Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize();
            final Path target = path.toAbsolutePath().normalize();
            if (!target.startsWith(container)
                || target.equals(container)
                || !target.getFileName().toString().contains("smoke")) {
                throw new IllegalStateException("Refusing unsafe smoke deletion: " + target);
            }
            try {
                if (Files.notExists(target)) {
                    return;
                }
                try (Stream<Path> entries = Files.walk(target)) {
                    for (final Path entry : entries.sorted(Comparator.reverseOrder()).toList()) {
                        Files.delete(entry);
                    }
                }
                this.check(Files.notExists(target), "Windows handle release", target.toString());
            } catch (final IOException exception) {
                throw new RuntimeException("Could not delete unloaded world " + target, exception);
            }
        }, this.fileExecutor);
    }

    private CompletionStage<World> requireLoad(
        final CompletionStage<WorldLoadResult> operation,
        final String label
    ) {
        return operation.thenApply(result -> {
            this.check(result.successful(), label, result.toString());
            final World world = Objects.requireNonNull(result.world());
            this.check(
                Objects.equals(result.worldKey(), world.getKey()),
                label + " result key",
                String.valueOf(result.worldKey())
            );
            this.check(
                Objects.equals(result.worldPath(), world.getWorldPath()),
                label + " result path",
                String.valueOf(result.worldPath())
            );
            return world;
        });
    }

    @Override
    public boolean onCommand(
        final CommandSender sender,
        final Command command,
        final String label,
        final String[] args
    ) {
        if (!command.getName().equals("tessera-console-context")) {
            return false;
        }
        if (args.length == 6 && args[0].equals("fixture")) {
            return this.prepareSelectorFixture(sender, args);
        }
        if (args.length == 5 && args[0].equals("block-fixture")) {
            return this.prepareBlockFixture(sender, args);
        }
        final String marker = "TESSERA_CONSOLE_CONTEXT_OK sender="
            + sender.getClass().getSimpleName()
            + " args="
            + String.join(",", args);
        this.getLogger().info(marker);
        sender.sendMessage(marker);
        return true;
    }

    private boolean prepareSelectorFixture(final CommandSender sender, final String[] args) {
        final NamespacedKey worldKey = NamespacedKey.fromString(args[1]);
        final World world = worldKey == null ? null : Bukkit.getWorld(worldKey);
        if (world == null) {
            sender.sendMessage("Unknown fixture world: " + args[1]);
            return true;
        }

        final double x;
        final double y;
        final double z;
        final int count;
        try {
            x = Double.parseDouble(args[2]);
            y = Double.parseDouble(args[3]);
            z = Double.parseDouble(args[4]);
            count = Integer.parseInt(args[5]);
        } catch (final NumberFormatException invalidNumber) {
            sender.sendMessage("Invalid selector fixture coordinates/count");
            return true;
        }

        final int chunkX = ((int)Math.floor(x)) >> 4;
        final int chunkZ = ((int)Math.floor(z)) >> 4;
        world.getChunkAtAsync(chunkX, chunkZ, true).whenComplete((chunk, loadFailure) -> {
            if (loadFailure != null) {
                this.getLogger().severe("TESSERA_SELECTOR_FIXTURE_FAILED world=" + args[1] + " error=" + loadFailure);
                return;
            }
            chunk.addPluginChunkTicket(this);
            Bukkit.getRegionScheduler().execute(this, world, chunkX, chunkZ, () -> {
                try {
                    final Location location = new Location(world, x, y, z);
                    for (int i = 0; i < count; ++i) {
                        world.spawn(location, ArmorStand.class, armorStand -> {
                            armorStand.setGravity(false);
                            armorStand.setMarker(true);
                            armorStand.addScoreboardTag("tessera_selector_fixture");
                        });
                    }
                    final String ready = "TESSERA_SELECTOR_FIXTURE_READY world=" + args[1]
                        + " chunk=" + chunkX + "," + chunkZ + " count=" + count;
                    this.getLogger().info(ready);
                    sender.sendMessage(ready);
                } catch (final Throwable fixtureFailure) {
                    this.getLogger().severe("TESSERA_SELECTOR_FIXTURE_FAILED world=" + args[1] + " error=" + fixtureFailure);
                }
            });
        });
        return true;
    }

    private boolean prepareBlockFixture(final CommandSender sender, final String[] args) {
        final NamespacedKey worldKey = NamespacedKey.fromString(args[1]);
        final World world = worldKey == null ? null : Bukkit.getWorld(worldKey);
        if (world == null) {
            sender.sendMessage("Unknown block fixture world: " + args[1]);
            return true;
        }

        final int x;
        final int y;
        final int z;
        try {
            x = Integer.parseInt(args[2]);
            y = Integer.parseInt(args[3]);
            z = Integer.parseInt(args[4]);
        } catch (final NumberFormatException invalidNumber) {
            sender.sendMessage("Invalid block fixture coordinates");
            return true;
        }

        final int chunkX = x >> 4;
        final int chunkZ = z >> 4;
        world.getChunkAtAsync(chunkX, chunkZ, true).whenComplete((chunk, loadFailure) -> {
            if (loadFailure != null) {
                this.getLogger().severe("TESSERA_BLOCK_FIXTURE_FAILED world=" + args[1] + " error=" + loadFailure);
                return;
            }
            chunk.addPluginChunkTicket(this);
            Bukkit.getRegionScheduler().execute(this, world, chunkX, chunkZ, () -> {
                try {
                    world.getBlockAt(x, y, z).setType(Material.STONE, false);
                    world.getBlockAt(x + 1, y, z).setType(Material.CHEST, false);
                    final String ready = "TESSERA_BLOCK_FIXTURE_READY world=" + args[1]
                        + " block=" + x + "," + y + "," + z;
                    this.getLogger().info(ready);
                    sender.sendMessage(ready);
                } catch (final Throwable fixtureFailure) {
                    this.getLogger().severe("TESSERA_BLOCK_FIXTURE_FAILED world=" + args[1] + " error=" + fixtureFailure);
                }
            });
        });
        return true;
    }

    private CompletionStage<Void> onGlobal(final Runnable operation) {
        final CompletableFuture<Void> result = new CompletableFuture<>();
        try {
            Bukkit.getGlobalRegionScheduler().execute(this, () -> {
                try {
                    operation.run();
                    result.complete(null);
                } catch (final Throwable throwable) {
                    result.completeExceptionally(throwable);
                }
            });
        } catch (final Throwable throwable) {
            result.completeExceptionally(throwable);
        }
        return result;
    }

    private CompletionStage<Void> onRegion(final World world, final Runnable operation) {
        final CompletableFuture<Void> result = new CompletableFuture<>();
        try {
            Bukkit.getRegionScheduler().execute(this, world, 0, 0, () -> {
                try {
                    operation.run();
                    result.complete(null);
                } catch (final Throwable throwable) {
                    result.completeExceptionally(throwable);
                }
            });
        } catch (final Throwable throwable) {
            result.completeExceptionally(throwable);
        }
        return result;
    }

    private CompletionStage<Void> onEntity(final Player player, final Runnable operation) {
        final CompletableFuture<Void> result = new CompletableFuture<>();
        final boolean accepted = player.getScheduler().execute(
            this,
            () -> {
                try {
                    operation.run();
                    result.complete(null);
                } catch (final Throwable throwable) {
                    result.completeExceptionally(throwable);
                }
            },
            () -> result.completeExceptionally(new IllegalStateException("Player retired")),
            1L
        );
        if (!accepted) {
            result.completeExceptionally(new IllegalStateException("Player scheduler already retired"));
        }
        return result;
    }

    private CompletionStage<Void> stopDuringCreate() {
        this.worlds.createWorldAsync(WorldCreator.ofKey(this.key("smoke_stop_create")));
        this.scheduleStop();
        return new CompletableFuture<>();
    }

    private CompletionStage<Void> stopDuringClone() {
        return this.requireLoad(
            this.worlds.createWorldAsync(
                WorldCreator.ofKey(this.key("smoke_stop_clone_source")).type(WorldType.FLAT)
            ),
            "stop-clone source"
        ).thenCompose(source -> {
            this.worlds.cloneWorldAsync(
                source,
                this.key("smoke_stop_clone_target"),
                WorldCloneOptions.defaults()
            );
            this.scheduleStop();
            return new CompletableFuture<>();
        });
    }

    private CompletionStage<Void> stopDuringUnload() {
        return this.requireLoad(
            this.worlds.createWorldAsync(
                WorldCreator.ofKey(this.key("smoke_stop_unload")).type(WorldType.FLAT)
            ),
            "stop-unload source"
        ).thenCompose(world -> {
            this.worlds.unloadWorldAsync(
                world,
                WorldUnloadOptions.builder().save(true).failWhenPlayersPresent().build()
            );
            this.scheduleStop();
            return new CompletableFuture<>();
        });
    }

    private void scheduleStop() {
        this.stopRequested.set(true);
        Bukkit.getGlobalRegionScheduler().runDelayed(this, ignored -> Bukkit.shutdown(), 1L);
    }

    private void requestServerStop() {
        this.stopRequested.set(true);
        Bukkit.getGlobalRegionScheduler().execute(this, Bukkit::shutdown);
    }

    private void failAndStop(final Throwable failure) {
        this.getLogger().severe("Tessera smoke test failed: " + failure);
        failure.printStackTrace();
        this.writeResult("FAIL", failure);
        this.requestServerStop();
    }

    private void check(final boolean condition, final String label, final String detail) {
        if (!condition) {
            throw new AssertionError(label + ": " + detail);
        }
        this.checks.add("PASS " + label);
        this.getLogger().info("PASS " + label);
    }

    private NamespacedKey key(final String value) {
        return new NamespacedKey(this, value);
    }

    private void writeResult(final String status, final Throwable failure) {
        if (!this.resultWritten.compareAndSet(false, true)) {
            return;
        }
        final String failureText;
        if (failure == null) {
            failureText = "";
        } else {
            final StringWriter writer = new StringWriter();
            failure.printStackTrace(new PrintWriter(writer));
            failureText = writer.toString();
        }
        final String json = "{\n"
            + "  \"status\":\"" + escape(status) + "\",\n"
            + "  \"mode\":\"" + escape(this.mode) + "\",\n"
            + "  \"checks\":" + this.checks.size() + ",\n"
            + "  \"regionThreads\":\"" + escape(this.regionThreads.toString()) + "\",\n"
            + "  \"failure\":\"" + escape(failureText) + "\"\n"
            + "}\n";
        try {
            Files.writeString(Path.of("tessera-smoke-result.json"), json);
        } catch (final IOException exception) {
            this.getLogger().severe("Could not write smoke result: " + exception);
        }
    }

    private static String escape(final String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n");
    }

    private static Throwable unwrap(final Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof java.util.concurrent.CompletionException
            || current instanceof java.util.concurrent.ExecutionException)
            && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private record RegionSample(String thread, long started, long finished) {
    }
}
