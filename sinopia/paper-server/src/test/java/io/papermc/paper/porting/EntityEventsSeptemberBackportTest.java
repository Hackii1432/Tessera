package io.papermc.paper.porting;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.animal.panda.Panda;
import net.minecraft.world.entity.decoration.Cushion;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CushionItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntityTargetEvent.TargetReason;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@AllFeatures
class EntityEventsSeptemberBackportTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cancelledCushionPlacementNeitherSpawnsNorConsumes(boolean cancelled) {
        ServerLevel level = mock(ServerLevel.class);
        when(level.enabledFeatures()).thenReturn(FeatureFlags.REGISTRY.allFlags());
        when(level.getRandom()).thenReturn(RandomSource.create(42L));
        UseOnContext context = mock(UseOnContext.class);
        when(context.getLevel()).thenReturn(level);
        when(context.getClickedFace()).thenReturn(Direction.UP);
        when(context.getClickLocation()).thenReturn(Vec3.ZERO);
        ItemStack stack = mock(ItemStack.class);
        when(context.getItemInHand()).thenReturn(stack);
        CushionItem item = mock(CushionItem.class);
        doCallRealMethod().when(item).useOn(context);
        EntityPlaceEvent event = mock(EntityPlaceEvent.class);
        when(event.isCancelled()).thenReturn(cancelled);
        try (var cushions = mockConstruction(Cushion.class, (cushion, ignored) -> {
                 when(cushion.level()).thenReturn(level);
                 when(cushion.blockPosition()).thenReturn(BlockPos.ZERO);
             });
             var placement = mockConstruction(BlockPlaceContext.class, (place, ignored) -> when(place.getClickedPos()).thenReturn(BlockPos.ZERO));
             var geometry = mockStatic(Cushion.class);
             var types = mockStatic(EntityType.class);
             var threads = mockStatic(ca.spottedleaf.moonrise.common.util.TickThread.class);
             var events = mockStatic(CraftEventFactory.class)) {
            threads.when(() -> ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(level, BlockPos.ZERO)).thenReturn(true);
            geometry.when(() -> Cushion.canBePlacedAt(eq(level), any())).thenReturn(true);
            events.when(() -> CraftEventFactory.callEntityPlaceEvent(eq(context), any())).thenReturn(event);
            assertEquals(cancelled ? InteractionResult.FAIL : InteractionResult.SUCCESS, item.useOn(context));
            assertEquals(1, cushions.constructed().size());
            Cushion cushion = cushions.constructed().getFirst();
            events.verify(() -> CraftEventFactory.callEntityPlaceEvent(context, cushion));
            verify(level, times(cancelled ? 0 : 1)).addFreshEntity(cushion);
            verify(stack, times(cancelled ? 0 : 1)).consume(eq(1), isNull());
            verify(cushion, times(cancelled ? 0 : 1)).destroyIfInFire(level);
            verify(cushion, times(cancelled ? 0 : 1)).gameEvent(any(), isNull());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void alliesUseTheNearbyAttackReason(boolean bee) throws Exception {
        Class<?> goalType = Class.forName(bee
            ? "net.minecraft.world.entity.animal.bee.Bee$BeeHurtByOtherGoal"
            : "net.minecraft.world.entity.animal.panda.Panda$PandaHurtByTargetGoal");
        Object goal = mock(goalType, CALLS_REAL_METHODS);
        Mob owner = bee ? mock(Bee.class) : mock(Panda.class);
        Mob ally = bee ? mock(Bee.class) : mock(Panda.class);
        LivingEntity attacker = mock(LivingEntity.class);
        when(owner.hasLineOfSight(attacker)).thenReturn(true);
        when(ally.isAggressive()).thenReturn(true);
        Field mob = TargetGoal.class.getDeclaredField("mob");
        mob.setAccessible(true);
        mob.set(goal, owner);
        Method alert = goalType.getDeclaredMethod("alertOther", Mob.class, LivingEntity.class);
        alert.setAccessible(true);
        alert.invoke(goal, ally, attacker);
        verify(ally).setTarget(attacker, TargetReason.TARGET_ATTACKED_NEARBY_ENTITY);
        verify(ally, never()).setTarget(attacker, TargetReason.TARGET_ATTACKED_ENTITY);
    }
}
