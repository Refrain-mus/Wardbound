package dev.marrowseal.wardbound.boss;
import dev.marrowseal.wardbound.Wardbound;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.List;

@Mod.EventBusSubscriber(modid=Wardbound.MODID)
public final class NotaryVisualCommands {
    public static final List<String> POSES=List.of("idle","manifest","declare","stamp","toll","judgment","unbound","hurt","collapse");
    private static boolean allowed(CommandSourceStack s){return s.hasPermission(2)||(s.getEntity() instanceof ServerPlayer p&&p.isCreative());}
    private static MourningNotaryPreviewEntity nearest(ServerPlayer p){return p.serverLevel().getEntitiesOfClass(MourningNotaryPreviewEntity.class,p.getBoundingBox().inflate(64)).stream().min(java.util.Comparator.comparingDouble(b->b.distanceToSqr(p))).orElse(null);}
    @SubscribeEvent public static void register(RegisterCommandsEvent e){
        var preview=Commands.literal("preview");
        preview.then(Commands.literal("summon").executes(c->{
            ServerPlayer p=c.getSource().getPlayerOrException();var boss=WardBosses.MOURNING_NOTARY_PREVIEW.get().create(p.serverLevel());if(boss==null)return 0;
            Vec3 look=new Vec3(p.getLookAngle().x,0,p.getLookAngle().z);if(look.lengthSqr()<.001)look=new Vec3(0,0,1);
            Vec3 pos=p.position().add(look.normalize().scale(8));boss.moveTo(pos.x,pos.y,pos.z,p.getYRot()+180,0);boss.setYBodyRot(p.getYRot()+180);boss.setYHeadRot(p.getYRot()+180);
            if(!p.serverLevel().addFreshEntity(boss))return 0;
            c.getSource().sendSuccess(()->Component.literal("Mourning Notary art preview. Use a clear 12 x 12 area with 12 blocks overhead. No combat/progression is started."),false);return 1;
        }));
        preview.then(Commands.literal("remove").executes(c->{var boss=nearest(c.getSource().getPlayerOrException());if(boss==null)return 0;boss.discard();return 1;}));
        var poses=Commands.literal("pose");for(String pose:POSES)poses.then(Commands.literal(pose).executes(c->{var boss=nearest(c.getSource().getPlayerOrException());if(boss==null){c.getSource().sendFailure(Component.literal("No Notary preview within 64 blocks."));return 0;}boss.pose(pose);return 1;}));
        preview.then(poses);
        var notary=Commands.literal("notary")
                .then(Commands.literal("diagnose").requires(NotaryVisualCommands::allowed).executes(c->{
                    ServerPlayer p=c.getSource().getPlayerOrException();
                    int combat=p.serverLevel().getEntitiesOfClass(MourningNotaryEntity.class,p.getBoundingBox().inflate(128)).size();
                    int art=p.serverLevel().getEntitiesOfClass(MourningNotaryPreviewEntity.class,p.getBoundingBox().inflate(128)).size();
                    c.getSource().sendSuccess(()->Component.literal("Notary diagnostic // difficulty="+p.level().getDifficulty()+" // dimension="+p.level().dimension().location()+" // combat="+combat+" // preview="+art+" // masterRealmLoaded="+MasterArenaManager.available(p)),false);
                    if(p.level().getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL)c.getSource().sendFailure(Component.literal("Peaceful removes hostile Master combat entities before the encounter can remain visible. Use /difficulty easy or higher."));
                    return 1;
                }))
                .then(preview.requires(NotaryVisualCommands::allowed));
        e.getDispatcher().register(Commands.literal("wardbound").then(notary));
    }
}
