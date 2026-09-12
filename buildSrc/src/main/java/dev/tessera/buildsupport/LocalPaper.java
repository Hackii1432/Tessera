package dev.tessera.buildsupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;

/** Disposable Git adapters for the versioned Paper tree. Never operates on Tessera's Git index. */
public final class LocalPaper {
    private static final String MARKER = ".tessera-local-paper";
    private static final String WORKSPACE = ".tessera-paper-workspace";
    private LocalPaper() {}

    static List<String> files(Path root) throws Exception {
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root)) {
            throw new IOException("Not a real directory: " + root);
        }
        List<String> result = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.toList()) {
                if (Files.isSymbolicLink(path)) throw new IOException("Symbolic link not allowed: " + path);
                if (Files.isRegularFile(path)) result.add(root.relativize(path).toString().replace('\\', '/'));
            }
        }
        Collections.sort(result);
        return result;
    }

    private static Path resolve(Path root, String relative) throws IOException {
        Path path = root.resolve(relative).normalize();
        if (relative.isEmpty() || !path.startsWith(root) || path.equals(root)) {
            throw new IOException("Path escapes owned directory: " + relative);
        }
        for (Path part = path; part != null && part.startsWith(root); part = part.getParent()) {
            if (Files.isSymbolicLink(part)) throw new IOException("Symbolic link not allowed: " + part);
        }
        return path;
    }

    public static String fingerprint(Path source) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (String name : files(source)) {
            if (name.equals(".git") || name.endsWith("/.git") || name.startsWith(".git/") || name.contains("/.git/")
                || name.startsWith("src/minecraft/") || name.contains("/src/minecraft/")
                || name.startsWith(".gradle/") || name.contains("/.gradle/")) {
                throw new IOException("Generated/Git files must not be stored in sinopia: " + name);
            }
            byte[] content = Files.readAllBytes(resolve(source, name));
            digest.update(name.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(Long.toString(content.length).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(content);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static String git(Path dir, String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of("git", "-c", "safe.directory=" + dir.toString().replace('\\', '/'),
            "-c", "core.autocrlf=false", "-c", "core.longpaths=true", "-c", "commit.gpgsign=false"));
        command.addAll(List.of(args));
        ProcessBuilder builder = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true);
        for (String variable : List.of("GIT_DIR", "GIT_WORK_TREE", "GIT_INDEX_FILE", "GIT_OBJECT_DIRECTORY",
                "GIT_ALTERNATE_OBJECT_DIRECTORIES", "GIT_COMMON_DIR")) builder.environment().remove(variable);
        builder.environment().put("GIT_AUTHOR_NAME", "Tessera local build");
        builder.environment().put("GIT_AUTHOR_EMAIL", "build@tessera.invalid");
        builder.environment().put("GIT_COMMITTER_NAME", "Tessera local build");
        builder.environment().put("GIT_COMMITTER_EMAIL", "build@tessera.invalid");
        builder.environment().put("GIT_AUTHOR_DATE", "2000-01-01T00:00:00Z");
        builder.environment().put("GIT_COMMITTER_DATE", "2000-01-01T00:00:00Z");
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) throw new IOException("git " + String.join(" ", args) + " failed in " + dir + ":\n" + output);
        return output.trim();
    }

    private static List<String> tracked(Path repo) throws Exception {
        String names = git(repo, "ls-files", "-z");
        return names.isEmpty() ? List.of() : Arrays.asList(names.split("\u0000"));
    }

    private static void copy(Path source, Path target, String name) throws Exception {
        Path output = resolve(target, name);
        Files.createDirectories(output.getParent());
        Files.copy(resolve(source, name), output, StandardCopyOption.REPLACE_EXISTING);
    }

    public static String prepare(Path source, Path output) throws Exception {
        source = source.toAbsolutePath().normalize();
        output = output.toAbsolutePath().normalize();
        if (source.startsWith(output) || output.startsWith(source)) throw new IOException("Source and snapshot overlap");
        String fingerprint = fingerprint(source);
        if (Files.exists(output)) {
            if (!Files.isRegularFile(resolve(output, MARKER)) || !Files.isDirectory(resolve(output, ".git"))) {
                throw new IOException("Refusing to replace an unowned directory: " + output);
            }
            if (!git(output, "status", "--porcelain", "--untracked-files=all").isEmpty()) {
                throw new IOException("Generated snapshot has edits; preserve them before rebuilding: " + output);
            }
            if (Files.readString(output.resolve(MARKER)).equals(fingerprint)) return git(output, "rev-parse", "HEAD");
        } else {
            Files.createDirectories(output);
            git(output, "init", "--quiet", "--initial-branch=tessera-local");
        }
        List<String> names = files(source);
        Set<String> wanted = new HashSet<>(names);
        // Only remove files tracked by our previous generated snapshot, never an arbitrary directory tree.
        for (String previous : tracked(output)) {
            if (!previous.equals(MARKER) && !wanted.contains(previous)) Files.deleteIfExists(resolve(output, previous));
        }
        for (String name : names) copy(source, output, name);
        if (wanted.contains("gradlew")) output.resolve("gradlew").toFile().setExecutable(true, false);
        Files.writeString(output.resolve(MARKER), fingerprint);
        git(output, "add", "--force", "--all");
        if (wanted.contains("gradlew")) git(output, "update-index", "--chmod=+x", "gradlew");
        String tree = git(output, "write-tree");
        String revision = git(output, "commit-tree", tree, "-m", "Tessera local Paper snapshot");
        git(output, "update-ref", "refs/heads/tessera-local", revision);
        return revision;
    }

    public static void workspace(Path source, Path snapshot, Path output) throws Exception {
        source = source.toAbsolutePath().normalize();
        snapshot = snapshot.toAbsolutePath().normalize();
        output = output.toAbsolutePath().normalize();
        String revision = git(snapshot, "rev-parse", "HEAD");
        String stamp = fingerprint(source) + "\n" + revision + "\n";
        if (Files.exists(output)) {
            Path marker = resolve(output, WORKSPACE);
            List<String> previous = Files.isRegularFile(marker) ? Files.readAllLines(marker) : List.of();
            // Captures create their own checkpoint commits, not snapshot commits. Compare source content.
            if (previous.size() != 2 || !previous.get(0).equals(fingerprint(source))) {
                throw new IOException("Paper workspace belongs to another base. Preserve/export edits and move it aside before preparing again: " + output);
            }
            git(output, "cat-file", "-e", previous.get(1) + "^{commit}");
            return; // Never reset an editable workspace, including unfinished patch application.
        }
        Files.createDirectories(output);
        git(output, "clone", "--quiet", "--no-hardlinks", snapshot.toString(), ".");
        Files.writeString(output.resolve(WORKSPACE), stamp);
        Files.writeString(output.resolve(".git/info/exclude"), "\n" + WORKSPACE + "\n", StandardOpenOption.APPEND);
    }

    public static int capture(Path source, Path workspace) throws Exception {
        source = source.toAbsolutePath().normalize();
        workspace = workspace.toAbsolutePath().normalize();
        Path marker = resolve(workspace, WORKSPACE);
        if (!Files.isRegularFile(marker)) throw new IOException("Not a Tessera Paper workspace: " + workspace);
        List<String> stamp = Files.readAllLines(marker);
        if (stamp.size() != 2 || !fingerprint(source).equals(stamp.get(0))) {
            throw new IOException("Local Paper base changed since workspace creation; refusing to overwrite it");
        }
        if (!git(workspace, "diff", "--name-only", "--diff-filter=U").isEmpty()) throw new IOException("Unresolved Paper workspace conflicts");
        Path minecraft = workspace.resolve("paper-server/src/minecraft/java");
        if (Files.exists(minecraft.resolve(".git"))) {
            if (!git(minecraft, "status", "--porcelain").isEmpty()
                || Files.exists(minecraft.resolve(".git/rebase-apply")) || Files.exists(minecraft.resolve(".git/rebase-merge"))) {
                throw new IOException("Minecraft workspace has uncommitted changes/conflicts; rebuild its patches first");
            }
        }
        Set<String> changed = new TreeSet<>();
        String diff = git(workspace, "diff", "--name-only", "--no-renames", "-z", stamp.get(1));
        String added = git(workspace, "ls-files", "--others", "--exclude-standard", "-z");
        if (!diff.isEmpty()) changed.addAll(Arrays.asList(diff.split("\u0000")));
        if (!added.isEmpty()) changed.addAll(Arrays.asList(added.split("\u0000")));
        if (changed.isEmpty()) return 0;
        for (String name : changed) {
            if (!(name.startsWith("paper-server/patches/") || name.startsWith("build-data/"))) {
                throw new IOException("Capture only exports Minecraft patches/build-data; edit regular Sinopia files in sinopia: " + name);
            }
            resolve(source, name);
            resolve(workspace, name);
        }
        // Validate every target before writing anything. No generated Minecraft source is ever exported.
        for (String name : changed) {
            if (Files.exists(workspace.resolve(name))) copy(workspace, source, name);
            else Files.deleteIfExists(resolve(source, name));
        }
        // Advance the editable workspace baseline so subsequent additions AND deletions round-trip.
        git(workspace, "add", "--all");
        String tree = git(workspace, "write-tree");
        String checkpoint = git(workspace, "commit-tree", tree, "-p", git(workspace, "rev-parse", "HEAD"),
            "-m", "Capture Sinopia patches into Tessera");
        git(workspace, "update-ref", "HEAD", checkpoint);
        Files.writeString(marker, fingerprint(source) + "\n" + checkpoint + "\n");
        return changed.size();
    }
}
