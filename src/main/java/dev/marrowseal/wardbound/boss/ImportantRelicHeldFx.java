package dev.marrowseal.wardbound.boss;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/** Lightweight selected/offhand aura for high-importance relic items. */
public final class ImportantRelicHeldFx {
    private ImportantRelicHeldFx() {}

    public static void tick(Level level, Entity entity, ItemStack stack, boolean isSelected,
                            float r1, float g1, float b1,
                            float r2, float g2, float b2) {
        if (!level.isClientSide || !(entity instanceof Player player)) return;
        if (!(isSelected || player.getOffhandItem() == stack)) return;
        long time = level.getGameTime() + slotSalt(stack);
        if (time % 5L != 0L) return;

        double px = player.getX() + (level.random.nextDouble() - 0.5) * 0.42;
        double py = player.getY() + 1.04 + level.random.nextDouble() * 0.35;
        double pz = player.getZ() + (level.random.nextDouble() - 0.5) * 0.42;
        level.addParticle(new DustParticleOptions(new Vector3f(r1, g1, b1), 0.95f), px, py, pz, 0.0, 0.008, 0.0);
        level.addParticle(new DustParticleOptions(new Vector3f(r2, g2, b2), 0.75f), px, py + 0.03, pz, 0.0, 0.012, 0.0);
        level.addParticle(ParticleTypes.ENCHANT, px, py + 0.06, pz, 0.0, 0.014, 0.0);
    }

    private static long slotSalt(ItemStack stack) {
        return Math.abs((long)System.identityHashCode(stack)) % 5L;
    }
}
