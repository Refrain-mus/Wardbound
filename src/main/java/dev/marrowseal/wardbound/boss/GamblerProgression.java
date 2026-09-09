package dev.marrowseal.wardbound.boss;

import dev.marrowseal.wardbound.*;
import dev.marrowseal.wardbound.item.WardItems;
import dev.marrowseal.wardbound.net.GamblerCinematicPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;

public final class GamblerProgression {
    private GamblerProgression() {}

    private static final String LEASE="gambler_table_lease", MOST="gambler_table_most", LEAST="gambler_table_least";

    public static boolean eligible(LockData d, UUID id) {
        return BossConfig.gambler().enabled()
                && MasterStory.chapter(d,id,CardMaster.PALE_GAMBLER)>=BossConfig.gambler().storyChapter()
                && OccultChainEvents.stage(d,id,OccultChainEvents.ChainEvent.BLACK_TABLE)>=BossConfig.gambler().chainStage()
                && CardEvolution.revisedCount(d,id)>=BossConfig.gambler().revisedCards();
    }

    /** Debug/admin wording only. The physical invitation deliberately does not expose these counters. */
    public static String requirements(LockData d,UUID id) {
        return "Gambler story "+MasterStory.chapter(d,id,CardMaster.PALE_GAMBLER)+"/"+BossConfig.gambler().storyChapter()+" | Black Table "
                +OccultChainEvents.stage(d,id,OccultChainEvents.ChainEvent.BLACK_TABLE)+"/"+BossConfig.gambler().chainStage()+" | revised cards "
                +CardEvolution.revisedCount(d,id)+"/"+BossConfig.gambler().revisedCards();
    }

    public static long now(ServerLevel l) { return l.getServer().overworld().getGameTime(); }

    public static boolean owns(LockData d,UUID owner,UUID boss) {
        return d.uniqueLong(owner,MOST)==boss.getMostSignificantBits() && d.uniqueLong(owner,LEAST)==boss.getLeastSignificantBits();
    }

    public static void claim(LockData d,UUID owner,UUID boss,long now) {
        d.setUniqueLong(owner,MOST,boss.getMostSignificantBits());
        d.setUniqueLong(owner,LEAST,boss.getLeastSignificantBits());
        d.setUniqueLong(owner,LEASE,now+600);
    }

    public static void release(LockData d,UUID owner,UUID boss) {
        if(owns(d,owner,boss)) {
            d.setUniqueLong(owner,LEASE,0);
            d.setUniqueLong(owner,MOST,0);
            d.setUniqueLong(owner,LEAST,0);
        }
    }

