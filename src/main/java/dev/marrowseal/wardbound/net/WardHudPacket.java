package dev.marrowseal.wardbound.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import dev.marrowseal.wardbound.client.ClientWardEventOverlay;

import java.util.function.Supplier;

/** Delivers transient Wardbound event feedback to the custom HUD. */
public class WardHudPacket {
    public static final int DEFAULT_DURATION_MS = 4300;
    public final String line;
    public final int mood;
    public final int durationMs;

    public WardHudPacket(String line, int mood) { this(line, mood, DEFAULT_DURATION_MS); }
    public WardHudPacket(String line, int mood, int durationMs) {
        this.line = line == null ? "" : line;
        this.mood = mood;
        this.durationMs = Math.max(1200, Math.min(6500, durationMs));
    }

    public static void encode(WardHudPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.line, 768);
        buf.writeVarInt(msg.mood);
        buf.writeVarInt(msg.durationMs);
    }

    public static WardHudPacket decode(FriendlyByteBuf buf) {
        return new WardHudPacket(buf.readUtf(768), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(WardHudPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientWardEventOverlay.show(msg.line, msg.mood, msg.durationMs)));
        ctx.get().setPacketHandled(true);
    }
}
