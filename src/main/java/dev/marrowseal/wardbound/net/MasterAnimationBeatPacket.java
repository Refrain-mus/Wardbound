package dev.marrowseal.wardbound.net;

import dev.marrowseal.wardbound.client.MasterMotionFeel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * A tiny server-authored animation beat. The gameplay release tick is the source of truth;
 * clients use the same beat for hit-stop, recoil, camera kick and Lodestone impact volume.
 */
public record MasterAnimationBeatPacket(int entityId,int style,int phase,float strength,int seed) {
    public static final int CLEAR=0, ANTICIPATE=1, RELEASE=2;
    public static final int GAMBLER=0, CURATOR=1, NOTARY=2;

    public static void encode(MasterAnimationBeatPacket p, FriendlyByteBuf b){
        b.writeVarInt(p.entityId);b.writeByte(p.style);b.writeByte(p.phase);b.writeFloat(p.strength);b.writeInt(p.seed);
    }
    public static MasterAnimationBeatPacket decode(FriendlyByteBuf b){
        int id=b.readVarInt(),style=b.readByte(),phase=b.readByte();float strength=b.readFloat();int seed=b.readInt();
        if(style<0||style>2||phase<0||phase>2||!Float.isFinite(strength))return new MasterAnimationBeatPacket(id,GAMBLER,CLEAR,0,seed);
        return new MasterAnimationBeatPacket(id,style,phase,Math.max(0,Math.min(2.5f,strength)),seed);
    }
    public static void handle(MasterAnimationBeatPacket p, Supplier<NetworkEvent.Context> c){
        c.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->MasterMotionFeel.accept(p)));
        c.get().setPacketHandled(true);
    }
}