    /** The final progression threshold materializes the invitation into the world in front of the player. */
    public static boolean manifestInvitation(ServerPlayer p) {
        if (p == null || p.getServer() == null || !(p.level() instanceof ServerLevel level)) return false;
        ItemStack probe = new ItemStack(WardItems.GAMBLER_INVITATION.get());
        if (p.getInventory().contains(probe)) return true;

        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 0.001) flat = new Vec3(0,0,1);
        flat = flat.normalize();
        Vec3 where = p.position().add(flat.scale(2.15)).add(0, 0.45, 0);
        BlockPos whereBlock = BlockPos.containing(where.x, where.y, where.z);
        if (!level.getBlockState(whereBlock).getCollisionShape(level, whereBlock).isEmpty() || !level.getFluidState(whereBlock).isEmpty())
            where = p.position().add(0, 0.65, 0);
        ItemEntity entity = new ItemEntity(level, where.x, where.y, where.z, probe);
        entity.setPickUpDelay(15);
        entity.setTarget(p.getUUID());
        entity.setInvulnerable(true);
        // This is a progression key, not ordinary loot. Do not let the five-minute item timer or
        // gravity turn "I did not pick it up immediately" into a permanent progression dead-end.
        entity.setUnlimitedLifetime();
        entity.setNoGravity(true);
        entity.setGlowingTag(true);
        entity.setDeltaMovement(Vec3.ZERO);
        if (!level.addFreshEntity(entity)) return false;
        Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                new GamblerCinematicPacket(GamblerCinematicPacket.INVITATION_REVEAL, p.getId(),
                        where.x, where.y, where.z, 2.8f, (int)(level.getGameTime() ^ p.getUUID().getLeastSignificantBits())));
        level.playSound(null, BlockPos.containing(where.x, where.y, where.z), WardSounds.GAMBLER_INVITATION_REVEAL.get(), SoundSource.PLAYERS, .92f, 1.0f);
        WardHud.send(p, "THE LAST TABLE // Something the house withheld has appeared in front of you.", WardHud.Mood.GLITCH);
        WardHud.send(p, "The invitation now bears your name. Pick it up when you are willing to let the last table measure you.", WardHud.Mood.WARD);
        return true;
    }

    /** Kept for commands/recovery; it now uses the same physical manifestation instead of silently entering inventory. */
    public static void giveInvitation(ServerPlayer p) {
        if (!manifestInvitation(p)) {
            WardHud.send(p, "THE LAST TABLE // No second invitation is written while one already answers to you.", WardHud.Mood.WARD);
        }
    }

    /** The fight always uses Wardbound's prepared Master recess; caller coordinates are irrelevant. */
    public static boolean summon(ServerPlayer p,boolean debug) {
        if(!debug && !MasterInvocationRelics.canInvoke(p,MasterArenaManager.Kind.GAMBLER))return false;
        ServerLevel source=p.serverLevel();
        LockData d=LockData.get(source.getServer());
        UUID id=p.getUUID();
        if(!BossConfig.gambler().enabled()) {
            WardHud.send(p,"THE LAST TABLE // The dealer has been forbidden from answering in this world.",WardHud.Mood.WARD);
            return false;
        }
        if(!debug && !BossConfig.gambler().repeatVictories() && d.uniqueInt(id,"gambler_defeated")>0) {
            WardHud.send(p,"THE LAST TABLE // This table has already learned how it loses to you. It will not open twice.",WardHud.Mood.WARD);
            return false;
        }
        if(!debug && !eligible(d,id)) return false;
        if(source.getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL) {
            WardHud.send(p,"THE LAST TABLE // Master encounters require Easy difficulty or higher.",WardHud.Mood.WARD);
            return false;
        }
        if(d.uniqueLong(id,LEASE)>now(source)) {
            WardHud.send(p,"THE LAST TABLE // Your previous table still has a chair pulled out for you.",WardHud.Mood.WARD);
            return false;
        }
        MasterArenaManager.Arena arena=MasterArenaManager.enter(p,MasterArenaManager.Kind.GAMBLER);
        if(arena==null)return false;
        ServerLevel level=arena.level();BlockPos center=arena.center();
        PaleGamblerEntity boss=WardBosses.PALE_GAMBLER.get().create(level);
        if(boss==null){MasterArenaManager.abort(p);return false;}
        boss.moveTo(Vec3.atBottomCenterOf(center));
        boss.begin(p,center,debug);
        if(!level.addFreshEntity(boss)){MasterArenaManager.abort(p);return false;}
        claim(d,id,boss.getUUID(),now(level));
        // The arena is intentionally quiet on arrival. PaleGamblerEntity starts the table-open VFX,
        // music/boss-bar presentation and summon animation only after the viewing grace/approach gate.
        return true;
    }

    public static void victory(ServerPlayer p,boolean debug) {
        if(debug)return;
        MasterArenaManager.beginVictoryLinger(p, MasterArenaManager.Kind.GAMBLER);
        LockData d=LockData.get(p.getServer());
        UUID id=p.getUUID();
        if(d.uniqueInt(id,"gambler_defeated")==0) {
            d.setUniqueInt(id,"gambler_defeated",1);dev.marrowseal.wardbound.champion.SilasMasterEncounter.victory(p,dev.marrowseal.wardbound.champion.SilasMasterEncounter.GAMBLER);
            MasterRewardDelivery.queue(p, MasterArenaManager.Kind.GAMBLER);
            WardHistory.recordSpecial(p,"WARD","The Pale Gambler lost the last table. Housebreaker's Ace and the Dealer's Hand were taken: permanent law in one hand, private violence in the other.");
            WardHud.send(p,"THE HOUSE CAN LOSE // The Ace carries law. The Dealer's Hand carries six attacks that were never part of the public deck.",WardHud.Mood.REWARD);
            MaestroProgression.unlockIfReady(p);
        } else {
            WardHud.send(p,"The house remembers this defeat. No further reward is granted.",WardHud.Mood.WARD);
        }
    }
}
