package dev.marrowseal.wardbound.boss;

import dev.marrowseal.wardbound.LockData;
import dev.marrowseal.wardbound.WardAdvancements;
import dev.marrowseal.wardbound.WardHud;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.ancientsmith.AncientSmithProgression;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/** Private pre-final arena for the Head of Cthulhu. Full Cthulhu uses a separate future encounter. */
@Mod.EventBusSubscriber(modid=Wardbound.MODID)
public final class CthulhuHeadArena {
    public static final ResourceLocation REALM_ID=new ResourceLocation(Wardbound.MODID,"cthulhu_abyss");
    public static final ResourceKey<Level> REALM=ResourceKey.create(Registries.DIMENSION,REALM_ID);
    public static final int FLOOR_Y=64,ARENA_RADIUS=47;
    private static final int CELL_SPACING=192;
    private static final String ROOT="WardboundCthulhuHeadArena",ACTIVE="active",CENTER="center",RETURN_DIM="return_dim";
    private static final String RX="return_x",RY="return_y",RZ="return_z",RYAW="return_yaw",RPITCH="return_pitch",EMPTY="empty",VICTORY="victory_until";
    private static final int VICTORY_LINGER=20*20;

    private CthulhuHeadArena(){}

    public static boolean active(ServerPlayer p){return p!=null&&p.getPersistentData().getCompound(ROOT).getBoolean(ACTIVE);}

    public static boolean start(ServerPlayer p,boolean debug){
        if(p==null||p.getServer()==null||active(p))return false;
        if(MasterArenaManager.active(p)||p.level().dimension().equals(MasterArenaManager.REALM)||AncientSmithProgression.active(p)||p.level().dimension().equals(AncientSmithProgression.REALM)){
            WardHud.send(p,"THE DREAMING ABYSS // ANOTHER CLAIM STILL HOLDS YOU",WardHud.Mood.WARD);return false;
        }
        if(p.level().dimension().equals(REALM))return false;
        ServerLevel realm=p.getServer().getLevel(REALM);if(realm==null){WardHud.send(p,"THE DREAMING ABYSS // THE SEA BEHIND SLEEP COULD NOT BE FOUND",WardHud.Mood.DANGER);return false;}
        BlockPos center=cellCenter(p.getUUID());preload(realm,center);if(cellOccupiedByOther(realm,center,p.getUUID())){WardHud.send(p,"THE DREAMING ABYSS // ANOTHER WITNESS IS ALREADY BEING DREAMED HERE",WardHud.Mood.DANGER);return false;}cleanup(realm,center,null);prepare(realm,center);
        CompoundTag root=new CompoundTag();root.putBoolean(ACTIVE,true);root.putLong(CENTER,center.asLong());root.putString(RETURN_DIM,p.level().dimension().location().toString());root.putDouble(RX,p.getX());root.putDouble(RY,p.getY());root.putDouble(RZ,p.getZ());root.putFloat(RYAW,p.getYRot());root.putFloat(RPITCH,p.getXRot());root.putInt(EMPTY,0);p.getPersistentData().put(ROOT,root);
        Vec3 spawn=playerSpawn(center);p.teleportTo(realm,spawn.x,spawn.y,spawn.z,180f,0f);if(!p.level().dimension().equals(REALM)){clear(p);return false;}p.fallDistance=0;
        CthulhuHeadEntity head=WardBosses.CTHULHU_HEAD.get().create(realm);if(head==null){abort(p);return false;}Vec3 boss=bossPos(center);head.moveTo(boss.x,boss.y,boss.z,0f,0f);head.setYBodyRot(0f);head.setYHeadRot(0f);if(!realm.addFreshEntity(head)){abort(p);return false;}head.beginCombat(p,center,debug);
        WardHud.send(p,"THE DREAMING ABYSS // SOMETHING OLDER THAN THE MASTERS HAS OPENED ITS EYES",WardHud.Mood.GLITCH,4200);return true;
    }

