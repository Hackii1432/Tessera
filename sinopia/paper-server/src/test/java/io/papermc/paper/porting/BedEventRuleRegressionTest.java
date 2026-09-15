package io.papermc.paper.porting;

import com.mojang.datafixers.util.Either;
import io.papermc.paper.block.bed.BedEnterAction;
import io.papermc.paper.block.bed.BedRuleResult;
import io.papermc.paper.event.player.PlayerBedFailEnterEvent;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.Blocks;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@AllFeatures
class BedEventRuleRegressionTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void bothEventBridgesUseTheSuppliedRuleWithoutReadingDimensionDefaults(boolean allowed) {
        ServerPlayer player = mock(ServerPlayer.class);
        ServerLevel level = mock(ServerLevel.class);
        when(player.level()).thenReturn(level);
        when(player.getBukkitEntity()).thenReturn(mock(CraftPlayer.class));
        BedRule rule = new BedRule(allowed ? BedRule.Rule.ALWAYS : BedRule.Rule.NEVER,
            allowed ? BedRule.Rule.NEVER : BedRule.Rule.ALWAYS, false, true, Optional.empty());
        AtomicReference<BedEnterAction> enter = new AtomicReference<>();
        AtomicReference<BedEnterAction> fail = new AtomicReference<>();
        Server server = mock(Server.class);
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));

        try (var bukkit = mockStatic(Bukkit.class);
             var blocks = mockStatic(CraftBlock.class);
             var enters = mockConstruction(PlayerBedEnterEvent.class, (event, context) -> {
                 enter.set((BedEnterAction) context.arguments().get(3));
                 when(event.useBed()).thenReturn(Event.Result.DENY);
             });
             var failures = mockConstruction(PlayerBedFailEnterEvent.class, (event, context) ->
                 fail.set((BedEnterAction) context.arguments().get(5)))) {
            bukkit.when(Bukkit::getServer).thenReturn(server);
            blocks.when(() -> CraftBlock.at(level, BlockPos.ZERO)).thenReturn(mock(CraftBlock.class));
            assertEquals(Either.left(Player.BedSleepingProblem.OTHER_PROBLEM),
                CraftEventFactory.callPlayerBedEnterEvent(player, BlockPos.ZERO, rule, Either.right(Unit.INSTANCE)));
            CraftEventFactory.callPlayerBedFailEnterEvent(player, BlockPos.ZERO, rule, Player.BedSleepingProblem.OTHER_PROBLEM);
            for (BedEnterAction action : new BedEnterAction[] {enter.get(), fail.get()}) {
                assertEquals(allowed ? BedRuleResult.ALLOWED : BedRuleResult.NEVER, action.canSleep());
                assertEquals(allowed ? BedRuleResult.NEVER : BedRuleResult.ALLOWED, action.canSetSpawn());
            }
            verify(level, never()).environmentAttributes();
            assertEquals(1, enters.constructed().size());
            assertEquals(1, failures.constructed().size());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void legacyBridgeUsesTheBedTypesPositionalAttribute(boolean straw) {
        ServerPlayer player = mock(ServerPlayer.class);
        ServerLevel level = mock(ServerLevel.class);
        EnvironmentAttributeSystem attributes = mock(EnvironmentAttributeSystem.class);
        AbstractBedBlock bed = (AbstractBedBlock) (straw ? Blocks.STRAW_BED : Blocks.BED.red());
        when(player.level()).thenReturn(level);
        when(level.getBlockState(BlockPos.ZERO)).thenReturn(bed.defaultBlockState());
        when(level.environmentAttributes()).thenReturn(attributes);
        var attribute = straw ? EnvironmentAttributes.STRAW_BED_RULE : EnvironmentAttributes.BED_RULE;
        when(attributes.getValue(attribute, BlockPos.ZERO)).thenReturn(BedRule.DESTROY_ON_LEAVE);

        try (var bridge = mockStatic(CraftEventFactory.class, CALLS_REAL_METHODS)) {
            bridge.when(() -> CraftEventFactory.callPlayerBedEnterEvent(eq(player), eq(BlockPos.ZERO), same(BedRule.DESTROY_ON_LEAVE), any()))
                .thenReturn(Either.right(Unit.INSTANCE));
            bridge.when(() -> CraftEventFactory.callPlayerBedFailEnterEvent(player, BlockPos.ZERO, BedRule.DESTROY_ON_LEAVE, Player.BedSleepingProblem.OTHER_PROBLEM))
                .thenReturn(mock(PlayerBedFailEnterEvent.class));
            CraftEventFactory.callPlayerBedEnterEvent(player, BlockPos.ZERO, Either.right(Unit.INSTANCE));
            CraftEventFactory.callPlayerBedFailEnterEvent(player, BlockPos.ZERO, Player.BedSleepingProblem.OTHER_PROBLEM);
            verify(attributes, times(2)).getValue(attribute, BlockPos.ZERO);
            verify(attributes, never()).getDimensionValue(any());
        }
    }
}
