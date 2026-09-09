package dev.marrowseal.wardbound.champion;

import dev.marrowseal.wardbound.LockData;
import dev.marrowseal.wardbound.WardHistory;
import dev.marrowseal.wardbound.WardHud;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.boss.AshenCuratorEntity;
import dev.marrowseal.wardbound.boss.MourningNotaryEntity;
import dev.marrowseal.wardbound.boss.PaleGamblerEntity;
import dev.marrowseal.wardbound.net.MasterLinePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;
import java.util.function.Predicate;

/** Master recognition, courage bookkeeping and the forced Phase-III dismissal of Silas. */
public final class SilasMasterEncounter {
    public static final String GAMBLER="gambler", CURATOR="curator", NOTARY="notary";
    private SilasMasterEncounter(){}

    public static void onMasterBegin(ServerPlayer initiator,String master,Predicate<UUID> participant){
        if(initiator==null||initiator.getServer()==null)return;
        var world=ChampionWorldData.get(initiator.getServer());
        DefeatedChampionEntity silas=world.person==null?null:SilasCompanion.findCurrent(initiator.getServer(),world.person);
        if(silas==null||!silas.companionActive()||silas.level()!=initiator.level()||silas.distanceToSqr(initiator)>128*128)return;
        UUID ownerId=silas.companionOwnerId();
        if(ownerId==null||participant==null||!participant.test(ownerId))return;
        ServerPlayer owner=initiator.getServer().getPlayerList().getPlayer(ownerId);
        if(owner==null)return;
        markStand(owner,master);
        react(owner,master);
    }

    public static void onCall(ServerPlayer player){
        if(player==null)return;
        var level=player.serverLevel();
        UUID id=player.getUUID();
        if(finalPhaseNearby(player))return;
        if(!level.getEntitiesOfClass(PaleGamblerEntity.class,player.getBoundingBox().inflate(128),e->!e.isRemoved()&&e.includes(id)).isEmpty()){markStand(player,GAMBLER);react(player,GAMBLER);}
        if(!level.getEntitiesOfClass(AshenCuratorEntity.class,player.getBoundingBox().inflate(128),e->!e.isRemoved()&&e.includes(id)).isEmpty()){markStand(player,CURATOR);react(player,CURATOR);}
        if(!level.getEntitiesOfClass(MourningNotaryEntity.class,player.getBoundingBox().inflate(128),e->!e.isRemoved()&&e.includes(id)).isEmpty()){markStand(player,NOTARY);react(player,NOTARY);}
    }

    private static void markStand(ServerPlayer p,String master){
        LockData data=LockData.get(p.getServer());
        data.setUniqueInt(p.getUUID(),"silas_stood_"+master,1);
        if(data.uniqueInt(p.getUUID(),"silas_master_courage")==0){
            data.setUniqueInt(p.getUUID(),"silas_master_courage",1);
            line(p,"SILAS VEYR","I kept giving it finer names. Prudence. Patience. Survival. It was fear. I am tired of letting fear choose the road for me.");
            WardHistory.recordSpecial(p,"WARD","SILAS VEYR // PRESENT BEFORE A MASTER // For the first time, Silas answered the ring without withdrawing from the old fear.");
            WardHud.send(p,"SILAS VEYR // HE DECLARES HIMSELF PRESENT",WardHud.Mood.REWARD);
        }
    }

    private static void react(ServerPlayer p,String master){
        LockData data=LockData.get(p.getServer());
        String key="silas_master_first_"+master;
        if(data.uniqueInt(p.getUUID(),key)!=0)return;
        data.setUniqueInt(p.getUUID(),key,1);
        switch(master){
            case GAMBLER -> {
                line(p,"THE PALE GAMBLER","Oh. The unfinished pest. You brought your curse back to my table? How sentimental. It will not be enough.");
                line(p,"SILAS VEYR","I am not here to be enough. I am here because I finally chose to come.");
            }
            case CURATOR -> {
                line(p,"THE ASHEN CURATOR","Silas Veyr. It has been a long time. Your final page remains as uncooperative as ever.");
                line(p,"SILAS VEYR","You kept the wrong pages.");
            }
            case NOTARY -> {
                line(p,"THE MOURNING NOTARY","SILAS VEYR // PRIOR MATTER REMAINS UNRESOLVED. YOUR RENEWED APPEARANCE IS IRREGULAR, NOT INADMISSIBLE.");
                line(p,"SILAS VEYR","Then write this one without my consent.");
            }
        }
        WardHistory.recordSpecial(p,"GLITCH","MASTER RECOGNITION // "+master.toUpperCase()+" acknowledged Silas Veyr's return to the encounter.");
    }

