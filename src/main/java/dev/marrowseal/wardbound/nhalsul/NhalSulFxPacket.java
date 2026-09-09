package dev.marrowseal.wardbound.nhalsul;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
public record NhalSulFxPacket(double x,double y,double z,double dx,double dy,double dz,float reach,int phase){
    public static void encode(NhalSulFxPacket p,FriendlyByteBuf b){b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);b.writeDouble(p.dx);b.writeDouble(p.dy);b.writeDouble(p.dz);b.writeFloat(p.reach);b.writeByte(p.phase);}
    public static NhalSulFxPacket decode(FriendlyByteBuf b){return new NhalSulFxPacket(b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readFloat(),b.readByte());}
    public static void handle(NhalSulFxPacket p,Supplier<NetworkEvent.Context> c){c.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->NhalSulFx.accept(p)));c.get().setPacketHandled(true);}
}
