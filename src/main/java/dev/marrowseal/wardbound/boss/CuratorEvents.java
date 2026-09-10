package dev.marrowseal.wardbound.boss;
import dev.marrowseal.wardbound.*;
import dev.marrowseal.wardbound.item.WardItems;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraftforge.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=Wardbound.MODID)
public final class CuratorEvents {
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){
        if(e.phase!=TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount%100!=0)return;
        var d=LockData.get(p.getServer());var id=p.getUUID();
        if(BossConfig.curator().enabled() && CuratorProgression.evidence(d,id) && d.uniqueInt(id,"curator_defeated")==0 && d.uniqueInt(id,"curator_writ_issued")==0){if(p.getInventory().contains(new ItemStack(WardItems.ARCHIVE_WRIT.get())))d.setUniqueInt(id,"curator_writ_issued",1);else{CuratorProgression.giveWrit(p);if(d.uniqueInt(id,"curator_writ_issued")!=0)CuratorProgression.message(p,"A returned record has bound itself into the Ashen Archive Codex. Open it before you decide what deserves to survive.");}}
    }
    private static boolean admin(net.minecraft.commands.CommandSourceStack s){return s.hasPermission(2)||(s.getEntity() instanceof ServerPlayer p && p.isCreative());}
    @SubscribeEvent public static void commands(RegisterCommandsEvent e){
        var curator = Commands.literal("curator")
            .then(Commands.literal("status").executes(c->{CuratorProgression.message(c.getSource().getPlayerOrException(),CuratorProgression.status(c.getSource().getPlayerOrException()));return 1;}))
            .then(Commands.literal("reload").requires(CuratorEvents::admin).executes(c->{WardConfig.load();c.getSource().sendSuccess(()->Component.literal("Wardbound reloaded. Curator combat/music settings apply to new encounters."),true);return 1;}))
            .then(Commands.literal("debug").requires(CuratorEvents::admin)
                .then(Commands.literal("summon").executes(c->{ServerPlayer p=c.getSource().getPlayerOrException();CuratorProgression.giveWrit(p);return CuratorProgression.summon(p,true)?1:0;}))
                .then(Commands.literal("kit").executes(c->{ServerPlayer p=c.getSource().getPlayerOrException();CuratorProgression.giveWrit(p);for(Item item:new Item[]{Items.NETHERITE_SWORD,Items.BOW,Items.NETHERITE_HELMET,Items.NETHERITE_CHESTPLATE,Items.NETHERITE_LEGGINGS,Items.NETHERITE_BOOTS}){ItemStack s=new ItemStack(item);if(!p.getInventory().add(s))p.drop(s,false);}for(Item item:new Item[]{Items.ARROW,Items.GOLDEN_CARROT}){ItemStack s=new ItemStack(item,64);if(!p.getInventory().add(s))p.drop(s,false);}return 1;})));
        e.getDispatcher().register(Commands.literal("wardbound").then(curator));
    }
}
