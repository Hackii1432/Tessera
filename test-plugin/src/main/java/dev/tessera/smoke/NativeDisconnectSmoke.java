package dev.tessera.smoke;

import ca.spottedleaf.moonrise.common.util.TickThread;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.papermc.paper.connection.DisconnectionReason;
import io.papermc.paper.threadedregions.RegionizedServer;
import io.papermc.paper.threadedregions.TickRegionScheduler;
import io.papermc.paper.util.KeepAlive;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Isolated native connection regression; never loaded by a production plugin or server. */
final class NativeDisconnectSmoke implements Listener {
    private final JavaPlugin plugin;
    private final AtomicBoolean started = new AtomicBoolean(), cancelOne = new AtomicBoolean(true);
    private final AtomicInteger globalTicks = new AtomicInteger();
    private final ConcurrentHashMap<String, List<PlayerKickEvent.Cause>> kicks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> quits = new ConcurrentHashMap<>();
    private final List<String> checks = new CopyOnWriteArrayList<>();

    NativeDisconnectSmoke(JavaPlugin plugin) { this.plugin = plugin; }

    void start() {
        Bukkit.getPluginManager().registerEvents(this, this.plugin);
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(this.plugin, task -> {
            this.globalTicks.incrementAndGet();
            if (Bukkit.getOnlinePlayers().size() == 3 && this.started.compareAndSet(false, true)) {
                Thread.ofPlatform().daemon().name("Tessera disconnect acceptance").start(this::test);
            }
        }, 1L, 1L);
        this.plugin.getLogger().info("DISCONNECT_READY_CLIENTS");
    }

    @EventHandler
    public void kick(PlayerKickEvent event) {
        check(TickThread.isTickThreadFor(((CraftPlayer) event.getPlayer()).getHandle()), "kick event belongs to player owner");
        this.kicks.computeIfAbsent(event.getPlayer().getName(), ignored -> new CopyOnWriteArrayList<>()).add(event.getCause());
        if (event.getPlayer().getName().equals("DisconnectOne") && this.cancelOne.get()) event.setCancelled(true);
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        this.quits.computeIfAbsent(event.getPlayer().getName(), ignored -> new AtomicInteger()).incrementAndGet();
    }