    public static void expelAtFinalPhase(ServerLevel level, Vec3 masterPos, String master, Predicate<UUID> participant){
        if(level==null||masterPos==null||participant==null)return;
        var world=ChampionWorldData.get(level.getServer());
        DefeatedChampionEntity silas=world.person==null?null:SilasCompanion.findCurrent(level.getServer(),world.person);
        if(silas==null||!silas.companionActive()||silas.level()!=level||silas.position().distanceToSqr(masterPos)>160*160)return;
        UUID ownerId=silas.companionOwnerId();
        if(ownerId==null||!participant.test(ownerId))return;
        ServerPlayer owner=level.getServer().getPlayerList().getPlayer(ownerId);
        if(owner!=null){
            LockData data=LockData.get(owner.getServer());
            data.setUniqueInt(owner.getUUID(),"silas_master_expelled_any",1);
            data.setUniqueInt(owner.getUUID(),"silas_master_expelled_"+master,1);
            switch(master){
                case GAMBLER -> line(owner,"THE PALE GAMBLER","Enough. I indulged the unfinished pest through two hands. The last hand is yours alone. Out.");
                case CURATOR -> line(owner,"THE ASHEN CURATOR","Enough. The final page admits no borrowed witness. Silas Veyr, return to the margin.");
                case NOTARY -> line(owner,"THE MOURNING NOTARY","THAT IS SUFFICIENT // THIRD ARTICLE EXCLUDES PRIOR UNRESOLVED WITNESSES. SILAS VEYR IS DISMISSED.");
            }
            line(owner,"SILAS VEYR","Finish it.");
            WardHistory.recordSpecial(owner,"DANGER","MASTER PHASE III // "+master.toUpperCase()+" forcibly dismissed Silas Veyr. The final phase was left to the player alone.");
            WardHud.send(owner,"SILAS VEYR // DISMISSED // THE FINAL PHASE IS YOURS",WardHud.Mood.DANGER);
        }
        SilasCompanion.returnToRoad(silas);
    }

    /** True only for a final-phase Master encounter this player actually belongs to. */
    public static boolean finalPhaseNearby(ServerPlayer player){
        if(player==null)return false;
        ServerLevel level=player.serverLevel();
        UUID id=player.getUUID();
        AABB area=player.getBoundingBox().inflate(128);
        if(!level.getEntitiesOfClass(PaleGamblerEntity.class,area,e->!e.isRemoved()&&e.includes(id)&&e.silasFinalPhase()).isEmpty())return true;
        if(!level.getEntitiesOfClass(AshenCuratorEntity.class,area,e->!e.isRemoved()&&e.includes(id)&&e.silasFinalPhase()).isEmpty())return true;
        return !level.getEntitiesOfClass(MourningNotaryEntity.class,area,e->!e.isRemoved()&&e.includes(id)&&e.silasFinalPhase()).isEmpty();
    }

    public static void victory(ServerPlayer p,String master){
        if(p==null||p.getServer()==null)return;
        LockData data=LockData.get(p.getServer());
        if(data.uniqueInt(p.getUUID(),"silas_stood_"+master)!=0)data.setUniqueInt(p.getUUID(),"silas_victory_"+master,1);
    }

    public static int distinctStands(LockData d,UUID id){
        int n=0;if(d.uniqueInt(id,"silas_stood_"+GAMBLER)!=0)n++;if(d.uniqueInt(id,"silas_stood_"+CURATOR)!=0)n++;if(d.uniqueInt(id,"silas_stood_"+NOTARY)!=0)n++;return n;
    }

    private static void line(ServerPlayer p,String speaker,String text){
        int words=text==null?0:text.trim().split("\\s+").length;
        int duration=Math.max(2600,Math.min(5400,1500+words*205));
        Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new MasterLinePacket(speaker,text,duration));
    }
}
