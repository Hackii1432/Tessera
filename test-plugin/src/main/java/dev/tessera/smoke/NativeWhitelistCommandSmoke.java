package dev.tessera.smoke;

import com.mojang.authlib.services.MinecraftServicesDiscoveryService;
import com.mojang.authlib.services.MinecraftServicesProfileRepository;
import com.mojang.authlib.services.response.discovery.Discovery;
import com.mojang.authlib.services.response.discovery.DiscoveryResponse;
import com.mojang.authlib.services.response.discovery.Endpoint;
import com.mojang.authlib.services.response.discovery.Endpoints;
import com.sun.net.httpserver.HttpServer;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.rcon.RconConsoleSource;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/** Only loaded in an isolated fixture; the real authlib HTTP client and native dispatcher are exercised. */
final class NativeWhitelistCommandSmoke implements Listener {
    private final JavaPlugin plugin;
    private final List<String> checks = new CopyOnWriteArrayList<>();
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger globalTicks = new AtomicInteger();
    private final AtomicBoolean started = new AtomicBoolean(), serviceUnavailable = new AtomicBoolean(true);
    private final CountDownLatch lateRelease = new CountDownLatch(1), lateFinished = new CountDownLatch(1);
    private HttpServer http;

    NativeWhitelistCommandSmoke(JavaPlugin plugin) { this.plugin = plugin; }

    void start() {
        Bukkit.getPluginManager().registerEvents(this, this.plugin);
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(this.plugin, task -> {
            this.globalTicks.incrementAndGet();
            if (Bukkit.getOnlinePlayers().size() == 2 && this.started.compareAndSet(false, true)) {
                Thread.ofPlatform().daemon().name("Tessera native whitelist acceptance").start(this::test);
            }
        }, 1L, 1L);
        this.plugin.getLogger().info("WHITELIST_READY_CLIENTS");
    }

    @EventHandler
    public void intercept(com.destroystokyo.paper.event.profile.PreLookupProfileEvent event) {
        if (!event.getName().equalsIgnoreCase("lateplayer")) return;
        if (!event.isAsynchronous() || ca.spottedleaf.moonrise.common.util.TickThread.isTickThread()) {
            throw new AssertionError("Profile interception must not block a tick thread");
        }
        try {
            if (!this.lateRelease.await(20, TimeUnit.SECONDS)) throw new AssertionError("Late lookup was not released");
            event.setUUID(id("lateplayer"));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } finally {
            this.lateFinished.countDown();
        }
    }

    @EventHandler
    public void veto(io.papermc.paper.event.server.WhitelistStateUpdateEvent event) {
        if ("vetoplayer".equalsIgnoreCase(event.getPlayerProfile().getName())) event.setCancelled(true);
    }

