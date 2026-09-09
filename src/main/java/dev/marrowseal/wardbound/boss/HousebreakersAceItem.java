package dev.marrowseal.wardbound.boss;

import dev.marrowseal.wardbound.LockData;
import dev.marrowseal.wardbound.WardHud;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.net.OpenAceChoicePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;

public class HousebreakersAceItem extends Item {
    public HousebreakersAceItem(Properties p) { super(p); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer p && p.getServer() != null) {
            LockData d = LockData.get(p.getServer());
            var id = p.getUUID();
            if (!p.isCreative() && d.uniqueInt(id, "gambler_defeated") == 0) {
                WardHud.send(p, "HOUSEBREAKER'S ACE // This card does not yet recognize your hand.", WardHud.Mood.GLITCH);
                return InteractionResultHolder.fail(stack);
            }
            boolean ward = MasterRewards.hasWardLaw(d, id);
            boolean world = MasterRewards.hasWorldLaw(d, id);
            int signed = MasterRewards.signedCount(d, id);
            if (signed >= 2) {
                WardHud.send(p, "HOUSEBREAKER'S ACE // Both stolen laws are already written into you.", WardHud.Mood.DANGER);
                return InteractionResultHolder.fail(stack);
            }
            Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                    new OpenAceChoicePacket(Math.max(0, 2 - signed), ward, world));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.literal("Right-click to steal one of the House's permanent laws."));
        lines.add(Component.literal("Each law may be signed once. The Ace bears only two signatures."));
        lines.add(Component.literal("These are not next-ward effects."));
    }
}
