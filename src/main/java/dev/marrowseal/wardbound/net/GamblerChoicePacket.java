package dev.marrowseal.wardbound.net;

import dev.marrowseal.wardbound.boss.PaleGamblerEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client selects one of the two cards in the Pale Gambler's forced hand. */
public record GamblerChoicePacket(int entityId, int token, int choice) {
    public static void encode(GamblerChoicePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeVarInt(msg.token);
        buf.writeByte(msg.choice);
    }

    public static GamblerChoicePacket decode(FriendlyByteBuf buf) {
        return new GamblerChoicePacket(buf.readVarInt(), buf.readVarInt(), buf.readByte());
    }

    public static void handle(GamblerChoicePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            // Entity ids are only unique inside one level. Scanning every dimension
            // lets a forged/stale packet alias an unrelated Gambler with the same id.
            // A real forced-choice screen always belongs to the level the player is in.
            if (player.serverLevel().getEntity(msg.entityId) instanceof PaleGamblerEntity gambler) {
                gambler.resolveForcedChoice(player, msg.token, msg.choice);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
