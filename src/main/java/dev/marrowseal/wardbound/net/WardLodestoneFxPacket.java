package dev.marrowseal.wardbound.net;

import dev.marrowseal.wardbound.client.WardLodestoneFx;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Lightweight client visual packet for mod-wide Lodestone presentation. */
public record WardLodestoneFxPacket(double x, double y, double z, int kind, float scale) {
    public static final int WARD_OPEN = 0;
    public static final int WARD_SUCCESS = 1;
    public static final int WARD_FAIL = 2;
    public static final int BARGAIN_MASTER = 3;
    public static final int BARGAIN_CURSE = 4;
    public static final int BARGAIN_DEATH = 5;
    public static final int BARGAIN_RITUAL = 6;
    public static final int BARGAIN_COVENANT = 7;
    public static final int REMEDY = 8;
    public static final int CONTRACT = 9;
    public static final int RELIC_SPLINTER = 10;
    public static final int RELIC_ELDRITCH = 11;
    public static final int RELIC_ECHO = 12;
    public static final int RELIC_BLOODGLASS = 13;
    public static final int LEDGER_WRITE = 14;
    public static final int CTHULHU_FIELD = 15;
    public static final int CTHULHU_TARGET = 16;
    public static final int CURSE_SIGN = 17;
    public static final int DEATH_AURA = 18;
    public static final int ASHEN_BRAND = 19;
    public static final int BARGAIN_DEBT = 20;
    public static final int BARGAIN_WAGER = 21;
    public static final int BARGAIN_SCAR = 22;
    public static final int BARGAIN_EPIC = 23;
    public static final int BARGAIN_UNIQUE = 24;
    public static final int BARGAIN_REFRESH = 25;
    public static final int MASTER_CROOKED = 26;
    public static final int MASTER_VEILED = 27;
    public static final int MASTER_EXACTING = 28;
    public static final int CARD_HAND_OPEN = 29;
    public static final int CARD_HOVER = 30;
    public static final int CARD_SIGN = 31;
    /** Chest minigame result: a flawless/perfect resolution, visually distinct from a normal win. */
    public static final int WARD_PERFECT = 32;
    public static final int DEALER_TABLE_SWEEP = 33;
    public static final int DEALER_CUT_WAVE = 34;
    public static final int DEALER_DEAD_DRAW = 35;
    public static final int DEALER_DOUBLE_DOWN = 36;
    public static final int DEALER_ROYAL_FLUSH = 37;
    public static final int DEALER_HOUSE_COLLAPSE = 38;
    public static final int REWARD_LAST_MARGIN = 39;
    public static final int REWARD_UNSIGNED_VERDICT = 40;
    public static final int CTHULHU_MANIFEST = 41;
    public static final int CTHULHU_EYE_CHARGE = 42;
    public static final int CTHULHU_EYE_BEAM = 43;
    public static final int CTHULHU_TENTACLE_TELEGRAPH = 44;
    public static final int CTHULHU_TENTACLE_IMPACT = 45;
    public static final int CTHULHU_GRASP = 46;
    public static final int CTHULHU_ROAR = 47;
    public static final int CTHULHU_VOID_RIFT = 48;
    public static final int CTHULHU_SLAM_TELEGRAPH = 49;
    public static final int CTHULHU_SLAM_IMPACT = 50;
    public static final int CTHULHU_PULSE = 51;
    public static final int CTHULHU_PHASE = 52;
    public static final int CTHULHU_DEATH = 53;

    public static void encode(WardLodestoneFxPacket msg, FriendlyByteBuf buf) {
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
        buf.writeVarInt(msg.kind);
        buf.writeFloat(msg.scale);
    }

    public static WardLodestoneFxPacket decode(FriendlyByteBuf buf) {
        return new WardLodestoneFxPacket(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt(), buf.readFloat());
    }

    public static void handle(WardLodestoneFxPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> WardLodestoneFx.spawn(msg)));
        ctx.get().setPacketHandled(true);
    }
}
