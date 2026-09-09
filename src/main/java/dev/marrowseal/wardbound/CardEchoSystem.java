package dev.marrowseal.wardbound;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One-shot family primers used by the 390+ card layer.
 *
 * The important rule is that an Echo never creates a second Contract/Ritual/
 * Covenant objective.  It arms a family token, and the next real card of that
 * family consumes the token during the same authoritative signature.
 */
public final class CardEchoSystem {
    private CardEchoSystem() {}

    private record EffectState(int duration, int amplifier) {}

    /** Snapshot of exactly the state a signed card is allowed to mutate. */
    public static final class ResolutionSnapshot {
        private final CompoundTag bargain;
        private final Map<MobEffect, EffectState> effects;
        private final float health;
        private final int food;

        private ResolutionSnapshot(CompoundTag bargain, Map<MobEffect, EffectState> effects, float health, int food) {
            this.bargain = bargain;
            this.effects = effects;
            this.health = health;
            this.food = food;
        }
    }

    public static boolean isPrimer(ForbiddenBargain card) {
        return card != null && card.id >= 391 && card.id <= 402;
    }

    public static ForbiddenBargain.Kind targetKind(ForbiddenBargain primer) {
        if (primer == null) return null;
        return switch (primer) {
            case DEBT_ECHO -> ForbiddenBargain.Kind.DEBT;
            case WAGER_ECHO -> ForbiddenBargain.Kind.WAGER;
            case SCAR_ECHO -> ForbiddenBargain.Kind.SCAR;
            case REMEDY_ECHO -> ForbiddenBargain.Kind.REMEDY;
            case CONTRACT_ECHO -> ForbiddenBargain.Kind.CONTRACT;
            case RITUAL_ECHO -> ForbiddenBargain.Kind.RITUAL;
            case COVENANT_ECHO -> ForbiddenBargain.Kind.COVENANT;
            case MASTER_ECHO -> ForbiddenBargain.Kind.MASTER;
            case EPIC_ECHO -> ForbiddenBargain.Kind.EPIC;
            case UNIQUE_ECHO -> ForbiddenBargain.Kind.UNIQUE;
            case CURSE_ECHO -> ForbiddenBargain.Kind.CURSE;
            case DEATH_ECHO -> ForbiddenBargain.Kind.DEATH;
            default -> null;
        };
    }

    public static ForbiddenBargain primerForKind(ForbiddenBargain.Kind kind) {
        if (kind == null) return null;
        return switch (kind) {
            case DEBT -> ForbiddenBargain.DEBT_ECHO;
            case WAGER -> ForbiddenBargain.WAGER_ECHO;
            case SCAR -> ForbiddenBargain.SCAR_ECHO;
            case REMEDY -> ForbiddenBargain.REMEDY_ECHO;
            case CONTRACT -> ForbiddenBargain.CONTRACT_ECHO;
            case RITUAL -> ForbiddenBargain.RITUAL_ECHO;
            case COVENANT -> ForbiddenBargain.COVENANT_ECHO;
            case MASTER -> ForbiddenBargain.MASTER_ECHO;
            case EPIC -> ForbiddenBargain.EPIC_ECHO;
            case UNIQUE -> ForbiddenBargain.UNIQUE_ECHO;
            case CURSE -> ForbiddenBargain.CURSE_ECHO;
            case DEATH -> ForbiddenBargain.DEATH_ECHO;
            case REFRESH -> null;
        };
    }

    private static String pendingKey(ForbiddenBargain.Kind kind) {
        return "echo_pending_kind_" + kind.ordinal();
    }

    public static boolean available(LockData data, UUID id, ForbiddenBargain primer) {
        ForbiddenBargain.Kind kind = targetKind(primer);
        return data != null && id != null && kind != null && data.uniqueInt(id, pendingKey(kind)) <= 0;
    }

    public static void arm(ServerPlayer player, LockData data, ForbiddenBargain primer) {
        ForbiddenBargain.Kind kind = targetKind(primer);
        if (player == null || data == null || kind == null) return;
        data.setUniqueInt(player.getUUID(), pendingKey(kind), 1);
        WardHud.message(player, Component.literal("ECHO ARMED // next " + kind.name() + " signature resolves x2")
                .withStyle(kind == ForbiddenBargain.Kind.CURSE || kind == ForbiddenBargain.Kind.DEATH
                        ? ChatFormatting.DARK_RED : ChatFormatting.LIGHT_PURPLE), false);
    }

    /**
     * Echo strength belongs to a concrete signature, not to every future copy
     * of a repeatable card.  A new signature therefore clears the old marker
     * before a currently armed family can set it again.  One-shot world laws
     * keep their marker forever simply because they cannot be signed again.
     */
    public static void beginSignature(LockData data, UUID id, ForbiddenBargain card) {
        if (data == null || id == null || card == null || isPrimer(card)
                || card == ForbiddenBargain.THE_RED_PEN || card == ForbiddenBargain.REFRESH_HAND) return;
        data.setUniqueInt(id, "card_echo_multiplier_" + card.id, 1);
        data.setUniqueInt(id, "card_echo_variant_" + card.id, 0);
    }

    /** Returns true exactly once for the next real card in the armed family. */
    public static boolean consumeIfMatching(LockData data, UUID id, ForbiddenBargain card) {
        if (data == null || id == null || card == null || isPrimer(card) || card == ForbiddenBargain.THE_RED_PEN
                || card == ForbiddenBargain.REFRESH_HAND) return false;
        String key = pendingKey(card.kind);
        if (data.uniqueInt(id, key) <= 0) return false;
        data.setUniqueInt(id, key, 0);
        return true;
    }

