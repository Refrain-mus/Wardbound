package dev.marrowseal.wardbound;

import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import dev.marrowseal.wardbound.item.CthulhuEyeItem;

/** Hidden pairings between signed cards. They become permanent Grimoire knowledge after first activation. */
public final class CardConjunctions {
    private CardConjunctions() {}

    public enum Conjunction {
        VEILED_GLASS("veiled_glass", "Veiled Glass", ForbiddenBargain.BELLGLASS_SIGHT, ForbiddenBargain.POCKET_ECLIPSE,
                "The glass stops answering darkness with light while the eclipse is open."),
        RED_COMMUNION("red_communion", "Red Communion", ForbiddenBargain.BLOOD_TITHE, ForbiddenBargain.VEIN_DRINKER,
                "Vein Drinker returns more blood while the Tithe is still collecting."),
        FUNERAL_CHORUS("funeral_chorus", "Funeral Chorus", ForbiddenBargain.MEMENTO_MORI, ForbiddenBargain.GRAVE_BELL,
                "Each Grave Bell payment advances Memento Mori's count by four extra names."),
        NIGHT_CROWN("night_crown", "The Night Crown", ForbiddenBargain.BLACK_SUN, ForbiddenBargain.MOONLIT_HUNT,
                "Under open night sky, the paired laws add Resistance I to their other gifts and dangers."),
        ASHEN_BRAND("ashen_brand", "Ashen Brand", ForbiddenBargain.CINDER_VOW, ForbiddenBargain.EMBER_COUNT,
                "Consumed Ember strikes burn longer and visibly brand their target."),
        BORROWED_MARCH("borrowed_march", "The Borrowed March", ForbiddenBargain.RED_MARCH, ForbiddenBargain.BORROWED_MOMENTUM,
                "While Momentum remains, the Red March collects its blood toll less often."),
        HUNTER_LANTERN("hunter_lantern", "Hunter's Lantern", ForbiddenBargain.MOONLIT_HUNT, ForbiddenBargain.BLACK_COMPASS,
                "Hostiles killed while traced at night repay one additional experience."),
        PALLBEARER_STEP("pallbearer_step", "Pallbearer's Step", ForbiddenBargain.COFFIN_ROAD, ForbiddenBargain.DUSTBOUND_SOLES,
                "The doubled binding hardens instead of deepening: grounded movement carries brief Resistance."),
        MERCURIAL_FEVER("mercurial_fever", "Mercurial Fever", ForbiddenBargain.SHIVERING_TITHE, ForbiddenBargain.QUICKSILVER_PRAYER,
                "During Quicksilver Prayer the tithe drives Haste II, while Hunger remains the price."),
        RED_LANTERN("red_lantern", "The Red Lantern", ForbiddenBargain.LANTERN_BLOOD, ForbiddenBargain.BLOOD_TITHE,
                "At low health the inward lantern sharpens Blood Tithe to Strength II before darkness closes in."),
        THORN_FERRY("thorn_ferry", "The Thorn Ferry", ForbiddenBargain.THORN_LEDGER, ForbiddenBargain.FERRYMAN_LEDGER,
                "A heavy melee blow spared by the Ferryman returns a harsher thorn to its attacker."),
        WHISPERED_FORTUNE("whispered_fortune", "Whispered Fortune", ForbiddenBargain.ORE_WHISPER, ForbiddenBargain.PILGRIMS_LUCK,
                "Ore broken while the whisper is active has a greatly improved chance to shake loose experience.");

        public final String id;
        public final String title;
        public final ForbiddenBargain left;
        public final ForbiddenBargain right;
        public final String formula;
        public final String effect;

        Conjunction(String id, String title, ForbiddenBargain left, ForbiddenBargain right, String effect) {
            this.id = id;
            this.title = title;
            this.left = left;
            this.right = right;
            this.formula = left.title + " + " + right.title;
            this.effect = effect;
        }

