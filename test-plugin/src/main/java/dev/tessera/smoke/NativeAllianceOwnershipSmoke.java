package dev.tessera.smoke;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.key.Key;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.monster.warden.AngerManagement;
import net.minecraft.world.entity.monster.warden.Warden;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Real native entities, natural AI ticks, cached references, callbacks and connected clients. */
final class NativeAllianceOwnershipSmoke implements Listener {
    private final JavaPlugin plugin;
    private final List<String> checks = new CopyOnWriteArrayList<>();
    private final List<org.bukkit.entity.Entity> pets = new ArrayList<>();
    private final AtomicInteger ruleCalls = new AtomicInteger();
    private volatile org.bukkit.entity.Entity removeSource;
    private volatile org.bukkit.entity.Entity removeTarget;
    private volatile org.bukkit.entity.Entity removePushTarget;
    private volatile boolean cancelAppearance;
    private final AtomicInteger appearanceVetoes = new AtomicInteger();
    private World world;
    private World nether;
    private org.bukkit.entity.Entity warden, guardian, creaking, retaliator;
    private HurtByTargetGoal hurtGoal;
    private org.bukkit.entity.Entity betaWarden, betaGuardian;
    private org.bukkit.entity.Entity caster, vex, evoker;

    NativeAllianceOwnershipSmoke(JavaPlugin plugin) { this.plugin = plugin; }

    void start() {
        Bukkit.getPluginManager().registerEvents(this, this.plugin);
        this.plugin.getLogger().info("ALLIANCE_READY_CLIENTS");
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(this.plugin, task -> {
            if (Bukkit.getOnlinePlayers().size() < 2) return;
            task.cancel();
            Bukkit.getAsyncScheduler().runNow(this.plugin, ignored -> run());
        }, 1, 5);
    }

