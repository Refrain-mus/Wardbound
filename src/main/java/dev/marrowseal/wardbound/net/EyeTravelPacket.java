package dev.marrowseal.wardbound.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import dev.marrowseal.wardbound.item.CthulhuEyeItem;

import java.util.function.Supplier;

/**
 * The destination chosen on the Eye's screen.
 *
 * <p>Client to server, and the only thing it carries is which of three places
 * was picked. Everything else - whether the player still holds the Eye, whether
 * it has a use left, whether that dimension exists - is decided on the server,
 * because a packet that says "put me in the End" is exactly the packet a client
 * would forge.
 */
public class EyeTravelPacket {

    public final int destination;

    public EyeTravelPacket(int destination) {
        this.destination = destination;
    }

    public static void encode(EyeTravelPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.destination);
    }

    public static EyeTravelPacket decode(FriendlyByteBuf buf) {
        return new EyeTravelPacket(buf.readVarInt());
    }

    public static void handle(EyeTravelPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            CthulhuEyeItem.travel(player, msg.destination);
        });
        ctx.get().setPacketHandled(true);
    }
}
