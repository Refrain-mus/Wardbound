package dev.marrowseal.wardbound.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** The twelve physical fragments bound into one record. Its purpose is to be returned to Silas. */
public final class UnconcludedTestimonyItem extends Item {
    public UnconcludedTestimonyItem(Properties properties) { super(properties); }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.wardbound.unconcluded_testimony.desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.wardbound.unconcluded_testimony.use").withStyle(ChatFormatting.DARK_AQUA));
    }
}
