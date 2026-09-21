package io.papermc.paper.datacomponent.item;

import com.google.common.base.Preconditions;
import io.papermc.paper.adventure.PaperAdventure;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import org.bukkit.DyeColor;
import org.bukkit.craftbukkit.util.Handleable;

import static io.papermc.paper.util.BoundChecker.requireRange;

public record PaperSignText(
    net.minecraft.world.level.block.entity.SignText impl
) implements SignText, Handleable<net.minecraft.world.level.block.entity.SignText> {

    @Override
    public net.minecraft.world.level.block.entity.SignText getHandle() {
        return this.impl;
    }

    @Override
    public List<Component> lines() {
        return io.papermc.paper.adventure.PaperAdventure.asAdventure(this.impl.getMessages(false));
    }

    @Override
    public List<Component> filteredLines() {
        return io.papermc.paper.adventure.PaperAdventure.asAdventure(this.impl.getMessages(true));
    }

    @Override
    public DyeColor color() {
        return DyeColor.getByWoolData((byte) this.impl.getColor().getId());
    }

    @Override
    public boolean hasGlowingText() {
        return this.impl.hasGlowingText();
    }

    @Override
    public Builder toBuilder() {
        return new BuilderImpl(this.impl.getMessages(false), this.impl.getMessages(true))
            .color(this.color())
            .hasGlowingText(this.hasGlowingText());
    }

    static final class BuilderImpl implements Builder {

        private List<net.minecraft.network.chat.Component> lines;
        private List<net.minecraft.network.chat.Component> filteredLines;
        private net.minecraft.world.item.DyeColor color = net.minecraft.world.level.block.entity.SignText.EMPTY.getColor();
        private boolean hasGlowingText = net.minecraft.world.level.block.entity.SignText.EMPTY.hasGlowingText();

        BuilderImpl() {
            this(List.of());
        }

        BuilderImpl(List<net.minecraft.network.chat.Component> messages) {
            this(messages, messages);
        }

        BuilderImpl(List<net.minecraft.network.chat.Component> messages, List<net.minecraft.network.chat.Component> filteredLines) {
            validateLineCount(0, messages.size());
            validateLineCount(0, filteredLines.size());
            this.lines = new ArrayList<>(messages);
            this.filteredLines = new ArrayList<>(filteredLines);
        }

        private static void validateLineCount(final int current, final int add) {
            final int newSize = current + add;
            Preconditions.checkArgument(
                newSize <= net.minecraft.world.level.block.entity.SignText.LINES,
                "Cannot have more than %s lines, had %s",
                net.minecraft.world.level.block.entity.SignText.LINES,
                newSize
            );
        }

        @Override
        public Builder lines(final List<? extends ComponentLike> messages) {
            validateLineCount(0, messages.size());
            this.lines = PaperAdventure.asVanilla(new ArrayList<>(ComponentLike.asComponents(messages)));
            this.filteredLines = new ArrayList<>(this.lines);
            return this;
        }

        @Override
        public Builder line(final int index, final ComponentLike message) {
            requireRange(index, "index", 0, net.minecraft.world.level.block.entity.SignText.LINES - 1);
            final net.minecraft.network.chat.Component line = PaperAdventure.asVanilla(message.asComponent());
            fillWithBlankLines(this.lines, index + 1);
            fillWithBlankLines(this.filteredLines, index + 1);
            this.lines.set(index, line);
            this.filteredLines.set(index, line);
            return this;
        }

        @Override
        public Builder addLine(final ComponentLike message) {
            validateLineCount(this.lines.size(), 1);
            this.line(this.lines.size(), message);
            return this;
        }

        @Override
        public Builder color(final DyeColor color) {
            this.color = net.minecraft.world.item.DyeColor.byId(color.getWoolData());
            return this;
        }

        @Override
        public Builder hasGlowingText(final boolean hasGlowingText) {
            this.hasGlowingText = hasGlowingText;
            return this;
        }

        @Override
        public SignText build() {
            if (this.lines.isEmpty() && this.filteredLines.isEmpty() && this.color == net.minecraft.world.item.DyeColor.BLACK && !this.hasGlowingText) {
                return new PaperSignText(net.minecraft.world.level.block.entity.SignText.EMPTY);
            }

            return new PaperSignText(new net.minecraft.world.level.block.entity.SignText(
                paddedCopy(this.lines),
                paddedCopy(this.filteredLines),
                this.color,
                this.hasGlowingText
            ));
        }

        private static void fillWithBlankLines(final List<net.minecraft.network.chat.Component> messages, final int size) {
            while (messages.size() < size) {
                messages.add(net.minecraft.network.chat.CommonComponents.EMPTY);
            }
        }

        private static List<net.minecraft.network.chat.Component> paddedCopy(final List<net.minecraft.network.chat.Component> messages) {
            final List<net.minecraft.network.chat.Component> copy = new ArrayList<>(messages);
            fillWithBlankLines(copy, net.minecraft.world.level.block.entity.SignText.LINES);
            return List.copyOf(copy);
        }
    }
}
