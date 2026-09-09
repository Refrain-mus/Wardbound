package dev.marrowseal.wardbound;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Fifty long-form world-play cards built on the same Stable -> Revision I ->
 * Revision II -> Palimpsest grammar as the first progression-card set.
 *
 * The cards are deliberately conditional. A player can collect several without
 * every card degenerating into a flat always-on stat stack; positioning, target
 * state, crowd pressure, terrain and play style decide which clauses are live.
 */
@Mod.EventBusSubscriber(modid = Wardbound.MODID)
public final class ExpandedProgressionCardEffects {
    private ExpandedProgressionCardEffects() {}

    public enum Family { BALLISTICS, EDGEWORK, BULWARK, PILGRIMAGE, INDUSTRY }

    private static final long PERMANENT = Long.MAX_VALUE;
    private static final long NONE = Long.MIN_VALUE;
    private static final String LAST_HURT = "expanded_progression_last_hurt";
    private static final ForbiddenBargain[] EXPANDED_CARDS = java.util.Arrays.stream(ForbiddenBargain.values())
            .filter(ExpandedProgressionCardEffects::isExpanded).toArray(ForbiddenBargain[]::new);

    public static boolean isExpanded(ForbiddenBargain card) {
        return card != null && card.id >= 180 && card.id <= 229;
    }

    public static Family family(ForbiddenBargain card) {
        if (!isExpanded(card)) return null;
        if (card.id <= 189) return Family.BALLISTICS;
        if (card.id <= 199) return Family.EDGEWORK;
        if (card.id <= 209) return Family.BULWARK;
        if (card.id <= 219) return Family.PILGRIMAGE;
        return Family.INDUSTRY;
    }

    public static boolean available(LockData data, UUID id, ForbiddenBargain card) {
        if (data == null || id == null || !isExpanded(card)) return false;
        return data.uniqueLong(id, timerKey(card)) == NONE;
    }

