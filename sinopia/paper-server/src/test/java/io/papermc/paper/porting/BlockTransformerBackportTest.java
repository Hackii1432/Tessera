package io.papermc.paper.porting;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.BlockTransformer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@AllFeatures
class BlockTransformerBackportTest {
    private static final BlockPos POS = new BlockPos(8, 64, 8);

    private static final class Attempt implements AutoCloseable {
        final ServerLevel level = mock(ServerLevel.class);
        final Player player;
        final ItemStack item = new ItemStack(Items.STICK, 2);
        final BlockState result = Blocks.STONE.defaultBlockState();
        final UseOnContext context = mock(UseOnContext.class);
        final BlockStateProvider provider = mock(BlockStateProvider.class);
        final List<EntityChangeBlockEvent> events = new ArrayList<>();
        final BlockTransformer transformer;

        Attempt(boolean cancel, boolean serverPlayer) {
            this.player = serverPlayer ? mock(ServerPlayer.class) : mock(Player.class);
            when(this.player.level()).thenReturn(this.level);
            CraftPlayer bukkitPlayer = mock(CraftPlayer.class);
            org.bukkit.Server bukkitServer = mock(org.bukkit.Server.class);
            org.bukkit.plugin.PluginManager plugins = mock(org.bukkit.plugin.PluginManager.class);
            when(this.player.getBukkitEntity()).thenReturn(bukkitPlayer);
            when(bukkitPlayer.getServer()).thenReturn(bukkitServer);
            when(bukkitServer.getPluginManager()).thenReturn(plugins);
            when(this.player.getOffhandItem()).thenReturn(ItemStack.EMPTY);
            if (this.player instanceof ServerPlayer server) server.containerMenu = mock(AbstractContainerMenu.class);
            when(this.context.getPlayer()).thenReturn(this.player);
            when(this.context.getLevel()).thenReturn(this.level);
            when(this.context.getClickedPos()).thenReturn(POS);
            when(this.context.getClickedFace()).thenReturn(Direction.UP);
            when(this.context.getHand()).thenReturn(InteractionHand.MAIN_HAND);
            when(this.context.getItemInHand()).thenReturn(this.item);
            when(this.level.getRandom()).thenReturn(RandomSource.create(1));
            when(this.level.getBlockState(POS)).thenReturn(Blocks.DIRT.defaultBlockState());
            when(this.provider.getOptionalState(eq(this.level), any(), eq(POS))).thenReturn(this.result);
            doAnswer(invocation -> {
                EntityChangeBlockEvent event = invocation.getArgument(0);
                this.events.add(event);
                assertEquals(bukkitPlayer, event.getEntity());
                org.bukkit.block.Block block = event.getBlock();
                assertEquals(POS.getX(), block.getX());
                assertEquals(POS.getZ(), block.getZ());
                assertEquals(this.result.asBlockData(), event.getBlockData());
                assertEquals(2, this.item.getCount(), "Event must precede item consumption");
                verify(this.level, never()).setBlock(any(), any(), anyInt());
                event.setCancelled(cancel);
                return null;
            }).when(plugins).callEvent(any(EntityChangeBlockEvent.class));
            this.transformer = new BlockTransformer(List.of(
                BlockTransformer.BlockTransformData.builder(this.provider).updateFromNeighbors(false).build()));
        }

        @Override
        public void close() {
        }
    }

    @Test
    void allowedTransformationCallsTheRealEventBridgeBeforeChangingTheBlock() {
        try (Attempt attempt = new Attempt(false, false)) {
            assertEquals(InteractionResult.SUCCESS, attempt.transformer.transformBlock(attempt.context));
            assertEquals(1, attempt.events.size());
            assertEquals(1, attempt.item.getCount());
            verify(attempt.level).setBlock(POS, attempt.result, Block.UPDATE_ALL_IMMEDIATE);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cancelledTransformationDoesNotChangeBlockOrConsumeItem(boolean creative) {
        try (Attempt attempt = new Attempt(true, true)) {
            when(attempt.player.hasInfiniteMaterials()).thenReturn(creative);
            assertEquals(InteractionResult.PASS, attempt.transformer.transformBlock(attempt.context));
            assertEquals(1, attempt.events.size());
            assertEquals(2, attempt.item.getCount());
            verify(attempt.level, never()).setBlock(any(), any(), anyInt());
            verify(((ServerPlayer) attempt.player).containerMenu, times(creative ? 0 : 1)).forceHeldSlot(InteractionHand.MAIN_HAND);
        }
    }

    @Test
    void nonMatchingTransformationDoesNotCallAnEvent() {
        try (Attempt attempt = new Attempt(false, false)) {
            when(attempt.provider.getOptionalState(eq(attempt.level), any(), eq(POS))).thenReturn(null);
            assertEquals(InteractionResult.PASS, attempt.transformer.transformBlock(attempt.context));
            assertTrue(attempt.events.isEmpty());
            assertEquals(2, attempt.item.getCount());
        }
    }
}
