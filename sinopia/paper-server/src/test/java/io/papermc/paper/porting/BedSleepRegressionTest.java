package io.papermc.paper.porting;

import com.mojang.datafixers.util.Either;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Sinopia - regression coverage for the 26.3 bed-overload port.
@AllFeatures
class BedSleepRegressionTest {
    private static final BlockPos POS = BlockPos.ZERO;

    private static AbstractBedBlock bed(boolean straw) {
        return (AbstractBedBlock) (straw ? Blocks.STRAW_BED : Blocks.BED.red());
    }

    /** Runs the real overload chain and validation, isolating the live world and plugin dispatcher. */
    private static final class SleepAttempt implements AutoCloseable {
        final ServerLevel level = mock(ServerLevel.class);
        final ServerPlayer player = mock(ServerPlayer.class, invocation -> switch (invocation.getMethod().getName()) {
            case "startSleepInBed", "freeAt", "getSleepTimer" -> invocation.callRealMethod();
            default -> Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        final BedRule rule;
        final AtomicInteger events = new AtomicInteger();
        final MockedStatic<CraftEventFactory> eventFactory = mockStatic(CraftEventFactory.class);
        boolean cancel;

        SleepAttempt(boolean dark) {
            // Vanilla straw-bed rule: no spawn point; destruction on leaving is not changed here.
            this.rule = BedRule.DESTROY_ON_LEAVE;
            when(this.player.level()).thenReturn(this.level);
            when(this.player.isAlive()).thenReturn(true);
            when(this.player.isCreative()).thenReturn(true);
            when(this.player.startSleeping(POS)).thenReturn(true);
            when(this.level.isDarkOutside()).thenReturn(dark);
            when(this.level.canSleepThroughNights()).thenReturn(true);
            when(this.level.getBlockState(any(BlockPos.class))).thenReturn(Blocks.AIR.defaultBlockState());
            this.eventFactory.when(() -> CraftEventFactory.callPlayerBedEnterEvent(eq(this.player), eq(POS), any()))
                .thenAnswer(invocation -> {
                    // Fail immediately on recursion, without overflowing the test JVM's stack.
                    assertEquals(1, this.events.incrementAndGet(), "The bed event must not be dispatched recursively");
                    return this.cancel ? Either.left(Player.BedSleepingProblem.OTHER_PROBLEM) : invocation.getArgument(2);
                });
        }

        @Override
        public void close() {
            this.eventFactory.close();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void vanillaEntryReachesBaseSleepOnceForBothBedTypes(boolean straw) throws Exception {
        try (SleepAttempt attempt = new SleepAttempt(true)) {
            Field counter = Player.class.getDeclaredField("sleepCounter");
            counter.setAccessible(true);
            counter.setInt(attempt.player, 42);
            AbstractBedBlock bed = bed(straw);
            assertEquals(Either.right(Unit.INSTANCE), attempt.player.startSleepInBed(bed, bed.defaultBlockState(), attempt.rule, POS));
            assertEquals(1, attempt.events.get());
            assertEquals(0, attempt.player.getSleepTimer());
            verify(attempt.player, times(1)).startSleeping(POS);
            verify(attempt.player, times(1)).awardStat(bed.getSleptInBedStatType());
            verify(attempt.level, times(1)).updateSleepingPlayerList();
            verify(attempt.player, never()).setRespawnPosition(any(), anyBoolean(), any());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void forceOnlyBypassesTheNormalSleepRestriction(boolean force) {
        try (SleepAttempt attempt = new SleepAttempt(false)) {
            AbstractBedBlock bed = bed(true);
            Either<Player.BedSleepingProblem, Unit> result = attempt.player.startSleepInBed(bed, bed.defaultBlockState(), attempt.rule, POS, force);
            assertEquals(force, result.right().isPresent());
            assertEquals(1, attempt.events.get());
            verify(attempt.player, times(force ? 1 : 0)).startSleeping(POS);
        }
    }

    @Test
    void pluginsCanStillCancelForcedSleep() {
        try (SleepAttempt attempt = new SleepAttempt(false)) {
            attempt.cancel = true;
            AbstractBedBlock bed = bed(true);
            assertTrue(attempt.player.startSleepInBed(bed, bed.defaultBlockState(), attempt.rule, POS, true).left().isPresent());
            assertEquals(1, attempt.events.get());
            verify(attempt.player, never()).startSleeping(any());
            verify(attempt.player, never()).awardStat(any(net.minecraft.resources.Identifier.class));
            verify(attempt.level, never()).updateSleepingPlayerList();
        }
    }

    @Test
    void forceCannotMakeADeadPlayerSleep() {
        try (SleepAttempt attempt = new SleepAttempt(true)) {
            when(attempt.player.isAlive()).thenReturn(false);
            AbstractBedBlock bed = bed(true);
            assertEquals(Either.left(Player.BedSleepingProblem.OTHER_PROBLEM),
                attempt.player.startSleepInBed(bed, bed.defaultBlockState(), attempt.rule, POS, true));
            assertEquals(0, attempt.events.get());
            verify(attempt.player, never()).startSleeping(any());
        }
    }

    @Test
    void rejectedBaseSleepDoesNotAwardStatistics() {
        try (SleepAttempt attempt = new SleepAttempt(true)) {
            when(attempt.player.startSleeping(POS)).thenReturn(false);
            AbstractBedBlock bed = bed(true);
            assertTrue(attempt.player.startSleepInBed(bed, bed.defaultBlockState(), attempt.rule, POS).left().isPresent());
            assertEquals(1, attempt.events.get());
            verify(attempt.player, times(1)).startSleeping(POS);
            verify(attempt.player, never()).awardStat(any(net.minecraft.resources.Identifier.class));
        }
    }

    @ParameterizedTest
    @CsvSource({"false,false,true", "false,true,true", "true,false,true", "true,true,true", "true,true,false"})
    void bukkitForwardsForceAndOnlyOccupiesSuccessfulBeds(boolean straw, boolean force, boolean succeeds) {
        AbstractBedBlock bed = bed(straw);
        BlockState state = bed.defaultBlockState();
        BedRule rule = new BedRule(BedRule.Rule.ALWAYS, BedRule.Rule.NEVER, false, straw, Optional.empty());
        ServerPlayer player = mock(ServerPlayer.class);
        ServerLevel level = mock(ServerLevel.class);
        EnvironmentAttributeSystem attributes = mock(EnvironmentAttributeSystem.class);
        CraftHumanEntity human = mock(CraftHumanEntity.class);
        World world = mock(World.class);
        when(human.getHandle()).thenReturn(player);
        when(human.getWorld()).thenReturn(world);
        when(player.level()).thenReturn(level);
        when(level.getBlockState(POS)).thenReturn(state);
        when(level.environmentAttributes()).thenReturn(attributes);
        when(attributes.getValue(any(), eq(POS))).thenReturn(rule);
        Either<Player.BedSleepingProblem, Unit> result = succeeds ? Either.right(Unit.INSTANCE) : Either.left(Player.BedSleepingProblem.OTHER_PROBLEM);
        // Stub both signatures so a missing force parameter fails at verification, not with an unrelated NPE.
        when(player.startSleepInBed(bed, state, rule, POS)).thenReturn(result);
        when(player.startSleepInBed(bed, state, rule, POS, force)).thenReturn(result);
        doCallRealMethod().when(human).sleep(any(Location.class), anyBoolean());

        assertEquals(succeeds, human.sleep(new Location(world, 0, 0, 0), force));
        verify(player).startSleepInBed(bed, state, rule, POS, force);
        verify(player, never()).startSleepInBed(bed, state, rule, POS);
        verify(level, times(succeeds ? 1 : 0)).setBlock(POS, state.setValue(AbstractBedBlock.OCCUPIED, true), Block.UPDATE_INVISIBLE);
    }
}
