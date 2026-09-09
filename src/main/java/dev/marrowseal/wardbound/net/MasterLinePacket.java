package dev.marrowseal.wardbound.net;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import dev.marrowseal.wardbound.client.ClientSavantOverlay;
import java.util.function.Supplier;
/** Master dialogue with a server-authored display window so subtitles can follow the animation beat. */
public record MasterLinePacket(String speaker,String line,int durationMs) {
    public static final int DEFAULT_DURATION_MS=5600;
    public MasterLinePacket(String speaker,String line){this(speaker,line,DEFAULT_DURATION_MS);}
    public MasterLinePacket{
        if(speaker==null)speaker="THE SAVANT // OBSERVING";
        if(line==null)line="";
        durationMs=Math.max(1800,Math.min(9000,durationMs));
    }
    public static void encode(MasterLinePacket p,FriendlyByteBuf b){b.writeUtf(p.speaker,80);b.writeUtf(p.line,512);b.writeVarInt(p.durationMs);}
    public static MasterLinePacket decode(FriendlyByteBuf b){return new MasterLinePacket(b.readUtf(80),b.readUtf(512),b.readVarInt());}
    public static void handle(MasterLinePacket p,Supplier<NetworkEvent.Context> c){c.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ClientSavantOverlay.showMaster(p.speaker,p.line,p.durationMs)));c.get().setPacketHandled(true);}
}
