package dev.marrowseal.wardbound.boss;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;

/** Shared physical protection for progression-critical relics and major Master rewards. */
public class ImportantRelicItem extends Item {
    public ImportantRelicItem(Properties properties) { super(properties); }

    @Override
    public boolean isFoil(ItemStack stack) { return true; }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (!entity.level().isClientSide) {
            entity.setInvulnerable(true);
            entity.setUnlimitedLifetime();
            entity.clearFire();
            if (entity.level() instanceof ServerLevel level && entity.getY() < level.getMinBuildHeight() - 8) {
                ServerPlayer nearest = level.players().stream()
                        .filter(p -> !p.isSpectator() && p.isAlive())
                        .min(java.util.Comparator.comparingDouble(p -> p.distanceToSqr(entity)))
                        .orElse(null);
                if (nearest != null) {
                    entity.setPos(nearest.getX(), nearest.getY() + 1.0, nearest.getZ());
                } else {
                    BlockPos base = level.getSharedSpawnPos();
                    BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base);
                    entity.setPos(top.getX() + .5, Math.max(level.getMinBuildHeight() + 4, top.getY() + 1), top.getZ() + .5);
                }
                entity.setDeltaMovement(0, .08, 0);
                entity.setNoPickUpDelay();
            }
        }
        return false;
    }
}
