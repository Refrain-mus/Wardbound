package dev.marrowseal.wardbound.boss;

import dev.marrowseal.wardbound.WardHud;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.net.OpenDealersHandPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;

/** The Pale Gambler's private combat hand. This item never participates in the normal card pools. */
public final class DealersHandItem extends ImportantRelicItem {
    public static final String OFFER_ROOT = "wardbound_dealers_hand_offer";
    public static final int COOLDOWN_TICKS = 20 * 45;
    private static final long OFFER_LIFETIME = 20L * 12L;

    public DealersHandItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer sp)) return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        if (sp.getCooldowns().isOnCooldown(this)) {
            WardHud.send(sp, "DEALER'S HAND // The cards are still remembering the last violence.", WardHud.Mood.WARD, 1800);
            return InteractionResultHolder.fail(stack);
        }

        var root = sp.getPersistentData();
        long now = sp.serverLevel().getGameTime();
        if (root.contains(OFFER_ROOT)) {
            var old = root.getCompound(OFFER_ROOT);
            if (old.getLong("Expiry") >= now && validSkill(old.getInt("Left")) && validSkill(old.getInt("Right"))) {
                Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                        new OpenDealersHandPacket(old.getInt("Left"), old.getInt("Right"), old.getLong("Nonce")));
                return InteractionResultHolder.success(stack);
            }
            root.remove(OFFER_ROOT);
        }

        int left = sp.getRandom().nextInt(DealerHandSkills.COUNT);
        int right = sp.getRandom().nextInt(DealerHandSkills.COUNT - 1);
        if (right >= left) right++;
        long nonce = sp.getRandom().nextLong() ^ now ^ ((long)sp.getId() << 32);
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putInt("Left", left);
        tag.putInt("Right", right);
        tag.putLong("Nonce", nonce);
        tag.putLong("Expiry", now + OFFER_LIFETIME);
        root.put(OFFER_ROOT, tag);
        Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new OpenDealersHandPacket(left, right, nonce));
        return InteractionResultHolder.success(stack);
    }

    private static boolean validSkill(int id) { return id >= 0 && id < DealerHandSkills.COUNT; }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Right-click to draw two attacks from the Gambler's private hand."));
        tooltip.add(Component.literal("Closing the draw does not reshuffle it. The same pair waits until the offer expires."));
    }
}
