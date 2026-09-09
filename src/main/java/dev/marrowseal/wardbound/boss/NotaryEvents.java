package dev.marrowseal.wardbound.boss;

import dev.marrowseal.wardbound.*;
import dev.marrowseal.wardbound.item.WardItems;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=Wardbound.MODID)
public final class NotaryEvents {
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!(e.player instanceof ServerPlayer p)||p.tickCount%100!=0||p.getServer()==null)return;
        var d=LockData.get(p.getServer());var id=p.getUUID();
        if(BossConfig.notary().enabled()&&NotaryProgression.evidence(d,id)&&d.uniqueInt(id,"notary_defeated")==0&&!p.getInventory().contains(new ItemStack(WardItems.FINAL_WITNESS_BELL.get()))){
            NotaryProgression.giveBell(p);WardHud.send(p,"FINAL WITNESS // A bell without a tongue has appeared among your things. It bears a notice addressed to you.",WardHud.Mood.GLITCH);
        }
    }
    private static boolean admin(net.minecraft.commands.CommandSourceStack s){return s.hasPermission(2)||(s.getEntity() instanceof ServerPlayer p&&p.isCreative());}
    @SubscribeEvent public static void commands(RegisterCommandsEvent e){
        var notary = Commands.literal("notary")
            .then(Commands.literal("status").executes(c->{ServerPlayer p=c.getSource().getPlayerOrException();c.getSource().sendSuccess(()->Component.literal(NotaryProgression.status(p)),false);return 1;}))
            .then(Commands.literal("reload").requires(NotaryEvents::admin).executes(c->{WardConfig.load();return 1;}))
            .then(Commands.literal("debug").requires(NotaryEvents::admin)
                .then(Commands.literal("bell").executes(c->{NotaryProgression.giveBell(c.getSource().getPlayerOrException());return 1;}))
                .then(Commands.literal("summon").executes(c->{ServerPlayer p=c.getSource().getPlayerOrException();return NotaryProgression.summon(p,true)?1:0;})));
        e.getDispatcher().register(Commands.literal("wardbound").then(notary));
    }
}
