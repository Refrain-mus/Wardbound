package dev.marrowseal.wardbound.net;

import dev.marrowseal.wardbound.LockData;
import dev.marrowseal.wardbound.WardHud;
import dev.marrowseal.wardbound.WardSounds;
import dev.marrowseal.wardbound.boss.MasterRewards;
import dev.marrowseal.wardbound.item.WardItems;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** Server-authoritative Housebreaker's Ace permanent-law selection. */
public record AceChoicePacket(int choice) {
    public static void encode(AceChoicePacket msg, FriendlyByteBuf buf) { buf.writeVarInt(msg.choice); }
    public static AceChoicePacket decode(FriendlyByteBuf buf) { return new AceChoicePacket(buf.readVarInt()); }

    public static void handle(AceChoicePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p != null) ctx.get().enqueueWork(() -> resolve(p, msg.choice));
        ctx.get().setPacketHandled(true);
    }

    private static void resolve(ServerPlayer p, int choice) {
        if (choice < 0 || choice > 1 || p.getServer() == null) return;
        ItemStack main = p.getMainHandItem();
        ItemStack off = p.getOffhandItem();
        if (!main.is(WardItems.HOUSEBREAKERS_ACE.get()) && !off.is(WardItems.HOUSEBREAKERS_ACE.get())) {
            WardHud.send(p, "HOUSEBREAKER'S ACE // The card is no longer in the hand that opened the law.", WardHud.Mood.GLITCH);
            return;
        }

        LockData d = LockData.get(p.getServer());
        boolean debug = p.isCreative();
        if (!MasterRewards.signAceLaw(d, p.getUUID(), choice, debug)) {
            WardHud.send(p, "HOUSEBREAKER'S ACE // That law is already yours, or the Ace has no signature left.", WardHud.Mood.DANGER);
            return;
        }

        int signed = MasterRewards.signedCount(d, p.getUUID());
        if (!debug && signed >= 2) {
            ItemStack held = main.is(WardItems.HOUSEBREAKERS_ACE.get()) ? main : off;
            held.shrink(1);
        }

        GamblerCinematicPacket oathFx = new GamblerCinematicPacket(GamblerCinematicPacket.ACE_OATH, p.getId(),
                p.getX(), p.getY(), p.getZ(), 3.2f, p.getId() * 131 + choice * 17 + signed);
        for (ServerPlayer viewer : p.serverLevel().players()) {
            if (viewer.distanceToSqr(p) <= 48.0 * 48.0)
                dev.marrowseal.wardbound.Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), oathFx);
        }
        p.serverLevel().playSound(null,p.blockPosition(),WardSounds.GAMBLER_ACE_OATH.get(),net.minecraft.sounds.SoundSource.PLAYERS,.9f,1.0f);

        WardHud.send(p, debug
                        ? (choice == 0
                            ? "HOUSEBREAKER'S ACE // DEBUG: THE HOUSE OWES YOU is now a permanent law."
                            : "HOUSEBREAKER'S ACE // DEBUG: THE WORLD IS ANOTHER TABLE is now a permanent law.")
                        : (choice == 0
                            ? "THE HOUSE OWES YOU // Every ordinary ward now begins in your debt: more life, more time, richer payment, and the first error is crossed out."
                            : "THE WORLD IS ANOTHER TABLE // The Gambler's law has left the felt. Your body now carries it into every fight."),
                WardHud.Mood.REWARD);
    }
}
