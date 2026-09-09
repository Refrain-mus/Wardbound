package dev.marrowseal.wardbound;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import dev.marrowseal.wardbound.item.SealwrightKeyItem;
import dev.marrowseal.wardbound.item.WardItems;

import java.util.Random;

/**
 * A chain: several seals cast by one house, put in different places.
 *
 * <p>This mod knows more than a million seal names, a couple of dozen houses,
 * and whether any of them thinks well of you. None of that was ever anywhere.
 * Every ward was a container in isolation, and the sealmakers were a line of
 * text in a footer. A chain is those people leaving a trail: break one of
 * somebody's seals and the ward tells you, in the way a ward can, roughly where
 * the next one of the set is.
 *
 * <p>Nothing is placed in the world and nothing needs to be. The mod already
 * decides whether a container is warded at the moment it is touched, so a chain
 * is a promise the world can keep without anything being built: walk to the
 * bearing you were given, open whatever is there, and it will be the next link.
 * A treasure hunt whose treasure did not have to exist beforehand.
 *
 * <p>Links get harder and pay more as they go, they are all by the same hand -
 * so a chain is also the fastest way to earn or ruin that house's regard - and
 * the last one carries no maker's mark at all.
 */
public final class Chain {

    private static final String HOUSE = "house";
    private static final String LINK = "link";
    private static final String LENGTH = "length";
    private static final String X = "x";
    private static final String Z = "z";
    private static final String DIM = "dim";

    private static final Random RNG = new Random();

    private Chain() {
    }

    /** True if this player is following a chain in this dimension right now. */
    public static boolean active(CompoundTag chain, String dimId) {
        return !chain.isEmpty() && chain.getString(DIM).equals(dimId);
    }

    /**
     * Whether the container at {@code pos} is the link the player is looking for.
     *
     * <p>Deliberately loose. The bearing is given in whole hundreds of blocks
     * and the search radius is wide, because a treasure hunt that needs you to
     * find one specific chest in a structure is not a hunt, it is a chore. Any
     * container in roughly the right place is the one.
     */
    public static boolean isTarget(CompoundTag chain, String dimId, BlockPos pos) {
        if (!active(chain, dimId)) return false;
        double dx = pos.getX() - chain.getInt(X);
        double dz = pos.getZ() - chain.getInt(Z);
        return dx * dx + dz * dz <= WardConfig.chainRadius * (double) WardConfig.chainRadius;
    }

    /** The seed a link uses, so every link in a chain is by the same house. */
    public static long seedFor(CompoundTag chain, long fallback) {
        String house = chain.getString(HOUSE);
        for (int nudge = 0; nudge < 96; nudge++) {
            long candidate = fallback + nudge * 0x2545F4914F6CDD1DL;
            if (Sealmakers.house(candidate).equals(house)) return candidate;
        }
        return fallback;
    }

