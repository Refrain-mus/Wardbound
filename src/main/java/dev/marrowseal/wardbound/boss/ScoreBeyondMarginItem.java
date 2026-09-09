package dev.marrowseal.wardbound.boss;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import java.util.List;

/** A fourth signature that appears only after the three known Masters are concluded. */
public final class ScoreBeyondMarginItem extends MasterSummonRelicItem {
    @Override public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity, int slotId, boolean isSelected) {
        ImportantRelicHeldFx.tick(level, entity, stack, isSelected, 0.08f, 0.74f, 0.68f, 0.56f, 0.28f, 0.84f);
        super.inventoryTick(stack, level, entity, slotId, isSelected);
    }
    public ScoreBeyondMarginItem(Properties p) { super(p); }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer p) {
            boolean ok = MaestroProgression.summon(p, false, p.isCreative());
            return ok ? InteractionResultHolder.consume(stack) : InteractionResultHolder.fail(stack);
        }
        return InteractionResultHolder.success(stack);
    }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.literal("No seal, archive or court claims the handwriting."));
        lines.add(Component.literal("Its author never belonged to the road of the three known Masters."));
        lines.add(Component.literal("Right-click to follow the score beyond their record."));
    }
}
