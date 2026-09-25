package io.papermc.paper.porting;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import net.minecraft.core.IdMapper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.BitStorage;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ZeroBitStorage;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.Strategy;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

@Normal
class MoonrisePalettePackingTest {
    private static final List<String> VALUES = java.util.stream.IntStream.range(0, 8192).mapToObj(i -> "entry_" + i).toList();

    private static Strategy<String> strategy(boolean biomes) {
        IdMapper<String> map = new IdMapper<>();
        VALUES.forEach(map::add);
        return biomes ? Strategy.createForBiomes(map) : Strategy.createForBlockStates(map);
    }

    private static PalettedContainer<String> container(Strategy<String> strategy, int distinct, boolean biomes, String[] presets) {
        var container = new PalettedContainer<>(VALUES.getFirst(), strategy, presets);
        int axisBits = biomes ? 2 : 4;
        int mask = (1 << axisBits) - 1;
        for (int i = 0; i < strategy.entryCount(); i++) {
            container.set(i & mask, i >> (2 * axisBits), (i >> axisBits) & mask, VALUES.get(i % distinct));
        }
        return container;
    }

    private static void check(PalettedContainerRO.PackedData<String> packed, int count, int distinct) {
        long[] data = packed.storage().map(java.util.stream.LongStream::toArray).orElse(null);
        BitStorage storage = data == null ? new ZeroBitStorage(count) : new SimpleBitStorage(packed.bitsPerEntry(), count, data);
        for (int i = 0; i < count; i++) assertEquals(VALUES.get(i % distinct), packed.paletteEntries().get(storage.get(i)), "entry " + i);
    }

    @ParameterizedTest
    @CsvSource({"false,1", "false,2", "false,16", "false,17", "false,256", "false,257", "false,4096",
        "true,1", "true,2", "true,8", "true,9", "true,64"})
    void blockAndBiomePalettesRoundTripAcrossStorageWidths(boolean biomes, int distinct) {
        Strategy<String> strategy = strategy(biomes);
        var container = container(strategy, distinct, biomes, null);
        var first = container.pack(strategy);
        // Reuse this thread's cache before consuming the first result's lazy stream.
        check(container(strategy, distinct == 1 ? 2 : 1, biomes, null).pack(strategy), strategy.entryCount(), distinct == 1 ? 2 : 1);
        check(first, strategy.entryCount(), distinct);
        Codec<PalettedContainer<String>> codec = PalettedContainer.codecRW(Codec.STRING, strategy, VALUES.getFirst(), null);
        var encoded = codec.encodeStart(NbtOps.INSTANCE, container).getOrThrow();
        check(codec.parse(NbtOps.INSTANCE, encoded).getOrThrow().pack(strategy), strategy.entryCount(), distinct);
    }

    @Test
    void unusedAntiXrayPresetsDoNotChangeSavedStates() {
        Strategy<String> strategy = strategy(false);
        var container = container(strategy, 2, false, new String[] {VALUES.get(30), VALUES.get(300)});
        check(container.pack(strategy), strategy.entryCount(), 2);
    }

    @Test
    void independentRegionsCanPackInParallel() throws Exception {
        try (var executor = Executors.newFixedThreadPool(4)) {
            var tasks = new ArrayList<java.util.concurrent.Callable<Void>>();
            for (int task = 0; task < 32; task++) {
                final int distinct = task % 2 == 0 ? 17 : 257;
                tasks.add(() -> {
                    Strategy<String> strategy = strategy(false);
                    var container = container(strategy, distinct, false, null);
                    for (int iteration = 0; iteration < 12; iteration++) check(container.pack(strategy), strategy.entryCount(), distinct);
                    return null;
                });
            }
            for (var task : executor.invokeAll(tasks)) task.get();
        }
    }
}
