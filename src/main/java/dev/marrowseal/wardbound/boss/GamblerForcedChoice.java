package dev.marrowseal.wardbound.boss;

/** Shared IDs/text for the Pale Gambler's two-card hand drawn from a three-card pool. */
public final class GamblerForcedChoice {
    public static final int THIRTY_CUT = 0;
    public static final int COINS_EDGE = 1;
    public static final int BLUNT_HAND = 2;
    public static final int CARD_COUNT = 3;

    private GamblerForcedChoice() {}

    public static String title(int id) {
        return switch (id) {
            case THIRTY_CUT -> "THE THIRTY CUT";
            case COINS_EDGE -> "THE COIN'S EDGE";
            case BLUNT_HAND -> "THE BLUNT HAND";
            default -> "THE UNPRINTED CARD";
        };
    }

    public static String description(int id) {
        return switch (id) {
            case THIRTY_CUT -> "Immediately lose 30% of your MAX HP. Armor and damage reduction cannot soften the cut.";
            case COINS_EDGE -> "A coin is tossed. Heads: lose no HP. Tails: lose half of your MAX HP.";
            case BLUNT_HAND -> "Lose 20% of your MAX HP, then suffer Weakness IV for 20 seconds.";
            default -> "The House has printed something it should not have.";
        };
    }
}
