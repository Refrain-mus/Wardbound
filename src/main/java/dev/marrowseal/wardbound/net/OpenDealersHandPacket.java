package dev.marrowseal.wardbound.net;
import dev.marrowseal.wardbound.client.ClientHooks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
public record OpenDealersHandPacket(int left,int right,long nonce){
 public static void encode(OpenDealersHandPacket p,FriendlyByteBuf b){b.writeVarInt(p.left);b.writeVarInt(p.right);b.writeLong(p.nonce);}
 public static OpenDealersHandPacket decode(FriendlyByteBuf b){return new OpenDealersHandPacket(b.readVarInt(),b.readVarInt(),b.readLong());}
 public static void handle(OpenDealersHandPacket p,Supplier<NetworkEvent.Context> c){c.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ClientHooks.openDealersHand(p)));c.get().setPacketHandled(true);}
}
