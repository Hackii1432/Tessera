import dev.tessera.x.RegionConnectivity;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import com.google.gson.GsonBuilder;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;

/** Differential tests against the unchanged native BFS loop, plus algorithm-only cost measurements. */
public final class RegionConnectivityComparison {
    record Node(long key) {}
    static final class Nodes extends Long2ReferenceOpenHashMap<Node> {
        long lookups;
        @Override public Node remove(long key) { lookups++; return super.remove(key); }
        @Override public Nodes clone() { return (Nodes)super.clone(); }
        void add(int x,int z) { long key=pack(x,z);put(key,new Node(key)); }
    }
    static long pack(int x,int z) {return (x&0xffffffffL)|((long)z<<32);}
    static volatile long sink;
    static int assertions;
    static void require(boolean ok,String reason) {assertions++;if(!ok)throw new AssertionError(reason);}
    static List<List<Node>> legacy(Nodes remaining,int radius) {
        List<List<Node>> result=new ArrayList<>();
        while(!remaining.isEmpty()) {
            List<Node> group=new ArrayList<>();Iterator<Node> first=remaining.values().iterator();
            group.add(first.next());first.remove();
            search:for(int i=0;i<group.size();i++) {
                long pos=group.get(i).key;int x=(int)pos,z=(int)(pos>>32);
                for(int dz=-radius;dz<=radius;dz++)for(int dx=-radius;dx<=radius;dx++) {
                    if((dx|dz)==0)continue;
                    Node next=remaining.remove(pack(x+dx,z+dz));
                    if(next==null)continue;group.add(next);
                    if(remaining.isEmpty())break search;
                }
            }
            result.add(group);
        }
        return result;
    }
    static List<List<Node>> covered(Nodes remaining,int radius) {return RegionConnectivity.find(remaining,radius,Node::key);}
    static void compare(Nodes input,int radius) {
        Nodes before=input.clone(),after=input.clone();
        var expected=legacy(before,radius);var actual=covered(after,radius);
        require(expected.equals(actual),"Different components or BFS order: radius="+radius+" nodes="+input.size());
        require(before.isEmpty()&&after.isEmpty(),"Unassigned nodes remain");
        require(expected.stream().mapToInt(List::size).sum()==input.size(),"Lost nodes");
    }
    static void differential() {
        Random random=new Random(77102);
        for(int test=0;test<3000;test++) {
            Nodes input=new Nodes();int radius=1+random.nextInt(8);
            int originX=test%7==0?Integer.MAX_VALUE-20:test%11==0?Integer.MIN_VALUE+5:random.nextInt(101)-50;
            int originZ=test%13==0?Integer.MAX_VALUE-20:test%17==0?Integer.MIN_VALUE+5:random.nextInt(101)-50;
            int width=1+random.nextInt(50),height=1+random.nextInt(40);
            for(int x=0;x<width;x++)for(int z=0;z<height;z++)if(random.nextDouble()<.45)input.add(originX+x,originZ+z);
            compare(input,radius);
        }
        for(int radius=1;radius<=8;radius++) {
            Nodes hollow=new Nodes();for(int x=-80;x<=80;x++)for(int z=-80;z<=80;z++)if(Math.abs(x)>65||Math.abs(z)>65)hollow.add(x,z);
            compare(hollow,radius);
            Nodes islands=new Nodes();for(int k=0;k<100;k++)for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++)islands.add(k*40+x,z);
            compare(islands,radius);
        }
    }
    static long allocated() {
        var bean=ManagementFactory.getThreadMXBean();
        return bean instanceof com.sun.management.ThreadMXBean b?b.getThreadAllocatedBytes(Thread.currentThread().threadId()):0;
    }
    static Map<String,Object> measure(Nodes input,int radius,boolean optimized) {
        for(int i=0;i<15;i++) {var result=optimized?covered(input.clone(),radius):legacy(input.clone(),radius);sink+=result.size();}
        var cpu=ManagementFactory.getThreadMXBean();
        List<Double> times=new ArrayList<>();long allocations=allocated(),cpuStart=cpu.getCurrentThreadCpuTime();
        int repetitions=30;
        for(int i=0;i<repetitions;i++) {
            long start=System.nanoTime();var result=optimized?covered(input.clone(),radius):legacy(input.clone(),radius);
            times.add((System.nanoTime()-start)/1e6);sink+=result.size();
        }
        long allocatedBytes=allocated()-allocations,cpuNanos=cpu.getCurrentThreadCpuTime()-cpuStart;
        Nodes counted=input.clone();var result=optimized?covered(counted,radius):legacy(counted,radius);
        var sorted=times.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        return Map.of("components",result.size(),"mapRemoves",counted.lookups,"meanMs",times.stream().mapToDouble(Double::doubleValue).average().orElseThrow(),
            "medianMs",sorted[sorted.length/2],"p95Ms",sorted[(int)Math.ceil(sorted.length*.95)-1],
            "allocatedBytesPerPass",allocatedBytes/(double)repetitions,"cpuMsPerPass",cpuNanos/1e6/repetitions);
    }
    public static void main(String[] args)throws Exception {
        differential();var rows=new ArrayList<Map<String,Object>>();
        for(int test=0;test<5;test++) {
            Nodes input=new Nodes();int radius=test==2?8:test==3?1:4;String name;
            if(test==4) {name="isolated_points";for(int i=0;i<1000;i++)input.add(i*40,0);}
            else if(test==1) {name="many_buffered_islands";for(int k=0;k<100;k++)for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)input.add(k*40+x,z);}
            else {name=test==2?"dense_radius8":test==3?"dense_radius1":"dense_radius4";for(int x=0;x<200;x++)for(int z=0;z<100;z++)input.add(x,z);}
            compare(input,radius);
            Map<String,Object> row=new LinkedHashMap<>();row.put("case",name);row.put("nodes",input.size());row.put("radius",radius);
            row.put("legacy",measure(input,radius,false));row.put("covered",measure(input,radius,true));rows.add(row);
        }
        var result=Map.of("status","PASS","scope","Algorithm-only differential BFS and clone/search cost; not a region-tick benchmark", "assertions",assertions,"cases",rows);
        Files.writeString(Path.of(args[0]),new GsonBuilder().setPrettyPrinting().create().toJson(result));
        System.out.println("CONNECTIVITY_PASS assertions="+assertions+" result="+args[0]);
    }
}
