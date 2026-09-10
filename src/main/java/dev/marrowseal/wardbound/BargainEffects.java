package dev.marrowseal.wardbound;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import dev.marrowseal.wardbound.item.SealedCardItem;
import dev.marrowseal.wardbound.item.CthulhuEyeItem;
import dev.marrowseal.wardbound.item.WardItems;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.common.Tags;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

/**
 * Server-authoritative standing card effects. Some cards are ordinary potion-like
 * scars, while master/epic cards can install persistent rules that affect normal
 * exploration and combat outside Wardbound minigames.
 */
@Mod.EventBusSubscriber(modid = Wardbound.MODID)
public final class BargainEffects {
    private BargainEffects() {}

    private static final UUID HEART_DEBT_ID = UUID.fromString("8d726db4-66d2-45ad-aee0-8bf3a75b3b11");
    private static final String CROOKED_NEXT = "WardboundCrookedStepNext";
    private static final String VEILED_STILL = "WardboundVeiledStill";
    private static final String VEILED_NEXT = "WardboundVeiledNext";
    private static final String EXACTING_NEXT = "WardboundExactingNext";
    private static final String RED_MARCH_NEXT = "WardboundRedMarchNext";
    private static final String HIDDEN_PAGE_DAY = "WardboundHiddenPageDay";

    private static final TagKey<EntityType<?>> ZOMBIE_FAMILY = entityFamilyTag("zombie_family");
    private static final TagKey<EntityType<?>> SKELETON_FAMILY = entityFamilyTag("skeleton_family");
    private static final TagKey<EntityType<?>> CREEPER_FAMILY = entityFamilyTag("creeper_family");
    private static final TagKey<EntityType<?>> SPIDER_FAMILY = entityFamilyTag("spider_family");
    private static final TagKey<EntityType<?>> ENDERMAN_FAMILY = entityFamilyTag("enderman_family");
    private static final TagKey<EntityType<?>> WITCH_FAMILY = entityFamilyTag("witch_family");
    private static final TagKey<EntityType<?>> VILLAGER_FAMILY = entityFamilyTag("villager_family");
    private static final TagKey<EntityType<?>> IRON_GOLEM_FAMILY = entityFamilyTag("iron_golem_family");
    private static final TagKey<EntityType<?>> PASSIVE_FAMILY = entityFamilyTag("passive_family");
    private static final TagKey<EntityType<?>> UNDEAD_FAMILY = entityFamilyTag("undead_family");
    private static final TagKey<Block> STONE_RITUAL_BLOCKS = blockFamilyTag("stone_ritual_blocks");
    private static final TagKey<Block> EARTH_RITUAL_BLOCKS = blockFamilyTag("earth_ritual_blocks");
    private static final TagKey<Block> SAND_RITUAL_BLOCKS = blockFamilyTag("sand_ritual_blocks");
    private static final TagKey<Block> OBSIDIAN_RITUAL_BLOCKS = blockFamilyTag("obsidian_ritual_blocks");

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (player.getServer() == null) return;

        LockData data = LockData.get(player.getServer());
        UUID id = player.getUUID();

        // Unique world laws. These are intentionally outside ward/minigame state.
        if (data.hasUnique(id, "red_march")) tickRedMarch(player, data, id);

        // Master laws are checked every tick where needed, but persistent potion
        // maintenance still only runs once per second.
        int pact = data.masterPact(id);
        if (pact == MasterSignature.CROOKED.ordinal()) tickCrookedStep(player, data, id);
        else if (pact == MasterSignature.VEILED.ordinal()) tickVeiledPassage(player, data, id);

        if (player.tickCount % 20 != 0) return;

