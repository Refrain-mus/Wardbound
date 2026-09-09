
package dev.marrowseal.wardbound;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * Server-authoritative short stun used by the Cthulhu Idol.
 *
 * <p>The original implementation only applied maximum slowness. That stopped
 * walking but did not actually stop AI, melee/ranged goals or target
 * reacquisition. The freeze now sets NoAI for the duration and stores the
 * original state in Forge persistent entity data. Because those tags are saved,
 * a chunk unload or even a server restart cannot strand a mob permanently with
 * NoAI=true: its normal state is restored when the timer expires.</p>
 */
@Mod.EventBusSubscriber(modid = Wardbound.MODID)
public final class CthulhuFreeze {

    private static final String TAG_UNTIL = "WardboundCthulhuFreezeUntil";
    private static final String TAG_ORIGINAL_NO_AI = "WardboundCthulhuOriginalNoAI";
    private static final Vector3f MARK_MAIN = new Vector3f(0.55f, 0.96f, 0.90f);
    private static final Vector3f MARK_SECOND = new Vector3f(0.96f, 0.88f, 0.63f);

    private CthulhuFreeze() {
    }

    public static void apply(Mob mob, int ticks) {
        if (mob.level().isClientSide || ticks <= 0) return;
        CompoundTag data = mob.getPersistentData();
        if (!data.contains(TAG_UNTIL)) {
            data.putBoolean(TAG_ORIGINAL_NO_AI, mob.isNoAi());
        }
        long until = mob.level().getGameTime() + ticks;
        data.putLong(TAG_UNTIL, Math.max(data.getLong(TAG_UNTIL), until));
        hold(mob);
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide) return;
        CompoundTag data = mob.getPersistentData();
        if (!data.contains(TAG_UNTIL)) return;

        if (mob.level().getGameTime() >= data.getLong(TAG_UNTIL)) {
            boolean original = data.getBoolean(TAG_ORIGINAL_NO_AI);
            data.remove(TAG_UNTIL);
            data.remove(TAG_ORIGINAL_NO_AI);
            mob.setNoAi(original);
            return;
        }
        hold(mob);
        spawnMarker(mob);
    }

    private static void hold(Mob mob) {
        mob.setNoAi(true);
        mob.getNavigation().stop();
        mob.setTarget(null);
        mob.setDeltaMovement(0.0, Math.min(0.0, mob.getDeltaMovement().y), 0.0);
    }

    private static void spawnMarker(Mob mob) {
        if (mob.level().getGameTime() % 4 != 0) return;
        double x = mob.getX();
        double y = mob.getY() + mob.getBbHeight() + 0.45;
        double z = mob.getZ();
        double t = mob.level().getGameTime() * 0.24;

        if (!(mob.level() instanceof ServerLevel level)) return;
        // eldritch cross ring
        for (int i = 0; i < 4; i++) {
            double ang = t + i * (Math.PI / 2.0);
            double px = x + Math.cos(ang) * 0.22;
            double pz = z + Math.sin(ang) * 0.22;
            level.sendParticles(new DustParticleOptions(MARK_MAIN, 0.95f), px, y, pz, 1, 0.0, 0.0, 0.0, 0.0);
        }
        // central eye/glyph pulse
        level.sendParticles(new DustParticleOptions(MARK_SECOND, 0.8f), x, y + 0.02, z, 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.ENCHANT, x, y + 0.08, z, 1, 0.0, 0.0, 0.0, 0.0);
        if (mob.level().getGameTime() % 8 == 0) {
            level.sendParticles(ParticleTypes.SCULK_SOUL, x, y + 0.12, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
