package dev.tessera.lab;

import ca.spottedleaf.common.time.TickData;
import com.google.gson.GsonBuilder;
import io.papermc.paper.threadedregions.TickRegionScheduler;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

/** Identical plugin binary for the preserved 019 reference and experimental Tessera-X. */
public final class SensorComparison extends JavaPlugin {
    private final ThreadMXBean cpu = ManagementFactory.getThreadMXBean();
    private final List<LivingEntity> bodies = new ArrayList<>();
    private final List<LivingEntity> entities = new ArrayList<>();
    private final List<Object> stages = new ArrayList<>();
    private final List<Double> wallSamples = new ArrayList<>();
    private final List<Double> cpuSamples = new ArrayList<>();
    private final List<Double> allocatedSamples = new ArrayList<>();
    private World world;
    private ServerLevel level;
    private Class<?> work;
    private Field sensors;
    private Field sensorCountdown;
    private int stage;
    private int frame;
    private Map<String, Long> before;
    private long gcBefore;
    private long gcTimeBefore;
    private long started;
    private final int warmup = Integer.getInteger("lab.warmup-ticks", 120);
    private final int measured = Integer.getInteger("lab.measured-ticks", 200);
    private final String[] names = {"sensor-dense", "sensor-moving", "mixed-native-ai"};

    @Override
    public void onEnable() {
        try {
            sensors = net.minecraft.world.entity.ai.Brain.class.getDeclaredField("sensors");
            sensors.setAccessible(true);
            sensorCountdown = Sensor.class.getDeclaredField("timeToTick");
            sensorCountdown.setAccessible(true);
            try { work = Class.forName("dev.tessera.x.SensorWork"); } catch (ClassNotFoundException reference) { work = null; }
            if (cpu.isThreadCpuTimeSupported()) cpu.setThreadCpuTimeEnabled(true);
            world = Bukkit.getWorlds().getFirst();
            level = ((CraftWorld)world).getHandle();
            Bukkit.getGlobalRegionScheduler().execute(this, () -> {
                world.setTime(18000);
                world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
                world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
            });
            for (int x = -2; x <= 1; x++) for (int z = -2; z <= 1; z++) world.addPluginChunkTicket(x, z, this);
            if (work != null) checkStableSort();
            Bukkit.getRegionScheduler().runDelayed(this, world, 0, 0, task -> setup(), 100);
        } catch (Throwable failure) { finish(failure); }
    }

    private Sensor<?> sensor(LivingEntity body) throws Exception {
        return (Sensor<?>)((Map<?, ?>)sensors.get(body.getBrain())).get(SensorType.NEAREST_LIVING_ENTITIES);
    }

