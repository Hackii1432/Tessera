package shopplugin;
import java.util.*;
import java.util.concurrent.*;
import java.lang.reflect.*;
import org.bukkit.*;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.plugin.java.JavaPlugin;

public final class ShopPlugin extends JavaPlugin {
    public void onEnable(){
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(this,task->{try{
            Class<?> lab=Class.forName("shoplab.ShopLab");
            lab.getMethod("global",net.minecraft.server.MinecraftServer.class).invoke(null,net.minecraft.server.MinecraftServer.getServer());
            if(lab.getField("finished").getBoolean(null)){task.cancel();Bukkit.shutdown();}
        }catch(Throwable e){e.printStackTrace();task.cancel();Bukkit.shutdown();}},1,1);
        Bukkit.getGlobalRegionScheduler().runDelayed(this,task->start(),40);
    }
    private void start(){try{
        World w=Bukkit.getWorlds().getFirst();List<CompletableFuture<?>> loads=new ArrayList<>();
        for(int x=-3;x<=15;x++)for(int z=-3;z<=15;z++){w.addPluginChunkTicket(x,z,this);loads.add(w.getChunkAtAsync(x,z,true));}
        CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).whenComplete((unused,e)->{
            if(e!=null){e.printStackTrace();Bukkit.getGlobalRegionScheduler().execute(this,Bukkit::shutdown);return;}
            Bukkit.getRegionScheduler().runDelayed(this,w,0,0,task->{try{
                var level=((CraftWorld)w).getHandle();Class.forName("shoplab.ShopLab").getMethod("attach",net.minecraft.server.level.ServerLevel.class,Object.class).invoke(null,level,level.getCurrentWorldData());
            }catch(Throwable error){error.printStackTrace();Bukkit.getGlobalRegionScheduler().execute(this,Bukkit::shutdown);}},50);
        });
    }catch(Throwable error){error.printStackTrace();Bukkit.getGlobalRegionScheduler().execute(this,Bukkit::shutdown);}}
}
