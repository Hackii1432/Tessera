package shoplab;

import com.google.gson.GsonBuilder;
import com.sun.management.ThreadMXBean;
import java.lang.invoke.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.village.poi.*;
import net.minecraft.world.entity.npc.villager.*;
import net.minecraft.world.item.trading.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/** Observer/input harness on real autonomous ticks. Never calls a Brain/entity tick or restock. */
public final class ShopLab {
    public static volatile boolean finished;
    private static final String mode=System.getProperty("shoplab.mode","baseline");
    private static final boolean smoke=Boolean.getBoolean("shoplab.smoke"),benchOnly=Boolean.getBoolean("shoplab.benchOnly");
    private static ServerLevel world;private static Object owner;private static MethodHandle ownerAccess;
    private static final ThreadLocal<Boolean> inside=ThreadLocal.withInitial(()->false);
    private static final ThreadMXBean bean=(ThreadMXBean)ManagementFactory.getThreadMXBean();
    private static final com.sun.management.OperatingSystemMXBean process=(com.sun.management.OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean();
    private static final List<Villager> mobs=new ArrayList<>();
    private static final List<BlockPos> blocks=new ArrayList<>();
    private static final List<Map<String,Object>> observations=new ArrayList<>(),benchmarks=new ArrayList<>(),restocks=new ArrayList<>();
    private static final List<Double> wall=new ArrayList<>(),cpu=new ArrayList<>(),alloc=new ArrayList<>();
    private static final int[] sizes={256,1024,4096,256,1024};
    private static final String[] names={"cells-256","cells-1024","cells-4096","redstone-and-poi-churn-256","rooms-2x1-1024"};
    private static int globalTicks,bootTicks,frame,phase=-1,assertions,restockCount;
    private static long cpuStart,wallStart,allocStart,processStart,queryCount,hitCount,fallbackCount;
    private static long benchmarkStarted,measurementStarted;
    private static volatile long requestedTime=26000;
    private static boolean initialRules;
    private static Villager merchant,reroll,photo,walker;
    private static BlockPos merchantPos,rerollPos,photoPos,walkerPos;
    private static int merchantReexhausted,photoReexhausted,photoRestockCount;private static boolean changedProfession,walkedOut;
    private static Class<?> memoClass;private static Method findMemo;
    private static int tickStartQueries;

    private static void require(boolean condition,String reason){assertions++;if(!condition)throw new AssertionError(reason);}
    private static void command(MinecraftServer server,String s){server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),s);}
    public static void global(MinecraftServer server){
        if(finished)return;
        try{
            globalTicks++;
            if(!initialRules){initialRules=true;command(server,"gamerule minecraft:spawn_mobs false");command(server,"gamerule minecraft:advance_time false");}
            long t=requestedTime;if(t>=0){requestedTime=-1;command(server,"time set "+t);}
        }catch(Throwable failure){fail(failure);}
    }
    public static void vanillaBoot(MinecraftServer server){
        if(finished){server.halt(false);return;}global(server);bootTicks++;
        try{
            if(bootTicks==20)for(int x=-3;x<=15;x++)for(int z=-3;z<=15;z++){server.overworld().setChunkForced(x,z,true);server.overworld().getChunk(x,z);}
            if(bootTicks==60)attach(server.overworld(),server.overworld());
        }catch(Throwable failure){fail(failure);}
    }
    public static void attach(ServerLevel level,Object region)throws Exception{
        world=level;owner=region;
        try{ownerAccess=MethodHandles.publicLookup().unreflect(Level.class.getMethod("getCurrentWorldData")).asType(MethodType.methodType(Object.class,Level.class));}catch(NoSuchMethodException vanilla){ownerAccess=null;}
        bean.setThreadCpuTimeEnabled(true);bean.setThreadAllocatedMemoryEnabled(true);
        if(mode.equals("shop")){
            memoClass=Class.forName("dev.tessera.x.ShopPoiSearch");
            require(memoClass.getField("ENABLED").getBoolean(null),"Shop flag not enabled");
            findMemo=Arrays.stream(memoClass.getMethods()).filter(m->m.getName().equals("find")).findFirst().orElseThrow();
        }
        if(benchOnly){phase=0;setupBenchmark();}else setupFunctional();
    }
    private static boolean owns(ServerLevel level){try{return world==level && (ownerAccess==null || (Object)ownerAccess.invokeExact((Level)level)==owner);}catch(Throwable e){fail(e);return false;}}
    public static void before(ServerLevel level){
        if(finished || world==null || !owns(level))return;
        inside.set(true);cpuStart=bean.getCurrentThreadCpuTime();wallStart=System.nanoTime();allocStart=bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
        try{frame++;if(phase<0)functionalInput();else benchmarkInput();}catch(Throwable failure){fail(failure);}
    }
    public static void after(ServerLevel level){
        if(!inside.get() || world!=level)return;
        try{
            long c=bean.getCurrentThreadCpuTime()-cpuStart,w=System.nanoTime()-wallStart,a=bean.getThreadAllocatedBytes(Thread.currentThread().threadId())-allocStart;
            if(phase<0){
                if(frame==(smoke?260:3700)){finishFunctional();if(smoke)finish();else {phase=0;setupBenchmark();}}
            }else{
                long now=System.nanoTime();
                if(measurementStarted==0 && now-benchmarkStarted>=10_000_000_000L){
                    wall.clear();cpu.clear();alloc.clear();queryCount=hitCount=fallbackCount=0;processStart=process.getProcessCpuTime();measurementStarted=now;
                    System.out.println("SHOP_LAB measurement started "+names[phase]+" frame="+frame);
                }else if(measurementStarted!=0){
                    wall.add(w/1e6);cpu.add(c/1e6);alloc.add((double)a);
                    if(wall.size()%100==0)System.out.println("SHOP_LAB measurement samples="+wall.size()+" "+names[phase]);
                    if(wall.size()>=10 && now-measurementStarted>=15_000_000_000L){collect();phase++;if(phase==sizes.length)finish();else setupBenchmark();}
                }
            }
        }catch(Throwable failure){fail(failure);}finally{inside.set(false);}
    }
    public static void nativeQuery(){if(inside.get())queryCount++;}
    public static void shopResult(boolean hit){if(inside.get()){if(hit)hitCount++;else fallbackCount++;}}
    public static void restocked(Villager v){
        if(finished || phase>=0 || v!=merchant && v!=photo)return;
        try{
            if(v==merchant)restockCount++;else photoRestockCount++;
            MerchantOffer o=v.getOffers().getFirst();
            require(o.getUses()==0,"Native restock did not reset trade uses");
            restocks.add(Map.of("who",v==merchant?"solid-cell":"trapdoor-cell","number",v==merchant?restockCount:photoRestockCount,"frame",frame,"gameTime",world.getGameTime(),"uses",o.getUses(),"demand",o.getDemand()));
            System.out.println("SHOP_LAB native restock solid="+restockCount+" trapdoor="+photoRestockCount+" frame="+frame);
        }catch(Throwable failure){fail(failure);}
    }
    private static void place(BlockPos p,BlockState s){world.setBlock(p,s,3);blocks.add(p.immutable());}
    private static void clear(){for(Villager v:mobs)v.discard();mobs.clear();for(BlockPos p:blocks)world.setBlock(p,Blocks.AIR.defaultBlockState(),2);blocks.clear();}
    private static void cell(BlockPos p,int kind){
        // kind 0: two-high walls, 1: three walls/open front/top trapdoor, 2: low walls/roof trapdoor.
        place(p.below(),Blocks.STONE.defaultBlockState());
        for(Direction d:Direction.Plane.HORIZONTAL){
            boolean front=d==Direction.EAST;
            if(kind!=1 || !front)place(p.relative(d),Blocks.STONE.defaultBlockState());
            if(kind==0 || kind==3 || kind==1 && !front)place(p.relative(d).above(),Blocks.STONE.defaultBlockState());
        }
        if(kind==1)place(p.east().above(),Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF,Half.TOP).setValue(TrapDoorBlock.FACING,Direction.WEST));
        if(kind==2)place(p.above(2),Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF,Half.BOTTOM).setValue(TrapDoorBlock.FACING,Direction.WEST));
        if(kind==3){
            world.setBlock(p.east(),Blocks.AIR.defaultBlockState(),3);world.setBlock(p.east().above(),Blocks.AIR.defaultBlockState(),3);
            place(p.east().below(),Blocks.STONE.defaultBlockState());place(p.east(2),Blocks.STONE.defaultBlockState());place(p.east(2).above(),Blocks.STONE.defaultBlockState());
            for(Direction side:List.of(Direction.NORTH,Direction.SOUTH)){place(p.east().relative(side),Blocks.STONE.defaultBlockState());place(p.east().relative(side).above(),Blocks.STONE.defaultBlockState());}
        }
    }
    private static Villager spawn(BlockPos p,int kind,boolean experienced){
        cell(p,kind);
        BlockPos job=kind==1?p.below():p.west();
        place(job,Blocks.BARREL.defaultBlockState());
        Villager v=new Villager(EntityTypes.VILLAGER,world);v.setPos(p.getX()+.5,p.getY(),p.getZ()+.5);
        v.setVillagerData(v.getVillagerData().withProfession(world.registryAccess(),VillagerProfession.FISHERMAN));
        v.setVillagerXp(experienced?1:0);v.setPersistenceRequired();
        require(world.addFreshEntity(v),"Villager spawn failed");v.refreshBrain(world);
        if(!mode.equals("vanilla"))try{require(Villager.class.getField("defaultActivationState").getBoolean(v),"Fixture must fully activate every villager, comparable to Vanilla");}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
        // Define a valid initial shop assignment; all subsequent work, validation and rerolls are native.
        PoiManager pm=world.getPoiManager();pm.add(job,world.registryAccess().getOrThrow(PoiTypes.FISHERMAN));
        require(pm.take(t->t.is(PoiTypes.FISHERMAN),(t,b)->b.equals(job),job,1).isPresent(),"Could not reserve initial workstation");
        v.getBrain().setMemory(MemoryModuleType.JOB_SITE,GlobalPos.of(world.dimension(),job));
        mobs.add(v);return v;
    }
    private static void exhaust(Villager v){MerchantOffer o=v.getOffers().getFirst();while(!o.isOutOfStock())o.increaseUses();}
    private static void setupFunctional(){
        clear();frame=0;merchantPos=new BlockPos(8,1,8);rerollPos=new BlockPos(20,1,8);photoPos=new BlockPos(32,1,8);
        merchant=spawn(merchantPos,0,true);reroll=spawn(rerollPos,0,false);photo=spawn(photoPos,1,true);
        walkerPos=new BlockPos(44,1,8);walker=spawn(walkerPos,1,true);
        for(int i=1;i<=5;i++)place(walkerPos.east(i).below(),Blocks.STONE.defaultBlockState());
        exhaust(merchant);exhaust(photo);System.out.println("SHOP_LAB functional started mode="+mode);
    }
    private static void functionalInput()throws Exception{
        if(frame==60)controlledTests();
        if(restockCount>merchantReexhausted){exhaust(merchant);merchantReexhausted=restockCount;}
        if(photoRestockCount>photoReexhausted){exhaust(photo);photoReexhausted=photoRestockCount;}
        if(frame==100){place(rerollPos.west(),Blocks.BLAST_FURNACE.defaultBlockState());observations.add(Map.of("case","workstation-replaced","frame",frame));}
        if(frame>100 && reroll.getVillagerData().profession().is(VillagerProfession.ARMORER) && !changedProfession){changedProfession=true;observations.add(Map.of("case","native-profession-change","frame",frame,"profession","armorer"));}
        if(frame==650)require(changedProfession,"Native workstation profession change not observed by 650 ticks");
        if(frame==700){world.setBlock(walkerPos.east().above(),Blocks.AIR.defaultBlockState(),3);walker.getBrain().setMemory(MemoryModuleType.WALK_TARGET,new WalkTarget(walkerPos.east(4),.5F,0));}
        if(frame>700 && frame<1100 && !walker.blockPosition().equals(walkerPos))walkedOut=true;
        if(frame==1100){require(walkedOut,"Villager did not move out after entrance opened");observations.add(Map.of("case","autonomous-walk-after-trapdoor-removal","position",walker.position().toString()));}
        if(frame==3000){
            require(restockCount==2,"Expected exactly two native restocks before day change, got "+restockCount);
            require(photoRestockCount==2,"Trapdoor cell did not preserve two native restocks");
            for(String who:List.of("solid-cell","trapdoor-cell")){
                var rows=restocks.stream().filter(r->r.get("who").equals(who)).toList();
                long first=((Number)rows.get(0).get("gameTime")).longValue(),second=((Number)rows.get(1).get("gameTime")).longValue();
                require(second-first>2400,"Second native restock ignored 2400-tick cooldown: "+who);
            }
            requestedTime=50000;
        }
        if(frame%600==0)System.out.println("SHOP_LAB functional frame="+frame+" restocks="+restockCount+" queries="+queryCount+" hits="+hitCount);
    }
    private static Object memo()throws Exception{return mode.equals("shop")?memoClass.getConstructor().newInstance():null;}
    @SuppressWarnings({"unchecked","rawtypes"})
    private static boolean query(Object memo,Villager v,Predicate<BlockPos> predicate,List output)throws Exception{
        Predicate<Holder<PoiType>> home=t->t.is(PoiTypes.HOME);
        if(memo!=null)return (boolean)findMemo.invoke(memo,world,v,world.getPoiManager(),home,predicate,output);
        world.getPoiManager().findAllClosestFirstWithType(home,predicate,v.blockPosition(),48,PoiManager.Occupancy.HAS_SPACE).limit(5).forEach(output::add);return false;
    }
    private static void controlledTests()throws Exception{
        Object memo=memo();PoiManager pm=world.getPoiManager();List<Object> out=new ArrayList<>();
        query(memo,merchant,p->true,out);require(out.isEmpty(),"Unexpected HOME in empty fixture");
        out.clear();boolean hit=query(memo,merchant,p->true,out);require(hit==mode.equals("shop"),"Strict 1x1 automatic admission/cache hit missing");
        BlockPos bed=merchantPos.offset(4,0,0);pm.add(bed,world.registryAccess().getOrThrow(PoiTypes.HOME));
        out.clear();hit=query(memo,merchant,p->true,out);require(!hit && out.size()==1,"New POI did not wake search");
        require(pm.take(t->t.is(PoiTypes.HOME),(t,p)->p.equals(bed),bed,1).isPresent(),"HOME ticket reserve failed");
        out.clear();query(memo,merchant,p->true,out);require(out.isEmpty(),"Full POI returned as free");
        out.clear();query(memo,merchant,p->true,out);require(pm.release(bed),"HOME ticket release failed");
        out.clear();hit=query(memo,merchant,p->true,out);require(!hit && out.size()==1,"Ticket release without block change did not wake search");
        int[] calls={0};Predicate<BlockPos> stateful=p->{calls[0]++;return false;};
        out.clear();query(memo,merchant,stateful,out);query(memo,merchant,stateful,out);require(calls[0]==2,"Stateful predicate was skipped");pm.remove(bed);
        out.clear();query(memo,merchant,p->true,out);out.clear();query(memo,merchant,p->true,out);
        world.setBlock(merchantPos.east(),Blocks.AIR.defaultBlockState(),3);world.setBlock(merchantPos.east().above(),Blocks.AIR.defaultBlockState(),3);
        place(merchantPos.east().below(),Blocks.STONE.defaultBlockState());
        place(merchantPos.east(2),Blocks.STONE.defaultBlockState());place(merchantPos.east(2).above(),Blocks.STONE.defaultBlockState());
        for(Direction side:List.of(Direction.NORTH,Direction.SOUTH)){place(merchantPos.east().relative(side),Blocks.STONE.defaultBlockState());place(merchantPos.east().relative(side).above(),Blocks.STONE.defaultBlockState());}
        out.clear();hit=query(memo,merchant,p->true,out);require(!hit,"Enclosed 2x1 room incorrectly stayed reduced");
        world.setBlock(merchantPos.east(),Blocks.STONE.defaultBlockState(),3);world.setBlock(merchantPos.east().above(),Blocks.STONE.defaultBlockState(),3);
        out.clear();query(memo,merchant,p->true,out);out.clear();hit=query(memo,merchant,p->true,out);require(hit==mode.equals("shop"),"Closed cell did not reactivate automatically");
        // Screenshot-style open front, workstation under the villager, upper trapdoor.
        Object photoMemo=memo();out.clear();query(photoMemo,photo,p->true,out);out.clear();hit=query(photoMemo,photo,p->true,out);
        require(hit==mode.equals("shop"),"Screenshot-style trapdoor cell not recognized");
        if(mode.equals("shop")){
            BlockPos door=photoPos.east().above();BlockState closed=world.getBlockState(door);boolean foundOpen=false;
            for(Direction facing:Direction.Plane.HORIZONTAL){
                world.setBlock(door,closed.setValue(TrapDoorBlock.OPEN,true).setValue(TrapDoorBlock.FACING,facing),3);
                out.clear();query(photoMemo,photo,p->true,out);out.clear();if(query(photoMemo,photo,p->true,out)){foundOpen=true;break;}
            }
            require(foundOpen,"No physically blocking OPEN trapdoor orientation was recognized");
            BlockState open=world.getBlockState(door);
            world.setBlock(door,open.setValue(TrapDoorBlock.OPEN,false).setValue(TrapDoorBlock.POWERED,false),3);
            BlockPos power=door.east();place(power,Blocks.REDSTONE_BLOCK.defaultBlockState());
            require(world.getBlockState(door).getValue(TrapDoorBlock.OPEN),"Real Redstone did not open entrance trapdoor");
            out.clear();require(query(photoMemo,photo,p->true,out),"Blocking powered/open trapdoor incorrectly disabled 1x1 mode");
            world.setBlock(power,Blocks.AIR.defaultBlockState(),3);
            require(!world.getBlockState(door).getValue(TrapDoorBlock.OPEN),"Trapdoor did not close after power removal");
            world.setBlock(door,Blocks.AIR.defaultBlockState(),3);out.clear();require(!query(photoMemo,photo,p->true,out),"Removing entrance trapdoor did not wake search");
            world.setBlock(door,closed,3);
        }
        observations.add(Map.of("case","controlled-poi-tickets-cell-transitions-trapdoors","assertions",assertions,"photoCell",photoPos.toShortString()));
        System.out.println("SHOP_LAB controlled tests passed assertions="+assertions);
    }
    private static void finishFunctional(){
        require(!merchant.isNoAi() && !photo.isNoAi(),"Shop changed native NoAI");
        require(changedProfession,"Native workstation/profession reroll not completed");
        require(smoke || restockCount>=3,"Next-day native restock not observed");
        require(smoke || photoRestockCount>=3,"Next-day native restock failed in trapdoor cell");
        require(merchant.getBrain().hasMemoryValue(MemoryModuleType.JOB_SITE),"Valid original workstation memory lost");
        observations.add(Map.of("case","autonomous-functional","restocks",restockCount,"professionChanged",changedProfession,"ticks",frame));
    }
    private static void setupBenchmark(){
        clear();int n=sizes[phase];
        for(int i=0;i<n;i++){int x=2+(i%64)*3,z=2+(i/64)*3;spawn(new BlockPos(x,1,z),phase==4?3:i%2==0?0:1,true);}
        frame=0;measurementStarted=0;benchmarkStarted=System.nanoTime();System.out.println("SHOP_LAB benchmark started "+names[phase]);
    }
    private static void benchmarkInput(){
        if(phase==3 && frame%20==0){
            BlockPos p=new BlockPos(1,2,1);
            if(frame%40!=0)world.getPoiManager().add(p,world.registryAccess().getOrThrow(PoiTypes.HOME));else world.getPoiManager().remove(p);
        }
        if(phase==3 && frame%2==0){BlockPos p=new BlockPos(0,1,0);world.setBlock(p,frame%4==0?Blocks.REDSTONE_BLOCK.defaultBlockState():Blocks.STONE.defaultBlockState(),3);}
    }
    private static double mean(List<Double> a){return a.stream().mapToDouble(x->x).average().orElse(0);}
    private static double percentile(List<Double> a,double p){var b=new ArrayList<>(a);Collections.sort(b);return b.get(Math.min(b.size()-1,(int)Math.ceil(p*b.size())-1));}
    private static void collect(){
        Map<String,Object> r=new LinkedHashMap<>();r.put("scenario",names[phase]);r.put("villagers",sizes[phase]);r.put("ticks",wall.size());
        r.put("worldWallMeanMs",mean(wall));r.put("worldWallP95Ms",percentile(wall,.95));r.put("worldWallP99Ms",percentile(wall,.99));
        r.put("worldCpuMeanMs",mean(cpu));r.put("allocatedBytesPerTick",mean(alloc));r.put("processCpuMsPerTick",(process.getProcessCpuTime()-processStart)/1e6/wall.size());
        r.put("measurementSeconds",(System.nanoTime()-measurementStarted)/1e9);r.put("observedWorldTicksPerSecond",wall.size()/((System.nanoTime()-measurementStarted)/1e9));
        r.put("nativePoiQueriesPerTick",queryCount/(double)wall.size());r.put("memoHitsPerTick",hitCount/(double)wall.size());r.put("memoMissesPerTick",fallbackCount/(double)wall.size());
        r.put("livingVillagers",mobs.stream().filter(Villager::isAlive).count());require(mobs.stream().allMatch(Villager::isAlive),"Benchmark villager died");
        benchmarks.add(r);System.out.println("SHOP_LAB benchmark result "+new GsonBuilder().create().toJson(r));
    }
    private static void finish(){write(null);finished=true;}
    private static void fail(Throwable error){if(finished)return;error.printStackTrace();write(error);finished=true;}
    private static void write(Throwable error){try{
        Map<String,Object> r=new LinkedHashMap<>();r.put("status",error==null?"PASS":"FAIL");r.put("failure",error==null?null:error.toString());r.put("mode",mode);r.put("smoke",smoke);r.put("benchOnly",benchOnly);r.put("assertions",assertions);r.put("observations",observations);r.put("restocks",restocks);r.put("benchmarks",benchmarks);r.put("productionContacted",false);
        Files.writeString(Path.of("shop-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(r));
    }catch(Exception e){e.printStackTrace();}}
}
