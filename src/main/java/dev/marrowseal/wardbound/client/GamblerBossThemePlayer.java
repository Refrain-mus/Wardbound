package dev.marrowseal.wardbound.client;

import dev.marrowseal.wardbound.WardSounds;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.boss.BossConfig;
import dev.marrowseal.wardbound.boss.PaleGamblerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Dedicated soundtrack controller for The Pale Gambler.
 *
 * <p>Users can swap the actual music by replacing
 * assets/wardbound/sounds/gambler/boss_theme.ogg in source, or by overriding
 * that exact resource path from a resource pack. The playback logic lives here:
 * start when a living Pale Gambler is active nearby, loop for the duration of
 * the encounter, then ease out on death / despawn / disengage.
 */
@Mod.EventBusSubscriber(modid = Wardbound.MODID, value = Dist.CLIENT)
public final class GamblerBossThemePlayer {
    private static GamblerThemeSound active;

    private GamblerBossThemePlayer() {}

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (active != null) active.beginStop();
            return;
        }

        PaleGamblerEntity boss = nearestBoss(mc);
        if (boss != null && BossConfig.gambler().themeEnabled()) {
            if (active == null || active.isFinished() || active.bossId() != boss.getId()) {
                if (active != null) active.beginStop();
                active = new GamblerThemeSound(boss);
                mc.getSoundManager().play(active);
            } else {
                active.bind(boss);
            }
        } else if (active != null) {
            active.beginStop();
            if (active.isFinished()) active = null;
        }

        // A streamed sound can be rejected/stopped by the engine before its fade-in
        // becomes audible. canStartSilent() permits a zero-volume start; this guard
        // also recreates the instance if the sound engine unexpectedly drops it.
        if (active != null && !active.isFinished() && !mc.isPaused() && ++active.observedTicks > 20
                && !mc.getSoundManager().isActive(active)) {
            PaleGamblerEntity current = nearestBoss(mc);
            mc.getSoundManager().stop(active);
            active = null;
            if (current != null && BossConfig.gambler().themeEnabled()) {
                active = new GamblerThemeSound(current);
                mc.getSoundManager().play(active);
            }
        }

        if (active != null && active.isFinished()) active = null;
    }

    private static PaleGamblerEntity nearestBoss(Minecraft mc) {
        PaleGamblerEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof PaleGamblerEntity boss) || !boss.isAlive() || boss.isRemoved() || boss.deathSequence() || !boss.introStarted()) continue;
            double dist = mc.player.distanceToSqr(boss);
            if (dist <= 1600.0 && dist < bestDist) {
                best = boss;
                bestDist = dist;
            }
        }
        return best;
    }

    private static final class GamblerThemeSound extends AbstractTickableSoundInstance {
        private PaleGamblerEntity boss;
        private final int bossId;
        private boolean stopping;
        private boolean finished;
        private float gain;
        private int observedTicks;

        private GamblerThemeSound(PaleGamblerEntity boss) {
            super(WardSounds.GAMBLER_BOSS_THEME.get(), SoundSource.MUSIC, RandomSource.create());
            this.boss = boss;
            this.bossId = boss.getId();
            this.looping = true;
            this.delay = 0;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.volume = 0.0f;
            this.pitch = 1.0f;
        }

        int bossId() { return bossId; }
        boolean isFinished() { return finished; }
        void bind(PaleGamblerEntity boss) { this.boss = boss; this.stopping = false; }
        void beginStop() { this.stopping = true; }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            var settings = BossConfig.gambler();
            if (!settings.themeEnabled()) stopping = true;
            if (boss == null || boss.isRemoved() || !boss.isAlive() || boss.deathSequence()) stopping = true;

            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null || boss == null || boss.level() != mc.level || mc.player.distanceToSqr(boss) > 1936.0) {
                stopping = true;
            }

            float inStep = 1.0f / Math.max(1, settings.themeFadeInTicks());
            float outStep = 1.0f / Math.max(1, settings.themeFadeOutTicks());
            gain = stopping ? Math.max(0.0f, gain - outStep) : Math.min(1.0f, gain + inStep);
            volume = Mth.clamp((float) settings.themeVolume(), 0.0f, 2.0f) * gain;

            if (gain <= 0.0001f && stopping) {
                finished = true;
                stop();
            }
        }
    }
}
