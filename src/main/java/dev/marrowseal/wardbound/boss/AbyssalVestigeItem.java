package dev.marrowseal.wardbound.boss;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import java.util.List;

/** Future final-ritual key. No full Cthulhu summon is implemented in this pass. */
public final class AbyssalVestigeItem extends Item {
    @Override public boolean isFoil(ItemStack stack) { return true; }
    @Override public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity, int slotId, boolean isSelected) {
        ImportantRelicHeldFx.tick(level, entity, stack, isSelected, 0.10f, 0.92f, 0.98f, 0.05f, 0.54f, 0.65f);
        super.inventoryTick(stack, level, entity, slotId, isSelected);
    }
    public AbyssalVestigeItem(Properties p) { super(p); }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.literal("This did not belong to the severed head."));
        lines.add(Component.literal("It remembers a body that never entered the arena."));
        lines.add(Component.literal("The Cthulhu Idol answers it. The altar has not yet been written."));
    }
}