    public static Vec3 bossPos(BlockPos center){return new Vec3(center.getX()+.5,center.getY()+4.0,center.getZ()+.5);}
    public static Vec3 playerSpawn(BlockPos center){return new Vec3(center.getX()+.5,center.getY()+1.05,center.getZ()+35.5);}

    public static void victory(CthulhuHeadEntity head){
        if(head==null||head.combatOwner()==null||!(head.level() instanceof ServerLevel level))return;
        ServerPlayer p=level.getServer().getPlayerList().getPlayer(head.combatOwner());if(p==null)return;CompoundTag root=p.getPersistentData().getCompound(ROOT);if(!root.getBoolean(ACTIVE))return;
        root.putLong(VICTORY,level.getGameTime()+VICTORY_LINGER);root.putInt(EMPTY,0);p.getPersistentData().put(ROOT,root);
        if(!head.isPractice()){LockData.get(p.getServer()).setUniqueInt(p.getUUID(),"cthulhu_head_defeated",1);WardAdvancements.cthulhuHeadDefeated(p);CthulhuHeadProgression.queueVictory(p);
            WardHud.send(p,"THE HEAD DREAMS NO MORE // THIS WAS NEVER THE WHOLE BODY. SOMETHING VAST BENEATH IT HAS NOTICED THE SILENCE",WardHud.Mood.REWARD,5600);}
        else WardHud.send(p,"THE DREAM WITHDRAWS // THIS REHEARSAL LEAVES NO MARK UPON THE DEEP",WardHud.Mood.WARD,4200);
    }