    /**
     * Starts a chain on a container that has just been beaten, sometimes.
     *
     * @return true if one was started
     */
    public static boolean maybeBegin(ServerPlayer player, LockData data, String dimId,
                                     BlockPos from, long lockSeed) {
        if (!WardConfig.chainEnabled) return false;
        if (data.totalBeaten(player.getUUID()) < WardConfig.chainAfterBeaten) return false;
        if (!data.chain(player.getUUID()).isEmpty()) return false;
        if (RNG.nextFloat() >= WardConfig.chainChance) return false;

        CompoundTag chain = new CompoundTag();
        chain.putString(HOUSE, Sealmakers.house(lockSeed));
        chain.putInt(LINK, 1);
        chain.putInt(LENGTH, WardConfig.chainMinLinks
                + RNG.nextInt(Math.max(1, WardConfig.chainMaxLinks - WardConfig.chainMinLinks + 1)));
        chain.putString(DIM, dimId);
        point(chain, from);
        data.setChain(player.getUUID(), chain);

        player.level().playSound(null, from, WardSounds.WARD_HUM_UNSIGNED.get(),
                SoundSource.BLOCKS, 0.8f, 0.8f);
        dev.marrowseal.wardbound.WardHud.message(player, Component.literal(
                        Sealmakers.house(lockSeed) + " cast " + chain.getInt(LENGTH)
                                + " of these, and did not leave them together.")
                .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC), false);
        tell(player, chain, from);
        return true;
    }

    /** Advances the chain after a link has been beaten, and points at the next one. */
    public static void advance(ServerPlayer player, LockData data, String dimId, BlockPos at) {
        CompoundTag chain = data.chain(player.getUUID()).copy();
        if (!active(chain, dimId)) return;

        int link = chain.getInt(LINK) + 1;
        if (link > chain.getInt(LENGTH)) {
            data.setChain(player.getUUID(), null);
            String house = chain.getString(HOUSE);

            // Their own key, off their own last seal. The only thing in the mod
            // that belongs to a named person, and the only reason to care which
            // name is in a footer once the trail is over.
            ItemStack prize = SealwrightKeyItem.of(WardItems.SEALWRIGHTS_KEY.get(), house);
            if (!player.getInventory().add(prize)) player.drop(prize, false);

            player.level().playSound(null, at, WardSounds.KEY_TURN.get(),
                    SoundSource.PLAYERS, 1.0f, 0.85f);
            dev.marrowseal.wardbound.WardHud.message(player, Component.literal(
                            "That was the last of them, and " + house
                                    + "'s own key was on it.")
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);
            return;
        }

        chain.putInt(LINK, link);
        point(chain, at);
        data.setChain(player.getUUID(), chain);
        tell(player, chain, at);
    }

    /** Picks where the next link is, relative to where the player is standing. */
    private static void point(CompoundTag chain, BlockPos from) {
        double angle = RNG.nextDouble() * Math.PI * 2;
        int distance = WardConfig.chainMinDistance
                + RNG.nextInt(Math.max(1, WardConfig.chainMaxDistance - WardConfig.chainMinDistance));
        chain.putInt(X, from.getX() + (int) Math.round(Math.cos(angle) * distance));
        chain.putInt(Z, from.getZ() + (int) Math.round(Math.sin(angle) * distance));
    }

    /**
     * Says where the next one is, the way a ward would: a heading and a rough
     * distance, never coordinates. Coordinates would make it a waypoint; a
     * heading makes it a walk.
     */
    private static void tell(ServerPlayer player, CompoundTag chain, BlockPos from) {
        int dx = chain.getInt(X) - from.getX();
        int dz = chain.getInt(Z) - from.getZ();
        int dist = (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
        dev.marrowseal.wardbound.WardHud.message(player, Component.literal(String.format(
                        "%d of %d  \u00b7  the next lies %s, about %d blocks",
                        chain.getInt(LINK), chain.getInt(LENGTH), compass(dx, dz),
                        Math.round(dist / 50f) * 50))
                .withStyle(ChatFormatting.DARK_AQUA), false);
    }

    /** Eight points. Any finer and it stops being a bearing and starts being a map. */
    private static String compass(int dx, int dz) {
        double a = Math.toDegrees(Math.atan2(dz, dx));
        if (a < 0) a += 360;
        String[] names = {"east", "south-east", "south", "south-west",
                "west", "north-west", "north", "north-east"};
        return names[(int) Math.round(a / 45.0) % 8];
    }

    /** One line for {@code /wardbound standing}. */
    public static String standing(MinecraftServer server, ServerPlayer player) {
        CompoundTag chain = LockData.get(server).chain(player.getUUID());
        if (chain.isEmpty()) return null;

        int dx = chain.getInt(X) - player.blockPosition().getX();
        int dz = chain.getInt(Z) - player.blockPosition().getZ();
        int dist = (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
        return "Following " + chain.getString(HOUSE) + ": link " + chain.getInt(LINK)
                + " of " + chain.getInt(LENGTH) + ", " + compass(dx, dz)
                + " about " + (Math.round(dist / 50f) * 50) + " blocks";
    }
}
