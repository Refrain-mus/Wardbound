package dev.marrowseal.wardbound;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The mod's own sound library. Every one of these is a short piece of dry foley
 * shipped as an ogg in {@code assets/wardbound/sounds/lock/}.
 *
 * <p>The mod used to borrow from vanilla, which is why every lock sounded like
 * the same experience orb no matter what you were supposedly doing to it, and
 * why beating one played the level-up jingle. Nothing here is borrowed.
 *
 * <p>Volume balance lives in {@link dev.marrowseal.wardbound.client.Sfx}, not in the
 * files: the files are all normalised to the same peak so that relative
 * loudness is a decision made in one place rather than baked into sixteen
 * separate renders.
 */
public final class WardSounds {

    public static final DeferredRegister<SoundEvent> REGISTRY =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Wardbound.MODID);

    // ---- keyway foley -------------------------------------------------
    /** Pick dragging up a pin. Quiet on purpose: it plays constantly. */
    public static final RegistryObject<SoundEvent> PIN_RISE = reg("pin_rise");
    /** The click you are playing for. */
    public static final RegistryObject<SoundEvent> PIN_SET = reg("pin_set");
    /** Released early; the pin falls back down its chamber. */
    public static final RegistryObject<SoundEvent> PIN_DROP = reg("pin_drop");
    /** Pushed past the shear line and the stack bound. */
    public static final RegistryObject<SoundEvent> OVERSET = reg("overset");
    /** The plug giving up one more degree. */
    public static final RegistryObject<SoundEvent> PLUG_TURN = reg("plug_turn");
    /** Tension lost. */
    public static final RegistryObject<SoundEvent> WRENCH_SLIP = reg("wrench_slip");

    // ---- shared across every lock -------------------------------------
    /** A light, high-mid cue when a normal ward/minigame engages. */
    public static final RegistryObject<SoundEvent> WARD_OPEN = reg("ward_open");
    /** Heavy low seal hit, reserved for reseals, gauntlets and major events. */
    public static final RegistryObject<SoundEvent> WARD_SEAL = reg("ward_seal");
    /** The ward breaks and the container opens. */
    public static final RegistryObject<SoundEvent> WARD_BREAK = reg("ward_break");
    /** Out of lives. */
    public static final RegistryObject<SoundEvent> WARD_FAIL = reg("ward_fail");
    /** Generic "that landed", used by the locks that are not the keyway. */
    public static final RegistryObject<SoundEvent> MARK = reg("mark");
    /** Generic "no". */
    public static final RegistryObject<SoundEvent> MARK_BAD = reg("mark_bad");
    /** Glassy three-note maker motif; intentionally distinct from gameplay MARK. */
    public static final RegistryObject<SoundEvent> MASTER_MOTIF = reg("master_motif");
    /** One of the eyes in the header closes. */
    public static final RegistryObject<SoundEvent> LIFE_LOST = reg("life_lost");

    // ---- the vessel and the yoke ---------------------------------------
    /** The vessel's surface being held still. */
    public static final RegistryObject<SoundEvent> SURFACE_STILL = reg("surface_still");
    /** And clouding over again. */
    public static final RegistryObject<SoundEvent> SURFACE_CLOSE = reg("surface_close");
    /** The yoke taking weight. */
    public static final RegistryObject<SoundEvent> YOKE_STRAIN = reg("yoke_strain");

    // ---- eldritch ------------------------------------------------------
    /** The Eye's screen opening. */
    public static final RegistryObject<SoundEvent> EYE_OPEN = reg("eye_open");
    /** Passing over one of its answers. */
    public static final RegistryObject<SoundEvent> EYE_HOVER = reg("eye_hover");
    /** Backing out without giving it one. */
    public static final RegistryObject<SoundEvent> EYE_CLOSE = reg("eye_close");
    /** Giving it one. */
    public static final RegistryObject<SoundEvent> EYE_CHOOSE = reg("eye_choose");
    /** What a warded container sounds like from a few blocks away. */
    public static final RegistryObject<SoundEvent> WARD_HUM = reg("ward_hum");
    /** The same, from a seal nobody signed. */
    public static final RegistryObject<SoundEvent> WARD_HUM_UNSIGNED = reg("ward_hum_unsigned");

    // ---- the rootway --------------------------------------------------
    /** A carved block turning in its socket. */
    public static final RegistryObject<SoundEvent> STONE_TURN = reg("stone_turn");
    /** The root filling one more channel. */
    public static final RegistryObject<SoundEvent> ROOT_GROW = reg("root_grow");
    /** It found no channel, and is going back. */
    public static final RegistryObject<SoundEvent> ROOT_WITHER = reg("root_wither");

    /** A rune-knot being tugged across the braid. */
    public static final RegistryObject<SoundEvent> BRAID_PULL = reg("braid_pull");
    /** The braided seal falling into place. */
    public static final RegistryObject<SoundEvent> BRAID_SETTLE = reg("braid_settle");
    /** A forbidden knot resisting the hand. */
    public static final RegistryObject<SoundEvent> BRAID_REBUKE = reg("braid_rebuke");

    /** A footfall across the hidden causeway. */
    public static final RegistryObject<SoundEvent> VEILSTEP_STEP = reg("veilstep_step");
    /** The causeway acknowledging a true crossing. */
    public static final RegistryObject<SoundEvent> VEILSTEP_CHIME = reg("veilstep_chime");
    /** The veil refusing a false step. */
    public static final RegistryObject<SoundEvent> VEILSTEP_REBUKE = reg("veilstep_rebuke");

    // ---- charms -------------------------------------------------------
    public static final RegistryObject<SoundEvent> KEY_TURN = reg("key_turn");
    public static final RegistryObject<SoundEvent> HEARTBEAT = reg("heartbeat");
    public static final RegistryObject<SoundEvent> HEART_USE = reg("heart_use");
    /** A charm's last use. */
    public static final RegistryObject<SoundEvent> CHARM_SPENT = reg("charm_spent");

    // ---- The Pale Gambler custom bank -------------------------------
    public static final RegistryObject<SoundEvent> GAMBLER_INVITATION_REVEAL = reg("gambler_invitation_reveal");
    public static final RegistryObject<SoundEvent> GAMBLER_TABLE_OPEN = reg("gambler_table_open");
    public static final RegistryObject<SoundEvent> GAMBLER_ACE_OATH = reg("gambler_ace_oath");
    public static final RegistryObject<SoundEvent> GAMBLER_CHOICE_BIND = reg("gambler_choice_bind");
    public static final RegistryObject<SoundEvent> GAMBLER_BOSS_INTRO = reg("gambler_boss_intro");
    public static final RegistryObject<SoundEvent> GAMBLER_BOSS_THEME = reg("gambler_boss_theme");
    public static final RegistryObject<SoundEvent> GAMBLER_PHASE_BREAK = reg("gambler_phase_break");
    public static final RegistryObject<SoundEvent> GAMBLER_ROYAL_CAST = reg("gambler_royal_cast");
    public static final RegistryObject<SoundEvent> GAMBLER_DEATH_COLLAPSE = reg("gambler_death_collapse");
    public static final RegistryObject<SoundEvent> GAMBLER_FAN_CAST = reg("gambler_fan_cast");
    public static final RegistryObject<SoundEvent> GAMBLER_FAN_IMPACT = reg("gambler_fan_impact");
    public static final RegistryObject<SoundEvent> GAMBLER_RING_CAST = reg("gambler_ring_cast");
    public static final RegistryObject<SoundEvent> GAMBLER_RING_IMPACT = reg("gambler_ring_impact");
    public static final RegistryObject<SoundEvent> GAMBLER_CARD_FLIP = reg("gambler_card_flip");
    public static final RegistryObject<SoundEvent> GAMBLER_CARD_SLAM = reg("gambler_card_slam");
    public static final RegistryObject<SoundEvent> GAMBLER_TEETH_RISE = reg("gambler_teeth_rise");
    public static final RegistryObject<SoundEvent> GAMBLER_STARFALL_CAST = reg("gambler_starfall_cast");
    public static final RegistryObject<SoundEvent> GAMBLER_STARFALL_IMPACT = reg("gambler_starfall_impact");
    public static final RegistryObject<SoundEvent> GAMBLER_WALL_RIP = reg("gambler_wall_rip");
    public static final RegistryObject<SoundEvent> GAMBLER_SPIRAL_CAST = reg("gambler_spiral_cast");
    public static final RegistryObject<SoundEvent> GAMBLER_CROSS_CAST = reg("gambler_cross_cast");
    public static final RegistryObject<SoundEvent> GAMBLER_CROSS_IMPACT = reg("gambler_cross_impact");
    public static final RegistryObject<SoundEvent> GAMBLER_CHIP_CAST = reg("gambler_chip_cast");
    public static final RegistryObject<SoundEvent> GAMBLER_CHIP_IMPACT = reg("gambler_chip_impact");
    public static final RegistryObject<SoundEvent> GAMBLER_POT_CAST = reg("gambler_pot_cast");
    public static final RegistryObject<SoundEvent> GAMBLER_POT_IMPACT = reg("gambler_pot_impact");
    public static final RegistryObject<SoundEvent> GAMBLER_HEAVY_HIT_1 = reg("gambler_heavy_hit_1");
    public static final RegistryObject<SoundEvent> GAMBLER_HEAVY_HIT_2 = reg("gambler_heavy_hit_2");
    public static final RegistryObject<SoundEvent> GAMBLER_HEAVY_HIT_3 = reg("gambler_heavy_hit_3");
    public static final RegistryObject<SoundEvent> GAMBLER_SPEECH_1 = reg("gambler_speech_1");
    public static final RegistryObject<SoundEvent> GAMBLER_SPEECH_2 = reg("gambler_speech_2");
    public static final RegistryObject<SoundEvent> GAMBLER_SPEECH_3 = reg("gambler_speech_3");
    public static final RegistryObject<SoundEvent> GAMBLER_AMBIENT_DETONATION = reg("gambler_ambient_detonation");
    public static final RegistryObject<SoundEvent> GAMBLER_VOICE_1 = reg("gambler_voice_1");
    public static final RegistryObject<SoundEvent> GAMBLER_VOICE_2 = reg("gambler_voice_2");
    public static final RegistryObject<SoundEvent> GAMBLER_VOICE_3 = reg("gambler_voice_3");
    public static final RegistryObject<SoundEvent> GAMBLER_HURT_1 = reg("gambler_hurt_1");
    public static final RegistryObject<SoundEvent> GAMBLER_HURT_2 = reg("gambler_hurt_2");
    public static final RegistryObject<SoundEvent> GAMBLER_HURT_3 = reg("gambler_hurt_3");
    public static final RegistryObject<SoundEvent> GAMBLER_AMBIENCE_1 = reg("gambler_ambience_1");
    public static final RegistryObject<SoundEvent> GAMBLER_AMBIENCE_2 = reg("gambler_ambience_2");
    public static final RegistryObject<SoundEvent> GAMBLER_AMBIENCE_3 = reg("gambler_ambience_3");

    // ---- witness grimoire ambience -----------------------------------
    public static final RegistryObject<SoundEvent> LEDGER_LOOT_AMBIENT = reg("ledger_loot_ambient");
    public static final RegistryObject<SoundEvent> LEDGER_CHRONICLE_AMBIENT = reg("ledger_chronicle_ambient");
    public static final RegistryObject<SoundEvent> LEDGER_LAST_WARD_AMBIENT = reg("ledger_last_ward_ambient");
    public static final RegistryObject<SoundEvent> LEDGER_GRIMOIRE_AMBIENT = reg("ledger_grimoire_ambient");
    public static final RegistryObject<SoundEvent> LEDGER_STATUS_AMBIENT = reg("ledger_status_ambient");

    private WardSounds() {
    }

    private static RegistryObject<SoundEvent> reg(String name) {
        return REGISTRY.register(name,
                () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(Wardbound.MODID, name)));
    }
}
