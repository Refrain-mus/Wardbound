package dev.marrowseal.wardbound.boss;
import dev.marrowseal.wardbound.WardHud;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import java.util.List;
public final class LastMarginItem extends ImportantRelicItem{
 public static final int COOLDOWN=20*70;
 public LastMarginItem(Properties p){super(p);}
 @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){ItemStack s=player.getItemInHand(hand);if(!(player instanceof ServerPlayer sp))return InteractionResultHolder.sidedSuccess(s,level.isClientSide);if(sp.getCooldowns().isOnCooldown(this))return InteractionResultHolder.fail(s);if(!MasterRewardEffects.openMargin(sp)){WardHud.send(sp,"THE LAST MARGIN // No blank space remains.",WardHud.Mood.WARD,1400);return InteractionResultHolder.fail(s);}sp.getCooldowns().addCooldown(this,COOLDOWN);return InteractionResultHolder.success(s);}
 @Override public void appendHoverText(ItemStack s,Level l,List<Component> tip,TooltipFlag f){tip.add(Component.literal("Right-click to open a six-second blank margin."));tip.add(Component.literal("The next wound is mostly archived, then returned as Ash Rebuttal."));}
}