    private void run() {
        try {
            Player one = Bukkit.getPlayerExact("AllianceAlpha"), two = Bukkit.getPlayerExact("AllianceBeta");
            require(one != null && two != null, "two connected clients");
            this.world = one.getWorld();
            this.nether = global(() -> Bukkit.getWorlds().stream().filter(w -> w.getEnvironment() == World.Environment.NETHER).findFirst().orElseThrow()).get(10, TimeUnit.SECONDS);
            prepare(this.world, 0).get(30, TimeUnit.SECONDS);
            prepare(this.world, 128).get(30, TimeUnit.SECONDS);
            prepare(this.nether, 0).get(60, TimeUnit.SECONDS);
            move(one, this.world, 8.5);
            move(two, this.world, 10.5);
            for (Player player : List.of(one, two)) on(player, () -> {
                player.setGameMode(GameMode.SURVIVAL); player.setInvulnerable(true);
                player.addAdditionalAlliedRule(Key.key("tessera_smoke", "owner"), other -> {
                    require(Bukkit.isOwnedByCurrentRegion(player) && Bukkit.isOwnedByCurrentRegion(other), "alliance predicate runs on both owners");
                    this.ruleCalls.incrementAndGet(); return false;
                });
                return null;
            }).get(10, TimeUnit.SECONDS);
            on(one, () -> { setup(one, two); return null; }).get(15, TimeUnit.SECONDS);
            require(on(this.caster, () -> this.caster.teleportAsync(new Location(this.world, 2058.5, 4, 8.5)))
                .get(10, TimeUnit.SECONDS).get(40, TimeUnit.SECONDS), "native caster region transfer");
            UUID owner = two.getUniqueId();
            move(two, this.world, 2056.5);
            Object a = on(one, io.papermc.paper.threadedregions.TickRegionScheduler::getCurrentRegion).get(10, TimeUnit.SECONDS);
            Object b = on(two, io.papermc.paper.threadedregions.TickRegionScheduler::getCurrentRegion).get(10, TimeUnit.SECONDS);
            require(a != b, "two distinct native regions");
            on(two, () -> { two.setInvulnerable(false); return null; }).get(10, TimeUnit.SECONDS);
            on(one, () -> { staleAndPetChecks(one, two, owner, "remote region"); return null; }).get(10, TimeUnit.SECONDS);
            on(two, () -> {
                two.setInvulnerable(true);
                this.betaWarden = spawn(this.world, 2060.5, EntityType.WARDEN, false);
                this.betaGuardian = spawn(this.world, 2063.5, EntityType.ELDER_GUARDIAN, false);
                armAura(this.betaWarden, 120); armAura(this.betaGuardian, 1200);
                return null;
            }).get(10, TimeUnit.SECONDS);
            on(one, () -> { ((Mob)this.warden).setAI(true); ((Mob)this.guardian).setAI(true); armAura(this.warden, 120); armAura(this.guardian, 1200); return null; }).get(10, TimeUnit.SECONDS);
            Thread.sleep(7500); // Only the isolated asynchronous fixture coordinator waits.
            for (Player player : List.of(one, two)) on(player, () -> {
                ServerPlayer nativePlayer = ((CraftPlayer)player).getHandle();
                require(nativePlayer.hasEffect(MobEffects.DARKNESS), "natural Warden darkness " + player.getName());
                require(nativePlayer.hasEffect(MobEffects.MINING_FATIGUE), "natural ElderGuardian fatigue " + player.getName());
                require(nativePlayer.getEffect(MobEffects.MINING_FATIGUE).getAmplifier() == 2, "vanilla fatigue amplifier " + player.getName());
                return null;
            }).get(10, TimeUnit.SECONDS);
            on(one, () -> { require(this.warden.isValid() && this.guardian.isValid(), "first region mobs survive natural aura ticks"); ((Mob)this.warden).setAI(false); ((Mob)this.guardian).setAI(false); return null; }).get(10, TimeUnit.SECONDS);
            on(two, () -> { require(this.betaWarden.isValid() && this.betaGuardian.isValid(), "second region mobs survive natural aura ticks"); ((Mob)this.betaWarden).setAI(false); ((Mob)this.betaGuardian).setAI(false); return null; }).get(10, TimeUnit.SECONDS);
            require(this.ruleCalls.get() > 0, "native aura alliance callbacks observed");
            require(on(this.caster, () -> this.caster.teleportAsync(new Location(this.nether, 10.5, 4, 8.5)))
                .get(10, TimeUnit.SECONDS).get(40, TimeUnit.SECONDS), "native caster dimension transfer");
            move(two, this.nether, 8.5);
            on(two, () -> { two.setInvulnerable(false); return null; }).get(10, TimeUnit.SECONDS);
            on(one, () -> { staleAndPetChecks(one, two, owner, "other dimension"); meleeAndGroupChecks(one); callbackChecks(one); return null; }).get(15, TimeUnit.SECONDS);
            require(on(this.caster, () -> this.caster.teleportAsync(new Location(this.world, 16.5, 4, 8.5)))
                .get(10, TimeUnit.SECONDS).get(40, TimeUnit.SECONDS), "native caster returns");
            move(two, this.world, 10.5);
            on(one, () -> {
                require(((net.minecraft.world.entity.monster.Vex)raw(this.vex)).getRootOwner() == raw(this.caster), "Vex root owner resumes after caster return");
                for (org.bukkit.entity.Entity pet : this.pets) require(pet.isAlliedTo(two), "owner alliance resumes " + pet.getType());
                Warden nativeWarden = (Warden) raw(this.warden);
                AngerManagement anger = (AngerManagement) field(nativeWarden, Warden.class, "angerManagement");
                for (int i = 0; i < 3; ++i) anger.tick(((CraftWorld)this.world).getHandle(), nativeWarden::canTargetEntity, nativeWarden);
                require(anger.getActiveEntity().orElse(null) == ((CraftPlayer)two).getHandle(), "Warden anger resolves again after owner return");
                for (org.bukkit.entity.Entity pet : this.pets) require(owner.equals(((Tameable)pet).getOwnerUniqueId()), "owner UUID retained after return " + pet.getType());
                return null;
            }).get(10, TimeUnit.SECONDS);
            require(Bukkit.getOnlinePlayers().size() == 2, "clients remained connected after repeated transfers");
            Files.writeString(Path.of("alliance-checks.txt"), String.join("\n", this.checks));
            this.plugin.getLogger().info("ALLIANCE_PASS " + this.checks.size() + " checks");
        } catch (Throwable failure) {
            this.plugin.getLogger().log(java.util.logging.Level.SEVERE, "ALLIANCE_FAIL", failure);
        } finally {
            Bukkit.getGlobalRegionScheduler().execute(this.plugin, Bukkit::shutdown);
        }
    }

