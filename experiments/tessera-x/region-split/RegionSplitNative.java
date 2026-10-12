package dev.tessera.lab;

import com.google.gson.GsonBuilder;
import io.papermc.paper.threadedregions.ThreadedRegionizer.ThreadedRegion;
import io.papermc.paper.threadedregions.TickRegionScheduler;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Repeater;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Natural chunk-ticket release plus real region scheduler and redstone ticks. No manual regionizer edits. */
public final class RegionSplitNative extends JavaPlugin implements Listener {
    private static final int Z = 512, Y = 80, LEFT = 512, RIGHT = 1024;
    private static final int[] STARTS = {10,50,100,160,230,310,410,530,670,830};
    private static final int[] WIDTHS = {4,8,12,16,20,4,8,12,16,20};
    private final List<Map<String,Object>> snapshots = new CopyOnWriteArrayList<>();
    private final AtomicBoolean finished = new AtomicBoolean();
    private final Circuit a = new Circuit(LEFT), b = new Circuit(RIGHT);
    private World world; private ServerLevel level;
    private Field sectionsField, deadField;
    private long releasedNanos, eligibleNanos, splitNanos;
    private int pollTicks;
    private final AtomicInteger assertions = new AtomicInteger();
    private long initialRegion;
    private final boolean expectedSplit = recalcDelayMillis() > 0;

