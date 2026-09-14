package io.papermc.paper.porting;

import com.destroystokyo.paper.ParticleBuilder;
import io.papermc.paper.configuration.GlobalConfiguration;
import java.io.DataInput;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.PistonMoveReaction;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.event.block.BrewingStartEvent;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises the actual API adapters and vanilla menu/block methods, without a live server. */
@AllFeatures
class PaperBackportRegressionTest {
    @ParameterizedTest
    @EnumSource(PushReaction.class)
    void pistonReactionIdsMatchVanilla(PushReaction reaction) {
        PistonMoveReaction expected = switch (reaction) {
            case PUSH_PULL -> PistonMoveReaction.MOVE;
            case PUSH -> PistonMoveReaction.PUSH_ONLY;
            case POPPED -> PistonMoveReaction.BREAK;
            case IMMOVEABLE -> PistonMoveReaction.BLOCK;
            case IGNORE_ENTITY -> PistonMoveReaction.IGNORE;
        };
        assertEquals(expected, PistonMoveReaction.getById(reaction.ordinal()));
        BuiltInRegistries.BLOCK.forEach(block -> {
            BlockState state = block.defaultBlockState();
            if (state.getPistonPushReaction() == reaction) {
                assertEquals(expected, state.asBlockData().getPistonMoveReaction(), block.toString());
            }
        });
    }

    @Test
    void particleBuilderKeepsLegacyExtraAndForwardsAllNewOptions() {
        World world = mock(World.class);
        ParticleBuilder builder = new ParticleBuilder(Particle.FLAME).location(new Location(world, 1, 2, 3));
        assertEquals(1, builder.speedX());
        assertEquals(Particle.RandomizationType.DEFAULT, builder.randomizationType());
        builder.extra(0.25);
        assertEquals(0.25, builder.speedX());
        assertEquals(0.25, builder.speedY());
        assertEquals(0.25, builder.speedZ());
        builder.speed(0.5, 0.75, 1.25).randomizationType(Particle.RandomizationType.ALTERNATIVE_WITH_SPEED).spawn();
        verify(world).spawnParticle(Particle.FLAME, null, null, 1, 2, 3, 1, 0, 0, 0,
            0.5, 0.75, 1.25, null, true, Particle.RandomizationType.ALTERNATIVE_WITH_SPEED);
    }

    @ParameterizedTest
    @EnumSource(Particle.RandomizationType.class)
    void playerParticlePacketCarriesSeparateSpeeds(Particle.RandomizationType randomization) {
        ServerPlayer player = mock(ServerPlayer.class);
        player.connection = mock(ServerGamePacketListenerImpl.class);
        CraftPlayer craft = mock(CraftPlayer.class, CALLS_REAL_METHODS);
        doReturn(player).when(craft).getHandle();
        craft.spawnParticle(Particle.FLAME, 1, 2, 3, 4, 0.1, 0.2, 0.3, 0.5, 0.75, 1.25, null, false, randomization);
        ArgumentCaptor<ClientboundLevelParticlesPacket> packets = ArgumentCaptor.forClass(ClientboundLevelParticlesPacket.class);
        verify(player.connection).send(packets.capture());
        ClientboundLevelParticlesPacket packet = packets.getValue();
        assertEquals(1, packet.x());
        assertEquals(4, packet.count());
        assertEquals(0.5F, packet.xMaxSpeed());
        assertEquals(0.75F, packet.yMaxSpeed());
        assertEquals(1.25F, packet.zMaxSpeed());
        assertEquals(randomization.name(), packet.randomizationType().name());
        assertFalse(packet.overrideLimiter());
    }

