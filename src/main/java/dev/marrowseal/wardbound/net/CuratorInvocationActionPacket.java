package dev.marrowseal.wardbound.net;

import dev.marrowseal.wardbound.boss.CuratorProgression;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** 0 preserve testimony, 1 burn testimony, 2 enter the prepared archive. */
public record CuratorInvocationActionPacket(int action) {
    public static void encode(CuratorInvocationActionPacket p,FriendlyByteBuf b){b.writeVarInt(p.action);}
    public static CuratorInvocationActionPacket decode(FriendlyByteBuf b){return new CuratorInvocationActionPacket(Math.max(0,Math.min(2,b.readVarInt())));}
    public static void handle(CuratorInvocationActionPacket p,Supplier<NetworkEvent.Context> c){
        c.get().enqueueWork(()->{ServerPlayer player=c.get().getSender();if(player!=null)CuratorProgression.handleInvocationAction(player,p.action);});c.get().setPacketHandled(true);
    }
}