    private void test() {
        try {
            Player one = global(() -> Bukkit.getPlayerExact("DisconnectOne")).get(10, TimeUnit.SECONDS);
            Player two = global(() -> Bukkit.getPlayerExact("DisconnectTwo")).get(10, TimeUnit.SECONDS);
            Player normal = global(() -> Bukkit.getPlayerExact("NormalQuit")).get(10, TimeUnit.SECONDS);
            Connection normalConnection = onPlayer(normal, () -> connection(normal)).get(10, TimeUnit.SECONDS);
            var world = global(() -> Bukkit.getWorlds().getFirst()).get(10, TimeUnit.SECONDS);
            check(onPlayer(two, () -> two.teleportAsync(new Location(world, 4096.5, 6, 0.5))).get(10, TimeUnit.SECONDS)
                .get(30, TimeUnit.SECONDS), "second player transferred to a distant region");
            Object firstOwner = onPlayer(one, TickRegionScheduler::getCurrentRegion).get(10, TimeUnit.SECONDS);
            Object secondOwner = onPlayer(two, TickRegionScheduler::getCurrentRegion).get(10, TimeUnit.SECONDS);
            check(firstOwner != secondOwner, "two distinct native region owners");
            global(() -> { server().tickRateManager().setFrozen(true); return null; }).get(10, TimeUnit.SECONDS);
            check(server().tickRateManager().isFrozen(), "gameplay freeze enabled for all disconnect cases");

            onPlayer(one, () -> { one.kick(net.kyori.adventure.text.Component.text("cancelled fixture kick")); return null; })
                .get(10, TimeUnit.SECONDS);
            Thread.sleep(1250);
            onPlayer(one, () -> {
                Connection connection = connection(one);
                check(!connection.handleConnectionDisconnectOnNextTick && connection.channel.config().isAutoRead(),
                    "cancelled kick kept connection active and readable");
                return null;
            }).get(10, TimeUnit.SECONDS);

            Connection accepted = onPlayer(one, () -> connection(one)).get(10, TimeUnit.SECONDS);
            HoldDisconnect first = hold(accepted);
            this.cancelOne.set(false);
            onPlayer(one, () -> {
                one.kick(net.kyori.adventure.text.Component.text("original accepted fixture kick"));
                check(accepted.handleConnectionDisconnectOnNextTick && accepted.isConnected(), "accepted kick sealed before write completion");
                // Fault injection only: an already expired challenge behind a delayed native disconnect must not cause retries.
                var keepAliveField = ServerCommonPacketListenerImpl.class.getDeclaredField("keepAlive");
                keepAliveField.setAccessible(true);
                KeepAlive keepAlive = (KeepAlive) keepAliveField.get(((CraftPlayer) one).getHandle().connection);
                keepAlive.pendingKeepAlives.clear();
                keepAlive.pendingKeepAlives.add(new KeepAlive.PendingKeepAlive(System.nanoTime() - TimeUnit.MINUTES.toNanos(1), 424242L));
                return null;
            }).get(10, TimeUnit.SECONDS);
            check(first.arrived.await(10, TimeUnit.SECONDS), "native disconnect reached Netty hold");
            int before = this.globalTicks.get();
            Thread.sleep(2200);
            accepted.disconnectSafely(new DisconnectionDetails(Component.literal("obsolete timeout"), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of(DisconnectionReason.TIMEOUT)));
            onPlayer(one, () -> { one.kick(net.kyori.adventure.text.Component.text("obsolete second kick")); return null; }).get(10, TimeUnit.SECONDS);
            check(this.globalTicks.get() > before + 10, "global ticks continued while disconnect write was delayed at freeze");
            check(first.reasons.equals(List.of("original accepted fixture kick")), "only original accepted packet written");
            check(this.kicks.get("DisconnectOne").equals(List.of(PlayerKickEvent.Cause.PLUGIN, PlayerKickEvent.Cause.PLUGIN)),
                "one cancelled and one accepted kick event, no timeout retries");
            first.release();
            awaitQuit("DisconnectOne");
            check(accepted.getDisconnectionDetails().reason().getString().equals("original accepted fixture kick"), "accepted reason preserved through close");

            Connection timedOut = onPlayer(two, () -> connection(two)).get(10, TimeUnit.SECONDS);
            HoldDisconnect second = hold(timedOut);
            this.plugin.getLogger().info("DISCONNECT_ARM_GAME_TIMEOUT");
            check(second.arrived.await(15, TimeUnit.SECONDS), "real unanswered keepalive reached native timeout");
            Thread.sleep(2200);
            check(this.kicks.get("DisconnectTwo").equals(List.of(PlayerKickEvent.Cause.TIMEOUT)), "exactly one native timeout event");
            check(second.reasons.size() == 1 && timedOut.isConnected() && timedOut.handleConnectionDisconnectOnNextTick,
                "region timeout packet pending without repeated disconnects");
            second.release();
            awaitQuit("DisconnectTwo");

            this.plugin.getLogger().info("DISCONNECT_READY_CONFIG");
            Connection configuration = null;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            while (configuration == null && System.nanoTime() < deadline) {
                configuration = global(() -> globalConnections().stream()
                    .filter(candidate -> candidate.getPacketListener() instanceof ServerConfigurationPacketListenerImpl listener
                        && listener.getOwner().name().equals("ConfigDrop")).findFirst().orElse(null)).get(10, TimeUnit.SECONDS);
                if (configuration == null) Thread.sleep(20);
            }
            check(configuration != null, "configuration-stage connection owned by global tick");
            HoldDisconnect config = hold(configuration);
            this.plugin.getLogger().info("DISCONNECT_ARM_CONFIG_TIMEOUT");
            check(config.arrived.await(15, TimeUnit.SECONDS), "configuration keepalive timeout reached Netty");
            before = this.globalTicks.get();
            Thread.sleep(2200);
            check(configuration.handleConnectionDisconnectOnNextTick && configuration.isConnected() && config.reasons.size() == 1,
                "global configuration timeout written exactly once");
            check(this.globalTicks.get() > before + 10, "global maintenance continues during configuration disconnect");
            config.release();
            Connection closedConfiguration = configuration;
            deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            boolean configurationRemoved = false;
            while (!configurationRemoved && System.nanoTime() < deadline) {
                configurationRemoved = global(() -> !globalConnections().contains(closedConfiguration))
                    .get(10, TimeUnit.SECONDS);
                if (!configurationRemoved) Thread.sleep(20);
            }
            check(configurationRemoved, "configuration connection removed by global cleanup");

            this.plugin.getLogger().info("DISCONNECT_NORMAL_QUIT");
            awaitQuit("NormalQuit");
            check(!this.kicks.containsKey("NormalQuit"), "normal client close did not trigger any kick event");
            check(this.quits.values().stream().allMatch(count -> count.get() == 1), "all gameplay quit events exactly once");
            // Quit fires inside native removal, before the player list and region/global registries are cleared.
            // Require the actual asynchronous cleanup to finish instead of racing the event callback.
            deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            boolean gameplayRemoved = false;
            while (!gameplayRemoved && System.nanoTime() < deadline) {
                gameplayRemoved = global(() -> Bukkit.getOnlinePlayers().isEmpty()
                    && java.util.Collections.disjoint(globalConnections(), List.of(accepted, timedOut, normalConnection)))
                    .get(10, TimeUnit.SECONDS);
                if (!gameplayRemoved) Thread.sleep(20);
            }
            check(gameplayRemoved, "all native gameplay connections removed");
            Files.write(Path.of("disconnect-checks.txt"), this.checks);
            this.plugin.getLogger().info("DISCONNECT_PASS " + this.checks.size());
        } catch (Throwable failure) {
            failure.printStackTrace();
            this.plugin.getLogger().severe("DISCONNECT_FAIL " + failure);
        } finally {
            Bukkit.getGlobalRegionScheduler().execute(this.plugin, Bukkit::shutdown);
        }
    }

