package dev.marrowseal.wardbound.item;

import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.champion.SilasLore;
import dev.marrowseal.wardbound.net.OpenSilasFragmentPacket;
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
import net.minecraftforge.network.PacketDistributor;

import java.util.List;

/** A physical testimony fragment. It remains as an object after being copied into the Witness Ledger. */
public final class SilasFragmentItem extends Item {
    public SilasFragmentItem(Properties properties) {
        super(properties);
    }

    public static SilasLore.Fragment fragment(ItemStack stack) {
        if (stack == null || !stack.hasTag()) return null;
        return SilasLore.Fragment.byId(stack.getTag().getString(SilasLore.FRAGMENT_TAG));
    }

    @Override
    public Component getName(ItemStack stack) {
        SilasLore.Fragment fragment = fragment(stack);
        return fragment == null ? Component.translatable("item.wardbound.silas_fragment") : Component.translatable("silas.fragment.title." + fragment.id);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        SilasLore.Fragment fragment = fragment(stack);
        if (fragment == null) {
            tooltip.add(Component.translatable("item.wardbound.silas_fragment.desc").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.add(Component.literal(fragment.family.title).withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("item.wardbound.silas_fragment.read").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.wardbound.silas_fragment.assemble").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        SilasLore.Fragment fragment = fragment(stack);
        if (fragment == null) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                if (SilasLore.assembleTestimony(serverPlayer)) return InteractionResultHolder.consume(stack);
                serverPlayer.sendSystemMessage(Component.translatable("silas.fragment.assemble_progress", SilasLore.physicalCount(serverPlayer))
                        .withStyle(ChatFormatting.DARK_GRAY));
                return InteractionResultHolder.consume(stack);
            }
            if (!SilasLore.canRead(serverPlayer, fragment)) {
                serverPlayer.sendSystemMessage(Component.translatable("silas.fragment.not_yet").withStyle(ChatFormatting.DARK_GRAY));
                return InteractionResultHolder.consume(stack);
            }
            SilasLore.discover(serverPlayer, fragment, true);
            Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new OpenSilasFragmentPacket(fragment.id, fragment.family.ordinal()));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