    public static void apply(ServerPlayer player, LockData data, ForbiddenBargain card) {
        if (player == null || data == null || !isExpanded(card)) return;
        int revision = CardEvolution.activeVariant(data, player.getUUID(), card);
        long duration = durationTicks(revision);
        data.setUniqueLong(player.getUUID(), timerKey(card), duration == PERMANENT
                ? PERMANENT : player.level().getGameTime() + duration);
        if (card == ForbiddenBargain.QUIET_LEDGER)
            data.setUniqueLong(player.getUUID(), LAST_HURT, player.level().getGameTime());
        tickOne(player, data, player.level().getGameTime());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player.getServer() == null) return;
        tickOne(player, LockData.get(player.getServer()), player.level().getGameTime());
    }

    private static void tickOne(ServerPlayer player, LockData data, long now) {
        UUID id = player.getUUID();
        // Expiry bookkeeping for all fifty cards, including clauses whose actual
        // effect only runs on damage/kill/block events. Without this sweep an
        // expired combat card could remain unavailable until combat happened again.
        if (player.tickCount % 20 == 0) {
            for (ForbiddenBargain card : EXPANDED_CARDS) active(data, id, card, now);
        }
        int threats = -1;

        boolean recoil = active(data, id, ForbiddenBargain.NAME_WITHOUT_RECOIL, now);
        syncModifier(player.getAttribute(Attributes.KNOCKBACK_RESISTANCE), modifierUuid(ForbiddenBargain.NAME_WITHOUT_RECOIL),
                "wardbound.name_without_recoil", recoil ? scaled(0.12D, 0.20D, 0.28D, data, id, ForbiddenBargain.NAME_WITHOUT_RECOIL) : 0D);

        boolean longStride = active(data, id, ForbiddenBargain.LONG_STRIDE, now);
        syncModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), modifierUuid(ForbiddenBargain.LONG_STRIDE),
                "wardbound.long_stride", longStride ? scaled(0.012D, 0.018D, 0.024D, data, id, ForbiddenBargain.LONG_STRIDE) : 0D);

        boolean sprint = active(data, id, ForbiddenBargain.SPRINT_CLAUSE, now);
        syncModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), modifierUuid(ForbiddenBargain.SPRINT_CLAUSE),
                "wardbound.sprint_clause", sprint && player.isSprinting()
                        ? scaled(0.014D, 0.021D, 0.028D, data, id, ForbiddenBargain.SPRINT_CLAUSE) : 0D);

        boolean emptyRoad = active(data, id, ForbiddenBargain.EMPTY_ROAD, now);
        boolean huntedRoad = active(data, id, ForbiddenBargain.HUNTED_ROAD, now);
        if (emptyRoad || huntedRoad) threats = hostileCount(player.serverLevel(), player, 10.0D);
        syncModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), modifierUuid(ForbiddenBargain.EMPTY_ROAD),
                "wardbound.empty_road", emptyRoad && threats == 0
                        ? scaled(0.014D, 0.021D, 0.028D, data, id, ForbiddenBargain.EMPTY_ROAD) : 0D);
        syncModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), modifierUuid(ForbiddenBargain.HUNTED_ROAD),
                "wardbound.hunted_road", huntedRoad && threats >= 3
                        ? scaled(0.016D, 0.024D, 0.032D, data, id, ForbiddenBargain.HUNTED_ROAD) : 0D);

        boolean sky = active(data, id, ForbiddenBargain.OPEN_SKY_FOOTNOTE, now);
        boolean skyVisible = sky && player.serverLevel().canSeeSky(player.blockPosition());
        syncModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), modifierUuid(ForbiddenBargain.OPEN_SKY_FOOTNOTE),
                "wardbound.open_sky_footnote", skyVisible
                        ? scaled(0.012D, 0.018D, 0.024D, data, id, ForbiddenBargain.OPEN_SKY_FOOTNOTE) : 0D);
        if (skyVisible && player.fallDistance > 3.0F && player.tickCount % 10 == 0)
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30, 0, true, false));

        boolean deep = active(data, id, ForbiddenBargain.DEEP_ROAD, now);
        boolean below = deep && player.getY() < 32.0D;
        syncModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), modifierUuid(ForbiddenBargain.DEEP_ROAD),
                "wardbound.deep_road", below
                        ? scaled(0.012D, 0.018D, 0.024D, data, id, ForbiddenBargain.DEEP_ROAD) : 0D);
        if (below && player.tickCount % 40 == 0)
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false));

        if (active(data, id, ForbiddenBargain.FEATHERED_DEBT, now) && player.fallDistance > 2.5F && player.tickCount % 10 == 0)
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 34, 0, true, false));

        if (active(data, id, ForbiddenBargain.CLIMBERS_MARGIN, now) && player.tickCount % 20 == 0) {
            int rev = CardEvolution.activeVariant(data, id, ForbiddenBargain.CLIMBERS_MARGIN);
            player.addEffect(new MobEffectInstance(MobEffects.JUMP, 50, rev >= 2 ? 1 : 0, true, false));
        }

        boolean longerHand = active(data, id, ForbiddenBargain.LONGER_HAND, now);
        syncModifier(player.getAttribute(ForgeMod.BLOCK_REACH.get()), modifierUuid(ForbiddenBargain.LONGER_HAND),
                "wardbound.longer_hand", longerHand
                        ? scaled(0.75D, 1.25D, 1.75D, data, id, ForbiddenBargain.LONGER_HAND) : 0D);

        boolean duelReach = active(data, id, ForbiddenBargain.DUELISTS_REACH, now);
        syncModifier(player.getAttribute(ForgeMod.ENTITY_REACH.get()), modifierUuid(ForbiddenBargain.DUELISTS_REACH),
                "wardbound.duelists_reach", duelReach
                        ? scaled(0.50D, 0.80D, 1.20D, data, id, ForbiddenBargain.DUELISTS_REACH) : 0D);

        boolean fortune = active(data, id, ForbiddenBargain.FORTUNE_MARGIN, now);
        syncModifier(player.getAttribute(Attributes.LUCK), modifierUuid(ForbiddenBargain.FORTUNE_MARGIN),
                "wardbound.fortune_margin", fortune
                        ? scaled(1.0D, 2.0D, 3.0D, data, id, ForbiddenBargain.FORTUNE_MARGIN) : 0D);

        if (active(data, id, ForbiddenBargain.SWIFT_TOOL, now)
                && player.getMainHandItem().getItem() instanceof DiggerItem && player.tickCount % 20 == 0) {
            int rev = CardEvolution.activeVariant(data, id, ForbiddenBargain.SWIFT_TOOL);
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 50, rev >= 2 ? 1 : 0, true, false));
        }

        if (active(data, id, ForbiddenBargain.CANDLEWORK, now)
                && player.getY() < 64.0D && player.serverLevel().getMaxLocalRawBrightness(player.blockPosition()) <= 7
                && player.tickCount % 40 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false));
        }

        if (active(data, id, ForbiddenBargain.QUIET_LEDGER, now) && player.isAlive() && player.tickCount % 40 == 0) {
            long last = data.uniqueLong(id, LAST_HURT);
            if (last == NONE) data.setUniqueLong(id, LAST_HURT, now);
            else if (now - last >= 200L && player.getHealth() < player.getMaxHealth()) {
                int rev = CardEvolution.activeVariant(data, id, ForbiddenBargain.QUIET_LEDGER);
                player.heal(rev >= 2 ? 1.0F : rev == 1 ? 0.75F : 0.50F);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(LivingHurtEvent event) {
        // Outgoing conditional damage.
        if (event.getSource().getEntity() instanceof ServerPlayer attacker && attacker.getServer() != null
                && attacker.level() instanceof ServerLevel level) {
            LockData data = LockData.get(attacker.getServer());
            UUID id = attacker.getUUID();
            long now = level.getGameTime();
            LivingEntity target = event.getEntity();
            float bonus = 0.0F;
            boolean projectile = event.getSource().getDirectEntity() instanceof Projectile;
            boolean melee = !projectile && event.getSource().getDirectEntity() == attacker;
            double distance = attacker.distanceTo(target);
            float targetRatio = target.getHealth() / Math.max(1.0F, target.getMaxHealth());
            int aroundTarget = -1;
            int aroundAttacker = -1;

            if (projectile) {
                if (active(data,id,ForbiddenBargain.PINHOLE_DOCTRINE,now) && distance >= 12.0D)
                    bonus += pct(data,id,ForbiddenBargain.PINHOLE_DOCTRINE,8,12,16);
                if (active(data,id,ForbiddenBargain.POINT_BLANK_RECEIPT,now) && distance <= 6.0D)
                    bonus += pct(data,id,ForbiddenBargain.POINT_BLANK_RECEIPT,10,15,20);
                if (active(data,id,ForbiddenBargain.HIGH_ARC_CLAUSE,now) && attacker.getY() - target.getY() >= 3.0D)
                    bonus += pct(data,id,ForbiddenBargain.HIGH_ARC_CLAUSE,10,15,20);
                if (active(data,id,ForbiddenBargain.UPWARD_INTEREST,now) && target.getY() - attacker.getY() >= 3.0D)
                    bonus += pct(data,id,ForbiddenBargain.UPWARD_INTEREST,10,15,20);
                if (active(data,id,ForbiddenBargain.FIRST_VOLLEY,now) && targetRatio >= 0.90F)
                    bonus += pct(data,id,ForbiddenBargain.FIRST_VOLLEY,12,18,24);
                if (active(data,id,ForbiddenBargain.LAST_BOLT,now) && targetRatio <= 0.33F)
                    bonus += pct(data,id,ForbiddenBargain.LAST_BOLT,12,18,24);
                if (active(data,id,ForbiddenBargain.SOLITARY_MARK,now)) {
                    aroundTarget = hostileCount(level,target,7.0D);
                    if (aroundTarget <= 1) bonus += pct(data,id,ForbiddenBargain.SOLITARY_MARK,10,15,20);
                }
                if (active(data,id,ForbiddenBargain.RUNNING_SIGHT,now) && attacker.isSprinting())
                    bonus += pct(data,id,ForbiddenBargain.RUNNING_SIGHT,8,12,16);
                if (active(data,id,ForbiddenBargain.STILL_HAND,now) && horizontalSpeed(attacker) < 0.020D)
                    bonus += pct(data,id,ForbiddenBargain.STILL_HAND,10,15,20);
                if (active(data,id,ForbiddenBargain.PIERCED_CROWD,now)) {
                    if (aroundTarget < 0) aroundTarget = hostileCount(level,target,7.0D);
                    int count = Math.min(4, Math.max(0, aroundTarget));
                    if (count > 0) bonus += count * pct(data,id,ForbiddenBargain.PIERCED_CROWD,3,4,5);
                }
            }

            if (melee) {
                if (active(data,id,ForbiddenBargain.RED_KNUCKLE,now)
                        && attacker.getHealth() / Math.max(1.0F, attacker.getMaxHealth()) < 0.50F)
                    bonus += pct(data,id,ForbiddenBargain.RED_KNUCKLE,8,12,16);
                if (active(data,id,ForbiddenBargain.OPENING_CUT,now) && targetRatio >= 0.90F)
                    bonus += pct(data,id,ForbiddenBargain.OPENING_CUT,10,15,20);
                if (active(data,id,ForbiddenBargain.EXECUTIONERS_MARGIN,now) && targetRatio <= 0.33F)
                    bonus += pct(data,id,ForbiddenBargain.EXECUTIONERS_MARGIN,12,18,24);
                if (active(data,id,ForbiddenBargain.LONE_DUEL,now)) {
                    aroundAttacker = hostileCount(level,attacker,8.0D);
                    if (aroundAttacker <= 1) bonus += pct(data,id,ForbiddenBargain.LONE_DUEL,10,15,20);
                }
                if (active(data,id,ForbiddenBargain.PRESSED_BLADE,now)) {
                    if (aroundAttacker < 0) aroundAttacker = hostileCount(level,attacker,8.0D);
                    if (aroundAttacker >= 4) bonus += pct(data,id,ForbiddenBargain.PRESSED_BLADE,10,15,20);
                }
                if (active(data,id,ForbiddenBargain.HIGH_GROUND,now) && attacker.getY() - target.getY() >= 1.5D)
                    bonus += pct(data,id,ForbiddenBargain.HIGH_GROUND,8,12,16);
                if (active(data,id,ForbiddenBargain.LOW_ROAD,now) && target.getY() - attacker.getY() >= 1.5D)
                    bonus += pct(data,id,ForbiddenBargain.LOW_ROAD,8,12,16);
                if (active(data,id,ForbiddenBargain.RUNNING_HAND,now) && attacker.isSprinting())
                    bonus += pct(data,id,ForbiddenBargain.RUNNING_HAND,8,12,16);
                if (active(data,id,ForbiddenBargain.STILL_POINT,now) && horizontalSpeed(attacker) < 0.020D)
                    bonus += pct(data,id,ForbiddenBargain.STILL_POINT,10,15,20);
                if (active(data,id,ForbiddenBargain.SECOND_WOUND,now) && targetRatio < 0.90F && targetRatio > 0.33F)
                    bonus += pct(data,id,ForbiddenBargain.SECOND_WOUND,8,12,16);
            }

            if (bonus > 0.0F) event.setAmount(event.getAmount() * (1.0F + Math.min(0.75F, bonus)));
        }

        // Incoming conditional mitigation and combat timestamp for Quiet Ledger.
        if (event.getEntity() instanceof ServerPlayer defender && defender.getServer() != null
                && defender.level() instanceof ServerLevel level) {
            LockData data = LockData.get(defender.getServer());
            UUID id = defender.getUUID();
            long now = level.getGameTime();
            data.setUniqueLong(id, LAST_HURT, now);
            float reduction = 0.0F;
            float healthRatio = defender.getHealth() / Math.max(1.0F, defender.getMaxHealth());
            int threats = -1;

            if (active(data,id,ForbiddenBargain.ASH_PLATE,now) && healthRatio >= 0.70F)
                reduction += pct(data,id,ForbiddenBargain.ASH_PLATE,8,12,16);
            if (active(data,id,ForbiddenBargain.LAST_PLATE,now) && healthRatio <= 0.35F)
                reduction += pct(data,id,ForbiddenBargain.LAST_PLATE,12,18,24);
            if (active(data,id,ForbiddenBargain.ARROW_LEDGER,now)
                    && event.getSource().getDirectEntity() instanceof Projectile)
                reduction += pct(data,id,ForbiddenBargain.ARROW_LEDGER,10,15,20);
            if (active(data,id,ForbiddenBargain.CLOSE_SEAL,now)
                    && event.getSource().getDirectEntity() instanceof LivingEntity
                    && event.getSource().getDirectEntity() == event.getSource().getEntity())
                reduction += pct(data,id,ForbiddenBargain.CLOSE_SEAL,8,12,16);
            if (active(data,id,ForbiddenBargain.CROWD_SHELTER,now)) {
                threats = hostileCount(level,defender,8.0D);
                if (threats >= 4) reduction += pct(data,id,ForbiddenBargain.CROWD_SHELTER,8,12,16);
            }
            if (active(data,id,ForbiddenBargain.SOLITARY_WARD,now)) {
                if (threats < 0) threats = hostileCount(level,defender,8.0D);
                if (threats <= 1) reduction += pct(data,id,ForbiddenBargain.SOLITARY_WARD,8,12,16);
            }
            if (active(data,id,ForbiddenBargain.FALLING_INK,now) && event.getSource().is(DamageTypeTags.IS_FALL))
                reduction += pct(data,id,ForbiddenBargain.FALLING_INK,20,35,50);
            if (active(data,id,ForbiddenBargain.FIRE_MARGIN,now) && event.getSource().is(DamageTypeTags.IS_FIRE))
                reduction += pct(data,id,ForbiddenBargain.FIRE_MARGIN,15,25,35);
            if (active(data,id,ForbiddenBargain.BLAST_RECEIPT,now) && event.getSource().is(DamageTypeTags.IS_EXPLOSION))
                reduction += pct(data,id,ForbiddenBargain.BLAST_RECEIPT,15,25,35);

            if (reduction > 0.0F) event.setAmount(event.getAmount() * (1.0F - Math.min(0.60F, reduction)));
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || player.getServer() == null
                || !(event.getEntity() instanceof Enemy) || !(player.level() instanceof ServerLevel level)) return;
        LockData data = LockData.get(player.getServer());
        UUID id = player.getUUID();
        long now = level.getGameTime();
        if (active(data,id,ForbiddenBargain.GRAVE_DIVIDEND,now)) {
            int rev = CardEvolution.activeVariant(data,id,ForbiddenBargain.GRAVE_DIVIDEND);
            player.heal(rev >= 2 ? 2.0F : rev == 1 ? 1.5F : 1.0F);
            level.sendParticles(ParticleTypes.SOUL, event.getEntity().getX(), event.getEntity().getY(0.7D), event.getEntity().getZ(),
                    3, 0.18D, 0.22D, 0.18D, 0.01D);
        }
        if (active(data,id,ForbiddenBargain.SCAVENGERS_NAME,now)) {
            int rev = CardEvolution.activeVariant(data,id,ForbiddenBargain.SCAVENGERS_NAME);
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, rev >= 2 ? 160 : rev == 1 ? 120 : 80,
                    rev >= 2 ? 1 : 0, true, false));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player) || player.isCreative()
                || player.getServer() == null || !(player.level() instanceof ServerLevel level)) return;
        LockData data = LockData.get(player.getServer());
        UUID id = player.getUUID();
        long now = level.getGameTime();
        BlockState state = event.getState();
        BlockPos pos = event.getPos();
        ItemStack tool = player.getMainHandItem();

        if (active(data,id,ForbiddenBargain.STONE_DIVIDEND,now) && state.is(BlockTags.MINEABLE_WITH_PICKAXE)
                && tool.getItem() instanceof DiggerItem && tool.getDestroySpeed(state) > 1.0F
                && tool.isCorrectToolForDrops(state) && level.getBlockEntity(pos) == null
                && player.getRandom().nextFloat() < chance(data,id,ForbiddenBargain.STONE_DIVIDEND,0.08F,0.12F,0.16F)) {
            copyOneNormalDrop(level, pos, state, player, tool);
        }
        if (active(data,id,ForbiddenBargain.TIMBER_SHARE,now) && state.is(BlockTags.LOGS)
                && player.getRandom().nextFloat() < chance(data,id,ForbiddenBargain.TIMBER_SHARE,0.15F,0.22F,0.30F)) {
            copyOneNormalDrop(level, pos, state, player, tool);
        }
        if (active(data,id,ForbiddenBargain.MASON_TITHE,now) && tool.isDamageableItem() && tool.getDamageValue() > 0
                && tool.getItem() instanceof DiggerItem && state.getDestroySpeed(level, pos) > 0.0F
                && tool.getDestroySpeed(state) > 1.0F && tool.isCorrectToolForDrops(state)
                && player.getRandom().nextFloat() < chance(data,id,ForbiddenBargain.MASON_TITHE,0.10F,0.16F,0.22F)) {
            tool.setDamageValue(Math.max(0, tool.getDamageValue() - 1));
        }
        if (active(data,id,ForbiddenBargain.HARVEST_MEMORY,now) && isMatureCrop(state)) {
            float bonus = chance(data,id,ForbiddenBargain.HARVEST_MEMORY,0.15F,0.25F,0.35F);
            List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), player, tool);
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
        }
    }

    private static void copyOneNormalDrop(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player, ItemStack tool) {
        if (level.getBlockEntity(pos) != null) return;
        List<ItemStack> drops = Block.getDrops(state, level, pos, null, player, tool);
        if (drops.isEmpty()) return;
        ItemStack stack = drops.get(player.getRandom().nextInt(drops.size()));
        if (stack.isEmpty() || stack.hasTag()) return;
        ItemStack copy = stack.copy();
        copy.setCount(1);
        Block.popResource(level, pos, copy);
    }

    private static boolean isMatureCrop(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) return crop.isMaxAge(state);
        if (block instanceof NetherWartBlock) return state.getValue(NetherWartBlock.AGE) >= 3;
        if (block instanceof CocoaBlock) return state.getValue(CocoaBlock.AGE) >= 2;
        if (block instanceof SweetBerryBushBlock) return state.getValue(SweetBerryBushBlock.AGE) >= 3;
        return false;
    }

    private static int hostileCount(ServerLevel level, LivingEntity center, double radius) {
        return level.getEntitiesOfClass(LivingEntity.class, center.getBoundingBox().inflate(radius),
                e -> e.isAlive() && e != center && e instanceof Enemy).size();
    }

    private static double horizontalSpeed(LivingEntity entity) {
        return entity.getDeltaMovement().horizontalDistance();
    }

    private static boolean active(LockData data, UUID id, ForbiddenBargain card, long now) {
        long until = data.uniqueLong(id, timerKey(card));
        if (until == NONE) return false;
        if (until == PERMANENT) return true;
        if (now < until) return true;
        data.setUniqueLong(id, timerKey(card), NONE);
        return false;
    }

    public static boolean activeForDisplay(LockData data, UUID id, ForbiddenBargain card, long now) {
        if (data == null || id == null || !isExpanded(card)) return false;
        long until = data.uniqueLong(id, timerKey(card));
        return until == PERMANENT || (until != NONE && until > now);
    }

    public static String status(LockData data, UUID id, ForbiddenBargain card, long now) {
        if (!isExpanded(card)) return "INACTIVE";
        long until = data.uniqueLong(id, timerKey(card));
        if (until == NONE || (until != PERMANENT && until <= now)) return "EXPIRED / READY TO RETURN";
        String strength = strengthText(data, id, card);
        if (until == PERMANENT) return "PALIMPSEST · PERMANENT · " + strength;
        long seconds = Math.max(0L, (until - now + 19L) / 20L);
        return String.format(Locale.ROOT, "ACTIVE · %d:%02d · %s", seconds / 60L, seconds % 60L, strength);
    }

    public static String variantTitle(ForbiddenBargain card, int level) {
        if (!isExpanded(card) || level <= 0) return card == null ? "Unknown Card" : card.title;
        if (level >= CardEvolution.MAX_REVISION) return card.title + ": Palimpsest";
        String suffix = switch (family(card)) {
            case BALLISTICS -> level == 1 ? "Annotated Trajectory" : "Redrafted Trajectory";
            case EDGEWORK -> level == 1 ? "Second Edge" : "Third Edge";
            case BULWARK -> level == 1 ? "Ash-Bound" : "Double-Bound";
            case PILGRIMAGE -> level == 1 ? "Longer Road" : "Unfolded Road";
            case INDUSTRY -> level == 1 ? "Second Accounting" : "Deep Accounting";
        };
        return card.title + ": " + suffix;
    }

    public static String variantText(ForbiddenBargain card, int level) {
        if (!isExpanded(card) || level <= 0) return card == null ? "" : card.debtText;
        String duration = level >= CardEvolution.MAX_REVISION ? "Permanent." : level >= 2 ? "For ten minutes." : "For seven minutes.";
        return duration + " " + strengthTextForRevision(card, level) + ".";
    }

    public static String strengthText(LockData data, UUID id, ForbiddenBargain card) {
        return strengthTextForRevision(card, CardEvolution.activeVariant(data, id, card));
    }

    private static String strengthTextForRevision(ForbiddenBargain card, int rev) {
        int tier = rev >= 2 ? 2 : rev == 1 ? 1 : 0;
        return switch (card) {
            case PINHOLE_DOCTRINE -> "+" + pick(tier,8,12,16) + "% projectile damage beyond 12 blocks";
            case POINT_BLANK_RECEIPT -> "+" + pick(tier,10,15,20) + "% projectile damage within 6 blocks";
            case HIGH_ARC_CLAUSE -> "+" + pick(tier,10,15,20) + "% projectile damage from high ground";
            case UPWARD_INTEREST -> "+" + pick(tier,10,15,20) + "% projectile damage into high ground";
            case FIRST_VOLLEY -> "+" + pick(tier,12,18,24) + "% projectile damage against near-full targets";
            case LAST_BOLT -> "+" + pick(tier,12,18,24) + "% projectile damage against low-health targets";
            case SOLITARY_MARK -> "+" + pick(tier,10,15,20) + "% projectile damage against isolated targets";
            case RUNNING_SIGHT -> "+" + pick(tier,8,12,16) + "% projectile damage while sprinting";
            case STILL_HAND -> "+" + pick(tier,10,15,20) + "% projectile damage while nearly still";
            case PIERCED_CROWD -> "+" + pick(tier,3,4,5) + "% projectile damage per nearby hostile, up to four";
            case RED_KNUCKLE -> "+" + pick(tier,8,12,16) + "% melee damage below half health";
            case OPENING_CUT -> "+" + pick(tier,10,15,20) + "% melee damage against near-full targets";
            case EXECUTIONERS_MARGIN -> "+" + pick(tier,12,18,24) + "% melee damage against low-health targets";
            case LONE_DUEL -> "+" + pick(tier,10,15,20) + "% melee damage in isolated fights";
            case PRESSED_BLADE -> "+" + pick(tier,10,15,20) + "% melee damage under crowd pressure";
            case HIGH_GROUND -> "+" + pick(tier,8,12,16) + "% melee damage from high ground";
            case LOW_ROAD -> "+" + pick(tier,8,12,16) + "% melee damage into high ground";
            case RUNNING_HAND -> "+" + pick(tier,8,12,16) + "% melee damage while sprinting";
            case STILL_POINT -> "+" + pick(tier,10,15,20) + "% melee damage while nearly still";
            case SECOND_WOUND -> "+" + pick(tier,8,12,16) + "% melee damage against wounded mid-health targets";
            case ASH_PLATE -> pick(tier,8,12,16) + "% damage reduction above 70% health";
            case LAST_PLATE -> pick(tier,12,18,24) + "% damage reduction below 35% health";
            case ARROW_LEDGER -> pick(tier,10,15,20) + "% projectile damage reduction";
            case CLOSE_SEAL -> pick(tier,8,12,16) + "% direct-creature damage reduction";
            case CROWD_SHELTER -> pick(tier,8,12,16) + "% damage reduction with four nearby hostiles";
            case SOLITARY_WARD -> pick(tier,8,12,16) + "% damage reduction in isolated fights";
            case FALLING_INK -> pick(tier,20,35,50) + "% fall damage reduction";
            case FIRE_MARGIN -> pick(tier,15,25,35) + "% fire damage reduction";
            case BLAST_RECEIPT -> pick(tier,15,25,35) + "% explosion damage reduction";
            case NAME_WITHOUT_RECOIL -> "+" + pick(tier,12,20,28) + "% knockback resistance";
            case LONG_STRIDE -> "+" + pick(tier,12,18,24) + "% base movement speed";
            case SPRINT_CLAUSE -> "additional sprint speed";
            case EMPTY_ROAD -> "additional speed while no hostiles are close";
            case HUNTED_ROAD -> "additional speed while three hostiles are close";
            case OPEN_SKY_FOOTNOTE -> "open-sky speed and automatic Slow Falling on long drops";
            case DEEP_ROAD -> "deep-underground speed and Night Vision";
            case FEATHERED_DEBT -> "automatic Slow Falling on long drops";
            case CLIMBERS_MARGIN -> tier >= 2 ? "Jump Boost II" : "Jump Boost I";
            case LONGER_HAND -> "+" + (tier==2?1.75:tier==1?1.25:0.75) + " block reach";
            case DUELISTS_REACH -> "+" + (tier==2?1.20:tier==1?0.80:0.50) + " entity reach";
            case STONE_DIVIDEND -> pick(tier,8,12,16) + "% chance for one extra pickaxe-mined drop";
            case TIMBER_SHARE -> pick(tier,15,22,30) + "% chance for one extra log drop";
            case MASON_TITHE -> pick(tier,10,16,22) + "% chance to repair one tool durability per block";
            case FORTUNE_MARGIN -> "+" + pick(tier,1,2,3) + " Luck";
            case SWIFT_TOOL -> tier >= 2 ? "Haste II while holding a digging tool" : "Haste I while holding a digging tool";
            case HARVEST_MEMORY -> "+" + pick(tier,15,25,35) + "% expected mature-crop yield";
            case GRAVE_DIVIDEND -> "hostile kills restore " + (tier==2?2.0:tier==1?1.5:1.0) + " health";
            case SCAVENGERS_NAME -> tier >= 2 ? "hostile kills grant Absorption II" : "hostile kills grant brief Absorption I";
            case CANDLEWORK -> "Night Vision in low underground light";
            case QUIET_LEDGER -> "recovery begins after ten seconds without taking damage";
            default -> "active revised clause";
        };
    }

    private static long durationTicks(int revision) {
        if (revision >= CardEvolution.MAX_REVISION) return PERMANENT;
        long minutes = revision >= 2 ? 10L : revision == 1 ? 7L : 4L;
        return 20L * 60L * minutes;
    }

    private static String timerKey(ForbiddenBargain card) {
        return "expanded_progression_until_" + card.id;
    }

    private static UUID modifierUuid(ForbiddenBargain card) {
        return UUID.nameUUIDFromBytes(("wardbound.expanded." + card.id).getBytes(StandardCharsets.UTF_8));
    }

    private static void syncModifier(AttributeInstance attribute, UUID uuid, String name, double amount) {
        if (attribute == null) return;
        AttributeModifier current = attribute.getModifier(uuid);
        if (Math.abs(amount) <= 0.0001D) {
            if (current != null) attribute.removeModifier(uuid);
            return;
        }
        if (current != null && Math.abs(current.getAmount() - amount) < 0.0001D) return;
        if (current != null) attribute.removeModifier(uuid);
        attribute.addTransientModifier(new AttributeModifier(uuid, name, amount, AttributeModifier.Operation.ADDITION));
    }

    private static float pct(LockData data, UUID id, ForbiddenBargain card, int base, int r1, int r2) {
        return pick(CardEvolution.activeVariant(data,id,card) >= 2 ? 2 : CardEvolution.activeVariant(data,id,card) == 1 ? 1 : 0, base,r1,r2) / 100.0F;
    }

    private static float chance(LockData data, UUID id, ForbiddenBargain card, float base, float r1, float r2) {
        int rev = CardEvolution.activeVariant(data,id,card);
        return rev >= 2 ? r2 : rev == 1 ? r1 : base;
    }

    private static double scaled(double base, double r1, double r2, LockData data, UUID id, ForbiddenBargain card) {
        int rev = CardEvolution.activeVariant(data,id,card);
        return rev >= 2 ? r2 : rev == 1 ? r1 : base;
    }

    private static int pick(int tier, int base, int r1, int r2) {
        return tier >= 2 ? r2 : tier == 1 ? r1 : base;
    }
}