    private void setup(Player one, Player two) throws Exception {
        ServerPlayer nativeTwo = ((CraftPlayer)two).getHandle();
        this.warden = spawn(this.world, 12.5, EntityType.WARDEN, true);
        this.guardian = spawn(this.world, 15.5, EntityType.ELDER_GUARDIAN, true);
        this.creaking = spawn(this.world, 18.5, EntityType.CREAKING, true);
        this.retaliator = spawn(this.world, 20.5, EntityType.HUSK, true);
        this.caster = spawn(this.world, 16.5, EntityType.EVOKER, true);
        this.vex = spawn(this.world, 17.5, EntityType.VEX, true);
        this.evoker = spawn(this.world, 19.5, EntityType.EVOKER, true);
        ((net.minecraft.world.entity.monster.Vex)raw(this.vex)).setOwner((net.minecraft.world.entity.Mob)raw(this.caster));
        require(((net.minecraft.world.entity.monster.Vex)raw(this.vex)).getRootOwner() == raw(this.caster), "local Vex root owner uses vanilla binding");
        Warden nativeWarden = (Warden) raw(this.warden);
        ((AngerManagement)field(nativeWarden, Warden.class, "angerManagement")).increaseAnger(nativeTwo, 100);
        ((Creaking)raw(this.creaking)).getBrain().setMemory(MemoryModuleType.NEAREST_PLAYERS, List.of(nativeTwo));
        PathfinderMob hurt = (PathfinderMob) raw(this.retaliator);
        ++hurt.tickCount;
        hurt.setLastHurtByMob(nativeTwo); this.hurtGoal = new HurtByTargetGoal(hurt);
        for (EntityType type : List.of(EntityType.WOLF, EntityType.CAT, EntityType.PARROT, EntityType.NAUTILUS)) {
            org.bukkit.entity.Entity pet = spawn(this.world, 8.5, type, true);
            ((net.minecraft.world.entity.TamableAnimal)raw(pet)).setTame(true, false);
            ((net.minecraft.world.entity.TamableAnimal)raw(pet)).setOwner(nativeTwo);
            this.pets.add(pet);
        }
    }

