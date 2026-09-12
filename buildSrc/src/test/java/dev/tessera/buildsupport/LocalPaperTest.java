package dev.tessera.buildsupport;

import java.nio.file.*;

/** Dependency-free integration checks against real Git; fixtures never touch a user's worktree. */
public final class LocalPaperTest {
    private static int assertions;
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        ++assertions;
    }
    private static void rejected(Checked action, String description) throws Exception {
        try { action.run(); } catch (java.io.IOException expected) { ++assertions; return; }
        throw new AssertionError("Expected refusal: " + description);
    }
    @FunctionalInterface interface Checked { void run() throws Exception; }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("sinopia-build-test-").toAbsolutePath();
        Path source = Files.createDirectories(root.resolve("source"));
        Path patch = source.resolve("paper-server/patches/features/test.patch");
        Files.createDirectories(patch.getParent());
        Files.writeString(patch, "original\n");
        Files.writeString(source.resolve("gradle.properties"), "mcVersion=test\n");
        Path snapshot = root.resolve("snapshot");
        String first = LocalPaper.prepare(source, snapshot);
        check(first.equals(LocalPaper.prepare(source, snapshot)), "unchanged snapshot is stable");
        check(LocalPaper.git(snapshot, "status", "--porcelain").isEmpty(), "snapshot is clean");
        check(first.equals(LocalPaper.prepare(source, root.resolve("snapshot-2"))), "snapshot commit is reproducible");
        Path workspace = root.resolve("workspace");
        LocalPaper.workspace(source, snapshot, workspace);
        Path workingPatch = workspace.resolve(source.relativize(patch));
        Files.writeString(workingPatch, "edited\n");
        LocalPaper.workspace(source, snapshot, workspace);
        check(Files.readString(workingPatch).equals("edited\n"), "workspace preparation preserves edits");
        check(LocalPaper.capture(source, workspace) == 1, "capture includes modified patch");
        check(Files.readString(patch).equals("edited\n"), "capture updates source");
        LocalPaper.prepare(source, snapshot);
        LocalPaper.workspace(source, snapshot, workspace);
        check(Files.readString(workingPatch).equals("edited\n"), "captured workspace can be reused after snapshot refresh");
        String capturedHead = LocalPaper.git(workspace, "rev-parse", "HEAD");
        check(LocalPaper.capture(source, workspace) == 0, "unchanged capture is a no-op");
        check(capturedHead.equals(LocalPaper.git(workspace, "rev-parse", "HEAD")), "no-op capture preserves history");
        Files.writeString(patch, "concurrent\n");
        rejected(() -> LocalPaper.capture(source, workspace), "concurrent source edits");
        String second = LocalPaper.prepare(source, snapshot);
        check(!first.equals(second), "source edit produces new revision");
        rejected(() -> LocalPaper.workspace(source, snapshot, workspace), "stale workspace");
        Files.writeString(snapshot.resolve("gradle.properties"), "unexpected edit\n");
        rejected(() -> LocalPaper.prepare(source, snapshot), "dirty generated snapshot");
        Path unowned = Files.createDirectories(root.resolve("unowned"));
        Files.writeString(unowned.resolve("keep.txt"), "keep");
        rejected(() -> LocalPaper.prepare(source, unowned), "unowned output directory");
        check(Files.readString(unowned.resolve("keep.txt")).equals("keep"), "unowned file survives");
        Path fresh = root.resolve("fresh");
        LocalPaper.prepare(source, fresh);
        Files.delete(patch);
        LocalPaper.prepare(source, fresh);
        check(!Files.exists(fresh.resolve(source.relativize(patch))), "source deletions propagate");
        Path captureWorkspace = root.resolve("capture-workspace");
        LocalPaper.workspace(source, fresh, captureWorkspace);
        Files.createDirectories(captureWorkspace.resolve("build-data"));
        Files.writeString(captureWorkspace.resolve("build-data/new.at"), "test\n");
        check(LocalPaper.capture(source, captureWorkspace) == 1, "new build-data is captured");
        Files.delete(captureWorkspace.resolve("build-data/new.at"));
        check(LocalPaper.capture(source, captureWorkspace) == 1, "captured additions can subsequently be deleted");
        check(!Files.exists(source.resolve("build-data/new.at")), "capture propagates deletions");
        Files.writeString(captureWorkspace.resolve("gradle.properties"), "wrong layer\n");
        rejected(() -> LocalPaper.capture(source, captureWorkspace), "wrong-layer changes");
        Path nestedGit = Files.createDirectories(source.resolve("nested")).resolve(".git");
        Files.writeString(nestedGit, "gitdir: elsewhere\n");
        rejected(() -> LocalPaper.prepare(source, root.resolve("nested-git")), "nested Git worktree import");
        Files.delete(nestedGit);
        Path forbidden = Files.createDirectories(source.resolve("paper-server/src/minecraft/java"));
        Files.writeString(forbidden.resolve("Vanilla.java"), "not allowed");
        rejected(() -> LocalPaper.prepare(source, root.resolve("forbidden")), "generated Minecraft source import");
        System.out.println("Sinopia build support: " + assertions + " checks passed; fixtures: " + root);
    }
}
