package dev.marrowseal.wardbound.net;

import dev.marrowseal.wardbound.client.ClientHooks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Opens Housebreaker's Ace and tells the client which permanent laws are already signed. */
public record OpenAceChoicePacket(int remainingUses, boolean wardLawSigned, boolean worldLawSigned) {
    public static void encode(OpenAceChoicePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(Math.max(0, Math.min(2, msg.remainingUses)));
        buf.writeBoolean(msg.wardLawSigned);
        buf.writeBoolean(msg.worldLawSigned);
    }

    public static OpenAceChoicePacket decode(FriendlyByteBuf buf) {
        return new OpenAceChoicePacket(Math.max(0, Math.min(2, buf.readVarInt())), buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(OpenAceChoicePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientHooks.openAceChoice(msg)));
        ctx.get().setPacketHandled(true);
    }
}