    private void test() {
        try {
            this.http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            this.http.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(2,
                Thread.ofPlatform().daemon().name("Whitelist local HTTP-", 0).factory()));
            this.http.createContext("/profiles/", exchange -> {
                String name = exchange.getRequestURI().getPath().substring("/profiles/".length());
                this.requests.add(name);
                try {
                    if (name.equals("slowplayer")) Thread.sleep(6000);
                    if (name.equals("movingplayer") || name.equals("leaveplayer") || name.equals("permitplayer")) Thread.sleep(1500);
                    int status = name.equals("missingplayer") ? 404 : name.equals("rconfailure") ? 503
                        : name.equals("serviceplayer") && this.serviceUnavailable.get() ? 503 : 200;
                    String body = status == 200 ? "{\"id\":\"" + id(name).toString().replace("-", "") + "\",\"name\":\"" + name + "\"}"
                        : "{\"error\":\"fixture\",\"errorMessage\":\"injected HTTP status\"}";
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(status, bytes.length);
                    try (var output = exchange.getResponseBody()) { output.write(bytes); }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } catch (java.io.IOException expectedClosedClient) {
                    // The native authlib read timeout intentionally closes slowplayer's request.
                    if (!name.equals("slowplayer")) throw expectedClosedClient;
                }
            });
            this.http.start();
            global(() -> {
                installLocalProfiles();
                server().setUsesAuthentication(true); // Only after both offline fixture clients are connected.
                for (Player player : Bukkit.getOnlinePlayers()) player.setOp(true);
                server().tickRateManager().setFrozen(true);
                return null;
            }).get(10, TimeUnit.SECONDS);
            check(server().tickRateManager().isFrozen(), "freeze: native simulation frozen");

            check(command("whitelist add smokelower").contains("Added smokelower"), "case: lowercase uncached add");
            check(command("minecraft:whitelist add SmokeLower").contains("already whitelisted"), "case: namespaced mixed-case duplicate");
            check(count("smokelower") == 1 && listed("SMOKELOWER"), "case: exactly one request and one UUID");
            check(command("whitelist remove SMOKELOWER").contains("Removed smokelower"), "case: uppercase removal without network");
            check(count("smokelower") == 1 && !listed("smokelower"), "case: removal retained canonical UUID");
            check(command("execute run minecraft:whitelist add WrApPeRpLaYeR").contains("Added wrapperplayer"), "alias: execute wrapper asynchronous lookup");

            check(command("whitelist add missingplayer").contains("does not exist"), "missing: real HTTP 404 gives Vanilla not-found");
            check(command("whitelist add serviceplayer").contains("profile service unavailable"), "service: real HTTP 503 is not player-not-found");
            this.serviceUnavailable.set(false);
            check(command("whitelist add ServicePlayer").contains("Added serviceplayer"), "service: retry with another spelling succeeds");
            check(count("serviceplayer") == 2, "service: no negative cache after outage");

            int before = this.globalTicks.get();
            check(command("whitelist add slowplayer").contains("profile service unavailable"), "slow: real authlib socket read timeout");
            check(this.globalTicks.get() - before >= 70 && !listed("slowplayer"), "slow: global ticks progressed during HTTP wait at freeze");

            before = this.globalTicks.get();
            check(command("whitelist add lateplayer").contains("profile service unavailable"), "timeout: deadline rejects late plugin result");
            this.lateRelease.countDown();
            check(this.lateFinished.await(5, TimeUnit.SECONDS), "timeout: interception worker finished");
            Thread.sleep(150);
            check(this.globalTicks.get() - before >= 140 && !listed("lateplayer"), "timeout: no delayed whitelist mutation");
            check(command("whitelist add vetoplayer").contains("cancelled or rejected") && !listed("vetoplayer"), "veto: Paper event cancellation is not success");

            List<Player> players = global(() -> List.<Player>copyOf(Bukkit.getOnlinePlayers())).get(5, TimeUnit.SECONDS);
            Player first = players.getFirst();
            CompletableFuture<Void> moving = playerCommand(first, "whitelist add MovingPlayer");
            waitRequest("movingplayer");
            World nether = global(() -> Bukkit.getWorlds().stream().filter(world -> world.getEnvironment() == World.Environment.NETHER)
                .findFirst().orElseThrow()).get(5, TimeUnit.SECONDS);
            check(first.teleportAsync(new Location(nether, 8, 75, 8)).get(10, TimeUnit.SECONDS), "player: dimension transfer completed during lookup");
            moving.get(10, TimeUnit.SECONDS);
            check(listed("movingplayer"), "player: dispatcher resumed on current entity owner at freeze");

            CompletableFuture<Void> permit = playerCommand(first, "whitelist add PermitPlayer");
            waitRequest("permitplayer");
            onPlayer(first, () -> first.setOp(false)).get(5, TimeUnit.SECONDS);
            permit.get(10, TimeUnit.SECONDS);
            check(!listed("permitplayer"), "permission: revoked permission prevents pending add");

            Player departing = players.getLast();
            CompletableFuture<Void> leave = playerCommand(departing, "whitelist add LeavePlayer");
            waitRequest("leaveplayer");
            onPlayer(departing, () -> departing.kick(net.kyori.adventure.text.Component.text("Intentional whitelist disconnect regression")))
                .get(5, TimeUnit.SECONDS);
            try { leave.get(10, TimeUnit.SECONDS); throw new AssertionError("Disconnected command source unexpectedly completed"); }
            catch (java.util.concurrent.ExecutionException expected) { /* native retired/disconnected source */ }
            check(!listed("leaveplayer"), "disconnect: retired source cannot mutate whitelist");

            this.plugin.getLogger().info("WHITELIST_READY_RCON");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (!Files.exists(Path.of("rcon-verified")) && System.nanoTime() < deadline) Thread.sleep(100);
            check(Files.exists(Path.of("rcon-verified")), "rcon: actual socket replies verified by runner");
            deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!listed("consoleplayer") && System.nanoTime() < deadline) Thread.sleep(100);
            check(listed("consoleplayer"), "console: real stdin namespaced command completed");
            check(global(() -> Bukkit.getOnlinePlayers().size()).get(5, TimeUnit.SECONDS) == 1, "clients: one intentional disconnect, other connection retained");
            check(this.requests.stream().allMatch(name -> name.equals(name.toLowerCase(java.util.Locale.ROOT))), "http: every outgoing name normalized with Locale.ROOT");
            Files.write(Path.of("whitelist-checks.txt"), this.checks);
            Files.write(Path.of("whitelist-http-requests.txt"), this.requests);
            this.plugin.getLogger().info("WHITELIST_PASS");
        } catch (Throwable failure) {
            this.plugin.getLogger().log(java.util.logging.Level.SEVERE, "WHITELIST_FAIL", failure);
            try { Files.writeString(Path.of("whitelist-failure.txt"), failure.toString()); }
            catch (Exception ignored) {}
        } finally {
            this.lateRelease.countDown();
            if (this.http != null) this.http.stop(0);
            Bukkit.getGlobalRegionScheduler().run(this.plugin, task -> Bukkit.shutdown());
        }
    }

    private void installLocalProfiles() throws ReflectiveOperationException {
        var discovery = MinecraftServicesDiscoveryService.createOffline(Proxy.NO_PROXY);
        var endpoints = new Endpoints(Map.of("getByName", new Endpoint("http://127.0.0.1:" + this.http.getAddress().getPort() + "/profiles/{name}")));
        var response = new DiscoveryResponse("test", "minecraft", new Discovery("minecraft", Endpoints.empty(), Endpoints.empty(), Endpoints.empty(), endpoints, Endpoints.empty()));
        Field supplier = MinecraftServicesDiscoveryService.class.getDeclaredField("discoverySupplier");
        supplier.setAccessible(true);
        supplier.set(discovery, (Supplier<DiscoveryResponse>) () -> response);
        Field field = MinecraftServicesProfileRepository.class.getDeclaredField("discoveryService");
        field.setAccessible(true);
        field.set(server().services().profileRepository(), discovery);
    }

    private String command(String input) throws Exception {
        return global(() -> {
            RconConsoleSource source = new RconConsoleSource(server(), new InetSocketAddress("127.0.0.1", 0));
            return server().getCommands().performPrefixedCommandAsync(source.createCommandSourceStack(), input)
                .thenApply(ignored -> source.getCommandResponse());
        }).thenCompose(future -> future).get(18, TimeUnit.SECONDS);
    }

    private CompletableFuture<Void> playerCommand(Player player, String input) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        player.getScheduler().run(this.plugin, task -> {
            var nativePlayer = ((CraftPlayer)player).getHandle();
            CommandSourceStack source = nativePlayer.createCommandSourceStack();
            server().getCommands().performPrefixedCommandAsync(source, input).whenComplete((ignored, failure) -> {
                if (failure == null) result.complete(null); else result.completeExceptionally(failure);
            });
        }, () -> result.completeExceptionally(new IllegalStateException("Retired source")));
        return result;
    }

    private CompletableFuture<Void> onPlayer(Player player, Runnable action) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        player.getScheduler().run(this.plugin, task -> {
            try { action.run(); result.complete(null); } catch (Throwable failure) { result.completeExceptionally(failure); }
        }, () -> result.completeExceptionally(new IllegalStateException("Retired player")));
        return result;
    }

    private <T> CompletableFuture<T> global(java.util.concurrent.Callable<T> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Bukkit.getGlobalRegionScheduler().run(this.plugin, task -> {
            try { result.complete(action.call()); } catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        return result;
    }

    private void waitRequest(String name) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (count(name) == 0 && System.nanoTime() < deadline) Thread.sleep(20);
        check(count(name) > 0, "request: " + name + " reached real HTTP client");
    }

    private boolean listed(String name) {
        return server().getPlayerList().getWhiteList().getEntries().stream()
            .anyMatch(entry -> entry.getUser() != null && entry.getUser().name().equalsIgnoreCase(name));
    }

    private long count(String name) { return this.requests.stream().filter(name::equals).count(); }
    private static UUID id(String name) { return UUID.nameUUIDFromBytes(("native-whitelist:" + name).getBytes(StandardCharsets.UTF_8)); }
    private static MinecraftServer server() { return ((CraftServer)Bukkit.getServer()).getServer(); }
    private void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        this.checks.add(message);
    }
}
