package dev.marrowseal.wardbound.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import dev.marrowseal.wardbound.item.WitnessLedgerItem;

import java.util.function.Supplier;

/** Client asks the server to open or refresh a Witness Ledger from hand, inventory or Curios. */
public class UseWitnessLedgerPacket {
    private final boolean animate;

    public UseWitnessLedgerPacket() {
        this(true);
    }

    public UseWitnessLedgerPacket(boolean animate) {
        this.animate = animate;
    }

    public static void encode(UseWitnessLedgerPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.animate);
    }

    public static UseWitnessLedgerPacket decode(FriendlyByteBuf buf) {
        return new UseWitnessLedgerPacket(buf.readBoolean());
    }

    public static void handle(UseWitnessLedgerPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) WitnessLedgerItem.tryOpenFromHandsInventoryOrCurios(player, msg.animate);
        });
        ctx.get().setPacketHandled(true);
    }
}
