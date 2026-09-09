package dev.marrowseal.wardbound.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import dev.marrowseal.wardbound.client.ClientHooks;

import java.util.function.Supplier;

/** Server opens the rare post-ward bargain card screen. */
public class OpenBargainPacket {

    public final BlockPos pos;
    public final int[] offerIds;
    public final float[] rewardAdds;
    /** Revision level for each offered card. 0 = original, 1/2 = revised clause, 3 = palimpsest clause. */
    public final int[] variantLevels;
    public final float bankedLoot;
    /** Seed of the signed ward whose hand wrote these cards. */
    public final long makerSeed;
    /** 0 normal, 1 solo epic, 2 forced curse, 3 solo unique, 4 forced death, 5 contract, 6 ritual, 7 covenant. */
    public final int dealMode;
    public final boolean forcedChoice;
    /** Optional presenter label for loose/world cards. Empty means derive the ward maker normally. */
    public final String presenter;
    /** Banked one-card redraws available in ordinary hands. */
    public final int mulliganReserve;

    public OpenBargainPacket(BlockPos pos, int[] offerIds, float[] rewardAdds, float bankedLoot,
                             long makerSeed, int dealMode, boolean forcedChoice, String presenter) {
        this(pos, offerIds, rewardAdds, new int[offerIds == null ? 0 : offerIds.length], bankedLoot, makerSeed, dealMode, forcedChoice, presenter, 0);
    }

    public OpenBargainPacket(BlockPos pos, int[] offerIds, float[] rewardAdds, int[] variantLevels, float bankedLoot,
                             long makerSeed, int dealMode, boolean forcedChoice, String presenter) {
        this(pos, offerIds, rewardAdds, variantLevels, bankedLoot, makerSeed, dealMode, forcedChoice, presenter, 0);
    }

    public OpenBargainPacket(BlockPos pos, int[] offerIds, float[] rewardAdds, int[] variantLevels, float bankedLoot,
                             long makerSeed, int dealMode, boolean forcedChoice, String presenter, int mulliganReserve) {
        this.pos = pos;
        this.offerIds = offerIds == null ? new int[0] : offerIds;
        this.rewardAdds = rewardAdds == null ? new float[0] : rewardAdds;
        this.variantLevels = variantLevels == null ? new int[0] : variantLevels;
        this.bankedLoot = bankedLoot;
        this.makerSeed = makerSeed;
        this.dealMode = dealMode;
        this.forcedChoice = forcedChoice;
        this.presenter = presenter == null ? "" : presenter;
        this.mulliganReserve = Math.max(0, Math.min(2, mulliganReserve));
    }

    public static void encode(OpenBargainPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        int n = Math.min(msg.offerIds.length, msg.rewardAdds.length);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeVarInt(msg.offerIds[i]);
            buf.writeFloat(msg.rewardAdds[i]);
            buf.writeVarInt(i < msg.variantLevels.length ? Math.max(0, Math.min(3, msg.variantLevels[i])) : 0);
        }
        buf.writeFloat(msg.bankedLoot);
        buf.writeLong(msg.makerSeed);
        buf.writeVarInt(msg.dealMode);
        buf.writeBoolean(msg.forcedChoice);
        buf.writeUtf(msg.presenter, 96);
        buf.writeVarInt(msg.mulliganReserve);
    }

    public static OpenBargainPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int n = Math.max(0, Math.min(8, buf.readVarInt()));
        int[] ids = new int[n];
        float[] rewards = new float[n];
        int[] variants = new int[n];
        for (int i = 0; i < n; i++) {
            ids[i] = buf.readVarInt();
            rewards[i] = buf.readFloat();
            variants[i] = Math.max(0, Math.min(3, buf.readVarInt()));
        }
        float banked = buf.readFloat();
        long makerSeed = buf.readLong();
        int dealMode = buf.readVarInt();
        boolean forcedChoice = buf.readBoolean();
        String presenter = buf.readUtf(96);
        int mulliganReserve = Math.max(0, Math.min(2, buf.readVarInt()));
        return new OpenBargainPacket(pos, ids, rewards, variants, banked, makerSeed, dealMode, forcedChoice, presenter, mulliganReserve);
    }

    public static void handle(OpenBargainPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openBargain(msg)));
        ctx.get().setPacketHandled(true);
    }
}
