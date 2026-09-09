package dev.marrowseal.wardbound.client;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.boss.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid=Wardbound.MODID,value=Dist.CLIENT)
public final class CuratorBossThemePlayer {
    private static Theme active;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;Minecraft mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null){if(active!=null){mc.getSoundManager().stop(active);active=null;}return;}
        AshenCuratorEntity selected=null;
        if(mc.player.isAlive()&&!mc.player.isSpectator())for(var ent:mc.level.entitiesForRendering())if(ent instanceof AshenCuratorEntity b && !b.isRemoved() && b.view().getInt("state")!=8 && b.clientParticipant(mc.player.getUUID()) && b.distanceToSqr(mc.player)<=1600 && b.view().getBoolean("theme")){selected=b;break;}
        if(active!=null){
            if(selected!=active.boss || active.boss.level()!=mc.level)active.stopping=true;
            else active.stopping=false;
        }
        if(active!=null && active.isStopped())active=null;
        if(active!=null && !mc.isPaused() && ++active.observedTicks>20 && !mc.getSoundManager().isActive(active)){mc.getSoundManager().stop(active);active=null;}
        // Finish the outgoing fade before starting a different archive. At most one sound owned here.
        if(active==null && selected!=null){active=new Theme(selected);mc.getSoundManager().play(active);}
    }
    private static final class Theme extends AbstractTickableSoundInstance {
        final AshenCuratorEntity boss;boolean stopping;float gain;int observedTicks;
        Theme(AshenCuratorEntity b){super(CuratorSounds.get("boss_theme"),SoundSource.MUSIC,RandomSource.create());boss=b;looping=true;delay=0;relative=true;attenuation=SoundInstance.Attenuation.NONE;volume=0;}
        @Override public boolean canStartSilent(){return true;}
        @Override public void tick(){
            Minecraft mc=Minecraft.getInstance();var v=boss.view();
            if(boss.isRemoved() || v.getInt("state")==8 || mc.player==null || mc.level!=boss.level() || !mc.player.isAlive() || mc.player.isSpectator() || boss.distanceToSqr(mc.player)>1600 || !boss.clientParticipant(mc.player.getUUID()))stopping=true;
            gain=stopping?Math.max(0,gain-1f/Math.max(1,v.getInt("fadeOut"))):Math.min(1,gain+1f/Math.max(1,v.getInt("fadeIn")));
            volume=v.getFloat("volume")*gain;if(stopping && gain<=0)stop();
        }
    }
}
