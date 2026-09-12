package dev.tessera.buildsupport;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;

/** Executes only against explicit, generated working directories. */
@UntrackedTask(because = "Validates generated Git state and protects editable workspaces on every invocation")
public abstract class LocalPaperTask extends DefaultTask {
    @Input public abstract Property<String> getOperation();
    @Input public abstract Property<String> getExpectedMinecraftVersion();
    @InputDirectory public abstract DirectoryProperty getSourceDirectory();
    @Internal public abstract DirectoryProperty getSnapshotDirectory();
    @Internal public abstract DirectoryProperty getWorkspaceDirectory();

    @TaskAction public void run() throws Exception {
        var source = getSourceDirectory().get().getAsFile().toPath();
        var snapshot = getSnapshotDirectory().get().getAsFile().toPath();
        java.nio.file.Files.createDirectories(snapshot.getParent());
        try (var channel = java.nio.channels.FileChannel.open(snapshot.getParent().resolve("sinopia-workspace.lock"),
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) throw new IllegalStateException("Another Sinopia operation is active; retry after it finishes");
            var properties = new java.util.Properties();
            try (var reader = java.nio.file.Files.newBufferedReader(source.resolve("gradle.properties"))) {
                properties.load(reader);
            }
            if (!getExpectedMinecraftVersion().get().equals(properties.getProperty("mcVersion"))) {
                throw new IllegalStateException("Tessera mcVersion and sinopia/gradle.properties do not match; changing paperRef alone no longer updates the local base");
            }
            switch (getOperation().get()) {
                case "prepare" -> getLogger().lifecycle("Sinopia snapshot: {}", LocalPaper.prepare(source, snapshot));
                case "workspace" -> LocalPaper.workspace(source, snapshot, getWorkspaceDirectory().get().getAsFile().toPath());
                case "capture" -> getLogger().lifecycle("Captured {} Sinopia patch/build-data files", LocalPaper.capture(source, getWorkspaceDirectory().get().getAsFile().toPath()));
                default -> throw new IllegalArgumentException("Unknown Sinopia operation");
            }
        }
    }
}
