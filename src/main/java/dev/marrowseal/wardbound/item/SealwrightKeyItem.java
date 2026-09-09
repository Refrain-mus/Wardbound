package dev.marrowseal.wardbound.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A sealwright's own key, taken off the last seal of one of their chains.
 *
 * <p>The only item in this mod that belongs to a particular person. It carries
 * the name of the house that cast it, and it works on that house's seals and on
 * nothing else - which is the point of it. Every other charm is a resource you
 * spend on whatever is in front of you; this one is a reason to care which name
 * is in the footer, and it turns a system that was flavour into something you
 * plan around for the rest of the save.
 *
 * <p>It is also the only charm that is not consumed by ordinary use, because a
 * reward for a five link chain that evaporates in two chests is not a reward.
 * It wears down, slowly, and it is worth going and finding another chain by the
 * same hand before it goes.
 */
public class SealwrightKeyItem extends CharmItem {

    private static final String HOUSE = "WardHouse";

    public SealwrightKeyItem(Properties properties) {
        super(properties, Charm.SEALWRIGHT);
    }

    public static ItemStack of(Item item, String house) {
        ItemStack stack = new ItemStack(item);
        stack.getOrCreateTag().putString(HOUSE, house);
        return stack;
    }

    /** Which house cast this key, or empty if somebody spawned one bare. */
    public static String houseOf(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? "" : tag.getString(HOUSE);
    }

    @Override
    public Component getName(ItemStack stack) {
        String house = houseOf(stack);
        return house.isEmpty()
                ? super.getName(stack)
                : Component.translatable("item.wardbound.sealwrights_key.named", house);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        String house = houseOf(stack);
        tooltip.add(Component.translatable("item.wardbound.sealwrights_key.desc")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        if (!house.isEmpty()) {
            tooltip.add(Component.translatable("wardbound.tooltip.key_house", house)
                    .withStyle(ChatFormatting.GOLD));
        }
        tooltip.add(Component.translatable("wardbound.tooltip.lives", charm.bonusLives())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("wardbound.tooltip.loot",
                        Math.round((charm.lootBonus() - 1f) * 100f))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("wardbound.tooltip.uses",
                        stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage())
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Brass, kept, and quietly proud of itself. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return !houseOf(stack).isEmpty();
    }
}
