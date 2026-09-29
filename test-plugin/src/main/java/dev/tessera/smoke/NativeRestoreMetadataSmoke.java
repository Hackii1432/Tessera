package dev.tessera.smoke;

import io.papermc.paper.world.PlayerRestoreResult;
import io.papermc.paper.world.PlayerRestoreStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import static dev.tessera.smoke.NativeRestoreSmoke.call;
import static dev.tessera.smoke.NativeRestoreSmoke.type;

/** Real public restore transactions, offline files, Bukkit queries and a no-login restart. */
final class NativeRestoreMetadataSmoke implements Listener {
    private static final UUID UNNAMED = UUID.fromString("be5b9780-3036-4bbd-b70c-8d20bed00001");
    private static final UUID BARE = UUID.fromString("be5b9780-3036-4bbd-b70c-8d20bed00002");
    private static final UUID STATS_ONLY = UUID.fromString("be5b9780-3036-4bbd-b70c-8d20bed00003");
    private static final UUID ADVANCEMENTS_ONLY = UUID.fromString("be5b9780-3036-4bbd-b70c-8d20bed00004");
    private static final UUID NEVER_SAVED = UUID.fromString("be5b9780-3036-4bbd-b70c-8d20bed00005");
    private static final long HISTORICAL = 1_600_000_000_000L;
    private final JavaPlugin plugin;
    private final AtomicInteger joins = new AtomicInteger(), quits = new AtomicInteger();
    private final Path evidence = Path.of("native-restore-evidence").toAbsolutePath();
    private final java.util.ArrayList<String> checks = new java.util.ArrayList<>();

    NativeRestoreMetadataSmoke(JavaPlugin plugin) { this.plugin = plugin; }

    @EventHandler public void join(PlayerJoinEvent event) { this.joins.incrementAndGet(); }
    @EventHandler public void quit(PlayerQuitEvent event) { this.quits.incrementAndGet(); }

    void start() {
        Bukkit.getPluginManager().registerEvents(this, this.plugin);
        this.plugin.getLogger().info("NATIVE_RESTORE_READY");
        Thread.ofVirtual().name("Native restore metadata fixture").start(() -> {
            try {
                Files.createDirectories(this.evidence);
                boolean restart = "1".equals(System.getenv("NATIVE_RESTORE_METADATA_RESTART"));
                if (restart) this.restart(); else this.run();
                String suffix = "1".equals(System.getenv("NATIVE_RESTORE_METADATA_MVE")) ? "-mve" : restart ? "-restart" : "";
                Files.writeString(this.evidence.resolve("metadata" + suffix + "-checks.txt"), String.join("\n", this.checks));
                this.plugin.getLogger().info("NATIVE_RESTORE_METADATA_PASS");
            } catch (Throwable failure) {
                this.plugin.getLogger().log(java.util.logging.Level.SEVERE, "NATIVE_RESTORE_COMPONENTS_FAIL", failure);
                this.global(() -> { Bukkit.shutdown(); return null; });
            }
        });
    }

