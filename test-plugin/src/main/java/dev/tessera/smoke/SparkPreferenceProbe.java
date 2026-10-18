package dev.tessera.smoke;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/** Name/command collision fixture only; not the real external spark plugin. */
public final class SparkPreferenceProbe extends JavaPlugin {
    @Override public void onEnable() { this.getLogger().info("SPARK_EXTERNAL_PROBE_ENABLED"); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        this.getLogger().info("SPARK_EXTERNAL_PROBE_COMMAND");
        return true;
    }
}
