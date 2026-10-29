import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public class PatchShopLab implements Opcodes {
    static final String LAB="shoplab/ShopLab";
    static MethodInsnNode call(String method,String desc){return new MethodInsnNode(INVOKESTATIC,LAB,method,desc,false);}
    static byte[] patch(String name,byte[] bytes,boolean vanilla){
        boolean target=Set.of("net/minecraft/server/level/ServerLevel","net/minecraft/server/MinecraftServer",
            "net/minecraft/world/entity/npc/villager/Villager","net/minecraft/world/entity/ai/village/poi/PoiManager",
            "ca/spottedleaf/moonrise/patches/poi_lookup/PoiAccess","dev/tessera/x/ShopPoiSearch").contains(name);
        if(!target)return bytes;
        ClassNode c=new ClassNode();new ClassReader(bytes).accept(c,0);
        for(MethodNode m:c.methods){
            if(name.equals("net/minecraft/server/level/ServerLevel") && m.name.equals("tick")){
                InsnList a=new InsnList();a.add(new VarInsnNode(ALOAD,0));a.add(call("before","(Lnet/minecraft/server/level/ServerLevel;)V"));m.instructions.insert(a);
                for(AbstractInsnNode ins:m.instructions.toArray())if(ins.getOpcode()==RETURN){InsnList b=new InsnList();b.add(new VarInsnNode(ALOAD,0));b.add(call("after","(Lnet/minecraft/server/level/ServerLevel;)V"));m.instructions.insertBefore(ins,b);}
            }
            if(vanilla && name.equals("net/minecraft/server/MinecraftServer") && m.name.equals("tickServer")){
                for(AbstractInsnNode ins:m.instructions.toArray())if(ins.getOpcode()==RETURN){InsnList a=new InsnList();a.add(new VarInsnNode(ALOAD,0));a.add(call("vanillaBoot","(Lnet/minecraft/server/MinecraftServer;)V"));m.instructions.insertBefore(ins,a);}
            }
            if(name.equals("net/minecraft/world/entity/npc/villager/Villager") && m.name.equals("restock")){
                for(AbstractInsnNode ins:m.instructions.toArray())if(ins.getOpcode()==RETURN){InsnList a=new InsnList();a.add(new VarInsnNode(ALOAD,0));a.add(call("restocked","(Lnet/minecraft/world/entity/npc/villager/Villager;)V"));m.instructions.insertBefore(ins,a);}
            }
            if((vanilla && name.endsWith("/PoiManager") && m.name.equals("findAllClosestFirstWithType"))
                ||(!vanilla && name.endsWith("/PoiAccess") && m.name.equals("findNearestPoiPositions"))){
                m.instructions.insert(call("nativeQuery","()V"));
            }
            if(name.equals("dev/tessera/x/ShopPoiSearch") && m.name.equals("find")){
                for(AbstractInsnNode ins:m.instructions.toArray())if(ins.getOpcode()==IRETURN){InsnList a=new InsnList();a.add(new InsnNode(DUP));a.add(call("shopResult","(Z)V"));m.instructions.insertBefore(ins,a);}
            }
        }
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);return w.toByteArray();
    }
    public static void main(String[] args)throws Exception{
        boolean vanilla=args[3].equals("vanilla");
        try(ZipFile in=new ZipFile(args[0]);ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(Path.of(args[1])))){
            for(ZipEntry e:Collections.list(in.entries())){
                if(e.isDirectory() || e.getName().matches("META-INF/.*\\.(SF|RSA|DSA)"))continue;
                byte[] b=in.getInputStream(e).readAllBytes();if(e.getName().endsWith(".class"))b=patch(e.getName().substring(0,e.getName().length()-6),b,vanilla);
                out.putNextEntry(new ZipEntry(e.getName()));out.write(b);out.closeEntry();
            }
            Path root=Path.of(args[2]);try(var files=Files.walk(root)){for(Path f:files.filter(f->f.toString().endsWith(".class")).toList()){
                out.putNextEntry(new ZipEntry(root.relativize(f).toString().replace('\\','/')));out.write(Files.readAllBytes(f));out.closeEntry();
            }}
        }
    }
}
