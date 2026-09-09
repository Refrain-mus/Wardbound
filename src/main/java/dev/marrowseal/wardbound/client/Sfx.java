package dev.marrowseal.wardbound.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.RegistryObject;
import dev.marrowseal.wardbound.WardSounds;
import dev.marrowseal.wardbound.WardConfig;
import dev.marrowseal.wardbound.Sealmakers;
import dev.marrowseal.wardbound.MasterSignature;

/**
 * Every sound a lock screen makes goes through here.
 *
 * <p>Two reasons it is not just a call to the sound manager.
 *
 * <p><b>Category.</b> Vanilla's {@code SimpleSoundInstance.forUI} routes through
 * {@code SoundSource.MASTER}, which means a mod that clicks this often would be
 * untunable short of turning the whole game down. These play on
 * {@code SoundSource.BLOCKS} instead, so the Blocks slider works on them.
 *
 * <p><b>Balance in one place.</b> The ogg files are all normalised to the same
 * peak, so how loud a pin click is relative to a lock breaking is decided here
 * and nowhere else. Nudging the mix is editing this file, not re-rendering audio.
 */
@OnlyIn(Dist.CLIENT)
public final class Sfx {

    /**
     * A constant pitch offset for the lock currently open, derived from the
     * house that made it.
     *
     * <p>Two seals by the same hand sound the same and two by different hands do
     * not. The mod already names its makers and already counts how many of each
     * one you have broken; this is the piece that lets you start recognising a
     * maker before you have read anything, which is what "you know this hand"
     * ought to mean. The range is narrow on purpose - a semitone or so either
     * way, enough to be a character and not enough to be a gimmick.
     */
    private static float houseTone = 1f;

    public static void setHouseTone(long seed) {
        long h = seed * 0x9E3779B97F4A7C15L;
        h ^= (h >>> 31);
        houseTone = 0.93f + Math.floorMod(h, 15) * 0.010f;
    }

    private Sfx() {
    }

    public static void play(RegistryObject<SoundEvent> sound, float volume, float pitch) {
        SoundEvent event = sound.get();
        float finalVolume = Math.max(0f, volume * WardConfig.accessibilitySfxVolume);
        if (finalVolume <= 0.0001f) return;
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(
                event.getLocation(), SoundSource.BLOCKS,
                finalVolume, pitch * houseTone,
                SoundInstance.createUnseededRandom(),
                false, 0, SoundInstance.Attenuation.NONE,
                0.0D, 0.0D, 0.0D, true));
    }

    // ------------------------------------------------------------------ shared events

    /** A lock opens. Pitch drifts up with the run so a long streak sounds like one. */
    public static void wardBreak(int streak) {
        play(WardSounds.WARD_BREAK, 0.85f, Math.min(1.35f, 0.98f + streak * 0.035f));
    }

    public static void wardFail() {
        play(WardSounds.WARD_FAIL, 0.90f, 1.0f);
    }

    public static void lifeLost() {
        play(WardSounds.LIFE_LOST, 0.70f, 0.96f + rand(0.08f));
    }

    /**
     * One note of a house's three-note calling card. The contour is derived
     * from the house name, so the same maker is recognisable across every lock
     * without shipping dozens of near-identical sound files.
     */
    public static void masterMotifNote(long seed, int note) {
        if (!WardConfig.masterAudioMotifsEnabled || WardConfig.masterAudioMotifVolume <= 0f) return;
        String house = Sealmakers.house(seed);
        int h = house.hashCode();
        int[][] contours = {
                {0, 3, 1}, {0, -2, 4}, {0, 5, 2}, {0, 1, -3},
                {0, 4, 7}, {0, -3, -1}, {0, 2, 6}, {0, -1, 3}
        };
        int[] contour = contours[Math.floorMod(h, contours.length)];
        int n = Math.max(0, Math.min(2, note));
        int baseStep = Math.floorMod(h >>> 4, 5) - 2;
        float semitones = baseStep + contour[n];
        float pitch = (float) Math.pow(2.0, semitones / 12.0);
        play(WardSounds.MASTER_MOTIF, WardConfig.masterAudioMotifVolume, 0.86f * pitch);
    }

    /** Hover voice for bargain cards, coloured by the hand that wrote them. */
    public static void bargainHover(long makerSeed, boolean refuse) {
        if (refuse || makerSeed == 0L) {
            play(WardSounds.EYE_HOVER, 0.28f, 0.88f);
            return;
        }
        MasterSignature sig = MasterSignature.ofSeed(makerSeed);
        float p = switch (sig) {
            case CROOKED -> 0.72f;
            case VEILED -> 0.82f;
            case EXACTING -> 0.96f;
        };
        play(WardSounds.EYE_HOVER, 0.30f, p);
    }

    /**
     * The generic "correct" blip. {@code step} is how far into the lock you are,
     * and it walks the pitch up a little each time so a sequence of correct
     * inputs rises instead of repeating one note twelve times.
     */
    public static void mark(int step) {
        play(WardSounds.MARK, 0.55f, Math.min(1.6f, 0.94f + step * 0.055f));
    }

    public static void markBad() {
        play(WardSounds.MARK_BAD, 0.60f, 0.97f + rand(0.06f));
    }

    // ------------------------------------------------------------------ keyway foley

    public static void pinRise() {
        play(WardSounds.PIN_RISE, 0.34f, 0.92f + rand(0.16f));
    }

    public static void pinSet(int index) {
        play(WardSounds.PIN_SET, 0.72f, Math.min(1.5f, 0.95f + index * 0.05f));
    }

    public static void pinDrop() {
        play(WardSounds.PIN_DROP, 0.45f, 0.90f + rand(0.18f));
    }

    public static void overset() {
        play(WardSounds.OVERSET, 0.75f, 0.94f + rand(0.10f));
    }

    public static void plugTurn(float pitch) {
        play(WardSounds.PLUG_TURN, 0.60f, pitch);
    }

    public static void wrenchSlip() {
        play(WardSounds.WRENCH_SLIP, 0.80f, 0.95f + rand(0.08f));
    }

    /**
     * Small random detune. Foley that repeats at an identical pitch stops
     * sounding like a physical object after about the fourth time you hear it.
     */
    private static float rand(float spread) {
        return (float) (Math.random() * spread) - spread * 0.5f;
    }
}
