package io.papermc.paper.porting;

import ca.spottedleaf.dataconverter.minecraft.MCDataConverter;
import ca.spottedleaf.dataconverter.minecraft.datatypes.MCTypeRegistry;
import com.google.common.cache.LoadingCache;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import io.papermc.paper.adventure.PaperAdventure;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.VarInt;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.game.ServerboundSetGameRulePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.network.ServerCommandSuggestionsProvider;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@Normal
class PaperMainRuntimeTest {
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 128})
    void gamerulePacketAcceptsBoundedLists(int size) {
        var entry = new ServerboundSetGameRulePacket.Entry(
            ResourceKey.create(Registries.GAME_RULE, Identifier.withDefaultNamespace("locator_bar")), "true");
        var packet = new ServerboundSetGameRulePacket(java.util.Collections.nCopies(size, entry));
        var buffer = Unpooled.buffer();
        try {
            ServerboundSetGameRulePacket.STREAM_CODEC.encode(buffer, packet);
            assertEquals(packet, ServerboundSetGameRulePacket.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {129, 1000000, Integer.MAX_VALUE})
    void oversizedGamerulePacketIsRejectedBeforeReadingEntries(int size) {
        var buffer = Unpooled.buffer();
        try {
            VarInt.write(buffer, size);
            assertThrows(DecoderException.class, () -> ServerboundSetGameRulePacket.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void namespacedFilterNeverMutatesProviderResults(int start) {
        boolean previous = org.spigotmc.SpigotConfig.sendNamespaced;
        org.spigotmc.SpigotConfig.sendNamespaced = false;
        try {
            StringRange range = StringRange.at(start);
            List<Suggestion> entries = List.of(new Suggestion(range, "minecraft:help"), new Suggestion(range, "help"));
            Suggestions input = new Suggestions(range, entries);
            Suggestions output = ServerCommandSuggestionsProvider.filterNamespacedSuggestions(input);
            assertEquals(start <= 1 ? List.of(entries.get(1)) : entries, output.getList());
            assertEquals(2, input.getList().size());
            org.spigotmc.SpigotConfig.sendNamespaced = true;
            assertSame(input, ServerCommandSuggestionsProvider.filterNamespacedSuggestions(input));
        } finally {
            org.spigotmc.SpigotConfig.sendNamespaced = previous;
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {4903, 5008})
    void obsoleteMemoryIsRemovedWithoutDiscardingOtherMemories(int from) throws Exception {
        var input = TagParser.parseCompoundFully("{id:'minecraft:goat',Brain:{memories:{'minecraft:is_tempted':{value:1b},'minecraft:temptation_cooldown_ticks':{value:42}}},plugin_data:{untouched:7},Passengers:[{id:'minecraft:axolotl',Brain:{memories:{'minecraft:is_tempted':{value:1b}}}}]}");
        var result = MCDataConverter.convertTag(MCTypeRegistry.ENTITY, input.copy(), from,
            SharedConstants.getCurrentVersion().dataVersion().version());
        var memories = result.getCompoundOrEmpty("Brain").getCompoundOrEmpty("memories");
        assertFalse(memories.contains("minecraft:is_tempted"));
        assertEquals(42, memories.getCompoundOrEmpty("minecraft:temptation_cooldown_ticks").getIntOr("value", -1));
        assertEquals(7, result.getCompoundOrEmpty("plugin_data").getIntOr("untouched", -1));
        assertFalse(result.getListOrEmpty("Passengers").getCompoundOrEmpty(0).getCompoundOrEmpty("Brain")
            .getCompoundOrEmpty("memories").contains("minecraft:is_tempted"));
        assertTrue(input.getCompoundOrEmpty("Brain").getCompoundOrEmpty("memories").contains("minecraft:is_tempted"));
    }

    @Test
    void jsonAndEntityChunkUseTheSameMemoryMigration() throws Exception {
        var input = com.google.gson.JsonParser.parseString("{\"id\":\"minecraft:goat\",\"Brain\":{\"memories\":{\"minecraft:is_tempted\":{\"value\":true}}}}").getAsJsonObject();
        var result = MCDataConverter.convertJson(MCTypeRegistry.ENTITY, input.deepCopy(), false, 4903,
            SharedConstants.getCurrentVersion().dataVersion().version());
        assertFalse(result.getAsJsonObject("Brain").getAsJsonObject("memories").has("minecraft:is_tempted"));
        var chunk = TagParser.parseCompoundFully("{Entities:[{id:'minecraft:goat',Brain:{memories:{'minecraft:is_tempted':{value:1b}}}}],Position:[I;0,0]}");
        var converted = MCDataConverter.convertTag(MCTypeRegistry.ENTITY_CHUNK, chunk, 4903,
            SharedConstants.getCurrentVersion().dataVersion().version());
        assertFalse(converted.getListOrEmpty("Entities").getCompoundOrEmpty(0).getCompoundOrEmpty("Brain")
            .getCompoundOrEmpty("memories").contains("minecraft:is_tempted"));
    }

    @Test
    void localizedCodecCachesStayBoundedUnderParallelUse() throws Exception {
        try (var executor = Executors.newFixedThreadPool(4)) {
            var tasks = new java.util.ArrayList<java.util.concurrent.Callable<Void>>();
            for (int index = 0; index < 1024; index++) {
                final Locale locale = new Locale.Builder().setLanguage("en").setVariant("test" + index).build();
                tasks.add(() -> {
                    assertNotNull(PaperAdventure.localizedCodec(locale));
                    assertNotNull(ComponentSerialization.localizedCodec(locale));
                    return null;
                });
            }
            for (var task : executor.invokeAll(tasks)) task.get();
        }
        for (Class<?> owner : List.of(PaperAdventure.class, ComponentSerialization.class)) {
            Field field = owner.getDeclaredField("LOCALIZED_CODECS");
            field.setAccessible(true);
            LoadingCache<?, ?> cache = (LoadingCache<?, ?>) field.get(null);
            cache.cleanUp();
            assertTrue(cache.size() <= 256, owner.getSimpleName());
        }
        assertSame(ComponentSerialization.CODEC, ComponentSerialization.localizedCodec(null));
        assertNotNull(PaperAdventure.localizedCodec(null));
    }
}
