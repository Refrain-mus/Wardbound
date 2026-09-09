package dev.marrowseal.wardbound.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import dev.marrowseal.wardbound.WardConfig;
import dev.marrowseal.wardbound.WardSounds;

/**
 * A charm is a consumable you carry rather than use: it binds itself to a lock
 * the moment the lock binds itself to you, and it is spent when that lock is
 * resolved, win or lose.
 *
 * <p>Binding at the start rather than at the end is what stops this from being
 * free. You cannot walk into a lock, see it going badly, and then pull the
 * heart out of your pocket; and you cannot beat a lock cleanly and keep the
 * charge because you never needed it. The decision is made before you know
 * anything, which is the only version of the decision that is interesting.
 *
 * <p>The tier bound to a chest is written into that chest's per-player state as
 * a plain string, so it survives logging out mid-lock, and so a charm dropped
 * or destroyed between binding and resolving simply fails to be spent rather
 * than corrupting anything.
 */
public enum Charm {

    /** Nothing carried. The chest plays by the dimension's own rules. */
    NONE(""),

    /**
     * The rusted key. One more mistake, four locks' worth of it. No effect on
     * the loot at all: it buys patience, not money.
     */
    WARD_KEY("ward_key"),

    /**
     * The heart. Two more mistakes and a quarter again on whatever comes out,
     * but only two locks before it stops beating.
     */
    ICHORHEART("ichorheart"),

    /**
     * A sealwright's own key. Stronger than either of the others and it lasts,
     * but it answers only the house whose name is on it, so whether it is worth
     * anything at all depends entirely on whose seal you are standing in front
     * of. See {@link SealwrightKeyItem}.
     */
    SEALWRIGHT("sealwrights_key");

    /** Uses before the charm is gone. Small on purpose; see {@link WardItems}. */
    public static final int KEY_DURABILITY = 4;
    public static final int HEART_DURABILITY = 2;
    /**
     * One. A key taken off a dead maker's last seal is not a tool you keep, and
     * a single enormous use is a decision about which ward deserves it - which
     * is a better thing to own than fourteen small ones.
     */
    public static final int WRIGHT_DURABILITY = 1;

    public final String id;

    Charm(String id) {
        this.id = id;
    }

    public static Charm byId(String s) {
        if (s == null || s.isEmpty()) return NONE;
        for (Charm c : values()) {
            if (c.id.equals(s)) return c;
        }
        return NONE;
    }

    // ------------------------------------------------------------------ numbers

    /** Extra mistakes this charm buys, on top of the dimension's own allowance. */
    public int bonusLives() {
        return switch (this) {
            case WARD_KEY -> WardConfig.keyBonusLives;
            case ICHORHEART -> WardConfig.heartBonusLives;
            case SEALWRIGHT -> WardConfig.wrightBonusLives;
            default -> 0;
        };
    }

    /** Multiplier folded into the chest's final loot roll. */
    public float lootBonus() {
        return switch (this) {
            case ICHORHEART -> WardConfig.heartLootBonus;
            case SEALWRIGHT -> WardConfig.wrightLootBonus;
            default -> 1.0f;
        };
    }

    public ItemStack newStack() {
        return switch (this) {
            case WARD_KEY -> new ItemStack(WardItems.WARD_KEY.get());
            case ICHORHEART -> new ItemStack(WardItems.ICHORHEART.get());
            case SEALWRIGHT -> new ItemStack(WardItems.SEALWRIGHTS_KEY.get());
            default -> ItemStack.EMPTY;
        };
    }

    public boolean matches(ItemStack stack) {
        return this != NONE && !stack.isEmpty() && stack.getItem() instanceof CharmItem charm
                && charm.charm == this;
    }

    /** How it is named in chat, without dragging the client's language file in. */
    public String label() {
        return switch (this) {
            case WARD_KEY -> "The old key";
            case ICHORHEART -> "The ichorheart";
            case SEALWRIGHT -> "The sealwright's key";
            default -> "Nothing";
        };
    }

    // ------------------------------------------------------------------ interaction hand

    /**
     * Returns the charm represented by the exact stack used to interact with the ward.
     * Nothing in the rest of the inventory is considered. A Sealwright key only
     * counts when the key itself names the house that cast this seal.
     */
    public static Charm fromInteractionStack(ItemStack stack, String house) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof CharmItem charmItem)) {
            return NONE;
        }
        Charm charm = charmItem.charm;
        if (charm == SEALWRIGHT) {
            return stack.getItem() instanceof SealwrightKeyItem
                    && SealwrightKeyItem.houseOf(stack).equals(house) ? SEALWRIGHT : NONE;
        }
        return charm;
    }

    /** Uses left on the exact interaction stack, never a matching stack elsewhere. */
    public static int usesLeft(ItemStack stack, Charm charm) {
        if (charm == NONE || stack == null || !charm.matches(stack)) return 0;
        return stack.getMaxDamage() - stack.getDamageValue();
    }

    /**
     * Spends one use from the exact stack that opened the ward. This is intentionally
     * strict: another Ichorheart/key in the hotbar or backpack can never be substituted.
     */
    public static boolean spend(ServerPlayer player, Charm charm, ItemStack stack) {
        if (charm == NONE || stack == null || !charm.matches(stack)) return false;

        stack.hurtAndBreak(1, player, p -> {
        });
        boolean broke = stack.isEmpty();

        Level level = player.level();
        if (broke) {
            level.playSound(null, player.blockPosition(), WardSounds.CHARM_SPENT.get(),
                    SoundSource.PLAYERS, 0.8f, 1.0f);
            dev.marrowseal.wardbound.WardHud.message(player, Component.literal(charm.label() + " is spent.")
                    .withStyle(ChatFormatting.DARK_GRAY), true);
        } else {
            level.playSound(null, player.blockPosition(),
                    (charm == ICHORHEART ? WardSounds.HEARTBEAT : WardSounds.KEY_TURN).get(),
                    SoundSource.PLAYERS, 0.55f, 1.0f);
        }
        return broke;
    }

    /** Told to the player when a lock binds to a charm, so the cost is never a surprise. */
    public Component bindingMessage(int usesLeft) {
        if (this == NONE) return null;
        String extra = this == ICHORHEART
                ? String.format("+%d lives, loot x%.2f", bonusLives(), lootBonus())
                : "+" + bonusLives() + " life";
        return Component.literal(label() + " answers the ward. " + extra
                        + "   \u00b7   " + usesLeft + " use" + (usesLeft == 1 ? "" : "s") + " left")
                .withStyle(this == ICHORHEART ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GOLD);
    }
}
