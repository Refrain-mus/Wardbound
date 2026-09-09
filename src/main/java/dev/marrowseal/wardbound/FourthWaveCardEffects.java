package dev.marrowseal.wardbound;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Runtime for the integrated 403..407 world-law wave. */
@Mod.EventBusSubscriber(modid = Wardbound.MODID)
public final class FourthWaveCardEffects {
    private FourthWaveCardEffects() {}

    private static final String LAW_MIRROR = "fourth_mirror_writ";
    private static final String LAW_ANATOMY = "fourth_borrowed_anatomy";
    private static final String LAW_NINTH = "fourth_ninth_margin";
    private static final String LAW_CORPSE = "fourth_corpse_ledger";
    private static final String LAW_CHAIN = "fourth_chain_custody";

    private static final int COPY_NONE = 0;
    private static final int COPY_UNDEAD = 1;
    private static final int COPY_SKELETON = 2;
    private static final int COPY_SPIDER = 3;
    private static final int COPY_CREEPER = 4;
    private static final int COPY_ENDER = 5;
    private static final int COPY_BLAZE = 6;
    private static final int COPY_AQUATIC = 7;
    private static final int COPY_WITCH = 8;
    private static final int COPY_GENERIC = 9;

    private static final List<PendingChain> PENDING_CHAINS = new ArrayList<>();
    private static final Set<UUID> SYNTHETIC_CHAIN = new HashSet<>();
    private record PendingChain(UUID owner, String dimension, BlockPos origin, Block block, long due, int limit) {}

    public static boolean isFourthWave(ForbiddenBargain card) {
        return card != null && card.id >= 403 && card.id <= 407;
    }

    public static boolean available(LockData data, UUID id, ForbiddenBargain card) {
        if (data == null || id == null || !isFourthWave(card)) return false;
        return switch (card) {
            case MIRROR_WRIT -> !data.hasUnique(id, LAW_MIRROR);
            case BORROWED_ANATOMY -> !data.hasUnique(id, LAW_ANATOMY);
            case NINTH_MARGIN -> !data.hasUnique(id, LAW_NINTH);
            case CORPSE_LEDGER -> !data.hasUnique(id, LAW_CORPSE);
            case CHAIN_OF_CUSTODY -> !data.hasUnique(id, LAW_CHAIN);
            default -> false;
        };
    }

    public static void apply(ServerPlayer player, LockData data, ForbiddenBargain card) {
        if (player == null || data == null || !isFourthWave(card)) return;
        UUID id = player.getUUID();
        switch (card) {
            case MIRROR_WRIT -> data.setUnique(id, LAW_MIRROR, true);
            case BORROWED_ANATOMY -> data.setUnique(id, LAW_ANATOMY, true);
            case NINTH_MARGIN -> data.setUnique(id, LAW_NINTH, true);
            case CORPSE_LEDGER -> {
                data.setUnique(id, LAW_CORPSE, true);
                data.addDeathHeartDebt(id, 1);
            }
            case CHAIN_OF_CUSTODY -> data.setUnique(id, LAW_CHAIN, true);
            default -> { }
        }
    }

    public static boolean isSyntheticChainBreak(UUID id) {
        return id != null && SYNTHETIC_CHAIN.contains(id);
    }

    private static boolean has(LockData data, UUID id, String key) {
        return data != null && id != null && data.hasUnique(id, key);
    }

