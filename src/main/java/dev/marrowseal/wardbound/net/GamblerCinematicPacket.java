package dev.marrowseal.wardbound.net;

import dev.marrowseal.wardbound.client.GamblerCinematicFx;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Small server-authored cinematic cue packet. Damage and gameplay remain fully
 * server-side; this packet only tells the client which premium VFX / player
 * animation to present at a known world position.
 */
public record GamblerCinematicPacket(int effect, int entityId, double x, double y, double z, float size, int seed) {
    public static final int INVITATION_REVEAL = 0;
    public static final int TABLE_OPEN = 1;
    public static final int PLAYER_STAGGER = 2;
    public static final int CHOICE_BIND = 3;
    public static final int PHASE_BREAK = 4;
    public static final int FAN_IMPACT = 5;
    public static final int RING_IMPACT = 6;
    public static final int FLOOR_CARD_IMPACT = 7;
    public static final int HOUSE_TEETH = 8;
    public static final int STARFALL = 9;
    public static final int CARD_WALL = 10;
    public static final int SPIRAL = 11;
    public static final int ROYAL = 12;
    public static final int ACE_OATH = 13;
    public static final int BOSS_INTRO = 14;
    public static final int HAND_CHARGE = 15;
    public static final int PLAYER_MARKED = 16;
    public static final int PLAYER_BRACE = 17;
    public static final int VICTORY_SHATTER = 18;
    public static final int PLAYER_AWE = 19;
    public static final int PLAYER_RELEASE = 20;
    public static final int FLOOR_CARD_PREVIEW = 21;
    public static final int FLOOR_CARD_STRIKE = 22;
    public static final int SWEEP_STRIKE = 23;
    public static final int CAGE_STRIKE = 24;
    public static final int MARK_RIFT = 25;
    public static final int DEALER_CROSS = 26;
    public static final int CHIP_STORM = 27;
    public static final int SPLIT_POT = 28;
    public static final int CHOICE_REVEAL = 29;
    public static final int SUMMON_PULSE = 30;
    public static final int AMBIENT_DETONATION = 31;
    public static final int FLOOR_FIELD_PREVIEW = 32;
    public static final int HOUSE_EXPOSED = 35;
    public static final int DANGER_CARD_PREVIEW = 36;
    public static final int DANGER_CARD_STRIKE = 37;
    public static final int BURIED_ACE = 38;
    public static final int MIRROR_DEBT = 39;
    public static final int HOUSE_EDGE = 40;
    public static final int DOUBLE_DOWN = 41;
    public static final int HAND_RELEASE = 42;
    public static final int DEAD_MANS_DRAW = 43;
    public static final int FALSE_TELL = 44;
    public static final int FALSE_TELL_REVEAL = 45;
    public static final int SUIT_CALL = 46;
    public static final int SUIT_CALL_STRIKE = 47;
    public static final int ROYAL_FLUSH = 48;
    public static final int LAST_DEAL = 49;
    public static final int ALL_IN = 50;
    public static final int HOUSE_READ = 51;
    public static final int FINAL_TELL = 52;
    /** Death timeline beat: the House presents one final card after the boundary implodes. */
    public static final int DEATH_FINAL_CARD = 53;
    public static final int NOOP = 54;

    public static void encode(GamblerCinematicPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.effect);
        buf.writeVarInt(msg.entityId + 1); // -1 survives VarInt without signed weirdness.
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
        buf.writeFloat(msg.size);
        buf.writeInt(msg.seed);
    }

    public static GamblerCinematicPacket decode(FriendlyByteBuf buf) {
        int effect = buf.readVarInt();
        int entityId = buf.readVarInt() - 1;
        double x = buf.readDouble(), y = buf.readDouble(), z = buf.readDouble();
        float size = buf.readFloat();
        int seed = buf.readInt();
        // Cosmetic packets should never be able to kick a player out of an otherwise healthy
        // encounter. Internal senders are bounded, but fail soft if a stale/malformed cosmetic
        // cue ever slips through after future VFX work.
        if (effect < 0 || effect > NOOP || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(size) || size < 0 || size > 64f) {
            return new GamblerCinematicPacket(NOOP, -1, 0, 0, 0, 0, 0);
        }
        return new GamblerCinematicPacket(effect, entityId, x, y, z, size, seed);
    }

    public static void handle(GamblerCinematicPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> GamblerCinematicFx.handle(msg)));
        ctx.get().setPacketHandled(true);
    }
}
