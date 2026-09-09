package dev.marrowseal.wardbound.boss;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import java.util.List;

/** Maestro's posthumous notation. It will only open from the End. */
public final class CanticleForSleeperItem extends Item {
    @Override public boolean isFoil(ItemStack stack) { return true; }
    @Override public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity, int slotId, boolean isSelected) {
        ImportantRelicHeldFx.tick(level, entity, stack, isSelected, 0.08f, 0.80f, 0.86f, 0.04f, 0.44f, 0.50f);
        super.inventoryTick(stack, level, entity, slotId, isSelected);
    }
    public CanticleForSleeperItem(Properties p) { super(p); }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer p) {
            boolean ok = CthulhuHeadProgression.invoke(p);
            return ok ? InteractionResultHolder.consume(stack) : InteractionResultHolder.fail(stack);
        }
        return InteractionResultHolder.success(stack);
    }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.literal("Maestro scored it from the silences Cthulhu left behind."));
        lines.add(Component.literal("The notation follows one old scar, and only beneath the End sky."));
        lines.add(Component.literal("It does not summon the whole thing."));
    }
}