    /**
     * Marks the signed card as echoed and promotes revision-aware cards by one
     * effective grade.  Non-revision laws keep a multiplier marker which the
     * 390+ runtime and ledger can inspect without duplicating objective state.
     */
    public static void strengthen(ServerPlayer player, LockData data, ForbiddenBargain card) {
        if (player == null || data == null || card == null) return;
        UUID id = player.getUUID();
        data.setUniqueInt(id, "card_echo_multiplier_" + card.id, 2);
        // Keep Echo separate from earned mutation progression.  The selected
        // signature resolves one effective grade stronger, but a future
        // un-echoed copy does not inherit a free permanent revision.
        int active = CardEvolution.activeVariant(data, id, card);
        int boosted = Math.min(CardEvolution.MAX_REVISION, Math.max(1, active + 1));
        data.setUniqueInt(id, "card_echo_variant_" + card.id, boosted);
        data.setUniqueInt(id, "echoed_signatures", data.uniqueInt(id, "echoed_signatures") + 1);
        WardHistory.recordSpecial(player, "GLITCH", "Echo x2 resolved into " + card.title + ".");
        WardHud.message(player, Component.literal("ECHO x2 // " + card.title)
                .withStyle(card.kind == ForbiddenBargain.Kind.CURSE || card.kind == ForbiddenBargain.Kind.DEATH
                        ? ChatFormatting.DARK_RED : ChatFormatting.GOLD, ChatFormatting.BOLD), false);
    }

    /** Capture state after the Echo marker is armed but before the real card resolves. */
    public static ResolutionSnapshot snapshotResolution(ServerPlayer player, LockData data) {
        if (player == null || data == null) return null;
        Map<MobEffect, EffectState> effects = new HashMap<>();
        for (MobEffectInstance effect : player.getActiveEffects()) {
            effects.put(effect.getEffect(), new EffectState(effect.getDuration(), effect.getAmplifier()));
        }
        return new ResolutionSnapshot(data.bargainSnapshot(player.getUUID()), effects,
                player.getHealth(), player.getFoodData().getFoodLevel());
    }

    /**
     * Finishes one x2 signature without replaying applyCardState. Numeric card
     * state deltas are doubled, newly written deadlines are extended, direct
     * potion terms are promoted, and immediate healing/food repayment is
     * repeated once. This makes stable legacy cards materially respond to Echo
     * while objective cards remain a single objective instance.
     */
    public static void finishResolution(ServerPlayer player, LockData data, ForbiddenBargain card, ResolutionSnapshot before) {
        if (player == null || data == null || card == null || before == null) return;
        UUID id = player.getUUID();
        data.amplifyBargainDelta(id, before.bargain, player.level().getGameTime());

        float healed = player.getHealth() - before.health;
        if (healed > 0.0F && player.getHealth() < player.getMaxHealth()) player.heal(healed);
        int fed = player.getFoodData().getFoodLevel() - before.food;
        if (fed > 0 && player.getFoodData().getFoodLevel() < 20) player.getFoodData().eat(fed, 0.0F);

        for (MobEffectInstance current : new java.util.ArrayList<>(player.getActiveEffects())) {
            EffectState old = before.effects.get(current.getEffect());
            boolean changed = old == null || old.duration != current.getDuration() || old.amplifier != current.getAmplifier();
            if (!changed || current.getDuration() <= 1) continue;
            int duration = current.getDuration();
            int amplifier = current.getAmplifier();
            if (durationOnly(current.getEffect())) {
                duration = duration > Integer.MAX_VALUE / 2 ? Integer.MAX_VALUE : duration * 2;
            } else {
                amplifier = Math.min(9, amplifier + 1);
            }
            player.addEffect(new MobEffectInstance(current.getEffect(), duration, amplifier,
                    current.isAmbient(), current.isVisible(), current.showIcon()));
        }
    }

    private static boolean durationOnly(MobEffect effect) {
        return effect == MobEffects.NIGHT_VISION || effect == MobEffects.FIRE_RESISTANCE
                || effect == MobEffects.WATER_BREATHING || effect == MobEffects.SLOW_FALLING
                || effect == MobEffects.INVISIBILITY || effect == MobEffects.GLOWING
                || effect == MobEffects.DARKNESS || effect == MobEffects.BLINDNESS
                || effect == MobEffects.CONFUSION || effect == MobEffects.DOLPHINS_GRACE;
    }

    public static int multiplier(LockData data, UUID id, ForbiddenBargain card) {
        if (data == null || id == null || card == null) return 1;
        return Math.max(1, Math.min(2, data.uniqueInt(id, "card_echo_multiplier_" + card.id)));
    }

    public static boolean isArmed(LockData data, UUID id, ForbiddenBargain primer) {
        ForbiddenBargain.Kind kind = targetKind(primer);
        return data != null && id != null && kind != null && data.uniqueInt(id, pendingKey(kind)) > 0;
    }

    public static int pendingCount(LockData data, UUID id) {
        if (data == null || id == null) return 0;
        int n = 0;
        for (ForbiddenBargain.Kind kind : ForbiddenBargain.Kind.values()) {
            if (kind != ForbiddenBargain.Kind.REFRESH && data.uniqueInt(id, pendingKey(kind)) > 0) n++;
        }
        return n;
    }
}
