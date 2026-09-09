package dev.marrowseal.wardbound.boss;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/** The invitation is a realm key; ordinary-world floor geometry is no longer part of invocation. */
public class GamblerInvitationItem extends MasterSummonRelicItem {
    public GamblerInvitationItem(Properties p) { super(p); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack=player.getItemInHand(hand);
        if(player instanceof ServerPlayer p){
            boolean ok=GamblerProgression.summon(p,p.isCreative());
            return ok?InteractionResultHolder.consume(stack):InteractionResultHolder.fail(stack);
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext c) {
        if(c.getPlayer() instanceof ServerPlayer p)
            return GamblerProgression.summon(p,p.isCreative())?InteractionResult.CONSUME:InteractionResult.FAIL;
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.literal("The paper has stopped measuring ordinary rooms."));
        lines.add(Component.literal("Right-click when you are willing to follow the invitation to the Last Table."));
    }
}
