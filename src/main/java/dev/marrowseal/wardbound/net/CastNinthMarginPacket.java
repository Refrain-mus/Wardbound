package dev.marrowseal.wardbound.net;

import dev.marrowseal.wardbound.FourthWaveCardEffects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client intent only; every spell-slot precondition is revalidated server-side. */
public final class CastNinthMarginPacket {
    public static void encode(CastNinthMarginPacket msg, FriendlyByteBuf buf) { }
    public static CastNinthMarginPacket decode(FriendlyByteBuf buf) { return new CastNinthMarginPacket(); }
    public static void handle(CastNinthMarginPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) FourthWaveCardEffects.castNinthMargin(player);
        });
        ctx.get().setPacketHandled(true);
    }
}
