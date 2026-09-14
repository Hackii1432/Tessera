package io.papermc.paper.porting;

import io.papermc.paper.configuration.WorldConfiguration;
import io.papermc.paper.event.entity.EntityBreakEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Prediction;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.entity.decoration.Cushion;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftRegionAccessor;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.entity.CraftCushion;
import org.bukkit.craftbukkit.entity.CraftEntityTypes;
import org.bukkit.craftbukkit.entity.CraftExperienceOrb;
import org.bukkit.craftbukkit.entity.CraftItemFrame;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.Event;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.spigotmc.SpigotWorldConfig;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Executes the backported methods with mocked world and plugin boundaries, not a live region scheduler. */
@AllFeatures
class PaperRc3EntityBackportTest {
    private static void setField(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true"})
    void cushionMovementDoesNotCastToHanging(boolean removed, boolean stationary) {
        ServerLevel level = mock(ServerLevel.class);
        Cushion cushion = mock(Cushion.class);
        when(cushion.level()).thenReturn(level);
        when(cushion.getBukkitEntity()).thenReturn(mock(CraftCushion.class));
        when(cushion.isRemoved()).thenReturn(removed);
        doCallRealMethod().when(cushion).move(any(), any());

        assertDoesNotThrow(() -> cushion.move(MoverType.PISTON, stationary ? Vec3.ZERO : new Vec3(0.5, 0, 0)));
        int expected = removed || stationary ? 0 : 1;
        verify(cushion, times(expected)).kill(level);
        verify(cushion, times(expected)).dropItem(level, null);
        verifyNoInteractions(level);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void hangingMovementStillHonorsThePluginCancellation(boolean cancel) {
        ServerLevel level = mock(ServerLevel.class);
        CraftServer server = mock(CraftServer.class);
        PluginManager plugins = mock(PluginManager.class);
        when(level.getCraftServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(plugins);
        BlockAttachedEntity entity = mock(BlockAttachedEntity.class);
        when(entity.level()).thenReturn(level);
        when(entity.getBukkitEntity()).thenReturn(mock(CraftItemFrame.class));
        doCallRealMethod().when(entity).move(any(), any());
        doAnswer(invocation -> {
            HangingBreakEvent event = invocation.getArgument(0);
            assertEquals(HangingBreakEvent.RemoveCause.PHYSICS, event.getCause());
            event.setCancelled(cancel);
            return null;
        }).when(plugins).callEvent(any(HangingBreakEvent.class));

        entity.move(MoverType.PISTON, new Vec3(0.5, 0, 0));
        verify(plugins, times(1)).callEvent(any(HangingBreakEvent.class));
        verify(entity, times(cancel ? 0 : 1)).kill(level);
        verify(entity, times(cancel ? 0 : 1)).dropItem(level, null);
    }

    @ParameterizedTest
    @CsvSource({"air,false", "stone,false", "bars,false", "air,true", "stone,true", "bars,true"})
    void attachmentBreakCauseMatchesTheBlockAndRetainsCancellation(String block, boolean cancel) throws Exception {
        ServerLevel level = mock(ServerLevel.class);
        SpigotWorldConfig config = mock(SpigotWorldConfig.class);
        setField(Level.class, level, "spigotConfig", config);
        BlockState state = switch (block) {
            case "air" -> Blocks.AIR.defaultBlockState();
            case "stone" -> Blocks.STONE.defaultBlockState();
            default -> Blocks.IRON_BARS.defaultBlockState();
        };
        when(level.getBlockState(BlockPos.ZERO)).thenReturn(state);
        BlockAttachedEntity entity = mock(BlockAttachedEntity.class);
        when(entity.level()).thenReturn(level);
        when(entity.blockPosition()).thenReturn(BlockPos.ZERO);
        when(entity.getBukkitEntity()).thenReturn(mock(CraftItemFrame.class));
        doCallRealMethod().when(entity).tick();
        PluginManager plugins = mock(PluginManager.class);
        List<Event> events = new ArrayList<>();
        doAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            events.add(event);
            if (event instanceof EntityBreakEvent broken) {
                assertEquals(state.isAir() ? EntityBreakEvent.RemoveCause.PHYSICS : EntityBreakEvent.RemoveCause.OBSTRUCTION, broken.getCause());
                broken.setCancelled(cancel);
            } else if (event instanceof HangingBreakEvent hanging) {
                assertEquals(cancel, hanging.isCancelled(), "Forward cancellation to the legacy Hanging event");
                assertEquals(state.isAir() ? HangingBreakEvent.RemoveCause.PHYSICS : HangingBreakEvent.RemoveCause.OBSTRUCTION, hanging.getCause());
            }
            return null;
        }).when(plugins).callEvent(any());
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            entity.tick();
        }
        assertEquals(2, events.size());
        verify(entity, times(cancel ? 0 : 1)).discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DROP);
        verify(entity, times(cancel ? 0 : 1)).dropItem(level, null);
    }

    @ParameterizedTest
    @CsvSource({"0,false", "3,false", "0,true", "3,true"})
    void experienceOrbsFireOneSpawnEventBeforeMergingAndRespectCancellation(double radius, boolean cancel) throws Exception {
        ServerLevel level = mock(ServerLevel.class);
        SpigotWorldConfig config = mock(SpigotWorldConfig.class);
        config.expMerge = radius;
        setField(Level.class, level, "spigotConfig", config);
        WorldConfiguration paper = mock(WorldConfiguration.class);
        paper.entities = mock(WorldConfiguration.Entities.class);
        paper.entities.behavior = mock(WorldConfiguration.Entities.Behavior.class);
        when(level.paperConfig()).thenReturn(paper);
        ExperienceOrb orb = mock(ExperienceOrb.class);
        when(orb.getBoundingBox()).thenReturn(new AABB(0, 0, 0, 1, 1, 1));
        CraftExperienceOrb craft = mock(CraftExperienceOrb.class);
        when(orb.getBukkitEntity()).thenReturn(craft);
        CraftServer server = mock(CraftServer.class);
        PluginManager plugins = mock(PluginManager.class);
        when(craft.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(plugins);
        doAnswer(invocation -> {
            EntitySpawnEvent event = invocation.getArgument(0);
            assertSame(craft, event.getEntity());
            verify(level, never()).getEntities(eq(orb), any(AABB.class));
            event.setCancelled(cancel);
            return null;
        }).when(plugins).callEvent(any(EntitySpawnEvent.class));

        assertEquals(!cancel, CraftEventFactory.doEntityAddEventCalling(level, orb, CreatureSpawnEvent.SpawnReason.CUSTOM));
        verify(plugins, times(1)).callEvent(any(EntitySpawnEvent.class));
        verify(orb, times(cancel ? 1 : 0)).discard(null);
        verify(level, times(!cancel && radius > 0 ? 1 : 0)).getEntities(eq(orb), any(AABB.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @SuppressWarnings("unchecked")
    void explicitPluginCreationInPeacefulRetainsTheFeatureGate(boolean enabled) throws Exception {
        ServerLevel level = mock(ServerLevel.class);
        when(level.getDifficulty()).thenReturn(Difficulty.PEACEFUL);
        when(level.getMinecraftWorld()).thenReturn(level);
        CraftRegionAccessor accessor = mock(CraftRegionAccessor.class);
        when(accessor.getHandle()).thenReturn(level);
        when(accessor.isEnabled(org.bukkit.entity.EntityType.ZOMBIE)).thenReturn(enabled);
        doCallRealMethod().when(accessor).createEntity(any(), any(), anyBoolean());
        EntityType<Zombie> type = mock(EntityType.class);
        Zombie zombie = mock(Zombie.class);
        EntitySpawnRequest request = new EntitySpawnRequest(EntitySpawnReason.COMMAND, true);
        when(type.create(level, request)).thenReturn(zombie);
        Method method = CraftEntityTypes.class.getDeclaredMethod("fromEntityType", EntityType.class);
        method.setAccessible(true);
        Function<CraftEntityTypes.SpawnData, Zombie> factory = (Function<CraftEntityTypes.SpawnData, Zombie>) method.invoke(null, type);
        CraftEntityTypes.EntityTypeData<org.bukkit.entity.Zombie, Zombie> data = new CraftEntityTypes.EntityTypeData<>(
            org.bukkit.entity.EntityType.ZOMBIE, org.bukkit.entity.Zombie.class, null, factory);
        try (MockedStatic<CraftEntityTypes> types = mockStatic(CraftEntityTypes.class)) {
            types.when(() -> CraftEntityTypes.getEntityTypeData(org.bukkit.entity.Zombie.class)).thenReturn(data);
            Location location = new Location(null, 8, 64, 8);
            if (enabled) {
                assertSame(zombie, accessor.createEntity(location, org.bukkit.entity.Zombie.class, false));
                verify(type).create(level, request);
            } else {
                IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> accessor.createEntity(location, org.bukkit.entity.Zombie.class, false));
                assertTrue(error.getMessage().contains("not an enabled feature"));
                verifyNoInteractions(type);
            }
        }
    }

    @Test
    void normalEntityCreationStillRejectsMonstersInPeaceful() {
        ServerLevel level = mock(ServerLevel.class);
        when(level.enabledFeatures()).thenReturn(FeatureFlags.REGISTRY.allFlags());
        when(level.getDifficulty()).thenReturn(Difficulty.PEACEFUL);
        assertFalse(EntityTypes.ZOMBIE.canSpawn(level));
        assertNull(EntityTypes.ZOMBIE.create(level, EntitySpawnReason.NATURAL));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void movementKickUsesTheExplicitCauseWithoutChangingTheThreadCheck(boolean duplicate) throws Exception {
        ServerLevel level = mock(ServerLevel.class);
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.level()).thenReturn(level);
        when(player.isImmobile()).thenReturn(true);
        ServerGamePacketListenerImpl listener = mock(ServerGamePacketListenerImpl.class);
        listener.player = player;
        setField(ServerGamePacketListenerImpl.class, listener, "receivedPositionThisTick", duplicate);
        doCallRealMethod().when(listener).handleMovePlayer(any());
        ServerboundMovePlayerPacket packet = mock(ServerboundMovePlayerPacket.class);
        when(packet.hasPosition()).thenReturn(true);
        try (MockedStatic<PacketUtils> packets = mockStatic(PacketUtils.class)) {
            listener.handleMovePlayer(packet);
            packets.verify(() -> PacketUtils.ensureRunningOnSameThread(packet, listener, level));
        }
        verify(listener, times(duplicate ? 1 : 0)).disconnect(any(Component.class), eq(PlayerKickEvent.Cause.INVALID_PLAYER_MOVEMENT));
        verify(listener, never()).disconnect(any(Component.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"filled", "monument", "mansion", "trial", "jungle", "swamp", "stick", "cancelled", "missing"})
    void droppedMapVariantsUseTheExistingMapUpdatePath(String variant) {
        ItemStack stack = new ItemStack(switch (variant) {
            case "monument" -> Items.OCEAN_MONUMENT_MAP;
            case "mansion" -> Items.WOODLAND_MANSION_MAP;
            case "trial" -> Items.BURIED_TRIAL_CHAMBERS_MAP;
            case "jungle" -> Items.JUNGLE_PYRAMID_MAP;
            case "swamp" -> Items.SWAMP_HUT_MAP;
            case "stick" -> Items.STICK;
            default -> Items.FILLED_MAP;
        });
        ServerLevel level = mock(ServerLevel.class);
        ItemEntity item = mock(ItemEntity.class);
        when(item.getItem()).thenReturn(stack);
        ServerPlayer player = mock(ServerPlayer.class, invocation -> {
            if (invocation.getMethod().getName().equals("drop")) {
                if (invocation.getArguments().length == 5) return invocation.callRealMethod();
                if (invocation.getArguments().length == 6) return variant.equals("cancelled") ? null : item;
            }
            return Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        when(player.level()).thenReturn(level);
        MapItemSavedData map = mock(MapItemSavedData.class);
        try (MockedStatic<MapItem> maps = mockStatic(MapItem.class)) {
            maps.when(() -> MapItem.getSavedData(stack, level)).thenReturn(variant.equals("missing") ? null : map);
            assertSame(variant.equals("cancelled") ? null : item, player.drop(stack, false, Prediction.PREDICTED, true, null));
            boolean update = stack.getItem() instanceof MapItem && !variant.equals("cancelled");
            maps.verify(() -> MapItem.getSavedData(stack, level), times(update ? 1 : 0));
            verify(map, times(update && !variant.equals("missing") ? 1 : 0)).tickCarriedBy(player, stack, null);
        }
    }
}
