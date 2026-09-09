package dev.marrowseal.wardbound.net;

import dev.marrowseal.wardbound.client.SilasFragmentScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record OpenSilasFragmentPacket(String fragmentId, int family) {
    public static void encode(OpenSilasFragmentPacket p, FriendlyByteBuf b) {
        b.writeUtf(p.fragmentId(), 128);
        b.writeVarInt(p.family());
    }

    public static OpenSilasFragmentPacket decode(FriendlyByteBuf b) {
        return new OpenSilasFragmentPacket(b.readUtf(128), Math.max(0, Math.min(4, b.readVarInt())));
    }

    public static void handle(OpenSilasFragmentPacket p, Supplier<NetworkEvent.Context> c) {
        c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SilasFragmentScreen.open(p)));
        c.get().setPacketHandled(true);
    }
}
