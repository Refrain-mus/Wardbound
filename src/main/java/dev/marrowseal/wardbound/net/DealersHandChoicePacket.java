package dev.marrowseal.wardbound.net;

import dev.marrowseal.wardbound.WardHud;
import dev.marrowseal.wardbound.boss.DealerHandSkills;
import dev.marrowseal.wardbound.boss.DealersHandItem;
import dev.marrowseal.wardbound.item.WardItems;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record DealersHandChoicePacket(int choice,long nonce){
    public static void encode(DealersHandChoicePacket p,FriendlyByteBuf b){b.writeVarInt(p.choice);b.writeLong(p.nonce);}
    public static DealersHandChoicePacket decode(FriendlyByteBuf b){return new DealersHandChoicePacket(b.readVarInt(),b.readLong());}
    public static void handle(DealersHandChoicePacket p,Supplier<NetworkEvent.Context> c){ServerPlayer sp=c.get().getSender();if(sp!=null)c.get().enqueueWork(()->resolve(sp,p));c.get().setPacketHandled(true);}

    private static void resolve(ServerPlayer p,DealersHandChoicePacket msg){
        var root=p.getPersistentData();
        if(!root.contains(DealersHandItem.OFFER_ROOT))return;
        var t=root.getCompound(DealersHandItem.OFFER_ROOT);
        long currentNonce=t.getLong("Nonce");
        // A delayed packet from an older screen must never erase a newer draw.
        if(currentNonce!=msg.nonce())return;
        if(msg.choice()<0||msg.choice()>1)return;
        long now=p.serverLevel().getGameTime();
        if(now>t.getLong("Expiry")){root.remove(DealersHandItem.OFFER_ROOT);return;}

        ItemStack main=p.getMainHandItem(),off=p.getOffhandItem();
        if(!main.is(WardItems.DEALERS_HAND.get())&&!off.is(WardItems.DEALERS_HAND.get())){
            root.remove(DealersHandItem.OFFER_ROOT);
            WardHud.send(p,"DEALER'S HAND // The hand that opened the draw is empty.",WardHud.Mood.GLITCH,1600);
            return;
        }
        int skill=msg.choice()==0?t.getInt("Left"):t.getInt("Right");
        if(skill<0||skill>=DealerHandSkills.COUNT){root.remove(DealersHandItem.OFFER_ROOT);return;}
        // If the chosen attack has no legal target/context, keep the draw so the player can actually use it.
        if(DealerHandSkills.execute(p,skill)){
            root.remove(DealersHandItem.OFFER_ROOT);
            p.getCooldowns().addCooldown(WardItems.DEALERS_HAND.get(),DealersHandItem.COOLDOWN_TICKS);
        }
    }
}
