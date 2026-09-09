package dev.marrowseal.wardbound.net;

import dev.marrowseal.wardbound.client.MasterDamageReactions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server-authored visual damage response for Master bosses.
 * This never interrupts gameplay state; it only layers a gated recoil/stagger pose on the client.
 */
public record MasterDamageReactionPacket(int entityId,int style,int kind,float strength,int seed) {
    public static final int LIGHT=0, HEAVY=1, VULNERABLE=2;
    public static final int GAMBLER=0, CURATOR=1, NOTARY=2;

    public static void encode(MasterDamageReactionPacket p, FriendlyByteBuf b){
        b.writeVarInt(p.entityId);b.writeByte(p.style);b.writeByte(p.kind);b.writeFloat(p.strength);b.writeInt(p.seed);
    }
    public static MasterDamageReactionPacket decode(FriendlyByteBuf b){
        int id=b.readVarInt(),style=b.readByte(),kind=b.readByte();float strength=b.readFloat();int seed=b.readInt();
        if(style<0||style>2||kind<0||kind>2||!Float.isFinite(strength))return new MasterDamageReactionPacket(id,GAMBLER,LIGHT,0,seed);
        return new MasterDamageReactionPacket(id,style,kind,Math.max(0,Math.min(2.5f,strength)),seed);
    }
    public static void handle(MasterDamageReactionPacket p, Supplier<NetworkEvent.Context> c){
        c.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->MasterDamageReactions.accept(p)));
        c.get().setPacketHandled(true);
    }
}
