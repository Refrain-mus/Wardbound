package dev.marrowseal.wardbound.boss;
import dev.marrowseal.wardbound.*;
import dev.marrowseal.wardbound.item.WardItems;
import dev.marrowseal.wardbound.net.OpenCuratorInvocationPacket;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

public final class CuratorProgression {
    public static int curseRevisions(LockData d,UUID id){return CurseEvolution.knownCount(d,id);}
    /** Debug/admin wording only. The writ itself remains diegetic. */
    public static String requirements(LockData d,UUID id){var s=BossConfig.curator();return "Curator chapter "+MasterStory.chapter(d,id,CardMaster.ASHEN_CURATOR)+"/"+s.storyChapter()+" | Ash in the Margin "+OccultChainEvents.stage(d,id,OccultChainEvents.ChainEvent.ASH_MARGIN)+"/"+s.chainStage()+" | evolved curses "+curseRevisions(d,id)+"/"+s.revisedCards()+" | record: "+(d.uniqueInt(id,"curator_record_choice")==0?"undecided":d.uniqueInt(id,"curator_record_choice")==1?"preserved":"burned");}
    public static boolean evidence(LockData d,UUID id){var s=BossConfig.curator();return MasterStory.chapter(d,id,CardMaster.ASHEN_CURATOR)>=s.storyChapter() && OccultChainEvents.stage(d,id,OccultChainEvents.ChainEvent.ASH_MARGIN)>=s.chainStage() && curseRevisions(d,id)>=s.revisedCards();}
    public static boolean eligible(LockData d,UUID id){return BossConfig.curator().enabled() && evidence(d,id) && d.uniqueInt(id,"curator_record_choice")!=0;}
    public static String status(ServerPlayer p){var d=LockData.get(p.getServer());var id=p.getUUID();var s=BossConfig.curator();return "Curator chapter "+MasterStory.chapter(d,id,CardMaster.ASHEN_CURATOR)+"/"+s.storyChapter()+" | Ash in the Margin "+OccultChainEvents.stage(d,id,OccultChainEvents.ChainEvent.ASH_MARGIN)+"/"+s.chainStage()+" | evolved curses "+curseRevisions(d,id)+"/"+s.revisedCards()+" | record: "+(d.uniqueInt(id,"curator_record_choice")==0?"undecided (use writ)":d.uniqueInt(id,"curator_record_choice")==1?"preserved":"destroyed")+" | defeated: "+d.uniqueInt(id,"curator_defeated");}
    public static void giveWrit(ServerPlayer p){ItemStack item=new ItemStack(WardItems.ARCHIVE_WRIT.get());if(!p.getInventory().contains(item) && !p.getInventory().add(item))p.drop(item,false);if(p.getServer()!=null)LockData.get(p.getServer()).setUniqueInt(p.getUUID(),"curator_writ_issued",1);}
    public static void openInvocationPanel(ServerPlayer p){
        if(p==null||p.getServer()==null)return;
        var d=LockData.get(p.getServer());var id=p.getUUID();
        boolean evidenceReady=p.isCreative()||evidence(d,id);
        int choice=d.uniqueInt(id,"curator_record_choice");
        String text;
        if(!evidenceReady) text="The codex opens to blank paper. The Ash in the Margin and the Curator's surviving evidence are still incomplete.";
        else if(choice==0) text="One returned testimony remains unfiled. Preservation keeps it admissible. Burning it makes the absence itself admissible.";
        else if(choice==1) text="The preserved testimony has been indexed. The archive may now manifest around the reader.";
        else text="The testimony has been burned. The empty place where it should have been has been indexed instead.";
        Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new OpenCuratorInvocationPacket(evidenceReady,choice,text));
    }

    public static void handleInvocationAction(ServerPlayer p,int action){
        if(p==null||p.getServer()==null)return;
        if(!p.getMainHandItem().is(WardItems.ARCHIVE_WRIT.get())&&!p.getOffhandItem().is(WardItems.ARCHIVE_WRIT.get()))return;
        var d=LockData.get(p.getServer());var id=p.getUUID();
        if(action==0||action==1){
            if((!p.isCreative()&&!evidence(d,id))||d.uniqueInt(id,"curator_record_choice")!=0){openInvocationPanel(p);return;}
            int choice=action==0?1:2;d.setUniqueInt(id,"curator_record_choice",choice);
            WardHistory.recordSpecial(p,"WARD",choice==1?"Preserved the Curator's returned testimony.":"Destroyed the Curator's returned testimony.");
            WardHud.send(p,choice==1?"ASH ARCHIVE // The testimony is preserved. The codex has made room for a living shelf.":"ASH ARCHIVE // The testimony is burned. The codex has indexed the missing page.",choice==1?WardHud.Mood.REWARD:WardHud.Mood.DANGER);
            openInvocationPanel(p);return;
        }
        if(action==2){
            if(d.uniqueInt(id,"curator_record_choice")==0){openInvocationPanel(p);return;}
            summon(p,p.isCreative());
        }
    }

    /** The prepared Archive owns its arena coordinates; invocation never samples the caller world. */
    public static boolean summon(ServerPlayer p,boolean debug){
        if(!debug && !MasterInvocationRelics.canInvoke(p, MasterArenaManager.Kind.CURATOR))return false;
        var d=LockData.get(p.getServer());var id=p.getUUID();var source=p.serverLevel();
        if(!BossConfig.curator().enabled()){message(p,"The archive is disabled in this world's config.");return false;}
        if(!debug && (!eligible(d,id)||(!BossConfig.curator().repeatVictories()&&d.uniqueInt(id,"curator_defeated")>0))){message(p,status(p));return false;}
        if(source.getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL){message(p,"Master encounters require Easy difficulty or higher.");return false;}
        for(ServerLevel world:p.getServer().getAllLevels())for(var e:world.getAllEntities())if(e instanceof AshenCuratorEntity boss && !boss.isRemoved() && boss.includes(id)){message(p,"An archive is already open for you.");return false;}
        MasterArenaManager.Arena arena=MasterArenaManager.enter(p,MasterArenaManager.Kind.CURATOR);if(arena==null)return false;
        var l=arena.level();var center=arena.center();
        AshenCuratorEntity boss=WardBosses.ASHEN_CURATOR.get().create(l);if(boss==null){MasterArenaManager.abort(p);return false;}
        boss.moveTo(Vec3.atBottomCenterOf(center));boss.begin(p,center,debug);
        if(!l.addFreshEntity(boss)){MasterArenaManager.abort(p);return false;}
        message(p,debug?"Practice archive: progression rewards disabled.":d.uniqueInt(id,"curator_record_choice")==1?"The preserved witness has been brought to the prepared archive.":"The missing witness has been brought to the prepared archive.");return true;
    }
    public static void message(ServerPlayer p,String text){p.displayClientMessage(Component.literal("ASH ARCHIVE // "+text),false);}
    public static void victory(ServerPlayer p,boolean practice){
        if(practice)return;
        MasterArenaManager.beginVictoryLinger(p, MasterArenaManager.Kind.CURATOR);
        var d=LockData.get(p.getServer());var id=p.getUUID();
        // A guest's victory never manufactures their missing story evidence.
        if(!eligible(d,id)||d.uniqueInt(id,"curator_defeated")>0)return;
        d.setUniqueInt(id,"curator_defeated",1);dev.marrowseal.wardbound.champion.SilasMasterEncounter.victory(p,dev.marrowseal.wardbound.champion.SilasMasterEncounter.CURATOR);
        MasterRewardDelivery.queue(p, MasterArenaManager.Kind.CURATOR);WardHistory.recordSpecial(p,"WARD","The Ashen Curator's index was broken. The Last Margin now archives a wound only when deliberately opened, then returns the edited harm as ash.");
        message(p,"THE LAST MARGIN // Open the blank space before the wound. What is preserved will answer back.");
        MaestroProgression.unlockIfReady(p);
    }
}
