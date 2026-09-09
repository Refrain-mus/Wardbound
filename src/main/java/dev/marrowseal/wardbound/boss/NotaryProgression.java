package dev.marrowseal.wardbound.boss;

import dev.marrowseal.wardbound.*;
import dev.marrowseal.wardbound.item.WardItems;
import dev.marrowseal.wardbound.net.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;

public final class NotaryProgression {
    private NotaryProgression(){}

    public static boolean evidence(LockData d,UUID id){
        var s=BossConfig.notary();
        return MasterStory.chapter(d,id,CardMaster.MOURNING_NOTARY)>=s.storyChapter()
                && OccultChainEvents.stage(d,id,OccultChainEvents.ChainEvent.WITNESS_SEQUENCE)>=s.chainStage()
                && d.uniqueInt(id,"objectives_completed_total")>=s.revisedCards();
    }
    public static boolean eligible(LockData d,UUID id){return BossConfig.notary().enabled()&&evidence(d,id);}
    public static String status(ServerPlayer p){var d=LockData.get(p.getServer());var id=p.getUUID();var s=BossConfig.notary();return "Notary chapter "+MasterStory.chapter(d,id,CardMaster.MOURNING_NOTARY)+"/"+s.storyChapter()+" | Witness Sequence "+OccultChainEvents.stage(d,id,OccultChainEvents.ChainEvent.WITNESS_SEQUENCE)+"/"+s.chainStage()+" | completed objectives "+d.uniqueInt(id,"objectives_completed_total")+"/"+s.revisedCards()+" | defeated: "+d.uniqueInt(id,"notary_defeated");}
    public static void giveBell(ServerPlayer p){ItemStack bell=new ItemStack(WardItems.FINAL_WITNESS_BELL.get());if(!p.getInventory().contains(bell)&&!p.getInventory().add(bell))p.drop(bell,false);}

