package dev.marrowseal.wardbound.net;

import dev.marrowseal.wardbound.client.ClientItemActivation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Displays the consumed shard itself using Minecraft's Totem-style activation animation. */
public record ShardActivationPacket(ItemStack stack) {
    public ShardActivationPacket {
        if (stack == null) stack = ItemStack.EMPTY;
    }

    public static void encode(ShardActivationPacket packet, FriendlyByteBuf buf) {
        buf.writeItem(packet.stack);
    }

    public static ShardActivationPacket decode(FriendlyByteBuf buf) {
        return new ShardActivationPacket(buf.readItem());
    }

    public static void handle(ShardActivationPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientItemActivation.show(packet.stack)));
        ctx.get().setPacketHandled(true);
    }
}
