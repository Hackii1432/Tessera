package dev.tessera.smoke;

import io.papermc.paper.world.WorldUnloadOptions;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Actual Brigadier execution, region owners, persistent stores and connected protocol clients. */
final class NativeDataCommandSmoke {
    private static final Identifier SHARED = Identifier.fromNamespaceAndPath("tessera_smoke", "data");
    private static final Identifier DURABLE = Identifier.fromNamespaceAndPath("tessera_smoke", "durable");
    private final JavaPlugin plugin;
    private final boolean restart;
    private final List<String> checks = new CopyOnWriteArrayList<>();
    private final Set<Object> owners = ConcurrentHashMap.newKeySet();
    private final java.util.Map<Integer, UUID> stands = new ConcurrentHashMap<>();
    private World world;

    NativeDataCommandSmoke(JavaPlugin plugin, boolean restart) {
        this.plugin = plugin;
        this.restart = restart;
    }

    void start() {
        this.plugin.getLogger().info("DATA_READY_CLIENTS");
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(this.plugin, task -> {
            if (Bukkit.getOnlinePlayers().size() < 2) return;
            task.cancel();
            (this.restart ? verifyRestart() : begin()).orTimeout(220, TimeUnit.SECONDS).whenComplete((ignored, failure) -> {
                if (failure != null) {
                    this.plugin.getLogger().log(java.util.logging.Level.SEVERE, "DATA_FAIL", failure);
                } else {
                    this.checks.add("clients: two connected protocol clients, no restore/reconnect requested");
                    try {
                        Files.writeString(Path.of(this.restart ? "data-restart-checks.txt" : "data-checks.txt"), String.join("\n", this.checks));
                        this.plugin.getLogger().info(this.restart ? "DATA_RESTART_PASS" : "DATA_PASS");
                    } catch (Exception error) {
                        this.plugin.getLogger().log(java.util.logging.Level.SEVERE, "DATA_FAIL", error);
                    }
                }
                Bukkit.getGlobalRegionScheduler().execute(this.plugin, Bukkit::shutdown);
            });
        }, 1, 5);
    }