    private static long recalcDelayMillis() {
        try { return (long)Class.forName("dev.tessera.x.ExperimentalOptions").getMethod("regionRecalcMaxDelayMillis").invoke(null); }
        catch (ClassNotFoundException legacyBuild) { return Long.getLong("tessera-x.region-recalc-max-delay-ms", 0L); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot read experimental options", failure); }
    }
    private void require(boolean ok, String reason) { assertions.incrementAndGet(); if (!ok) throw new AssertionError(reason); }
    @Override public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this,this);
        Bukkit.getGlobalRegionScheduler().runDelayed(this, ignored -> start(), 100);
    }
    private CompletableFuture<Void> load(int cx) {
        world.addPluginChunkTicket(cx,Z,this);
        return world.getChunkAtAsync(cx,Z,true).thenApply(ignored -> null);
    }
    private void start() {
        try {
            world = Bukkit.getWorlds().getFirst(); level = ((CraftWorld)world).getHandle();
            require(level.regioniser.sectionChunkShift == 1, "Native fixture requires production grid-exponent=1");
            sectionsField = ThreadedRegion.class.getDeclaredField("sectionByKey"); sectionsField.setAccessible(true);
            deadField = ThreadedRegion.class.getDeclaredField("deadSections"); deadField.setAccessible(true);
            CompletableFuture<Void> loading = CompletableFuture.completedFuture(null);
            for (int cx=LEFT;cx<=RIGHT;cx+=8) { final int x=cx; loading=loading.thenCompose(ignored -> load(x)); }
            loading.thenCompose(ignored -> CompletableFuture.allOf(a.prepare(),b.prepare()))
                .whenComplete((ignored,error) -> {
                    if (error!=null) { finish(error); return; }
                    Bukkit.getGlobalRegionScheduler().runDelayed(this, task -> begin(),40);
                });
        } catch(Throwable e) { finish(e); }
    }
    private void begin() {
        try {
            var ar=level.regioniser.getRegionAtSynchronised(LEFT,Z);
            var br=level.regioniser.getRegionAtSynchronised(RIGHT,Z);
            require(ar!=null && ar==br,"Fixture has not merged into one region"); initialRegion=ar.id;
            getLogger().info("NATIVE_SPLIT_INITIAL region="+initialRegion);
            a.run(); b.run();
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(this, task -> {
                if (finished.get()) {task.cancel();return;}
                try {
                    pollTicks+=5;
                    if (pollTicks==40) {
                        releasedNanos=System.nanoTime();
                        for(int cx=752;cx<=816;cx+=8) require(world.removePluginChunkTicket(cx,Z,this),"Missing bridge ticket");
                        getLogger().info("NATIVE_SPLIT_RELEASE bridge tickets removed");
                    }
                    if(releasedNanos!=0) sample();
                    if(pollTicks>=940) {
                        // A split can finish between two diagnostic polls. It proves eligibility,
                        // but does not give us an earlier sampled eligibility timestamp.
                        require(eligibleNanos!=0 || splitNanos!=0,"Bridge never became eligible for split; retained holders still connect it");
                        require((splitNanos!=0)==expectedSplit,"Unexpected actual split outcome");
                        a.verify();b.verify();task.cancel();finish(null);
                    }
                } catch(Throwable error) {task.cancel();finish(error);}
            },5,5);
        } catch(Throwable e) {finish(e);}
    }
    @SuppressWarnings("unchecked") private void sample() {
        long now=System.nanoTime();
        var ar=level.regioniser.getRegionAtSynchronised(LEFT,Z);
        var br=level.regioniser.getRegionAtSynchronised(RIGHT,Z);
        require(ar!=null && br!=null,"Circuit ownership disappeared");
        if(ar!=br) {
            if(splitNanos==0) {splitNanos=now;getLogger().info("NATIVE_SPLIT_OBSERVED regions="+ar.id+","+br.id);}
            return;
        }
        final Set<Long> aliveSections = new HashSet<>(); final int[] counts=new int[2];
        level.regioniser.computeForAllRegions(region -> {
            if(region!=ar)return;
            try {
                var all=(Long2ReferenceOpenHashMap<?>)sectionsField.get(region);
                var dead=(Set<?>)deadField.get(region); counts[0]=all.size();counts[1]=dead.size();
                for(var entry:all.long2ReferenceEntrySet()) if(!dead.contains(entry.getValue())) aliveSections.add(entry.getLongKey());
            } catch(ReflectiveOperationException e) {throw new RuntimeException(e);}
        });
        int components=components(aliveSections,Math.max(level.regioniser.emptySectionCreateRadius,level.regioniser.regionSectionMergeRadius));
        if(components>1 && eligibleNanos==0) {eligibleNanos=now;getLogger().info("NATIVE_SPLIT_ELIGIBLE sections="+counts[0]+" dead="+counts[1]);}
        if(pollTicks%20==0 || components>1) snapshots.add(Map.of("msAfterTicketRelease",(now-releasedNanos)/1e6,
            "region",ar.id,"sections",counts[0],"deadSections",counts[1],"componentsAfterDeadRemoval",components));
    }
    private int components(Set<Long> remaining,int radius) {
        int count=0;ArrayList<Long> queue=new ArrayList<>();
        while(!remaining.isEmpty()) {
            count++;queue.clear();long first=remaining.iterator().next();remaining.remove(first);queue.add(first);
            for(int i=0;i<queue.size();i++) {
                long pos=queue.get(i);int x=ChunkPos.getX(pos),z=ChunkPos.getZ(pos);
                for(int dx=-radius;dx<=radius;dx++) for(int dz=-radius;dz<=radius;dz++) {
                    long next=ChunkPos.pack(x+dx,z+dz);if(remaining.remove(next))queue.add(next);
                }
            }
        }
        return count;
    }
    private final class Circuit {
        final int chunkX; final List<Edge> edges=new CopyOnWriteArrayList<>();
        volatile int tick; long lastClock,lastRegion; int transitions; volatile boolean complete;
        Circuit(int chunkX){this.chunkX=chunkX;}
        CompletableFuture<Void> prepare() {
            // Pin every circuit chunk. The bridge uses the separate ticket interval above.
            CompletableFuture<Void> loading=CompletableFuture.completedFuture(null);
            for(int dx=0;dx<=4;dx++){int cx=chunkX+dx;loading=loading.thenCompose(ignored->load(cx));}
            return loading.thenCompose(ignored -> {
                CompletableFuture<Void> done=new CompletableFuture<>();
                Bukkit.getRegionScheduler().execute(RegionSplitNative.this,world,chunkX,Z,()->{
                    try {
                        Repeater repeater=(Repeater)Material.REPEATER.createBlockData();repeater.setFacing(BlockFace.WEST);repeater.setDelay(1);
                        for(int dx=0;dx<=64;dx++) {
                            world.getBlockAt(chunkX*16+dx,Y-1,Z*16).setType(Material.STONE,false);
                            world.getBlockAt(chunkX*16+dx,Y,Z*16).setType(Material.AIR,false);
                            if(dx>0)world.getBlockAt(chunkX*16+dx,Y,Z*16).setBlockData(dx%4==1?repeater:Material.REDSTONE_WIRE.createBlockData(),true);
                        }
                        done.complete(null);
                    } catch(Throwable e){done.completeExceptionally(e);}
                });return done;
            });
        }
        void run() {
            Bukkit.getRegionScheduler().runAtFixedRate(RegionSplitNative.this,world,chunkX,Z,task->{
                if(finished.get()){task.cancel();return;}
                try {
                    tick++;long clock=TickRegionScheduler.getCurrentRegionizedWorldData().getRedstoneGameTime();
                    long region=TickRegionScheduler.getCurrentRegion().id;
                    if(tick>1) require(clock==lastClock+1,"Redstone clock jumped during split");
                    if(tick>1 && region!=lastRegion)transitions++;
                    lastClock=clock;lastRegion=region;
                    for(int i=0;i<STARTS.length;i++) {
                        if(tick==STARTS[i]) world.getBlockAt(chunkX*16,Y,Z*16).setType(Material.REDSTONE_BLOCK,true);
                        if(tick==STARTS[i]+WIDTHS[i]) world.getBlockAt(chunkX*16,Y,Z*16).setType(Material.AIR,true);
                    }
                    if(tick>=910){complete=true;task.cancel();}
                }catch(Throwable e){task.cancel();finish(e);}
            },1,1);
        }
        void verify() {
            require(complete,"Circuit did not complete");
            require(transitions==(expectedSplit?1:0),"Wrong number of circuit ownership transitions: "+transitions);
            for(int stage=0;stage<16;stage++) {
                final int s=stage;var trace=edges.stream().filter(e->e.stage==s).toList();
                require(trace.size()==STARTS.length*2,"Missing/duplicate redstone events");
                for(int pulse=0;pulse<STARTS.length;pulse++) {
                    var rise=trace.get(pulse*2);var fall=trace.get(pulse*2+1);
                    require(rise.oldValue==0 && rise.newValue==15 && fall.oldValue==15 && fall.newValue==0,"Wrong edge polarity");
                    require(rise.tick-STARTS[pulse]==2*(stage+1)-1,"Wrong scheduled update latency");
                    require(fall.tick-rise.tick==WIDTHS[pulse],"Wrong pulse width");
                }
            }
        }
        Map<String,Object> report(){return Map.of("chunkX",chunkX,"ticks",tick,"transitions",transitions,"events",edges);}
    }
    private record Edge(int stage,int tick,int oldValue,int newValue){}
    @EventHandler(priority=EventPriority.MONITOR) public void onRedstone(BlockRedstoneEvent event) {
        if(event.getBlock().getWorld()!=world || event.getBlock().getY()!=Y || event.getOldCurrent()==event.getNewCurrent())return;
        for(Circuit circuit:List.of(a,b)) {
            int dx=event.getBlock().getX()-circuit.chunkX*16-1;
            if(event.getBlock().getZ()==Z*16 && dx>=0 && dx<64 && dx%4==0 && circuit.tick>0)
                circuit.edges.add(new Edge(dx/4,circuit.tick,event.getOldCurrent(),event.getNewCurrent()));
        }
    }
    private void finish(Throwable error) {
        if(!finished.compareAndSet(false,true))return;
        try {
            Map<String,Object> result=new LinkedHashMap<>();result.put("status",error==null?"PASS":"FAIL");
            result.put("failure",error==null?null:error.toString());result.put("scope","Real plugin tickets, chunk holders, TickRegionData, region scheduler and repeater events");
            result.put("assertions",assertions.get());result.put("expectedSplit",expectedSplit);result.put("initialRegion",initialRegion);
            result.put("eligibleMsAfterTicketRelease",eligibleNanos==0?null:(eligibleNanos-releasedNanos)/1e6);
            result.put("splitMsAfterTicketRelease",splitNanos==0?null:(splitNanos-releasedNanos)/1e6);
            result.put("snapshots",snapshots);result.put("circuits",List.of(a.report(),b.report()));
            try{result.put("diagnostics",Class.forName("dev.tessera.x.RegionRecalculation").getMethod("diagnostics").invoke(null));}
            catch(ClassNotFoundException reference){result.put("diagnostics",Map.of());}
            Files.writeString(Path.of("native-split-result.json"),new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(result));
            if(error==null)getLogger().info("NATIVE_SPLIT_PASS assertions="+assertions.get());else getLogger().log(java.util.logging.Level.SEVERE,"NATIVE_SPLIT_FAIL",error);
        }catch(Exception e){getLogger().log(java.util.logging.Level.SEVERE,"Failed to save native split result",e);}
        Bukkit.getGlobalRegionScheduler().execute(this,Bukkit::shutdown);
    }
}
