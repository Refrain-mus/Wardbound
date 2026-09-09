package dev.marrowseal.wardbound.net;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import dev.marrowseal.wardbound.client.MasterTelegraphs;
import java.util.*;
import java.util.function.Supplier;

/** Server-owned geometric warnings consumed by the client Lodestone telegraph layer. */
public record MasterCuesPacket(int entityId,double y,List<Cue> cues) {
    /**
     * Floor-card and Master telegraphs can legitimately be ornate enough to exceed the old
     * 96-cue guard. This is a network safety ceiling, not a visual-detail budget.
     *
     * Keeping the ceiling well above current encounter output preserves authored VFX,
     * while the encoder-side clamp makes any future accidental runaway cue generation degrade
     * by truncating presentation rather than disconnecting the client.
     */
    public static final int MAX_CUES = 8192;

    public record Cue(boolean circle,double x,double z,double a,double b,double width,int color) {}

    public static void encode(MasterCuesPacket p,FriendlyByteBuf b){
        b.writeInt(p.entityId);
        b.writeDouble(p.y);
        int n=Math.min(MAX_CUES,p.cues.size());
        b.writeVarInt(n);
        for(int i=0;i<n;i++){
            Cue c=p.cues.get(i);
            b.writeBoolean(c.circle);b.writeDouble(c.x);b.writeDouble(c.z);b.writeDouble(c.a);b.writeDouble(c.b);b.writeDouble(c.width);b.writeInt(c.color);
        }
    }

    public static MasterCuesPacket decode(FriendlyByteBuf b){
        int id=b.readInt();
        double y=b.readDouble();
        int n=b.readVarInt();
        if(n<0 || n>MAX_CUES || !Double.isFinite(y))throw new IllegalArgumentException("Invalid Master cues");
        List<Cue> cues=new ArrayList<>(n);
        for(int i=0;i<n;i++){
            Cue c=new Cue(b.readBoolean(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readInt());
            if(!Double.isFinite(c.x+c.z+c.a+c.b+c.width) || c.width<0 || c.width>4)throw new IllegalArgumentException("Invalid cue geometry");
            cues.add(c);
        }
        return new MasterCuesPacket(id,y,List.copyOf(cues));
    }

    public static void handle(MasterCuesPacket p,Supplier<NetworkEvent.Context> c){c.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->MasterTelegraphs.show(p)));c.get().setPacketHandled(true);}
}