    private CompletableFuture<Void> begin() {
        NamespacedKey key = new NamespacedKey("tessera_smoke", "data");
        return Bukkit.getRuntimeWorldManager().createWorldAsync(WorldCreator.ofKey(key).type(WorldType.FLAT).generateStructures(false))
            .thenCompose(result -> {
                require(result.successful(), "create runtime test dimension: " + result);
                this.world = java.util.Objects.requireNonNull(result.world());
                return CompletableFuture.allOf(prepare(0), prepare(128));
            }).thenCompose(ignored -> {
                require(this.owners.size() == 2, "two actual independent region owners");
                this.checks.add("parallel-regions: two distinct owners, chunks 0 and 128");
                List<Player> players = testPlayers();
                return CompletableFuture.allOf(movePlayer(players.get(0), new Location(this.world, 8, 74, 8)),
                    movePlayer(players.get(1), new Location(this.world, 2056, 74, 8)));
            }).thenCompose(ignored -> {
                this.plugin.getLogger().info("DATA_READY_RCON " + this.world.getKey());
                return waitForRcon();
            }).thenCompose(ignored -> command("tick freeze", true))
            .thenCompose(ignored -> CompletableFuture.allOf(blockCommands(0), blockCommands(128)))
            .thenCompose(ignored -> storageCommands())
            .thenCompose(ignored -> CompletableFuture.allOf(parallelWrites(0), parallelWrites(128)))
            .thenCompose(ignored -> {
                CompoundTag data = server().getCommandStorage().get(SHARED);
                require(data.getIntOr("region0", -1) == 150 && data.getIntOr("region128", -1) == 150,
                    "parallel whole-document updates preserved both owners' fields: " + data);
                this.checks.add("storage-parallel: 300 actual command writes, both final values 150");
                return ownershipRejections();
            }).thenCompose(ignored -> command("tick unfreeze", true))
            .thenCompose(ignored -> playerReads())
            .thenCompose(ignored -> command("data merge storage tessera_smoke:durable {marker:17017,removed:1}", true))
            .thenCompose(ignored -> command("data remove storage tessera_smoke:durable removed", true))
            .thenCompose(ignored -> global(() -> {
                // Real root storage save and snapshot paths, detached encoding while later writes remain dirty.
                return server().tessera$beginLevelRootSnapshot(java.util.concurrent.ForkJoinPool.commonPool());
            })).thenCompose(future -> future)
            .thenCompose(ignored -> global(() -> { server().tessera$endLevelRootSnapshot(); return null; }))
            .thenCompose(ignored -> {
                this.checks.add("global-snapshot: actual root CommandStorage snapshot completed");
                List<Player> players = testPlayers();
                World home = Bukkit.getWorld(NamespacedKey.minecraft("overworld"));
                return CompletableFuture.allOf(movePlayer(players.get(0), new Location(home, 8, 75, 8)),
                    movePlayer(players.get(1), new Location(home, 12, 75, 8)));
            }).thenCompose(ignored -> {
                return CompletableFuture.allOf(onRegion(0, () -> this.world.getChunkAt(0, 0).removePluginChunkTicket(this.plugin)),
                    onRegion(128, () -> this.world.getChunkAt(128, 0).removePluginChunkTicket(this.plugin)));
            }).thenCompose(ignored -> Bukkit.getRuntimeWorldManager().unloadWorldAsync(this.world, WorldUnloadOptions.defaults()))
            .thenCompose(result -> {
                require(result.successful(), "runtime unload with save: " + result);
                return Bukkit.getRuntimeWorldManager().loadWorldAsync(key);
            }).thenCompose(result -> {
                require(result.successful(), "runtime reload: " + result);
                this.world = java.util.Objects.requireNonNull(result.world());
                return CompletableFuture.allOf(reload(0), reload(128));
            }).thenAccept(ignored -> this.checks.add("block-reload: both command block NBT values persisted after actual unload/reload")).toCompletableFuture();
    }

