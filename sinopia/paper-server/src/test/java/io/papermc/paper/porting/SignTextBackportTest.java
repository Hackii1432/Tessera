package io.papermc.paper.porting;

import io.papermc.paper.datacomponent.item.PaperSignText;
import io.papermc.paper.datacomponent.item.SignText;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.DyeColor;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@AllFeatures
class SignTextBackportTest {
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4})
    void shortListsAlwaysBuildFourIndependentLines(int count) {
        List<Component> input = new ArrayList<>();
        for (int i = 0; i < count; i++) input.add(Component.text("line " + i));
        // Keep the pre-existing binary signature returning Builder.
        SignText.Builder builder = SignText.signText(input);
        SignText built = builder.build();
        input.clear();
        assertEquals(4, built.lines().size());
        assertEquals(built.lines(), built.filteredLines());
        for (int i = count; i < 4; i++) assertEquals(Component.empty(), built.lines().get(i));
        builder.line(3, Component.text("changed"));
        assertNotEquals(Component.text("changed"), built.lines().get(3));
        assertDoesNotThrow(() -> net.minecraft.world.level.block.entity.SignText.CODEC
            .encodeStart(com.mojang.serialization.JsonOps.INSTANCE, ((PaperSignText) built).getHandle()).getOrThrow());
    }

    @Test
    void emptyBuilderCanSetAnyLineAndAppendUntilFull() {
        SignText.Builder builder = SignText.signText().line(1, Component.text("second"));
        builder.addLine(Component.text("third")).addLine(Component.text("fourth"));
        assertEquals(List.of(Component.empty(), Component.text("second"), Component.text("third"), Component.text("fourth")), builder.build().lines());
        assertThrows(IllegalArgumentException.class, () -> builder.addLine(Component.text("fifth")));
        assertThrows(IllegalArgumentException.class, () -> builder.line(-1, Component.empty()));
        assertThrows(IllegalArgumentException.class, () -> builder.line(4, Component.empty()));
        assertThrows(IllegalArgumentException.class, () -> builder.lines(java.util.Collections.nCopies(5, Component.empty())));
    }

    @Test
    void toBuilderDoesNotMutateOriginalOrVanillaEmptySingleton() {
        SignText original = SignText.signText().color(DyeColor.RED).hasGlowingText(true).build();
        SignText modified = original.toBuilder().line(0, Component.text("new")).build();
        assertEquals(Component.empty(), original.lines().getFirst());
        assertEquals(Component.text("new"), modified.filteredLines().getFirst());
        assertEquals(DyeColor.RED, modified.color());
        assertTrue(modified.hasGlowingText());
        assertTrue(net.minecraft.world.level.block.entity.SignText.EMPTY.getMessages(false).stream().allMatch(c -> c.getString().isEmpty()));
    }

    @Test
    void replacingLinesResetsOldFilteredText() {
        SignText result = SignText.signText(List.of(Component.text("old"))).build().toBuilder()
            .lines(List.of(Component.text("new"))).addLine(Component.text("next")).build();
        assertEquals(result.lines(), result.filteredLines());
        assertEquals(Component.text("next"), result.lines().get(1));
    }
}