        long day = player.level().getDayTime() / 24000L;
        CurseEvolution.tick(player, data, id, day);
        SavantMemory.flushDeferred(player);
        tickSavantCardWarnings(player, data, id, day);
        if (data.hasUnique(id, "moonlit_hunt") && player.level().isNight()) {
            refresh(player, MobEffects.MOVEMENT_SPEED, true);
            if (player.level() instanceof ServerLevel level) {
                for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(12.0D),
                        m -> m instanceof Enemy && m.isAlive())) {
                    mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 60, 0, true, false, true));
                    mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, true, false, true));
                }
            }
        }

        if (data.hasUnique(id, "hollow_lantern")) {
            refresh(player, MobEffects.NIGHT_VISION, true);
            if (player.level() instanceof ServerLevel sl && CardConditions.isDirectDaylight(sl, player.blockPosition()))
                refresh(player, MobEffects.WEAKNESS, true);
        }
        if (data.hasUnique(id, "bone_ward")) {
            if (player.getHealth() <= player.getMaxHealth() * 0.5f) refresh(player, MobEffects.DAMAGE_RESISTANCE, true);
            else refresh(player, MobEffects.DIG_SLOWDOWN, true);
        }
        if (data.hasUnique(id, "drowned_name")) {
            refresh(player, MobEffects.WATER_BREATHING, true);
            if (player.isInWaterOrBubble()) refresh(player, MobEffects.MOVEMENT_SPEED, true);
            else if (player.level() instanceof ServerLevel sl && CardConditions.isDirectDaylight(sl, player.blockPosition()))
                refresh(player, MobEffects.WEAKNESS, true);
        }
        if (data.hasUnique(id, "house_of_ash") && player.isOnFire()) refresh(player, MobEffects.DAMAGE_BOOST, true);

        long ashenUntil = data.uniqueLong(id, "ashen_tongue_until");
        if (data.hasAshenTongue(id) && ashenUntil != Long.MIN_VALUE && player.level().getGameTime() >= ashenUntil) {
            data.setAshenTongue(id, false);
            data.setUniqueLong(id, "ashen_tongue_until", Long.MIN_VALUE);
            WardHud.message(player, net.minecraft.network.chat.Component.literal("Ashen Tongue finally loosens from your mouth.")
                    .withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC), false);
        }
        int bloodStage = data.hasBloodTithe(id) ? data.curseStage(id, "blood_tithe", day) : 0;
        boolean bloodTitheActive = data.hasBloodTithe(id) && player.getHealth() <= player.getMaxHealth() * 0.5f;
        boolean redLantern = bloodTitheActive && data.hasUnique(id, "lantern_blood") && player.getHealth() <= 10.0F;
        if (bloodTitheActive && !redLantern) {
            int bloodAmp = CurseEvolution.bloodStrengthAmplifier(data, id, day);
            if (bloodAmp > 0) player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, bloodAmp, true, false, true));
            else refresh(player, MobEffects.DAMAGE_BOOST, true);
        }
        if (redLantern) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 1, true, false, true));
            CardConjunctions.discover(player, data, CardConjunctions.Conjunction.RED_LANTERN);
        }
        if (bloodStage > 0) {
            WardHistory.discoverAnomaly(player, "curse_escalation");
            if (player.level().isNight()) refresh(player, MobEffects.HUNGER, true);
        }

        tickWorldPlayCards(player, data, id);
        tickDeathCards(player, data, id);
        tickHiddenLedgerEvent(player, day);

        boolean strength = (data.hasCrimsonBalance(id) || data.hasOpenVein(id)) && !redLantern;
        boolean weakness = data.hasCrimsonBalance(id) || data.hasThinBlood(id) || data.hasAshenTongue(id);
        refresh(player, MobEffects.DAMAGE_BOOST, strength);
        refresh(player, MobEffects.WEAKNESS, weakness);
        refresh(player, MobEffects.MOVEMENT_SPEED, data.hasGlassNerve(id));
        refresh(player, MobEffects.DIG_SLOWDOWN, data.hasGlassNerve(id));
        refresh(player, MobEffects.DAMAGE_RESISTANCE, data.hasPaleCovenant(id));
        refresh(player, MobEffects.HUNGER, data.hasPaleCovenant(id));
        refresh(player, MobEffects.REGENERATION, data.hasStillHeart(id));
        refresh(player, MobEffects.MOVEMENT_SLOWDOWN, data.hasStillHeart(id));
        refresh(player, MobEffects.DIG_SPEED, data.hasUnique(id, "shivering_tithe"));
        refresh(player, MobEffects.HUNGER, data.hasUnique(id, "shivering_tithe"));

        data.ensureMementoDeathDebt(id);
        syncHeartDebt(player, data.heartDebt(id) + data.deathHeartDebt(id));
    }

    /**
     * Runtime timers used to live only in ServerPlayer persistent NBT. That compound survives relog
     * but is not copied automatically to the respawn clone, so dying could reset private-law
     * cooldowns or the once-per-day hidden-page guard. Migrate old keys lazily, then keep the
     * authoritative value in LockData with the rest of the card state.
     */
    private static long runtimeLong(ServerPlayer player, LockData data, UUID id, String legacyKey) {
        String key = "runtime_" + legacyKey;
        long value = data.uniqueLong(id, key);
        if (value == Long.MIN_VALUE && player.getPersistentData().contains(legacyKey)) {
            value = player.getPersistentData().getLong(legacyKey);
            data.setUniqueLong(id, key, value);
            player.getPersistentData().remove(legacyKey);
        }
        return value;
    }

    private static void setRuntimeLong(ServerPlayer player, LockData data, UUID id, String legacyKey, long value) {
        player.getPersistentData().remove(legacyKey);
        data.setUniqueLong(id, "runtime_" + legacyKey, value);
    }

    private static int runtimeInt(ServerPlayer player, LockData data, UUID id, String legacyKey) {
        String key = "runtime_" + legacyKey;
        int value = data.uniqueInt(id, key);
        if (value == 0 && player.getPersistentData().contains(legacyKey)) {
            value = player.getPersistentData().getInt(legacyKey);
            data.setUniqueInt(id, key, value);
            player.getPersistentData().remove(legacyKey);
        }
        return value;
    }

    private static void setRuntimeInt(ServerPlayer player, LockData data, UUID id, String legacyKey, int value) {
        player.getPersistentData().remove(legacyKey);
        data.setUniqueInt(id, "runtime_" + legacyKey, value);
    }

    /** Crooked master's private law: sprinting occasionally folds a safe step forward. Dealer favor changes reach and recovery. */
    private static void tickCrookedStep(ServerPlayer player, LockData data, UUID id) {
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!player.isSprinting() || !player.onGround() || player.isPassenger()) return;
        if (player.getDeltaMovement().horizontalDistanceSqr() < 0.015D) return;
        long now = level.getGameTime();
        long next = runtimeLong(player, data, id, CROOKED_NEXT);
        if (now < next) return;

        int relation = CardMaster.PALE_GAMBLER.relation(data, id);
        double distance = relation >= 14 ? 4.0D : relation >= 8 ? 3.5D : relation <= -3 ? 2.5D : 3.0D;
        long cooldown = relation >= 14 ? 20L * 16L : relation >= 8 ? 20L * 20L : relation <= -3 ? 20L * 28L : 20L * 24L;
        double yaw = Math.toRadians(player.getYRot());
        double dx = -Math.sin(yaw) * distance;
        double dz =  Math.cos(yaw) * distance;
        double nx = player.getX() + dx;
        double ny = player.getY();
        double nz = player.getZ() + dz;
        BlockPos below = BlockPos.containing(nx, ny - 0.15D, nz);
        boolean floor = level.getBlockState(below).blocksMotion();
        boolean clear = level.noCollision(player, player.getBoundingBox().move(dx, 0.0D, dz));
        if (!floor || !clear) {
            setRuntimeLong(player, data, id, CROOKED_NEXT, now + 60L);
            return;
        }

        level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY(0.55), player.getZ(),
                relation >= 14 ? 22 : 16, 0.18, 0.32, 0.18, 0.02);
        player.teleportTo(level, nx, ny, nz, player.getYRot(), player.getXRot());
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, nx, player.getY(0.55), nz,
                relation >= 8 ? 18 : 12, 0.14, 0.24, 0.14, 0.015);
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 0.42f, relation >= 14 ? 1.42f : 1.28f);
        setRuntimeLong(player, data, id, CROOKED_NEXT, now + cooldown);
    }

    /** Veiled master's private law: still crouching opens a short personal veil. Favor makes the veil quicker and longer-lived. */
    private static void tickVeiledPassage(ServerPlayer player, LockData data, UUID id) {
        if (!(player.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        if (now < runtimeLong(player, data, id, VEILED_NEXT)) return;

        int relation = CardMaster.ASHEN_CURATOR.relation(data, id);
        int stillNeeded = relation >= 14 ? 26 : relation >= 8 ? 32 : relation <= -3 ? 52 : 40;
        int duration = relation >= 14 ? 20 * 12 : relation >= 8 ? 20 * 10 : relation <= -3 ? 20 * 6 : 20 * 8;
        long cooldown = relation >= 14 ? 20L * 22L : relation >= 8 ? 20L * 26L : relation <= -3 ? 20L * 38L : 20L * 30L;

        boolean still = player.isShiftKeyDown() && player.onGround()
                && player.getDeltaMovement().horizontalDistanceSqr() < 0.0012D;
        int ticks = still ? runtimeInt(player, data, id, VEILED_STILL) + 1 : 0;
        setRuntimeInt(player, data, id, VEILED_STILL, ticks);
        if (ticks < stillNeeded) return;

        setRuntimeInt(player, data, id, VEILED_STILL, 0);
        setRuntimeLong(player, data, id, VEILED_NEXT, now + cooldown);
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, duration, 0, false, false, true));
        if (relation >= 14) player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 4, 0, true, false, true));
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY(0.5), player.getZ(),
                relation >= 8 ? 30 : 22, 0.28, 0.55, 0.28, 0.01);
        level.playSound(null, player.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK,
                SoundSource.PLAYERS, 0.18f, relation >= 14 ? 1.88f : 1.72f);
    }

    /** Unique law: sprinting is subsidized with saturation, but periodically paid in blood. */
    private static void tickRedMarch(ServerPlayer player, LockData data, UUID id) {
        if (!(player.level() instanceof ServerLevel level) || !player.isSprinting() || player.isPassenger()) return;
        if (player.tickCount % 40 == 0) player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 3, 0, true, false, false));
        long now = level.getGameTime();
        long momentum = data.uniqueLong(id, "momentum_until");
        boolean borrowedMarch = momentum != Long.MIN_VALUE && momentum > now;
        if (borrowedMarch) CardConjunctions.discover(player, data, CardConjunctions.Conjunction.BORROWED_MARCH);
        long next = runtimeLong(player, data, id, RED_MARCH_NEXT);
        if (now < next) return;
        setRuntimeLong(player, data, id, RED_MARCH_NEXT, now + 20L * (borrowedMarch ? 12L : 8L));
        player.hurt(level.damageSources().magic(), 1.0F);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, player.getX(), player.getY(0.55), player.getZ(),
                4, 0.18, 0.22, 0.18, 0.01);
    }

    private static void tickHiddenLedgerEvent(ServerPlayer player, long day) {
        if (!WardHistory.hasLedger(player) || !(player.level() instanceof ServerLevel level)) return;
        long time = Math.floorMod(level.getDayTime(), 24000L);
        if (time < 18000L || time > 18120L) return;
        LockData data = LockData.get(player.getServer());
        UUID id = player.getUUID();
        if (runtimeLong(player, data, id, HIDDEN_PAGE_DAY) == day) return;
        if (player.getRandom().nextFloat() >= 0.08f) return;
        setRuntimeLong(player, data, id, HIDDEN_PAGE_DAY, day);
        String[] pages = {
                "A page you did not write now contains your name once, then crosses it out.",
                "The margin records a door you have never opened: SEVEN STEPS BELOW.",
                "There is fresh ink here. It says only: I REMEMBER THE CHEST TOO.",
                "A dry page has become damp around one sentence: DO NOT LET THE EYE COUNT FOR YOU."
        };
        String line = pages[player.getRandom().nextInt(pages.length)];
        WardHistory.recordSpecial(player, "GLITCH", line);
        WardHistory.discoverAnomaly(player, "hidden_page");
        WardHud.message(player, net.minecraft.network.chat.Component.literal("Something writes in the Witness Ledger."), false);
        level.playSound(null, player.blockPosition(), WardSounds.MARK_BAD.get(), SoundSource.PLAYERS, 0.22f, 0.58f);
        dev.marrowseal.wardbound.fx.WardLodestoneDispatch.emit(player, dev.marrowseal.wardbound.net.WardLodestoneFxPacket.LEDGER_WRITE, 0.85f);
    }

    /** Exacting master's private law: a patient full-strength melee hit gains a measured half-stroke. */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getTarget() instanceof LivingEntity target) || !target.isAlive()) return;
        if (player.getServer() == null || !(player.level() instanceof ServerLevel level)) return;
        LockData data = LockData.get(player.getServer());
        UUID id = player.getUUID();

        int ember = data.uniqueInt(id, "ember_hits");
        boolean ashenBrandStrike = ember > 0 && data.hasUnique(id, "cinder_vow");
        if (ember > 0) {
            int emberVariant = CardEvolution.activeVariant(data, id, ForbiddenBargain.EMBER_COUNT);
            if (CardBranches.alternate(data, id, ForbiddenBargain.EMBER_COUNT)) target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 160, 0, false, false, true));
            target.setSecondsOnFire(ashenBrandStrike ? 8 : CardBranches.alternate(data, id, ForbiddenBargain.EMBER_COUNT) ? 2 : emberVariant >= 3 ? 7 : emberVariant >= 2 ? 6 : emberVariant == 1 ? 5 : 4);
            if (ashenBrandStrike) {
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 4, 0, false, false, true));
                CardConjunctions.discover(player, data, CardConjunctions.Conjunction.ASHEN_BRAND);
            }
            data.setUniqueInt(id, "ember_hits", ember - 1);
            dev.marrowseal.wardbound.fx.WardLodestoneDispatch.emit(level, new net.minecraft.world.phys.Vec3(target.getX(), target.getY(0.55), target.getZ()), dev.marrowseal.wardbound.net.WardLodestoneFxPacket.ASHEN_BRAND, ashenBrandStrike ? 0.9f : 0.6f);
        }
        int coal = data.uniqueInt(id, "coal_kiss_hits");
        if (coal > 0) {
            int coalVariant = CardEvolution.activeVariant(data, id, ForbiddenBargain.COAL_KISS);
            target.addEffect(new MobEffectInstance((CardBranches.alternate(data, id, ForbiddenBargain.COAL_KISS) ? MobEffects.MOVEMENT_SLOWDOWN : MobEffects.WEAKNESS), 20 * (coalVariant >= 3 ? 7 : coalVariant >= 2 ? 6 : coalVariant == 1 ? 5 : 4), 0, false, true, true));
            data.setUniqueInt(id, "coal_kiss_hits", coal - 1);
        }
        int iron = data.uniqueInt(id, "iron_echo_hits");
        if (iron > 0 && player.getAttackStrengthScale(0.5f) >= 0.95f) {
            double yaw = Math.toRadians(player.getYRot());
            int ironVariant = CardEvolution.activeVariant(data, id, ForbiddenBargain.IRON_ECHO);
            if (CardBranches.alternate(data, id, ForbiddenBargain.IRON_ECHO)) target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 0, false, true, true));
            target.knockback(CardBranches.alternate(data, id, ForbiddenBargain.IRON_ECHO) ? 0.35D : ironVariant >= 3 ? 1.95D : ironVariant >= 2 ? 1.75D : ironVariant == 1 ? 1.55D : 1.35D, Math.sin(yaw), -Math.cos(yaw));
            data.setUniqueInt(id, "iron_echo_hits", iron - 1);
            level.playSound(null, target.blockPosition(), SoundEvents.ANVIL_HIT, SoundSource.PLAYERS, 0.22f, 1.35f);
        }
        if (data.hasUnique(id, "cinder_vow") && !ashenBrandStrike) {
            target.setSecondsOnFire(Math.max(target.getRemainingFireTicks() / 20, 0) + 3);
            level.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY(0.55), target.getZ(), 4, 0.14, 0.18, 0.14, 0.008);
        }

        if (data.masterPact(id) != MasterSignature.EXACTING.ordinal()) return;
        if (player.getAttackStrengthScale(0.5f) < 0.95f) return;
        long now = level.getGameTime();
        if (now < runtimeLong(player, data, id, EXACTING_NEXT)) return;

        int exactingRelation = CardMaster.MOURNING_NOTARY.relation(data, id);
        double fraction = exactingRelation >= 14 ? 0.70D : exactingRelation >= 8 ? 0.60D : exactingRelation <= -3 ? 0.40D : 0.50D;
        float extra = (float) Math.max(1.0D, player.getAttributeValue(Attributes.ATTACK_DAMAGE) * fraction);
        target.hurt(level.damageSources().playerAttack(player), extra);
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 4, 0, false, false, true));
        level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.55), target.getZ(),
                14, target.getBbWidth() * 0.24, target.getBbHeight() * 0.22, target.getBbWidth() * 0.24, 0.06);
        level.playSound(null, target.blockPosition(), SoundEvents.ANVIL_LAND,
                SoundSource.PLAYERS, 0.22f, 1.72f);
        long exactingCooldown = exactingRelation >= 14 ? 20L * 6L : exactingRelation >= 8 ? 20L * 7L
                : exactingRelation <= -3 ? 20L * 10L : 20L * 8L;
        setRuntimeLong(player, data, id, EXACTING_NEXT, now + exactingCooldown);
    }

    /** Grave Interest: every fifth hostile kill at night pays the player back. */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (player.getServer() == null || !(player.level() instanceof ServerLevel level)) return;
        LockData data = LockData.get(player.getServer());
        UUID id = player.getUUID();

        LivingEntity victim = event.getEntity();
        trackKillObjectives(player, victim, data, id);
        if (victim.getPersistentData().getBoolean("WardboundGuardian"))
            PlayerImprint.add(player, data, PlayerImprint.Trace.VIOLENT, 1, "killed a creature sent by a failed ward");
        boolean undeadVictim = isUndeadFamily(victim);
        if (undeadVictim) {
            progressObjective(player, data, id, "ossuary_vow");
            int salt = data.uniqueInt(id, "salt_circle");
            if (salt > 0) {
                data.setUniqueInt(id, "salt_circle", salt - 1);
                int saltVariant = CardEvolution.activeVariant(data, id, ForbiddenBargain.SALT_CIRCLE);
                int saltInitial = saltVariant >= 3 ? 14 : saltVariant >= 2 ? 12 : saltVariant == 1 ? 10 : 8;
                int saltPaid = Math.max(1, saltInitial - salt + 1);
                if (CardBranches.alternate(data, id, ForbiddenBargain.SALT_CIRCLE)) player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 240, 0, false, true, true));
                else player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 20, saltVariant >= 3 && saltPaid % 3 == 0 ? 1 : saltVariant >= 2 && saltPaid % 4 == 0 ? 1 : 0, false, true, true));
            }
        }
        if (!(victim instanceof Enemy)) return;
        progressObjective(player, data, id, "grim_harvest");
        long compassUntil = data.uniqueLong(id, "black_compass_until");
        if (data.hasUnique(id, "moonlit_hunt") && level.isNight()
                && compassUntil != Long.MIN_VALUE && compassUntil > level.getGameTime()) {
            ExperienceOrb.award(level, player.position(), 1);
            CardConjunctions.discover(player, data, CardConjunctions.Conjunction.HUNTER_LANTERN);
        }

        // Field-card drops never interrupt combat. A hidden pity counter prevents
        // pathological saves from going thousands of eligible kills without ever seeing one.
        if (data.totalBeaten(id) >= WardConfig.fieldCardAfterBeaten) {
            int pity = data.uniqueInt(id, "field_card_pity");
            boolean guaranteed = pity >= CardBalance.FIELD_PITY_GUARANTEE;
            float chance = CardBalance.fieldDropChance(pity);
            if (guaranteed || player.getRandom().nextFloat() < chance) {
                data.setUniqueInt(id, "field_card_pity", 0);
                CardMaster dealer = CardMaster.choose(data, id, player.getRandom());
                ItemStack sealed = new ItemStack(WardItems.SEALED_CARD.get());
                sealed.getOrCreateTag().putString(SealedCardItem.TAG_DEALER, dealer.id);
                if (!player.getInventory().add(sealed)) player.drop(sealed, false);
                String line = dealer.fieldDropLine(dealer.known(data, id), player.getRandom());
                WardHud.message(player, net.minecraft.network.chat.Component.literal(line)
                        .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE, net.minecraft.ChatFormatting.ITALIC), false);
                level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.72f, 0.72f);
                level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.34f, 0.62f);
                level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY(0.8), player.getZ(), 10, 0.24, 0.38, 0.24, 0.015);
            } else {
                data.setUniqueInt(id, "field_card_pity", Math.min(CardBalance.FIELD_PITY_GUARANTEE, pity + 1));
            }
        }

        int vein = data.uniqueInt(id, "vein_drinker");
        if (vein > 0) {
            data.setUniqueInt(id, "vein_drinker", vein - 1);
            boolean redCommunion = data.hasBloodTithe(id);
            if (redCommunion) CardConjunctions.discover(player, data, CardConjunctions.Conjunction.RED_COMMUNION);
            int veinVariant = CardEvolution.activeVariant(data, id, ForbiddenBargain.VEIN_DRINKER);
            float veinHeal = redCommunion ? 3.0F : 2.0F;
            int veinInitial = veinVariant >= 3 ? 14 : veinVariant >= 2 ? 12 : veinVariant == 1 ? 10 : 8;
            int veinPaid = Math.max(1, veinInitial - vein + 1);
            if (veinVariant >= 3 && veinPaid % 3 == 0) veinHeal += 1.0F;
            else if (veinVariant >= 2 && veinPaid % 4 == 0) veinHeal += 1.0F;
            if (CardBranches.alternate(data, id, ForbiddenBargain.VEIN_DRINKER)) player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, redCommunion ? 1 : 0, false, true, true));
            else player.heal(veinHeal);
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, player.getX(), player.getY(0.75), player.getZ(), 6, 0.22, 0.28, 0.22, 0.02);
            level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_CLUSTER_HIT, SoundSource.PLAYERS, 0.20f, 0.55f);
        }

        int hunter = data.uniqueInt(id, "hunter_dividend");
        if (hunter > 0) {
            boolean publicBounty = CardBranches.alternate(data, id, ForbiddenBargain.HUNTERS_DIVIDEND);
            ExperienceOrb.award(level, player.position(), publicBounty ? 4 : 2);
            if (!publicBounty) player.getFoodData().eat(1, 0.15F);
            int hunterVariant = CardEvolution.activeVariant(data, id, ForbiddenBargain.HUNTERS_DIVIDEND);
            int hunterInitial = hunterVariant >= 3 ? 16 : hunterVariant >= 2 ? 14 : hunterVariant == 1 ? 12 : 10;
            int paid = Math.max(1, hunterInitial - hunter + 1);
            data.setUniqueInt(id, "hunter_dividend", hunter - 1);
            if (publicBounty || paid % (hunterVariant >= 3 ? 4 : 3) == 0) player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 5, 0, false, false, true));
        }

        int coldLedger = data.uniqueInt(id, "cold_ledger");
        if (coldLedger > 0) {
            int paid = 9 - coldLedger;
            data.setUniqueInt(id, "cold_ledger", coldLedger - 1);
            ExperienceOrb.award(level, player.position(), 1);
            if (paid % 4 == 0) player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 6, 0, false, true, true));
        }

        int silentDividend = data.uniqueInt(id, "silent_dividend");
        if (silentDividend > 0) {
            int paid = 13 - silentDividend;
            data.setUniqueInt(id, "silent_dividend", silentDividend - 1);
            if (paid % 3 == 0) {
                ExperienceOrb.award(level, player.position(), 2);
                player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 20 * 6, 0, false, true, true));
                level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY(0.65), player.getZ(), 7, 0.2, 0.3, 0.2, 0.01);
            }
        }

        int ration = data.uniqueInt(id, "grave_ration");
        if (ration > 0) {
            if (CardBranches.alternate(data, id, ForbiddenBargain.GRAVE_RATION)) player.heal(1.0F);
            else if (player.getFoodData().getFoodLevel() < 20) player.getFoodData().eat(1, 0.1F);
            else ExperienceOrb.award(level, player.position(), CardEvolution.activeVariant(data, id, ForbiddenBargain.GRAVE_RATION) >= 3 ? 3 : CardEvolution.activeVariant(data, id, ForbiddenBargain.GRAVE_RATION) >= 2 ? 2 : 1);
            data.setUniqueInt(id, "grave_ration", ration - 1);
        }

        if (data.hasUnique(id, "memento_mori")) advanceMori(player, data, id, 1);

        if (data.hasUnique(id, "grave_bell")) {
            int n = data.uniqueInt(id, "grave_bell_kills") + 1;
            int bellTarget = DeathResonance.graveBellTarget(data, id);
            if (n >= bellTarget) {
                data.setUniqueInt(id, "grave_bell_kills", 0);
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 30, 1, false, true, true));
                ExperienceOrb.award(level, player.position(), 5);
                level.playSound(null, player.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.45f, 0.55f);
                DeathResonance.add(player, data, 4, "the Grave Bell completed a toll");
                if (data.hasUnique(id, "memento_mori")) {
                    CardConjunctions.discover(player, data, CardConjunctions.Conjunction.FUNERAL_CHORUS);
                    advanceMori(player, data, id, 4);
                }
            } else data.setUniqueInt(id, "grave_bell_kills", n);
        }

        if (data.hasUnique(id, "last_witness")) {
            int kills = data.uniqueInt(id, "witness_kills") + 1;
            if (kills >= 7) {
                data.setUniqueInt(id, "witness_kills", 0);
                data.setUniqueInt(id, "witness_charge", 1);
                WardHud.message(player, net.minecraft.network.chat.Component.literal(
                        "The Last Witness has heard seven deaths. One echo is ready."), false);
            } else data.setUniqueInt(id, "witness_kills", kills);
        }

        if (data.hasAshenTongue(id)) {
            int stage = data.ashenTongueStage(id, level.getGameTime());
            float chance = (stage >= 2 ? 0.25f : stage == 1 ? 0.20f : 0.15f)
                    + CurseEvolution.ashenXpLossChanceBonus(data, id);
            if (player.getRandom().nextFloat() < chance) ExperienceOrb.award(level, player.position(), 1);
        }

        if (!level.isNight() || !data.hasBlackHarvest(id)) return;
        if (data.bumpBlackHarvestKill(id) < 5) return;

        player.heal(4.0F);
        player.getFoodData().eat(2, 0.4F);
        ExperienceOrb.award(level, player.position(), 5);
        level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY(0.55), player.getZ(),
                10, 0.25, 0.35, 0.25, 0.02);
        level.playSound(null, player.blockPosition(), SoundEvents.SCULK_CATALYST_BLOOM,
                SoundSource.PLAYERS, 0.45f, 0.72f);
        WardHud.message(player, net.minecraft.network.chat.Component.literal(
                "Grave Interest collects five names and pays one back."), false);
    }

    /** A Second Entry: once per Minecraft day, final lethal damage is crossed out. */
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.getServer() == null || !(player.level() instanceof ServerLevel level)) return;
        LockData data = LockData.get(player.getServer());
        UUID id = player.getUUID();

        if (event.getAmount() >= player.getHealth() && data.uniqueInt(id, "witchfire_reprise") > 0) {
            data.setUniqueInt(id, "witchfire_reprise", 0);
            event.setAmount(0.0F);
            player.setHealth(Math.min(2.0F, player.getMaxHealth()));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 4, 2, false, true, true));
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY(0.5), player.getZ(), 18, 0.28, 0.48, 0.28, 0.02);
            level.playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.7f, 0.56f);
            WardHud.message(player, net.minecraft.network.chat.Component.literal("Witchfire Tithe refuses one death and burns the receipt."), false);
            return;
        }

        if (event.getAmount() >= player.getHealth() && data.hasUnique(id, "memento_mori")
                && data.uniqueInt(id, "mori_charge") > 0) {
            data.setUniqueInt(id, "mori_charge", Math.max(0, data.uniqueInt(id, "mori_charge") - 1));
            event.setAmount(0.0F);
            player.setHealth(Math.min(2.0F, player.getMaxHealth()));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 4, 2, false, true, true));
            level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY(0.5), player.getZ(), 20, 0.28, 0.5, 0.28, 0.02);
            level.playSound(null, player.blockPosition(), SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.PLAYERS, 0.65f, 0.62f);
            WardHud.message(player, net.minecraft.network.chat.Component.literal("Memento Mori returns a death it had already counted."), false);
            DeathResonance.add(player, data, 10, "Memento Mori returned a counted death");
            return;
        }

        float afterHit = player.getHealth() - event.getAmount();
        if (data.hasUnique(id, "last_witness") && data.uniqueInt(id, "witness_charge") > 0
                && afterHit > 0.0F && afterHit < 8.0F) {
            data.setUniqueInt(id, "witness_charge", 0);
            float current = player.getHealth();
            if (current > 8.0F) {
                event.setAmount(Math.max(0.0F, current - 8.0F));
            } else {
                event.setAmount(0.0F);
                player.heal(8.0F - current);
            }
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY(0.5), player.getZ(),
                    16, 0.25, 0.45, 0.25, 0.02);
            level.playSound(null, player.blockPosition(), SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.PLAYERS, 0.55f, 0.78f);
            WardHud.message(player, net.minecraft.network.chat.Component.literal(
                    "The Last Witness forces the count back to four hearts."), false);
            return;
        }

        if (event.getAmount() < player.getHealth()) return;
        if (!data.hasSecondEntry(id)) return;
        long day = level.getDayTime() / 24000L;
        if (data.secondEntryDay(id) == day) return;

        data.setSecondEntryDay(id, day);
        event.setAmount(0.0F);
        player.setHealth(Math.min(2.0F, player.getMaxHealth()));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 5, 3, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 15, 0, false, true, true));
        level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY(0.5), player.getZ(),
                34, 0.42, 0.72, 0.42, 0.03);
        level.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE,
                SoundSource.PLAYERS, 0.68f, 0.62f);
        WardHud.message(player, net.minecraft.network.chat.Component.literal(
                "A Second Entry crosses out the line where you died."), false);
    }

    /** Brittle Pilgrimage is a world-play curse, not a minigame modifier. */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.getServer() == null) return;
        LockData data = LockData.get(player.getServer());
        UUID id = player.getUUID();
        long now = player.level().getGameTime();

        long boneward = data.uniqueLong(id, "boneward_until");
        if (boneward != Long.MIN_VALUE) {
            if (now >= boneward) data.setUniqueLong(id, "boneward_until", Long.MIN_VALUE);
            else if (event.getSource().getDirectEntity() instanceof Projectile) event.setAmount(event.getAmount() * 0.60F);
        }
        long blastward = data.uniqueLong(id, "blastward_until");
        if (blastward != Long.MIN_VALUE) {
            if (now >= blastward) data.setUniqueLong(id, "blastward_until", Long.MIN_VALUE);
            else if (event.getSource().is(DamageTypeTags.IS_EXPLOSION)) event.setAmount(event.getAmount() * 0.40F);
        }
        long witchward = data.uniqueLong(id, "witchward_until");
        if (witchward != Long.MIN_VALUE) {
            if (now >= witchward) data.setUniqueLong(id, "witchward_until", Long.MIN_VALUE);
            else if (event.getSource().is(DamageTypes.MAGIC) || event.getSource().is(DamageTypes.INDIRECT_MAGIC))
                event.setAmount(event.getAmount() * 0.50F);
        }
        int enderSteps = data.uniqueInt(id, "ender_step_charges");
        if (enderSteps > 0 && event.getAmount() >= 4.0F && !event.getSource().is(DamageTypes.FALL)) {
            event.setAmount(event.getAmount() * 0.50F);
            data.setUniqueInt(id, "ender_step_charges", enderSteps - 1);
            if (player.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY(0.5), player.getZ(), 14, 0.26, 0.44, 0.26, 0.02);
                level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.32f, 1.28f);
            }
        }

        int ferryman = data.uniqueInt(id, "ferryman_guard");
        if (ferryman > 0 && event.getAmount() >= (CardBranches.alternate(data, id, ForbiddenBargain.FERRYMAN_LEDGER) ? 8.0F : 4.0F) && !event.getSource().is(DamageTypes.FALL)) {
            event.setAmount(event.getAmount() * (CardBranches.alternate(data, id, ForbiddenBargain.FERRYMAN_LEDGER) ? 0.50F : 0.65F));
            data.setUniqueInt(id, "ferryman_guard", ferryman - 1);
            if (ferryman == 1 && CardEvolution.activeVariant(data, id, ForbiddenBargain.FERRYMAN_LEDGER) >= 2) {
                int fv = CardEvolution.activeVariant(data, id, ForbiddenBargain.FERRYMAN_LEDGER);
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * (fv >= 3 ? 12 : 8), fv >= 3 ? 1 : 0, false, true, true));
            }
            if (data.hasUnique(id, "thorn_ledger") && event.getSource().getEntity() instanceof LivingEntity attacker
                    && attacker != player && !(event.getSource().getDirectEntity() instanceof Projectile)) {
                attacker.hurt(player.damageSources().magic(), 2.0F);
                CardConjunctions.discover(player, data, CardConjunctions.Conjunction.THORN_FERRY);
            }
            if (player.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY(0.6), player.getZ(), 8, 0.22, 0.32, 0.22, 0.02);
                level.playSound(null, player.blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 0.22f, 0.78f);
            }
        }
        if (data.hasUnique(id, "cinder_vow") && (event.getSource().is(DamageTypes.IN_FIRE)
                || event.getSource().is(DamageTypes.ON_FIRE) || event.getSource().is(DamageTypes.LAVA))) {
            event.setAmount(event.getAmount() * 0.35F);
        }

        if (!data.hasUnique(id, "cinder_vow") && data.hasUnique(id, "house_of_ash")
                && (event.getSource().is(DamageTypes.IN_FIRE) || event.getSource().is(DamageTypes.ON_FIRE)
                || event.getSource().is(DamageTypes.LAVA))) {
            event.setAmount(event.getAmount() * 0.45F);
        }
        if (data.hasUnique(id, "black_reprieve") && event.getAmount() < player.getHealth()
                && player.getHealth() - event.getAmount() < 6.0F) {
            long day = player.level().getDayTime() / 24000L;
            if (data.uniqueLong(id, "black_reprieve_day") != day) {
                data.setUniqueLong(id, "black_reprieve_day", day);
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 8, 0, false, true, true));
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 30, 1, false, true, true));
                if (player.level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY(0.6), player.getZ(), 14, 0.24, 0.4, 0.24, 0.02);
                    sl.playSound(null, player.blockPosition(), SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.PLAYERS, 0.45f, 0.60f);
                }
                WardHud.message(player, Component.literal("The Black Reprieve writes protection after the wound, not before it.")
                        .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC), false);
            }
        }

        if (event.getSource().is(DamageTypes.FALL)) {
            if (data.hasUnique(id, "coffin_road")) {
                event.setAmount(0.0F);
                return;
            }
            if (data.hasBrittlePilgrimage(id)) {
                long day = player.level().getDayTime() / 24000L;
                int stage = data.curseStage(id, "brittle", day);
                if (stage > 0) WardHistory.discoverAnomaly(player, "curse_escalation");
                float mult = CurseEvolution.brittleFallMultiplier(data, id, day);
                event.setAmount(event.getAmount() * mult);
                if (event.getAmount() >= 4.0F) {
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 5, stage >= 2 ? 1 : 0, false, true, true));
                    if (CurseEvolution.active(data, id, CurseEvolution.Corruption.ASHEN_PILGRIMAGE))
                        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,
                                CurseEvolution.ashenPilgrimageWeaknessTicks(data, id), 0, false, true, true));
                }
            }
            if (data.hasUnique(id, "dustbound_soles")) event.setAmount(event.getAmount() * 0.70F);
            return;
        }

        if (data.hasUnique(id, "thorn_ledger")) {
            if (event.getSource().getDirectEntity() instanceof Projectile) event.setAmount(event.getAmount() * 1.20F);
            else if (event.getSource().getEntity() instanceof LivingEntity attacker && attacker != player) {
                attacker.hurt(player.damageSources().magic(), 1.0F);
            }
        }
    }

    /** Unique law: the first completed food of each day grants one random minute-long boon. */
    @SubscribeEvent
    public static void onItemFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !event.getItem().isEdible()) return;
        if (player.getServer() == null) return;
        LockData data = LockData.get(player.getServer());
        UUID id = player.getUUID();

        int meals = data.uniqueInt(id, "hearthmark_meals");
        if (meals > 0) {
            int hearthVariant = CardEvolution.activeVariant(data, id, ForbiddenBargain.HEARTHMARK);
            if (CardBranches.alternate(data, id, ForbiddenBargain.HEARTHMARK)) player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 400, 0, false, true, true));
            else player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * (hearthVariant >= 3 ? 10 : hearthVariant >= 2 ? 9 : hearthVariant == 1 ? 7 : 6), hearthVariant >= 2 ? 1 : 0, false, true, true));
            data.setUniqueInt(id, "hearthmark_meals", meals - 1);
            if (meals == 1) player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 20 * (hearthVariant >= 3 ? 15 : hearthVariant >= 2 ? 20 : 30), 0, false, true, true));
        }

        int paleRation = data.uniqueInt(id, "pale_ration");
        if (paleRation > 0) {
            data.setUniqueInt(id, "pale_ration", paleRation - 1);
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 20, 0, false, true, true));
            if (paleRation == 1) player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 20 * 20, 0, false, true, true));
        }

        if (!data.hasUnique(id, "first_supper")) return;
        long day = player.level().getDayTime() / 24000L;
        if (data.uniqueLong(id, "first_supper_day") == day) return;
        data.setUniqueLong(id, "first_supper_day", day);
        MobEffect[] boons = {MobEffects.MOVEMENT_SPEED, MobEffects.DAMAGE_RESISTANCE, MobEffects.DIG_SPEED, MobEffects.DAMAGE_BOOST};
        MobEffect boon = boons[player.getRandom().nextInt(boons.length)];
        player.addEffect(new MobEffectInstance(boon, 20 * 60, 0, false, true, true));
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY(0.5), player.getZ(), 18, 0.28, 0.45, 0.28, 0.02);
            level.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.45f, 0.82f);
        }
        WardHud.message(player, net.minecraft.network.chat.Component.literal(
                "The First Supper chooses what you needed after you swallowed it."), false);
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || player.getServer() == null) return;
        LockData data = LockData.get(player.getServer());
        UUID id = player.getUUID();
        var state = event.getState();
        ServerLevel level = (ServerLevel) player.level();
        BlockPos pos = event.getPos();

        boolean syntheticChain = FourthWaveCardEffects.isSyntheticChainBreak(id);
        if (!syntheticChain) {
            boolean stoneTarget = state.is(STONE_RITUAL_BLOCKS) || state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE) || state.is(Blocks.TUFF)
                    || state.is(Blocks.ANDESITE) || state.is(Blocks.DIORITE) || state.is(Blocks.GRANITE);
            ritualProgressOrMisstep(player, data, id, "stone_communion", stoneTarget, CardConditions.isDark(level, pos));

            boolean oreTarget = state.is(Tags.Blocks.ORES);
            ritualProgressOrMisstep(player, data, id, "vein_litany", oreTarget, pos.getY() <= 32);

            boolean logTarget = state.is(BlockTags.LOGS);
            ritualProgressOrMisstep(player, data, id, "wooden_confession", logTarget, level.canSeeSky(pos.above()));

            boolean earthTarget = state.is(EARTH_RITUAL_BLOCKS) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.ROOTED_DIRT)
                    || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)
                    || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY) || state.is(Blocks.MUD);
            ritualProgressOrMisstep(player, data, id, "earthen_vigil", earthTarget, CardConditions.isOpenNight(level, pos.above()));

            boolean obsidianTarget = state.is(OBSIDIAN_RITUAL_BLOCKS) || state.is(Blocks.OBSIDIAN);
            ritualProgressOrMisstep(player, data, id, "obsidian_prayer", obsidianTarget, level.getMaxLocalRawBrightness(pos) >= 10);

            boolean sandTarget = state.is(SAND_RITUAL_BLOCKS) || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND) || state.is(Blocks.SANDSTONE)
                    || state.is(Blocks.RED_SANDSTONE) || state.is(Blocks.GLASS) || state.is(Blocks.GLASS_PANE);
            ritualProgressOrMisstep(player, data, id, "sand_liturgy", sandTarget, CardConditions.isDirectDaylight(level, pos.above()));
        }

        int left = data.uniqueInt(id, "pilgrims_luck_blocks");
        if (left > 0) {
            data.setUniqueInt(id, "pilgrims_luck_blocks", left - 1);
            long whisperUntil = data.uniqueLong(id, "ore_whisper_until");
            boolean whisperedFortune = state.is(Tags.Blocks.ORES) && whisperUntil != Long.MIN_VALUE && whisperUntil > level.getGameTime();
            if (whisperedFortune) CardConjunctions.discover(player, data, CardConjunctions.Conjunction.WHISPERED_FORTUNE);
            int pilgrimVariant = CardEvolution.activeVariant(data, id, ForbiddenBargain.PILGRIMS_LUCK);
            float baseLuck = pilgrimVariant >= 3 ? 0.26f : pilgrimVariant >= 2 ? 0.22f : pilgrimVariant == 1 ? 0.18f : 0.15f;
            float luckChance = whisperedFortune ? Math.min(0.45f, baseLuck + 0.20f) : baseLuck;
            int steps = data.uniqueInt(id, "card_branch_paid_" + ForbiddenBargain.PILGRIMS_LUCK.id) + 1;
            data.setUniqueInt(id, "card_branch_paid_" + ForbiddenBargain.PILGRIMS_LUCK.id, steps);
            int interval = (pilgrimVariant >= 3 ? 3 : pilgrimVariant >= 2 ? 4 : 5) - (whisperedFortune ? 1 : 0);
            if (CardBranches.alternate(data, id, ForbiddenBargain.PILGRIMS_LUCK) ? steps % interval == 0 : player.getRandom().nextFloat() < luckChance) {
                ExperienceOrb.award(level, player.position(), 1);
                level.sendParticles(ParticleTypes.ENCHANT, event.getPos().getX() + 0.5, event.getPos().getY() + 0.5, event.getPos().getZ() + 0.5,
                        whisperedFortune ? 8 : 4, 0.16, 0.16, 0.16, 0.01);
            }
        }
    }

    private static void trackKillObjectives(ServerPlayer player, LivingEntity victim, LockData data, UUID id) {
        if (victim instanceof Zombie || matchesHostileFamily(victim, ZOMBIE_FAMILY, "zombie", "drowned", "husk"))
            progressObjective(player, data, id, "rotten_ledger");
        if (victim instanceof AbstractSkeleton || matchesHostileFamily(victim, SKELETON_FAMILY, "skeleton", "stray"))
            progressObjective(player, data, id, "bone_tally");
        if (victim instanceof Creeper || matchesHostileFamily(victim, CREEPER_FAMILY, "creeper"))
            progressObjective(player, data, id, "creeper_clause");
        if (victim instanceof Spider || matchesHostileFamily(victim, SPIDER_FAMILY, "spider"))
            progressObjective(player, data, id, "silk_warrant");
        if (victim instanceof EnderMan || matchesHostileFamily(victim, ENDERMAN_FAMILY, "enderman"))
            progressObjective(player, data, id, "ender_audit");
        if (victim instanceof Witch || matchesHostileFamily(victim, WITCH_FAMILY, "witch")) {
            progressObjective(player, data, id, "witch_ledger");
            progressObjective(player, data, id, "witchfire_tithe");
        }

        boolean undead = isUndeadFamily(victim);
        boolean zombieFamily = victim instanceof Zombie || matchesHostileFamily(victim, ZOMBIE_FAMILY, "zombie", "drowned", "husk");
        if (victim instanceof Villager || (!undead && !zombieFamily && matchesFamily(victim, VILLAGER_FAMILY, "villager", "trader")))
            progressObjective(player, data, id, "red_census");
        if (victim instanceof IronGolem || matchesFamily(victim, IRON_GOLEM_FAMILY, "iron_golem", "irongolem"))
            progressObjective(player, data, id, "iron_silence");
        if (isPassiveFamily(victim))
            progressObjective(player, data, id, "innocence_tax");
    }

    private static boolean isUndeadFamily(LivingEntity entity) {
        return entity.getMobType() == MobType.UNDEAD || entity.getType().is(UNDEAD_FAMILY) || matchesPath(entity,
                "zombie", "skeleton", "drowned", "husk", "stray", "wither", "undead", "ghoul", "revenant");
    }

    /** Explicit datapack tags always win; name fallback for hostile contracts is restricted to actual hostile mobs. */
    private static boolean matchesHostileFamily(LivingEntity entity, TagKey<EntityType<?>> tag, String... pathHints) {
        if (entity.getType().is(tag)) return true;
        return entity instanceof Enemy && matchesPath(entity, pathHints);
    }

    private static boolean isPassiveFamily(LivingEntity entity) {
        if (entity.getType().is(PASSIVE_FAMILY)) return true;
        if (entity instanceof Enemy || entity.getMobType() == MobType.UNDEAD) return false;
        return entity instanceof Animal || matchesPath(entity,
                "cow", "pig", "sheep", "chicken", "rabbit", "goat", "horse", "llama", "deer");
    }

    private static TagKey<EntityType<?>> entityFamilyTag(String path) {
        return TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(Wardbound.MODID, path));
    }

    private static TagKey<Block> blockFamilyTag(String path) {
        return TagKey.create(Registries.BLOCK, new ResourceLocation(Wardbound.MODID, path));
    }

    private static boolean matchesFamily(LivingEntity entity, TagKey<EntityType<?>> tag, String... pathHints) {
        return entity.getType().is(tag) || matchesPath(entity, pathHints);
    }

    private static boolean matchesPath(LivingEntity entity, String... pathHints) {
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (key == null) return false;
        String path = key.getPath().toLowerCase(java.util.Locale.ROOT);
        for (String hint : pathHints) if (path.equals(hint) || path.startsWith(hint + "_") || path.endsWith("_" + hint)) return true;
        return false;
    }

    private static void advanceMori(ServerPlayer player, LockData data, UUID id, int amount) {
        if (!data.hasUnique(id, "memento_mori") || amount <= 0) return;
        int n = data.uniqueInt(id, "mori_kills") + amount;
        int target = DeathResonance.mementoTarget(data, id);
        int earned = n / target;
        data.setUniqueInt(id, "mori_kills", n % target);
        if (earned <= 0) return;
        int before = data.uniqueInt(id, "mori_charge");
        int after = Math.min(2, before + earned);
        data.setUniqueInt(id, "mori_charge", after);
        if (after > before) {
            WardHud.message(player, net.minecraft.network.chat.Component.literal(
                    "Memento Mori closes another count. Pale reprieves: " + after + "/2."), false);
        } else {
            WardHud.message(player, net.minecraft.network.chat.Component.literal(
                    "Memento Mori completed another count, but both pale margins were already occupied.")
                    .withStyle(net.minecraft.ChatFormatting.DARK_GRAY, net.minecraft.ChatFormatting.ITALIC), false);
        }
    }

    private static void tickSavantCardWarnings(ServerPlayer player, LockData data, UUID id, long day) {
        warnCurseMaturity(player, data, id, "blood_tithe", "Blood Tithe", day);
        warnCurseMaturity(player, data, id, "brittle", "Brittle Pilgrimage", day);
        long ashenUntil = data.uniqueLong(id, "ashen_tongue_until");
        long remaining = ashenUntil == Long.MIN_VALUE ? Long.MAX_VALUE : ashenUntil - player.level().getGameTime();
        if (data.hasAshenTongue(id) && remaining > 0 && remaining <= 20L * 60L)
            CthulhuEyeItem.speakInsight(player, "ashen_expiry",
                    "Ashen Tongue has less than a minute left. It will leave without ceremony. Most things here do not.", 20L * 80L);

        int fieldPity = data.uniqueInt(id, "field_card_pity");
        String fieldPressure = CardBalance.pressureBand(fieldPity, CardBalance.FIELD_PITY_START, CardBalance.FIELD_PITY_GUARANTEE);
        if ("imminent".equals(fieldPressure) || "certain".equals(fieldPressure)) {
            CthulhuEyeItem.speakInsight(player, "field_pressure_high",
                    "A sealed card is becoming statistically difficult to avoid. I dislike when probability starts behaving like intent.", 20L * 60L * 14L);
        } else if ("gathering".equals(fieldPressure)) {
            CthulhuEyeItem.speakInsight(player, "field_pressure_mid",
                    "The field-card distribution has drifted. Another dealer is leaning on the scale.", 20L * 60L * 16L);
        }
        long objectiveNow = player.level().getGameTime();
        for (CardObjectives.Objective objective : CardObjectives.Objective.values()) {
            if (objective.kind != ForbiddenBargain.Kind.CONTRACT || !data.hasUnique(id, "obj_" + objective.key)) continue;
            long started = data.uniqueLong(id, "obj_" + objective.key + "_started_at");
            if (started == Long.MIN_VALUE) continue;
            long elapsed = Math.max(0L, objectiveNow - started);
            if (elapsed >= 20L * 60L * 5L && elapsed < 20L * 60L * 5L + 40L) {
                CthulhuEyeItem.speakInsight(player, "contract_exacting_window_" + objective.key,
                        objective.title + " has one minute left in its exacting margin. The best streak needs "
                                + CardObjectives.exactingStreakNeeded(objective) + ".", 20L * 60L * 20L);
            }
            if (elapsed >= 20L * 60L * 8L && elapsed < 20L * 60L * 8L + 40L) {
                CthulhuEyeItem.speakInsight(player, "contract_swift_window_" + objective.key,
                        objective.title + " has two minutes left in its Swift Clause. The paper is less patient than you are.", 20L * 60L * 20L);
            }
        }

        int deathPity = data.uniqueInt(id, "death_hand_pity");
        String deathPressure = CardBalance.pressureBand(deathPity, CardBalance.DEATH_PITY_START, CardBalance.DEATH_PITY_GUARANTEE);
        if ("imminent".equals(deathPressure) || "certain".equals(deathPressure)) {
            CthulhuEyeItem.speakInsight(player, "death_pressure_high",
                    "The next few eligible card tables deserve caution. The lower deck has become impatient.", 20L * 60L * 18L);
        } else if ("gathering".equals(deathPressure)) {
            CthulhuEyeItem.speakInsight(player, "death_pressure_mid",
                    "There is a second deck under the ordinary one. It has begun pressing upward.", 20L * 60L * 20L);
        }

        // One global hint budget prevents several independently cooled hints from firing in a burst.
        maybeSpeakConjunctionHint(player, data, id);
    }

    private static void maybeSpeakConjunctionHint(ServerPlayer player, LockData data, UUID id) {
        long now = player.level().getGameTime();
        long next = data.uniqueLong(id, "savant_conjunction_hint_next");
        if (next != Long.MIN_VALUE && now < next) return;
        java.util.List<String[]> hints = new java.util.ArrayList<>();
        java.util.function.BiConsumer<String, String> add = (key, line) -> hints.add(new String[]{key, line});

        if (!CardConjunctions.known(data, id, CardConjunctions.Conjunction.VEILED_GLASS)
                && (data.hasUnique(id, "bellglass_sight") || data.uniqueLong(id, "pocket_eclipse_until") > now))
            add.accept("veiled_glass", "Bellglass reacts to ordinary darkness. A manufactured darkness may make the reaction contradict itself.");
        if (!CardConjunctions.known(data, id, CardConjunctions.Conjunction.RED_COMMUNION)
                && (data.hasBloodTithe(id) || data.uniqueInt(id, "vein_drinker") > 0))
            add.accept("red_communion", "A blood debt and a blood repayment are not separate ledgers merely because they arrived on different cards.");
        if (!CardConjunctions.known(data, id, CardConjunctions.Conjunction.FUNERAL_CHORUS)
                && (data.hasUnique(id, "memento_mori") || data.hasUnique(id, "grave_bell")))
            add.accept("funeral_chorus", "One Death law counts names. Another rings after enough of them. Their arithmetic is suspiciously compatible.");
        if (!CardConjunctions.known(data, id, CardConjunctions.Conjunction.NIGHT_CROWN)
                && (data.hasUnique(id, "black_sun") || data.hasUnique(id, "moonlit_hunt")))
            add.accept("night_crown", "Two separate laws claim the open night. Territorial disputes between laws occasionally resemble bonuses.");
        if (!CardConjunctions.known(data, id, CardConjunctions.Conjunction.ASHEN_BRAND)
                && (data.hasUnique(id, "cinder_vow") || data.uniqueInt(id, "ember_hits") > 0))
            add.accept("ashen_brand", "A permanent flame law would respond differently to a finite supply of ember-strikes.");
        if (!CardConjunctions.known(data, id, CardConjunctions.Conjunction.BORROWED_MARCH)
                && (data.hasUnique(id, "red_march") || data.uniqueLong(id, "momentum_until") > now))
            add.accept("borrowed_march", "The Red March charges for movement. Borrowed Momentum changes who appears to be paying for that movement.");
        if (!CardConjunctions.known(data, id, CardConjunctions.Conjunction.HUNTER_LANTERN)
                && (data.hasUnique(id, "moonlit_hunt") || data.uniqueLong(id, "black_compass_until") > now))
            add.accept("hunter_lantern", "A hunt becomes easier to account for when another law has already marked the prey.");
        if (!CardConjunctions.known(data, id, CardConjunctions.Conjunction.PALLBEARER_STEP)
                && (data.hasUnique(id, "coffin_road") || data.hasUnique(id, "dustbound_soles")))
            add.accept("pallbearer_step", "Two laws both burden the feet. Sometimes identical penalties stack; sometimes the rules notice the redundancy first.");
        if (!CardConjunctions.known(data, id, CardConjunctions.Conjunction.MERCURIAL_FEVER)
                && (data.hasUnique(id, "shivering_tithe") || data.uniqueLong(id, "quicksilver_until") > now))
            add.accept("mercurial_fever", "You have more than one law interested in haste. Their shared enthusiasm may be medically unhelpful.");
        if (!CardConjunctions.known(data, id, CardConjunctions.Conjunction.RED_LANTERN)
                && (data.hasUnique(id, "lantern_blood") || data.hasBloodTithe(id)))
            add.accept("red_lantern", "Lantern Blood watches low health. Blood Tithe also watches low health. Neither has agreed to look away.");
        if (!CardConjunctions.known(data, id, CardConjunctions.Conjunction.THORN_FERRY)
                && (data.hasUnique(id, "thorn_ledger") || data.uniqueInt(id, "ferryman_guard") > 0))
            add.accept("thorn_ferry", "One ledger reduces a heavy blow. Another invoices melee contact. A spared blow can still have an address for the return mail.");
        if (!CardConjunctions.known(data, id, CardConjunctions.Conjunction.WHISPERED_FORTUNE)
                && (data.uniqueLong(id, "ore_whisper_until") > now || data.uniqueInt(id, "pilgrims_luck_blocks") > 0))
            add.accept("whispered_fortune", "Ore Whisper changes what you can find. Pilgrim's Luck changes what a broken block can pay. Those are adjacent operations.");

        if (hints.isEmpty()) {
            data.setUniqueLong(id, "savant_conjunction_hint_next", now + 20L * 60L * 4L);
            return;
        }
        String[] hint = hints.get(player.getRandom().nextInt(hints.size()));
        int known = CardConjunctions.knownCount(data, id);
        long cooldown = Math.max(20L * 60L * 4L, 20L * 60L * (9L - Math.min(5, known / 2)));
        data.setUniqueLong(id, "savant_conjunction_hint_next", now + cooldown);
        CthulhuEyeItem.speakInsight(player, "conjunction_hint_" + hint[0], hint[1], cooldown);
    }

    private static void warnCurseMaturity(ServerPlayer player, LockData data, UUID id, String key, String title, long day) {
        long start = data.curseDay(id, key);
        if (start == Long.MIN_VALUE) return;
        long age = Math.max(0L, day - start);
        if (age == 1 || age == 3) {
            int next = age == 1 ? 1 : 2;
            CthulhuEyeItem.speakInsight(player, "curse_" + key + "_" + next,
                    title + " will deepen with the next dawn. You may regard this as useful information rather than a warning.", 20L * 60L * 18L);
        }
    }

    private static void ritualProgressOrMisstep(ServerPlayer player, LockData data, UUID id, String key,
                                                boolean targetBlock, boolean conditionMet) {
        if (!targetBlock || !data.hasUnique(id, "obj_" + key)) return;
        CardObjectives.Objective objective = CardObjectives.byKey(key);
        if (objective == null) return;
        if (conditionMet) progressObjective(player, data, id, key);
        else recordRitualMisstep(player, data, id, key, objective.title);
    }

    private static void recordRitualMisstep(ServerPlayer player, LockData data, UUID id, String key, String title) {
        int mistakes = data.uniqueInt(id, "obj_" + key + "_missteps") + 1;
        data.setUniqueInt(id, "obj_" + key + "_missteps", mistakes);
        // Give useful feedback without turning every wrong block into chat spam.
        if (mistakes == 1 || mistakes == 2 || mistakes == 3 || mistakes == 8) {
            WardHud.message(player, net.minecraft.network.chat.Component.literal(
                    title + " // the condition was not met (purity " + CardObjectives.ritualPurity(mistakes) + "%)")
                    .withStyle(net.minecraft.ChatFormatting.DARK_GRAY, net.minecraft.ChatFormatting.ITALIC), false);
            CthulhuEyeItem.speakInsight(player, "ritual_misstep_" + key + "_" + mistakes,
                    "That block belonged to " + title + ", but not under those conditions. Rituals are pedantic by design.", 20L * 45L);
        }
    }

    private static void progressObjective(ServerPlayer player, LockData data, UUID id, String key) {
        CardObjectives.Objective objective = CardObjectives.byKey(key);
        if (objective == null) return;
        progressObjective(player, data, id, key, objective.goal, objective.title);
    }

    private static void progressObjective(ServerPlayer player, LockData data, UUID id, String key, int goal, String title) {
        if (!data.hasUnique(id, "obj_" + key)) return;
        String countKey = "obj_" + key + "_progress";
        int before = data.uniqueInt(id, countKey);
        int now = Math.min(goal, before + 1);
        data.setUniqueInt(id, countKey, now);

        long tick = player.level().getGameTime();
        if (data.uniqueLong(id, "obj_" + key + "_started_at") == Long.MIN_VALUE)
            data.setUniqueLong(id, "obj_" + key + "_started_at", tick);
        long last = data.uniqueLong(id, "obj_" + key + "_last_progress");
        int streak = last != Long.MIN_VALUE && tick - last <= 20L * 30L
                ? data.uniqueInt(id, "obj_" + key + "_streak") + 1 : 1;
        data.setUniqueLong(id, "obj_" + key + "_last_progress", tick);
        data.setUniqueInt(id, "obj_" + key + "_streak", streak);
        data.setUniqueInt(id, "obj_" + key + "_best_streak",
                Math.max(streak, data.uniqueInt(id, "obj_" + key + "_best_streak")));

        int beforeQuarter = (before * 4) / goal;
        int nowQuarter = (now * 4) / goal;
        if (nowQuarter > beforeQuarter && now < goal) {
            String mark = nowQuarter == 1 ? "one quarter" : nowQuarter == 2 ? "half" : "three quarters";
            WardHud.message(player, net.minecraft.network.chat.Component.literal(title + " // " + now + "/" + goal + " · " + mark)
                    .withStyle(net.minecraft.ChatFormatting.DARK_AQUA), false);
            if (nowQuarter == 2) {
                CthulhuEyeItem.speakInsight(player, "objective_50_" + key,
                        title + " is half complete. The useful part of a count is that it does not care whether you are tired of it.", 20L * 60L * 8L);
            } else if (nowQuarter == 3) {
                CthulhuEyeItem.speakInsight(player, "objective_75_" + key,
                        title + " is three quarters complete. At this point, stopping would be a decision rather than an accident.", 20L * 60L * 10L);
            }
        }
        if (now >= goal) {
            data.setUnique(id, "obj_" + key, false);
            data.setUniqueInt(id, countKey, 0);
            completeObjective(player, data, id, key, title);
        }
    }

    private static void completeObjective(ServerPlayer player, LockData data, UUID id, String key, String title) {
        if (!(player.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        CardObjectives.Objective objective = CardObjectives.byKey(key);
        int missteps = data.uniqueInt(id, "obj_" + key + "_missteps");
        int bestStreak = data.uniqueInt(id, "obj_" + key + "_best_streak");
        long started = data.uniqueLong(id, "obj_" + key + "_started_at");
        long elapsed = started == Long.MIN_VALUE ? Long.MAX_VALUE : Math.max(0L, now - started);

        switch (key) {
            case "rotten_ledger" -> {
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 60 * 10, 0, false, true, true));
                ExperienceOrb.award(level, player.position(), 12);
            }
            case "bone_tally" -> data.setUniqueLong(id, "boneward_until", now + 20L * 60L * 15L);
            case "creeper_clause" -> data.setUniqueLong(id, "blastward_until", now + 20L * 60L * 15L);
            case "silk_warrant" -> {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 60 * 12, 1, false, true, true));
                player.addEffect(new MobEffectInstance(MobEffects.JUMP, 20 * 60 * 12, 0, false, true, true));
            }
            case "ender_audit" -> data.setUniqueInt(id, "ender_step_charges", 5);
            case "witch_ledger" -> {
                java.util.List<MobEffect> harmful = new java.util.ArrayList<>();
                for (MobEffectInstance effect : player.getActiveEffects()) {
                    if (effect.getEffect().getCategory() == MobEffectCategory.HARMFUL) harmful.add(effect.getEffect());
                }
                for (MobEffect effect : harmful) player.removeEffect(effect);
                data.setUniqueLong(id, "witchward_until", now + 20L * 60L * 12L);
            }
            case "stone_communion" -> player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 20 * 60 * 10, 1, false, true, true));
            case "vein_litany" -> data.setUniqueLong(id, "ore_whisper_until", now + 20L * 60L * 12L);
            case "wooden_confession" -> {
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 20 * 60 * 15, 0, false, true, true));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 60 * 15, 0, false, true, true));
            }
            case "earthen_vigil" -> {
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 60 * 10, 1, false, true, true));
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 60 * 10, 0, false, true, true));
            }
            case "obsidian_prayer" -> {
                player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 60 * 30, 0, false, true, true));
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 60 * 10, 0, false, true, true));
            }
            case "red_census" -> {
                data.setUniqueInt(id, "red_census_loot_charges", 6);
                player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 20 * 60 * 20, 4, false, true, true));
            }
            case "iron_silence" -> {
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 60 * 15, 1, false, true, true));
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 20 * 60 * 15, 0, false, true, true));
            }
            case "innocence_tax" -> {
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 60 * 10, 1, false, true, true));
                player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 20 * 60 * 10, 0, false, true, true));
            }
            case "witchfire_tithe" -> {
                data.setUniqueInt(id, "witchfire_reprise", 1);
                ExperienceOrb.award(level, player.position(), 20);
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 60 * 10, 0, false, true, true));
            }
            case "grim_harvest" -> {
                data.setUniqueInt(id, "vein_drinker", 8);
                ExperienceOrb.award(level, player.position(), 8);
            }
            case "sand_liturgy" -> {
                player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 60 * 10, 0, false, true, true));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 60 * 10, 0, false, true, true));
            }
            case "ossuary_vow" -> {
                data.setUniqueInt(id, "ossuary_loot_charges", 10);
                player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 20 * 60 * 6, 0, false, true, true));
            }
        }

        boolean finePrint = false;
        String finePrintLine = "";
        if (objective != null && objective.kind == ForbiddenBargain.Kind.CONTRACT) {
            int neededStreak = CardObjectives.swiftStreakNeeded(objective);
            int exactingNeeded = CardObjectives.exactingStreakNeeded(objective);
            boolean exactingClause = elapsed <= CardObjectives.EXACTING_WINDOW_TICKS && bestStreak >= exactingNeeded;
            if (elapsed <= CardObjectives.SWIFT_WINDOW_TICKS && bestStreak >= neededStreak) {
                finePrint = true;
                int clauseXp = exactingClause ? 8 : 4;
                ExperienceOrb.award(level, player.position(), clauseXp);
                data.setUniqueInt(id, "contract_fine_prints", data.uniqueInt(id, "contract_fine_prints") + 1);
                if (exactingClause)
                    data.setUniqueInt(id, "contract_exacting_clauses", data.uniqueInt(id, "contract_exacting_clauses") + 1);
                finePrintLine = (exactingClause ? "Exacting swift clause honored: +8 experience." : "Swift clause honored: +4 experience.");
            }
        } else if (objective != null && objective.kind == ForbiddenBargain.Kind.RITUAL) {
            int purity = CardObjectives.ritualPurity(missteps);
            if (purity >= 100) {
                finePrint = true;
                ExperienceOrb.award(level, player.position(), 6);
                player.addEffect(new MobEffectInstance(MobEffects.LUCK, 20 * 60 * 2, 0, false, true, true));
                data.setUniqueInt(id, "ritual_pure_completions", data.uniqueInt(id, "ritual_pure_completions") + 1);
                finePrintLine = "Purity clause honored: +6 experience and brief Luck.";
            } else if (purity >= 84) {
                ExperienceOrb.award(level, player.position(), 3);
                data.setUniqueInt(id, "ritual_legible_completions", data.uniqueInt(id, "ritual_legible_completions") + 1);
                finePrintLine = "The scarred rite remains legible: +3 experience.";
            }
        } else if (objective != null && objective.kind == ForbiddenBargain.Kind.COVENANT) {
            int stain = data.uniqueInt(id, "covenant_stain") + 1;
            data.setUniqueInt(id, "covenant_stain", stain);
            int deathPity = Math.min(CardBalance.DEATH_PITY_GUARANTEE, data.uniqueInt(id, "death_hand_pity") + 2);
            data.setUniqueInt(id, "death_hand_pity", deathPity);
            finePrintLine = "Covenant stain " + stain + "; Death-hand pressure deepens.";
            CthulhuEyeItem.speakInsight(player, "covenant_stain_" + Math.min(stain, 4),
                    "The covenant paid exactly what it promised. It also left something in the margin. That part was not a breach of contract.", 20L * 60L * 12L);
        }

        if (objective != null) {
            long priorBest = data.uniqueLong(id, "obj_best_time_" + key);
            if (elapsed != Long.MAX_VALUE && (priorBest == Long.MIN_VALUE || elapsed < priorBest))
                data.setUniqueLong(id, "obj_best_time_" + key, elapsed);
            data.setUniqueInt(id, "obj_record_streak_" + key,
                    Math.max(bestStreak, data.uniqueInt(id, "obj_record_streak_" + key)));
            if (objective.kind == ForbiddenBargain.Kind.RITUAL) {
                int purity = CardObjectives.ritualPurity(missteps);
                data.setUniqueInt(id, "obj_best_purity_" + key,
                        Math.max(purity, data.uniqueInt(id, "obj_best_purity_" + key)));
            }
        }

        int beforeRelation = CardMaster.MOURNING_NOTARY.relation(data, id);
        int relation = CardMaster.MOURNING_NOTARY.addRelation(data, id, finePrint ? 2 : 1);
        if (relation > beforeRelation && (relation == 3 || relation == 8 || relation == 14)) {
            CthulhuEyeItem.speakInsight(player, "notary_objective_relation_" + relation,
                    "The Mourning Notary has begun to count your completed obligations separately from your signatures.", 20L * 90L);
        }
        data.setUniqueInt(id, "objectives_completed_total", data.uniqueInt(id, "objectives_completed_total") + 1);
        if (objective != null)
            data.setUniqueInt(id, "objectives_completed_" + objective.kind.name().toLowerCase(java.util.Locale.ROOT),
                    data.uniqueInt(id, "objectives_completed_" + objective.kind.name().toLowerCase(java.util.Locale.ROOT)) + 1);
        data.setUniqueInt(id, "obj_completed_" + key, data.uniqueInt(id, "obj_completed_" + key) + 1);

        level.playSound(null, player.blockPosition(), WardSounds.MASTER_MOTIF.get(), SoundSource.PLAYERS, 0.58f, 0.92f);
        level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY(0.7), player.getZ(), 22, 0.36, 0.52, 0.36, 0.025);
        WardHud.message(player, net.minecraft.network.chat.Component.literal(title + " is fulfilled. The payment is made.")
                .withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD), false);
        if (!finePrintLine.isEmpty()) WardHud.message(player, net.minecraft.network.chat.Component.literal(finePrintLine)
                .withStyle(finePrint ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.DARK_PURPLE, net.minecraft.ChatFormatting.ITALIC), false);
        WardHistory.recordSpecial(player, "WARD", title + " fulfilled; payment collected. " + finePrintLine);
        if (objective != null && objective.kind == ForbiddenBargain.Kind.RITUAL) {
            int purity = CardObjectives.ritualPurity(missteps);
            SavantMemory.onRitual(player, key, purity);
            OccultChainEvents.onRitualComplete(player, data, purity);
        }
        MasterStory.evaluate(player, data, CardMaster.MOURNING_NOTARY);

        data.setUniqueInt(id, "obj_" + key + "_missteps", 0);
        data.setUniqueInt(id, "obj_" + key + "_streak", 0);
        data.setUniqueInt(id, "obj_" + key + "_best_streak", 0);
        data.setUniqueLong(id, "obj_" + key + "_started_at", Long.MIN_VALUE);
        data.setUniqueLong(id, "obj_" + key + "_last_progress", Long.MIN_VALUE);
    }

    private static void tickWorldPlayCards(ServerPlayer player, LockData data, UUID id) {
        if (!(player.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();

        long oreUntil = data.uniqueLong(id, "ore_whisper_until");
        if (oreUntil != Long.MIN_VALUE) {
            if (now >= oreUntil) data.setUniqueLong(id, "ore_whisper_until", Long.MIN_VALUE);
            else if (player.tickCount % 20 == 0) {
                BlockPos center = player.blockPosition();
                int oreVariant = CardEvolution.activeVariant(data, id, ForbiddenBargain.ORE_WHISPER);
                int radius = CardBranches.alternate(data, id, ForbiddenBargain.ORE_WHISPER) ? 4 : oreVariant >= 3 ? 9 : oreVariant >= 2 ? 8 : oreVariant == 1 ? 7 : 6;
                float revealChance = CardBranches.alternate(data, id, ForbiddenBargain.ORE_WHISPER) ? 1.0f : oreVariant >= 3 ? 0.29f : oreVariant >= 2 ? 0.25f : oreVariant == 1 ? 0.21f : 0.18f;
                for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -4, -radius), center.offset(radius, 4, radius))) {
                    if (level.getBlockState(pos).is(Tags.Blocks.ORES) && player.getRandom().nextFloat() < revealChance) {
                        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                                1, 0.04, 0.04, 0.04, 0.0);
                    }
                }
            }
        }

        long quicksilver = data.uniqueLong(id, "quicksilver_until");
        if (quicksilver != Long.MIN_VALUE) {
            if (now < quicksilver) {
                boolean mercurial = data.hasUnique(id, "shivering_tithe");
                if (!CardBranches.alternate(data, id, ForbiddenBargain.QUICKSILVER_PRAYER)) player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 35, 0, true, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 35, mercurial || CardBranches.alternate(data, id, ForbiddenBargain.QUICKSILVER_PRAYER) ? 1 : 0, true, false, true));
                if (mercurial) CardConjunctions.discover(player, data, CardConjunctions.Conjunction.MERCURIAL_FEVER);
            } else {
                data.setUniqueLong(id, "quicksilver_until", Long.MIN_VALUE);
                if (data.uniqueInt(id, "quicksilver_debt") > 0) {
                    data.setUniqueInt(id, "quicksilver_debt", 0);
                    int qv = CardEvolution.activeVariant(data, id, ForbiddenBargain.QUICKSILVER_PRAYER);
                    int debtSeconds = qv >= 3 ? 15 : qv >= 2 ? 20 : qv == 1 ? 25 : 30;
                    player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * debtSeconds, 0, false, true, true));
                    data.setUniqueLong(id, "quicksilver_weak_until", now + 20L * debtSeconds);
                    WardHud.message(player, net.minecraft.network.chat.Component.literal("Quicksilver Prayer has reached the slow cost hidden inside it."), false);
                }
            }
        }

        long momentum = data.uniqueLong(id, "momentum_until");
        if (momentum != Long.MIN_VALUE) {
            if (now < momentum) player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 35, CardBranches.alternate(data, id, ForbiddenBargain.BORROWED_MOMENTUM) ? 0 : 1, true, false, true));
            else {
                data.setUniqueLong(id, "momentum_until", Long.MIN_VALUE);
                if (data.uniqueInt(id, "momentum_debt") > 0) {
                    data.setUniqueInt(id, "momentum_debt", 0);
                    int mv = CardEvolution.activeVariant(data, id, ForbiddenBargain.BORROWED_MOMENTUM);
                    int debtSeconds = mv >= 3 ? 30 : mv >= 2 ? 40 : mv == 1 ? 50 : 60;
                    if (!CardBranches.alternate(data, id, ForbiddenBargain.BORROWED_MOMENTUM)) player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * debtSeconds, 0, false, true, true));
                    if (!CardBranches.alternate(data, id, ForbiddenBargain.BORROWED_MOMENTUM)) WardHud.message(player, net.minecraft.network.chat.Component.literal("Borrowed Momentum has reached the slow part of the contract."), false);
                }
            }
        }

        long eclipse = data.uniqueLong(id, "pocket_eclipse_until");
        boolean eclipseActive = eclipse != Long.MIN_VALUE && eclipse > now;
        boolean veiledGlass = data.hasUnique(id, "bellglass_sight") && eclipseActive
                && CardConditions.isDark(level, player.blockPosition());
        if (data.hasUnique(id, "bellglass_sight")) {
            boolean nightReader = CardBranches.alternate(data, id, ForbiddenBargain.BELLGLASS_SIGHT);
            if (!nightReader || level.getMaxLocalRawBrightness(player.blockPosition()) <= 4) refresh(player, MobEffects.NIGHT_VISION, true);
            int bellVariant = CardEvolution.activeVariant(data, id, ForbiddenBargain.BELLGLASS_SIGHT);
            int localLight = level.getMaxLocalRawBrightness(player.blockPosition());
            boolean bellDark = bellVariant >= 3 ? localLight <= 0 : bellVariant >= 2 ? localLight <= 2 : bellVariant == 1 ? localLight <= 4 : CardConditions.isDark(level, player.blockPosition());
            if (veiledGlass) CardConjunctions.discover(player, data, CardConjunctions.Conjunction.VEILED_GLASS);
            else if (bellDark && !nightReader) refresh(player, MobEffects.GLOWING, true);
            if (!nightReader && bellVariant >= 2 && CardConditions.isDirectDaylight(level, player.blockPosition()))
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 30, 0, true, false, true));
        }
        if (data.hasUnique(id, "dustbound_soles")) refresh(player, MobEffects.MOVEMENT_SLOWDOWN, true);
        if (data.hasUnique(id, "lantern_blood")) {
            refresh(player, MobEffects.NIGHT_VISION, true);
            if (player.getHealth() <= 10.0F) player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, true, false, true));
        }

        long compass = data.uniqueLong(id, "black_compass_until");
        if (compass != Long.MIN_VALUE) {
            if (now >= compass) data.setUniqueLong(id, "black_compass_until", Long.MIN_VALUE);
            else {
                int compassVariant = CardEvolution.activeVariant(data, id, ForbiddenBargain.BLACK_COMPASS);
                double compassRange = CardBranches.alternate(data, id, ForbiddenBargain.BLACK_COMPASS) ? 12.0D : compassVariant >= 3 ? 28.0D : compassVariant >= 2 ? 24.0D : compassVariant == 1 ? 20.0D : 16.0D;
                for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(compassRange), m -> m instanceof Enemy && m.isAlive()))
                    { mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, true, false, true));
                      if (CardBranches.alternate(data, id, ForbiddenBargain.BLACK_COMPASS)) mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0, true, false, true)); }
            }
        }

        if (eclipse != Long.MIN_VALUE) {
            if (now >= eclipse) data.setUniqueLong(id, "pocket_eclipse_until", Long.MIN_VALUE);
            else if (CardBranches.alternate(data, id, ForbiddenBargain.POCKET_ECLIPSE) ? player.isCrouching() && player.onGround() && player.getDeltaMovement().horizontalDistanceSqr() < 0.0001D : CardConditions.isDark(level, player.blockPosition()))
                { int eclipseVariant = CardEvolution.activeVariant(data, id, ForbiddenBargain.POCKET_ECLIPSE);
                  player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, veiledGlass ? 55 : eclipseVariant >= 3 ? 60 : eclipseVariant >= 2 ? 50 : eclipseVariant == 1 ? 42 : 35, 0, true, false, true)); }
        }
    }

    private static void tickDeathCards(ServerPlayer player, LockData data, UUID id) {
        if (!(player.level() instanceof ServerLevel level)) return;
        boolean auraBeat = player.tickCount % 40 == 0;
        if (data.hasUnique(id, "memento_mori") && auraBeat && data.uniqueInt(id, "mori_charge") > 0) {
            int charge = data.uniqueInt(id, "mori_charge");
            dev.marrowseal.wardbound.fx.WardLodestoneDispatch.emit(player, dev.marrowseal.wardbound.net.WardLodestoneFxPacket.DEATH_AURA, Math.min(1.15f, 0.4f + charge * 0.12f), 0.75);
        }
        if (data.hasUnique(id, "black_sun")) {
            if (level.isNight()) {
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 35, 1, true, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 35, 0, true, false, true));
                if (auraBeat && CardConditions.isOpenNight(level, player.blockPosition()))
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY(1.0), player.getZ(), 3, 0.38, 0.54, 0.38, 0.006);
                if (data.hasUnique(id, "moonlit_hunt") && CardConditions.isOpenNight(level, player.blockPosition())) {
                    player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 35, 0, true, false, true));
                    CardConjunctions.discover(player, data, CardConjunctions.Conjunction.NIGHT_CROWN);
                }
            } else if (CardConditions.isDirectDaylight(level, player.blockPosition())) {
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 35, 0, true, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 35, 0, true, false, true));
                if (auraBeat) dev.marrowseal.wardbound.fx.WardLodestoneDispatch.emit(player, dev.marrowseal.wardbound.net.WardLodestoneFxPacket.CURSE_SIGN, 0.55f);
            }
        }
        if (data.hasUnique(id, "coffin_road")) {
            refresh(player, MobEffects.MOVEMENT_SLOWDOWN, true);
            if (player.onGround() && player.tickCount % 18 == 0 && player.getDeltaMovement().horizontalDistanceSqr() > 0.002D)
                level.sendParticles(ParticleTypes.ASH, player.getX(), player.getY(0.08), player.getZ(), 2, 0.22, 0.04, 0.22, 0.004);
            if (data.hasUnique(id, "dustbound_soles") && player.onGround()) {
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 35, 0, true, false, true));
                CardConjunctions.discover(player, data, CardConjunctions.Conjunction.PALLBEARER_STEP);
            }
        }
        if (data.hasUnique(id, "grave_bell")) {
            refresh(player, MobEffects.HUNGER, true);
            if (auraBeat && data.uniqueInt(id, "grave_bell_kills") >= 4)
                dev.marrowseal.wardbound.fx.WardLodestoneDispatch.emit(player, dev.marrowseal.wardbound.net.WardLodestoneFxPacket.DEATH_AURA, 0.45f, 0.65);
        }
    }

    private static void refresh(ServerPlayer player, MobEffect effect, boolean active) {
        if (!active) return;
        MobEffectInstance current = player.getEffect(effect);
        if (current == null || current.getDuration() < 35 || current.getAmplifier() != 0) {
            player.addEffect(new MobEffectInstance(effect, 80, 0, true, false, true));
        }
    }

    private static void syncHeartDebt(ServerPlayer player, int hearts) {
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) return;
        AttributeModifier old = maxHealth.getModifier(HEART_DEBT_ID);
        double amount = -2.0D * Math.max(0, Math.min(7, hearts));
        if (old != null && Math.abs(old.getAmount() - amount) > 0.0001D) {
            maxHealth.removeModifier(old);
            old = null;
        }
        if (hearts <= 0) {
            if (old != null) maxHealth.removeModifier(old);
            return;
        }
        if (old == null) {
            maxHealth.addTransientModifier(new AttributeModifier(HEART_DEBT_ID,
                    "Wardbound card heart debt", amount, AttributeModifier.Operation.ADDITION));
        }
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }
}