    private void staleAndPetChecks(Player local, Player owner, UUID ownerId, String phase) throws Exception {
        ServerPlayer foreign = (ServerPlayer)((CraftPlayer)owner).getHandleRaw();
        Warden nativeWarden = (Warden) raw(this.warden);
        require(!nativeWarden.canTargetEntity(foreign), "stale Warden target rejected " + phase);
        AngerManagement anger = (AngerManagement)field(nativeWarden, Warden.class, "angerManagement");
        anger.tick(((CraftWorld)this.world).getHandle(), nativeWarden::canTargetEntity, nativeWarden);
        require(anger.getActiveEntity().isEmpty(), "remote anger live handle retired " + phase);
        var byUuid = (it.unimi.dsi.fastutil.objects.Object2IntMap<?>)field(anger, AngerManagement.class, "angerByUuid");
        require(byUuid.getInt(ownerId) > 0, "remote anger UUID retained " + phase);
        require(((Creaking)raw(this.creaking)).checkCanMove(), "stale Creaking memory rejected " + phase);
        require(!this.hurtGoal.canUse(), "stale retaliation attacker rejected " + phase);
        require(((net.minecraft.world.entity.monster.Vex)raw(this.vex)).getRootOwner() == null, "remote Vex root owner unavailable " + phase);
        this.evoker.isAlliedTo(this.vex);
        require(this.evoker.isValid() && this.vex.isValid(), "Evoker and Vex alliance evaluation survives " + phase);
        for (org.bukkit.entity.Entity pet : this.pets) {
            require(!local.isAlliedTo(pet), "remote pet owner rules not executed " + phase + " " + pet.getType());
            require(ownerId.equals(((Tameable)pet).getOwnerUniqueId()), "remote pet owner UUID retained " + phase + " " + pet.getType());
            require(pet.isValid(), "pet retained " + phase + " " + pet.getType());
        }
        var fangs = (net.minecraft.world.entity.projectile.EvokerFangs)raw(spawn(this.world, 24.5, EntityType.EVOKER_FANGS, true));
        fangs.setOwner(foreign);
        require(fangs.getOwner() == null, "fangs live owner unavailable " + phase);
        var cow = (LivingEntity)raw(spawn(this.world, 24.5, EntityType.COW, true));
        cow.setPermanentlyInvulnerable(false);
        float before = cow.getHealth();
        var damage = fangs.getClass().getDeclaredMethod("dealDamageTo", LivingEntity.class); damage.setAccessible(true); damage.invoke(fangs, cow);
        require(cow.getHealth() == before - 6, "fangs native magic damage with remote owner " + phase);
        var ref = (net.minecraft.world.entity.EntityReference<?>)field(fangs, fangs.getClass(), "owner");
        require(ref.getUUID().equals(ownerId), "fangs owner UUID retained " + phase);
        cow.discard(); fangs.discard();
    }

