package dev.tessera.smoke;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import net.minecrell.terminalconsole.TerminalConsoleAppender;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.status.StatusData;
import org.apache.logging.log4j.status.StatusListener;
import org.apache.logging.log4j.status.StatusLogger;
import org.bukkit.Bukkit;
import org.jline.terminal.Terminal;
import org.jline.terminal.impl.AbstractWindowsTerminal;

/** Isolated Windows-console fixture, never installed on a production server. */
final class ConsoleShutdownSmoke {
    static final int BURST = 512;
    private final RuntimeWorldLifecycleSmokePlugin plugin;
    private final Path status = Path.of("console-shutdown-status.txt");
    private final Path checks = Path.of("console-shutdown-checks.txt");

    ConsoleShutdownSmoke(RuntimeWorldLifecycleSmokePlugin plugin) {
        this.plugin = plugin;
    }

    void start() {
        try {
            Files.writeString(this.status, "Status listener installed before normal stop.\n");
            Files.writeString(this.checks, "burst=" + BURST + "\n");
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot initialise console shutdown fixture", failure);
        }
        // Keep the listener until process exit: the error under test happens AFTER plugin disable.
        StatusLogger.getLogger().registerListener(new StatusListener() {
            @Override
            public synchronized void log(StatusData event) {
                try {
                    Files.writeString(status, event.getFormattedStatus() + "\n", StandardOpenOption.APPEND);
                } catch (IOException failure) {
                    throw new IllegalStateException("Cannot record console shutdown status", failure);
                }
            }

            @Override
            public Level getStatusLevel() {
                return Level.WARN;
            }

            @Override
            public void close() {
                // No persistent file handle; each record is closed before returning.
            }
        });
        Bukkit.getGlobalRegionScheduler().runDelayed(this.plugin, task -> {
            Terminal terminal = TerminalConsoleAppender.getTerminal();
            String type = terminal == null ? "null" : terminal.getClass().getName();
            boolean windows = terminal instanceof AbstractWindowsTerminal<?>;
            boolean reader = TerminalConsoleAppender.getReader() != null;
            try {
                Files.writeString(this.checks, "terminal=" + type + "\nwindows=" + windows
                    + "\nreader=" + reader + "\n", StandardOpenOption.APPEND);
            } catch (IOException failure) {
                throw new IllegalStateException("Cannot record real console backend", failure);
            }
            this.plugin.getLogger().info("CONSOLE_SHUTDOWN_READY terminal=" + type
                + " windows=" + windows + " reader=" + reader);
        }, 40L);
    }

    void stopping() {
        try {
            Files.writeString(this.checks, "normal-disable=true\n", StandardOpenOption.APPEND);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot record plugin disable", failure);
        }
        for (int index = 1; index <= BURST; ++index) {
            this.plugin.getLogger().info("CONSOLE_SHUTDOWN_BURST " + index + "/" + BURST);
        }
    }
}
