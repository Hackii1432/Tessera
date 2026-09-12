package io.papermc.paper.porting;

import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.visitors.CollectToTag;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

@Normal
class Sinopia263StorageTest {
    @TempDir Path temporary;

    private RegionFileStorage storage(Path folder) {
        return new RegionFileStorage(new RegionStorageInfo("port-test", Level.OVERWORLD, "test"), folder, false);
    }

    @Test
    void readsScansAndDeletesDoNotCreateMissingRegions() throws Exception {
        Path folder = this.temporary.resolve("absent");
        try (RegionFileStorage storage = this.storage(folder)) {
            ChunkPos pos = new ChunkPos(1000, -1000);
            assertNull(storage.read(pos));
            CollectToTag collector = new CollectToTag();
            storage.scanChunk(pos, collector);
            assertNull(collector.getResult());
            storage.write(pos, null);
            assertFalse(Files.exists(folder));
        }
    }

    @Test
    void directoriesAreNotTreatedAsRegionFiles() throws Exception {
        Path directory = Files.createDirectories(this.temporary.resolve("r.0.0.mca"));
        try (RegionFileStorage storage = this.storage(this.temporary)) {
            assertNull(storage.read(new ChunkPos(0, 0)));
            assertTrue(Files.isDirectory(directory));
        }
    }

    @Test
    void aWriteInvalidatesTheNegativeCacheAndSurvivesReopening() throws Exception {
        ChunkPos pos = new ChunkPos(-33, 65);
        CompoundTag data = new CompoundTag();
        data.putString("marker", "saved");
        try (RegionFileStorage storage = this.storage(this.temporary)) {
            assertNull(storage.read(pos));
            assertTrue(storage.moonrise$doesRegionFileNotExistNoIO(pos.x(), pos.z()));
            storage.write(pos, data);
            assertFalse(storage.moonrise$doesRegionFileNotExistNoIO(pos.x(), pos.z()));
            assertEquals(data, storage.read(pos));
            CollectToTag collector = new CollectToTag();
            storage.scanChunk(pos, collector);
            assertEquals(data, collector.getResult());
        }
        try (RegionFileStorage reopened = this.storage(this.temporary)) {
            assertEquals(data, reopened.read(pos));
            reopened.write(pos, null);
            assertNull(reopened.read(pos));
        }
    }

    @Test
    void newFillAndOptimizedBitAccessAgreeForEveryWidth() {
        for (int bits = 1; bits <= 32; bits++) {
            SimpleBitStorage storage = new SimpleBitStorage(bits, 4096);
            int value = bits == 32 ? Integer.MAX_VALUE : (int)((1L << bits) - 1L);
            storage.fill(value);
            for (int index = 0; index < storage.getSize(); index++) {
                assertEquals(value, storage.get(index), "fill at bit width " + bits);
                storage.set(index, index & value);
            }
            for (int index = 0; index < storage.getSize(); index++) {
                assertEquals(index & value, storage.get(index), "set at bit width " + bits);
            }
        }
    }
}
