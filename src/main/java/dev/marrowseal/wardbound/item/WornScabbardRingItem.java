package dev.marrowseal.wardbound.item;

import dev.marrowseal.wardbound.champion.SilasCompanion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.UUID;

/** A visually unremarkable ring once fixed to Nhal-Sûl's scabbard. */
public final class WornScabbardRingItem extends Item {
    private static final String OWNER = "SilasRingOwner";

    public WornScabbardRingItem(Properties properties) { super(properties); }

    public static ItemStack boundTo(ServerPlayer player) {
        ItemStack stack = new ItemStack(WardItems.WORN_SCABBARD_RING.get());
        if (player != null) stack.getOrCreateTag().putUUID(OWNER, player.getUUID());
        return stack;
    }

    public static boolean belongsTo(ItemStack stack, ServerPlayer player) {
        if (stack == null || player == null) return false;
        if (!stack.hasTag() || !stack.getTag().hasUUID(OWNER)) return player.hasPermissions(2);
        UUID owner = stack.getTag().getUUID(OWNER);
        return owner.equals(player.getUUID());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResultHolder.pass(stack);
        return SilasCompanion.call(serverPlayer, stack)
                ? InteractionResultHolder.consume(stack)
                : InteractionResultHolder.fail(stack);
    }
}
