package dev.marrowseal.wardbound;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Long-lived player attunements created by consuming the two shard relics.
 * They are removed the moment they bind to the next ward the player commits to.
 */
public final class WardEffects {

    public static final DeferredRegister<MobEffect> REGISTRY =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, Wardbound.MODID);

    public static final RegistryObject<MobEffect> WARD_SPLINTER_ATTUNEMENT = REGISTRY.register(
            "ward_splinter_attunement",
            () -> new ShardAttunementEffect(0x79E9D7));

    public static final RegistryObject<MobEffect> ELDRITCH_SHARD_ATTUNEMENT = REGISTRY.register(
            "eldritch_shard_attunement",
            () -> new ShardAttunementEffect(0x9A55E8));

    /** 3:30 combat blessing granted independently of the next-ward attunement. */
    public static final RegistryObject<MobEffect> ELDRITCH_STORM = REGISTRY.register(
            "eldritch_storm",
            () -> new ShardAttunementEffect(0x8D3FE8));

    public static final RegistryObject<MobEffect> ECHO_SHARD_ATTUNEMENT = REGISTRY.register(
            "echo_shard_attunement",
            () -> new ShardAttunementEffect(0x6F9FD8));

    public static final RegistryObject<MobEffect> BLOODGLASS_FERVOR = REGISTRY.register(
            "bloodglass_fervor",
            () -> new ShardAttunementEffect(0xA93A4C));

    /**
     * While Eldritch Storm is active, every direct player attack on a mob
     * calls down a visual lightning strike and applies the damage/electrified
     * hit directly to the struck mob only. Using a visual-only bolt avoids the
     * normal area interaction that could also hurt the player.
     */
    public static void onPlayerAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getTarget() instanceof Mob target) || !target.isAlive()) return;
        if (!player.hasEffect(ELDRITCH_STORM.get())) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(target.getX(), target.getY(), target.getZ());
            bolt.setCause(player);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }

        // Direct the actual impact to the struck target so the player does not
        // get clipped by a full vanilla lightning bolt near melee range.
        target.hurt(level.damageSources().lightningBolt(), 5.0F);
        target.setSecondsOnFire(2);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1, false, true, true));
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, false, false, true));

        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY(0.55), target.getZ(),
                20, target.getBbWidth() * 0.3, target.getBbHeight() * 0.25, target.getBbWidth() * 0.3, 0.03);
        level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY(0.65), target.getZ(),
                8, target.getBbWidth() * 0.2, target.getBbHeight() * 0.2, target.getBbWidth() * 0.2, 0.01);
    }

    /** Bloodglass Shard: every fourth hostile kill during the fervor returns a little blood and hunger. */
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer victim && victim.getServer() != null) {
            LockData data = LockData.get(victim.getServer());
            if (data.uniqueInt(victim.getUUID(), "refuse_punishment_stage") == 2) {
                data.setUniqueInt(victim.getUUID(), "refuse_punishment_stage", 0);
            }
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getEntity() instanceof Enemy) || !player.hasEffect(BLOODGLASS_FERVOR.get())) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        int n = player.getPersistentData().getInt("WardboundBloodglassKills") + 1;
        if (n < 4) {
            player.getPersistentData().putInt("WardboundBloodglassKills", n);
            return;
        }
        player.getPersistentData().remove("WardboundBloodglassKills");
        player.heal(2.0F);
        player.getFoodData().eat(1, 0.25F);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, player.getX(), player.getY(0.55), player.getZ(),
                7, 0.25, 0.35, 0.25, 0.02);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.25f, 0.62f);
        WardHud.message(player, net.minecraft.network.chat.Component.literal(
                "Bloodglass drinks the fourth death and returns a little of it."), false);
    }

    private WardEffects() {
    }

    private static final class ShardAttunementEffect extends MobEffect {
        private ShardAttunementEffect(int color) {
            super(MobEffectCategory.BENEFICIAL, color);
        }
    }
}
