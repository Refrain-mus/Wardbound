package dev.marrowseal.wardbound.boss;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;


/** A consent relic. The first use opens service; later uses perform the three witness tolls. */
public final class FinalWitnessBellItem extends MasterSummonRelicItem {
    public static final String CONSENT="WardboundNotaryConsent", TOLLS="WardboundNotaryTolls";
    public FinalWitnessBellItem(Properties p){super(p);}

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand){
        ItemStack stack=player.getItemInHand(hand);
        if(player instanceof ServerPlayer p){
            if(!MasterInvocationRelics.canInvoke(p,MasterArenaManager.Kind.NOTARY)) return InteractionResultHolder.fail(stack);
            if(stack.getOrCreateTag().getBoolean(CONSENT)) NotaryProgression.toll(p,stack);
            else NotaryProgression.openConsentPanel(p);
        }
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }


}
