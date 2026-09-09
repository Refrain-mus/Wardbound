package dev.marrowseal.wardbound.ancientsmith;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Explicit operator tools for scene and asset testing. */
@Mod.EventBusSubscriber(modid="wardbound")
public final class SmithPreviewCommands {
    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        var smithCommand = Commands.literal("smith").requires(s -> s.hasPermission(2))
                .then(Commands.literal("status").executes(c -> {
                    var p=c.getSource().getPlayerOrException();
                    c.getSource().sendSuccess(() -> Component.literal(AncientSmithProgression.status(p)), false);
                    return 1;
                }))
                .then(Commands.literal("acquire").executes(c -> {
                    var p=c.getSource().getPlayerOrException();
                    boolean ok=AncientSmithProgression.start(p,true);
                    if(!ok)c.getSource().sendFailure(Component.literal("Acquisition scene could not start."));
                    return ok?1:0;
                }))
                .then(Commands.literal("reset").executes(c -> {
                    var p=c.getSource().getPlayerOrException();
                    AncientSmithProgression.debugReset(p);
                    c.getSource().sendSuccess(() -> Component.literal("Ancient Smith progression reset; existing Nhal-Sûl items were left alone."), false);
                    return 1;
                }))
                .then(Commands.literal("place").executes(c -> {
                    var p=c.getSource().getPlayerOrException();var level=p.serverLevel();
                    var smith=AncientSmithModule.SMITH.get().create(level);var anvil=AncientSmithModule.ANVIL.get().create(level);
                    if(smith==null||anvil==null)return 0;
                    var flat=p.getLookAngle().multiply(1,0,1);
                    if(flat.lengthSqr()<1.0E-6)flat=new net.minecraft.world.phys.Vec3(0,0,1);
                    var where=p.position().add(flat.normalize().scale(12));
                    smith.moveTo(where.x,where.y,where.z,p.getYRot()+180,0);
                    var a=smith.local(0,0,-5.125);anvil.moveTo(a.x,a.y,a.z,smith.getYRot(),0);
                    if(!level.noCollision(smith)||!level.noCollision(anvil)){
                        c.getSource().sendFailure(Component.literal("Clear a 12-block-tall space first."));return 0;
                    }
                    level.addFreshEntity(smith);level.addFreshEntity(anvil);
                    smith.preview(SmithTimeline.State.IDLE_HEAVY, 0);
                    c.getSource().sendSuccess(() -> Component.literal("Ancient Smith and anvil placed in a stable idle pose. No progression changed."),false);return 1;
                }))
                .then(Commands.literal("scene").executes(c -> {
                    var p=c.getSource().getPlayerOrException();
                    var result=AncientSmithHooks.beginNhalSulReforge(p,p.getMainHandItem());
                    c.getSource().sendSuccess(() -> Component.literal(result.name()),false);
                    return result==AncientSmithHooks.Result.STARTED?1:0;
                }))
                .then(Commands.literal("cancel").executes(c -> {AncientSmithHooks.cancel(c.getSource().getPlayerOrException());return 1;}))
                .then(Commands.literal("pose")
                        .then(Commands.argument("clip",StringArgumentType.word())
                                .suggests((c,b)->{for(var s:SmithTimeline.State.values())b.suggest(s.clip);return b.buildFuture();})
                                .executes(c->{
                                    var p=c.getSource().getPlayerOrException();String clip=StringArgumentType.getString(c,"clip");
                                    for(var state:SmithTimeline.State.values())if(state.clip.equals(clip)){
                                        var smith=p.level().getEntitiesOfClass(AncientSmithEntity.class,p.getBoundingBox().inflate(32)).stream().min(java.util.Comparator.comparingDouble(p::distanceToSqr)).orElse(null);
                                        if(smith!=null&&smith.sceneTick()<0){smith.preview(state,100);return 1;}
                                    }
                                    return 0;
                                })));
        event.getDispatcher().register(Commands.literal("wardbound").then(smithCommand));
    }
}