    private void setup() {
        try {
            for (LivingEntity entity : entities) entity.getBukkitEntity().remove();
            bodies.clear(); entities.clear();
            if (stage == 0) {
                for (int x = -16; x <= 16; x++) for (int z = -16; z <= 16; z++)
                    world.getBlockAt(x, 79, z).setType(org.bukkit.Material.STONE, false);
            }
            Random random = new Random(482913);
            int count = stage == 2 ? 384 : 1024;
            for (int i = 0; i < count; i++) {
                EntityType type = stage == 2
                    ? switch (i % 4) { case 0 -> EntityType.VILLAGER; case 1 -> EntityType.ZOMBIE; case 2 -> EntityType.CHICKEN; default -> EntityType.SHEEP; }
                    : i < 128 ? EntityType.VILLAGER : EntityType.COW;
                double x = random.nextDouble() * 28 - 14;
                double z = random.nextDouble() * 28 - 14;
                org.bukkit.entity.LivingEntity bukkit = (org.bukkit.entity.LivingEntity)world.spawnEntity(
                    new org.bukkit.Location(world, x, 80, z), type);
                LivingEntity entity = ((CraftLivingEntity)bukkit).getHandle();
                entity.getRandom().setSeed(9000L + i);
                for (Object value : ((Map<?, ?>)sensors.get(entity.getBrain())).values())
                    sensorCountdown.setLong(value, i % 20);
                entity.setNoGravity(true);
                bukkit.setInvulnerable(true);
                entity.activatedTick = Long.MAX_VALUE;
                if (entity instanceof Mob mob) mob.setNoAi(stage != 2);
                entity.noPhysics = stage != 2;
                entities.add(entity);
                if (type == EntityType.VILLAGER) {
                    if (stage != 2) entity.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(64);
                    bodies.add(entity);
                }
            }
            wallSamples.clear(); cpuSamples.clear(); allocatedSamples.clear(); frame = 0;
            before = metrics(); gcBefore = gcCount(); gcTimeBefore = gcTime(); started = System.nanoTime();
            getLogger().info("LAB_STAGE " + names[stage] + " entities=" + entities.size() + " sensors=" + bodies.size());
            Bukkit.getRegionScheduler().runAtFixedRate(this, world, 0, 0, task -> {
                try {
                    if (frame == warmup) {
                        before = metrics(); gcBefore = gcCount(); gcTimeBefore = gcTime(); started = System.nanoTime();
                    }
                    if (frame >= warmup + measured) {
                        task.cancel(); collect(); stage++;
                        if (stage == names.length) finish(null); else setup();
                        return;
                    }
                    if (stage == 2) {
                        // Actual entity/brain/goal/physics work is done by the native ServerLevel entity phase.
                        for (LivingEntity entity : entities) entity.activatedTick = Long.MAX_VALUE;
                    } else {
                        long wall = System.nanoTime(); long cpuStart = cpu.getCurrentThreadCpuTime(); long allocated = allocated();
                        runSensorPhase(stage == 1, frame < 10);
                        if (frame >= warmup) {
                            wallSamples.add((System.nanoTime() - wall) / 1e6);
                            cpuSamples.add((cpu.getCurrentThreadCpuTime() - cpuStart) / 1e6);
                            allocatedSamples.add((allocated() - allocated) / 1024.0);
                        }
                    }
                    frame++;
                } catch (Throwable failure) { task.cancel(); finish(failure); }
            }, 1, 1);
        } catch (Throwable failure) { finish(failure); }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void runSensorPhase(boolean moving, boolean verify) throws Exception {
        for (LivingEntity body : bodies) sensorCountdown.setLong(sensor(body), 1L);
        if (work != null) work.getMethod("begin", ServerLevel.class,
            io.papermc.paper.threadedregions.RegionizedWorldData.class).invoke(null, level, level.getCurrentWorldData());
        try {
            if (moving) {
                // Deliberate invalidation after preparation: correctness must hold on changed data.
                LivingEntity target = entities.get(entities.size() - 1);
                target.setPos(target.getX() + (frame % 2 == 0 ? .125 : -.125), target.getY(), target.getZ());
            }
            for (LivingEntity body : bodies) {
                List<LivingEntity> oracle = null;
                if (verify) {
                    double range = body.getAttributeValue(Attributes.FOLLOW_RANGE);
                    oracle = level.getEntitiesOfClass(LivingEntity.class, body.getBoundingBox().inflate(range, range, range),
                        other -> other != body && other.isAlive());
                    oracle.sort(Comparator.comparingDouble(body::distanceToSqr));
                }
                ((Sensor)sensor(body)).tick(level, body);
                if (verify) {
                    List<LivingEntity> actual = body.getBrain().getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES).orElseThrow();
                    if (actual.size() != oracle.size()) throw new AssertionError("Sensor candidate count changed");
                    for (int i = 0; i < actual.size(); i++) if (actual.get(i) != oracle.get(i)) throw new AssertionError("Sensor order differs from native");
                }
            }
        } finally { if (work != null) work.getMethod("end").invoke(null); }
    }

    private void checkStableSort() throws Exception {
        Method sort = work.getMethod("stableOrder", double[].class);
        Random random = new Random(7258);
        for (int trial = 0; trial < 250; trial++) {
            double[] keys = new double[trial < 5 ? trial : random.nextInt(4097)];
            for (int i = 0; i < keys.length; i++) keys[i] = random.nextInt(31);
            if (keys.length > 6) { keys[0] = Double.NaN; keys[1] = -0.0; keys[2] = 0.0; keys[3] = Double.POSITIVE_INFINITY; }
            Integer[] expected = new Integer[keys.length]; for (int i = 0; i < keys.length; i++) expected[i] = i;
            Arrays.sort(expected, Comparator.comparingDouble(i -> keys[i]));
            int[] actual = (int[])sort.invoke(null, (Object)keys);
            for (int i = 0; i < actual.length; i++) if (actual[i] != expected[i]) throw new AssertionError("Primitive stable sort mismatch");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Long> metrics() throws Exception {
        Map<String, Long> result = new LinkedHashMap<>(work == null ? Map.of()
            : (Map<String, Long>)work.getMethod("metrics").invoke(null));
        long workerCpu = 0, workerAllocated = 0;
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.getName().startsWith("Tessera-X Sensor Compute ")) {
                workerCpu += Math.max(0, cpu.getThreadCpuTime(thread.threadId()));
                if (cpu instanceof com.sun.management.ThreadMXBean extended)
                    workerAllocated += Math.max(0, extended.getThreadAllocatedBytes(thread.threadId()));
            }
        }
        // Window totals include short tasks whose per-task CPU deltas fall below Windows timer resolution.
        result.put("workerCpuWindowNanos", workerCpu);
        result.put("workerAllocatedWindowBytes", workerAllocated);
        return result;
    }

