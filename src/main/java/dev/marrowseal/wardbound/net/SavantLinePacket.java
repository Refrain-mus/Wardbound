package dev.marrowseal.wardbound.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import dev.marrowseal.wardbound.client.ClientSavantOverlay;

import java.util.function.Supplier;

/** Delivers one Savant utterance to the dedicated HUD overlay. */
public class SavantLinePacket {

    public final String line;

    public SavantLinePacket(String line) {
        this.line = line == null ? "" : line;
    }

    public static void encode(SavantLinePacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.line, 512);
    }

    public static SavantLinePacket decode(FriendlyByteBuf buf) {
        return new SavantLinePacket(buf.readUtf(512));
    }

    public static void handle(SavantLinePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSavantOverlay.show(msg.line)));
        ctx.get().setPacketHandled(true);
    }
}
