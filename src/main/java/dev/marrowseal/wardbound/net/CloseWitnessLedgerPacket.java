package dev.marrowseal.wardbound.net;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import dev.marrowseal.wardbound.item.WitnessLedgerItem;

import java.util.function.Supplier;

/** Client tells the server the Ledger UI closed so the held GeoItem can play its closing motion. */
public class CloseWitnessLedgerPacket {
    public static void encode(CloseWitnessLedgerPacket msg, net.minecraft.network.FriendlyByteBuf buf) { }
    public static CloseWitnessLedgerPacket decode(net.minecraft.network.FriendlyByteBuf buf) { return new CloseWitnessLedgerPacket(); }
    public static void handle(CloseWitnessLedgerPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) WitnessLedgerItem.triggerClose(player);
        });
        ctx.get().setPacketHandled(true);
    }
}
