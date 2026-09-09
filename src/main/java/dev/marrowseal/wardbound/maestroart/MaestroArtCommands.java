package dev.marrowseal.wardbound.maestroart;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="wardbound")
public final class MaestroArtCommands {
    private MaestroArtCommands() {}
    private static boolean allowed(CommandSourceStack source){return source.hasPermission(2)||(source.getEntity() instanceof ServerPlayer p&&p.isCreative());}
    private static MaestroArtEntity nearest(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player=source.getPlayerOrException();
        var entity=player.serverLevel().getEntitiesOfClass(MaestroArtEntity.class,player.getBoundingBox().inflate(64)).stream().min(java.util.Comparator.comparingDouble(e->e.distanceToSqr(player))).orElse(null);
        if(entity==null)source.sendFailure(Component.literal("No Maestro art preview within 64 blocks."));return entity;
    }
    @SubscribeEvent public static void register(RegisterCommandsEvent event){
        var root=Commands.literal("maestro_art").requires(MaestroArtCommands::allowed);
        root.then(Commands.literal("summon").executes(c->{
            ServerPlayer p=c.getSource().getPlayerOrException();MaestroArtEntity e=MaestroArtModule.type().create(p.serverLevel());if(e==null)return 0;
            Vec3 forward=new Vec3(p.getLookAngle().x,0,p.getLookAngle().z);if(forward.lengthSqr()<.001)forward=new Vec3(0,0,1);
            Vec3 at=p.position().add(forward.normalize().scale(5));float yaw=p.getYRot()+180;
            e.moveTo(at.x,at.y,at.z,yaw,0);e.setYBodyRot(yaw);e.setYHeadRot(yaw);
            if(!p.serverLevel().addFreshEntity(e))return 0;
            c.getSource().sendSuccess(()->Component.literal("Maestro art preview. /wardbound maestro_art showcase or play <clip>. Use an open 8 x 8 x 5 area."),false);return 1;
        }));
        root.then(Commands.literal("showcase").executes(c->{var e=nearest(c.getSource());if(e==null)return 0;e.beginShowcase();return 1;}));
        root.then(Commands.literal("remove").executes(c->{var e=nearest(c.getSource());if(e==null)return 0;e.discard();return 1;}));
        var play=Commands.literal("play");
        for(String name:MaestroRig.CLIPS)play.then(Commands.literal(name).executes(c->{var e=nearest(c.getSource());return e!=null&&e.playClip(name)?1:0;}));
        root.then(play);event.getDispatcher().register(Commands.literal("wardbound").then(root));
    }
}