    public static void abort(ServerPlayer p){if(p==null||p.getServer()==null||!active(p))return;CompoundTag root=p.getPersistentData().getCompound(ROOT).copy();BlockPos center=root.contains(CENTER)?BlockPos.of(root.getLong(CENTER)):null;ServerLevel realm=p.getServer().getLevel(REALM);if(p.isAlive())teleportBack(p,root);clear(p);if(realm!=null&&center!=null)cleanup(realm,center,null);}

    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!(e.player instanceof ServerPlayer p))return;
        if(!active(p)){if(p.level().dimension().equals(REALM)){ServerLevel out=p.getServer().overworld();Vec3 s=Vec3.atBottomCenterOf(out.getSharedSpawnPos()).add(0,1,0);p.teleportTo(out,s.x,s.y,s.z,p.getYRot(),p.getXRot());}return;}
        CompoundTag root=p.getPersistentData().getCompound(ROOT);
        if(!p.level().dimension().equals(REALM)){BlockPos center=root.contains(CENTER)?BlockPos.of(root.getLong(CENTER)):null;ServerLevel realm=p.getServer().getLevel(REALM);if(root.contains(VICTORY)){clear(p);if(realm!=null&&center!=null)cleanup(realm,center,null);return;}if(realm!=null&&center!=null){Vec3 spawn=playerSpawn(center);p.teleportTo(realm,spawn.x,spawn.y,spawn.z,180f,0f);p.fallDistance=0;WardHud.send(p,"THE DREAMING ABYSS // THE DREAM DOES NOT RELEASE AN UNFINISHED WITNESS",WardHud.Mood.DANGER,1800);return;}clear(p);return;}
        if(!root.contains(CENTER)){abort(p);return;}BlockPos center=BlockPos.of(root.getLong(CENTER));long now=p.serverLevel().getGameTime();
        if(root.contains(VICTORY)){if(now>=root.getLong(VICTORY)){CompoundTag copy=root.copy();teleportBack(p,copy);clear(p);cleanup(p.getServer().getLevel(REALM),center,null);CthulhuHeadProgression.deliverVictory(p);}return;}
        double radial=p.position().subtract(Vec3.atCenterOf(center)).multiply(1,0,1).lengthSqr();if(p.getY()<center.getY()-7||radial>(ARENA_RADIUS+2)*(ARENA_RADIUS+2)){if(p.isCreative())p.teleportTo(p.serverLevel(),playerSpawn(center).x,playerSpawn(center).y,playerSpawn(center).z,180,0);else p.hurt(p.damageSources().fellOutOfWorld(),1000f);}
        boolean boss=!p.serverLevel().getEntitiesOfClass(CthulhuHeadEntity.class,new AABB(center).inflate(64,40,64),h->h.isCombat()&&p.getUUID().equals(h.combatOwner())&&!h.isRemoved()).isEmpty();
        if(boss){root.putInt(EMPTY,0);p.getPersistentData().put(ROOT,root);}else{int empty=root.getInt(EMPTY)+1;root.putInt(EMPTY,empty);p.getPersistentData().put(ROOT,root);if(empty>=60)abort(p);}
    }

    @SubscribeEvent public static void death(LivingDeathEvent e){if(!(e.getEntity() instanceof ServerPlayer p)||!active(p)||!p.level().dimension().equals(REALM))return;CthulhuHeadProgression.noteFailedAttempt(p);CompoundTag root=p.getPersistentData().getCompound(ROOT).copy();BlockPos c=root.contains(CENTER)?BlockPos.of(root.getLong(CENTER)):null;ServerLevel realm=p.serverLevel();clear(p);if(c!=null)cleanup(realm,c,null);}
    @SubscribeEvent public static void drops(LivingDropsEvent e){if(e.getEntity() instanceof ServerPlayer p&&p.level().dimension().equals(REALM))e.getDrops().clear();}
    @SubscribeEvent public static void breakBlock(BlockEvent.BreakEvent e){if(e.getPlayer() instanceof ServerPlayer p&&p.level().dimension().equals(REALM))e.setCanceled(true);}
    @SubscribeEvent public static void place(BlockEvent.EntityPlaceEvent e){if(e.getEntity() instanceof ServerPlayer p&&p.level().dimension().equals(REALM))e.setCanceled(true);}
    @SubscribeEvent public static void fluid(BlockEvent.FluidPlaceBlockEvent e){if(e.getLevel() instanceof ServerLevel l&&l.dimension().equals(REALM))e.setCanceled(true);}

    private static void clear(ServerPlayer p){p.getPersistentData().remove(ROOT);}
    private static void teleportBack(ServerPlayer p,CompoundTag root){ResourceLocation id=ResourceLocation.tryParse(root.getString(RETURN_DIM));ServerLevel target=id==null?null:p.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,id));if(target==null||target.dimension().equals(REALM))target=p.getServer().overworld();Vec3 preferred=new Vec3(root.getDouble(RX),root.getDouble(RY),root.getDouble(RZ));Vec3 q=safeReturn(target,p,preferred);float yaw=Float.isFinite(root.getFloat(RYAW))?root.getFloat(RYAW):p.getYRot(),pitch=Float.isFinite(root.getFloat(RPITCH))?root.getFloat(RPITCH):p.getXRot();p.teleportTo(target,q.x,q.y,q.z,yaw,pitch);p.fallDistance=0;}
    private static Vec3 safeReturn(ServerLevel l,ServerPlayer p,Vec3 preferred){Vec3 fallback=Vec3.atBottomCenterOf(l.getSharedSpawnPos()).add(0,1,0);if(!finite(preferred))preferred=fallback;if(safeAt(l,p,preferred))return preferred;BlockPos base=BlockPos.containing(preferred);for(int r=1;r<=6;r++)for(int x=-r;x<=r;x++)for(int z=-r;z<=r;z++){if(Math.abs(x)!=r&&Math.abs(z)!=r)continue;for(int y=-2;y<=4;y++){Vec3 q=Vec3.atBottomCenterOf(base.offset(x,y,z));if(safeAt(l,p,q))return q;}}return fallback;}
    private static boolean safeAt(ServerLevel l,ServerPlayer p,Vec3 q){if(!finite(q))return false;BlockPos feet=BlockPos.containing(q),head=feet.above(),floor=feet.below();if(!l.isInWorldBounds(head)||!l.getWorldBorder().isWithinBounds(feet))return false;l.getChunkAt(floor);if(!l.getFluidState(feet).isEmpty()||!l.getFluidState(head).isEmpty()||!l.getFluidState(floor).isEmpty())return false;if(!l.getBlockState(floor).isFaceSturdy(l,floor,Direction.UP))return false;AABB moved=p.getBoundingBox().move(q.subtract(p.position()));return l.noCollision(p,moved);}
    private static boolean finite(Vec3 q){return q!=null&&Double.isFinite(q.x)&&Double.isFinite(q.y)&&Double.isFinite(q.z);}

    private static BlockPos cellCenter(UUID id){long h=id.getMostSignificantBits()^Long.rotateLeft(id.getLeastSignificantBits(),21);int x=(Math.floorMod((int)h,64)-32)*CELL_SPACING,z=(Math.floorMod((int)(h>>>32),64)-32)*CELL_SPACING;return new BlockPos(x,FLOOR_Y,z);}
    private static void preload(ServerLevel l,BlockPos c){int minX=(c.getX()-52)>>4,maxX=(c.getX()+52)>>4,minZ=(c.getZ()-52)>>4,maxZ=(c.getZ()+52)>>4;for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)l.getChunk(x,z);}
    private static void prepare(ServerLevel l,BlockPos c){
        for(int x=-ARENA_RADIUS;x<=ARENA_RADIUS;x++)for(int z=-ARENA_RADIUS;z<=ARENA_RADIUS;z++){double r=Math.sqrt(x*x+z*z);if(r>ARENA_RADIUS+.2)continue;BlockPos below=c.offset(x,-1,z),floor=c.offset(x,0,z);l.setBlock(below,Blocks.OBSIDIAN.defaultBlockState(),2);BlockState top;if(r>44)top=Blocks.CRYING_OBSIDIAN.defaultBlockState();else if(((x*x+z*z)%37)<3)top=Blocks.SCULK.defaultBlockState();else if((Math.abs(x)+Math.abs(z))%13==0)top=Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();else top=Blocks.DEEPSLATE_TILES.defaultBlockState();l.setBlock(floor,top,2);}
        l.setBlock(c,Blocks.REINFORCED_DEEPSLATE.defaultBlockState(),2);
        for(int i=0;i<12;i++){double a=i*Math.PI*2/12;int x=(int)Math.round(Math.cos(a)*42),z=(int)Math.round(Math.sin(a)*42);for(int y=1;y<=4+(i%3);y++)l.setBlock(c.offset(x,y,z),y==4+(i%3)?Blocks.SCULK_CATALYST.defaultBlockState():Blocks.CRYING_OBSIDIAN.defaultBlockState(),2);}
    }
    private static boolean cellOccupiedByOther(ServerLevel l,BlockPos c,UUID owner){AABB box=new AABB(c).inflate(60,36,60);for(ServerPlayer p:l.players())if(!p.getUUID().equals(owner)&&p.distanceToSqr(Vec3.atCenterOf(c))<60*60)return true;return !l.getEntitiesOfClass(CthulhuHeadEntity.class,box,h->h.isCombat()&&h.combatOwner()!=null&&!h.combatOwner().equals(owner)&&!h.isRemoved()).isEmpty();}
    private static void cleanup(ServerLevel l,BlockPos c,UUID keepOwner){if(l==null)return;AABB box=new AABB(c).inflate(60,36,60);for(Entity e:l.getEntities((Entity)null,box,e->e!=null&&!e.isRemoved())){if(e instanceof ServerPlayer)continue;if(e instanceof CthulhuHeadEntity h&&keepOwner!=null&&keepOwner.equals(h.combatOwner()))continue;if(e instanceof CthulhuHeadEntity||e instanceof ItemEntity||e instanceof ExperienceOrb||e instanceof Projectile)e.discard();}}
}
