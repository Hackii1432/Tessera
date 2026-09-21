package io.papermc.paper.porting;

import com.destroystokyo.paper.MaterialTags;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.Compostable;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.craftbukkit.inventory.CraftItemType;
import org.bukkit.inventory.ItemType;
import org.bukkit.support.RegistryHelper;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@AllFeatures
class ItemMetadataSeptemberBackportTest {
    @Test
    void tagsContainNewVariantsWithoutDroppingLegacyConcrete() {
        assertTrue(MaterialTags.MUSHROOMS.getValues().containsAll(Tag.ITEMS_MUSHROOMS.getValues()));
        assertTrue(MaterialTags.ORES.getValues().containsAll(Tag.ORES.getValues()));
        assertTrue(MaterialTags.ORES.isTagged(Material.ANCIENT_DEBRIS));
        for (Tag<Material> tag : java.util.List.of(Tag.CONCRETE, Tag.CONCRETE_SLABS, Tag.CONCRETE_STAIRS, Tag.WOOL_SLABS, Tag.WOOL_STAIRS, Tag.ITEMS_CUSHIONS)) {
            assertFalse(tag.getValues().isEmpty());
            assertTrue(MaterialTags.COLORABLE.getValues().containsAll(tag.getValues()), tag.getKey().toString());
        }
    }

    @ParameterizedTest
    @CsvSource({"WHEAT_SEEDS,0.3", "WHEAT,0.65", "BREAD,0.85", "CAKE,1.0"})
    void vanillaCompostChancesAreProbabilitiesWithoutWorldAccess(String material, float expected) {
        MinecraftServer server = mock(MinecraftServer.class);
        when(server.reloadableRegistries()).thenReturn(RegistryHelper.context().datapack().fullRegistries());
        ItemType type = Material.valueOf(material).asItemType();
        try (var servers = mockStatic(MinecraftServer.class); var bukkit = mockStatic(Bukkit.class)) {
            servers.when(MinecraftServer::getServer).thenReturn(server);
            assertEquals(expected, type.getCompostChance(), 0.00001F);
            bukkit.verifyNoInteractions();
            verify(server, never()).overworld();
        }
    }

    @ParameterizedTest
    @CsvSource({"COAL,1600", "STICK,100", "DIAMOND,0"})
    void vanillaBurnDurationDoesNotBorrowAWorldRandom(String material, int expected) {
        MinecraftServer server = mock(MinecraftServer.class);
        when(server.reloadableRegistries()).thenReturn(RegistryHelper.context().datapack().fullRegistries());
        ItemType type = Material.valueOf(material).asItemType();
        try (var servers = mockStatic(MinecraftServer.class); var bukkit = mockStatic(Bukkit.class)) {
            servers.when(MinecraftServer::getServer).thenReturn(server);
            assertEquals(expected, type.getBurnDuration());
            bukkit.verifyNoInteractions();
            verify(server, never()).overworld();
        }
    }

    @Test
    void constantDatapackValuesDoNotRequireAServerOrWorld() {
        Item item = mock(Item.class);
        when(item.components()).thenReturn(DataComponentMap.builder()
            .set(DataComponents.COOKING_FUEL, new CookingFuel(new ResolvableInt.Constant(123), new net.minecraft.world.level.storage.loot.providers.number.floats.ResolvableFloat.Constant(1.0F)))
            .set(DataComponents.COMPOSTABLE, new Compostable(new ResolvableInt.Constant(2))).build());
        CraftItemType<?> type = mock(CraftItemType.class);
        when(type.getHandle()).thenReturn(item);
        doCallRealMethod().when(type).getBurnDuration();
        doCallRealMethod().when(type).getCompostChance();
        try (var servers = mockStatic(MinecraftServer.class); var bukkit = mockStatic(Bukkit.class)) {
            assertEquals(123, type.getBurnDuration());
            assertEquals(1.0F, type.getCompostChance());
            servers.verifyNoInteractions();
            bukkit.verifyNoInteractions();
        }
    }
}
