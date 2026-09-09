package dev.marrowseal.wardbound.champion;

import dev.marrowseal.wardbound.LockData;
import dev.marrowseal.wardbound.WardHistory;
import dev.marrowseal.wardbound.WardHud;
import dev.marrowseal.wardbound.item.WardItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Physical world fragments plus indirect observations that build Silas without turning him into an exposition terminal. */
public final class SilasLore {
    public static final String FRAGMENT_TAG = "SilasFragment";
    private static final String MASK = "silas_fragment_mask";

    public enum Family {
        ROAD("Road Fragment"), ARCHIVE("Archive Fragment"), DOCKET("Docket / Article"), TABLE("Table Remnant"), PERSONAL("Private Margin");
        public final String title;
        Family(String title) { this.title = title; }
    }

    public enum Fragment {
        INNKEEPER("innkeepers_account", "The Innkeeper's Account", Family.ROAD, SilasProgression.TRACE),
        NAMES_GRIP("names_on_the_grip", "Names on the Grip", Family.PERSONAL, SilasProgression.WAYFARER),
        PHYSICIAN("physicians_refusal", "A Physician's Refusal", Family.ROAD, SilasProgression.TRACE),
        UNCLAIMED_HAND("unclaimed_hand", "The Unclaimed Hand", Family.TABLE, SilasProgression.COMPANIONS),
        ARCHIVE_44V("archive_44_v", "Archive Entry 44–V", Family.ARCHIVE, SilasProgression.HANDS),
        DEATH_CERTIFICATE("death_certificate_without_death", "The Death Certificate Without a Death", Family.DOCKET, SilasProgression.HANDS),
        SERAS_MORNING("seras_morning", "Sera's Morning", Family.PERSONAL, SilasProgression.COMPANIONS),
        CHAMPIONS_RETURN("champions_return", "The Champion's Return", Family.ROAD, SilasProgression.HANDS),
        CURATOR_CROSS_REFERENCE("curator_cross_reference", "Curator: Cross-Reference", Family.ARCHIVE, SilasProgression.HANDS),
        SILAS_MARGIN("silas_private_margin", "A Margin in Silas' Hand", Family.PERSONAL, SilasProgression.HANDS),
        NOTARY_ADDENDUM("notary_addendum", "The Notary's Addendum", Family.DOCKET, SilasProgression.HANDS),
        CURATOR_FINAL("curator_final_annotation", "The Curator's Final Annotation", Family.ARCHIVE, SilasProgression.HANDS);

        public final String id;
        public final String title;
        public final Family family;
        public final int minStage;
        Fragment(String id, String title, Family family, int minStage) {
            this.id = id;
            this.title = title;
            this.family = family;
            this.minStage = minStage;
        }

        public static Fragment byId(String id) {
            if (id == null) return null;
            for (Fragment f : values()) if (f.id.equals(id)) return f;
            return null;
        }
    }

    private SilasLore() {}

    public static int mask(LockData data, UUID id) {
        return data == null || id == null ? 0 : data.uniqueInt(id, MASK);
    }

    public static boolean known(LockData data, UUID id, Fragment fragment) {
        return fragment != null && (mask(data, id) & (1 << fragment.ordinal())) != 0;
    }


    public static boolean canRead(ServerPlayer player, Fragment fragment) {
        if (player == null || player.getServer() == null || fragment == null) return false;
        LockData data = LockData.get(player.getServer());
        int stage = SilasProgression.stage(data, player.getUUID());
        if (fragment.minStage == SilasProgression.TRACE && SilasProgression.maxChapter(data, player.getUUID()) < 3) return false;
        if (stage < fragment.minStage) return false;
        if (fragment == Fragment.NOTARY_ADDENDUM && dev.marrowseal.wardbound.MasterStory.chapter(data, player.getUUID(), dev.marrowseal.wardbound.CardMaster.MOURNING_NOTARY) < 6) return false;
        if (fragment == Fragment.CURATOR_FINAL && dev.marrowseal.wardbound.MasterStory.chapter(data, player.getUUID(), dev.marrowseal.wardbound.CardMaster.ASHEN_CURATOR) < 6) return false;
        return true;
    }

    public static void discover(ServerPlayer player, Fragment fragment, boolean announce) {
        if (player == null || player.getServer() == null || fragment == null) return;
        LockData data = LockData.get(player.getServer());
        UUID id = player.getUUID();
        int bit = 1 << fragment.ordinal();
        int before = mask(data, id);
        if ((before & bit) != 0) return;
        data.setUniqueInt(id, MASK, before | bit);
        WardHistory.discoverSilasFragment(player, fragment.id);
        if (announce) WardHud.send(player, "UNRESOLVED TESTIMONY // " + fragment.title, WardHud.Mood.GLITCH);
    }

    public static ItemStack note(Fragment fragment) {
        ItemStack stack = new ItemStack(WardItems.SILAS_FRAGMENT.get());
        if (fragment != null) stack.getOrCreateTag().putString(FRAGMENT_TAG, fragment.id);
        return stack;
    }

