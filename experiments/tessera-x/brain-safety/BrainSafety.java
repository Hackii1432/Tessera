package dev.tessera.lab;

import com.google.common.collect.ImmutableList;
import com.google.gson.GsonBuilder;
import com.mojang.datafixers.util.Pair;
import io.papermc.paper.threadedregions.TickRegionScheduler;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.schedule.Activity;
import org.bukkit.*;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

/** Tests native Brain snapshots and a deterministic inter-region sensor range interleaving. */
public final class BrainSafety extends JavaPlugin {
    private final AtomicInteger assertions = new AtomicInteger();
    private final AtomicBoolean finished = new AtomicBoolean();
    private final Map<String,Object> results = new ConcurrentHashMap<>();
    private final CountDownLatch aUpdated = new CountDownLatch(1), bUpdated = new CountDownLatch(1), aQueried = new CountDownLatch(1);
    private World world; private ServerLevel level;
    private final LivingEntity[] bodies = new LivingEntity[2], targets = new LivingEntity[2];
    private final long[] regionIds = new long[2], threads = new long[2];
    private final boolean local = Boolean.getBoolean("tessera-x.sensor-local-conditions");
    private void require(boolean value,String message) { assertions.incrementAndGet(); if(!value) throw new AssertionError(message); }
    @Override public void onEnable() {
        Bukkit.getGlobalRegionScheduler().runDelayed(this, task -> start(), 100);
    }
    private void start() {
        try {
            world = Bukkit.getWorlds().getFirst(); level = ((CraftWorld)world).getHandle();
            world.setGameRule(GameRule.DO_MOB_SPAWNING,false);
            CompletableFuture<?>[] jobs = new CompletableFuture<?>[6]; int n=0;
            for(int group=0;group<2;group++) for(int offset=0;offset<3;offset++) {
                int cx=group*512+offset;
                world.addPluginChunkTicket(cx,0,this);
                jobs[n++]=world.getChunkAtAsync(cx,0,true);
            }
            CompletableFuture.allOf(jobs).thenCompose(ignored -> CompletableFuture.allOf(prepare(0),prepare(1)))
                .whenComplete((ignored,error) -> {
                    if(error!=null) {finish(error);return;}
                    Bukkit.getGlobalRegionScheduler().runDelayed(this, task -> probes(),40);
                });
        } catch(Throwable error) {finish(error);}
    }
    private CompletableFuture<Void> prepare(int group) {
        CompletableFuture<Void> done=new CompletableFuture<>();
        Bukkit.getRegionScheduler().execute(this,world,group*512,0,() -> {
            try {
                double x=group*8192+2;
                bodies[group]=spawn(EntityType.VILLAGER,x);
                targets[group]=spawn(EntityType.COW,x+32);
                bodies[group].getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(group==0?64:16);
                if(group==0) snapshots(bodies[0]);
                done.complete(null);
            } catch(Throwable error) {done.completeExceptionally(error);}
        });
        return done;
    }
    private LivingEntity spawn(EntityType type,double x) {
        var bukkit=(org.bukkit.entity.LivingEntity)world.spawnEntity(new Location(world,x,80,2),type);
        LivingEntity entity=((CraftLivingEntity)bukkit).getHandle();
        entity.setNoGravity(true); ((Mob)entity).setNoAi(true); entity.noPhysics=true;
        return entity;
    }
    private static final class Recorder implements BehaviorControl<LivingEntity> {
        Behavior.Status status=Behavior.Status.RUNNING;
        Runnable action=() -> {}; final String name; final List<String> trace;
        Recorder(String name,List<String> trace) {this.name=name;this.trace=trace;}
        public Behavior.Status getStatus(){return status;}
        public Set<MemoryModuleType<?>> getRequiredMemories(){return Set.of();}
        public boolean tryStart(ServerLevel level,LivingEntity body,long time){return false;}
        public void tickOrStop(ServerLevel level,LivingEntity body,long time){trace.add(name);action.run();}
        public void doStop(ServerLevel level,LivingEntity body,long time){status=Behavior.Status.STOPPED;}
        public String debugString(){return name;}
    }
    private void install(Brain<LivingEntity> brain,Recorder... values) {
        ImmutableList.Builder<Pair<Integer,? extends BehaviorControl<? super LivingEntity>>> builder=ImmutableList.builder();
        for(int i=0;i<values.length;i++) builder.add(Pair.of(i,values[i]));
        brain.addActivity(Activity.CORE,builder.build(),Set.of(),Set.of());
    }
    private void snapshots(LivingEntity body) throws Exception {
        List<String> all=new ArrayList<>(), trace=new ArrayList<>();
        Brain<LivingEntity> brain=new Brain<>();
        Recorder a=new Recorder("A",trace), b=new Recorder("B",trace), c=new Recorder("C",trace);
        install(brain,a,b); List<?> held=brain.getRunningBehaviors();
        a.action=() -> {brain.removeAllBehaviors();b.status=Behavior.Status.STOPPED;install(brain,c);};
        brain.tick(level,body);require(trace.equals(List.of("A","B")),"Mutation changed captured running snapshot");
        require(held.size()==2 && held.get(0)==a && held.get(1)==b,"Public snapshot changed after internal clear");
        brain.tick(level,body);require(trace.equals(List.of("A","B","C")),"Next tick did not use new behavior map"); all.addAll(trace);
        trace.clear(); brain.removeAllBehaviors();
        Recorder r=new Recorder("R",trace), s=new Recorder("S",trace); AtomicBoolean recursive=new AtomicBoolean();
        r.action=() -> {if(recursive.compareAndSet(false,true)) brain.tick(level,body);};
        install(brain,r,s);brain.tick(level,body);
        require(trace.equals(List.of("R","R","S","S")),"Recursive tick changed running snapshot order");all.addAll(trace);
        trace.clear();brain.removeAllBehaviors();
        Recorder x=new Recorder("X",trace), y=new Recorder("Y",trace); AtomicBoolean throwing=new AtomicBoolean(true);
        x.action=() -> {if(throwing.getAndSet(false)) throw new IllegalStateException("expected fixture exception");};
        install(brain,x,y);boolean caught=false;
        try {brain.tick(level,body);} catch(IllegalStateException expected){caught=true;}
        require(caught,"Behavior exception was swallowed");brain.tick(level,body);
        require(trace.equals(List.of("X","X","Y")),"Exception left scratch dirty/busy");all.addAll(trace);
        trace.clear();brain.removeAllBehaviors();
        Recorder p=new Recorder("P",trace), q=new Recorder("Q",trace);p.action=() -> brain.stopAll(level,body);
        install(brain,p,q);brain.tick(level,body);require(trace.equals(List.of("P","Q")),"stopAll changed outer captured snapshot");all.addAll(trace);
        trace.clear();brain.removeAllBehaviors();
        Recorder[] many=new Recorder[300];for(int i=0;i<many.length;i++)many[i]=new Recorder("N"+i,trace);
        install(brain,many);brain.tick(level,body);require(trace.size()==300,"Large snapshot truncated");
        if(Boolean.getBoolean("tessera-x.brain-scratch")) {
            Field scratch=Brain.class.getDeclaredField("tesseraXRunningScratch");scratch.setAccessible(true);
            Field busy=Brain.class.getDeclaredField("tesseraXRunningScratchBusy");busy.setAccessible(true);
            require(scratch.get(brain)==null,"Oversized scratch retained"); require(!busy.getBoolean(brain),"Scratch busy after callback");
        }
        results.put("brainTrace",all);results.put("largeSnapshotSize",trace.size());
    }
    private void probes() {
        CompletableFuture<Void> left=new CompletableFuture<>(),right=new CompletableFuture<>();
        Bukkit.getRegionScheduler().execute(this,world,0,0,() -> probe(0,left));
        Bukkit.getRegionScheduler().execute(this,world,512,0,() -> probe(1,right));
        CompletableFuture.allOf(left,right).whenComplete((ignored,error) -> {
            if(error==null) try {
                require(regionIds[0]!=regionIds[1],"Sensor probes were in same region");
                require(threads[0]!=threads[1],"Sensor probes were on same worker");
                results.put("regions",regionIds);results.put("threads",threads);
            } catch(Throwable failure) {error=failure;}
            finish(error);
        });
    }
    private void await(CountDownLatch latch) {
        try {require(latch.await(5,TimeUnit.SECONDS),"Probe interleaving timed out");}
        catch(InterruptedException error){Thread.currentThread().interrupt();throw new RuntimeException(error);}
    }
    private void probe(int group,CompletableFuture<Void> done) {
        try {
            regionIds[group]=TickRegionScheduler.getCurrentRegion().id;threads[group]=Thread.currentThread().threadId();
            Sensor<LivingEntity> probe=new Sensor<>(1) {
                public Set<MemoryModuleType<?>> requires(){return Set.of();}
                protected void doTick(ServerLevel owner,LivingEntity body) {
                    if(group==0) {aUpdated.countDown();await(bUpdated);
                        boolean seen=Sensor.isEntityTargetable(owner,body,targets[0]);
                        require(seen==local,"Wide-range sensor did not match expected legacy/fixed result: "+seen);
                        results.put("wideRangeCrossRegionTargetSeen",seen);aQueried.countDown();
                    } else {bUpdated.countDown();await(aQueried);
                        boolean seen=Sensor.isEntityTargetable(owner,body,targets[1]);
                        require(!seen,"Short-range sensor saw target beyond 16 blocks");results.put("shortRangeTargetSeen",seen);
                    }
                }
            };
            if(group==1) await(aUpdated);
            probe.tick(level,bodies[group]);
            if(group==0) {
                LivingEntity shortBody=spawn(EntityType.VILLAGER,6);shortBody.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(16);
                new Sensor<LivingEntity>(1) {public Set<MemoryModuleType<?>> requires(){return Set.of();}protected void doTick(ServerLevel owner,LivingEntity body){}}
                    .tick(level,shortBody);
                boolean seen=Sensor.isEntityTargetable(level,bodies[0],targets[0]);
                require(seen==local,"Same-thread query used previous body's range");results.put("wideRangeAfterShortSensorSeen",seen);
            }
            done.complete(null);
        } catch(Throwable error) {aUpdated.countDown();bUpdated.countDown();aQueried.countDown();done.completeExceptionally(error);}
    }
    private void finish(Throwable error) {
        if(!finished.compareAndSet(false,true))return;
        try {
            Map<String,Object> out=new TreeMap<>(results);out.put("status",error==null?"PASS":"FAIL");
            out.put("failure",error==null?null:error.toString());out.put("assertions",assertions.get());
            out.put("localConditions",local);out.put("brainScratch",Boolean.getBoolean("tessera-x.brain-scratch"));
            Files.writeString(Path.of("brain-safety-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(out));
            if(error!=null)getLogger().log(java.util.logging.Level.SEVERE,"Native brain safety failed",error);
        } catch(Throwable failure){failure.printStackTrace();}
        Bukkit.getGlobalRegionScheduler().execute(this,Bukkit::shutdown);
    }
}
