package dev.marrowseal.wardbound.ancientsmith;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
public record SmithFxPacket(int entity,int event,double x,double y,double z){
 public static void encode(SmithFxPacket p,FriendlyByteBuf b){b.writeVarInt(p.entity);b.writeByte(p.event);b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);}
 public static SmithFxPacket decode(FriendlyByteBuf b){return new SmithFxPacket(b.readVarInt(),b.readUnsignedByte(),b.readDouble(),b.readDouble(),b.readDouble());}
 public static void handle(SmithFxPacket p,Supplier<NetworkEvent.Context> c){c.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->SmithClientFx.accept(p)));c.get().setPacketHandled(true);}
}
