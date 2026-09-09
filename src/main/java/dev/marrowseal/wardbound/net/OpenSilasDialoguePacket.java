package dev.marrowseal.wardbound.net;

import dev.marrowseal.wardbound.client.SilasDialogueScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record OpenSilasDialoguePacket(String key, int stage) {
    public static void encode(OpenSilasDialoguePacket p, FriendlyByteBuf b) {
        b.writeUtf(p.key(), 256);
        b.writeVarInt(p.stage());
    }

    public static OpenSilasDialoguePacket decode(FriendlyByteBuf b) {
        return new OpenSilasDialoguePacket(b.readUtf(256), Math.max(1, Math.min(5, b.readVarInt())));
    }

    public static void handle(OpenSilasDialoguePacket p, Supplier<NetworkEvent.Context> c) {
        c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SilasDialogueScreen.open(p)));
        c.get().setPacketHandled(true);
    }
}
