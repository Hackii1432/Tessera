package io.papermc.paper.porting;

import ca.spottedleaf.dataconverter.minecraft.MCDataConverter;
import ca.spottedleaf.dataconverter.minecraft.datatypes.MCDataType;
import ca.spottedleaf.dataconverter.minecraft.datatypes.MCTypeRegistry;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.DSL;
import com.mojang.serialization.Dynamic;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@Normal
class Sinopia263MigrationTest {
    private static final int OLD = 4903;

    private static int current() {
        return SharedConstants.getCurrentVersion().dataVersion().version();
    }

    private static CompoundTag vanilla(DSL.TypeReference type, CompoundTag input, int from) {
        return (CompoundTag) DataFixers.getDataFixer().update(type, new Dynamic<>(NbtOps.INSTANCE, input.copy()), from, current()).getValue();
    }

    @Test
    void blockStateFieldNamesMatchVanilla() throws Exception {
        CompoundTag input = TagParser.parseCompoundFully("{Name:'minecraft:oak_fence',Properties:{north:'true',waterlogged:'false'},plugin_data:{untouched:42}}");
        CompoundTag actual = MCDataConverter.convertTag(MCTypeRegistry.BLOCK_STATE, input.copy(), OLD, current());
        assertEquals(vanilla(References.BLOCK_STATE, input, OLD), actual);
        assertEquals("minecraft:oak_fence", actual.getStringOr("id", ""));
        assertFalse(actual.contains("Name"));
        assertTrue(actual.contains("properties"));
        assertEquals(input.getCompoundOrEmpty("plugin_data"), actual.getCompoundOrEmpty("plugin_data"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"noise", "surface", "carvers", "full"})
    void terrainStatusesAndPaletteMatchVanilla(String status) throws Exception {
        CompoundTag input = TagParser.parseCompoundFully("{Status:'minecraft:" + status + "',xPos:0,zPos:0,sections:[{Y:0b,block_states:{palette:[{Name:'minecraft:stone'}]},biomes:{palette:['minecraft:plains']}}],Heightmaps:{test:[L;1]},plugin_data:7}");
        CompoundTag actual = MCDataConverter.convertTag(MCTypeRegistry.CHUNK, input.copy(), OLD, current());
        assertEquals(vanilla(References.CHUNK, input, OLD), actual);
        assertEquals("minecraft:" + switch (status) {
            case "noise", "surface" -> "biomes";
            case "carvers" -> "terrain";
            default -> status;
        }, actual.getStringOr("Status", ""));
        boolean discarded = status.equals("noise") || status.equals("surface");
        assertEquals(!discarded, actual.getListOrEmpty("sections").getCompoundOrEmpty(0).contains("block_states"));
    }

    @Test
    void intermediateSnapshotAndRetrogenMatchVanilla() throws Exception {
        CompoundTag input = TagParser.parseCompoundFully("{Status:'minecraft:surface',below_zero_retrogen:{target_status:'minecraft:carvers'},sections:[{Y:-1b,block_states:{palette:[{id:'minecraft:stone'}]}},{Y:0b,block_states:{palette:[{id:'minecraft:stone'}]}}]}");
        assertEquals(vanilla(References.CHUNK, input, 5010), MCDataConverter.convertTag(MCTypeRegistry.CHUNK, input.copy(), 5010, current()));
    }

    @Test
    void nestedStructurePaletteMatchesVanilla() throws Exception {
        CompoundTag input = TagParser.parseCompoundFully("{size:[1,1,1],palette:[{Name:'minecraft:iron_bars',Properties:{east:'true'}}],blocks:[{pos:[0,0,0],state:0}],entities:[]}");
        assertEquals(vanilla(References.STRUCTURE, input, OLD), MCDataConverter.convertTag(MCTypeRegistry.STRUCTURE, input.copy(), OLD, current()));
    }

    @Test
    void olderDataRunsBothConversionStages() throws Exception {
        CompoundTag input = TagParser.parseCompoundFully("{Name:'minecraft:grass_path'}");
        CompoundTag actual = MCDataConverter.convertTag(MCTypeRegistry.BLOCK_STATE, input.copy(), 2586, current());
        assertEquals(vanilla(References.BLOCK_STATE, input, 2586), actual);
        assertEquals("minecraft:dirt_path", actual.getStringOr("id", ""));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void jsonUsesTheSameBoundary(boolean compressed) {
        JsonObject input = JsonParser.parseString("{\"Name\":\"minecraft:oak_stairs\",\"Properties\":{\"shape\":\"inner_left\"}}").getAsJsonObject();
        JsonObject actual = MCDataConverter.convertJson(MCTypeRegistry.BLOCK_STATE, input, compressed, OLD, current());
        assertEquals("minecraft:oak_stairs", actual.get("id").getAsString());
        assertEquals("inner_left", actual.getAsJsonObject("properties").get("shape").getAsString());
        assertSame(actual, MCDataConverter.convertJson(MCTypeRegistry.BLOCK_STATE, actual, compressed, current(), current()));
    }

    @Test
    void currentDataAndDowngradesDoNotRunLegacyWalkers() throws Exception {
        CompoundTag input = TagParser.parseCompoundFully("{id:'minecraft:oak_stairs',properties:{shape:'inner_left'}}");
        assertSame(input, MCDataConverter.convertTag(MCTypeRegistry.BLOCK_STATE, input, current(), current()));
        assertSame(input, MCDataConverter.convertTag(MCTypeRegistry.BLOCK_STATE, input, current(), OLD));
    }

    @Test
    void unmappedTypesCannotSilentlySkipNewFixes() {
        assertThrows(IllegalArgumentException.class, () -> MCDataConverter.convertTag(new MCDataType("PluginType"), new CompoundTag(), OLD, current()));
    }

    @Test
    void independentConversionsCanRunInParallel() throws Exception {
        try (var executor = Executors.newFixedThreadPool(4)) {
            var results = new ArrayList<java.util.concurrent.Future<CompoundTag>>();
            for (int index = 0; index < 32; index++) {
                final int marker = index;
                results.add(executor.submit(() -> {
                    CompoundTag input = TagParser.parseCompoundFully("{Name:'minecraft:stone'}");
                    input.putInt("marker", marker);
                    return MCDataConverter.convertTag(MCTypeRegistry.BLOCK_STATE, input, OLD, current());
                }));
            }
            for (int index = 0; index < results.size(); index++) {
                CompoundTag result = results.get(index).get();
                assertEquals(index, result.getIntOr("marker", -1));
                assertEquals("minecraft:stone", result.getStringOr("id", ""));
            }
        }
    }
}
