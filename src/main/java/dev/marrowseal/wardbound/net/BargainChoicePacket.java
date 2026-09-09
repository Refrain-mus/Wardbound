package dev.marrowseal.wardbound.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import dev.marrowseal.wardbound.ChestValuator;

import java.util.function.Supplier;

/** Client chooses one offered debt, or -1 to refuse all of them. */
public class BargainChoicePacket {

    public final BlockPos pos;
    public final int bargainId;
    public final int branch;

    public BargainChoicePacket(BlockPos pos, int bargainId) {
        this(pos, bargainId, 0);
    }

    public BargainChoicePacket(BlockPos pos, int bargainId, int branch) {
        this.branch = branch;
        this.pos = pos;
        this.bargainId = bargainId;
    }

    public static void encode(BargainChoicePacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeVarInt(msg.bargainId + 1);
        buf.writeVarInt(msg.branch); // -1 remains representable as zero
    }

    public static BargainChoicePacket decode(FriendlyByteBuf buf) {
        return new BargainChoicePacket(buf.readBlockPos(), buf.readVarInt() - 1, buf.readVarInt());
    }

    public static void handle(BargainChoicePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            ChestValuator.resolveBargainChoice(player, msg.pos, msg.bargainId, msg.branch);
        });
        ctx.get().setPacketHandled(true);
    }
}
