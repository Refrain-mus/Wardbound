
package dev.marrowseal.wardbound.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import dev.marrowseal.wardbound.client.CthulhuIdolFieldFx;

import java.util.function.Supplier;

/** Server tells nearby clients to render the temporary freeze field created by the Cthulhu Idol. */
public class CthulhuFieldPacket {

    public final double x;
    public final double y;
    public final double z;
    public final float radius;
    public final int durationTicks;

    public CthulhuFieldPacket(double x, double y, double z, float radius, int durationTicks) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
        this.durationTicks = durationTicks;
    }

    public static void encode(CthulhuFieldPacket msg, FriendlyByteBuf buf) {
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
        buf.writeFloat(msg.radius);
        buf.writeVarInt(msg.durationTicks);
    }

    public static CthulhuFieldPacket decode(FriendlyByteBuf buf) {
        return new CthulhuFieldPacket(buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readFloat(), buf.readVarInt());
    }

    public static void handle(CthulhuFieldPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> CthulhuIdolFieldFx.start(msg)));
        ctx.get().setPacketHandled(true);
    }
}
