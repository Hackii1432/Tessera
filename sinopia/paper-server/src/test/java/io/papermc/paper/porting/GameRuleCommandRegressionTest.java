package io.papermc.paper.porting;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.event.world.PaperWorldGameRuleChangeEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.GameRuleCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.gamerules.GameRules;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Sinopia - 26.3 command feedback must reflect the result before/after the event bridge.
@AllFeatures
class GameRuleCommandRegressionTest {
    private record Completion(boolean success, int result) {}

    /** Real command parsing, event bridge and rule storage; only the plugin decision and live server are mocked. */
    private static final class Attempt implements AutoCloseable {
        final GameRules rules = new GameRules(FeatureFlags.REGISTRY.allFlags());
        final ServerLevel level = mock(ServerLevel.class);
        final MinecraftServer server = mock(MinecraftServer.class);
        final CommandSourceStack source = mock(CommandSourceStack.class);
        final CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        final List<Component> messages = new ArrayList<>();
        final List<Boolean> broadcasts = new ArrayList<>();
        final List<Completion> completions = new ArrayList<>();
        final MockedConstruction<PaperWorldGameRuleChangeEvent> events;
        String pluginValue;
        boolean cancelled;

        Attempt() {
            when(this.level.getGameRules()).thenReturn(this.rules);
            when(this.level.getWorld()).thenReturn(mock(CraftWorld.class));
            when(this.level.getServer()).thenReturn(this.server);
            when(this.source.getLevel()).thenReturn(this.level);
            when(this.source.getBukkitSender()).thenReturn(mock(CommandSender.class));
            when(this.source.permissions()).thenReturn(PermissionSet.ALL_PERMISSIONS);
            when(this.source.hasPermission(any(Permission.class), anyString())).thenReturn(true);
            doAnswer(invocation -> {
                this.messages.add(invocation.<Supplier<Component>>getArgument(0).get());
                this.broadcasts.add(invocation.getArgument(1));
                return null;
            }).when(this.source).sendSuccess(any(), anyBoolean());
            this.dispatcher.setConsumer((context, success, result) -> this.completions.add(new Completion(success, result)));
            CommandBuildContext buildContext = mock(CommandBuildContext.class);
            when(buildContext.enabledFeatures()).thenReturn(FeatureFlags.REGISTRY.allFlags());
            GameRuleCommand.register(this.dispatcher, buildContext);
            this.events = mockConstruction(PaperWorldGameRuleChangeEvent.class, (event, context) -> {
                when(event.callEvent()).thenAnswer(invocation -> !this.cancelled);
                when(event.getValue()).thenAnswer(invocation -> this.pluginValue == null ? context.arguments().get(3) : this.pluginValue);
            });
        }

        int execute(String arguments) throws CommandSyntaxException {
            return this.dispatcher.execute("gamerule " + arguments, this.source);
        }

        void success(String key, String rule, String value, boolean broadcast, int result) {
            assertEquals(1, this.messages.size());
            TranslatableContents contents = assertInstanceOf(TranslatableContents.class, this.messages.getFirst().getContents());
            assertEquals(key, contents.getKey());
            assertArrayEquals(new Object[] {rule, value}, contents.getArgs());
            assertEquals(List.of(broadcast), this.broadcasts);
            assertEquals(List.of(new Completion(true, result)), this.completions);
        }

        void failure(String key, String arguments) {
            CommandSyntaxException failure = assertThrows(CommandSyntaxException.class, () -> this.execute(arguments));
            Component message = assertInstanceOf(Component.class, failure.getRawMessage());
            assertEquals(key, assertInstanceOf(TranslatableContents.class, message.getContents()).getKey());
            assertTrue(this.messages.isEmpty());
            assertEquals(List.of(new Completion(false, 0)), this.completions);
        }

