package dev.marrowseal.wardbound.client;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.champion.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.sounds.*;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Optional streamed resource-pack music. Empty hooks stay silent and do not log missing assets. */
@Mod.EventBusSubscriber(modid=Wardbound.MODID,value=Dist.CLIENT)
public final class ChampionMusic {
    private static Theme active;private static ClientLevel world;private static long testimonyUntil;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        if(mc.level!=world){if(active!=null)mc.getSoundManager().stop(active);active=null;world=mc.level;testimonyUntil=0;}
        if(world==null||mc.player==null||mc.isPaused())return;
        DefeatedChampionEntity nearest=null;double dist=24*24;
        for(var entity:world.entitiesForRendering())if(entity instanceof DefeatedChampionEntity c&&c.isAlive()&&c.distanceToSqr(mc.player)<dist){nearest=c;dist=c.distanceToSqr(mc.player);}
        if(nearest!=null&&(nearest.action()==DefeatedChampionEntity.TALK||nearest.action()==DefeatedChampionEntity.SEATED_TALK||mc.screen instanceof SilasDialogueScreen))testimonyUntil=world.getGameTime()+600;
        boolean quiet=nearest!=null&&nearest.relaxed();
        SoundEvent selected=world.getGameTime()<testimonyUntil?ChampionSounds.TESTIMONY.get():ChampionSounds.PRESENCE.get();
        if(active!=null&&(!quiet||active.boss!=nearest||active.event!=selected))active.leaving=true;
        if(active!=null&&active.isStopped())active=null;
        if(active!=null&&++active.observedTicks>20&&!mc.getSoundManager().isActive(active)){mc.getSoundManager().stop(active);active=null;}
        if(active==null&&quiet&&world.getGameTime()%20==0){
            var definition=mc.getSoundManager().getSoundEvent(selected.getLocation());
            if(definition!=null&&definition.getWeight()>0){active=new Theme(nearest,selected);mc.getSoundManager().play(active);}
        }
    }
    private static final class Theme extends AbstractTickableSoundInstance {
        final DefeatedChampionEntity boss;final SoundEvent event;boolean leaving;int observedTicks;
        Theme(DefeatedChampionEntity boss,SoundEvent event){super(event,SoundSource.MUSIC,RandomSource.create());this.boss=boss;this.event=event;looping=true;relative=true;attenuation=SoundInstance.Attenuation.NONE;volume=0;}
        @Override public boolean canStartSilent(){return true;}
        @Override public void tick(){var mc=Minecraft.getInstance();
            if(mc.player==null||boss.isRemoved()||!boss.isAlive()||mc.level!=boss.level()){stop();return;}
            float target=leaving?0:.55f*(float)Math.max(0,Math.min(1,(24-boss.distanceTo(mc.player))/12));
            volume+=Math.max(-.015f,Math.min(.01f,target-volume));if(leaving&&volume<=.001f)stop();
        }
    }
}