        public boolean contains(ForbiddenBargain card) {
            return card != null && (card == left || card == right);
        }

        public ForbiddenBargain partnerOf(ForbiddenBargain card) {
            if (card == left) return right;
            if (card == right) return left;
            return null;
        }
    }

    public static java.util.List<Conjunction> related(ForbiddenBargain card) {
        if (card == null) return java.util.List.of();
        java.util.List<Conjunction> out = new java.util.ArrayList<>();
        for (Conjunction conjunction : Conjunction.values()) if (conjunction.contains(card)) out.add(conjunction);
        return java.util.List.copyOf(out);
    }

    public static boolean known(LockData data, UUID player, Conjunction conjunction) {
        return data.hasUnique(player, "conjunction_" + conjunction.id);
    }

    public static int knownCount(LockData data, UUID player) {
        int count = 0;
        for (Conjunction conjunction : Conjunction.values()) if (known(data, player, conjunction)) count++;
        return count;
    }

    /** Knowledge progression only: resonance changes how clearly the system can be read, not raw player stats. */
    public static int resonanceTier(int known) {
        if (known >= Conjunction.values().length) return 4;
        if (known >= 9) return 3;
        if (known >= 6) return 2;
        if (known >= 3) return 1;
        return 0;
    }

    public static String resonanceName(int known) {
        return switch (resonanceTier(known)) {
            case 1 -> "Pattern";
            case 2 -> "Network";
            case 3 -> "Chorus";
            case 4 -> "Complete Resonance";
            default -> "Dormant";
        };
    }

    public static int nextResonanceAt(int known) {
        if (known < 3) return 3;
        if (known < 6) return 6;
        if (known < 9) return 9;
        if (known < Conjunction.values().length) return Conjunction.values().length;
        return Conjunction.values().length;
    }

    /** Returns true only on the first activation. */
    public static boolean discover(ServerPlayer player, LockData data, Conjunction conjunction) {
        UUID id = player.getUUID();
        if (known(data, id, conjunction)) return false;
        data.setUnique(id, "conjunction_" + conjunction.id, true);
        WardHistory.discoverConjunction(player, conjunction);
        OccultChainEvents.onConjunctionDiscovered(player, data);
        MasterStory.evaluateAll(player, data);
        WardHistory.recordSpecial(player, "GLITCH", "Conjunction discovered: " + conjunction.title + " // " + conjunction.formula);
        WardHud.message(player, net.minecraft.network.chat.Component.literal(
                "CONJUNCTION // " + conjunction.title + " // " + conjunction.formula)
                .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE, net.minecraft.ChatFormatting.BOLD), false);
        if (player.level() instanceof ServerLevel level) {
            level.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.54f, 0.58f);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY(0.65), player.getZ(),
                    18, 0.28, 0.48, 0.28, 0.02);
        }
        int known = knownCount(data, id);
        String resonance = switch (known) {
            case 3 -> "Three conjunctions are now documented. The Grimoire has stopped treating them as accidents.";
            case 6 -> "Six conjunctions. At this density, the cards are no longer independent variables.";
            case 9 -> "Nine conjunctions. You are carrying enough interacting law for coincidence to be an irresponsible explanation.";
            case 12 -> "All twelve conjunctions are documented. I would congratulate you, but this is an inventory of things that learned to cooperate.";
            default -> null;
        };
        if (resonance != null) {
            WardHistory.recordSpecial(player, "GLITCH", "Conjunction resonance " + known + "/" + Conjunction.values().length + ".");
            CthulhuEyeItem.speakInsight(player, "conjunction_resonance_" + known, resonance, 20L * 90L);
        } else {
            CthulhuEyeItem.speakInsight(player, "conjunction_" + conjunction.id,
                    "Two laws have noticed each other. " + conjunction.title + ". I would not call that cooperation.", 20L * 90L);
        }
        return true;
    }
}