    private CompletableFuture<Void> prepare(int x) {
        CompletableFuture<?>[] chunks = new CompletableFuture<?>[9];
        int i = 0;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) chunks[i++] = this.world.getChunkAtAsync(x + dx, dz, true);
        return CompletableFuture.allOf(chunks).thenCompose(ignored -> onRegion(x, () -> {
            require(Bukkit.isOwnedByCurrentRegion(this.world, x, 0), "actual block owner");
            this.owners.add(io.papermc.paper.threadedregions.TickRegionScheduler.getCurrentRegion());
            this.world.getChunkAt(x, 0).addPluginChunkTicket(this.plugin);
            this.world.getBlockAt(x * 16 + 8, 70, 8).setType(Material.COMMAND_BLOCK);
            this.world.getBlockAt(x * 16 + 10, 70, 8).setType(Material.COMMAND_BLOCK);
            ArmorStand stand = this.world.spawn(new Location(this.world, x * 16 + 6, 72, 8), ArmorStand.class,
                entity -> { entity.setGravity(false); entity.setInvulnerable(true); });
            this.stands.put(x, stand.getUniqueId());
        }));
    }

    private CompletableFuture<Void> blockCommands(int x) {
        String pos = (x * 16 + 8) + " 70 8";
        String other = (x * 16 + 10) + " 70 8";
        String prefix = "execute in " + this.world.getKey() + " run ";
        return command(prefix + "data merge block " + pos + " {Command:'say initial-" + x + "',SuccessCount:7}", true)
            .thenCompose(ignored -> command(prefix + "data get block " + pos + " SuccessCount 2", true))
            .thenApply(result -> { require(result == 14, "actual numeric result with scale"); return result; })
            .thenCompose(ignored -> command(prefix + "data modify block " + other + " Command set from block " + pos + " Command", true))
            .thenCompose(ignored -> command(prefix + "data remove block " + pos + " Command", true))
            .thenCompose(ignored -> command(prefix + "data modify block " + pos + " Command set value 'say final-" + x + "'", true))
            .thenCompose(ignored -> onRegion(x, () -> {
                require(blockTag(x, 8).getStringOr("Command", "").equals("say final-" + x), "block set/remove/default");
                require(blockTag(x, 10).getStringOr("Command", "").equals("say initial-" + x), "same-region block from");
                this.checks.add("block-commands: " + x + " merge/get/path/scale/set/remove/from under freeze");
            }))
            .thenCompose(ignored -> command(prefix + "execute positioned " + (x * 16 + 8) + " 70 8 run data get entity @e[type=minecraft:armor_stand,distance=..8,limit=1] Health", true))
            .thenCompose(ignored -> command("data get entity " + this.stands.get(x) + " Health", true))
            .thenAccept(ignored -> this.checks.add("entity-reads: " + x + " bounded selector and UUID owner routing under freeze"));
    }

    private CompletableFuture<Void> storageCommands() {
        return command("data merge storage tessera_smoke:data {numbers:[2,3],object:{keep:1},text:'abcdef'}", true)
            .thenCompose(ignored -> command("data modify storage tessera_smoke:data numbers insert 1 value 9", true))
            .thenCompose(ignored -> command("data modify storage tessera_smoke:data numbers prepend value 1", true))
            .thenCompose(ignored -> command("data modify storage tessera_smoke:data numbers append value 4", true))
            .thenCompose(ignored -> command("data modify storage tessera_smoke:data numbers[2] set value 8", true))
            .thenCompose(ignored -> command("data remove storage tessera_smoke:data numbers[3]", true))
            .thenCompose(ignored -> command("data modify storage tessera_smoke:data object merge value {added:2}", true))
            .thenCompose(ignored -> command("data modify storage tessera_smoke:data sliced set string storage tessera_smoke:data text 1 4", true))
            .thenCompose(ignored -> command("execute store result storage tessera_smoke:data numeric int 1 run data get storage tessera_smoke:data numbers", true))
            .thenCompose(ignored -> command("execute if data storage tessera_smoke:data object.added run data get storage tessera_smoke:data object.added", true))
            .thenCompose(ignored -> command("execute in " + this.world.getKey() + " positioned 8 70 8 run data modify storage tessera_smoke:data computed set compute default integer {type:'minecraft:constant',value:23}", true))
            .thenCompose(ignored -> command("execute in " + this.world.getKey() + " run data modify storage tessera_smoke:data copied set from block 8 70 8 SuccessCount", true))
            .thenAccept(ignored -> {
                CompoundTag data = server().getCommandStorage().get(SHARED);
                require(data.getListOrEmpty("numbers").toString().equals("[1,2,8,4]"), "all list mutations: " + data);
                require(data.getCompoundOrEmpty("object").getIntOr("keep", -1) == 1
                    && data.getCompoundOrEmpty("object").getIntOr("added", -1) == 2, "compound merge preserved old fields");
                require(data.getStringOr("sliced", "").equals("bcd"), "substring");
                require(data.getIntOr("numeric", -1) == 4 && data.getIntOr("computed", -1) == 23 && data.getIntOr("copied", -1) == 7,
                    "real callbacks, compute and block-source values: " + data);
                this.checks.add("storage-grammar: get/merge/remove/modify, list variants, from/string/compute, execute store/if");
            });
    }

    private CompletableFuture<Void> parallelWrites(int x) {
        return onRegion(x, () -> {
            CommandSourceStack source = console().withLevel(level()).withPosition(new net.minecraft.world.phys.Vec3(x * 16 + 8, 70, 8)).withSuppressedOutput();
            for (int i = 1; i <= 150; i++) {
                AtomicInteger result = new AtomicInteger();
                server().getCommands().performPrefixedCommand(source.withCallback((success, value) -> {
                    if (success) result.set(value);
                }), "data modify storage tessera_smoke:data region" + x + " set value " + i);
                require(result.get() > 0, "parallel command result " + x + ":" + i);
            }
        });
    }

    private CompletableFuture<Void> ownershipRejections() {
        String prefix = "execute in " + this.world.getKey() + " run ";
        return command(prefix + "data modify block 8 70 8 Command set from block 2056 70 8 Command", false)
            .thenCompose(ignored -> command(prefix + "data get block 500008 70 8 Command", false))
            .thenCompose(ignored -> command(prefix + "execute positioned 8 70 8 run data get entity @e[type=minecraft:armor_stand,distance=..3000,limit=1] Health", false))
            .thenCompose(ignored -> command("data get entity @e[nbt={Health:20.0f},limit=1]", false))
            .thenCompose(ignored -> command("execute as @e[nbt={Health:20.0f},limit=1] run data get storage tessera_smoke:data numeric", false))
            .thenCompose(ignored -> command("data merge entity " + this.stands.get(0) + " {Pos:[2056.0d,70.0d,8.0d]}", false))
            .thenCompose(ignored -> command("execute store result entity " + this.stands.get(0) + " Health float 1 run data get storage tessera_smoke:data numeric", false))
            .thenCompose(ignored -> onRegion(0, () -> {
                require(blockTag(0, 8).getStringOr("Command", "").equals("say final-0"), "failed foreign copy did not mutate target");
                var old = level().getBlockEntity(new BlockPos(8, 70, 8));
                var accessor = new net.minecraft.server.commands.data.BlockDataAccessor(old, new BlockPos(8, 70, 8));
                this.world.getBlockAt(8, 70, 8).setType(Material.STONE);
                try { accessor.getData(); throw new AssertionError("stale block accessor was accepted"); }
                catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { /* Expected before serialization. */ }
                this.world.getBlockAt(8, 70, 8).setType(Material.COMMAND_BLOCK);
            }))
            .thenCompose(ignored -> command(prefix + "data merge block 8 70 8 {Command:'say final-0',SuccessCount:7}", true))
            .thenAccept(ignored -> this.checks.add("ownership-rejections: foreign/unloaded/cross-region/NBT-filter/entity-write/stale-accessor all rejected without target changes"));
    }

    private CompletableFuture<Void> playerReads() {
        List<Player> players = testPlayers();
        return command("data get entity DataAlpha Health", true)
            .thenCompose(ignored -> command("execute as DataAlpha at @s run data get entity @s Health", true))
            .thenCompose(ignored -> command("execute as DataAlpha at @s run data get entity @a[distance=..8,nbt={Health:20.0f},limit=1] Health", true))
            .thenCompose(ignored -> command("execute as DataAlpha as DataBeta if entity @s[nbt={Health:20.0f}] run data get storage tessera_smoke:data numeric", false))
            .thenCompose(ignored -> movePlayer(players.get(1), new Location(Bukkit.getWorld(NamespacedKey.minecraft("the_nether")), 8, 80, 8)))
            .thenCompose(ignored -> command("execute store result storage tessera_smoke:data dimension-length int 1 run data get entity "
                + "DataBeta Dimension", true))
            .thenCompose(ignored -> onPlayer(players.getFirst(), player -> {
                player.setOp(true);
                AtomicInteger result = new AtomicInteger();
                server().getCommands().performPrefixedCommand(((CraftPlayer) player).getHandle().createCommandSourceStack()
                    .withCallback((success, value) -> { if (success) result.set(value); }), "data get entity @s Health");
                require(result.get() > 0, "player @s with real player command source");
                result.set(0);
                server().getCommands().performPrefixedCommand(((CraftPlayer) player).getHandle().createCommandSourceStack()
                    .withCallback((success, value) -> { if (success) result.set(value); }), "data get entity DataBeta Health");
                require(result.get() == 0, "player command must not serialize another dimension's entity");
            })).thenAccept(ignored -> {
                require(server().getCommandStorage().get(SHARED).getIntOr("dimension-length", -1) == "minecraft:the_nether".length(),
                    "actual target dimension and synchronous store result after a real transfer");
                this.checks.add("player-reads: name/UUID owner routing, @s, actual Nether transfer, numeric callback, foreign player rejection");
            });
    }

    private CompletableFuture<Void> movePlayer(Player player, Location target) {
        var moved = new CompletableFuture<Boolean>();
        return onPlayer(player, current -> current.teleportAsync(target).whenComplete((success, failure) -> {
            if (failure != null) moved.completeExceptionally(failure); else moved.complete(success);
        })).thenCompose(ignored -> moved).thenAccept(success -> require(success, "real client dimension/region transfer"));
    }

    private CompletableFuture<Void> reload(int x) {
        return this.world.getChunkAtAsync(x, 0, true).thenCompose(ignored -> onRegion(x, () -> {
            require(blockTag(x, 8).getStringOr("Command", "").equals("say final-" + x), "persisted target block " + x);
            require(blockTag(x, 10).getStringOr("Command", "").equals("say initial-" + x), "persisted copied block " + x);
        }));
    }

    private CompletableFuture<Void> verifyRestart() {
        return command("data get storage tessera_smoke:durable marker", true).thenAccept(result -> {
            require(result == 17017, "real saved CommandStorage loaded after restart");
            require(!server().getCommandStorage().get(DURABLE).contains("removed"), "removed value did not reappear after restart");
            require(server().getCommandStorage().get(SHARED).getIntOr("region128", -1) == 150, "parallel update survived restart");
            this.checks.add("storage-restart: durable marker, removed value absent, parallel final values retained");
        });
    }

    private CompletableFuture<Void> waitForRcon() {
        var done = new CompletableFuture<Void>();
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(this.plugin, task -> {
            if (!Files.exists(Path.of("rcon-verified"))) return;
            task.cancel();
            this.checks.add("rcon: actual socket block/entity/storage replies verified by runner");
            done.complete(null);
        }, 1, 5);
        return done;
    }

    private CompletableFuture<Integer> command(String text, boolean expectedSuccess) {
        AtomicInteger result = new AtomicInteger(Integer.MIN_VALUE);
        return global(() -> server().getCommands().performPrefixedCommandAsync(
            console().withCallback((success, value) -> result.set(success ? value : Integer.MIN_VALUE)), text))
            .thenCompose(future -> future).thenApply(ignored -> {
                require((result.get() != Integer.MIN_VALUE) == expectedSuccess, "command outcome: " + text + " = " + result.get());
                return result.get();
            });
    }

    private <T> CompletableFuture<T> global(java.util.function.Supplier<T> action) {
        var future = new CompletableFuture<T>();
        Bukkit.getGlobalRegionScheduler().execute(this.plugin, () -> {
            try { future.complete(action.get()); } catch (Throwable error) { future.completeExceptionally(error); }
        });
        return future;
    }

    private CompletableFuture<Void> onRegion(int x, Runnable action) {
        var future = new CompletableFuture<Void>();
        Bukkit.getRegionScheduler().execute(this.plugin, this.world, x, 0, () -> {
            try { action.run(); future.complete(null); } catch (Throwable error) { future.completeExceptionally(error); }
        });
        return future;
    }

    private CompletableFuture<Void> onPlayer(Player player, java.util.function.Consumer<Player> action) {
        var future = new CompletableFuture<Void>();
        if (!player.getScheduler().execute(this.plugin, () -> {
            try { action.accept(player); future.complete(null); } catch (Throwable error) { future.completeExceptionally(error); }
        }, () -> future.completeExceptionally(new AssertionError("player retired")), 1)) {
            future.completeExceptionally(new AssertionError("player scheduler rejected"));
        }
        return future;
    }

    private CompoundTag blockTag(int x, int offset) { return level().getBlockEntity(new BlockPos(x * 16 + offset, 70, 8)).saveWithFullMetadata(level().registryAccess()); }
    private ServerLevel level() { return ((CraftWorld) this.world).getHandle(); }
    private static MinecraftServer server() { return ((CraftServer) Bukkit.getServer()).getServer(); }
    private static List<Player> testPlayers() { return List.of(java.util.Objects.requireNonNull(Bukkit.getPlayerExact("DataAlpha")), java.util.Objects.requireNonNull(Bukkit.getPlayerExact("DataBeta"))); }
    private static CommandSourceStack console() { return server().createCommandSourceStack(); }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
