package dev.marrowseal.wardbound.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.net.UseCurioIdolPacket;
import dev.marrowseal.wardbound.net.UseWitnessLedgerPacket;
import dev.marrowseal.wardbound.net.CastNinthMarginPacket;

@Mod.EventBusSubscriber(modid = Wardbound.MODID, value = Dist.CLIENT)
public final class WardKeybindHandler {

    private WardKeybindHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        while (WardKeybinds.USE_CTHULHU_IDOL.consumeClick()) {
            Wardbound.CHANNEL.sendToServer(new UseCurioIdolPacket());
        }
        while (WardKeybinds.OPEN_WITNESS_LEDGER.consumeClick()) {
            Wardbound.CHANNEL.sendToServer(new UseWitnessLedgerPacket(true));
        }
        while (WardKeybinds.CAST_NINTH_MARGIN.consumeClick()) {
            Wardbound.CHANNEL.sendToServer(new CastNinthMarginPacket());
        }
    }
}
