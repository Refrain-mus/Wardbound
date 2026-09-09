package dev.marrowseal.wardbound.boss;

/**
 * Bosses whose real native health is mirrored by a small cinematic ledger.
 * AttributeFix makes the native attribute authoritative during ordinary combat;
 * the ledger only protects authored death/collapse sequences from vanilla teardown.
 */
public interface CinematicVitalityHost {
    MasterVitality wardboundVitality();
    boolean wardboundVitalityActive();
    boolean wardboundVitalityLocked();
    void wardboundVitalityChanged();
}
