package dev.marrowseal.wardbound.net;

import dev.marrowseal.wardbound.client.NotaryInvocationScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Consent panel for the Bell of Final Witness. */
public record OpenNotaryInvocationPacket(boolean eligible, String status) {
    public static void encode(OpenNotaryInvocationPacket p,FriendlyByteBuf b){b.writeBoolean(p.eligible);b.writeUtf(p.status,384);}
    public static OpenNotaryInvocationPacket decode(FriendlyByteBuf b){return new OpenNotaryInvocationPacket(b.readBoolean(),b.readUtf(384));}
    public static void handle(OpenNotaryInvocationPacket p,Supplier<NetworkEvent.Context> c){
        c.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->NotaryInvocationScreen.open(p)));c.get().setPacketHandled(true);
    }
}
