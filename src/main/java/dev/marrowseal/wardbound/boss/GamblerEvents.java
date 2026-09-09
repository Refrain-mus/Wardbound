package dev.marrowseal.wardbound.boss;
import dev.marrowseal.wardbound.*;
import dev.marrowseal.wardbound.item.WardItems;
import dev.marrowseal.wardbound.net.GamblerCinematicPacket;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid=Wardbound.MODID)
public final class GamblerEvents {
    private static final String CHOICE_PENDING="gambler_forced_choice_pending";
    private static final String CHOICE_DEADLINE="gambler_forced_choice_deadline";

    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e) {
        if(e.phase!=TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.getServer()==null)return;
        LockData d=LockData.get(p.getServer());var id=p.getUUID();

        // A forced hand survives screen replacement, dimension travel and disconnects.
        // If its server deadline has passed, reconnecting merely lets the house collect.
        if(p.tickCount%20==0){
            int token=d.uniqueInt(id,CHOICE_PENDING);
            long deadline=d.uniqueLong(id,CHOICE_DEADLINE);
            if(token!=0 && deadline!=Long.MIN_VALUE && p.level().getGameTime()>=deadline)forceChoiceForfeit(p,token);
        }

        if(p.tickCount%100!=0)return;
        if(d.uniqueInt(id,"gambler_invitation_issued")==0 && GamblerProgression.eligible(d,id)) {
            if (GamblerProgression.manifestInvitation(p)) d.setUniqueInt(id,"gambler_invitation_issued",1);
        }
    }

    /** Atomically consumes one matching forced-choice claim. */
    static boolean clearChoiceClaim(ServerPlayer p,int token){
        return p!=null && clearChoiceClaim(p.getServer(),p.getUUID(),token);
    }

    /** Server-side variant used when an encounter must clean up claims for offline participants too. */
    static boolean clearChoiceClaim(MinecraftServer server,UUID player,int token){
        if(server==null || player==null || token==0)return false;
        LockData d=LockData.get(server);
        if(d.uniqueInt(player,CHOICE_PENDING)!=token)return false;
        d.setUniqueInt(player,CHOICE_PENDING,0);
        d.setUniqueLong(player,CHOICE_DEADLINE,Long.MIN_VALUE);
        return true;
    }

    /** Clears any stale Gambler choice claim after the owning encounter has been reset or removed. */
    static boolean clearAnyChoiceClaim(MinecraftServer server,UUID player){
        if(server==null || player==null)return false;
        LockData d=LockData.get(server);
        int token=d.uniqueInt(player,CHOICE_PENDING);
        long deadline=d.uniqueLong(player,CHOICE_DEADLINE);
        if(token==0 && deadline==Long.MIN_VALUE)return false;
        d.setUniqueInt(player,CHOICE_PENDING,0);
        d.setUniqueLong(player,CHOICE_DEADLINE,Long.MIN_VALUE);
        return true;
    }

    /** Timeout/disconnect result: the house takes the bad side of the coin. */
    static boolean forceChoiceForfeit(ServerPlayer p,int token){
        if(!clearChoiceClaim(p,token))return false;
        float after=p.getHealth()-Math.max(1f,p.getMaxHealth()*.50f);
        p.invulnerableTime=0;
        if(after<=0f)p.kill(); else p.setHealth(after);
        WardHud.message(p,Component.literal("THE PALE GAMBLER // Refusal is a choice. The house takes half.")
                .withStyle(net.minecraft.ChatFormatting.DARK_RED,net.minecraft.ChatFormatting.BOLD),false);
        p.level().playSound(null,p.blockPosition(),WardSounds.GAMBLER_HEAVY_HIT_3.get(),SoundSource.HOSTILE,.78f,.74f);
        Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                new GamblerCinematicPacket(GamblerCinematicPacket.PLAYER_STAGGER,p.getId(),
                        p.getX(),p.getY(),p.getZ(),1.15f,token ^ p.getId()));
        return true;
    }

    private static boolean debugAllowed(net.minecraft.commands.CommandSourceStack s) {
        return s.hasPermission(2) || (s.getEntity() instanceof ServerPlayer p && p.isCreative());
    }

    private static int giveDebug(ServerPlayer p, net.minecraft.world.item.Item item) {
        ItemStack stack = new ItemStack(item);
        if (!p.getInventory().add(stack)) p.drop(stack, false);
        return 1;
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent e) {
        var gambler = Commands.literal("gambler")
            .then(Commands.literal("reload").requires(GamblerEvents::debugAllowed).executes(c -> {
                WardConfig.load();
                c.getSource().sendSuccess(() -> Component.literal("Wardbound config reloaded. Combat changes apply to new tables."), true);
                return 1;
            }))
            .then(Commands.literal("status").executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                p.sendSystemMessage(Component.literal(GamblerProgression.requirements(LockData.get(p.getServer()), p.getUUID())));
                return 1;
            }))
            .then(Commands.literal("invitation").executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                if (!GamblerProgression.eligible(LockData.get(p.getServer()), p.getUUID())) {
                    p.sendSystemMessage(Component.literal("Invitation not ready: " + GamblerProgression.requirements(LockData.get(p.getServer()), p.getUUID())));
                    return 0;
                }
                return GamblerProgression.manifestInvitation(p) ? 1 : 0;
            }))
            .then(Commands.literal("reclaim").executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                LockData d = LockData.get(p.getServer());
                if (d.uniqueInt(p.getUUID(), "gambler_defeated") == 0 || MasterRewards.signedCount(d, p.getUUID()) >= 2) return 0;
                ItemStack stack = new ItemStack(WardItems.HOUSEBREAKERS_ACE.get());
                if (!p.getInventory().contains(stack) && !p.getInventory().add(stack)) p.drop(stack, false);
                return 1;
            }))
            // Backwards-compatible alias: this is explicitly a debug summon and
            // does not require Gambler story / chain / revision progression.
            .then(Commands.literal("summon").requires(GamblerEvents::debugAllowed).executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                return GamblerProgression.summon(p, true) ? 1 : 0;
            }))
            .then(Commands.literal("debug").requires(GamblerEvents::debugAllowed)
                .then(Commands.literal("summon").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    return GamblerProgression.summon(p, true) ? 1 : 0;
                }))
                .then(Commands.literal("invitation").executes(c ->
                        giveDebug(c.getSource().getPlayerOrException(), WardItems.GAMBLER_INVITATION.get())))
                .then(Commands.literal("ace").executes(c ->
                        giveDebug(c.getSource().getPlayerOrException(), WardItems.HOUSEBREAKERS_ACE.get())))
                .then(Commands.literal("kit").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    giveDebug(p, WardItems.GAMBLER_INVITATION.get());
                    giveDebug(p, WardItems.HOUSEBREAKERS_ACE.get());
                    p.sendSystemMessage(Component.literal("Gambler debug kit given. Creative Invitation/Ace ignore progression gating."));
                    return 1;
                }))
                .then(Commands.literal("clear_ace").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    LockData d = LockData.get(p.getServer());
                    d.setUniqueInt(p.getUUID(), "house_ace_queued", 0);
                    d.setUnique(p.getUUID(), MasterRewards.ACE_WARD_LAW, false);
                    d.setUnique(p.getUUID(), MasterRewards.ACE_WORLD_LAW, false);
                    d.setUniqueInt(p.getUUID(), "house_ace_uses", 0);
                    p.sendSystemMessage(Component.literal("Ace debug state cleared: both permanent laws and legacy queued clauses reset."));
                    return 1;
                }))
            );
        e.getDispatcher().register(Commands.literal("wardbound").then(gambler));
    }
}
