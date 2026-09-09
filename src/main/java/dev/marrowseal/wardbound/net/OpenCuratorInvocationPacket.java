package dev.marrowseal.wardbound.net;

import dev.marrowseal.wardbound.client.CuratorInvocationScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Opens the Ashen Archive Codex without committing the encounter. */
public record OpenCuratorInvocationPacket(boolean evidenceReady, int recordChoice, String status) {
    public static void encode(OpenCuratorInvocationPacket p,FriendlyByteBuf b){
        b.writeBoolean(p.evidenceReady);b.writeVarInt(p.recordChoice);b.writeUtf(p.status,384);
    }
    public static OpenCuratorInvocationPacket decode(FriendlyByteBuf b){
        return new OpenCuratorInvocationPacket(b.readBoolean(),Math.max(0,Math.min(2,b.readVarInt())),b.readUtf(384));
    }
    public static void handle(OpenCuratorInvocationPacket p,Supplier<NetworkEvent.Context> c){
        c.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->CuratorInvocationScreen.open(p)));c.get().setPacketHandled(true);
    }
}