    private void run() throws Exception {
        require(Bukkit.getPlayerRestoreService().contractVersion() == 1, "contract remains exactly one");
        waitFor(() -> await(this.global(() -> Bukkit.getOnlinePlayers().size())) == 3, "three initial protocol clients");
        Player one = await(this.global(() -> Bukkit.getPlayerExact("RestoreOne")));
        Player two = await(this.global(() -> Bukkit.getPlayerExact("RestoreTwo")));
        Player realPreview = await(this.global(() -> Bukkit.getPlayerExact("RestorePreview")));
        await(this.global(() -> { Bukkit.getServerTickManager().setFrozen(true); return null; }));
        for (Player player : List.of(one, two, realPreview)) await(this.owner(player, () -> {
            player.setGameMode(GameMode.CREATIVE);
            player.setTotalExperience(137);
            player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 7));
            player.getEnderChest().setItem(0, new ItemStack(Material.EMERALD, 5));
            player.setStatistic(Statistic.JUMP, 23);
            player.setStatistic(Statistic.DEATHS, 7);
            player.getPersistentDataContainer().set(NamespacedKey.fromString("fixture:lifetime"), PersistentDataType.LONG, 882211L);
            player.saveData();
            return null;
        }));
        Object handle = await(this.owner(one, () -> call(one, "getHandle")));
        long login = await(this.owner(one, one::getLastLogin));
        Object server = call(Bukkit.getServer(), "getServer");
        Object playerList = call(server, "getPlayerList");
        Path active = ((java.io.File)call(playerList.getClass().getField("playerIo").get(playerList), "getPlayerDir")).toPath().getParent().toAbsolutePath();
        Files.writeString(this.evidence.resolve("metadata-active-path.txt"), active.toString());
        signal("OFFLINE");
        for (Player player : List.of(two, realPreview)) {
            Object nativePlayer = await(this.owner(player, () -> call(player, "getHandle")));
            await(this.owner(player, () -> { player.kick(net.kyori.adventure.text.Component.text("Intentional offline identity fixture")); return null; }));
            await((CompletionStage<?>)nativePlayer.getClass().getField("tessera$logoutDrained").get(nativePlayer));
        }
        require(await(this.global(() -> Bukkit.getOnlinePlayers().size())) == 1, "one continuous connection remains");

        for (Player player : List.of(two, realPreview)) {
            Path file = data(active, player.getUniqueId());
            Object tag = read(file);
            Object bukkit = compound(tag, "bukkit"), paper = compound(tag, "Paper");
            call(bukkit, "putString", "lastKnownName", player.getName());
            call(bukkit, "putLong", "firstPlayed", HISTORICAL - 1000);
            call(bukkit, "putLong", "lastPlayed", HISTORICAL + 1000);
            call(paper, "putLong", "LastLogin", HISTORICAL);
            call(paper, "putLong", "LastSeen", HISTORICAL + 2000);
            call(bukkit, "putString", "unknown-owner-metadata", "preserve me");
            call(paper, "putLong", "unknown-lifetime", 991122L);
            Object opaque = type("net.minecraft.nbt.CompoundTag").getConstructor().newInstance();
            call(opaque, "putString", "nested", "unknown player extension");
            call(opaque, "putLongArray", "counters", new long[]{17, 51, 9999999999L});
            call(tag, "put", "UnknownExtension", opaque);
            write(tag, file);
            Files.setLastModifiedTime(file, FileTime.fromMillis(HISTORICAL + 3000));
        }
        Object prototype = read(data(active, two.getUniqueId()));
        for (UUID id : List.of(UNNAMED, BARE)) {
            Object tag = call(prototype, "copy");
            call(tag, "store", "UUID", type("net.minecraft.core.UUIDUtil").getField("CODEC").get(null), id);
            call(tag, "remove", "bukkit"); call(tag, "remove", "Paper");
            if (id.equals(UNNAMED)) {
                call(tag, "put", "bukkit", type("net.minecraft.nbt.CompoundTag").getConstructor().newInstance());
                call(tag, "put", "Paper", type("net.minecraft.nbt.CompoundTag").getConstructor().newInstance());
            }
            write(tag, data(active, id));
            Files.setLastModifiedTime(data(active, id), FileTime.fromMillis(HISTORICAL + 4000));
        }
        Files.copy(active.resolve("stats/" + two.getUniqueId() + ".json"), active.resolve("stats/" + STATS_ONLY + ".json"), StandardCopyOption.COPY_ATTRIBUTES);
        Files.copy(active.resolve("advancements/" + two.getUniqueId() + ".json"), active.resolve("advancements/" + ADVANCEMENTS_ONLY + ".json"), StandardCopyOption.COPY_ATTRIBUTES);
        Set<UUID> saved = Set.of(one.getUniqueId(), two.getUniqueId(), realPreview.getUniqueId(), UNNAMED, BARE);
        Files.writeString(this.evidence.resolve("metadata-online-id.txt"), one.getUniqueId().toString());
        Files.writeString(this.evidence.resolve("metadata-saved-ids.txt"), saved.stream().map(UUID::toString).sorted().collect(Collectors.joining("\n")));
        Path source = await(snapshot("metadata-source")).resolve("players");
        World target = await(await(this.global(() -> Bukkit.getRuntimeWorldManager().createWorldAsync(new WorldCreator("metadata_target").type(WorldType.FLAT))))).world();
        require(target != null, "replacement world prepared");
        Object onlineTag = read(data(source, one.getUniqueId()));
        call(onlineTag, "putLong", "WorldUUIDMost", target.getUID().getMostSignificantBits());
        call(onlineTag, "putLong", "WorldUUIDLeast", target.getUID().getLeastSignificantBits());
        call(onlineTag, "putString", "Dimension", target.getKey().toString());
        write(onlineTag, data(source, one.getUniqueId()));
        Map<String, byte[]> sourceBytes = bytes(source);
        assertNotRegistered(); // Before any Bukkit offline query creates its own wrapper.
        var service = Bukkit.getPlayerRestoreService();
        for (int round = 0; round < 2; round++) {
            await(this.owner(one, () -> { one.setTotalExperience(999); return null; }));
            UUID id = UUID.randomUUID();
            check(await(service.prepareAsync(id, source, this.evidence.resolve("metadata-rollback-" + round), target)), id, PlayerRestoreStatus.PREPARED);
            assertNotRegistered();
            check(await(service.applyAsync(id, true)), id, PlayerRestoreStatus.APPLIED);
            check(await(service.completeAsync(id, true)), id, PlayerRestoreStatus.COMPLETED);
            assertNotRegistered();
            verifyOffline(source, active, saved, one.getUniqueId());
            await(this.owner(one, () -> {
                require(one.getName().equals("RestoreOne") && one.getLastLogin() == login && call(one, "getHandle") == handle, "online identity/session retained");
                require(one.getTotalExperience() == 137 && one.getWorld().equals(target), "native XP and world restored");
                require(one.getInventory().getItem(0).getAmount() == 7 && one.getEnderChest().getItem(0).getAmount() == 5, "native inventories restored");
                require(one.getPersistentDataContainer().get(NamespacedKey.fromString("fixture:lifetime"), PersistentDataType.LONG) == 882211L, "online lifetime PDC unchanged");
                return null;
            }));
            Path resave = await(snapshot("metadata-resave-" + round)).resolve("players");
            verifyOffline(source, resave, saved, one.getUniqueId());
            this.checks.add("Commit/resave " + round + ": entire offline NBT, absent fields/files, JSON bytes, mtime, UUID set, Bukkit identities/times, lifetime PDC and stats exact; online session unchanged.");
        }

        // Hold a real admitted reader so Prepare is pending when the player retires.
        signal("REJOIN");
        waitFor(() -> await(this.global(() -> Bukkit.getPlayerExact("RestoreTwo"))) != null, "intentional rejoin for Prepare race");
        Player returning = await(this.global(() -> Bukkit.getPlayerExact("RestoreTwo")));
        Object returningHandle = await(this.owner(returning, () -> { returning.setTotalExperience(999); return call(returning, "getHandle"); }));
        Object fence = type("org.bukkit.craftbukkit.world.PlayerStoreFence").getField("INSTANCE").get(null);
        UUID race = UUID.randomUUID();
        Path rollback = this.evidence.resolve("metadata-disconnect-rollback");
        CompletionStage<PlayerRestoreResult> preparing;
        try (AutoCloseable lease = (AutoCloseable)call(fence, "requireRead")) {
            preparing = service.prepareAsync(race, source, rollback, target);
            waitFor(() -> (boolean)call(fence, "isClosed"), "Prepare acquired fence and awaits admission");
            require(!preparing.toCompletableFuture().isDone(), "Prepare cannot finish before admitted reader");
            signal("PREPARE_DISCONNECT");
            await(this.owner(returning, () -> { returning.kick(net.kyori.adventure.text.Component.text("Intentional disconnect during Prepare")); return null; }));
            await((CompletionStage<?>)returningHandle.getClass().getField("tessera$logoutDrained").get(returningHandle));
        }
        check(await(preparing), race, PlayerRestoreStatus.PREPARED);
        Object backup = read(data(rollback, returning.getUniqueId()));
        require(((Number)optional(backup, "getInt", "XpTotal")).intValue() == 999, "fresh backup includes captured logout");
        require("RestoreTwo".equals(optional(compound(backup, "bukkit"), "getString", "lastKnownName")), "logout backup keeps actual identity");
        require(((Number)optional(compound(backup, "Paper"), "getLong", "LastLogin")).longValue() > HISTORICAL, "logout backup keeps actual session login, not preview zero");
        check(await(service.applyAsync(race, true)), race, PlayerRestoreStatus.APPLIED);
        check(await(service.applyAsync(race, false)), race, PlayerRestoreStatus.ROLLED_BACK);
        check(await(service.completeAsync(race, false)), race, PlayerRestoreStatus.COMPLETED);
        verifyOffline(rollback, active, saved, one.getUniqueId());
        require(read(data(active, returning.getUniqueId())).equals(backup), "full captured logout restored by rollback");
        this.checks.add("Disconnect during pending Prepare: counted writer drain, real owner logout captured with session identity, apply then rollback restores exact fresh backup and releases fence.");
        for (int round = 0; round < 2; round++) {
            UUID id = UUID.randomUUID();
            Path backupPath = this.evidence.resolve("metadata-final-rollback-" + round);
            check(await(service.prepareAsync(id, source, backupPath, target)), id, PlayerRestoreStatus.PREPARED);
            check(await(service.applyAsync(id, true)), id, PlayerRestoreStatus.APPLIED);
            if (round == 0) check(await(service.applyAsync(id, false)), id, PlayerRestoreStatus.ROLLED_BACK);
            check(await(service.completeAsync(id, round != 0)), id, PlayerRestoreStatus.COMPLETED);
            verifyOffline(round == 0 ? backupPath : source, active, saved, one.getUniqueId());
        }
        require(!(boolean)call(fence, "isClosed"), "no remaining admission fence");
        require(this.joins.get() == 4 && this.quits.get() == 3, "only actual joins/quits, none from previews: " + this.joins + "/" + this.quits);
        Map<String, byte[]> after = bytes(source);
        require(sourceBytes.keySet().equals(after.keySet()), "source file set unchanged");
        for (var entry : sourceBytes.entrySet()) require(Arrays.equals(entry.getValue(), after.get(entry.getKey())), "source bytes unchanged: " + entry.getKey());
        this.checks.add("Repeated rollback/commit; no synthetic participants or join/quit events; real name RestorePreview accepted; source untouched; no network profile resolution used by test or restore.");
    }

    private void restart() throws Exception {
        // The runner intentionally connects no clients on this process start.
        Path active = Path.of(Files.readString(this.evidence.resolve("metadata-active-path.txt")));
        UUID online = UUID.fromString(Files.readString(this.evidence.resolve("metadata-online-id.txt")));
        Set<UUID> saved = Files.readAllLines(this.evidence.resolve("metadata-saved-ids.txt")).stream().map(UUID::fromString).collect(Collectors.toSet());
        require(await(this.global(() -> Bukkit.getOnlinePlayers().isEmpty())), "restart has no clients");
        verifyOffline(this.evidence.resolve("metadata-source/players"), active, saved, online);
        require(this.joins.get() == 0 && this.quits.get() == 0, "no login needed to obtain correct offline identities");
        this.checks.add("Fresh process, zero clients/logins: original offline NBT, missing files/metadata, timestamps including file fallback, Bukkit names/count and lifetime counters unchanged.");
        if ("1".equals(System.getenv("NATIVE_RESTORE_METADATA_MVE"))) {
            this.verifyMve(active, saved);
            verifyOffline(this.evidence.resolve("metadata-source/players"), active, saved, online);
        }
    }

    private void verifyMve(Path active, Set<UUID> saved) throws Exception {
        // MVE deliberately imports existing stats-/advancement-only UUIDs too.
        // They already occur in the unchanged source; unlike Bukkit's .dat-only
        // enumeration, this does not imply a joined player or a new native file.
        Set<UUID> knownToMve = new java.util.HashSet<>(saved);
        knownToMve.add(STATS_ONLY);
        knownToMve.add(ADVANCEMENTS_ONLY);
        waitFor(() -> await(this.global(() -> Bukkit.getPluginManager().isPluginEnabled("mosaik_vanilla_enhancements"))), "real MVE enabled");
        var mve = Bukkit.getPluginManager().getPlugin("mosaik_vanilla_enhancements");
        Object api = call(mve, "getPlayerStatsApiManager");
        require(api != null, "actual MVE statistics manager");
        var field = api.getClass().getDeclaredField("snapshotManager");
        field.setAccessible(true);
        Object manager = field.get(api);
        waitFor(() -> ((List<?>)call(call(manager, "getSnapshot"), "players")).size() == knownToMve.size(), "MVE refresh contains exactly the source's known UUIDs");
        Object before = call(manager, "getSnapshot");
        List<?> first = (List<?>)call(before, "players");
        require((boolean)call(manager, "isLifetimeAccumulationEnabled"), "MVE actual LIFETIME mode");
        for (int round = 0; round < 2; round++) {
            Object previousDate = call(call(manager, "getSnapshot"), "updatedAt");
            call(manager, "requestRefresh");
            waitFor(() -> !previousDate.equals(call(call(manager, "getSnapshot"), "updatedAt")), "MVE completed subsequent refresh");
            List<?> views = (List<?>)call(call(manager, "getSnapshot"), "players");
            Set<UUID> ids = new java.util.HashSet<>();
            for (Object view : views) {
                UUID id = (UUID)call(view, "uuid");
                ids.add(id);
                require(!(boolean)call(view, "online"), "MVE sees nobody online");
                for (Object previous : first) if (id.equals(call(previous, "uuid"))) {
                    require(call(previous, "stats").equals(call(view, "stats")), "MVE lifetime stats unchanged across refresh");
                    require(call(previous, "playtimeSeconds").equals(call(view, "playtimeSeconds")), "MVE lifetime playtime unchanged");
                }
                if (saved.contains(id) && !id.equals(UNNAMED) && !id.equals(BARE)) {
                    Object data = read(data(active, id));
                    require(optional(compound(data, "bukkit"), "getString", "lastKnownName").equals(call(view, "name")), "MVE name matches real stored identity");
                    require(java.time.Instant.ofEpochMilli((long)optional(compound(data, "Paper"), "getLong", "LastSeen")).equals(call(view, "lastSeenAt")), "MVE uses historical LastSeen without login");
                    require((long)call(call(view, "stats"), "deaths") == 7L, "MVE nonzero lifetime counter retained");
                } else {
                    require(!"RestorePreview".equals(call(view, "name")), "MVE unnamed UUID did not acquire preview identity");
                    if (!saved.contains(id)) require(call(view, "lastSeenAt") == null, "JSON-only UUID has no invented activity");
                }
            }
            require(ids.equals(knownToMve), "MVE exact source UUID set, without additional preview participant");
        }
        Path ledger = mve.getDataFolder().toPath().resolve("data/player-statistics.json");
        waitFor(() -> Files.isRegularFile(ledger) && (int)call(call(jsonFile(ledger), "getAsJsonArray", "players"), "size") == knownToMve.size(), "MVE complete durable ledger written");
        Object persisted = jsonFile(ledger);
        require((int)call(call(persisted, "get", "version"), "getAsInt") == 9, "actual MVE format 9");
        Set<UUID> storedIds = new java.util.HashSet<>();
        for (Object entry : (Iterable<?>)call(persisted, "getAsJsonArray", "players")) {
            Object stored = call(call(entry, "getAsJsonObject"), "getAsJsonObject", "player");
            UUID id = UUID.fromString((String)call(call(stored, "get", "uuid"), "getAsString"));
            storedIds.add(id);
            require(!(boolean)call(call(stored, "get", "online"), "getAsBoolean"), "MVE persisted player is offline");
            if (saved.contains(id) && !id.equals(UNNAMED) && !id.equals(BARE)) {
                Object data = read(data(active, id));
                require(optional(compound(data, "bukkit"), "getString", "lastKnownName").equals(call(call(stored, "get", "name"), "getAsString")), "MVE persisted name exact");
                require(java.time.Instant.ofEpochMilli((long)optional(compound(data, "Paper"), "getLong", "LastSeen")).equals(java.time.Instant.parse((String)call(call(stored, "get", "lastSeenAt"), "getAsString"))), "MVE persisted LastSeen exact");
            }
        }
        require(storedIds.equals(knownToMve), "MVE persisted source UUID set exact");
        Path firstLedger = this.evidence.resolve("metadata-mve-first-ledger.json");
        if (Files.exists(firstLedger)) {
            require(call(jsonFile(firstLedger), "get", "lifetimePlayers").equals(call(persisted, "get", "lifetimePlayers")), "entire persisted lifetime ledger unchanged across MVE restart");
            this.checks.add("Second real MVE process: full lifetimePlayers ledger unchanged from first MVE process, without any joined client.");
        } else {
            Files.copy(ledger, firstLedger);
        }
        // Preserve real product output as evidence, without modifying it.
        Files.copy(ledger, this.evidence.resolve("metadata-mve-ledger.json"), StandardCopyOption.REPLACE_EXISTING);
        this.checks.add("MVE " + mve.getPluginMeta().getVersion() + " with LuckPerms " + Bukkit.getPluginManager().getPlugin("LuckPerms").getPluginMeta().getVersion()
            + ": actual startup/refresh, exact UUID set and offline names/LastSeen, LIFETIME statistics/playtime stable across two extra refreshes; no logins. Does not exercise the full MCC world transaction.");
    }

    private void verifyOffline(Path expected, Path actual, Set<UUID> saved, UUID online) throws Exception {
        for (UUID id : saved) {
            if (id.equals(online)) continue;
            Path before = data(expected, id), after = data(actual, id);
            Object tag = read(before);
            require(tag.equals(read(after)), "complete offline NBT unchanged: " + id);
            require(Files.getLastModifiedTime(before).equals(Files.getLastModifiedTime(after)), "offline mtime unchanged: " + id);
            for (String store : List.of("stats", "advancements")) {
                Path a = expected.resolve(store + "/" + id + ".json"), b = actual.resolve(store + "/" + id + ".json");
                require(Files.exists(a) == Files.exists(b), "missing store remains missing: " + store + " / " + id);
                if (Files.exists(a)) require(Arrays.equals(Files.readAllBytes(a), Files.readAllBytes(b)), "offline JSON exact: " + store + " / " + id);
            }
        }
        // Query the published live store through the same Bukkit surface used by consumers.
        Set<UUID> listed = Arrays.stream(Bukkit.getOfflinePlayers()).map(org.bukkit.OfflinePlayer::getUniqueId).collect(Collectors.toSet());
        require(listed.equals(saved), "no new participants in Bukkit offline enumeration: " + listed);
        for (String name : List.of("RestoreTwo", "RestorePreview")) {
            UUID id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var offline = Bukkit.getOfflinePlayer(id);
            Object tag = read(data(actual, id));
            Object bukkit = compound(tag, "bukkit"), paper = compound(tag, "Paper");
            require(name.equals(offline.getName()) && !offline.isOnline(), "correct real offline name: " + name);
            require(offline.getFirstPlayed() == (long)optional(bukkit, "getLong", "firstPlayed"), "Bukkit firstPlayed exact");
            require(offline.getLastPlayed() == (long)optional(bukkit, "getLong", "lastPlayed"), "Bukkit lastPlayed exact");
            require(offline.getLastLogin() == (long)optional(paper, "getLong", "LastLogin"), "Bukkit lastLogin exact");
            require(offline.getLastSeen() == (long)optional(paper, "getLong", "LastSeen"), "Bukkit lastSeen exact");
            require(offline.getStatistic(Statistic.JUMP) == 23, "offline statistic preserved");
            require(offline.getPersistentDataContainer().get(NamespacedKey.fromString("fixture:lifetime"), PersistentDataType.LONG) == 882211L, "offline lifetime PDC preserved");
        }
        var unnamed = Bukkit.getOfflinePlayer(UNNAMED);
        long fallback = Files.getLastModifiedTime(data(actual, UNNAMED)).toMillis();
        require(unnamed.getName() == null && unnamed.getFirstPlayed() == fallback && unnamed.getLastPlayed() == fallback
            && unnamed.getLastLogin() == fallback && unnamed.getLastSeen() == fallback, "missing metadata stays absent, historical fallback unchanged");
        var bare = Bukkit.getOfflinePlayer(BARE);
        require(bare.getName() == null && bare.getLastLogin() == 0 && bare.getLastSeen() == 0, "absent Paper compound remains absent");
        for (UUID id : List.of(STATS_ONLY, ADVANCEMENTS_ONLY, NEVER_SAVED)) {
            require(!Files.exists(data(actual, id)) && !Bukkit.getOfflinePlayer(id).hasPlayedBefore() && Bukkit.getOfflinePlayer(id).getName() == null, "no invented joined player: " + id);
        }
        require(Arrays.equals(Files.readAllBytes(expected.resolve("stats/" + STATS_ONLY + ".json")), Files.readAllBytes(actual.resolve("stats/" + STATS_ONLY + ".json"))), "stats-only counter unchanged");
        require(Arrays.equals(Files.readAllBytes(expected.resolve("advancements/" + ADVANCEMENTS_ONLY + ".json")), Files.readAllBytes(actual.resolve("advancements/" + ADVANCEMENTS_ONLY + ".json"))), "advancements-only store unchanged");
    }

    private void assertNotRegistered() throws Exception {
        // UUID lookup is local only; never use getOfflinePlayer(String)/profile completion.
        Object server = call(Bukkit.getServer(), "getServer");
        Object cache = call(call(server, "services"), "nameToIdCache");
        for (UUID id : List.of(UNNAMED, BARE, STATS_ONLY, ADVANCEMENTS_ONLY, NEVER_SAVED)) {
            require(((java.util.Optional<?>)call(cache, "get", id)).isEmpty(), "preview never inserted a profile: " + id);
            require(Bukkit.getPlayer(id) == null, "preview never registered online: " + id);
        }
    }

    private CompletionStage<Path> snapshot(String name) {
        Path path = this.evidence.resolve(name);
        return this.global(() -> Bukkit.getRuntimeWorldManager().snapshotWorldsAsync(List.copyOf(Bukkit.getWorlds()), path)).thenCompose(Function.identity()).thenApply(result -> {
            require(result.successful(), "runtime snapshot succeeded: " + result); return path;
        });
    }
    private void signal(String signal) throws Exception {
        this.plugin.getLogger().info("NATIVE_RESTORE_METADATA_" + signal);
        waitFor(() -> Files.exists(this.evidence.resolve("metadata-runner-" + signal)), "runner signal " + signal);
    }
    private static Path data(Path root, UUID id) { return root.resolve("data/" + id + ".dat"); }
    private static Object read(Path path) throws Exception { return call(type("net.minecraft.nbt.NbtIo"), "readCompressed", path, call(type("net.minecraft.nbt.NbtAccounter"), "create", 16L * 1024 * 1024)); }
    private static Object jsonFile(Path path) throws Exception { return call(call(type("com.google.gson.JsonParser"), "parseString", Files.readString(path)), "getAsJsonObject"); }
    private static void write(Object tag, Path path) throws Exception { call(type("net.minecraft.nbt.NbtIo"), "writeCompressed", tag, path); }
    private static Object optional(Object tag, String method, String key) throws Exception { return ((java.util.Optional<?>)call(tag, method, key)).orElseThrow(); }
    private static Object compound(Object tag, String key) throws Exception { return optional(tag, "getCompound", key); }
    private static Map<String, byte[]> bytes(Path root) throws Exception {
        Map<String, byte[]> result = new HashMap<>();
        try (var paths = Files.walk(root)) { for (Path file : paths.filter(Files::isRegularFile).toList()) result.put(root.relativize(file).toString(), Files.readAllBytes(file)); }
        return result;
    }
    @FunctionalInterface private interface Action<T> { T get() throws Exception; }
    @FunctionalInterface private interface Check { boolean get() throws Exception; }
    private static void waitFor(Check check, String reason) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
        do { if (check.get()) return; Thread.sleep(20); } while (System.nanoTime() < until);
        throw new AssertionError("Timed out: " + reason);
    }
    private <T> CompletionStage<T> owner(Player player, Action<T> action) throws Exception {
        Object scheduler = player.getClass().getField("taskScheduler").get(player);
        Function<Object, CompletionStage<T>> run = ignored -> {
            try { return CompletableFuture.completedStage(action.get()); } catch (Throwable failure) { return CompletableFuture.failedStage(failure); }
        };
        return (CompletionStage<T>)call(scheduler, "scheduleRestore", run);
    }
    private <T> CompletionStage<T> global(Supplier<T> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Bukkit.getGlobalRegionScheduler().execute(this.plugin, () -> {
            try { result.complete(action.get()); } catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        return result;
    }
    private static <T> T await(CompletionStage<T> stage) throws Exception { return stage.toCompletableFuture().get(90, TimeUnit.SECONDS); }
    private static void check(PlayerRestoreResult result, UUID id, PlayerRestoreStatus status) { require(result.successful() && result.operationId().equals(id) && result.status() == status, "transaction result: " + result); }
    private static void require(boolean condition, String reason) { if (!condition) throw new AssertionError(reason); }
}
