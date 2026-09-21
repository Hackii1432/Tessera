package io.papermc.paper.porting;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.generator.CraftWorldInfo;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.support.RegistryHelper;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@AllFeatures
class BiomeResolverBackportTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reusableVanillaProvidersUseNoMutableSamplerCache(boolean worldInfo) throws Exception {
        BiomeSource source = mock(BiomeSource.class);
        BiomeResolver resolver = mock(BiomeResolver.class);
        RandomState random = mock(RandomState.class);
        NoiseBasedChunkGenerator generator = mock(NoiseBasedChunkGenerator.class);
        when(generator.getBiomeSource()).thenReturn(source);
        when(generator.generatorSettings()).thenReturn(Holder.direct(mock(NoiseGeneratorSettings.class)));
        when(source.possibleBiomes()).thenReturn(java.util.Set.of());
        when(source.createUncachedResolver(random)).thenReturn(resolver);
        var plains = RegistryHelper.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        when(resolver.getNoiseBiome(anyInt(), anyInt(), anyInt())).thenReturn(plains);
        BiomeProvider provider;
        if (worldInfo) {
            try (var factory = mockStatic(RandomState.class)) {
                factory.when(() -> RandomState.create(any(), anyLong(), any())).thenReturn(random);
                provider = new CraftWorldInfo("test", NamespacedKey.minecraft("overworld"), 42L,
                    FeatureFlags.REGISTRY.allFlags(), World.Environment.NORMAL, mock(DimensionType.class),
                    generator, RegistryHelper.registryAccess(), UUID.randomUUID()).vanillaBiomeProvider();
            }
        } else {
            ServerLevel level = mock(ServerLevel.class);
            ServerChunkCache cache = mock(ServerChunkCache.class);
            when(level.getChunkSource()).thenReturn(cache);
            when(cache.getGenerator()).thenReturn(generator);
            when(cache.randomState()).thenReturn(random);
            CraftWorld world = mock(CraftWorld.class);
            when(world.getHandle()).thenReturn(level);
            doCallRealMethod().when(world).vanillaBiomeProvider();
            provider = world.vanillaBiomeProvider();
        }
        verify(source).createUncachedResolver(random);
        verify(source, never()).createCachingResolver(any());
        try (var workers = Executors.newFixedThreadPool(2)) {
            Callable<org.bukkit.block.Biome> sample = () -> provider.getBiome(null, 1000, 64, -1000);
            for (var result : workers.invokeAll(java.util.Collections.nCopies(64, sample))) {
                assertEquals(org.bukkit.block.Biome.PLAINS, result.get());
            }
        }
        verify(resolver, times(64)).getNoiseBiome(250, 16, -250);
    }
}
