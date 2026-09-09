package dev.marrowseal.wardbound.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import dev.marrowseal.wardbound.ChestValuator;

import java.util.function.Supplier;

/** Client left the screen mid game; keep the complete attempt state. */
public class SaveProgressPacket {

    public final BlockPos pos;
    public final int lives;
    public final int progress;
    /** The lock this progress belongs to. A save for a stale lock is discarded. */
    public final long seed;
    public final float clockLeft;
    public final float elapsedSeconds;
    public final int mistakes;
    public final float hurriedClock;
    /** v38: rapid consecutive mistakes accumulated for the real skill profile. */
    public final int rapidMistakes;
    /** v38: prevents the one-shot hybrid micro-round from replaying after ESC/reopen. */
    public final boolean hybridCompleted;
    /** 0 none, 1 cleared, 2 failed. */
    public final int hybridOutcome;
    /** True when ESC/reopen occurred inside the borrowed micro-round. */
    public final boolean hybridActive;
    public final float hybridTimer;
    public final int hybridStep;
    public final float hybridGauge;

    public SaveProgressPacket(BlockPos pos, int lives, int progress, long seed,
                              float clockLeft, float elapsedSeconds, int mistakes, float hurriedClock,
                              int rapidMistakes, boolean hybridCompleted, int hybridOutcome,
                              boolean hybridActive, float hybridTimer, int hybridStep, float hybridGauge) {
        this.pos = pos;
        this.lives = lives;
        this.progress = progress;
        this.seed = seed;
        this.clockLeft = clockLeft;
        this.elapsedSeconds = elapsedSeconds;
        this.mistakes = mistakes;
        this.hurriedClock = hurriedClock;
        this.rapidMistakes = rapidMistakes;
        this.hybridCompleted = hybridCompleted;
        this.hybridOutcome = hybridOutcome;
        this.hybridActive = hybridActive && !hybridCompleted;
        this.hybridTimer = hybridTimer;
        this.hybridStep = hybridStep;
        this.hybridGauge = hybridGauge;
    }

    public static void encode(SaveProgressPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeVarInt(msg.lives);
        buf.writeVarInt(msg.progress);
        buf.writeLong(msg.seed);
        buf.writeFloat(msg.clockLeft);
        buf.writeFloat(msg.elapsedSeconds);
        buf.writeVarInt(msg.mistakes);
        buf.writeFloat(msg.hurriedClock);
        buf.writeVarInt(msg.rapidMistakes);
        buf.writeBoolean(msg.hybridCompleted);
        buf.writeVarInt(msg.hybridOutcome);
        buf.writeBoolean(msg.hybridActive);
        buf.writeFloat(msg.hybridTimer);
        buf.writeVarInt(msg.hybridStep);
        buf.writeFloat(msg.hybridGauge);
    }

    public static SaveProgressPacket decode(FriendlyByteBuf buf) {
        return new SaveProgressPacket(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(),
                buf.readLong(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readFloat(),
                buf.readVarInt(), buf.readBoolean(), buf.readVarInt(),
                buf.readBoolean(), buf.readFloat(), buf.readVarInt(), buf.readFloat());
    }

    public static void handle(SaveProgressPacket msg, Supplier<NetworkEvent.Context> ctx) {
        // Legacy protocol slot retained so older serialized registrations do not
        // shift packet ids. Live screens no longer save resumable client state:
        // closing/replacing a ward screen is a failure. Trusting this obsolete
        // packet would let a modified client manufacture progress/lives/timers.
        ctx.get().enqueueWork(() -> {
            // Intentionally inert.
        });
        ctx.get().setPacketHandled(true);
    }
}