    // ---------------------------------------------------------------- projectile mirror

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile().level() instanceof ServerLevel level)) return;
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit) || !(hit.getEntity() instanceof ServerPlayer player)) return;
        if (player.getServer() == null) return;
        Projectile projectile = event.getProjectile();
        if (projectile.getOwner() == player || projectile.getDeltaMovement().lengthSqr() < 0.01D) return;

        LockData data = LockData.get(player.getServer());
        UUID id = player.getUUID();
        if (!has(data, id, LAW_MIRROR)) return;
        long now = level.getGameTime();
        if (now < data.uniqueLong(id, "fourth_mirror_ready")) return;

        Vec3 motion = projectile.getDeltaMovement().normalize();
        Vec3 look = player.getLookAngle().normalize();
        int echo = CardEchoSystem.multiplier(data, id, ForbiddenBargain.MIRROR_WRIT);
        double facingCut = echo > 1 ? -0.12D : -0.28D;
        if (look.dot(motion) > facingCut) return; // player must actually face the incoming line

        Entity oldOwner = projectile.getOwner();
        Vec3 start = player.getEyePosition().add(look.scale(0.85D));
        Vec3 reflected;
        if (oldOwner instanceof LivingEntity living && living.isAlive()) {
            reflected = living.getEyePosition().subtract(start).normalize();
        } else reflected = motion.scale(-1.0D);

        boolean returnToSender = data.hasUnique(id, "third_law_358");
        double speed = Math.max(0.9D, projectile.getDeltaMovement().length()) * (returnToSender ? 1.35D : 1.18D) * (echo > 1 ? 1.18D : 1.0D);
        projectile.setOwner(player);
        projectile.setPos(start.x, start.y, start.z);
        projectile.setDeltaMovement(reflected.scale(speed));
        projectile.hurtMarked = true;
        long cooldown = returnToSender ? 28L : 42L;
        if (echo > 1) cooldown = Math.max(16L, cooldown - 12L);
        data.setUniqueLong(id, "fourth_mirror_ready", now + cooldown);
        data.setUniqueInt(id, "fourth_mirror_returns", data.uniqueInt(id, "fourth_mirror_returns") + 1);
        // 1.20.1 Forge exposes an explicit impact result.  SKIP_ENTITY avoids
        // the old cancellation/piercing-arrow re-hit loop while still letting
        // the reflected projectile continue with its rewritten owner/vector.
        event.setImpactResult(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
        level.sendParticles(ParticleTypes.ENCHANT, start.x, start.y, start.z, 16, .18, .18, .18, .03);
        level.playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, .72f, 1.55f);
    }

    // ---------------------------------------------------------------- anatomy / kill memory

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide()) return;

        // Corpse debt is recorded independently of who caused the death.
        if (dead instanceof ServerPlayer fallen && fallen.getServer() != null) {
            LockData data = LockData.get(fallen.getServer());
            UUID id = fallen.getUUID();
            if (has(data, id, LAW_CORPSE)) {
                int stacks = Math.min(3, Math.max(0, data.uniqueInt(id, "fourth_corpse_stacks")) + 1);
                data.setUniqueInt(id, "fourth_corpse_stacks", stacks);
                data.setUniqueInt(id, "fourth_corpse_x", fallen.blockPosition().getX());
                data.setUniqueInt(id, "fourth_corpse_y", fallen.blockPosition().getY());
                data.setUniqueInt(id, "fourth_corpse_z", fallen.blockPosition().getZ());
                String corpseDimension=fallen.level().dimension().location().toString();
                data.setUniqueString(id, "fourth_corpse_dimension", corpseDimension);
                // Keep the legacy hash for existing 39.8.x saves; new logic prefers the collision-free string.
                data.setUniqueInt(id, "fourth_corpse_dim", corpseDimension.hashCode());
                data.setUnique(id, "fourth_corpse_due", true);
                WardHistory.recordSpecial(fallen, "DEATH", "The Corpse Ledger records a debt at " + fallen.blockPosition().toShortString() + ".");
            }
        }

        if (!(event.getSource().getEntity() instanceof ServerPlayer killer) || killer.getServer() == null || !(dead instanceof Enemy)) return;
        LockData data = LockData.get(killer.getServer());
        UUID id = killer.getUUID();
        if (!has(data, id, LAW_ANATOMY) && !has(data, id, LAW_NINTH)) return;
        int before = data.uniqueInt(id, "fourth_copy_type");
        int copy = classify(dead);
        data.setUniqueInt(id, "fourth_copy_type", copy);
        data.setUniqueInt(id, "fourth_copy_entity", BuiltInRegistries.ENTITY_TYPE.getId(dead.getType()));
        if (copy != before) {
            WardHud.message(killer, Component.literal("BORROWED FORM // " + copyName(copy))
                    .withStyle(ChatFormatting.DARK_AQUA), false);
        }
    }

    private static int classify(LivingEntity entity) {
        String path = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
        if (path.contains("skeleton") || path.contains("stray") || path.contains("bogged")) return COPY_SKELETON;
        if (path.contains("spider")) return COPY_SPIDER;
        if (path.contains("creeper")) return COPY_CREEPER;
        if (path.contains("enderman") || path.contains("endermite")) return COPY_ENDER;
        if (path.contains("blaze") || path.contains("magma_cube")) return COPY_BLAZE;
        if (path.contains("drowned") || path.contains("guardian") || path.contains("squid")) return COPY_AQUATIC;
        if (path.contains("witch")) return COPY_WITCH;
        if (path.contains("zombie") || path.contains("husk") || path.contains("phantom") || path.contains("zombified")) return COPY_UNDEAD;
        return COPY_GENERIC;
    }

    private static String copyName(int type) {
        return switch (type) {
            case COPY_UNDEAD -> "grave endurance";
            case COPY_SKELETON -> "bone trajectory";
            case COPY_SPIDER -> "silk step";
            case COPY_CREEPER -> "fuse skin";
            case COPY_ENDER -> "folded distance";
            case COPY_BLAZE -> "cinder blood";
            case COPY_AQUATIC -> "drowned lung";
            case COPY_WITCH -> "witch measure";
            case COPY_GENERIC -> "predator draft";
            default -> "blank margin";
        };
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        // Defensive borrowed anatomy.
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            LockData data = LockData.get(player.getServer());
            UUID id = player.getUUID();
            if (has(data, id, LAW_ANATOMY)) {
                int copy = data.uniqueInt(id, "fourth_copy_type");
                int echo = CardEchoSystem.multiplier(data, id, ForbiddenBargain.BORROWED_ANATOMY);
                if (copy == COPY_CREEPER && event.getSource().is(DamageTypeTags.IS_EXPLOSION)) {
                    event.setAmount(event.getAmount() * (echo > 1 ? .45f : .65f));
                }
                if (copy == COPY_ENDER && event.getSource().getDirectEntity() instanceof Projectile
                        && player.level() instanceof ServerLevel level
                        && level.getGameTime() >= data.uniqueLong(id, "fourth_ender_ready")) {
                    if (foldForward(player, level, echo > 1 ? 8.5D : 6.0D)) {
                        data.setUniqueLong(id, "fourth_ender_ready", level.getGameTime() + (echo > 1 ? 50L : 80L));
                        event.setAmount(event.getAmount() * .55f);
                    }
                }
            }
        }

        // Offensive borrowed anatomy.
        if (event.getSource().getEntity() instanceof ServerPlayer attacker && attacker.getServer() != null) {
            LockData data = LockData.get(attacker.getServer());
            UUID id = attacker.getUUID();
            if (!has(data, id, LAW_ANATOMY)) return;
            int copy = data.uniqueInt(id, "fourth_copy_type");
            int echo = CardEchoSystem.multiplier(data, id, ForbiddenBargain.BORROWED_ANATOMY);
            if (copy == COPY_SKELETON && event.getSource().getDirectEntity() instanceof Projectile)
                event.setAmount(event.getAmount() * (echo > 1 ? 1.40f : 1.20f));
            if (copy == COPY_BLAZE && event.getSource().getDirectEntity() == attacker)
                event.getEntity().setSecondsOnFire(echo > 1 ? 7 : 4);
        }
    }

    // ---------------------------------------------------------------- player tick / corpse return / passive copy / chain scheduler

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.getServer() == null) return;
        LockData data = LockData.get(player.getServer());
        UUID id = player.getUUID();
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();

        if (player.tickCount % 20 == 0 && has(data, id, LAW_ANATOMY)) {
            int type = data.uniqueInt(id, "fourth_copy_type");
            int echo = CardEchoSystem.multiplier(data, id, ForbiddenBargain.BORROWED_ANATOMY);
            int amp = echo > 1 ? 1 : 0;
            switch (type) {
                case COPY_UNDEAD -> player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 45, amp, true, false, true));
                case COPY_SPIDER -> {
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 45, amp, true, false, true));
                    player.addEffect(new MobEffectInstance(MobEffects.JUMP, 45, 0, true, false, true));
                }
                case COPY_BLAZE -> player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 45, 0, true, false, true));
                case COPY_AQUATIC -> {
                    player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 45, 0, true, false, true));
                    player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 45, 0, true, false, true));
                }
                case COPY_WITCH -> player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 45, 0, true, false, true));
                case COPY_GENERIC -> player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 45, amp, true, false, true));
                default -> { }
            }
        }

        if (has(data, id, LAW_CORPSE) && data.hasUnique(id, "fourth_corpse_due")) {
            int stacks = Math.max(1, Math.min(3, data.uniqueInt(id, "fourth_corpse_stacks")));
            if (player.tickCount % 20 == 0) {
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 45, stacks - 1, true, false, true));
                if (stacks >= 2) player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 45, stacks - 2, true, false, true));
            }
            String currentDimension=player.level().dimension().location().toString();
            String storedDimension=data.uniqueString(id,"fourth_corpse_dimension");
            boolean sameDimension=!storedDimension.isBlank()
                    ? currentDimension.equals(storedDimension)
                    : currentDimension.hashCode()==data.uniqueInt(id,"fourth_corpse_dim");
            if (sameDimension) {
                BlockPos corpse = new BlockPos(data.uniqueInt(id, "fourth_corpse_x"), data.uniqueInt(id, "fourth_corpse_y"), data.uniqueInt(id, "fourth_corpse_z"));
                if (player.blockPosition().distSqr(corpse) <= 16.0D) {
                    data.setUnique(id, "fourth_corpse_due", false);
                    data.setUniqueInt(id, "fourth_corpse_stacks", 0);
                    // The debt is settled; discard the corpse address so stale coordinates cannot
                    // be mistaken for a future debt and old saves do not accumulate dead state.
                    data.setUniqueString(id, "fourth_corpse_dimension", "");
                    data.setUniqueInt(id, "fourth_corpse_dim", 0);
                    data.setUniqueInt(id, "fourth_corpse_x", 0);
                    data.setUniqueInt(id, "fourth_corpse_y", 0);
                    data.setUniqueInt(id, "fourth_corpse_z", 0);
                    int echo = CardEchoSystem.multiplier(data, id, ForbiddenBargain.CORPSE_LEDGER);
                    player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * (echo > 1 ? 45 : 25), echo > 1 ? 1 : 0, false, true, true));
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 12, 0, false, true, true));
                    DeathResonance.add(player, data, echo > 1 ? 10 : 5, "a corpse debt was personally reclaimed");
                    level.sendParticles(ParticleTypes.SOUL, corpse.getX()+.5, corpse.getY()+.8, corpse.getZ()+.5, 24, .35, .55, .35, .03);
                    level.playSound(null, corpse, SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, .8f, .78f);
                    WardHud.message(player, Component.literal("CORPSE DEBT CLOSED // the place recognizes you")
                            .withStyle(ChatFormatting.DARK_GREEN), false);
                }
            }
        }

        if (has(data, id, LAW_NINTH) && player.getInventory().selected == 8 && player.getInventory().getItem(8).isEmpty()) {
            int prev = data.uniqueInt(id, "fourth_ninth_selected");
            if (prev == 0) {
                data.setUniqueInt(id, "fourth_ninth_selected", 1);
                WardHud.message(player, Component.literal("NINTH MARGIN // " + copyName(data.uniqueInt(id, "fourth_copy_type")) + " // press R")
                        .withStyle(ChatFormatting.AQUA), false);
            }
        } else if (data.uniqueInt(id, "fourth_ninth_selected") != 0) data.setUniqueInt(id, "fourth_ninth_selected", 0);

        processChains(player, data, now);
    }

    // ---------------------------------------------------------------- Ninth Margin active cast

    public static void castNinthMargin(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        LockData data = LockData.get(player.getServer());
        UUID id = player.getUUID();
        if (!has(data, id, LAW_NINTH)) return;
        if (player.getInventory().selected != 8 || !player.getInventory().getItem(8).isEmpty()) {
            WardHud.message(player, Component.literal("The Ninth Margin only opens while the ninth hotbar slot is selected and empty.")
                    .withStyle(ChatFormatting.GRAY), false);
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        long ready = data.uniqueLong(id, "fourth_spell_ready");
        if (ready > now) {
            long sec = Math.max(1, (ready - now + 19L) / 20L);
            WardHud.message(player, Component.literal("Ninth Margin cooling // " + sec + "s").withStyle(ChatFormatting.DARK_GRAY), false);
            return;
        }
        int type = data.uniqueInt(id, "fourth_copy_type");
        if (type == COPY_NONE) type = COPY_GENERIC;
        boolean anatomy = has(data, id, LAW_ANATOMY);
        int echo = CardEchoSystem.multiplier(data, id, ForbiddenBargain.NINTH_MARGIN);
        float power = (anatomy ? 1.25f : 1.0f) * (echo > 1 ? 1.35f : 1.0f);
        long cooldown = anatomy ? 20L * 18L : 20L * 24L;
        if (echo > 1) cooldown = cooldown * 3L / 4L;

        switch (type) {
            case COPY_UNDEAD -> {
                player.heal(4.0f * power);
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 8, echo > 1 ? 1 : 0, false, true, true));
            }
            case COPY_SKELETON -> {
                int shots = echo > 1 ? 5 : 3;
                for (int i = 0; i < shots; i++) {
                    Arrow arrow = new Arrow(level, player);
                    float yaw = player.getYRot() + (i - (shots-1)/2f) * 6.0f;
                    arrow.shootFromRotation(player, player.getXRot(), yaw, 0.0f, 2.7f * power, 0.25f);
                    arrow.setBaseDamage(2.0D * power);
                    level.addFreshEntity(arrow);
                }
            }
            case COPY_SPIDER -> {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 12, echo > 1 ? 3 : 2, false, true, true));
                player.addEffect(new MobEffectInstance(MobEffects.JUMP, 20 * 12, 1, false, true, true));
            }
            case COPY_CREEPER -> {
                level.explode(player, player.getX(), player.getY()+.2, player.getZ(), 2.3f * power, net.minecraft.world.level.Level.ExplosionInteraction.NONE);
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 5, 2, false, true, true));
            }
            case COPY_ENDER -> foldForward(player, level, (echo > 1 ? 13.0D : 9.0D) * (anatomy ? 1.15D : 1.0D));
            case COPY_BLAZE -> {
                for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(6.0D),
                        e -> e.isAlive() && e instanceof Enemy)) {
                    target.setSecondsOnFire(echo > 1 ? 10 : 6);
                    target.hurt(level.damageSources().magic(), 3.0f * power);
                }
                player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 15, 0, false, true, true));
            }
            case COPY_AQUATIC -> {
                player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 20 * 30, 0, false, true, true));
                player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 20 * 15, echo > 1 ? 1 : 0, false, true, true));
                player.heal(2.0f * power);
            }
            case COPY_WITCH -> {
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 12, echo > 1 ? 2 : 1, false, true, true));
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 20, echo > 1 ? 1 : 0, false, true, true));
            }
            default -> {
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 10, echo > 1 ? 2 : 1, false, true, true));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 10, 1, false, true, true));
            }
        }
        player.causeFoodExhaustion(anatomy ? 1.0f : 1.4f);
        data.setUniqueLong(id, "fourth_spell_ready", now + cooldown);
        data.setUniqueInt(id, "fourth_spells_cast", data.uniqueInt(id, "fourth_spells_cast") + 1);
        level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY()+1, player.getZ(), 22, .4, .6, .4, .04);
        level.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, .78f, .62f);
        WardHud.message(player, Component.literal("NINTH MARGIN CAST // " + copyName(type)).withStyle(ChatFormatting.AQUA), false);
    }

    private static boolean foldForward(ServerPlayer player, ServerLevel level, double distance) {
        Vec3 look = player.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < .01D) return false;
        look = look.normalize();
        for (double d = distance; d >= 2.0D; d -= 1.0D) {
            Vec3 dest = player.position().add(look.scale(d));
            BlockPos feet = BlockPos.containing(dest);
            if (!level.getWorldBorder().isWithinBounds(feet)) continue;
            if (!level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)) continue;
            if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) continue;
            if (!level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) continue;
            level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY()+1, player.getZ(), 16, .3, .6, .3, .04);
            player.teleportTo(level, dest.x, dest.y, dest.z, player.getYRot(), player.getXRot());
            level.sendParticles(ParticleTypes.PORTAL, dest.x, dest.y+1, dest.z, 16, .3, .6, .3, .04);
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- Chain of Custody

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || player.getServer() == null || event.isCanceled()) return;
        UUID id = player.getUUID();
        if (SYNTHETIC_CHAIN.contains(id)) return;
        LockData data = LockData.get(player.getServer());
        if (!has(data, id, LAW_CHAIN) || !player.isShiftKeyDown()) return;
        BlockState state = event.getState();
        if (!(state.is(Tags.Blocks.ORES) || state.is(BlockTags.LOGS))) return;
        if (!player.hasCorrectToolForDrops(state)) return;
        int echo = CardEchoSystem.multiplier(data, id, ForbiddenBargain.CHAIN_OF_CUSTODY);
        int limit = echo > 1 ? 12 : 6;
        synchronized (PENDING_CHAINS) {
            PENDING_CHAINS.add(new PendingChain(id, player.level().dimension().location().toString(), event.getPos().immutable(), state.getBlock(), player.level().getGameTime()+1L, limit));
        }
    }

    private static void processChains(ServerPlayer player, LockData data, long now) {
        List<PendingChain> mine = new ArrayList<>();
        synchronized (PENDING_CHAINS) {
            Iterator<PendingChain> it = PENDING_CHAINS.iterator();
            while (it.hasNext()) {
                PendingChain chain = it.next();
                if (!chain.owner.equals(player.getUUID())) continue;
                if (!chain.dimension.equals(player.level().dimension().location().toString())) {
                    // A one-tick mining continuation belongs to the world in which it was created.
                    // Never reinterpret its BlockPos after portal/dimension travel.
                    it.remove();
                    continue;
                }
                if (chain.due <= now) {
                    mine.add(chain);
                    it.remove();
                }
            }
        }
        for (PendingChain chain : mine) breakConnected(player, data, chain);
    }

    private static void breakConnected(ServerPlayer player, LockData data, PendingChain chain) {
        if (!(player.level() instanceof ServerLevel level)) return;
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        queue.add(chain.origin);
        seen.add(chain.origin);
        int broken = 0;
        SYNTHETIC_CHAIN.add(player.getUUID());
        try {
            while (!queue.isEmpty() && broken < chain.limit) {
                BlockPos base = queue.removeFirst();
                for (Direction dir : Direction.values()) {
                    if (broken >= chain.limit) break;
                    BlockPos next = base.relative(dir);
                    if (!seen.add(next)) continue;
                    BlockState state = level.getBlockState(next);
                    if (state.getBlock() != chain.block) continue;
                    if (!player.hasCorrectToolForDrops(state)) continue;
                    if (player.gameMode.destroyBlock(next)) {
                        broken++;
                        queue.addLast(next.immutable());
                        player.causeFoodExhaustion(.45f);
                        level.sendParticles(ParticleTypes.CRIT, next.getX()+.5, next.getY()+.5, next.getZ()+.5, 3, .18, .18, .18, .01);
                    }
                }
            }
        } finally {
            SYNTHETIC_CHAIN.remove(player.getUUID());
        }
        if (broken > 0) {
            level.playSound(null, player.blockPosition(), SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, .42f, 1.35f);
            data.setUniqueInt(player.getUUID(), "fourth_chain_blocks", data.uniqueInt(player.getUUID(), "fourth_chain_blocks") + broken);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id=event.getEntity().getUUID();
        synchronized (PENDING_CHAINS) { PENDING_CHAINS.removeIf(chain->chain.owner.equals(id)); }
        SYNTHETIC_CHAIN.remove(id);
    }

    public static String status(LockData data, UUID id, ForbiddenBargain card) {
        if (data == null || id == null || card == null) return "INACTIVE";
        return switch (card) {
            case MIRROR_WRIT -> has(data,id,LAW_MIRROR) ? "ACTIVE · " + data.uniqueInt(id,"fourth_mirror_returns") + " projectile return(s)" : "INACTIVE";
            case BORROWED_ANATOMY -> has(data,id,LAW_ANATOMY) ? "ACTIVE · borrowed " + copyName(data.uniqueInt(id,"fourth_copy_type")) : "INACTIVE";
            case NINTH_MARGIN -> has(data,id,LAW_NINTH) ? "ACTIVE · virtual slot 9 · " + data.uniqueInt(id,"fourth_spells_cast") + " cast(s)" : "INACTIVE";
            case CORPSE_LEDGER -> has(data,id,LAW_CORPSE) ? (data.hasUnique(id,"fourth_corpse_due") ? "ACTIVE · CORPSE DEBT x"+Math.max(1,data.uniqueInt(id,"fourth_corpse_stacks")) : "ACTIVE · no unpaid corpse") : "INACTIVE";
            case CHAIN_OF_CUSTODY -> has(data,id,LAW_CHAIN) ? "ACTIVE · " + data.uniqueInt(id,"fourth_chain_blocks") + " chained block(s)" : "INACTIVE";
            default -> "INACTIVE";
        };
    }
}
