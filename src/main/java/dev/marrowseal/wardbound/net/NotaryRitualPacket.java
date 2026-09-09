package dev.marrowseal.wardbound.net;

import dev.marrowseal.wardbound.client.MourningNotaryTextureFx;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Client ritual pulse for the three witness tolls. */
public record NotaryRitualPacket(double x,double y,double z,int toll) {
    public static void encode(NotaryRitualPacket p,FriendlyByteBuf b){b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);b.writeVarInt(p.toll);}
    public static NotaryRitualPacket decode(FriendlyByteBuf b){return new NotaryRitualPacket(b.readDouble(),b.readDouble(),b.readDouble(),Math.max(1,Math.min(3,b.readVarInt())));}
    public static void handle(NotaryRitualPacket p,Supplier<NetworkEvent.Context> c){
        c.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->MourningNotaryTextureFx.ritualPulse(p)));c.get().setPacketHandled(true);
    }
}