        @Override
        public void close() {
            this.events.close();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void changedBooleanIsSuccessfulEvenWhenItsNumericResultIsZero(boolean value) throws Exception {
        try (Attempt attempt = new Attempt()) {
            attempt.rules.set(GameRules.KEEP_INVENTORY, !value, null);
            int result = value ? 1 : 0;
            assertEquals(result, attempt.execute("keep_inventory " + value));
            assertEquals(value, attempt.rules.get(GameRules.KEEP_INVENTORY));
            attempt.success("commands.gamerule.set", "keep_inventory", Boolean.toString(value), true, result);
            assertEquals(1, attempt.events.constructed().size());
            verify(attempt.server).onGameRuleChanged(attempt.level, GameRules.KEEP_INVENTORY, value);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"random_tick_speed", "minecraft:random_tick_speed"})
    void integerChangeUsesTheCommandWorldAndWorksWithEitherName(String name) throws Exception {
        try (Attempt attempt = new Attempt()) {
            GameRules otherWorld = new GameRules(FeatureFlags.REGISTRY.allFlags());
            assertEquals(9, attempt.execute(name + " 9"));
            assertEquals(9, attempt.rules.get(GameRules.RANDOM_TICK_SPEED));
            assertEquals(3, otherWorld.get(GameRules.RANDOM_TICK_SPEED));
            attempt.success("commands.gamerule.set", "random_tick_speed", "9", true, 9);
            assertEquals(1, attempt.events.constructed().size());
            verify(attempt.server).onGameRuleChanged(attempt.level, GameRules.RANDOM_TICK_SPEED, 9);
        }
    }

    @Test
    void unchangedValueStillReportsAlreadySet() {
        try (Attempt attempt = new Attempt()) {
            attempt.failure("commands.gamerule.not_set", "random_tick_speed 3");
            assertEquals(3, attempt.rules.get(GameRules.RANDOM_TICK_SPEED));
        }
    }

    @Test
    void pluginCancellationIsNotReportedAsAnAlreadySetValue() {
        try (Attempt attempt = new Attempt()) {
            attempt.cancelled = true;
            attempt.pluginValue = "12";
            attempt.failure("sinopia.commands.gamerule.cancelled", "random_tick_speed 9");
            assertEquals(3, attempt.rules.get(GameRules.RANDOM_TICK_SPEED));
            assertEquals(1, attempt.events.constructed().size());
            verifyNoInteractions(attempt.server);
        }
    }

    @Test
    void pluginReplacementIsUsedForStorageFeedbackAndResult() throws Exception {
        try (Attempt attempt = new Attempt()) {
            attempt.pluginValue = "12";
            assertEquals(12, attempt.execute("random_tick_speed 9"));
            assertEquals(12, attempt.rules.get(GameRules.RANDOM_TICK_SPEED));
            attempt.success("commands.gamerule.set", "random_tick_speed", "12", true, 12);
            assertEquals(1, attempt.events.constructed().size());
            verify(attempt.server).onGameRuleChanged(attempt.level, GameRules.RANDOM_TICK_SPEED, 12);
        }
    }

    @Test
    void pluginKeepingTheOldValueReportsNoChange() {
        try (Attempt attempt = new Attempt()) {
            attempt.pluginValue = "3";
            attempt.failure("commands.gamerule.not_set", "random_tick_speed 9");
            assertEquals(3, attempt.rules.get(GameRules.RANDOM_TICK_SPEED));
        }
    }

    @Test
    void pluginsCanStillReplaceAnInitiallyUnchangedRequest() throws Exception {
        try (Attempt attempt = new Attempt()) {
            attempt.pluginValue = "12";
            assertEquals(12, attempt.execute("random_tick_speed 3"));
            assertEquals(12, attempt.rules.get(GameRules.RANDOM_TICK_SPEED));
            attempt.success("commands.gamerule.set", "random_tick_speed", "12", true, 12);
        }
    }

    @Test
    void queryDoesNotTriggerAnEventOrBroadcast() throws Exception {
        try (Attempt attempt = new Attempt()) {
            assertEquals(3, attempt.execute("random_tick_speed"));
            attempt.success("commands.gamerule.query", "random_tick_speed", "3", false, 3);
            assertTrue(attempt.events.constructed().isEmpty());
            verifyNoInteractions(attempt.server);
        }
    }

    @Test
    void invalidValueDoesNotReachTheEventOrStorage() {
        try (Attempt attempt = new Attempt()) {
            assertThrows(CommandSyntaxException.class, () -> attempt.execute("random_tick_speed -1"));
            assertEquals(3, attempt.rules.get(GameRules.RANDOM_TICK_SPEED));
            assertTrue(attempt.events.constructed().isEmpty());
            assertTrue(attempt.messages.isEmpty());
            verifyNoInteractions(attempt.server);
        }
    }
}
