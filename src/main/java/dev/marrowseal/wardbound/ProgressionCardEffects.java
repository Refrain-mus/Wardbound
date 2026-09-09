package dev.marrowseal.wardbound;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Progressive world-play cards requested as ordinary Wagers. Their first three
 * revisions are timed; a Palimpsest signature turns the same law permanent.
 * Strength is derived from CardEvolution, so old saves do not need a new data
 * structure and the Grimoire can describe the exact revision already in play.
 */
@Mod.EventBusSubscriber(modid = Wardbound.MODID)
public final class ProgressionCardEffects {
    private ProgressionCardEffects() {}

    private static final UUID LONG_HAND_MODIFIER = UUID.fromString("f7ee45cf-5f20-4ae6-99d0-a8cb5ee24171");
    private static final UUID IRON_PULSE_MODIFIER = UUID.fromString("275f7bd6-74c7-4582-b828-a45cd7e1d459");
    private static final long PERMANENT = Long.MAX_VALUE;
    private static final long NONE = Long.MIN_VALUE;

    public static boolean isProgressionCard(ForbiddenBargain card) {
        return switch (card) {
            case BLOODWELL_REFLEX, LONG_HAND, HARVEST_SHARE, BALLISTIC_SCRIPT,
                    FAR_LEDGER, CROWD_INTEREST, IRON_PULSE -> true;
            default -> false;
        };
    }

    public static boolean available(LockData data, UUID id, ForbiddenBargain card) {
        if (data == null || id == null || !isProgressionCard(card)) return false;
        // Timed revisions return to the pool after their ink dries. A Palimpsest
        // uses Long.MAX_VALUE and deliberately never appears again.
        return data.uniqueLong(id, timerKey(card)) == NONE;
    }