    private void callbackChecks(Player player) throws Exception {
        var level = ((CraftWorld)this.world).getHandle();
        var source = spawn(this.world, 26.5, EntityType.WARDEN, true);
        this.removeSource = source;
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.DARKNESS);
        var result = MobEffectUtil.addEffectToPlayersAround(level, raw(source), raw(source).position(), 50,
            new MobEffectInstance(MobEffects.DARKNESS, 260), 200, EntityPotionEffectEvent.Cause.WARDEN);
        require(result.isEmpty() && !player.hasPotionEffect(org.bukkit.potion.PotionEffectType.DARKNESS), "effect source retirement stops old-owner application");
        require(!source.isValid(), "effect callback actually retired source");
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.MINING_FATIGUE);
        this.cancelAppearance = true;
        Entity elder = raw(this.guardian); elder.tickCount = 1200 - Math.floorMod(elder.getId(), 1200);
        var aura = net.minecraft.world.entity.monster.ElderGuardian.class.getDeclaredMethod("customServerAiStep", net.minecraft.server.level.ServerLevel.class);
        aura.setAccessible(true); aura.invoke(elder, level);
        this.cancelAppearance = false;
        require(this.appearanceVetoes.get() > 0 && !player.hasPotionEffect(org.bukkit.potion.PotionEffectType.MINING_FATIGUE), "ElderGuardian appearance veto still suppresses fatigue");
        var cow = spawn(this.world, 28.5, EntityType.COW, true);
        var nativeCow = (LivingEntity)raw(cow);
        this.removeTarget = cow;
        require(!nativeCow.addEffect(new MobEffectInstance(MobEffects.SPEED, 100), EntityPotionEffectEvent.Cause.PLUGIN), "potion target retirement vetoes mutation");
        require(!nativeCow.hasEffect(MobEffects.SPEED), "retired target effect map unchanged");
        var other = spawn(this.world, 30.5, EntityType.ARMOR_STAND, true);
        AtomicInteger callbacks = new AtomicInteger();
        Key key = Key.key("tessera_smoke", "retire");
        player.addAdditionalAlliedRule(key, entity -> { callbacks.incrementAndGet(); entity.remove(); return false; });
        require(!player.isAlliedTo(other) && !other.isValid() && callbacks.get() == 1, "alliance callback retirement stops reverse evaluation");
        player.removeAdditionalAlliedRule(key);
        var controlled = spawn(this.world, 31.5, EntityType.CREAKING, true);
        var nativeCreaking = (Creaking) raw(controlled);
        nativeCreaking.getBrain().setMemory(MemoryModuleType.NEAREST_PLAYERS, List.of(((CraftPlayer)player).getHandle()));
        Field accessor = Creaking.class.getDeclaredField("CAN_MOVE"); accessor.setAccessible(true);
        @SuppressWarnings("unchecked") var canMove = (net.minecraft.network.syncher.EntityDataAccessor<Boolean>)accessor.get(null);
        nativeCreaking.getEntityData().set(canMove, false);
        Key creakingKey = Key.key("tessera_smoke", "retire_creaking");
        player.addAdditionalAlliedRule(creakingKey, entity -> { if (entity == controlled) entity.remove(); return false; });
        player.setInvulnerable(false);
        nativeCreaking.aiStep();
        player.setInvulnerable(true);
        player.removeAdditionalAlliedRule(creakingKey);
        require(!controlled.isValid() && !nativeCreaking.getEntityData().get(canMove), "Creaking source callback stops movement data continuation");
        var pushed = spawn(this.world, 30.5, EntityType.COW, true);
        Entity nativePushed = raw(pushed);
        nativePushed.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        this.removePushTarget = pushed;
        nativePushed.push(1, 0.7, 1, ((CraftPlayer)player).getHandle());
        require(!pushed.isValid() && nativePushed.getDeltaMovement().equals(net.minecraft.world.phys.Vec3.ZERO), "push callback retirement leaves velocity unchanged");
    }

    private void meleeAndGroupChecks(Player player) throws Exception {
        var level = ((CraftWorld)this.world).getHandle();
        ServerPlayer attacker = ((CraftPlayer)player).getHandle();
        var primary = (LivingEntity)raw(spawn(this.world, 9.5, EntityType.COW, true));
        var nearby = (LivingEntity)raw(spawn(this.world, 9.0, EntityType.COW, true));
        primary.setPermanentlyInvulnerable(false); nearby.setPermanentlyInvulnerable(false);
        var sweep = net.minecraft.world.entity.player.Player.class.getDeclaredMethod("doSweepAttack", Entity.class, float.class, net.minecraft.world.damagesource.DamageSource.class, float.class);
        sweep.setAccessible(true);
        float health = nearby.getHealth();
        sweep.invoke(attacker, primary, 6.0F, level.damageSources().playerAttack(attacker), 1.0F);
        require(nearby.getHealth() < health, "native sword sweep damages local target beside remote-owned pets");
        nearby.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        var mace = net.minecraft.world.item.MaceItem.class.getDeclaredMethod("knockback", net.minecraft.world.level.Level.class, Entity.class, Entity.class);
        mace.setAccessible(true); mace.invoke(null, level, attacker, primary);
        require(nearby.getDeltaMovement().lengthSqr() > 0, "native mace knockback beside remote-owned pets");
        PathfinderMob hurt = (PathfinderMob) raw(this.retaliator);
        hurt.setLastHurtByMob(primary);
        require(this.hurtGoal.canUse(), "local retaliation still uses vanilla selection");
        this.hurtGoal.start();
        require(hurt.getTarget() == primary, "local retaliation target retained");
        var first = (net.minecraft.world.entity.monster.zombie.ZombifiedPiglin)raw(spawn(this.world, 32.5, EntityType.ZOMBIFIED_PIGLIN, true));
        var second = (net.minecraft.world.entity.monster.zombie.ZombifiedPiglin)raw(spawn(this.world, 34.5, EntityType.ZOMBIFIED_PIGLIN, true));
        first.setTarget(primary);
        var alert = first.getClass().getDeclaredMethod("alertOthers"); alert.setAccessible(true); alert.invoke(first);
        require(second.getTarget() == primary, "native piglin group alarm retains local target");
        second.setTarget(null);
        second.getBukkitEntity().addAdditionalAlliedRule(Key.key("tessera_smoke", "retire_source"), ignored -> { first.discard(); return false; });
        alert.invoke(first);
        require(second.getTarget() == null && first.isRemoved(), "piglin alliance callback retirement stops group mutation");
        second.discard(); primary.discard(); nearby.discard();
    }

    @EventHandler
    public void appearance(io.papermc.paper.event.entity.ElderGuardianAppearanceEvent event) {
        if (this.cancelAppearance) { this.appearanceVetoes.incrementAndGet(); event.setCancelled(true); }
    }

    @EventHandler
    public void push(io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent event) {
        if (event.getEntity() == this.removePushTarget) { this.removePushTarget = null; event.getEntity().remove(); }
    }

    @EventHandler
    public void potion(EntityPotionEffectEvent event) {
        if (this.removeSource != null && event.getCause() == EntityPotionEffectEvent.Cause.WARDEN) {
            org.bukkit.entity.Entity source = this.removeSource; this.removeSource = null; source.remove();
        }
        if (event.getEntity() == this.removeTarget) {
            this.removeTarget = null; event.getEntity().remove();
        }
    }

    private org.bukkit.entity.Entity spawn(World world, double x, EntityType type, boolean noAI) {
        var entity = world.spawnEntity(new Location(world, x, 4, 8.5), type);
        entity.setGravity(false); entity.setInvulnerable(true); entity.setPersistent(true);
        if (entity instanceof Mob mob) { mob.setAI(!noAI); mob.setRemoveWhenFarAway(false); }
        return entity;
    }

    private static void armAura(org.bukkit.entity.Entity entity, int period) {
        Entity handle = raw(entity); handle.tickCount = period - 1 - Math.floorMod(handle.getId(), period);
    }

    private static Entity raw(org.bukkit.entity.Entity entity) { return ((CraftEntity)entity).getHandle(); }
    private static Object field(Object instance, Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(instance);
    }
    private void move(Player player, World destination, double x) throws Exception {
        require(on(player, () -> player.teleportAsync(new Location(destination, x, 4, 8.5))).get(10, TimeUnit.SECONDS).get(40, TimeUnit.SECONDS), "native transfer " + player.getName() + " " + destination.getName() + " " + x);
    }
    private CompletableFuture<Void> prepare(World world, int x) {
        var futures = new ArrayList<CompletableFuture<?>>();
        for (int dx = -1; dx <= 1; ++dx) for (int dz = -1; dz <= 1; ++dz) futures.add(world.getChunkAtAsync(x + dx, dz, true));
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).thenCompose(ignored -> {
            var result = new CompletableFuture<Void>();
            Bukkit.getRegionScheduler().execute(this.plugin, world, x, 0, () -> {
                try { world.getChunkAt(x, 0).addPluginChunkTicket(this.plugin); result.complete(null); }
                catch (Throwable failure) { result.completeExceptionally(failure); }
            }); return result;
        });
    }
    private <T> CompletableFuture<T> on(org.bukkit.entity.Entity player, Callable<T> action) {
        if (!(player instanceof Player)) {
            var result = new CompletableFuture<T>();
            var task = player.getScheduler().run(this.plugin, ignored -> {
                try { result.complete(action.call()); } catch (Throwable failure) { result.completeExceptionally(failure); }
            }, () -> result.completeExceptionally(new IllegalStateException("test entity retired")));
            if (task == null) result.completeExceptionally(new IllegalStateException("test entity not schedulable"));
            return result;
        }
        return ((CraftEntity)player).taskScheduler.scheduleRestore(entity -> {
            try { return CompletableFuture.completedFuture(action.call()); }
            catch (Throwable failure) { return CompletableFuture.failedFuture(failure); }
        }).toCompletableFuture();
    }
    private <T> CompletableFuture<T> global(Callable<T> action) {
        var result = new CompletableFuture<T>();
        Bukkit.getGlobalRegionScheduler().execute(this.plugin, () -> {
            try { result.complete(action.call()); } catch (Throwable failure) { result.completeExceptionally(failure); }
        }); return result;
    }
    private void require(boolean condition, String check) { if (!condition) throw new AssertionError(check); this.checks.add(check); }
}