    @Test
    void oldPlayerParticleOverloadStillDelegatesWithDefaultRandomization() {
        org.bukkit.entity.Player player = mock(org.bukkit.entity.Player.class, CALLS_REAL_METHODS);
        player.spawnParticle(Particle.FLAME, 1, 2, 3, 4, 0.1, 0.2, 0.3, 0.5, null, true);
        verify(player).spawnParticle(Particle.FLAME, 1, 2, 3, 4, 0.1, 0.2, 0.3,
            0.5, 0.5, 0.5, null, true, Particle.RandomizationType.DEFAULT);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 200, 400, 1200})
    void brewingEventUsesTheRecipeDuration(int duration) {
        BrewingStartEvent event = new BrewingStartEvent(mock(org.bukkit.block.Block.class),
            new org.bukkit.inventory.ItemStack(Material.NETHER_WART), duration);
        assertEquals(duration, event.getBrewingTime());
        assertEquals(duration, event.getRecipeBrewTime());
    }

    @Test
    void brewingMenuRegistersAndTracksAllFourDataSlots() {
        ServerLevel level = mock(ServerLevel.class);
        Player player = mock(Player.class);
        when(player.level()).thenReturn(level);
        RecipeManager recipes = mock(RecipeManager.class);
        when(level.recipeAccess()).thenReturn(recipes);
        when(recipes.propertySet(any())).thenReturn(mock(RecipePropertySet.class));
        Inventory inventory = new Inventory(player, mock(EntityEquipment.class));
        SimpleContainerData data = new SimpleContainerData(4);
        BrewingStandMenu menu = new BrewingStandMenu(1, inventory, new SimpleContainer(5), data);
        assertEquals(4, menu.dataSlots.size());
        assertEquals(4, menu.remoteDataSlots.size());
        for (int i = 0; i < 4; i++) {
            data.set(i, 100 + i);
            assertEquals(100 + i, menu.dataSlots.get(i).get());
            data.set(i, 200 + i);
            assertEquals(200 + i, menu.dataSlots.get(i).get());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void noteBlockChangesOnceUnlessUpdatesAreDisabled(boolean disabled) {
        GlobalConfiguration config = new GlobalConfiguration();
        config.blockUpdates = config.new BlockUpdates();
        config.blockUpdates.disableNoteblockUpdates = disabled;
        try (MockedStatic<GlobalConfiguration> settings = mockStatic(GlobalConfiguration.class)) {
            settings.when(GlobalConfiguration::get).thenReturn(config);
            ServerLevel level = mock(ServerLevel.class);
            when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
            Player player = mock(Player.class);
            BlockState state = Blocks.NOTE_BLOCK.defaultBlockState().setValue(NoteBlock.NOTE, 4);
            state.useWithoutItem(level, player, new BlockHitResult(Vec3.ZERO, Direction.UP, BlockPos.ZERO, false));
            verify(level).setBlockAndUpdate(BlockPos.ZERO, state.setValue(NoteBlock.NOTE, disabled ? 4 : 5));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 16777216, Integer.MAX_VALUE})
    void invalidLongArrayLengthIsRejectedBeforeReadingOrAllocatingPayload(int length) throws Exception {
        DataInput input = mock(DataInput.class);
        when(input.readInt()).thenReturn(length);
        assertThrows(IllegalArgumentException.class, () -> LongArrayTag.TYPE.load(input, NbtAccounter.unlimitedHeap()));
        verify(input, never()).readLong();
    }

    @Test
    void normalLongArraysStillRoundTrip() throws Exception {
        DataInput input = mock(DataInput.class);
        when(input.readInt()).thenReturn(2);
        when(input.readLong()).thenReturn(Long.MIN_VALUE, Long.MAX_VALUE);
        assertArrayEquals(new long[] {Long.MIN_VALUE, Long.MAX_VALUE},
            LongArrayTag.TYPE.load(input, NbtAccounter.unlimitedHeap()).getAsLongArray());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1, 2, 80})
    void signLimitCountsCodepointsAndCanBeDisabled(int limit) throws Exception {
        Method limiter = ServerGamePacketListenerImpl.class.getDeclaredMethod("limitSignLine", String.class, int.class);
        limiter.setAccessible(true);
        String text = "\uD83C\uDF1F".repeat(81);
        String limited = (String) limiter.invoke(null, text, limit);
        assertEquals(limit <= 0 ? text : "\uD83C\uDF1F".repeat(limit), limited);
        assertEquals("", limiter.invoke(null, "", limit));
    }
}