    private void collect() throws Exception {
        TickData.TickReportData report = ((TickRegionScheduler.RegionScheduleHandle)TickRegionScheduler.getCurrentTickingTask())
            .getTickReport5s(System.nanoTime());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workload", names[stage]); result.put("entities", entities.size()); result.put("sensorBodies", bodies.size());
        result.put("warmupTicks", warmup); result.put("measuredTicks", measured);
        result.put("regionId", TickRegionScheduler.getCurrentRegion().id);
        result.put("kernelWallMs", stats(wallSamples)); result.put("kernelOwnerCpuMs", stats(cpuSamples));
        result.put("kernelOwnerAllocatedKiB", stats(allocatedSamples));
        result.put("fullRegionTickLast5sMs", stats(Arrays.stream(report.timePerTickData().rawData()).mapToDouble(n -> n / 1e6).boxed().toList()));
        result.put("fullRegionCollectedTicks", report.collectedTicks());
        result.put("fullRegionOwnerCpuAverageMs", (report.timePerTickData().segmentAll().average()
            - report.missingCPUTimeData().segmentAll().average()) / 1e6);
        result.put("gcCollections", gcCount() - gcBefore); result.put("gcTimeMs", gcTime() - gcTimeBefore);
        result.put("measuredWallSeconds", (System.nanoTime() - started) / 1e9);
        Map<String, Long> after = metrics(); Map<String, Long> delta = new LinkedHashMap<>();
        after.forEach((key, value) -> delta.put(key, key.equals("workers") || key.equals("maxActiveWorkers") ? value : value - before.getOrDefault(key, 0L)));
        result.put("computeMetrics", delta); stages.add(result);
        getLogger().info("LAB_STAGE_DONE " + names[stage] + " fullMeanMs=" + report.timePerTickData().segmentAll().average() / 1e6);
    }

    private long allocated() {
        return cpu instanceof com.sun.management.ThreadMXBean extended && extended.isThreadAllocatedMemorySupported()
            ? extended.getThreadAllocatedBytes(Thread.currentThread().threadId()) : 0;
    }
    private long gcCount() { return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(g -> Math.max(0, g.getCollectionCount())).sum(); }
    private long gcTime() { return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(g -> Math.max(0, g.getCollectionTime())).sum(); }
    private Map<String, Object> stats(List<Double> values) {
        if (values.isEmpty()) return Map.of();
        double[] sorted = values.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        return Map.of("count", sorted.length, "mean", Arrays.stream(sorted).average().orElseThrow(),
            "median", sorted[sorted.length / 2], "p95", sorted[(int)Math.ceil(sorted.length * .95) - 1],
            "p99", sorted[(int)Math.ceil(sorted.length * .99) - 1], "max", sorted[sorted.length - 1]);
    }

    private void finish(Throwable failure) {
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", failure == null ? "PASS" : "FAIL");
            result.put("failure", failure == null ? null : failure.toString());
            result.put("timestamp", Instant.now().toString()); result.put("serverVersion", Bukkit.getVersion());
            result.put("brand", io.papermc.paper.ServerBuildInfo.buildInfo().brandName());
            result.put("sensorFeaturePresent", work != null); result.put("stages", stages);
            Files.writeString(Path.of("comparison-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result));
            getLogger().info("LAB_RESULT " + result.get("status"));
            if (failure != null) getLogger().log(java.util.logging.Level.SEVERE, "Comparison failed", failure);
        } catch (Throwable writeFailure) { writeFailure.printStackTrace(); }
        Bukkit.getGlobalRegionScheduler().execute(this, Bukkit::shutdown);
    }
}