    private HoldDisconnect hold(Connection connection) throws Exception {
        HoldDisconnect held = new HoldDisconnect();
        connection.channel.eventLoop().submit(() -> connection.channel.pipeline().addBefore("packet_handler", "tessera_disconnect_fixture", held))
            .get(10, TimeUnit.SECONDS);
        return held;
    }

    private void awaitQuit(String name) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!this.quits.containsKey(name) && System.nanoTime() < deadline) Thread.sleep(20);
        check(this.quits.containsKey(name) && this.quits.get(name).get() == 1, "native quit completed once for " + name);
    }

    private <T> CompletableFuture<T> global(Callable<T> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Bukkit.getGlobalRegionScheduler().execute(this.plugin, () -> complete(action, result));
        return result;
    }

    private <T> CompletableFuture<T> onPlayer(Player player, Callable<T> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        boolean accepted = player.getScheduler().execute(this.plugin, () -> {
            try {
                check(TickThread.isTickThreadFor(((CraftPlayer) player).getHandle()), "player work executed on current native owner");
                complete(action, result);
            } catch (Throwable failure) { result.completeExceptionally(failure); }
        }, () -> result.completeExceptionally(new IllegalStateException("Fixture player retired")), 1L);
        if (!accepted) result.completeExceptionally(new IllegalStateException("Fixture scheduling rejected"));
        return result;
    }

    private static <T> void complete(Callable<T> action, CompletableFuture<T> result) {
        try { result.complete(action.call()); }
        catch (Throwable failure) { result.completeExceptionally(failure); }
    }

    private static Connection connection(Player player) { return ((CraftPlayer) player).getHandle().connection.connection; }
    private static MinecraftServer server() { return ((CraftServer) Bukkit.getServer()).getServer(); }

    @SuppressWarnings("unchecked")
    private static List<Connection> globalConnections() throws ReflectiveOperationException {
        if (!RegionizedServer.isGlobalTickThread()) throw new AssertionError("Native connection registry requires global owner");
        // The ordinary Paper listener list is unused by Folia. Inspect the actual private registry only in this fixture.
        var connections = RegionizedServer.class.getDeclaredField("connections");
        connections.setAccessible(true);
        return List.copyOf((List<Connection>) connections.get(RegionizedServer.getInstance()));
    }

    private void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        this.checks.add(message);
    }

    private static final class HoldDisconnect extends ChannelOutboundHandlerAdapter {
        final CountDownLatch arrived = new CountDownLatch(1);
        final List<String> reasons = new CopyOnWriteArrayList<>();
        private ChannelHandlerContext context;
        private ClientboundDisconnectPacket packet;
        private ChannelPromise promise;

        @Override
        public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
            if (message instanceof ClientboundDisconnectPacket packet) {
                this.reasons.add(packet.reason().getString());
                this.context = context;
                this.packet = packet;
                this.promise = promise;
                this.arrived.countDown();
            } else {
                super.write(context, message, promise);
            }
        }

        void release() throws Exception {
            this.context.executor().submit(() -> this.context.writeAndFlush(this.packet, this.promise)).get(10, TimeUnit.SECONDS);
        }
    }
}
