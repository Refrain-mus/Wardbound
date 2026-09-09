package dev.marrowseal.wardbound.champion;

import dev.marrowseal.wardbound.LockData;
import dev.marrowseal.wardbound.WardHud;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;

public final class DefeatedChampionEntity extends PathfinderMob implements GeoEntity {
    public static final int REST = 0, DRAW = 1, READY = 2, STRIKE = 3, SHEATHE = 4, TALK = 5;
    public static final int SIT_DOWN = 6, SITTING = 7, STAND_UP = 8, SHIFT = 9, LOOK = 10, CHECK_SWORD = 11, WEARY = 12, SEATED_TALK = 13, UNCONCLUDED = 14;
    private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(DefeatedChampionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DRAWN = SynchedEntityData.defineId(DefeatedChampionEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private long incarnation = -1;
    private net.minecraft.core.BlockPos camp = net.minecraft.core.BlockPos.ZERO;
    private static java.util.List<String> cachedExcludedTargetEntries = java.util.List.of();
    private static java.util.Set<ResourceLocation> cachedExcludedTargetIds = java.util.Set.of();
    private static java.util.Set<TagKey<EntityType<?>>> cachedExcludedTargetTags = java.util.Set.of();
    private int actionTicks = 0, cooldown = 0, peace = 0, idleDelay = 180, unconcludedAfterglow = 0;
    private boolean companion = false;
    private java.util.UUID companionOwner;
    private net.minecraft.core.BlockPos companionReturnCamp = net.minecraft.core.BlockPos.ZERO;
    private int companionTicks = 0, departureTicks = 0, combatMove = 0;
    private int nextMasterEncounterCheck = 0;
    private boolean cachedMasterEncounter = false;

    public DefeatedChampionEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        xpReward = 0;
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 160)
                .add(Attributes.ARMOR, 14)
                .add(Attributes.ARMOR_TOUGHNESS, 6)
                .add(Attributes.ATTACK_DAMAGE, 24)
                .add(Attributes.KNOCKBACK_RESISTANCE, .85)
                .add(Attributes.MOVEMENT_SPEED, .29)
                .add(Attributes.FOLLOW_RANGE, 24);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ACTION, REST);
        entityData.define(DRAWN, false);
    }

    public int action() { return entityData.get(ACTION); }
    public boolean drawn() { return entityData.get(DRAWN); }
    public long incarnation() { return incarnation; }
    public boolean seated() { return action() == SIT_DOWN || action() == SITTING || action() == SEATED_TALK; }
    public boolean relaxed() { return !drawn() && action() != DRAW && action() != STRIKE && action() != UNCONCLUDED; }
    public boolean companionActive() { return companion; }
    public boolean companionOwnerIs(java.util.UUID id) { return companion && id != null && id.equals(companionOwner); }
    public java.util.UUID companionOwnerId() { return companion ? companionOwner : null; }
    public net.minecraft.core.BlockPos companionReturnCamp() { return companionReturnCamp; }
    public net.minecraft.core.BlockPos campPosition() { return camp; }

    void arrive(long token, net.minecraft.core.BlockPos pos) {
        incarnation = token;
        camp = pos;
        companion = false;
        companionOwner = null;
        companionReturnCamp = pos;
        companionTicks = 0;
        departureTicks = 0;
        restrictTo(pos, 12);
        setTarget(null);
        entityData.set(DRAWN, false);
        act(REST, 0);
    }

    void startCompanion(java.util.UUID owner, net.minecraft.core.BlockPos returnCamp, int ticks) {
        companion = true;
        companionOwner = owner;
        companionReturnCamp = returnCamp == null ? camp : returnCamp;
        companionTicks = Math.max(200, ticks);
        departureTicks = 0;
        setTarget(null);
        setHealth(getMaxHealth());
        act(DRAW, 14);
    }

    private void act(int a, int ticks) {
        entityData.set(ACTION, a);
        actionTicks = ticks;
        // Authored talk/gesture/combat clips are rooted poses. Do not let a stale navigation path
        // slide Silas through them or make GeckoLib replace them with the walk cycle.
        if (a != REST && a != READY) getNavigation().stop();
    }

    public boolean previewRest(String pose) {
        if (getTarget() != null || drawn() || isInWaterOrBubble() || !onGround()) return false;
        if (pose.equals("stand")) {
            if (seated()) act(STAND_UP, 32);
            else act(REST, 0);
            return true;
        }
        if (seated() || action() == STAND_UP) return false;
        switch (pose) {
            case "sit" -> {
                act(SIT_DOWN, 36);
                idleDelay = 500;
            }
            case "shift" -> act(SHIFT, 80);
            case "look" -> act(LOOK, 80);
            case "sword" -> act(CHECK_SWORD, 80);
            case "weary" -> act(WEARY, 80);
            default -> { return false; }
        }
        return true;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new DefendGoal());
        goalSelector.addGoal(2, new Goal() {
            { setFlags(EnumSet.of(Flag.MOVE)); }
            @Override public boolean canUse() { return seated() || action() == STAND_UP; }
            @Override public void tick() { getNavigation().stop(); }
        });
        goalSelector.addGoal(3, new MoveTowardsRestrictionGoal(this, .7));
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 6));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean canBeLeashed(Player p) { return false; }
    @Override public boolean canChangeDimensions() { return false; }
    @Override public boolean causeFallDamage(float f, float m, DamageSource s) { return false; }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (action() == UNCONCLUDED) return false;
        // Outside a voluntary companion call Silas is not a combat participant at all.
        if (!companion) return false;
        if (!(source.getEntity() instanceof Mob attacker) || attacker == this || !attacker.isAlive()) return false;
        float resolved = amount;
        // Broken Guard: the old champion still remembers how to take a blow on steel.
        if (random.nextFloat() < .22f && drawn()) {
            resolved *= .28f;
            playSound(SoundEvents.SHIELD_BLOCK, .55f, .72f);
        }
        boolean hit = super.hurt(source, resolved);
        if (hit && action() != UNCONCLUDED) setTarget(attacker);
        return hit;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        var level = (ServerLevel) level();
        var record = ChampionWorldData.get(level.getServer());
        if (!record.accepts(this)) {
            discard();
            return;
        }
        if (cooldown > 0) cooldown--;
        if (unconcludedAfterglow > 0) unconcludedAfterglow--;
        if (!companion) {
            setTarget(null);
            if (tickCount % 20 == 0) {
                for (Mob mob : level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(28), m -> m != this && m.getTarget() == this)) mob.setTarget(null);
            }
            if (tickCount % 20 == 0) {
                for (Monster hostile : level.getEntitiesOfClass(Monster.class, getBoundingBox().inflate(7), m -> m.isAlive())) {
                    if (hostile.getTarget() instanceof ServerPlayer witness && witness.distanceToSqr(this) < 18 * 18) {
                        SilasLore.observeUntargeted(witness);
                    }
                }
            }
        } else if (!tickCompanion(level)) {
            record.remember(this);
            return;
        }
        if (action() == UNCONCLUDED) {
            getNavigation().stop();
            setTarget(null);
            setDeltaMovement(0, Math.min(0, getDeltaMovement().y), 0);
            if (actionTicks > 0) {
                actionTicks--;
                if (actionTicks == 94) level.playSound(null, blockPosition(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), net.minecraft.sounds.SoundSource.NEUTRAL, .22f, .52f);
                if (actionTicks == 68) level.playSound(null, blockPosition(), SoundEvents.SCULK_CATALYST_BLOOM, net.minecraft.sounds.SoundSource.NEUTRAL, .22f, .66f);
                if (actionTicks == 34) {
                    level.playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, net.minecraft.sounds.SoundSource.NEUTRAL, .28f, .58f);
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, getX(), getY() + .9, getZ(), 10, .22, .45, .22, .015);
                }
                if (actionTicks == 8) level.playSound(null, blockPosition(), SoundEvents.ARMOR_EQUIP_IRON, net.minecraft.sounds.SoundSource.NEUTRAL, .20f, .72f);
            }
            if (actionTicks <= 0) {
                setHealth(Math.max(32.0f, getMaxHealth() * .30f));
                invulnerableTime = 30;
                act(WEARY, 80);
                idleDelay = 260;
            }
            record.remember(this);
            return;
        }
        LivingEntity foe = getTarget();
        if (foe != null && (!foe.isAlive() || distanceToSqr(foe) > 576 || foe.blockPosition().distSqr(camp) > 900)) {
            setTarget(null);
            foe = null;
        }
        if ((isInWaterOrBubble() || !onGround()) && seated()) act(STAND_UP, 32);

        if (actionTicks > 0) {
            actionTicks--;
            if (action() == DRAW && actionTicks == 4) {
                entityData.set(DRAWN, true);
                playSound(SoundEvents.ARMOR_EQUIP_IRON, .5f, .8f);
                if (companion && foe != null && distanceToSqr(foe) < 12.25 && hasLineOfSight(foe)) {
                    foe.hurt(damageSources().mobAttack(this), 18.0f);
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK, foe.getX(), foe.getY() + foe.getBbHeight() * .55, foe.getZ(), 1, 0, 0, 0, 0);
                }
            }
            if (action() == STRIKE && actionTicks == 6 && foe != null && distanceToSqr(foe) < 10.24 && hasLineOfSight(foe)) {
                if (combatMove == 2) {
                    // Old Champion's Sweep: used only when the call has drawn a crowd.
                    for (Mob mob : level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(3.2), this::validCompanionTarget)) {
                        mob.hurt(damageSources().mobAttack(this), 17.0f);
                    }
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK, getX(), getY() + 1.1, getZ(), 3, .5, .1, .5, 0);
                } else {
                    super.doHurtTarget(foe);
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK, getX(), getY() + 1.1, getZ(), 1, 0, 0, 0, 0);
                }
                playSound(SoundEvents.PLAYER_ATTACK_SWEEP, .7f, combatMove == 2 ? .62f : .75f);
                combatMove = 0;
            }
            if (actionTicks == 0) {
                int finished = action();
                if (finished == SHEATHE) entityData.set(DRAWN, false);
                if (finished == SIT_DOWN || finished == SEATED_TALK) act(SITTING, 0);
                else {
                    act(drawn() ? READY : REST, 0);
                    idleDelay = 160 + random.nextInt(200);
                }
            }
        }

        if (foe == null) {
            peace++;
            if (peace > 60 && drawn() && actionTicks == 0) act(SHEATHE, 14);
            if (tickCount % 60 == 0) heal(3);
        } else peace = 0;

        if (foe == null && !drawn() && actionTicks == 0 && getNavigation().isDone() && onGround() && !isInWaterOrBubble()) {
            if (--idleDelay <= 0) {
                if (action() == SITTING) {
                    act(STAND_UP, 32);
                } else if (action() == REST) {
                    int choice = random.nextInt(5);
                    if (choice == 0 && level.noCollision(getBoundingBox().inflate(.65, 0, .9))) {
                        act(SIT_DOWN, 36);
                        idleDelay = 300 + random.nextInt(400);
                    } else {
                        act(switch (choice) {
                            case 1 -> SHIFT;
                            case 2 -> LOOK;
                            case 3 -> CHECK_SWORD;
                            default -> WEARY;
                        }, 80);
                    }
                }
            }
        }

        if (tickCount % 100 == 0) {
            if (!level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(80), p -> !p.isSpectator()).isEmpty()) {
                record.lastSeen = level.getGameTime();
                record.setDirty();
            }
            record.remember(this);
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player instanceof ServerPlayer p) {
            if (getTarget() != null) {
                WardHud.send(p, net.minecraft.network.chat.Component.translatable("silas.busy").getString(), WardHud.Mood.WARD);
                return InteractionResult.CONSUME;
            }
            if (action() == SIT_DOWN || action() == STAND_UP || action() == UNCONCLUDED) return InteractionResult.CONSUME;
            if (!companion && ChampionDialogue.offerCompleteTestimony(p, player.getItemInHand(hand))) {
                getLookControl().setLookAt(p, 30, 30);
                act(LOOK, 80);
                return InteractionResult.CONSUME;
            }
            if (!companion && ChampionDialogue.reactToAwakenedNhal(p, player.getItemInHand(hand))) {
                getLookControl().setLookAt(p, 30, 30);
                act(CHECK_SWORD, 80);
                return InteractionResult.CONSUME;
            }
            if (unconcludedAfterglow > 0) {
                unconcludedAfterglow = 0;
                if (ChampionDialogue.speakAfterUnconcluded(p)) {
                    getLookControl().setLookAt(p, 30, 30);
                    act(seated() ? SEATED_TALK : TALK, 50);
                }
            } else if (ChampionDialogue.speak(p, false)) {
                getLookControl().setLookAt(p, 30, 30);
                int nextAction = conversationAction(p);
                act(nextAction, nextAction == TALK || nextAction == SEATED_TALK ? 50 : 80);
            }
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }


    private int conversationAction(ServerPlayer player) {
        if (seated()) return SEATED_TALK;
        if (player == null || player.getServer() == null) return TALK;
        LockData data = LockData.get(player.getServer());
        int stage = data.uniqueInt(player.getUUID(), "silas_latest_stage");
        int index = data.uniqueInt(player.getUUID(), "silas_latest_index");
        if (stage == SilasProgression.HANDS) {
            if (index == 1) return CHECK_SWORD; // Gambler: hand goes to Nhal-Sûl, but does not draw.
            if (index == 2) return LOOK;        // Curator: his gaze leaves the player for an empty point.
            if (index == 3) return WEARY;       // Notary: the remembered judgment stills him.
        }
        if (stage == SilasProgression.UNCONCLUDED) {
            if (index == 1) return CHECK_SWORD;
            if (index == 0 || index == 4) return WEARY;
        }
        return TALK;
    }

    /** Silas cannot acquire a completed death. Fatal damage enters a six-second unresolved state instead. */
    @Override
    public void die(DamageSource source) {
        if (level().isClientSide || action() == UNCONCLUDED) return;
        setHealth(1.0f);
        deathTime = 0;
        invulnerableTime = 120;
        hurtTime = 10;
        setTarget(null);
        getNavigation().stop();
        act(UNCONCLUDED, 120);
        unconcludedAfterglow = 600;
        playSound(SoundEvents.AMETHYST_CLUSTER_BREAK, 0.42f, 0.58f);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.ASH, getX(), getY() + 1.0, getZ(), 18, .32, .65, .32, .01);
            for (ServerPlayer witness : serverLevel.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(18), p -> !p.isSpectator())) {
                SilasLore.observeUnconcluded(witness);
            }
            ChampionWorldData.get(serverLevel.getServer()).remember(this);
        }
    }

    @Override protected void dropAllDeathLoot(DamageSource source) {}

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putLong("SilasIncarnation", incarnation);
        t.putLong("SilasCamp", camp.asLong());
        t.putBoolean("SilasSeated", seated());
        t.putBoolean("SilasDrawn", drawn());
        t.putInt("SilasUnconcludedTicks", action() == UNCONCLUDED ? Math.max(0, actionTicks) : 0);
        t.putInt("SilasIdleDelay", idleDelay);
        t.putInt("SilasUnconcludedAfterglow", unconcludedAfterglow);
        t.putBoolean("SilasCompanion", companion);
        if (companionOwner != null) t.putUUID("SilasCompanionOwner", companionOwner);
        t.putLong("SilasCompanionReturnCamp", companionReturnCamp.asLong());
        t.putInt("SilasCompanionTicks", companionTicks);
        t.putInt("SilasDepartureTicks", departureTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        incarnation = t.contains("SilasIncarnation") ? t.getLong("SilasIncarnation") : -1;
        camp = net.minecraft.core.BlockPos.of(t.getLong("SilasCamp"));
        restrictTo(camp, 12);
        entityData.set(DRAWN, t.getBoolean("SilasDrawn"));
        int unresolved = Math.max(0, Math.min(120, t.getInt("SilasUnconcludedTicks")));
        if (unresolved > 0) act(UNCONCLUDED, unresolved);
        else if (t.getBoolean("SilasSeated")) act(SITTING, 0);
        idleDelay = Math.max(80, Math.min(700, t.getInt("SilasIdleDelay")));
        unconcludedAfterglow = Math.max(0, Math.min(600, t.getInt("SilasUnconcludedAfterglow")));
        companion = t.getBoolean("SilasCompanion");
        companionOwner = t.hasUUID("SilasCompanionOwner") ? t.getUUID("SilasCompanionOwner") : null;
        companionReturnCamp = t.contains("SilasCompanionReturnCamp") ? net.minecraft.core.BlockPos.of(t.getLong("SilasCompanionReturnCamp")) : camp;
        companionTicks = Math.max(0, Math.min(600 * 20, t.getInt("SilasCompanionTicks")));
        departureTicks = Math.max(0, Math.min(40, t.getInt("SilasDepartureTicks")));
        if (companion && companionOwner == null) departureTicks = 40;
    }

    private boolean tickCompanion(ServerLevel level) {
        if (!companion) return true;
        ServerPlayer owner = companionOwner == null ? null : level.getServer().getPlayerList().getPlayer(companionOwner);
        if (departureTicks > 0) {
            getNavigation().stop();
            setTarget(null);
            if (departureTicks == 36 && drawn()) act(SHEATHE, 14);
            departureTicks--;
            if (departureTicks <= 0) SilasCompanion.returnToRoad(this);
            return false;
        }
        if (owner == null || !owner.isAlive() || owner.isSpectator() || owner.level() != level) {
            departureTicks = 40;
            setTarget(null);
            getNavigation().stop();
            return false;
        }
        if (tickCount >= nextMasterEncounterCheck) {
            cachedMasterEncounter = SilasCompanion.masterEncounterNearby(owner);
            nextMasterEncounterCheck = tickCount + 20;
            refreshExcludedTargetRules();
        }
        if (!cachedMasterEncounter && --companionTicks <= 0) {
            departureTicks = 40;
            setTarget(null);
            getNavigation().stop();
            return false;
        }
        camp = owner.blockPosition();
        restrictTo(camp, 32);
        LivingEntity foe = getTarget();
        if (foe != null && (!foe.isAlive() || !validCompanionTarget(foe) || distanceToSqr(foe) > 32 * 32)) {
            setTarget(null);
            foe = null;
        }
        if (foe == null && tickCount % 10 == 0) {
            Mob best = null;
            double bestD = Double.MAX_VALUE;
            AABB search = owner.getBoundingBox().inflate(24, 12, 24);
            for (Mob mob : level.getEntitiesOfClass(Mob.class, search, this::validCompanionTarget)) {
                double d = distanceToSqr(mob);
                if (d < bestD) { bestD = d; best = mob; }
            }
            if (best != null) setTarget(best);
        }
        if (tickCount % 20 == 0) {
            for (Mob enemy : level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(10), this::validCompanionTarget)) {
                if (enemy.getTarget() == null || enemy.getTarget() == owner) enemy.setTarget(this);
            }
        }
        if (getTarget() == null) {
            double d = distanceToSqr(owner);
            if (d > 7 * 7) getNavigation().moveTo(owner, d > 15 * 15 ? 1.28 : .95);
            else getNavigation().stop();
        }
        return true;
    }

    private boolean validCompanionTarget(LivingEntity entity) {
        if (!companion || entity == null || entity == this || !entity.isAlive()) return false;
        if (!(entity instanceof Mob mob)) return false;
        ResourceLocation key = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        if (key != null && cachedExcludedTargetIds.contains(key)) return false;
        for (TagKey<EntityType<?>> tag : cachedExcludedTargetTags) if (mob.getType().is(tag)) return false;
        ServerPlayer owner = companionOwner == null || !(level() instanceof ServerLevel serverLevel)
                ? null : serverLevel.getServer().getPlayerList().getPlayer(companionOwner);
        return mob instanceof Monster || mob.getType().getCategory() == MobCategory.MONSTER || (owner != null && mob.getTarget() == owner);
    }

    private static void refreshExcludedTargetRules() {
        java.util.List<String> entries = ChampionConfig.silasCompanionExcludedTargets();
        if (entries.equals(cachedExcludedTargetEntries)) return;
        cachedExcludedTargetEntries = java.util.List.copyOf(entries);
        java.util.Set<ResourceLocation> ids = new java.util.HashSet<>();
        java.util.Set<TagKey<EntityType<?>>> tags = new java.util.HashSet<>();
        for (String raw : entries) {
            if (raw == null) continue;
            String entry = raw.trim();
            if (entry.isEmpty()) continue;
            boolean isTag = entry.startsWith("#");
            ResourceLocation id = ResourceLocation.tryParse(isTag ? entry.substring(1) : entry);
            if (id == null) continue;
            if (isTag) tags.add(TagKey.create(Registries.ENTITY_TYPE, id));
            else ids.add(id);
        }
        cachedExcludedTargetIds = java.util.Set.copyOf(ids);
        cachedExcludedTargetTags = java.util.Set.copyOf(tags);
    }

    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "silas", 5, s -> {
            int currentAction = action();
            boolean locomotion = s.isMoving() && !seated() && (currentAction == REST || currentAction == READY);
            String clip = locomotion
                    ? "walk"
                    : switch (currentAction) {
                        case DRAW -> "draw";
                        case READY -> "guard";
                        case STRIKE -> "strike";
                        case SHEATHE -> "sheathe";
                        case TALK -> "talk";
                        case SIT_DOWN -> "sit_down";
                        case SITTING -> "sitting";
                        case STAND_UP -> "stand_up";
                        case SHIFT -> "shift";
                        case LOOK -> "look";
                        case CHECK_SWORD -> "check_sword";
                        case WEARY -> "weary";
                        case SEATED_TALK -> "seated_talk";
                        case UNCONCLUDED -> "unconcluded";
                        default -> "rest";
                    };
            var animation = RawAnimation.begin();
            String name = "animation.defeated_champion." + clip;
            boolean loop = clip.equals("rest") || clip.equals("walk") || clip.equals("guard") || clip.equals("sitting");
            return s.setAndContinue(loop ? animation.thenLoop(name) : animation.thenPlayAndHold(name));
        }));
    }

    private final class DefendGoal extends Goal {
        DefendGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() { return companion && getTarget() != null && getTarget().isAlive(); }
        @Override public void stop() { getNavigation().stop(); }
        @Override
        public void tick() {
            LivingEntity foe = getTarget();
            if (foe == null) return;
            getLookControl().setLookAt(foe, 30, 30);
            if (action() == SIT_DOWN) {
                getNavigation().stop();
                return;
            }
            if (seated()) {
                getNavigation().stop();
                act(STAND_UP, 32);
                return;
            }
            if (action() == STAND_UP) {
                getNavigation().stop();
                return;
            }
            if (!drawn()) {
                getNavigation().stop();
                if (action() != DRAW) act(DRAW, 14);
                return;
            }
            if (actionTicks > 0) {
                getNavigation().stop();
                return;
            }
            if (distanceToSqr(foe) > 7.8) {
                double d = distanceToSqr(foe);
                if (d < 64.0 && cooldown == 0 && random.nextFloat() < .10f) {
                    // Wayfarer Step: a short, controlled lunge rather than a teleport.
                    net.minecraft.world.phys.Vec3 step = foe.position().subtract(position());
                    if (step.lengthSqr() > .01) {
                        step = step.normalize().scale(.58);
                        setDeltaMovement(step.x, Math.max(getDeltaMovement().y, .06), step.z);
                        cooldown = 8;
                        if (level() instanceof ServerLevel serverLevel) {
                            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.ASH, getX(), getY() + .2, getZ(), 5, .18, .08, .18, .01);
                        }
                    }
                } else getNavigation().moveTo(foe, 1.1);
            } else {
                getNavigation().stop();
                if (cooldown == 0 && hasLineOfSight(foe)) {
                    int crowd = level().getEntitiesOfClass(Mob.class, getBoundingBox().inflate(3.4), DefeatedChampionEntity.this::validCompanionTarget).size();
                    combatMove = crowd >= 3 ? 2 : 0;
                    act(STRIKE, 14);
                    cooldown = combatMove == 2 ? 38 : 26;
                }
            }
        }
    }
}
