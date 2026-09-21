package io.papermc.paper.porting;

import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import ca.spottedleaf.moonrise.patches.chunk_system.queue.ChunkUnloadQueue;
import ca.spottedleaf.moonrise.patches.chunk_system.util.ParallelSearchRadiusIteration;
import io.papermc.paper.configuration.GlobalConfiguration;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Normal
class MoonriseSeptemberBackportTest {
    @TempDir Path directory;

    @Test
    void debugCoordinatesKeepDifferentXAndZ() {
        ChunkUnloadQueue queue = new ChunkUnloadQueue(4);
        queue.addChunk(35, -49);
        var section = queue.toDebugJson().getAsJsonArray().get(0).getAsJsonObject();
        assertEquals(2, section.get("sectionX").getAsInt());
        assertEquals(-4, section.get("sectionZ").getAsInt());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3, 8, 32})
    void euclideanSearchOrdersEveryChunkByDistance(int radius) {
        long[] actual = ParallelSearchRadiusIteration.getEuclideanIteration(radius);
        long[] search = ParallelSearchRadiusIteration.getSearchIteration(radius);
        assertArrayEquals(Arrays.stream(search).sorted().toArray(), Arrays.stream(actual).sorted().toArray());
        int previous = -1;
        for (long packed : actual) {
            int x = CoordinateUtils.getChunkX(packed), z = CoordinateUtils.getChunkZ(packed);
            int distance = x * x + z * z;
            assertTrue(distance >= previous, "Euclidean distance must never decrease");
            previous = distance;
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3, 16})
    void integerPropertyRejectsValuesOutsideItsActualRange(int min) {
        IntegerProperty property = IntegerProperty.create("test", min, min + 3);
        for (int value = min; value <= min + 3; value++) assertEquals(value - min, property.moonrise$getIdFor(value));
        for (int invalid : new int[] {min - 1, min + 4, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertTrue(property.moonrise$getIdFor(invalid) < 0, "invalid value " + invalid);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingRegionDoesNotEvictFromAFullCache() throws Exception {
        RegionStorageInfo info = new RegionStorageInfo("cache-test", Level.OVERWORLD, "test");
        try (RegionFileStorage storage = new RegionFileStorage(info, this.directory, false)) {
            Field field = RegionFileStorage.class.getDeclaredField("regionCache");
            field.setAccessible(true);
            Long2ObjectLinkedOpenHashMap<RegionFile> cache = (Long2ObjectLinkedOpenHashMap<RegionFile>) field.get(storage);
            int size = GlobalConfiguration.get() == null ? 256 : GlobalConfiguration.get().misc.regionFileCacheSize;
            RegionFile oldest = mock(RegionFile.class);
            for (int i = 0; i < size; i++) cache.putAndMoveToFirst(ChunkPos.pack(i, 0), i == 0 ? oldest : mock(RegionFile.class));
            assertNull(storage.moonrise$getRegionFileIfExists(-320, -320));
            assertEquals(size, cache.size());
            verify(oldest, never()).close();
            assertFalse(Files.exists(this.directory.resolve("r.-10.-10.mca")));
            Files.createFile(this.directory.resolve("r.-11.-11.mca"));
            assertNotNull(storage.moonrise$getRegionFileIfExists(-352, -352));
            assertEquals(size, cache.size());
            verify(oldest).close();
        }
    }
}
