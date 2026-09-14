package io.papermc.paper.porting;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.phys.Vec3;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.spigotmc.SpigotWorldConfig;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@AllFeatures
class WorldgenAndBedBackportTest {
    @ParameterizedTest
    @ValueSource(strings = {"minecraft", "custom"})
    @SuppressWarnings("unchecked")
    void abandonedCampUsesConfiguredSeedWithoutChangingCustomStructureSets(String namespace) throws Exception {
        RandomSpreadStructurePlacement original = new RandomSpreadStructurePlacement(32, 8, RandomSpreadType.LINEAR, 123);
        StructureSet set = new StructureSet(List.of(), original);
        Holder.Reference<StructureSet> holder = mock(Holder.Reference.class);
        when(holder.value()).thenReturn(set);
        when(holder.unwrapKey()).thenReturn(Optional.of(ResourceKey.create(Registries.STRUCTURE_SET,
            Identifier.fromNamespaceAndPath(namespace, "abandoned_camp"))));
        SpigotWorldConfig config = mock(SpigotWorldConfig.class);
        config.abandonedCampSeed = 91231199;
        Method inject = ChunkGeneratorStructureState.class.getDeclaredMethod("injectSpigot", List.class, SpigotWorldConfig.class);
        inject.setAccessible(true);
        List<Holder<StructureSet>> result = (List<Holder<StructureSet>>) inject.invoke(null, List.of(holder), config);
        RandomSpreadStructurePlacement actual = (RandomSpreadStructurePlacement) result.getFirst().value().placement();
        assertEquals(namespace.equals("minecraft") ? 91231199 : 123, actual.salt);
        assertEquals(original.spacing(), actual.spacing());
        assertEquals(original.separation(), actual.separation());
        assertEquals(original.spreadType(), actual.spreadType());
        assertEquals(123, original.salt, "Do not mutate the registry's original placement");
        if (!namespace.equals("minecraft")) assertSame(holder, result.getFirst());
    }

    @Test
    @SuppressWarnings("unchecked")
    void bedExplosionCapturesItsBlockBeforeRemovalAndAttachesTheSnapshot() throws Exception {
        ServerLevel level = mock(ServerLevel.class);
        BlockPos pos = new BlockPos(0, 64, 0);
        BedBlock bed = (BedBlock) Blocks.BED.red();
        BlockState state = bed.defaultBlockState();
        when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
        DamageSources sources = mock(DamageSources.class);
        when(level.damageSources()).thenReturn(sources);
        Holder<DamageType> type = Holder.direct(new DamageType("bed", 0.0F));
        DamageSource damage = new DamageSource(type);
        when(sources.badRespawnPointExplosion(any())).thenReturn(damage);
        CraftBlock block = mock(CraftBlock.class);
        org.bukkit.block.BlockState snapshot = mock(org.bukkit.block.BlockState.class);
        when(block.getState()).thenReturn(snapshot);
        try (MockedStatic<CraftBlock> blocks = mockStatic(CraftBlock.class)) {
            blocks.when(() -> CraftBlock.at(level, pos)).thenReturn(block);
            Method destroy = BedBlock.class.getDeclaredMethod("destroyOnUse", BlockState.class, Level.class, BlockPos.class, Player.class);
            destroy.setAccessible(true);
            destroy.invoke(bed, state, level, pos, mock(Player.class));
            var order = inOrder(block, level);
            order.verify(block).getState();
            order.verify(level).removeBlock(pos, false);
            ArgumentCaptor<DamageSource> actual = ArgumentCaptor.forClass(DamageSource.class);
            verify(level).explode(isNull(), actual.capture(), isNull(), eq(Vec3.atCenterOf(pos)), eq(5.0F), eq(true), eq(Level.ExplosionInteraction.BLOCK));
            assertSame(snapshot, actual.getValue().causingBlockSnapshot());
        }
    }
}
