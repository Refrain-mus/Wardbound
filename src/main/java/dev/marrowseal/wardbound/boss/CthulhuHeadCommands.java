package dev.marrowseal.wardbound.boss;

import dev.marrowseal.wardbound.Wardbound;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=Wardbound.MODID)
public final class CthulhuHeadCommands {
    private static boolean allowed(CommandSourceStack s){return s.hasPermission(2)||(s.getEntity() instanceof ServerPlayer p&&p.isCreative());}
    private static CthulhuHeadEntity nearest(ServerPlayer p){return p.serverLevel().getEntitiesOfClass(CthulhuHeadEntity.class,p.getBoundingBox().inflate(128)).stream().min(java.util.Comparator.comparingDouble(e->e.distanceToSqr(p))).orElse(null);}
    private static CthulhuHeadEntity find(CommandSourceStack s) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var e=nearest(s.getPlayerOrException());if(e==null)s.sendFailure(Component.literal("No Cthulhu head within 128 blocks."));return e;
    }
    @SubscribeEvent public static void register(RegisterCommandsEvent event){
        var root=Commands.literal("cthulhu_head").requires(CthulhuHeadCommands::allowed);
        root.then(Commands.literal("fight").executes(c->{ServerPlayer p=c.getSource().getPlayerOrException();return CthulhuHeadArena.start(p,true)?1:0;}));
        root.then(Commands.literal("arena_abort").executes(c->{ServerPlayer p=c.getSource().getPlayerOrException();CthulhuHeadArena.abort(p);return 1;}));
        root.then(Commands.literal("summon").executes(c->{
            ServerPlayer p=c.getSource().getPlayerOrException();var e=WardBosses.CTHULHU_HEAD.get().create(p.serverLevel());if(e==null)return 0;
            Vec3 direction=new Vec3(p.getLookAngle().x,0,p.getLookAngle().z);if(direction.lengthSqr()<.001)direction=new Vec3(0,0,1);
            Vec3 at=p.position().add(direction.normalize().scale(32));
            e.moveTo(at.x,at.y,at.z,p.getYRot()+180,0);e.setYBodyRot(p.getYRot()+180);e.setYHeadRot(p.getYRot()+180);
            if(!p.serverLevel().addFreshEntity(e))return 0;
            c.getSource().sendSuccess(()->Component.literal("Cthulhu head: 20-block visual rig. Use /wardbound cthulhu_head play <clip>. Clear 64 x 64 x 40 blocks for animation inspection."),false);return 1;
        }));
        var play=Commands.literal("play");
        for(String clip:CthulhuHeadEntity.CLIPS)play.then(Commands.literal(clip).executes(c->{var e=find(c.getSource());if(e==null)return 0;e.playClip(clip);return 1;}));
        root.then(play);
        root.then(Commands.literal("showcase").executes(c->{var e=find(c.getSource());if(e==null)return 0;e.beginShowcase();return 1;}));
        root.then(Commands.literal("remove").executes(c->{var e=find(c.getSource());if(e==null)return 0;e.discard();return 1;}));
        root.then(Commands.literal("gaze_auto").executes(c->{var e=find(c.getSource());if(e==null)return 0;e.automaticGaze();return 1;}));
        root.then(Commands.literal("gaze").then(Commands.argument("left",EntityArgument.entity()).then(Commands.argument("right",EntityArgument.entity()).executes(c->{
            var e=find(c.getSource());if(e==null)return 0;
            var left=EntityArgument.getEntity(c,"left");var right=EntityArgument.getEntity(c,"right");
            if(!(left instanceof LivingEntity l)||!(right instanceof LivingEntity r)){c.getSource().sendFailure(Component.literal("Eye targets must be living entities."));return 0;}
            e.setEyeTargets(l,r);return 1;
        }))));
        event.getDispatcher().register(Commands.literal("wardbound").then(root));
    }
}
