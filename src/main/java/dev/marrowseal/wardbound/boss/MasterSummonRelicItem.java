package dev.marrowseal.wardbound.boss;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Shared physical rules for the known Master invocation relics, including Maestro's fourth score. */
public abstract class MasterSummonRelicItem extends Item {
    protected MasterSummonRelicItem(Properties properties) { super(properties); }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (!entity.level().isClientSide) {
            entity.setInvulnerable(true);
            entity.setUnlimitedLifetime();
            entity.clearFire();
        }
        return false;
    }
}
