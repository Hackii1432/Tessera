package dev.tessera.lab;

import ca.spottedleaf.moonrise.patches.poi_lookup.PoiAccess;
import com.google.gson.GsonBuilder;
import com.mojang.datafixers.util.Pair;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.AcquirePoi;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.*;
import net.minecraft.world.entity.PathfinderMob;
import org.bukkit.*;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

/** Actual native POI storage, query callbacks, reservations and generic AcquirePoi behavior. */
public final class PoiSafety extends JavaPlugin {
    private World world; private ServerLevel level; private PoiManager manager;
    private Holder<PoiType> home; private int assertions; private final Map<String,Object> out=new LinkedHashMap<>();
    private final List<Object> trace=new ArrayList<>(); private final AtomicBoolean finished=new AtomicBoolean();
    private void require(boolean value,String reason){assertions++;if(!value)throw new AssertionError(reason);}
    @Override public void onEnable(){Bukkit.getGlobalRegionScheduler().runDelayed(this,task -> start(),100);}
    private void start(){
        try {
            world=Bukkit.getWorlds().getFirst();level=((CraftWorld)world).getHandle();manager=level.getPoiManager();
            world.setGameRule(GameRule.DO_MOB_SPAWNING,false);
            home=level.registryAccess().lookupOrThrow(Registries.POINT_OF_INTEREST_TYPE).getOrThrow(PoiTypes.HOME);
            List<CompletableFuture<?>> loads=new ArrayList<>();
            for(int x=-4;x<=3;x++)for(int z=-4;z<=3;z++){world.addPluginChunkTicket(x,z,this);loads.add(world.getChunkAtAsync(x,z,true));}
            CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).whenComplete((ignored,error) -> {
                if(error!=null){finish(error);return;}
                Bukkit.getRegionScheduler().runDelayed(this,world,0,0,task -> checks(),40);
            });
        }catch(Throwable error){finish(error);}
    }
    private List<Pair<Holder<PoiType>,BlockPos>> query(BlockPos center,int range,double distance,PoiManager.Occupancy occupancy,int max,Predicate<BlockPos> filter){
        List<Pair<Holder<PoiType>,BlockPos>> found=new ArrayList<>();
        PoiAccess.findNearestPoiPositions(manager,t -> t.is(PoiTypes.HOME),filter,center,range,distance,occupancy,true,max,found);
        return found;
    }
    private List<Long> keys(List<Pair<Holder<PoiType>,BlockPos>> records){return records.stream().map(pair -> pair.getSecond().asLong()).toList();}
    private long allocated(){var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();return bean.getThreadAllocatedBytes(Thread.currentThread().threadId());}
    private void checks(){
        try {
            // Empty-query kernel warms native POI loading first; no query result is cached.
            BlockPos center=new BlockPos(0,1,0);
            for(int i=0;i<200;i++)require(query(center,48,Double.MAX_VALUE,PoiManager.Occupancy.HAS_SPACE,5,p -> true).isEmpty(),"Flat empty query found a POI");
            long bytes=allocated(),start=System.nanoTime();
            for(int i=0;i<500;i++)query(center,48,Double.MAX_VALUE,PoiManager.Occupancy.HAS_SPACE,5,p -> true);
            out.put("emptyQueryMeanMs",(System.nanoTime()-start)/1e6/500);out.put("emptyQueryAllocatedBytes",(allocated()-bytes)/500.0);
            Random random=new Random(82173);List<BlockPos> placed=new ArrayList<>();
            for(int i=0;i<60;i++){
                BlockPos pos=new BlockPos(random.nextInt(49)-24,1+i%2*16,random.nextInt(49)-24);
                if(placed.contains(pos)){i--;continue;}placed.add(pos);require(manager.add(pos,home)!=null,"POI add failed");
                if(i%5==0)require(manager.take(t -> t.is(PoiTypes.HOME),(t,p) -> p.equals(pos),pos,1).isPresent(),"Initial reservation failed");
            }
            for(int i=0;i<200;i++){
                BlockPos at=new BlockPos(random.nextInt(33)-16,1,random.nextInt(33)-16);int range=4+random.nextInt(45),max=1+random.nextInt(10);
                PoiManager.Occupancy occupancy=PoiManager.Occupancy.values()[i%PoiManager.Occupancy.values().length];
                double distance=i%2==0?Double.MAX_VALUE:400;List<Long> calls=new ArrayList<>();
                List<Long> result=keys(query(at,range,distance,occupancy,max,p -> {calls.add(p.asLong());return (p.asLong()&3)!=0;}));
                require(result.size()<=max,"Query exceeded max");trace.add(List.of(at.asLong(),range,distance,occupancy.name(),max,result,calls));
            }
            List<Long> expected=keys(query(center,48,Double.MAX_VALUE,PoiManager.Occupancy.HAS_SPACE,5,p -> true));
            AtomicBoolean recurse=new AtomicBoolean();List<Long> nested=keys(query(center,48,Double.MAX_VALUE,PoiManager.Occupancy.HAS_SPACE,5,p -> {
                if(recurse.compareAndSet(false,true))query(new BlockPos(10,1,10),8,Double.MAX_VALUE,PoiManager.Occupancy.ANY,3,unused -> true);
                return true;
            }));require(recurse.get() && nested.equals(expected),"Reentrant query corrupted outer results");
            boolean caught=false;try{query(center,48,Double.MAX_VALUE,PoiManager.Occupancy.ANY,5,p -> {throw new IllegalStateException("expected predicate");});}catch(IllegalStateException error){caught=true;}
            require(caught,"Predicate exception swallowed");require(keys(query(center,48,Double.MAX_VALUE,PoiManager.Occupancy.HAS_SPACE,5,p -> true)).equals(expected),"Dirty scratch after exception");
            for(BlockPos pos:placed)manager.remove(pos);require(query(center,48,Double.MAX_VALUE,PoiManager.Occupancy.ANY,5,p -> true).isEmpty(),"Removed POIs remained visible");
            // Broad read-only search exercises capacity eviction; no world or record reference is retained.
            PoiAccess.findNearestPoiRecords(manager,t -> true,p -> true,center,144,Double.MAX_VALUE,PoiManager.Occupancy.ANY,false,5,new ArrayList<>());
            if(Boolean.getBoolean("tessera-x.poi-scratch")){
                var pool=PoiAccess.class.getDeclaredField("TESSERA_X_SEARCH");pool.setAccessible(true);
                require(((ThreadLocal<?>)pool.get(null)).get()==null,"Oversized POI scratch retained");
            }
            out.put("queryTrace",trace);
            reservationCase();
        }catch(Throwable error){finish(error);}
    }
    private void reservationCase(){
        for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)world.getBlockAt(x,0,z).setType(Material.STONE,false);
        var foot=(org.bukkit.block.data.type.Bed)Bukkit.createBlockData(Material.RED_BED);
        foot.setFacing(org.bukkit.block.BlockFace.EAST);foot.setPart(org.bukkit.block.data.type.Bed.Part.FOOT);
        world.getBlockAt(4,1,0).setBlockData(foot,false);
        var head=(org.bukkit.block.data.type.Bed)foot.clone();head.setPart(org.bukkit.block.data.type.Bed.Part.HEAD);
        world.getBlockAt(5,1,0).setBlockData(head,false);
        BlockPos bed=new BlockPos(5,1,0);manager.add(bed,home);
        require(manager.getType(bed).filter(t -> t.is(PoiTypes.HOME)).isPresent(),"Single test bed POI missing");
        var entity=world.spawnEntity(new Location(world,.5,1,.5),EntityType.VILLAGER);
        PathfinderMob body=(PathfinderMob)((CraftLivingEntity)entity).getHandle();body.setNoAi(true);body.setNoGravity(true);body.setOnGround(true);
        AtomicBoolean reserved=new AtomicBoolean();
        BehaviorControl<PathfinderMob> acquire=AcquirePoi.create(t -> t.is(PoiTypes.HOME),MemoryModuleType.HOME,false,Optional.empty(),(owner,pos) -> {
            if(reserved.compareAndSet(false,true))require(manager.take(t -> t.is(PoiTypes.HOME),(t,p) -> p.equals(pos),pos,1).isPresent(),"Callback reservation failed");
            return true;
        });
        AtomicInteger calls=new AtomicInteger();
        Bukkit.getRegionScheduler().runAtFixedRate(this,world,0,0,task -> {
            try {
                acquire.tryStart(level,body,level.getGameTime());
                if(calls.incrementAndGet()<60)return;task.cancel();require(reserved.get(),"Acquisition never reached validation callback");
                boolean assigned=body.getBrain().getMemory(MemoryModuleType.HOME).isPresent();
                out.put("homeSavedDespiteFailedReservation",assigned);
                require(assigned!=Boolean.getBoolean("tessera-x.poi-reservation-check"),"Unexpected failed-reservation memory result: "+assigned);
                successfulReservation(body,bed);
            }catch(Throwable error){task.cancel();finish(error);}
        },1,1);
    }
    private void successfulReservation(PathfinderMob body,BlockPos bed){
        require(manager.release(bed),"Could not release callback reservation");body.getBrain().eraseMemory(MemoryModuleType.HOME);
        BehaviorControl<PathfinderMob> acquire=AcquirePoi.create(t -> t.is(PoiTypes.HOME),MemoryModuleType.HOME,false,Optional.empty());
        AtomicInteger calls=new AtomicInteger();
        Bukkit.getRegionScheduler().runAtFixedRate(this,world,0,0,task -> {
            try {
                acquire.tryStart(level,body,level.getGameTime());if(calls.incrementAndGet()<60)return;task.cancel();
                require(body.getBrain().getMemory(MemoryModuleType.HOME).orElseThrow().pos().equals(bed),"Successful reservation did not assign HOME");
                require(manager.getCountInRange(t -> t.is(PoiTypes.HOME),bed,1,PoiManager.Occupancy.HAS_SPACE)==0,"Assigned bed still offered as free");
                require(manager.release(bed),"Successful reservation not releasable");
                require(manager.getCountInRange(t -> t.is(PoiTypes.HOME),bed,1,PoiManager.Occupancy.HAS_SPACE)==1,"Released bed not offered again");
                out.put("successfulReservationAssignedAndReleased",true);finish(null);
            }catch(Throwable error){task.cancel();finish(error);}
        },1,1);
    }
    private void finish(Throwable error){
        if(!finished.compareAndSet(false,true))return;
        try {
            out.put("status",error==null?"PASS":"FAIL");out.put("failure",error==null?null:error.toString());out.put("assertions",assertions);
            out.put("scratch",Boolean.getBoolean("tessera-x.poi-scratch"));out.put("reservationCheck",Boolean.getBoolean("tessera-x.poi-reservation-check"));
            Files.writeString(Path.of("poi-safety-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(out));
            if(error!=null)getLogger().log(java.util.logging.Level.SEVERE,"POI safety failed",error);
        }catch(Throwable write){write.printStackTrace();}
        Bukkit.getGlobalRegionScheduler().execute(this,Bukkit::shutdown);
    }
}
