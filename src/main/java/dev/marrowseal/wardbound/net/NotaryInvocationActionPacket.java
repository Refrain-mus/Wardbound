package dev.marrowseal.wardbound.net;

import dev.marrowseal.wardbound.boss.NotaryProgression;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** 0 accept service, 1 withdraw/reset attestation. */
public record NotaryInvocationActionPacket(int action) {
    public static void encode(NotaryInvocationActionPacket p,FriendlyByteBuf b){b.writeVarInt(p.action);}
    public static NotaryInvocationActionPacket decode(FriendlyByteBuf b){return new NotaryInvocationActionPacket(Math.max(0,Math.min(1,b.readVarInt())));}
    public static void handle(NotaryInvocationActionPacket p,Supplier<NetworkEvent.Context> c){
        c.get().enqueueWork(()->{ServerPlayer player=c.get().getSender();if(player!=null)NotaryProgression.handleConsentAction(player,p.action);});c.get().setPacketHandled(true);
    }
}