    public static void openConsentPanel(ServerPlayer p){
        if(p==null||p.getServer()==null)return;var d=LockData.get(p.getServer());boolean ready=p.isCreative()||eligible(d,p.getUUID());
        String text=ready?"The record is complete enough to admit a future witness. Accepting service authorizes three tolls; the third files your presence as evidence.":"The notice names you, but the evidentiary chain is incomplete. The Notary cannot yet serve the final article.";
        Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new OpenNotaryInvocationPacket(ready,text));
    }

    public static void handleConsentAction(ServerPlayer p,int action){
        ItemStack bell=heldBell(p);if(bell.isEmpty())return;
        if(action==1){bell.getOrCreateTag().putBoolean(FinalWitnessBellItem.CONSENT,false);bell.getOrCreateTag().putInt(FinalWitnessBellItem.TOLLS,0);WardHud.send(p,"FINAL WITNESS // Service withdrawn. The bell returns to silence.",WardHud.Mood.WARD);return;}
        if(!p.isCreative()&&!eligible(LockData.get(p.getServer()),p.getUUID())){openConsentPanel(p);return;}
        bell.getOrCreateTag().putBoolean(FinalWitnessBellItem.CONSENT,true);bell.getOrCreateTag().putInt(FinalWitnessBellItem.TOLLS,0);
        WardHistory.recordSpecial(p,"WARD","Accepted service of the Mourning Notary's final witness notice.");
        WardHud.send(p,"FINAL WITNESS // Service accepted. Toll the bell three times when you are prepared to be entered into the record.",WardHud.Mood.DANGER);
        p.serverLevel().playSound(null,p.blockPosition(),SoundEvents.BELL_RESONATE,SoundSource.PLAYERS,.7f,.58f);
    }

    public static void toll(ServerPlayer p,ItemStack bell){
        if(p==null||bell.isEmpty())return;var tag=bell.getOrCreateTag();if(!tag.getBoolean(FinalWitnessBellItem.CONSENT)){openConsentPanel(p);return;}
        if(!p.isCreative()&&!eligible(LockData.get(p.getServer()),p.getUUID())){tag.putBoolean(FinalWitnessBellItem.CONSENT,false);tag.putInt(FinalWitnessBellItem.TOLLS,0);openConsentPanel(p);return;}
        if(p.serverLevel().getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL){WardHud.send(p,"FINAL WITNESS // Master encounters require Easy difficulty or higher.",WardHud.Mood.WARD);return;}
        int before=Math.max(0,Math.min(2,tag.getInt(FinalWitnessBellItem.TOLLS)));int next=before+1;
        tag.putInt(FinalWitnessBellItem.TOLLS,next);pulse(p,next);
        float pitch=next==1?.72f:next==2?.62f:.50f;
        p.serverLevel().playSound(null,p.blockPosition(),SoundEvents.BELL_BLOCK,SoundSource.PLAYERS,1.15f,pitch);
        p.serverLevel().playSound(null,p.blockPosition(),SoundEvents.SOUL_ESCAPE,SoundSource.PLAYERS,.35f,.55f+next*.05f);
        if(next==1)WardHud.send(p,"FIRST TOLL // Identity entered.",WardHud.Mood.WARD);
        else if(next==2)WardHud.send(p,"SECOND TOLL // Presence witnessed.",WardHud.Mood.DANGER);
        else{
            WardHud.send(p,"THIRD TOLL // The prepared court opens elsewhere.",WardHud.Mood.DANGER);
            if(summon(p,p.isCreative())){tag.putBoolean(FinalWitnessBellItem.CONSENT,false);tag.putInt(FinalWitnessBellItem.TOLLS,0);}else tag.putInt(FinalWitnessBellItem.TOLLS,2);
        }
    }

    private static void pulse(ServerPlayer p,int toll){Vec3 v=p.position().add(0,.15,0);var pkt=new NotaryRitualPacket(v.x,v.y,v.z,toll);for(ServerPlayer w:p.serverLevel().players())if(w.distanceToSqr(v)<48*48)Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(()->w),pkt);}
    private static ItemStack heldBell(ServerPlayer p){if(p.getMainHandItem().is(WardItems.FINAL_WITNESS_BELL.get()))return p.getMainHandItem();if(p.getOffhandItem().is(WardItems.FINAL_WITNESS_BELL.get()))return p.getOffhandItem();return ItemStack.EMPTY;}

    /** The prepared court owns its location; the bell never validates the caller's ground. */
    public static boolean summon(ServerPlayer p,boolean debug){
        if(!debug && !MasterInvocationRelics.canInvoke(p, MasterArenaManager.Kind.NOTARY))return false;
        if(!BossConfig.notary().enabled()){WardHud.send(p,"FINAL WITNESS // The Notary is disabled in this world's config.",WardHud.Mood.WARD);return false;}
        var d=LockData.get(p.getServer());var id=p.getUUID();var source=p.serverLevel();
        if(!debug&&!eligible(d,id)){WardHud.send(p,status(p),WardHud.Mood.WARD);return false;}
        if(!debug&&!BossConfig.notary().repeatVictories()&&d.uniqueInt(id,"notary_defeated")>0){WardHud.send(p,"FINAL WITNESS // Your judgment is already entered. No duplicate original will be filed.",WardHud.Mood.WARD);return false;}
        if(source.getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL){WardHud.send(p,"FINAL WITNESS // Master encounters require Easy difficulty or higher. On Peaceful, hostile Master entities are removed before their court can render.",WardHud.Mood.WARD);return false;}
        for(ServerLevel world:p.getServer().getAllLevels())for(var e:world.getAllEntities())if(e instanceof MourningNotaryEntity n&&!n.isRemoved()&&n.includes(id)){WardHud.send(p,"FINAL WITNESS // Another judgment is already in session.",WardHud.Mood.WARD);return false;}
        MasterArenaManager.Arena arena=MasterArenaManager.enter(p,MasterArenaManager.Kind.NOTARY);if(arena==null)return false;
        var level=arena.level();var center=arena.center();
        MourningNotaryEntity boss=WardBosses.MOURNING_NOTARY.get().create(level);if(boss==null){MasterArenaManager.abort(p);return false;}
        boss.moveTo(center.getX()+0.5,center.getY()+0.85,center.getZ()+0.5,0,0);boss.begin(p,center,debug);
        if(!level.addFreshEntity(boss)){MasterArenaManager.abort(p);return false;}
        WardHud.send(p,"THE MOURNING NOTARY // The prepared court has accepted your presence as evidence.",WardHud.Mood.DANGER);return true;
    }

    public static void victory(ServerPlayer p,boolean practice){if(practice)return;MasterArenaManager.beginVictoryLinger(p,MasterArenaManager.Kind.NOTARY);var d=LockData.get(p.getServer());var id=p.getUUID();if(!eligible(d,id)||d.uniqueInt(id,"notary_defeated")>0)return;d.setUniqueInt(id,"notary_defeated",1);dev.marrowseal.wardbound.champion.SilasMasterEncounter.victory(p,dev.marrowseal.wardbound.champion.SilasMasterEncounter.NOTARY);MasterRewardDelivery.queue(p,MasterArenaManager.Kind.NOTARY);WardHistory.recordSpecial(p,"WARD","The Mourning Notary entered a final judgment and was forced to leave the signature line blank. The unsigned sentence followed the witness home.");WardHud.send(p,"FINAL JUDGMENT // The last signature remains yours. The unsigned verdict does not require it.",WardHud.Mood.REWARD);MaestroProgression.unlockIfReady(p);}
}
