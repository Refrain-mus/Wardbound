package dev.marrowseal.wardbound.boss;
import dev.marrowseal.wardbound.WardHud;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import java.util.List;
public final class UnsignedVerdictItem extends Item{
 @Override public boolean isFoil(ItemStack stack){return true;}
 @Override public void inventoryTick(ItemStack stack,Level level,net.minecraft.world.entity.Entity entity,int slotId,boolean isSelected){ImportantRelicHeldFx.tick(level,entity,stack,isSelected,0.86f,0.26f,0.30f,0.90f,0.74f,0.34f);super.inventoryTick(stack,level,entity,slotId,isSelected);}
 public static final int COOLDOWN=20*80;
 public UnsignedVerdictItem(Properties p){super(p);}
 @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){ItemStack s=player.getItemInHand(hand);if(!(player instanceof ServerPlayer sp))return InteractionResultHolder.sidedSuccess(s,level.isClientSide);if(sp.getCooldowns().isOnCooldown(this))return InteractionResultHolder.fail(s);if(!MasterRewardEffects.fileVerdict(sp)){WardHud.send(sp,"UNSIGNED VERDICT // The court cannot file a sentence against empty air.",WardHud.Mood.WARD,1600);return InteractionResultHolder.fail(s);}sp.getCooldowns().addCooldown(this,COOLDOWN);return InteractionResultHolder.success(s);}
 @Override public void appendHoverText(ItemStack s,Level l,List<Component> tip,TooltipFlag f){tip.add(Component.literal("Right-click a creature in your sight to file an unsigned sentence."));tip.add(Component.literal("The judgment arrives three seconds later."));}
}
