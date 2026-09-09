package dev.marrowseal.wardbound.boss;

import dev.marrowseal.wardbound.Wardbound;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Operator/creative test entrance. Normal progression enters through Score Beyond the Margin. */
@Mod.EventBusSubscriber(modid= Wardbound.MODID)
public final class MaestroCommands {
    private MaestroCommands(){}
    @SubscribeEvent public static void register(RegisterCommandsEvent event){
        var root=Commands.literal("maestro").requires(s->s.hasPermission(2)||(s.getEntity() instanceof ServerPlayer p&&p.isCreative()));
        root.then(Commands.literal("summon").executes(c->summon(c.getSource().getPlayerOrException(),true)?1:0));
        root.then(Commands.literal("summon_rewarded").executes(c->summon(c.getSource().getPlayerOrException(),false)?1:0));
        root.then(Commands.literal("remove").executes(c->{ServerPlayer p=c.getSource().getPlayerOrException();removeOwned(p);return 1;}));
        root.then(Commands.literal("status").executes(c->{ServerPlayer p=c.getSource().getPlayerOrException();MaestroEntity m=findOwned(p);c.getSource().sendSuccess(()->Component.literal(m==null?"No active Maestro encounter.":"Maestro phase "+m.phase()+" | state "+m.combatState()+" | beat "+m.combatBeat()+" | health "+Math.round(m.wardboundVitality().current())+"/"+Math.round(m.wardboundVitality().maximum())),false);return m==null?0:1;}));
        event.getDispatcher().register(Commands.literal("wardbound").then(root));
    }
    public static boolean summon(ServerPlayer p,boolean practice){
        // Commands remain explicit debug entrances. The diegetic path is ScoreBeyondMarginItem -> MaestroProgression.
        return MaestroProgression.summon(p, practice, true);
    }
    private static MaestroEntity findOwned(ServerPlayer p){
        if(p==null||p.getServer()==null)return null;
        for(ServerLevel level:p.getServer().getAllLevels()){
            AABB box=new AABB(p.blockPosition()).inflate(256,128,256);
            for(MaestroEntity e:level.getEntitiesOfClass(MaestroEntity.class,box,m->!m.isRemoved()&&m.includes(p.getUUID())))return e;
            // Private Master cells are not spatially related to the caller after a command teleport; scan loaded entities too.
            for(var e:level.getAllEntities())if(e instanceof MaestroEntity m&&!m.isRemoved()&&m.includes(p.getUUID()))return m;
        }
        return null;
    }
    private static void removeOwned(ServerPlayer p){MaestroEntity m=findOwned(p);if(m!=null)m.discard();if(MasterArenaManager.active(p))MasterArenaManager.abort(p);}
}
