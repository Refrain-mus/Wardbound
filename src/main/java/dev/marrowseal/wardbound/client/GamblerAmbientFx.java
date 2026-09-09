package dev.marrowseal.wardbound.client;

import dev.marrowseal.wardbound.WardConfig;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.boss.PaleGamblerEntity;
import dev.marrowseal.wardbound.item.WardItems;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Keeps The Pale Gambler visually alive even while he is between attack impacts. */
@Mod.EventBusSubscriber(modid=Wardbound.MODID,value=Dist.CLIENT)
public final class GamblerAmbientFx {
    private static int ticks;
    private GamblerAmbientFx() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null || mc.player==null)return;
        ticks++;
        int cadence=WardConfig.accessibilityReduceMotion?4:1;
        if(ticks%cadence!=0)return;
        for(PaleGamblerEntity boss:mc.level.getEntitiesOfClass(PaleGamblerEntity.class,mc.player.getBoundingBox().inflate(72))){
            GamblerCinematicFx.ambientBoss(boss,ticks);
        }
        if(mc.player.getMainHandItem().is(WardItems.HOUSEBREAKERS_ACE.get()) || mc.player.getOffhandItem().is(WardItems.HOUSEBREAKERS_ACE.get())){
            GamblerCinematicFx.ambientAce(mc.player,ticks);
        }
    }
}
