package io.papermc.paper.porting;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.Fluids;
import org.bukkit.TreeType;
import org.bukkit.craftbukkit.CraftRegionAccessor;
import org.bukkit.support.RegistryHelper;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Sinopia - preserve vanilla poplar growth while identifying it correctly for Bukkit.
@AllFeatures
class PoplarTreeRegressionTest {
    private static ResourceKey<Feature> featureKey(String name) throws ReflectiveOperationException {
        return (ResourceKey<Feature>) TreeFeatures.class.getField(name).get(null);
    }

    private static void identify(Holder<Feature> holder) throws Throwable {
        Method method = TreeGrower.class.getDeclaredMethod("setTreeType", Holder.class);
        method.setAccessible(true);
        try {
            method.invoke(TreeGrower.POPLAR, holder);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    // The same tests also run in standalone Sinopia, which has no Folia ThreadLocal.
    private static ThreadLocal<TreeType> regionTreeType() throws ReflectiveOperationException {
        try {
            return (ThreadLocal<TreeType>) SaplingBlock.class.getField("treeTypeRT").get(null);
        } catch (NoSuchFieldException exception) {
            return null;
        }
    }

    private static TreeType currentTreeType() throws ReflectiveOperationException {
        ThreadLocal<TreeType> local = regionTreeType();
        return local == null ? (TreeType) SaplingBlock.class.getField("treeType").get(null) : local.get();
    }

    @AfterEach
    void clearTreeType() throws ReflectiveOperationException {
        ThreadLocal<TreeType> local = regionTreeType();
        if (local == null) {
            SaplingBlock.class.getField("treeType").set(null, null);
        } else {
            local.remove();
        }
    }

    @ParameterizedTest
    @CsvSource({
        "RED_POPLAR, RED_POPLAR",
        "ORANGE_POPLAR, ORANGE_POPLAR",
        "YELLOW_POPLAR, YELLOW_POPLAR",
        "OAK, TREE",
        "CHERRY_BEES_005, CHERRY",
        "PALE_OAK_CREAKING, PALE_OAK_CREAKING"
    })
    void realVanillaFeatureHasTheCorrectBukkitType(String feature, String type) throws Throwable {
        identify(RegistryHelper.registryAccess().lookupOrThrow(Registries.FEATURE).getOrThrow(featureKey(feature)));
        assertEquals(type, currentTreeType().name());
    }

    private static RegistryAccess registry(ResourceKey<Feature> key, Feature feature) {
        RegistryAccess access = mock(RegistryAccess.class);
        Registry<Feature> registry = mock(Registry.class);
        Holder.Reference<Feature> holder = mock(Holder.Reference.class);
        when(access.lookupOrThrow(Registries.FEATURE)).thenReturn(registry);
        when(registry.get(key)).thenReturn(Optional.of(holder));
        when(holder.is(key)).thenReturn(true);
        when(holder.value()).thenReturn(feature);
        return access;
    }

    @ParameterizedTest
    @CsvSource({
        "0, RED_POPLAR, true", "1, ORANGE_POPLAR, true", "2, YELLOW_POPLAR, true",
        "0, RED_POPLAR, false", "1, ORANGE_POPLAR, false", "2, YELLOW_POPLAR, false"
    })
    void saplingGrowthReachesVanillaPlacementAndRetainsFailureHandling(int selection, String type, boolean placed) throws Exception {
        BlockPos pos = new BlockPos(20, 80, 30);
        BlockState sapling = Blocks.POPLAR_SAPLING.defaultBlockState().setValue(SaplingBlock.STAGE, 1);
        ServerLevel level = mock(ServerLevel.class, RETURNS_DEEP_STUBS);
        ChunkGenerator generator = mock(ChunkGenerator.class);
        RandomSource random = mock(RandomSource.class);
        Feature feature = mock(Feature.class);
        RegistryAccess registries = registry(featureKey(type), feature);
        when(level.registryAccess()).thenReturn(registries);
        when(level.getFluidState(pos)).thenReturn(Fluids.EMPTY.defaultFluidState());
        when(random.nextInt(3)).thenReturn(selection);
        when(feature.place(level, generator, random, pos)).thenReturn(placed);

        assertEquals(placed, TreeGrower.POPLAR.growTree(level, generator, pos, sapling, random));
        assertEquals(type, currentTreeType().name());
        verify(random).nextInt(3); // Vanilla retains three equally likely colors.
        verify(feature).place(level, generator, random, pos);
        verify(level).setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_SKIP_ALL_SIDEEFFECTS | Block.UPDATE_CLIENTS);
        verify(level, times(placed ? 0 : 1)).setBlock(pos, sapling, Block.UPDATE_NONE);
    }

    @ParameterizedTest
    @CsvSource({
        "RED_POPLAR, true", "ORANGE_POPLAR, true", "YELLOW_POPLAR, true",
        "RED_POPLAR, false", "ORANGE_POPLAR, false", "YELLOW_POPLAR, false",
        "TREE, true"
    })
    void bukkitGenerationSelectsTheRequestedFeatureAndPropagatesItsResult(String type, boolean placed) throws Exception {
        BlockPos pos = new BlockPos(20, 80, 30);
        WorldGenLevel level = mock(WorldGenLevel.class);
        ChunkGenerator generator = mock(ChunkGenerator.class);
        RandomSource random = mock(RandomSource.class);
        Feature feature = mock(Feature.class);
        RegistryAccess registries = registry(featureKey(type.equals("TREE") ? "OAK" : type), feature);
        when(level.registryAccess()).thenReturn(registries);
        when(feature.place(level, generator, random, pos)).thenReturn(placed);
        CraftRegionAccessor accessor = mock(CraftRegionAccessor.class, CALLS_REAL_METHODS);

        assertEquals(placed, accessor.generateTree(level, generator, pos, random, TreeType.valueOf(type)));
        verify(feature).place(level, generator, random, pos);
    }

    @Test
    void vanillaPoplarWeightsAndExistingEnumOrdinalsAreUnchanged() throws Exception {
        Field field = TreeGrower.class.getDeclaredField("trees");
        field.setAccessible(true);
        WeightedList<ResourceKey<Feature>> trees = (WeightedList<ResourceKey<Feature>>) field.get(TreeGrower.POPLAR);
        assertEquals(List.of(
            new Weighted<>(TreeFeatures.RED_POPLAR, 1),
            new Weighted<>(TreeFeatures.ORANGE_POPLAR, 1),
            new Weighted<>(TreeFeatures.YELLOW_POPLAR, 1)
        ), trees.unwrap());
        assertEquals(25, TreeType.PALE_OAK_CREAKING.ordinal());
        assertTrue(TreeType.valueOf("RED_POPLAR").ordinal() > TreeType.PALE_OAK_CREAKING.ordinal());
    }

    @Test
    void unknownFeaturesAreNotSilentlyReplacedByOak() {
        assertThrows(IllegalArgumentException.class, () -> identify(Holder.direct(mock(Feature.class))));
    }

    @Test
    void foliaKeepsSimultaneousTreeTypesOnTheirOwnThreads() throws Exception {
        ThreadLocal<TreeType> local = regionTreeType();
        assumeTrue(local != null, "Standalone Sinopia does not use region threading");
        Holder<Feature> red = RegistryHelper.registryAccess().lookupOrThrow(Registries.FEATURE).getOrThrow(TreeFeatures.RED_POPLAR);
        Holder<Feature> yellow = RegistryHelper.registryAccess().lookupOrThrow(Registries.FEATURE).getOrThrow(TreeFeatures.YELLOW_POPLAR);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> identifyOnThread(red, barrier));
            var second = executor.submit(() -> identifyOnThread(yellow, barrier));
            assertEquals("RED_POPLAR", first.get(10, TimeUnit.SECONDS));
            assertEquals("YELLOW_POPLAR", second.get(10, TimeUnit.SECONDS));
        }
        assertNull(local.get());
    }

    private static String identifyOnThread(Holder<Feature> holder, CyclicBarrier barrier) throws Exception {
        try {
            try {
                identify(holder);
            } catch (Throwable failure) {
                throw new AssertionError(failure);
            }
            barrier.await(5, TimeUnit.SECONDS);
            return currentTreeType().name();
        } finally {
            regionTreeType().remove();
        }
    }
}