    /** Rarely adds one currently-legal unread fragment to a resolved ward chest. */
    public static void tryAddWorldFragment(List<ItemStack> loot, ServerPlayer player, RandomSource random) {
        if (loot == null || player == null || random == null || player.getServer() == null) return;
        LockData data = LockData.get(player.getServer());
        if (data.uniqueInt(player.getUUID(), "silas_testimony_assembled") != 0) return;
        int stage = SilasProgression.stage(data, player.getUUID());
        if (SilasProgression.maxChapter(data, player.getUUID()) < 3) return;
        List<Fragment> eligible = new ArrayList<>();
        for (Fragment f : Fragment.values()) {
            // Names on the Grip is intentionally learned by observing Nhal-Sûl, not by chest loot.
            if (f == Fragment.NAMES_GRIP) continue;
            if (f == Fragment.NOTARY_ADDENDUM && dev.marrowseal.wardbound.MasterStory.chapter(data, player.getUUID(), dev.marrowseal.wardbound.CardMaster.MOURNING_NOTARY) < 6) continue;
            if (f == Fragment.CURATOR_FINAL && dev.marrowseal.wardbound.MasterStory.chapter(data, player.getUUID(), dev.marrowseal.wardbound.CardMaster.ASHEN_CURATOR) < 6) continue;
            if (f.minStage <= stage && !carries(player, f)) eligible.add(f);
        }
        if (eligible.isEmpty()) return;
        float chance = stage == SilasProgression.TRACE ? 0.09f : stage >= SilasProgression.UNCONCLUDED ? 0.17f : 0.13f;
        String pityKey = "silas_fragment_pity";
        int pity = Math.max(0, data.uniqueInt(player.getUUID(), pityKey));
        if (random.nextFloat() >= chance && pity < 7) {
            data.setUniqueInt(player.getUUID(), pityKey, pity + 1);
            return;
        }
        data.setUniqueInt(player.getUUID(), pityKey, 0);
        loot.add(note(eligible.get(random.nextInt(eligible.size()))));
    }


    public static boolean carries(ServerPlayer player, Fragment fragment) {
        if (player == null || fragment == null) return false;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(WardItems.SILAS_FRAGMENT.get()) || !stack.hasTag()) continue;
            if (fragment.id.equals(stack.getTag().getString(FRAGMENT_TAG))) return true;
        }
        return false;
    }

    public static int physicalCount(ServerPlayer player) {
        if (player == null) return 0;
        int count = 0;
        for (Fragment fragment : Fragment.values()) if (carries(player, fragment)) count++;
        return count;
    }

    public static boolean hasCompletePhysicalSet(ServerPlayer player) {
        return physicalCount(player) == Fragment.values().length;
    }

    public static boolean assembleTestimony(ServerPlayer player) {
        if (player == null || player.getServer() == null) return false;
        LockData data = LockData.get(player.getServer());
        if (data.uniqueInt(player.getUUID(), "silas_testimony_assembled") != 0) return false;
        if (!hasCompletePhysicalSet(player)) return false;
        for (Fragment fragment : Fragment.values()) discover(player, fragment, false);
        for (Fragment fragment : Fragment.values()) {
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (!stack.is(WardItems.SILAS_FRAGMENT.get()) || !stack.hasTag()) continue;
                if (!fragment.id.equals(stack.getTag().getString(FRAGMENT_TAG))) continue;
                stack.shrink(1);
                break;
            }
        }
        ItemStack testimony = new ItemStack(WardItems.UNCONCLUDED_TESTIMONY.get());
        if (!player.getInventory().add(testimony)) player.drop(testimony, false);
        data.setUniqueInt(player.getUUID(), "silas_testimony_assembled", 1);
        WardHistory.recordSpecial(player, "GLITCH", "TESTIMONY COMPLETE // 12/12 // The unresolved fragments have been assembled into a single record for Silas Veyr.");
        WardHud.send(player, "TESTIMONY COMPLETE // 12/12", WardHud.Mood.REWARD);
        return true;
    }

    public static void observeGrip(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        LockData data = LockData.get(player.getServer());
        boolean first = !known(data, player.getUUID(), Fragment.NAMES_GRIP);
        discover(player, Fragment.NAMES_GRIP, true);
        if (data.uniqueInt(player.getUUID(), "silas_testimony_assembled") == 0 && !carries(player, Fragment.NAMES_GRIP)) {
            ItemStack stack = note(Fragment.NAMES_GRIP);
            if (!player.getInventory().add(stack)) player.drop(stack, false);
            if (!first) WardHud.send(player, "NHAL-SÛL // A physical rubbing of the grip has been restored.", WardHud.Mood.WARD);
        }
    }

    public static void observeUnconcluded(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        LockData data = LockData.get(player.getServer());
        UUID id = player.getUUID();
        if (data.uniqueInt(id, "silas_unconcluded_seen") != 0) return;
        data.setUniqueInt(id, "silas_unconcluded_seen", 1);
        WardHistory.recordSpecial(player, "GLITCH", "OBSERVATION // HIS DEATH DID NOT COMPLETE // Silas Veyr crossed the fatal threshold without acquiring an ending.");
        WardHud.send(player, "OBSERVATION // HIS DEATH DID NOT COMPLETE", WardHud.Mood.GLITCH);
    }

    public static void observeUntargeted(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        LockData data = LockData.get(player.getServer());
        UUID id = player.getUUID();
        if (data.uniqueInt(id, "silas_untargeted_seen") != 0) return;
        data.setUniqueInt(id, "silas_untargeted_seen", 1);
        WardHistory.recordSpecial(player, "GLITCH", "OBSERVATION // THE HOSTILE DO NOT SEE HIM // A creature crossed within striking distance of Silas Veyr and behaved as though the space were empty.");
        WardHud.send(player, "OBSERVATION // THE HOSTILE DO NOT SEE HIM", WardHud.Mood.GLITCH);
    }

    public static String translationKey(Fragment fragment) {
        return fragment == null ? "silas.fragment.unknown" : "silas.fragment." + fragment.id;
    }

    public static String familyKey(Fragment fragment) {
        return fragment == null ? "silas.fragment.family.unknown" : "silas.fragment.family." + fragment.family.name().toLowerCase(Locale.ROOT);
    }
}