    public static void apply(ServerPlayer player, LockData data, ForbiddenBargain card) {
        if (player == null || data == null || !isProgressionCard(card)) return;
        int revision = CardEvolution.activeVariant(data, player.getUUID(), card);
        long duration = durationTicks(card, revision);
        data.setUniqueLong(player.getUUID(), timerKey(card), duration == PERMANENT
                ? PERMANENT : player.level().getGameTime() + duration);
        // Apply reach/armor immediately instead of waiting for the next regular tick.
        tickOne(player, data, player.getUUID(), player.level().getGameTime());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player.getServer() == null) return;
        LockData data = LockData.get(player.getServer());
        tickOne(player, data, player.getUUID(), player.level().getGameTime());
    }

    private static void tickOne(ServerPlayer player, LockData data, UUID id, long now) {
        boolean renewal = active(data, id, ForbiddenBargain.BLOODWELL_REFLEX, now);
        boolean reach = active(data, id, ForbiddenBargain.LONG_HAND, now);
        active(data, id, ForbiddenBargain.HARVEST_SHARE, now); // expiry bookkeeping
        active(data, id, ForbiddenBargain.BALLISTIC_SCRIPT, now);
        active(data, id, ForbiddenBargain.FAR_LEDGER, now);
        active(data, id, ForbiddenBargain.CROWD_INTEREST, now);
        boolean armor = active(data, id, ForbiddenBargain.IRON_PULSE, now);

        int reachRevision = CardEvolution.activeVariant(data, id, ForbiddenBargain.LONG_HAND);
        syncModifier(player.getAttribute(ForgeMod.BLOCK_REACH.get()), LONG_HAND_MODIFIER,
                "wardbound.long_hand", reach ? reachAmount(reachRevision) : 0.0D);

        int armorRevision = CardEvolution.activeVariant(data, id, ForbiddenBargain.IRON_PULSE);
        double armorAmount = armor ? armorAmount(player.getHealth() / Math.max(1.0F, player.getMaxHealth()), armorRevision) : 0.0D;
        syncModifier(player.getAttribute(Attributes.ARMOR), IRON_PULSE_MODIFIER,
                "wardbound.iron_pulse", armorAmount);

        if (renewal && player.tickCount % 20 == 0 && player.isAlive()) {
            float ratio = player.getHealth() / Math.max(1.0F, player.getMaxHealth());
            if (ratio < 0.50F) {
                int revision = CardEvolution.activeVariant(data, id, ForbiddenBargain.BLOODWELL_REFLEX);
                float base = revision >= 2 ? 1.0F : revision == 1 ? 0.75F : 0.50F;
                float heal = ratio < 0.25F ? base * 1.75F : base;
                player.heal(heal);
                if (player.level() instanceof ServerLevel level && player.tickCount % 40 == 0)
                    level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.0D, player.getZ(),
                            2, 0.22D, 0.24D, 0.22D, 0.01D);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker) || attacker.getServer() == null) return;
        if (!(attacker.level() instanceof ServerLevel level)) return;
        LockData data = LockData.get(attacker.getServer());
        UUID id = attacker.getUUID();
        long now = level.getGameTime();
        float multiplier = 1.0F;

        if (event.getSource().getDirectEntity() instanceof Projectile) {
            if (active(data, id, ForbiddenBargain.BALLISTIC_SCRIPT, now)) {
                int revision = CardEvolution.activeVariant(data, id, ForbiddenBargain.BALLISTIC_SCRIPT);
                multiplier *= 1.0F + (revision >= 2 ? 0.20F : revision == 1 ? 0.15F : 0.10F);
            }
            if (active(data, id, ForbiddenBargain.FAR_LEDGER, now)) {
                int revision = CardEvolution.activeVariant(data, id, ForbiddenBargain.FAR_LEDGER);
                float cap = revision >= 2 ? 0.20F : revision == 1 ? 0.15F : 0.10F;
                double distance = attacker.distanceTo(event.getEntity());
                float scale = (float) Mth.clamp((distance - 4.0D) / 20.0D, 0.0D, 1.0D);
                multiplier *= 1.0F + cap * scale;
            }
        }

        if (active(data, id, ForbiddenBargain.CROWD_INTEREST, now)) {
            int revision = CardEvolution.activeVariant(data, id, ForbiddenBargain.CROWD_INTEREST);
            int max = revision >= 2 ? 6 : revision == 1 ? 5 : 4;
            float each = revision >= 2 ? 0.05F : revision == 1 ? 0.04F : 0.03F;
            List<LivingEntity> threats = level.getEntitiesOfClass(LivingEntity.class,
                    attacker.getBoundingBox().inflate(10.0D), e -> e.isAlive() && e instanceof Enemy);
            int count = Math.min(max, threats.size());
            if (count > 0) multiplier *= 1.0F + each * count;
        }

        if (multiplier != 1.0F) event.setAmount(event.getAmount() * multiplier);
    }

    /** Mature crop breaks receive a probabilistic copy of their normal loot. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCropBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player)
                || player.isCreative() || player.getServer() == null || !(player.level() instanceof ServerLevel level)) return;
        LockData data = LockData.get(player.getServer());
        UUID id = player.getUUID();
        long now = level.getGameTime();
        if (!active(data, id, ForbiddenBargain.HARVEST_SHARE, now)) return;
        BlockState state = event.getState();
        if (!isMatureCrop(state)) return;

        int revision = CardEvolution.activeVariant(data, id, ForbiddenBargain.HARVEST_SHARE);
        float bonus = revision >= 2 ? 0.60F : revision == 1 ? 0.40F : 0.25F;
        BlockPos pos = event.getPos();
        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), player, player.getMainHandItem());
        for (ItemStack stack : drops) {
            if (stack.isEmpty()) continue;
            float expected = stack.getCount() * bonus;
            int extra = Mth.floor(expected);
            if (player.getRandom().nextFloat() < expected - extra) extra++;
            if (extra <= 0) continue;
            ItemStack copy = stack.copy();
            copy.setCount(extra);
            Block.popResource(level, pos, copy);
        }
        if (player.getRandom().nextFloat() < 0.22F)
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5D, pos.getY() + 0.7D, pos.getZ() + 0.5D,
                    3, 0.18D, 0.16D, 0.18D, 0.01D);
    }

    private static boolean isMatureCrop(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) return crop.isMaxAge(state);
        if (block instanceof NetherWartBlock) return state.getValue(NetherWartBlock.AGE) >= 3;
        if (block instanceof CocoaBlock) return state.getValue(CocoaBlock.AGE) >= 2;
        if (block instanceof SweetBerryBushBlock) return state.getValue(SweetBerryBushBlock.AGE) >= 3;
        return false;
    }

    private static boolean active(LockData data, UUID id, ForbiddenBargain card, long now) {
        long until = data.uniqueLong(id, timerKey(card));
        if (until == NONE) return false;
        if (until == PERMANENT) return true;
        if (now < until) return true;
        data.setUniqueLong(id, timerKey(card), NONE);
        return false;
    }

    private static void syncModifier(AttributeInstance attribute, UUID uuid, String name, double amount) {
        if (attribute == null) return;
        AttributeModifier current = attribute.getModifier(uuid);
        if (amount <= 0.0001D) {
            if (current != null) attribute.removeModifier(uuid);
            return;
        }
        if (current != null && Math.abs(current.getAmount() - amount) < 0.0001D) return;
        if (current != null) attribute.removeModifier(uuid);
        attribute.addTransientModifier(new AttributeModifier(uuid, name, amount, AttributeModifier.Operation.ADDITION));
    }

    private static double reachAmount(int revision) {
        return revision >= 2 ? 4.0D : revision == 1 ? 3.0D : 2.0D;
    }

    private static double armorAmount(float healthRatio, int revision) {
        double low = revision >= 2 ? 4.0D : revision == 1 ? 3.0D : 2.0D;
        if (healthRatio <= 0.20F) return low * 3.0D;
        if (healthRatio <= 0.35F) return low * 2.0D;
        if (healthRatio <= 0.60F) return low;
        return 0.0D;
    }

    private static long durationTicks(ForbiddenBargain card, int revision) {
        if (revision >= CardEvolution.MAX_REVISION) return PERMANENT;
        long minutes = switch (card) {
            case LONG_HAND -> revision >= 2 ? 8L : revision == 1 ? 5L : 3L;
            case HARVEST_SHARE -> revision >= 2 ? 12L : revision == 1 ? 8L : 5L;
            case CROWD_INTEREST -> revision >= 2 ? 8L : revision == 1 ? 5L : 3L;
            default -> revision >= 2 ? 10L : revision == 1 ? 7L : 4L;
        };
        return 20L * 60L * minutes;
    }

    public static boolean activeForDisplay(LockData data, UUID id, ForbiddenBargain card, long now) {
        if (data == null || id == null || !isProgressionCard(card)) return false;
        long until = data.uniqueLong(id, timerKey(card));
        return until == PERMANENT || (until != NONE && until > now);
    }

    public static String status(LockData data, UUID id, ForbiddenBargain card, long now) {
        if (!isProgressionCard(card)) return "INACTIVE";
        long until = data.uniqueLong(id, timerKey(card));
        if (until == NONE || (until != PERMANENT && until <= now)) return "EXPIRED / READY TO RETURN";
        String strength = strengthText(data, id, card);
        if (until == PERMANENT) return "PALIMPSEST · PERMANENT · " + strength;
        long seconds = Math.max(0L, (until - now + 19L) / 20L);
        return String.format(Locale.ROOT, "ACTIVE · %d:%02d · %s", seconds / 60L, seconds % 60L, strength);
    }

    private static String strengthText(LockData data, UUID id, ForbiddenBargain card) {
        int revision = CardEvolution.activeVariant(data, id, card);
        return switch (card) {
            case BLOODWELL_REFLEX -> revision >= 2 ? "strong low-health renewal" : revision == 1 ? "improved low-health renewal" : "low-health renewal";
            case LONG_HAND -> "+" + (int) reachAmount(revision) + " block reach";
            case HARVEST_SHARE -> "+" + (revision >= 2 ? 60 : revision == 1 ? 40 : 25) + "% mature-crop yield";
            case BALLISTIC_SCRIPT -> "+" + (revision >= 2 ? 20 : revision == 1 ? 15 : 10) + "% projectile damage";
            case FAR_LEDGER -> "distance projectile bonus up to +" + (revision >= 2 ? 20 : revision == 1 ? 15 : 10) + "%";
            case CROWD_INTEREST -> "hostile-pressure damage bonus";
            case IRON_PULSE -> "armor rises as health falls";
            default -> "active";
        };
    }

    private static String timerKey(ForbiddenBargain card) {
        return switch (card) {
            case BLOODWELL_REFLEX -> "bloodwell_reflex_until";
            case LONG_HAND -> "long_hand_until";
            case HARVEST_SHARE -> "harvest_share_until";
            case BALLISTIC_SCRIPT -> "ballistic_script_until";
            case FAR_LEDGER -> "far_ledger_until";
            case CROWD_INTEREST -> "crowd_interest_until";
            case IRON_PULSE -> "iron_pulse_until";
            default -> "progression_card_unused_until";
        };
    }
}
