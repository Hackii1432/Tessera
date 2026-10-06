package io.papermc.paper.configuration;

import com.mojang.serialization.Codec;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.BucketResultScope;
import io.papermc.paper.util.sanitizer.ItemObfuscationSession;
import io.papermc.paper.util.sanitizer.OversizedItemComponentSanitizer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.EncoderCache;
import net.minecraft.util.TickThrottler;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.bukkit.Instrument;
import org.bukkit.Registry;
import org.bukkit.block.BlockType;
import org.bukkit.craftbukkit.block.CraftBlockStates;
import org.bukkit.entity.Player;
import org.bukkit.support.RegistryHelper;
import org.bukkit.support.environment.VanillaFeature;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

@VanillaFeature
public class PaperOctoberRegressionTest {
    @Test
    void bundleCodecsCarryCacheMarker() {
        assertInstanceOf(OversizedItemComponentSanitizer.ObfuscationDependantCodec.class, OversizedItemComponentSanitizer.BUNDLE_CONTENTS);
        assertInstanceOf(OversizedItemComponentSanitizer.ObfuscationDependantCodec.class, OversizedItemComponentSanitizer.BUNDLE_CONTENTS_STREAM);
    }

    @Test
    void actualBucketPlacementPublishesOnlyAfterSuccessfulSetBlock() {
        var level = Mockito.mock(net.minecraft.world.level.Level.class, Mockito.RETURNS_DEEP_STUBS);
        var player = Mockito.mock(net.minecraft.world.entity.player.Player.class);
        var position = BlockPos.ZERO;
        var stack = new ItemStack(Items.WATER_BUCKET);
        var event = Mockito.mock(org.bukkit.event.player.PlayerBucketEmptyEvent.class);
        Mockito.when(level.getBlockState(position)).thenReturn(Blocks.AIR.defaultBlockState());
        Mockito.when(level.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.WATER_EVAPORATES, position)).thenReturn(false);
        Mockito.when(event.getItemStack()).thenReturn(new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND));
        try (var factory = Mockito.mockStatic(org.bukkit.craftbukkit.event.CraftEventFactory.class)) {
            factory.when(() -> org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerBucketEmptyEvent(level, player,
                position, position, net.minecraft.core.Direction.UP, stack, net.minecraft.world.InteractionHand.MAIN_HAND)).thenReturn(event);
            var bucket = (net.minecraft.world.item.BucketItem) Items.WATER_BUCKET;
            try (var use = BucketResultScope.openUse()) {
                assertFalse(bucket.emptyContents(player, level, position, null, net.minecraft.core.Direction.UP,
                    position, stack, net.minecraft.world.InteractionHand.MAIN_HAND));
                assertNull(BucketResultScope.takeUseResult());
            }
            Mockito.when(level.setBlock(Mockito.eq(position), Mockito.any(), Mockito.anyInt())).thenReturn(true);
            try (var use = BucketResultScope.openUse()) {
                assertTrue(bucket.emptyContents(player, level, position, null, net.minecraft.core.Direction.UP,
                    position, stack, net.minecraft.world.InteractionHand.MAIN_HAND));
                assertTrue(java.util.Objects.requireNonNull(BucketResultScope.takeUseResult()).is(Items.DIAMOND));
            }
            assertNull(BucketResultScope.takeUseResult());
        }
    }

    @Test
    void bundleCacheSeparatesBothSerializationOrdersAndParallelSessions() {
        GlobalConfiguration previous = GlobalConfiguration.get();
        GlobalConfiguration config = new GlobalConfiguration();
        config.unsupportedSettings = config.new UnsupportedSettings();
        GlobalConfiguration.set(config);
        try {
            BundleContents value = new BundleContents(List.of(new ItemStackTemplate(Items.DIAMOND, 5)));
            var ops = RegistryOps.create(NbtOps.INSTANCE, RegistryHelper.registryAccess());
            for (boolean networkFirst : List.of(true, false)) {
                Codec<BundleContents> codec = new EncoderCache(64).wrap(OversizedItemComponentSanitizer.BUNDLE_CONTENTS);
                if (!networkFirst) assertEquals(value, codec.parse(ops, codec.encodeStart(ops, value).getOrThrow()).getOrThrow());
                Tag network;
                try (var session = ItemObfuscationSession.start(ItemObfuscationSession.ObfuscationLevel.OVERSIZED)) {
                    network = codec.encodeStart(ops, value).getOrThrow();
                    assertTrue(network.toString().contains("minecraft:paper"));
                }
                Tag disk = codec.encodeStart(ops, value).getOrThrow();
                assertNotEquals(network, disk);
                assertEquals(value, codec.parse(ops, disk).getOrThrow());
                CompletableFuture<?>[] work = new CompletableFuture<?>[8];
                for (int i = 0; i < work.length; i++) {
                    final boolean obfuscated = i % 2 == 0;
                    work[i] = CompletableFuture.runAsync(() -> {
                        for (int j = 0; j < 100; j++) {
                            try (var session = ItemObfuscationSession.start(obfuscated ? ItemObfuscationSession.ObfuscationLevel.ALL : ItemObfuscationSession.ObfuscationLevel.NONE)) {
                                assertEquals(obfuscated ? network : disk, codec.encodeStart(ops, value).getOrThrow());
                            }
                        }
                    });
                }
                CompletableFuture.allOf(work).join();
            }
        } finally {
            GlobalConfiguration.set(previous);
        }
    }

    @Test
    void disabledCombinedSpamThresholdDoesNotAccumulate() {
        TickThrottler throttler = new TickThrottler(0, 0);
        for (int i = 0; i < 100; i++) assertTrue(throttler.isIncrementAndUnderThreshold(20, 0));
        assertTrue(throttler.isIncrementAndUnderThreshold(1, 2));
        assertFalse(throttler.isIncrementAndUnderThreshold(1, 2));
        assertTrue(throttler.isIncrementAndUnderThreshold(100, -1));
    }

    @Test
    void reusedViewerUnawareRendererDoesNotReuseOldMessage() {
        ChatRenderer renderer = ChatRenderer.viewerUnaware((source, name, message) -> name.append(message));
        Player player = Mockito.mock(Player.class);
        assertEquals(Component.text("One").append(Component.text("first")), renderer.render(player, Component.text("One"), Component.text("first"), player));
        assertEquals(Component.text("Two").append(Component.text("second")), renderer.render(player, Component.text("Two"), Component.text("second"), player));
        assertSame(ChatRenderer.defaultRenderer(), ChatRenderer.defaultRenderer());
    }

    @Test
    void blockTypeInstrumentMatchesBlockData() {
        assertEquals(Instrument.BELL, BlockType.GOLD_BLOCK.getInstrument());
        Registry.BLOCK.stream().forEach(type -> assertEquals(org.bukkit.craftbukkit.block.data.CraftBlockData.fromVanilla(
            ((org.bukkit.craftbukkit.block.data.CraftBlockData) type.createBlockData()).getState().instrument(), Instrument.class),
            type.getInstrument(), type.getKey().toString()));
    }

    @Test
    void explicitBlockSnapshotFlagsAreIndependentAcrossThreads() {
        CompletableFuture<?>[] tasks = new CompletableFuture<?>[8];
        for (int i = 0; i < tasks.length; i++) {
            final boolean snapshot = i % 2 == 0;
            tasks[i] = CompletableFuture.runAsync(() -> {
                for (int j = 0; j < 50; j++) {
                    var state = Blocks.CHEST.defaultBlockState();
                    var entity = new ChestBlockEntity(BlockPos.ZERO, state);
                    assertEquals(snapshot, ((org.bukkit.block.TileState) CraftBlockStates.getBlockState(null, BlockPos.ZERO, state, entity, snapshot)).isSnapshot());
                }
            });
        }
        CompletableFuture.allOf(tasks).join();
    }

    @Test
    void bucketResultScopesHandleFailureCancellationReentrancyAndCleanup() {
        ItemStack outer = new ItemStack(Items.DIAMOND);
        ItemStack inner = new ItemStack(Items.EMERALD);
        try (var use = BucketResultScope.openUse()) {
            try (var placement = BucketResultScope.openPlacement()) {
                try (var nestedUse = BucketResultScope.openUse()) {
                    try (var nestedPlacement = BucketResultScope.openPlacement()) {
                        nestedPlacement.setResult(inner);
                        nestedPlacement.completePlacement(true);
                    }
                    assertSame(inner, BucketResultScope.takeUseResult());
                }
                try (var directNestedPlacement = BucketResultScope.openPlacement()) {
                    directNestedPlacement.setResult(inner);
                    directNestedPlacement.completePlacement(true);
                }
                placement.setResult(outer);
                placement.completePlacement(true);
            }
            assertSame(outer, BucketResultScope.takeUseResult());
            assertNull(BucketResultScope.takeUseResult());
        }
        try (var use = BucketResultScope.openUse()) {
            try (var failed = BucketResultScope.openPlacement()) {
                failed.setResult(outer);
                failed.completePlacement(false);
            }
            assertNull(BucketResultScope.takeUseResult());
            assertThrows(IllegalStateException.class, () -> {
                try (var throwing = BucketResultScope.openPlacement()) {
                    throwing.setResult(inner);
                    throw new IllegalStateException("event failure");
                }
            });
            assertNull(BucketResultScope.takeUseResult());
        }
        assertNull(BucketResultScope.takeUseResult());
    }

    @Test
    void bucketScopesAreThreadIsolatedAndEmptyResultsAreNotNull() {
        CompletableFuture<?>[] tasks = new CompletableFuture<?>[8];
        for (int i = 0; i < tasks.length; i++) {
            final ItemStack result = new ItemStack(i % 2 == 0 ? Items.DIAMOND : Items.EMERALD);
            tasks[i] = CompletableFuture.runAsync(() -> {
                for (int j = 0; j < 100; j++) {
                    try (var use = BucketResultScope.openUse()) {
                        try (var placement = BucketResultScope.openPlacement()) {
                            placement.setResult(result);
                            placement.completePlacement(true);
                        }
                        assertSame(result, BucketResultScope.takeUseResult());
                    }
                    assertNull(BucketResultScope.takeUseResult());
                }
            });
        }
        CompletableFuture.allOf(tasks).join();
        try (var use = BucketResultScope.openUse()) {
            try (var placement = BucketResultScope.openPlacement()) {
                placement.setResult(ItemStack.EMPTY);
                placement.completePlacement(true);
            }
            assertSame(ItemStack.EMPTY, BucketResultScope.takeUseResult());
        }
        assertNull(BucketResultScope.takeUseResult());
    }
    @Test
    void gameMasterCodecRejectsBeforeDecoderOrEncoderWork() {
        var context = Mockito.mock(net.minecraft.network.protocol.game.GameProtocols.Context.class);
        @SuppressWarnings("unchecked")
        net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, net.minecraft.network.protocol.game.ServerboundTestInstanceBlockActionPacket> delegate = Mockito.mock(net.minecraft.network.codec.StreamCodec.class);
        var secured = net.minecraft.network.protocol.game.GameProtocols.IS_GAME_MASTER.apply(delegate, context);
        var buffer = Mockito.mock(net.minecraft.network.RegistryFriendlyByteBuf.class);
        var packet = Mockito.mock(net.minecraft.network.protocol.game.ServerboundTestInstanceBlockActionPacket.class);
        assertThrows(net.minecraft.network.SkipPacketDecoderException.class, () -> secured.decode(buffer));
        assertThrows(net.minecraft.network.SkipPacketEncoderException.class, () -> secured.encode(buffer, packet));
        Mockito.verifyNoInteractions(delegate);
        Mockito.when(context.canUseCommandBlocks()).thenReturn(true);
        Mockito.when(delegate.decode(buffer)).thenReturn(packet);
        assertSame(packet, secured.decode(buffer));
        secured.encode(buffer, packet);
        Mockito.verify(delegate).encode(buffer, packet);
    }

    @Test
    void basicCommandDeclaresCheckedSyntaxErrors() throws Exception {
        var method = io.papermc.paper.command.brigadier.BasicCommand.class.getMethod("execute",
            io.papermc.paper.command.brigadier.CommandSourceStack.class, String[].class);
        assertArrayEquals(new Class<?>[] {com.mojang.brigadier.exceptions.CommandSyntaxException.class}, method.getExceptionTypes());
    }

    @Test
    void registryConcurrentReadsKeepWrapperIdentity() {
        Object expected = Registry.BLOCK.get(org.bukkit.NamespacedKey.minecraft("gold_block"));
        CompletableFuture<?>[] tasks = new CompletableFuture<?>[8];
        for (int i = 0; i < tasks.length; i++) {
            tasks[i] = CompletableFuture.runAsync(() -> {
                for (int j = 0; j < 500; j++) assertSame(expected, Registry.BLOCK.get(org.bukkit.NamespacedKey.minecraft("gold_block")));
            });
        }
        CompletableFuture.allOf(tasks).join();
    }
    @Test
    void postEffectsPacketDoesNotRetainMutablePlayerList() throws Exception {
        var player = Mockito.mock(net.minecraft.server.level.ServerPlayer.class);
        var effects = new java.util.ArrayList<net.minecraft.resources.Identifier>();
        effects.add(net.minecraft.resources.Identifier.withDefaultNamespace("blur"));
        var field = net.minecraft.world.entity.player.Player.class.getDeclaredField("postEffects");
        field.setAccessible(true);
        field.set(player, effects);
        player.connection = Mockito.mock(net.minecraft.server.network.ServerGamePacketListenerImpl.class);
        Mockito.doCallRealMethod().when(player).sendPostEffects();
        player.sendPostEffects();
        var captor = org.mockito.ArgumentCaptor.forClass(net.minecraft.network.protocol.Packet.class);
        Mockito.verify(player.connection).send(captor.capture());
        var packet = (net.minecraft.network.protocol.common.ClientboundPostEffectsPacket) captor.getValue();
        effects.clear();
        assertEquals(1, packet.postEffects().size());
        assertThrows(UnsupportedOperationException.class, () -> packet.postEffects().clear());
    }
}
