package dev.marrowseal.wardbound.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import dev.marrowseal.wardbound.ChestValuator;

import javax.annotation.Nullable;
import java.util.List;

/** A field-earned card kept closed until the player chooses a safe moment to read it. */
public class SealedCardItem extends Item {
    public static final String TAG_DEALER = "WardboundCardDealer";

    public SealedCardItem(Properties properties) {
        super(properties);
    }

    public static void setDealer(ItemStack stack, String dealerId) {
        if (stack == null || stack.isEmpty()) return;
        if (dealerId == null || dealerId.isBlank()) stack.getOrCreateTag().remove(TAG_DEALER);
        else stack.getOrCreateTag().putString(TAG_DEALER, dealerId);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            if (ChestValuator.openLooseCard(serverPlayer, stack)) {
                if (!player.getAbilities().instabuild) stack.shrink(1);
                return InteractionResultHolder.consume(stack);
            }
            return InteractionResultHolder.fail(stack);
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("The back bears no name you can read.")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
