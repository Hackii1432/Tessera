import io.papermc.paper.threadedregions.ThreadedRegionizer;
import io.papermc.paper.threadedregions.ThreadedRegionizer.*;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.world.level.ChunkPos;
import com.google.gson.GsonBuilder;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Runs the actual server regionizer, with controlled chunk ownership and real monotonic time. */
public final class RegionSplitComparison {
    static final long DELAY = Long.getLong("tessera-x.region-recalc-max-delay-ms", 0L);
    static final List<Map<String, Object>> RESULTS = new ArrayList<>();
    static int assertions;
    static final Field DEAD, AGE;
    static {
        try {
            DEAD = ThreadedRegion.class.getDeclaredField("deadSections"); DEAD.setAccessible(true);
            Field age;
            try { age = ThreadedRegion.class.getDeclaredField("firstDeadSectionNanos"); age.setAccessible(true); }
            catch (NoSuchFieldException absent) { age = null; }
            AGE = age;
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
    static int dead(ThreadedRegion<?, ?> region) throws Exception { return ((Set<?>)DEAD.get(region)).size(); }
    static long age(ThreadedRegion<?, ?> region) throws Exception { return AGE.getLong(region); }
    static final class Section implements ThreadedRegionSectionData {}
    static final class Data implements ThreadedRegionData<Data, Section> {
        final Callbacks callbacks;
        final ThreadedRegion<Data, Section> owner;
        Data(Callbacks callbacks, ThreadedRegion<Data, Section> owner) { this.callbacks = callbacks; this.owner = owner; }
        public void split(ThreadedRegionizer<Data, Section> regionizer,
                Long2ReferenceOpenHashMap<ThreadedRegion<Data, Section>> into,
                ReferenceOpenHashSet<ThreadedRegion<Data, Section>> regions) {
            callbacks.dataSplits++;
            require(regions.size() > 1, "Data split needs multiple children");
            for (var entry : into.long2ReferenceEntrySet()) {
                require(regions.contains(entry.getValue()), "Split map references a foreign child");
            }
        }
        public void mergeInto(ThreadedRegion<Data, Section> into) {
            callbacks.dataMerges++;
            try {
                if (DELAY != 0L && dead(owner) > 0) {
                    long now = System.nanoTime();
                    require(now - age(into) >= now - age(owner), "Merge rejuvenated source dead sections");
                    callbacks.ageTransfers++;
                }
            } catch (Exception error) { throw new RuntimeException(error); }
        }
    }
    static final class Callbacks implements RegionCallbacks<Data, Section> {
        int splits, dataSplits, merges, dataMerges, ageTransfers;
        public Section createNewSectionData(int x, int z, int shift) { return new Section(); }
        public Data createNewData(ThreadedRegion<Data, Section> region) { return new Data(this, region); }
        public void onRegionCreate(ThreadedRegion<Data, Section> region) {}
        public void onRegionDestroy(ThreadedRegion<Data, Section> region) {}
        public void onRegionActive(ThreadedRegion<Data, Section> region) {}
        public void onRegionInactive(ThreadedRegion<Data, Section> region) {}
        public void preMerge(ThreadedRegion<Data, Section> from, ThreadedRegion<Data, Section> into) { merges++; }
        public void preSplit(ThreadedRegion<Data, Section> from, List<ThreadedRegion<Data, Section>> into) { splits++; }
    }
    static final class Graph {
        final Callbacks callbacks = new Callbacks();
        // Production grid-exponent=1: 2x2 chunks per section, create radius=4, merge radius=1.
        final ThreadedRegionizer<Data, Section> engine = new ThreadedRegionizer<>(512, 1.0 / 6.0, 4, 1, 1, null, callbacks);
        final Set<ChunkPos> live = new HashSet<>();
        long releaseNanos, maxReleaseNanos; int releases;
        void add(int x, int z) { require(live.add(new ChunkPos(x, z)), "Duplicate input chunk"); engine.addChunk(x, z); }
        void remove(int x, int z) { require(live.remove(new ChunkPos(x, z)), "Missing input chunk"); engine.removeChunk(x, z); }
        void line(int start, int end) { for (int x = start; x <= end; x++) add(x, 0); }
        void cut(int start, int end) { for (int x = start; x <= end; x++) remove(x, 0); }
        List<ThreadedRegion<Data, Section>> regions() {
            var result = new ArrayList<ThreadedRegion<Data, Section>>(); engine.computeForAllRegions(result::add); return result;
        }
        void releaseAll() {
            for (var region : regions()) if (region.tryMarkTicking(() -> false)) {
                long start = System.nanoTime(); region.markNotTicking(); long elapsed = System.nanoTime() - start;
                releaseNanos += elapsed; maxReleaseNanos = Math.max(maxReleaseNanos, elapsed); releases++;
            }
        }
        void verify() {
            Set<ChunkPos> owned = new HashSet<>();
            for (var region : regions()) for (long key : region.getOwnedChunks()) {
                ChunkPos pos = ChunkPos.unpack(key);
                require(owned.add(pos), "Chunk belongs to two regions: " + pos);
                require(engine.getRegionAtSynchronised(pos.x(), pos.z()) == region, "Ownership lookup differs");
            }
            require(owned.equals(live), "Lost or invented owned chunks");
            require(callbacks.splits == callbacks.dataSplits, "Missing data-split callback");
            require(callbacks.merges == callbacks.dataMerges, "Missing data-merge callback");
        }
        Map<String, Object> summary(String name) {
            var map = new LinkedHashMap<String, Object>();
            map.put("case", name); map.put("regions", regions().size()); map.put("liveChunks", live.size());
            map.put("splitCallbacks", callbacks.splits); map.put("releases", releases);
            map.put("releaseTotalMs", releaseNanos / 1e6); map.put("maxReleaseMs", maxReleaseNanos / 1e6);
            return map;
        }
    }
    static void waitReleases(Graph graph, long ms) throws Exception {
        long end = System.nanoTime() + ms * 1_000_000L;
        while (System.nanoTime() < end) { graph.releaseAll(); Thread.sleep(25); }
        graph.releaseAll();
    }
    static void cutComparison(String name, int length, int cutStart, int cutEnd, boolean belowMinimum) throws Exception {
        Graph graph = new Graph(); graph.line(0, length); graph.releaseAll();
        require(graph.regions().size() == 1, name + ": initial line must merge");
        var parent = graph.engine.getRegionAtSynchronised(0, 0);
        int sections = parent.getOwnedSections().size();
        long start = System.nanoTime(); graph.cut(cutStart, cutEnd);
        int dead = dead(parent);
        require(dead > 0, "Cut must produce dead sections");
        require(belowMinimum ? sections < 512 : sections >= 512 && (double)dead / sections < 1.0 / 6.0,
            name + ": must remain below original cleanup gate: " + sections + "/" + dead);
        graph.releaseAll(); require(graph.regions().size() == 1, "Split before cleanup deadline");
        Double splitMs = null;
        long limit = start + 3_000_000_000L;
        while (System.nanoTime() < limit) {
            graph.releaseAll();
            if (graph.regions().size() > 1) { splitMs = (System.nanoTime() - start) / 1e6; break; }
            Thread.sleep(25);
        }
        require(graph.regions().size() == (DELAY == 0L ? 1 : 2), "Unexpected final region count");
        graph.verify();
        var result = graph.summary(name); result.put("initialSections", sections); result.put("deadSectionsAfterCut", dead);
        result.put("deadRatio", (double)dead / sections); result.put("splitMsFromCut", splitMs);
        result.put("observationMs", (System.nanoTime() - start) / 1e6); RESULTS.add(result);
        if (DELAY != 0L) {
            graph.line(cutStart, cutEnd); graph.releaseAll();
            require(graph.regions().size() == 1, "Rejoined bridge must merge"); graph.verify();
        }
    }
    static void controls() throws Exception {
        Graph graph = new Graph(); graph.line(0, 64); graph.add(32, 8); graph.releaseAll();
        var parent = graph.engine.getRegionAtSynchronised(0, 0); long id = parent.id;
        graph.remove(32, 8); require(dead(parent) > 0, "Spur removal must produce dead sections");
        waitReleases(graph, 1200); graph.verify();
        require(graph.regions().size() == 1 && graph.regions().getFirst().id == id, "Connected bridge was split");
        if (DELAY != 0L) require(dead(parent) == 0, "Connected control did not clean dead sections");
        RESULTS.add(graph.summary("connected_bridge"));

        graph = new Graph(); graph.line(-64, 0); graph.releaseAll();
        parent = graph.engine.getRegionAtSynchronised(-64, 0);
        require(parent.tryMarkTicking(() -> false), "Failed to hold tick lease"); graph.cut(-56, -8);
        Thread.sleep(1200); require(graph.regions().size() == 1, "Split while tick lease is held");
        parent.markNotTicking(); graph.releaseAll(); graph.verify();
        require(graph.regions().size() == (DELAY == 0L ? 1 : 2), "Release failed to process overdue cleanup");
        RESULTS.add(graph.summary("negative_coordinates_tick_lease"));

        graph = new Graph(); graph.line(0, 64); graph.releaseAll();
        parent = graph.engine.getRegionAtSynchronised(0, 0); graph.cut(8, 56); Thread.sleep(650);
        graph.line(8, 56); require(dead(parent) == 0, "Restored bridge must revive all dead sections");
        Thread.sleep(500); graph.releaseAll(); require(graph.regions().size() == 1, "Revived bridge split");
        graph.cut(8, 56); waitReleases(graph, 550);
        require(graph.regions().size() == 1, "New dead batch inherited an expired deadline");
        waitReleases(graph, 650); graph.verify();
        require(graph.regions().size() == (DELAY == 0L ? 1 : 2), "Fresh batch cleanup failed");
        RESULTS.add(graph.summary("revival_resets_deadline"));

        graph = new Graph(); graph.add(0, 0); graph.releaseAll(); graph.remove(0, 0); graph.releaseAll(); graph.verify();
        require(graph.regions().isEmpty(), "Fully dead region must retire immediately"); RESULTS.add(graph.summary("all_dead_immediate"));

        graph = new Graph(); graph.line(0, 64); graph.releaseAll();
        require(!graph.regions().getFirst().tryMarkTicking(() -> true), "Abort ignored");
        waitReleases(graph, 1200); graph.verify();
        require(graph.regions().size() == 1, "No-change region split"); RESULTS.add(graph.summary("no_dead_sections"));

        graph = new Graph(); graph.line(0, 4); graph.line(64, 68); graph.add(2, 8); graph.add(66, 8); graph.releaseAll();
        require(graph.regions().size() == 2, "Merge fixture needs two separate regions");
        var left = graph.engine.getRegionAtSynchronised(0, 0); var right = graph.engine.getRegionAtSynchronised(64, 0);
        graph.remove(2, 8); Thread.sleep(200); graph.remove(66, 8);
        require(dead(left) > 0 && dead(right) > 0, "Both merge sources need a dead-section batch");
        require(left.tryMarkTicking(() -> false) && right.tryMarkTicking(() -> false), "Cannot hold both merge leases");
        graph.line(5, 63); require(graph.regions().size() > 1, "Merged a ticking source prematurely");
        left.markNotTicking(); right.markNotTicking(); graph.releaseAll(); graph.verify();
        require(graph.regions().size() == 1, "Pending merge did not settle");
        if (DELAY != 0L) require(graph.callbacks.ageTransfers > 0, "No aged dead sections transferred during merge");
        var mergeResult = graph.summary("pending_merge_preserves_age");
        mergeResult.put("ageTransfers", graph.callbacks.ageTransfers); RESULTS.add(mergeResult);
    }
    public static void main(String[] args) throws Exception {
        var result = new LinkedHashMap<String, Object>();
        result.put("scope", "Actual ThreadedRegionizer; controlled add/removeChunk, not real chunk tickets or TickRegionData");
        result.put("delayMs", DELAY); result.put("gridExponent", 1);
        try {
            cutComparison("below_minimum", 64, 8, 56, true);
            cutComparison("below_dead_ratio", 256, 112, 144, false); controls(); result.put("status", "PASS");
        } catch (Throwable error) { result.put("status", "FAIL"); result.put("failure", error.toString()); throw error;
        } finally {
            result.put("assertions", assertions); result.put("cases", RESULTS);
            try { result.put("diagnostics", Class.forName("dev.tessera.x.RegionRecalculation").getMethod("diagnostics").invoke(null)); }
            catch (ClassNotFoundException reference) { result.put("diagnostics", Map.of()); }
            Files.writeString(Path.of(args[0]), new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(result));
        }
        System.out.println("REGION_SPLIT_PASS assertions=" + assertions + " result=" + args[0]);
    }
}
