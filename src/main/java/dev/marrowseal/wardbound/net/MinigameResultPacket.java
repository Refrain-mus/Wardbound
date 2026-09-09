package dev.marrowseal.wardbound.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import dev.marrowseal.wardbound.ChestValuator;

import java.util.function.Supplier;

/** Client tells the server how the minigame ended, plus balance-only telemetry. */
public class MinigameResultPacket {

    public final BlockPos pos;
    /** Exact live ward seed this result belongs to. */
    public final long seed;
    public final boolean success;
    public final int livesLeft;
    public final int maxLives;
    public final float performance;
    public final float timeRemainingRatio;
    public final float elapsedSeconds;
    public final int mistakes;
    public final int latencyMs;
    public final String modifiers;
    /** v38: rapid consecutive error events used by the persistent skill profile. */
    public final int rapidMistakes;
    /** 0 no hybrid, 1 hybrid cleared, 2 hybrid failed. */
    public final int hybridOutcome;

    public MinigameResultPacket(BlockPos pos, long seed, boolean success, int livesLeft, int maxLives,
                                float performance, float timeRemainingRatio,
                                float elapsedSeconds, int mistakes, int latencyMs, String modifiers,
                                int rapidMistakes, int hybridOutcome) {
        this.pos = pos;
        this.seed = seed;
        this.success = success;
        this.livesLeft = livesLeft;
        this.maxLives = maxLives;
        this.performance = performance;
        this.timeRemainingRatio = timeRemainingRatio;
        this.elapsedSeconds = elapsedSeconds;
        this.mistakes = mistakes;
        this.latencyMs = latencyMs;
        this.modifiers = modifiers == null ? "" : modifiers;
        this.rapidMistakes = rapidMistakes;
        this.hybridOutcome = hybridOutcome;
    }

    public static void encode(MinigameResultPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeLong(msg.seed);
        buf.writeBoolean(msg.success);
        buf.writeVarInt(msg.livesLeft);
        buf.writeVarInt(msg.maxLives);
        buf.writeFloat(msg.performance);
        buf.writeFloat(msg.timeRemainingRatio);
        buf.writeFloat(msg.elapsedSeconds);
        buf.writeVarInt(msg.mistakes);
        buf.writeVarInt(msg.latencyMs);
        buf.writeUtf(msg.modifiers, 512);
        buf.writeVarInt(msg.rapidMistakes);
        buf.writeVarInt(msg.hybridOutcome);
    }

    public static MinigameResultPacket decode(FriendlyByteBuf buf) {
        return new MinigameResultPacket(buf.readBlockPos(), buf.readLong(), buf.readBoolean(),
                buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(512),
                buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(MinigameResultPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (msg.pos.distSqr(player.blockPosition()) > 100.0D) return;
            int maxLives = Math.max(1, Math.min(64, msg.maxLives));
            int lives = Math.max(0, Math.min(msg.livesLeft, maxLives));
            float perf = Float.isFinite(msg.performance) ? Math.max(0f, Math.min(1f, msg.performance)) : 0f;
            float time = Float.isFinite(msg.timeRemainingRatio) ? Math.max(0f, Math.min(1f, msg.timeRemainingRatio)) : 0f;
            float elapsed = Float.isFinite(msg.elapsedSeconds) ? Math.max(0f, Math.min(3600f, msg.elapsedSeconds)) : 0f;
            int mistakes = Math.max(0, Math.min(999, msg.mistakes));
            int latency = Math.max(0, Math.min(5000, msg.latencyMs));
            String modifiers = msg.modifiers.length() > 512 ? msg.modifiers.substring(0, 512) : msg.modifiers;
            int rapidMistakes = Math.max(0, Math.min(999, msg.rapidMistakes));
            int hybridOutcome = Math.max(0, Math.min(2, msg.hybridOutcome));
            if (msg.success) {
                ChestValuator.resolveSuccess(player, msg.pos, msg.seed, lives, maxLives, perf, time, elapsed,
                        mistakes, latency, modifiers, rapidMistakes, hybridOutcome);
            } else {
                ChestValuator.resolveFailure(player, msg.pos, msg.seed, maxLives, time, elapsed,
                        mistakes, latency, modifiers, rapidMistakes, hybridOutcome);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
