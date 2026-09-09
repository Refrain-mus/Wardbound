package dev.marrowseal.wardbound.boss;

import dev.marrowseal.wardbound.*;
import dev.marrowseal.wardbound.item.WardItems;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;


/**
 * The Curator is invoked through a codex decision, not by placing a card on the floor.
 * During the encounter the same book remains the seal-breaking tool.
 */
public final class ArchiveWritItem extends MasterSummonRelicItem {
    public ArchiveWritItem(Properties p){super(p);}
    private boolean seal(ServerPlayer p){for(AshenCuratorEntity boss:p.serverLevel().getEntitiesOfClass(AshenCuratorEntity.class,p.getBoundingBox().inflate(24)))if(boss.breakSeal(p))return true;return false;}

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand){
        if(player instanceof ServerPlayer p){if(!seal(p) && MasterInvocationRelics.canInvoke(p,MasterArenaManager.Kind.CURATOR))CuratorProgression.openInvocationPanel(p);}
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);
    }

    @Override public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide && entity instanceof Player player && (isSelected || player.getOffhandItem() == stack) && level.getGameTime() % 5 == 0) {
            double x = player.getX() + (level.random.nextDouble() - 0.5) * 0.45;
            double y = player.getY() + 1.04 + level.random.nextDouble() * 0.35;
            double z = player.getZ() + (level.random.nextDouble() - 0.5) * 0.45;
            level.addParticle(new DustParticleOptions(new Vector3f(0.93f, 0.80f, 0.60f), 0.95f), x, y, z, 0.0, 0.008, 0.0);
            level.addParticle(ParticleTypes.ENCHANT, x, y + 0.03, z, 0.0, 0.01, 0.0);
        }
        super.inventoryTick(stack, level, entity, slotId, isSelected);
    }


    @Override public InteractionResult useOn(UseOnContext c){
        if(c.getPlayer() instanceof ServerPlayer p){if(!seal(p) && MasterInvocationRelics.canInvoke(p,MasterArenaManager.Kind.CURATOR))CuratorProgression.openInvocationPanel(p);}
        return InteractionResult.sidedSuccess(c.getLevel().isClientSide);
    }


}
