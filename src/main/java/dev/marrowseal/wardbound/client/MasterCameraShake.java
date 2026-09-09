package dev.marrowseal.wardbound.client;

import dev.marrowseal.wardbound.WardConfig;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.net.MasterAnimationBeatPacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Short, accessibility-aware camera impulses for Master encounter impact beats. */
@Mod.EventBusSubscriber(modid=Wardbound.MODID,value=Dist.CLIENT)
public final class MasterCameraShake {
    private static int ticks;
    private static int total;
    private static float strength;
    private static float frequency;
    private static long seed;
    private static int style = MasterAnimationBeatPacket.GAMBLER;

    private MasterCameraShake(){}

    public static void pulse(float amount,int durationTicks,float hz,long salt,int styleId){
        if(WardConfig.accessibilityDisableShake || WardConfig.accessibilityReduceMotion || amount<=0 || durationTicks<=0)return;
        if(durationTicks>ticks || amount>strength){
            ticks=Math.max(ticks,durationTicks);
            total=Math.max(total,durationTicks);
            strength=Math.max(strength,amount);
            frequency=Math.max(0.35f,hz);
            seed=salt;
            style=styleId;
        }
    }

    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles e){
        if(ticks<=0 || WardConfig.accessibilityDisableShake || WardConfig.accessibilityReduceMotion){ticks=0;strength=0;return;}
        float partial=(float)e.getPartialTick();
        float elapsed=(total-ticks)+partial;
        float life=Math.max(0f,Math.min(1f,(ticks-partial)/(float)Math.max(1,total)));
        float envelope=life*life*(3f-2f*life);
        double t=(elapsed+seed*.013)*frequency;
        float yaw,pitch,roll;
        switch(style){
            case MasterAnimationBeatPacket.CURATOR -> {
                // Heavy, low-frequency archive rumble.
                yaw=(float)Math.sin(t*.55+.25f)*.22f*strength*envelope;
                pitch=(float)(Math.sin(t*.92)+Math.cos(t*.48+1.1))*.38f*strength*envelope;
                roll=(float)Math.sin(t*.66+2.0)*.10f*strength*envelope;
            }
            case MasterAnimationBeatPacket.NOTARY -> {
                // Rhythmic radial kick, like a struck bell throwing pressure rings.
                float ring=(float)Math.abs(Math.sin(t*1.08));
                yaw=(float)Math.sin(t*1.08)*.18f*strength*envelope*(.65f+ring);
                pitch=(float)Math.cos(t*1.08+.2f)*.16f*strength*envelope*(.55f+ring);
                roll=(float)Math.sin(t*2.16+.4f)*.14f*strength*envelope*(.55f+ring);
            }
            default -> {
                // Short lateral casino snap.
                yaw=(float)(Math.sin(t*2.35)+Math.sin(t*.84+1.4))*.42f*strength*envelope;
                pitch=(float)(Math.cos(t*1.18+.28)+Math.sin(t*.96))*.18f*strength*envelope;
                roll=(float)Math.sin(t*2.95+.8)*.26f*strength*envelope;
            }
        }
        e.setYaw(e.getYaw()+yaw);
        e.setPitch(e.getPitch()+pitch);
        e.setRoll(e.getRoll()+roll);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END || ticks<=0)return;
        if(--ticks<=0){ticks=0;total=0;strength=0;style=MasterAnimationBeatPacket.GAMBLER;}
    }
}
