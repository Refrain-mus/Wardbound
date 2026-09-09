package dev.marrowseal.wardbound.net;

import dev.marrowseal.wardbound.client.ClientHooks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server opens the Pale Gambler's forced hand: two cards drawn from a three-card pool. */
public record OpenGamblerChoicePacket(int entityId, int token, int phase, int durationTicks, int leftCard, int rightCard) {
    public static void encode(OpenGamblerChoicePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeVarInt(msg.token);
        buf.writeVarInt(msg.phase);
        buf.writeVarInt(msg.durationTicks);
        buf.writeByte(msg.leftCard);
        buf.writeByte(msg.rightCard);
    }

    public static OpenGamblerChoicePacket decode(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        int token = buf.readVarInt();
        int phase = Math.max(1, Math.min(3, buf.readVarInt()));
        int duration = Math.max(20, Math.min(20 * 30, buf.readVarInt()));
        int leftCard = Math.max(0, Math.min(2, buf.readByte()));
        int rightCard = Math.max(0, Math.min(2, buf.readByte()));
        if (rightCard == leftCard) rightCard = (leftCard + 1) % 3;
        return new OpenGamblerChoicePacket(entityId, token, phase, duration, leftCard, rightCard);
    }

    public static void handle(OpenGamblerChoicePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openGamblerChoice(msg)));
        ctx.get().setPacketHandled(true);
    }
}
