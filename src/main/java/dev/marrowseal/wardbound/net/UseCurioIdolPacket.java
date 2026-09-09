
package dev.marrowseal.wardbound.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import dev.marrowseal.wardbound.item.CthulhuIdolItem;

import java.util.function.Supplier;

/** Client asks the server to activate a Cthulhu Idol from Curios or a hand. */
public class UseCurioIdolPacket {

    public static void encode(UseCurioIdolPacket msg, FriendlyByteBuf buf) {
    }

    public static UseCurioIdolPacket decode(FriendlyByteBuf buf) {
        return new UseCurioIdolPacket();
    }

    public static void handle(UseCurioIdolPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) CthulhuIdolItem.tryUseFromHandsOrCurios(player);
        });
        ctx.get().setPacketHandled(true);
    }
}
