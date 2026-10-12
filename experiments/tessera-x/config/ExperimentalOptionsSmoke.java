package dev.tessera.lab;

import com.google.gson.GsonBuilder;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/** Verifies effective native static flags, not just parsed YAML, in actual dedicated-server startup. */
public final class ExperimentalOptionsSmoke extends JavaPlugin {
    private int assertions;
    private void require(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
    private Object field(String type,String name) throws Exception {
        Field field=Class.forName(type).getDeclaredField(name);field.setAccessible(true);return field.get(null);
    }
    @Override public void onEnable(){
        try{
            String mode=System.getProperty("lab.config-mode");
            Map<String,Object> actual=new LinkedHashMap<>();
            actual.put("sensor-sorting",field("dev.tessera.x.SensorWork","ENABLED"));
            actual.put("sensor-adaptive",field("dev.tessera.x.SensorWork","ADAPTIVE"));
            actual.put("sensor-reuse",field("dev.tessera.x.SensorWork","REUSE"));
            actual.put("verify-sensors",field("dev.tessera.x.SensorWork","VERIFY"));
            actual.put("sensor-workers",field("dev.tessera.x.SensorWork","WORKERS"));
            actual.put("brain-scratch",field("net.minecraft.world.entity.ai.Brain","TESSERA_X_REUSE_RUNNING"));
            actual.put("sensor-local-conditions",field("net.minecraft.world.entity.ai.sensing.Sensor","TESSERA_X_LOCAL_CONDITIONS"));
            actual.put("poi-scratch",field("ca.spottedleaf.moonrise.patches.poi_lookup.PoiAccess","TESSERA_X_SCRATCH"));
            actual.put("poi-reservation-check",field("net.minecraft.world.entity.ai.behavior.AcquirePoi","TESSERA_X_RESERVATION_CHECK"));
            actual.put("region-covered-search",field("dev.tessera.x.RegionConnectivity","ENABLED"));
            actual.put("region-recalc-delay-nanos",field("dev.tessera.x.RegionRecalculation","MAX_DELAY_NANOS"));
            actual.put("work-timings",field("dev.tessera.x.WorkTimings","ENABLED"));
            Set<String> recommended=Set.of("sensor-local-conditions","poi-scratch","poi-reservation-check","region-covered-search");
            boolean all=mode.equals("all-on");
            for(var entry:actual.entrySet()){
                if(entry.getValue() instanceof Boolean flag)require(flag==(all || mode.equals("recommended") && recommended.contains(entry.getKey())),"Wrong effective flag "+entry);
            }
            int expectedWorkers=mode.equals("master-off")?0:mode.equals("default-generated")?4:2;
            require(((Number)actual.get("sensor-workers")).intValue()==expectedWorkers,"Worker setting ignored");
            long expectedDelay=all || mode.equals("recommended") ? 1_000_000_000L:0L;
            require(((Number)actual.get("region-recalc-delay-nanos")).longValue()==expectedDelay,"Region deadline setting ignored");
            require(Files.isRegularFile(Path.of(System.getProperty("lab.config-file"))),"Config not in selected paper directory");
            long workerThreads=Thread.getAllStackTraces().keySet().stream().filter(thread -> thread.getName().startsWith("Tessera-X Sensor Compute ")).count();
            require(workerThreads==(all?2:0),"Disabled feature started a worker pool");
            actual.put("actual-worker-threads",workerThreads);actual.put("status","PASS");actual.put("mode",mode);actual.put("assertions",assertions);
            Files.writeString(Path.of("config-smoke-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(actual));
        }catch(Throwable error){
            try{Files.writeString(Path.of("config-smoke-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("status","FAIL","failure",error.toString())));}catch(Exception ignored){}
            getLogger().log(java.util.logging.Level.SEVERE,"Config smoke failed",error);
        }
        Bukkit.getGlobalRegionScheduler().runDelayed(this,task -> Bukkit.shutdown(),10);
    }
}
