package dev.marrowseal.wardbound.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import dev.marrowseal.wardbound.WardConfig;
import dev.marrowseal.wardbound.MinigameType;
import dev.marrowseal.wardbound.DimSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The screen behind "Config" next to Wardbound in the Mods list.
 *
 * <p>Rebuilt around a table rather than around widgets. The old version named
 * every field by hand, which was fine at fourteen settings and became the reason
 * the screen fell thirty-five settings behind the file: adding one meant a
 * variable, a constructor line, a read, a write and a caption, in five places,
 * and so it simply did not get done. A setting is now one row in {@link #pages},
 * and everything else follows from it.
 *
 * <p>Auto-generated config screens - Configured and the like - read
 * {@code ForgeConfigSpec}, and this mod's config is a hand written JSON file
 * with paragraphs of explanation in it. Migrating would hand over the screen and
 * throw away the annotated file, which is the part people actually edit, so the
 * screen is ours and the file stays readable.
 */
@OnlyIn(Dist.CLIENT)
public class WardConfigScreen extends Screen {

    private static final int COL_BG = 0xF21C1611;
    private static final int COL_PANEL_EDGE = 0xFF0D0906;
    private static final int COL_HEADER = 0xFF2A2018;
    private static final int COL_BRASS = 0xFF7A5C2A;
    private static final int COL_BRASS_LIT = 0xFFC9A052;
    private static final int COL_TEXT = 0xFFEADCBC;
    private static final int COL_NOTE = 0xFF8B7A5E;
    private static final int COL_FAINT = 0xFF6A5B45;
    private static final int COL_BAD = 0xFFC2553F;

    private static final int MAX_PANEL_W = 760;
    private static final int MAX_PANEL_H = 430;
    private static final int NORMAL_ROWS_PER_CHUNK = 10;
    private static final int SINGLE_ROWS_PER_CHUNK = 6;

    private enum Kind { INT, FLOAT, PERCENT, BOOL, TEXT }

    /** One setting: what it is called, what it does, and how to read and write it. */
    private static final class Row {
        final String label;
        final String note;
        final Kind kind;
        final IntSupplier readInt;
        final DoubleSupplier readFloat;
        final BooleanSupplier readBool;
        final Supplier<String> readText;
        final Consumer<String> writeNumber;
        final Consumer<Boolean> writeBool;
        final Consumer<String> writeText;

        EditBox box;
        Button toggle;
        boolean value;

        private Row(String label, String note, Kind kind, IntSupplier ri, DoubleSupplier rf,
                    BooleanSupplier rb, Supplier<String> rt, Consumer<String> wn,
                    Consumer<Boolean> wb, Consumer<String> wt) {
            this.label = label;
            this.note = note;
            this.kind = kind;
            this.readInt = ri;
            this.readFloat = rf;
            this.readBool = rb;
            this.readText = rt;
            this.writeNumber = wn;
            this.writeBool = wb;
            this.writeText = wt;
        }

        static Row ofInt(String label, String note, IntSupplier get, Consumer<String> set) {
            return new Row(label, note, Kind.INT, get, null, null, null, set, null, null);
        }

        static Row ofFloat(String label, String note, DoubleSupplier get, Consumer<String> set) {
            return new Row(label, note, Kind.FLOAT, null, get, null, null, set, null, null);
        }

        static Row ofPercent(String label, String note, DoubleSupplier get, Consumer<String> set) {
            String help = note == null || note.isBlank() ? "0 - 100%" : note.replace("0 - 1", "0 - 100%");
            if (!help.contains("%")) help += " · enter %";
            return new Row(label, help, Kind.PERCENT, null, get, null, null, set, null, null);
        }

        static Row ofBool(String label, String note, BooleanSupplier get, Consumer<Boolean> set) {
            return new Row(label, note, Kind.BOOL, null, null, get, null, null, set, null);
        }

        static Row ofText(String label, String note, Supplier<String> get, Consumer<String> set) {
            return new Row(label, note, Kind.TEXT, null, null, null, get, null, null, set);
        }

        String initial() {
            return switch (kind) {
                case INT -> String.valueOf(readInt.getAsInt());
                case FLOAT -> trim(readFloat.getAsDouble());
                case PERCENT -> trim(readFloat.getAsDouble() * 100.0);
                case BOOL -> "";
                case TEXT -> readText.get();
            };
        }

        private static String trim(double d) {
            String s = String.format(Locale.ROOT, "%.3f", d);
            while (s.contains(".") && (s.endsWith("0") || s.endsWith("."))) {
                s = s.substring(0, s.length() - 1);
            }
            return s;
        }
    }

    private record Page(String title, List<Row> rows) {
    }

    private final List<Page> pages = new ArrayList<>();
    private final Screen parent;
    private int page;
    private int settingsChunk;
    private String problem = "";
    private int tuningIndex = MinigameType.CONSTELLATION.ordinal();
    private int dimensionIndex = 0;

    public WardConfigScreen(Screen parent) {
        super(Component.literal("Wardbound"));
        this.parent = parent;
        ACTIVE = this;
        buildPages();
    }

    /**
     * Common settings from the file, grouped the way the file groups them. Per-minigame
     * tuning used to stay in wardbound.json only; the screen now exposes it through a single
     * rotating tuning page so the Mods menu can reach the whole gameplay surface without
     * turning the panel into nineteen separate tab pages.
     *
     * <p>Deliberately verbose rather than reflective. Reflection over the config
     * class would be shorter and would also silently pick up anything added to
     * it, including the things that have no business on a screen, and would lose
     * the one line of explanation that is the only reason most of these numbers
     * mean anything.
     */
    private void buildPages() {
        pages.add(new Page("Ward frequency", List.of(
                Row.ofInt("Lock threshold", "below this uses the low chance",
                        () -> WardConfig.lockThreshold, s -> WardConfig.lockThreshold = pInt(s, WardConfig.lockThreshold)),
                Row.ofPercent("Ward chance, poor", "0 - 1",
                        () -> WardConfig.lockChanceLow, s -> WardConfig.lockChanceLow = pFloat(s, WardConfig.lockChanceLow)),
                Row.ofPercent("Ward chance, rich", "0 - 1",
                        () -> WardConfig.lockChanceHigh, s -> WardConfig.lockChanceHigh = pFloat(s, WardConfig.lockChanceHigh)),
                Row.ofInt("Min value to ward", "junk chests below this stay open",
                        () -> WardConfig.minValueToLock, s -> WardConfig.minValueToLock = pInt(s, WardConfig.minValueToLock)),
                Row.ofPercent("Loot kept on a loss", "40% means 60% of loot value is lost",
                        () -> WardConfig.failLootMultiplier, s -> WardConfig.failLootMultiplier = pFloat(s, WardConfig.failLootMultiplier)),
                Row.ofFloat("Global ward roll", "multiplies poor/rich ward chance; 1 = unchanged, 0 = disabled",
                        () -> WardConfig.globalRollMultiplier, s -> WardConfig.globalRollMultiplier = pFloat(s, WardConfig.globalRollMultiplier)),
                Row.ofBool("Confirm click", "first click previews the ward; second click begins",
                        () -> WardConfig.requireConfirmClick, v -> WardConfig.requireConfirmClick = v),
                Row.ofBool("Auto item values", "score modded items on their own",
                        () -> WardConfig.autoItemValues, v -> WardConfig.autoItemValues = v))));

        pages.add(new Page("Minigame feel", List.of(
                Row.ofInt("Min pins", "keyway and drum",
                        () -> WardConfig.minPins, s -> WardConfig.minPins = pInt(s, WardConfig.minPins)),
                Row.ofInt("Max pins", "keyway caps at 6",
                        () -> WardConfig.maxPins, s -> WardConfig.maxPins = pInt(s, WardConfig.maxPins)),
                Row.ofInt("Input grace, ms", "timing forgiveness, 0 - 200",
                        () -> WardConfig.inputGraceMs, s -> WardConfig.inputGraceMs = pInt(s, WardConfig.inputGraceMs)),
                Row.ofFloat("Auto value scale", "how hard modded items are scored",
                        () -> WardConfig.autoValueScale, s -> WardConfig.autoValueScale = pFloat(s, WardConfig.autoValueScale)),
                Row.ofFloat("Balance edge", "how far the beam may lean, 0.4 - 1",
                        () -> WardConfig.balanceEdge, s -> WardConfig.balanceEdge = pFloat(s, WardConfig.balanceEdge)),
                Row.ofBool("Ward ambience", "warded chests hum when you are near",
                        () -> WardConfig.ambienceEnabled, v -> WardConfig.ambienceEnabled = v),
                Row.ofFloat("Ambience volume", "before distance falloff",
                        () -> WardConfig.ambienceVolume, s -> WardConfig.ambienceVolume = pFloat(s, WardConfig.ambienceVolume)),
                Row.ofFloat("Vessel settle, s", "delay before you may commit blind",
                        () -> WardConfig.vesselSettle, s -> WardConfig.vesselSettle = pFloat(s, WardConfig.vesselSettle)),
                Row.ofFloat("Streak difficulty step", "per ward in a win streak",
                        () -> WardConfig.streakDifficultyPerLock, s -> WardConfig.streakDifficultyPerLock = pFloat(s, WardConfig.streakDifficultyPerLock)),
                Row.ofFloat("Streak difficulty cap", "ceiling on the above",
                        () -> WardConfig.streakDifficultyCap, s -> WardConfig.streakDifficultyCap = pFloat(s, WardConfig.streakDifficultyCap)))));

        pages.add(new Page("Rewards", List.of(
                Row.ofFloat("Streak bonus per ward", "loot per ward in a win streak",
                        () -> WardConfig.streakBonusPerLock, s -> WardConfig.streakBonusPerLock = pFloat(s, WardConfig.streakBonusPerLock)),
                Row.ofFloat("Streak bonus cap", "ceiling on the above",
                        () -> WardConfig.streakBonusCap, s -> WardConfig.streakBonusCap = pFloat(s, WardConfig.streakBonusCap)),
                Row.ofFloat("Pulse intensity", "how strongly the active ward throbs",
                        () -> WardConfig.pulseIntensity, s -> WardConfig.pulseIntensity = pFloat(s, WardConfig.pulseIntensity)),
                Row.ofPercent("Eye drop chance", "once the threshold is beaten",
                        () -> WardConfig.eyeDropChance, s -> WardConfig.eyeDropChance = pFloat(s, WardConfig.eyeDropChance)))));

        pages.add(new Page("Charms", List.of(
                Row.ofInt("Key: extra lives", "durability 4",
                        () -> WardConfig.keyBonusLives, s -> WardConfig.keyBonusLives = pInt(s, WardConfig.keyBonusLives)),
                Row.ofInt("Heart: extra lives", "durability 2",
                        () -> WardConfig.heartBonusLives, s -> WardConfig.heartBonusLives = pInt(s, WardConfig.heartBonusLives)),
                Row.ofFloat("Heart: loot bonus", "1.25 = +25%",
                        () -> WardConfig.heartLootBonus, s -> WardConfig.heartLootBonus = pFloat(s, WardConfig.heartLootBonus)),
                Row.ofPercent("Key drop chance", "per resolved ward",
                        () -> WardConfig.keyDropChance, s -> WardConfig.keyDropChance = pFloat(s, WardConfig.keyDropChance)),
                Row.ofPercent("Heart drop chance", "scaled by chest worth",
                        () -> WardConfig.heartDropChance, s -> WardConfig.heartDropChance = pFloat(s, WardConfig.heartDropChance)),
                Row.ofPercent("Relic drop chance", "shim and cyclopean lens",
                        () -> WardConfig.relicDropChance, s -> WardConfig.relicDropChance = pFloat(s, WardConfig.relicDropChance)))));

        pages.add(new Page("Re-seal offer", List.of(
                Row.ofBool("Offer enabled", "offer an optional second minigame after a win",
                        () -> WardConfig.temptEnabled, v -> WardConfig.temptEnabled = v),
                Row.ofPercent("Offer chance", "per resolved ward",
                        () -> WardConfig.temptChance, s -> WardConfig.temptChance = pFloat(s, WardConfig.temptChance)),
                Row.ofFloat("Offer multiplier", "compounding, per re-seal",
                        () -> WardConfig.temptMultiplier, s -> WardConfig.temptMultiplier = pFloat(s, WardConfig.temptMultiplier)),
                Row.ofInt("Offer max depth", "maximum optional re-seal depth per chest",
                        () -> WardConfig.temptMaxDepth, s -> WardConfig.temptMaxDepth = pInt(s, WardConfig.temptMaxDepth)),
                Row.ofFloat("Offer difficulty step", "per re-seal",
                        () -> WardConfig.temptDifficultyPerDepth, s -> WardConfig.temptDifficultyPerDepth = pFloat(s, WardConfig.temptDifficultyPerDepth)),
                Row.ofFloat("Grudge difficulty", "per failed ward on one container",
                        () -> WardConfig.spiteDifficulty, s -> WardConfig.spiteDifficulty = pFloat(s, WardConfig.spiteDifficulty)),
                Row.ofFloat("Grudge loot", "per failed ward on one container",
                        () -> WardConfig.spiteLoot, s -> WardConfig.spiteLoot = pFloat(s, WardConfig.spiteLoot)),
                Row.ofInt("Grudge max", "how far one container escalates",
                        () -> WardConfig.spiteMax, s -> WardConfig.spiteMax = pInt(s, WardConfig.spiteMax)))));

        pages.add(new Page("Failure & guardians", List.of(
                Row.ofPercent("Guardian chance", "after a failed ward",
                        () -> WardConfig.guardianChance, s -> WardConfig.guardianChance = pFloat(s, WardConfig.guardianChance)),
                Row.ofInt("Guardian max count", "scaled down by chest worth",
                        () -> WardConfig.guardianMaxCount, s -> WardConfig.guardianMaxCount = pInt(s, WardConfig.guardianMaxCount)),
                Row.ofInt("Sulk, seconds", "how long a beaten chest stays shut",
                        () -> WardConfig.guardianSealSeconds, s -> WardConfig.guardianSealSeconds = pInt(s, WardConfig.guardianSealSeconds)),
                Row.ofBool("Guardian dimension lock", "Nether/End mobs stay in their native dimension",
                        () -> WardConfig.guardianDimensionLock, v -> WardConfig.guardianDimensionLock = v),
                Row.ofBool("Guardian progression lock", "requires visiting Nether/End before those tiers unlock",
                        () -> WardConfig.guardianProgressionLock, v -> WardConfig.guardianProgressionLock = v),
                Row.ofPercent("Wild pool chance", "ignore curated list, but still obey guardian locks",
                        () -> WardConfig.guardianWildChance, s -> WardConfig.guardianWildChance = pFloat(s, WardConfig.guardianWildChance)),
                Row.ofInt("Horde size", "when it sends a crowd",
                        () -> WardConfig.hordeSize, s -> WardConfig.hordeSize = pInt(s, WardConfig.hordeSize)),
                Row.ofPercent("Burn chance", "a lost ward takes the contents",
                        () -> WardConfig.failBurnChance, s -> WardConfig.failBurnChance = pFloat(s, WardConfig.failBurnChance)),
                Row.ofPercent("Burn floor", "minimum loot value that may survive",
                        () -> WardConfig.failBurnFloor, s -> WardConfig.failBurnFloor = pFloat(s, WardConfig.failBurnFloor)),
                Row.ofPercent("Burn ceiling", "maximum loot value that may survive",
                        () -> WardConfig.failBurnCeiling, s -> WardConfig.failBurnCeiling = pFloat(s, WardConfig.failBurnCeiling)))));

        pages.add(new Page("Progression tiers", List.of(
                Row.ofInt("Tier step", "resolved wards per tier; 0 disables",
                        () -> WardConfig.tierStep, s -> WardConfig.tierStep = pInt(s, WardConfig.tierStep)),
                Row.ofInt("Tier max", "ceiling on the curve",
                        () -> WardConfig.tierMax, s -> WardConfig.tierMax = pInt(s, WardConfig.tierMax)),
                Row.ofFloat("Tier difficulty step", "per tier",
                        () -> WardConfig.tierDifficultyStep, s -> WardConfig.tierDifficultyStep = pFloat(s, WardConfig.tierDifficultyStep)),
                Row.ofFloat("Tier loot step", "per tier, larger on purpose",
                        () -> WardConfig.tierLootStep, s -> WardConfig.tierLootStep = pFloat(s, WardConfig.tierLootStep)),
                Row.ofBool("Watcher enabled", "one house starts following you",
                        () -> WardConfig.watcherEnabled, v -> WardConfig.watcherEnabled = v),
                Row.ofInt("Watcher after", "seals before it notices",
                        () -> WardConfig.watcherAfter, s -> WardConfig.watcherAfter = pInt(s, WardConfig.watcherAfter)),
                Row.ofFloat("Watcher ramp", "how fast it closes in",
                        () -> WardConfig.watcherRamp, s -> WardConfig.watcherRamp = pFloat(s, WardConfig.watcherRamp)),
                Row.ofPercent("Watcher max chance", "never certain",
                        () -> WardConfig.watcherMaxChance, s -> WardConfig.watcherMaxChance = pFloat(s, WardConfig.watcherMaxChance)))));

        pages.add(new Page("Endgame wards", List.of(
                Row.ofInt("Verdict after", "its seals before it judges you",
                                        () -> WardConfig.verdictAfter, s -> WardConfig.verdictAfter = pInt(s, WardConfig.verdictAfter)),
                Row.ofInt("Verdict threshold", "regard needed for favour",
                                        () -> WardConfig.verdictThreshold, s -> WardConfig.verdictThreshold = pInt(s, WardConfig.verdictThreshold)),
                Row.ofFloat("Contempt difficulty", "on that house's seals",
                                        () -> WardConfig.contemptDifficulty, s -> WardConfig.contemptDifficulty = pFloat(s, WardConfig.contemptDifficulty)),
                Row.ofFloat("Contempt loot", "and what it pays",
                                        () -> WardConfig.contemptLoot, s -> WardConfig.contemptLoot = pFloat(s, WardConfig.contemptLoot)),
                Row.ofPercent("Unsigned chance", "a seal with no maker",
                                        () -> WardConfig.unsignedChance, s -> WardConfig.unsignedChance = pFloat(s, WardConfig.unsignedChance)),
                Row.ofFloat("Unsigned, End factor", "how much likelier there",
                                        () -> WardConfig.unsignedEndFactor, s -> WardConfig.unsignedEndFactor = pFloat(s, WardConfig.unsignedEndFactor)),
                Row.ofFloat("Unsigned loot", "what one is worth",
                                        () -> WardConfig.unsignedLoot, s -> WardConfig.unsignedLoot = pFloat(s, WardConfig.unsignedLoot)),
                Row.ofInt("Gauntlet after", "unsigned seals before threes",
                                        () -> WardConfig.gauntletAfter, s -> WardConfig.gauntletAfter = pInt(s, WardConfig.gauntletAfter)),
                Row.ofBool("Cthulhu ward", "ultra-rare singular ward",
                                        () -> WardConfig.cthulhuWardsEnabled, v -> WardConfig.cthulhuWardsEnabled = v),
                Row.ofPercent("Cthulhu chance", "singular ward rarity",
                                        () -> WardConfig.cthulhuWardChance, s -> WardConfig.cthulhuWardChance = pFloat(s, WardConfig.cthulhuWardChance)),
                Row.ofInt("Cthulhu min value", "minimum chest value",
                                        () -> WardConfig.cthulhuWardMinValue, s -> WardConfig.cthulhuWardMinValue = pInt(s, WardConfig.cthulhuWardMinValue)),
                Row.ofFloat("Cthulhu difficulty", "extra difficulty multiplier",
                                        () -> WardConfig.cthulhuWardDifficulty, s -> WardConfig.cthulhuWardDifficulty = pFloat(s, WardConfig.cthulhuWardDifficulty)),
                Row.ofFloat("Cthulhu loot", "extra loot multiplier",
                                        () -> WardConfig.cthulhuWardLoot, s -> WardConfig.cthulhuWardLoot = pFloat(s, WardConfig.cthulhuWardLoot)),
                Row.ofInt("Idol cooldown", "seconds between uses",
                                        () -> WardConfig.cthulhuIdolCooldownSeconds, s -> WardConfig.cthulhuIdolCooldownSeconds = pInt(s, WardConfig.cthulhuIdolCooldownSeconds)),
                Row.ofFloat("Idol freeze", "seconds of arrest",
                                        () -> WardConfig.cthulhuIdolFreezeSeconds, s -> WardConfig.cthulhuIdolFreezeSeconds = pFloat(s, WardConfig.cthulhuIdolFreezeSeconds)),
                Row.ofBool("Eldritch wards", "rare elite chained wards",
                                        () -> WardConfig.eldritchWardsEnabled, v -> WardConfig.eldritchWardsEnabled = v),
                Row.ofPercent("Eldritch chance", "chance for an elite ward",
                                        () -> WardConfig.eldritchWardChance, s -> WardConfig.eldritchWardChance = pFloat(s, WardConfig.eldritchWardChance)),
                Row.ofInt("Eldritch stages", "minigames in the elite encounter",
                                        () -> WardConfig.eldritchWardStages, s -> WardConfig.eldritchWardStages = pInt(s, WardConfig.eldritchWardStages)),
                Row.ofFloat("Eldritch loot", "final payout multiplier",
                                        () -> WardConfig.eldritchWardLoot, s -> WardConfig.eldritchWardLoot = pFloat(s, WardConfig.eldritchWardLoot)),
                Row.ofBool("Possessed wards", "rare single corrupted minigame",
                                        () -> WardConfig.possessedWardsEnabled, v -> WardConfig.possessedWardsEnabled = v),
                Row.ofPercent("Possessed chance", "very rare one-minigame mutation",
                                        () -> WardConfig.possessedWardChance, s -> WardConfig.possessedWardChance = pFloat(s, WardConfig.possessedWardChance)),
                Row.ofFloat("Possessed loot", "reward multiplier",
                                        () -> WardConfig.possessedWardLoot, s -> WardConfig.possessedWardLoot = pFloat(s, WardConfig.possessedWardLoot)),
                Row.ofBool("Ward afflictions", "persistent named ward afflictions",
                                        () -> WardConfig.wardAfflictionsEnabled, v -> WardConfig.wardAfflictionsEnabled = v),
                Row.ofPercent("Affliction chance", "chance for a new ward affliction",
                                        () -> WardConfig.wardAfflictionChance, s -> WardConfig.wardAfflictionChance = pFloat(s, WardConfig.wardAfflictionChance)),
                Row.ofBool("Living wards", "old mutated wards may awaken",
                                        () -> WardConfig.livingWardsEnabled, v -> WardConfig.livingWardsEnabled = v),
                Row.ofPercent("Living chance", "chance once mutation threshold is met",
                                        () -> WardConfig.livingWardChance, s -> WardConfig.livingWardChance = pFloat(s, WardConfig.livingWardChance)),
                Row.ofFloat("Living loot", "reward multiplier for living wards",
                                        () -> WardConfig.livingWardLoot, s -> WardConfig.livingWardLoot = pFloat(s, WardConfig.livingWardLoot)))));

        pages.add(new Page("Progression gates", List.of(
                Row.ofInt("Affliction after", "ward wins before afflictions can roll",
                                        () -> WardConfig.afflictionAfterBeaten, s -> WardConfig.afflictionAfterBeaten = pInt(s, WardConfig.afflictionAfterBeaten)),
                Row.ofInt("Possessed after", "ward wins before possessed wards can roll",
                                        () -> WardConfig.possessedAfterBeaten, s -> WardConfig.possessedAfterBeaten = pInt(s, WardConfig.possessedAfterBeaten)),
                Row.ofInt("Unsigned after", "ward wins before unsigned seals can roll",
                                        () -> WardConfig.unsignedAfterBeaten, s -> WardConfig.unsignedAfterBeaten = pInt(s, WardConfig.unsignedAfterBeaten)),
                Row.ofInt("Eldritch after", "ward wins before mutation chains can roll",
                                        () -> WardConfig.eldritchAfterBeaten, s -> WardConfig.eldritchAfterBeaten = pInt(s, WardConfig.eldritchAfterBeaten)),
                Row.ofInt("Cthulhu after", "ward wins before Cthulhu wards can roll",
                                        () -> WardConfig.cthulhuAfterBeaten, s -> WardConfig.cthulhuAfterBeaten = pInt(s, WardConfig.cthulhuAfterBeaten)),
                Row.ofInt("Fresh reroll after", "wins before reward may reroll chest table",
                                        () -> WardConfig.bonusFreshRollAfterBeaten, s -> WardConfig.bonusFreshRollAfterBeaten = pInt(s, WardConfig.bonusFreshRollAfterBeaten)),
                Row.ofBool("Progression loot caps", "large-modpack reward curve limits early multipliers",
                                        () -> WardConfig.progressionLootCapsEnabled, v -> WardConfig.progressionLootCapsEnabled = v),
                Row.ofInt("Normal cards after", "resolved wards before ordinary post-ward card hands can appear",
                                        () -> WardConfig.normalCardsAfterBeaten, s -> WardConfig.normalCardsAfterBeaten = pInt(s, WardConfig.normalCardsAfterBeaten)),
                Row.ofInt("Field cards after", "resolved wards before hostile mobs can drop Sealed Cards (default 20)",
                                        () -> WardConfig.fieldCardAfterBeaten, s -> WardConfig.fieldCardAfterBeaten = pInt(s, WardConfig.fieldCardAfterBeaten)),
                Row.ofPercent("Field card drop", "eligible hostile kill base chance; pity starts after 10 misses, kill 30 is guaranteed",
                                        () -> WardConfig.fieldCardDropChance, s -> WardConfig.fieldCardDropChance = pFloat(s, WardConfig.fieldCardDropChance)),
                Row.ofInt("Master cards after", "ward wins before private-law cards can appear",
                                        () -> WardConfig.masterCardsAfterBeaten, s -> WardConfig.masterCardsAfterBeaten = pInt(s, WardConfig.masterCardsAfterBeaten)),
                Row.ofInt("Contract cards after", "ward wins before objective Contract Hands can appear",
                                        () -> WardConfig.contractCardsAfterBeaten, s -> WardConfig.contractCardsAfterBeaten = pInt(s, WardConfig.contractCardsAfterBeaten)),
                Row.ofInt("Ritual cards after", "ward wins before mining/world Ritual Hands can appear",
                                        () -> WardConfig.ritualCardsAfterBeaten, s -> WardConfig.ritualCardsAfterBeaten = pInt(s, WardConfig.ritualCardsAfterBeaten)),
                Row.ofInt("Covenant cards after", "ward wins before dark requirement Covenants can appear",
                                        () -> WardConfig.covenantCardsAfterBeaten, s -> WardConfig.covenantCardsAfterBeaten = pInt(s, WardConfig.covenantCardsAfterBeaten)),
                Row.ofInt("Curse cards after", "ward wins before curse hands can appear",
                                        () -> WardConfig.curseCardsAfterBeaten, s -> WardConfig.curseCardsAfterBeaten = pInt(s, WardConfig.curseCardsAfterBeaten)),
                Row.ofInt("Epic cards after", "ward wins before epic cards can appear",
                                        () -> WardConfig.epicCardsAfterBeaten, s -> WardConfig.epicCardsAfterBeaten = pInt(s, WardConfig.epicCardsAfterBeaten)),
                Row.ofInt("Unique cards after", "ward wins before unique world laws can appear",
                                        () -> WardConfig.uniqueCardsAfterBeaten, s -> WardConfig.uniqueCardsAfterBeaten = pInt(s, WardConfig.uniqueCardsAfterBeaten)),
                Row.ofInt("Death cards after", "ward wins before Death Hands can exist",
                                        () -> WardConfig.deathCardsAfterBeaten, s -> WardConfig.deathCardsAfterBeaten = pInt(s, WardConfig.deathCardsAfterBeaten)))));

        pages.add(new Page("Mastery & relics", List.of(
                Row.ofInt("Mercy after", "losses before mercy can appear",
                                        () -> WardConfig.mercyAfterLosses, s -> WardConfig.mercyAfterLosses = pInt(s, WardConfig.mercyAfterLosses)),
                Row.ofPercent("Mercy chance", "chance on an eligible retry",
                                        () -> WardConfig.mercyChance, s -> WardConfig.mercyChance = pFloat(s, WardConfig.mercyChance)),
                Row.ofFloat("Mercy loot", "reward multiplier during mercy",
                                        () -> WardConfig.mercyLoot, s -> WardConfig.mercyLoot = pFloat(s, WardConfig.mercyLoot)),
                Row.ofPercent("Perfect relic", "Ward Splinter chance on Perfect",
                                        () -> WardConfig.perfectRelicChance, s -> WardConfig.perfectRelicChance = pFloat(s, WardConfig.perfectRelicChance)),
                Row.ofPercent("Eldritch relic", "Eldritch Shard chance on Perfect finale",
                                        () -> WardConfig.eldritchRelicChance, s -> WardConfig.eldritchRelicChance = pFloat(s, WardConfig.eldritchRelicChance)),
                Row.ofBool("Master rivalries", "houses react to rival mastery",
                                        () -> WardConfig.masterRivalriesEnabled, v -> WardConfig.masterRivalriesEnabled = v),
                Row.ofInt("Rivalry after", "rival familiarity threshold",
                                        () -> WardConfig.masterRivalryAfter, s -> WardConfig.masterRivalryAfter = pInt(s, WardConfig.masterRivalryAfter)),
                Row.ofFloat("Rivalry difficulty", "reactive difficulty multiplier",
                                        () -> WardConfig.masterRivalryDifficulty, s -> WardConfig.masterRivalryDifficulty = pFloat(s, WardConfig.masterRivalryDifficulty)),
                Row.ofFloat("Rivalry loot", "reactive reward multiplier",
                                        () -> WardConfig.masterRivalryLoot, s -> WardConfig.masterRivalryLoot = pFloat(s, WardConfig.masterRivalryLoot)))));

        pages.add(new Page("Ward aging", List.of(
                Row.ofFloat("Mutation difficulty", "per persistent mutation tier",
                                        () -> WardConfig.wardMutationDifficultyPerLevel, s -> WardConfig.wardMutationDifficultyPerLevel = pFloat(s, WardConfig.wardMutationDifficultyPerLevel)),
                Row.ofInt("Mutation I age", "visible ward age before mutation I",
                                        () -> WardConfig.mutationTier1Age, s -> WardConfig.mutationTier1Age = pInt(s, WardConfig.mutationTier1Age)),
                Row.ofInt("Mutation II age", "visible ward age before mutation II",
                                        () -> WardConfig.mutationTier2Age, s -> WardConfig.mutationTier2Age = pInt(s, WardConfig.mutationTier2Age)),
                Row.ofInt("Mutation III age", "visible ward age before mutation III",
                                        () -> WardConfig.mutationTier3Age, s -> WardConfig.mutationTier3Age = pInt(s, WardConfig.mutationTier3Age)),
                Row.ofInt("Mutation I after", "global ward wins before physical mutation I can exist",
                                        () -> WardConfig.mutationTier1AfterBeaten, s -> WardConfig.mutationTier1AfterBeaten = pInt(s, WardConfig.mutationTier1AfterBeaten)),
                Row.ofInt("Mutation II after", "global ward wins before physical mutation II can exist",
                                        () -> WardConfig.mutationTier2AfterBeaten, s -> WardConfig.mutationTier2AfterBeaten = pInt(s, WardConfig.mutationTier2AfterBeaten)),
                Row.ofInt("Mutation III after", "global ward wins before physical mutation III can exist",
                                        () -> WardConfig.mutationTier3AfterBeaten, s -> WardConfig.mutationTier3AfterBeaten = pInt(s, WardConfig.mutationTier3AfterBeaten)),
                Row.ofBool("Post-win residue", "world-space aftermath after wins",
                                        () -> WardConfig.postWinResidueEnabled, v -> WardConfig.postWinResidueEnabled = v),
                Row.ofFloat("Old ward / scar", "extra loot per visible scar",
                                        () -> WardConfig.oldWardLootPerScar, s -> WardConfig.oldWardLootPerScar = pFloat(s, WardConfig.oldWardLootPerScar)),
                Row.ofFloat("Old ward cap", "maximum age-based multiplier",
                                        () -> WardConfig.oldWardLootCap, s -> WardConfig.oldWardLootCap = pFloat(s, WardConfig.oldWardLootCap)))));

        pages.add(new Page("Chains", List.of(
                Row.ofBool("Chains enabled", "seals that come in sets",
                        () -> WardConfig.chainEnabled, v -> WardConfig.chainEnabled = v),
                Row.ofInt("Chains after", "resolved wards before chains can begin",
                        () -> WardConfig.chainAfterBeaten, x -> WardConfig.chainAfterBeaten = pInt(x, WardConfig.chainAfterBeaten)),
                Row.ofPercent("Chain chance", "per ward beaten",
                        () -> WardConfig.chainChance, s -> WardConfig.chainChance = pFloat(s, WardConfig.chainChance)),
                Row.ofInt("Min links", "shortest set",
                        () -> WardConfig.chainMinLinks, s -> WardConfig.chainMinLinks = pInt(s, WardConfig.chainMinLinks)),
                Row.ofInt("Max links", "longest set",
                        () -> WardConfig.chainMaxLinks, s -> WardConfig.chainMaxLinks = pInt(s, WardConfig.chainMaxLinks)),
                Row.ofInt("Min distance", "blocks between links",
                        () -> WardConfig.chainMinDistance, s -> WardConfig.chainMinDistance = pInt(s, WardConfig.chainMinDistance)),
                Row.ofInt("Max distance", "blocks between links",
                        () -> WardConfig.chainMaxDistance, s -> WardConfig.chainMaxDistance = pInt(s, WardConfig.chainMaxDistance)),
                Row.ofInt("Bearing radius", "how loose the heading is",
                        () -> WardConfig.chainRadius, s -> WardConfig.chainRadius = pInt(s, WardConfig.chainRadius)),
                Row.ofFloat("Difficulty per link", "each one harder",
                        () -> WardConfig.chainDifficultyPerLink, s -> WardConfig.chainDifficultyPerLink = pFloat(s, WardConfig.chainDifficultyPerLink)),
                Row.ofInt("Wright key: lives", "on its own house's seals",
                        () -> WardConfig.wrightBonusLives, s -> WardConfig.wrightBonusLives = pInt(s, WardConfig.wrightBonusLives)),
                Row.ofFloat("Wright key: loot", "1.5 = +50%",
                        () -> WardConfig.wrightLootBonus, s -> WardConfig.wrightLootBonus = pFloat(s, WardConfig.wrightLootBonus)),
                Row.ofFloat("Loot per link", "each one worth more",
                        () -> WardConfig.chainLootPerLink, s -> WardConfig.chainLootPerLink = pFloat(s, WardConfig.chainLootPerLink)))));

        pages.add(new Page("Attention", List.of(
                Row.ofInt("Gauntlet stages", "minigames in a row",
                        () -> WardConfig.gauntletStages, s -> WardConfig.gauntletStages = pInt(s, WardConfig.gauntletStages)),
                Row.ofFloat("Gauntlet loot", "on top of unsigned",
                        () -> WardConfig.gauntletLoot, s -> WardConfig.gauntletLoot = pFloat(s, WardConfig.gauntletLoot)),
                Row.ofBool("Attention enabled", "something watches after a gauntlet",
                        () -> WardConfig.attentionEnabled, v -> WardConfig.attentionEnabled = v),
                Row.ofInt("Attention start", "seals to break before it lets go",
                        () -> WardConfig.attentionStart, s -> WardConfig.attentionStart = pInt(s, WardConfig.attentionStart)),
                Row.ofInt("Attention per loss", "how much a loss adds back",
                        () -> WardConfig.attentionPerLoss, s -> WardConfig.attentionPerLoss = pInt(s, WardConfig.attentionPerLoss)),
                Row.ofInt("Attention cap", "how far it can climb",
                        () -> WardConfig.attentionCap, s -> WardConfig.attentionCap = pInt(s, WardConfig.attentionCap)),
                Row.ofPercent("Re-seal chance", "emptied chests close again",
                        () -> WardConfig.attentionResealChance, s -> WardConfig.attentionResealChance = pFloat(s, WardConfig.attentionResealChance)),
                Row.ofPercent("Horde on a win", "winning is not a reprieve",
                        () -> WardConfig.attentionHordeChance, s -> WardConfig.attentionHordeChance = pFloat(s, WardConfig.attentionHordeChance)))));


        pages.add(new Page("Cards & debts", List.of(
                Row.ofBool("Master signatures", "bias one existing quirk slot",
                        () -> WardConfig.masterSignaturesEnabled, v -> WardConfig.masterSignaturesEnabled = v),
                Row.ofPercent("Signature bias", "bias probability; never adds a quirk",
                        () -> WardConfig.masterSignatureBiasChance, s -> WardConfig.masterSignatureBiasChance = pFloat(s, WardConfig.masterSignatureBiasChance)),
                Row.ofInt("Reveal hand after", "familiarity threshold",
                        () -> WardConfig.masterSignatureRevealAfter, s -> WardConfig.masterSignatureRevealAfter = pInt(s, WardConfig.masterSignatureRevealAfter)),
                Row.ofInt("Know hand after", "second familiarity threshold",
                        () -> WardConfig.masterSignatureKnowAfter, s -> WardConfig.masterSignatureKnowAfter = pInt(s, WardConfig.masterSignatureKnowAfter)),
                Row.ofBool("Forbidden bargains", "rare post-ward card choice",
                        () -> WardConfig.forbiddenBargainsEnabled, v -> WardConfig.forbiddenBargainsEnabled = v),
                Row.ofPercent("Bargain chance", "per eligible ward",
                        () -> WardConfig.forbiddenBargainChance, s -> WardConfig.forbiddenBargainChance = pFloat(s, WardConfig.forbiddenBargainChance)),
                Row.ofInt("Bargain cards", "ordinary hands may deal 2 - 4",
                        () -> WardConfig.forbiddenBargainMaxOffers, s -> WardConfig.forbiddenBargainMaxOffers = pInt(s, WardConfig.forbiddenBargainMaxOffers)),
                Row.ofFloat("Borrowed Breath", "flat loot addition",
                        () -> WardConfig.bargainBorrowedBreathReward, s -> WardConfig.bargainBorrowedBreathReward = pFloat(s, WardConfig.bargainBorrowedBreathReward)),
                Row.ofFloat("Debt of Iron", "flat loot addition",
                        () -> WardConfig.bargainIronDebtReward, s -> WardConfig.bargainIronDebtReward = pFloat(s, WardConfig.bargainIronDebtReward)),
                Row.ofFloat("Watching Mark", "flat loot addition",
                        () -> WardConfig.bargainWatchingMarkReward, s -> WardConfig.bargainWatchingMarkReward = pFloat(s, WardConfig.bargainWatchingMarkReward)))));

        pages.add(new Page("Timing & bonuses", List.of(
                Row.ofFloat("Ward clock, base", "seconds before value scaling",
                        () -> WardConfig.wardClockBaseSeconds, s -> WardConfig.wardClockBaseSeconds = pFloat(s, WardConfig.wardClockBaseSeconds)),
                Row.ofFloat("Ward clock, value", "seconds added per chest worth",
                        () -> WardConfig.wardClockValueSeconds, s -> WardConfig.wardClockValueSeconds = pFloat(s, WardConfig.wardClockValueSeconds)),
                Row.ofFloat("Clock difficulty", "time returned to harder minigames",
                        () -> WardConfig.wardClockDifficultyCompensation, s -> WardConfig.wardClockDifficultyCompensation = pFloat(s, WardConfig.wardClockDifficultyCompensation)),
                Row.ofFloat("Clock minimum", "hard floor in seconds",
                        () -> WardConfig.wardClockMinimumSeconds, s -> WardConfig.wardClockMinimumSeconds = pFloat(s, WardConfig.wardClockMinimumSeconds)),
                Row.ofFloat("Heart ward seconds", "extra time if the heart intervenes",
                        () -> WardConfig.heartWardSeconds, s -> WardConfig.heartWardSeconds = pFloat(s, WardConfig.heartWardSeconds)),
                Row.ofFloat("Hurried time scale", "global Hurried minigame time multiplier",
                        () -> WardConfig.hurriedTimeScale, s -> WardConfig.hurriedTimeScale = pFloat(s, WardConfig.hurriedTimeScale)),
                Row.ofFloat("Life reward cap", "clean wins can add up to this much",
                        () -> WardConfig.successLifeBonusMax, s -> WardConfig.successLifeBonusMax = pFloat(s, WardConfig.successLifeBonusMax)),
                Row.ofFloat("Skill reward cap", "clean play reward ceiling",
                        () -> WardConfig.successSkillBonusMax, s -> WardConfig.successSkillBonusMax = pFloat(s, WardConfig.successSkillBonusMax)),
                Row.ofFloat("Known hand bonus", "per known seal broken",
                        () -> WardConfig.knownHandBonusPerSeal, s -> WardConfig.knownHandBonusPerSeal = pFloat(s, WardConfig.knownHandBonusPerSeal)),
                Row.ofFloat("Known hand cap", "ceiling on the above",
                        () -> WardConfig.knownHandBonusCap, s -> WardConfig.knownHandBonusCap = pFloat(s, WardConfig.knownHandBonusCap)))));

        pages.add(new Page("Anomalies", List.of(
                Row.ofBool("Quirks enabled", "allow authored quirk rolls",
                        () -> WardConfig.quirksEnabled, v -> WardConfig.quirksEnabled = v),
                Row.ofBool("Anomalies enabled", "allow hurried, shrouded and kin",
                        () -> WardConfig.anomaliesEnabled, v -> WardConfig.anomaliesEnabled = v),
                Row.ofInt("One quirk after", "value threshold for first quirk",
                        () -> WardConfig.quirkOneThreshold, s -> WardConfig.quirkOneThreshold = pInt(s, WardConfig.quirkOneThreshold)),
                Row.ofInt("Two quirks after", "value threshold for second quirk",
                        () -> WardConfig.quirkTwoThreshold, s -> WardConfig.quirkTwoThreshold = pInt(s, WardConfig.quirkTwoThreshold)),
                Row.ofPercent("Low-value quirk chance", "0 - 1",
                        () -> WardConfig.quirkLowValueChance, s -> WardConfig.quirkLowValueChance = pFloat(s, WardConfig.quirkLowValueChance)),
                Row.ofInt("Two anomalies after", "value threshold for a second anomaly",
                        () -> WardConfig.anomalyTwoThreshold, s -> WardConfig.anomalyTwoThreshold = pInt(s, WardConfig.anomalyTwoThreshold)),
                Row.ofPercent("Hurried chance", "0 - 1",
                        () -> WardConfig.anomalyHurriedChance, s -> WardConfig.anomalyHurriedChance = pFloat(s, WardConfig.anomalyHurriedChance)),
                Row.ofPercent("Shrouded chance", "0 - 1",
                        () -> WardConfig.anomalyShroudedChance, s -> WardConfig.anomalyShroudedChance = pFloat(s, WardConfig.anomalyShroudedChance)),
                Row.ofPercent("Guttering chance", "0 - 1",
                        () -> WardConfig.anomalyGutteringChance, s -> WardConfig.anomalyGutteringChance = pFloat(s, WardConfig.anomalyGutteringChance)),
                Row.ofPercent("Unlit chance", "0 - 1",
                        () -> WardConfig.anomalyUnlitChance, s -> WardConfig.anomalyUnlitChance = pFloat(s, WardConfig.anomalyUnlitChance)),
                Row.ofPercent("Spent chance", "starts one stage down but costs a life",
                        () -> WardConfig.anomalySpentChance, s -> WardConfig.anomalySpentChance = pFloat(s, WardConfig.anomalySpentChance)),
                Row.ofFloat("Hurried minimum", "minimum hurried clock in seconds",
                        () -> WardConfig.hurriedMinSeconds, s -> WardConfig.hurriedMinSeconds = pFloat(s, WardConfig.hurriedMinSeconds)),
                Row.ofFloat("Hurried base", "base hurried clock before difficulty",
                        () -> WardConfig.hurriedBaseSeconds, s -> WardConfig.hurriedBaseSeconds = pFloat(s, WardConfig.hurriedBaseSeconds)))));

        pages.add(new Page("Relics & presentation", List.of(
                Row.ofInt("Eye cooldown", "ticks between uses",
                        () -> WardConfig.eyeCooldownTicks, s -> WardConfig.eyeCooldownTicks = pInt(s, WardConfig.eyeCooldownTicks)),
                Row.ofFloat("Eye loot threshold", "End chest value needed to appear",
                        () -> WardConfig.eyeLootThreshold, s -> WardConfig.eyeLootThreshold = pFloat(s, WardConfig.eyeLootThreshold)),
                Row.ofInt("Savant after wins", "resolved ward gate before End chests may drop it",
                        () -> WardConfig.eyeAfterBeaten, s -> WardConfig.eyeAfterBeaten = pInt(s, WardConfig.eyeAfterBeaten)),
                Row.ofPercent("Lens true-name chance", "0 - 1",
                        () -> WardConfig.lensTrueNameChance, s -> WardConfig.lensTrueNameChance = pFloat(s, WardConfig.lensTrueNameChance)),
                Row.ofInt("Slow ember seconds", "extra clock seconds added",
                        () -> WardConfig.emberSeconds, s -> WardConfig.emberSeconds = pInt(s, WardConfig.emberSeconds)),
                Row.ofFloat("Ember loot threshold", "Nether chest value needed to appear",
                        () -> WardConfig.emberLootThreshold, s -> WardConfig.emberLootThreshold = pFloat(s, WardConfig.emberLootThreshold)),
                Row.ofInt("Ember after wins", "resolved ward gate before Nether chests may drop it",
                        () -> WardConfig.emberAfterBeaten, s -> WardConfig.emberAfterBeaten = pInt(s, WardConfig.emberAfterBeaten)),
                Row.ofPercent("Ember drop chance", "0 - 1",
                        () -> WardConfig.emberDropChance, s -> WardConfig.emberDropChance = pFloat(s, WardConfig.emberDropChance)),
                Row.ofFloat("Card hover lift", "visual lift in pixels",
                        () -> WardConfig.bargainCardHoverLift, s -> WardConfig.bargainCardHoverLift = pFloat(s, WardConfig.bargainCardHoverLift)),
                Row.ofFloat("Card anim speed", "hover/settle animation speed",
                        () -> WardConfig.bargainCardAnimSpeed, s -> WardConfig.bargainCardAnimSpeed = pFloat(s, WardConfig.bargainCardAnimSpeed)),
                Row.ofInt("Ambience period", "ticks between nearby hum checks",
                        () -> WardConfig.ambiencePeriodTicks, s -> WardConfig.ambiencePeriodTicks = pInt(s, WardConfig.ambiencePeriodTicks)),
                Row.ofFloat("Ambience range", "blocks",
                        () -> WardConfig.ambienceRange, s -> WardConfig.ambienceRange = pFloat(s, WardConfig.ambienceRange)))));

        pages.add(new Page("Director", List.of(
                Row.ofBool("Anti-repetition", "de-weight recently played minigames",
                        () -> WardConfig.antiRepetitionEnabled, v -> WardConfig.antiRepetitionEnabled = v),
                Row.ofInt("Recent window", "1 - 8 recent minigames remembered",
                        () -> WardConfig.antiRepetitionWindow, s -> WardConfig.antiRepetitionWindow = pInt(s, WardConfig.antiRepetitionWindow)),
                Row.ofFloat("Newest weight", "0.2 = most recent is 20% as likely",
                        () -> WardConfig.antiRepetitionRecentWeight, s -> WardConfig.antiRepetitionRecentWeight = pFloat(s, WardConfig.antiRepetitionRecentWeight)),
                Row.ofFloat("Weight recovery", "added per older slot toward 1.0",
                        () -> WardConfig.antiRepetitionDecay, s -> WardConfig.antiRepetitionDecay = pFloat(s, WardConfig.antiRepetitionDecay)),
                Row.ofBool("Master motifs", "three-note maker signature on open",
                        () -> WardConfig.masterAudioMotifsEnabled, v -> WardConfig.masterAudioMotifsEnabled = v),
                Row.ofFloat("Motif volume", "0 disables it; routed through Blocks",
                        () -> WardConfig.masterAudioMotifVolume, s -> WardConfig.masterAudioMotifVolume = pFloat(s, WardConfig.masterAudioMotifVolume)),
                Row.ofBool("Ward scars", "old breaks and burns remain on the frame",
                        () -> WardConfig.wardHistoryScarsEnabled, v -> WardConfig.wardHistoryScarsEnabled = v),
                Row.ofInt("Scar cap", "maximum old marks shown at once",
                        () -> WardConfig.wardHistoryScarCap, s -> WardConfig.wardHistoryScarCap = pInt(s, WardConfig.wardHistoryScarCap)))));

        pages.add(new Page("QA & rewards", List.of(
                Row.ofFloat("Loot hard cap", "absolute final multiplier ceiling",
                        () -> WardConfig.maxLootMultiplier, v -> WardConfig.maxLootMultiplier = pFloat(v, WardConfig.maxLootMultiplier)),
                Row.ofBool("Performance grades", "Scraped / Clean / Perfect resolution grading",
                        () -> WardConfig.cleanWinsEnabled, v -> WardConfig.cleanWinsEnabled = v),
                Row.ofPercent("Clean performance", "minimum quality threshold; 50 - 100%",
                        () -> WardConfig.cleanWinPerformanceThreshold, v -> WardConfig.cleanWinPerformanceThreshold = pFloat(v, WardConfig.cleanWinPerformanceThreshold)),
                Row.ofPercent("Perfect performance", "must be at least Clean; 50 - 100%",
                        () -> WardConfig.perfectWinPerformanceThreshold, v -> WardConfig.perfectWinPerformanceThreshold = pFloat(v, WardConfig.perfectWinPerformanceThreshold)),
                Row.ofPercent("Clean time left", "minimum ward time remaining; up to 95%",
                        () -> WardConfig.cleanWinTimeRemaining, v -> WardConfig.cleanWinTimeRemaining = pFloat(v, WardConfig.cleanWinTimeRemaining)),
                Row.ofPercent("Perfect time left", "must be at least Clean; up to 95%",
                        () -> WardConfig.perfectWinTimeRemaining, v -> WardConfig.perfectWinTimeRemaining = pFloat(v, WardConfig.perfectWinTimeRemaining)),
                Row.ofFloat("Scraped loot scale", "multiplier for a successful but rough resolution",
                        () -> WardConfig.scrapedWinLootScale, v -> WardConfig.scrapedWinLootScale = pFloat(v, WardConfig.scrapedWinLootScale)),
                Row.ofFloat("Clean loot add", "flat, still obeys hard cap",
                        () -> WardConfig.cleanWinLootAdd, v -> WardConfig.cleanWinLootAdd = pFloat(v, WardConfig.cleanWinLootAdd)),
                Row.ofFloat("Perfect loot add", "flat, still obeys hard cap",
                        () -> WardConfig.perfectWinLootAdd, v -> WardConfig.perfectWinLootAdd = pFloat(v, WardConfig.perfectWinLootAdd)),
                Row.ofInt("Clean regard", "extra watcher regard for a clean win",
                        () -> WardConfig.cleanWinRegardBonus, v -> WardConfig.cleanWinRegardBonus = pInt(v, WardConfig.cleanWinRegardBonus)),
                Row.ofInt("Perfect regard", "extra watcher regard for a perfect win",
                        () -> WardConfig.perfectWinRegardBonus, v -> WardConfig.perfectWinRegardBonus = pInt(v, WardConfig.perfectWinRegardBonus)),
                Row.ofBool("Telemetry", "writes logs/wardbound-telemetry.csv",
                        () -> WardConfig.telemetryEnabled, v -> WardConfig.telemetryEnabled = v),
                Row.ofBool("Log failures", "include failed wards in telemetry",
                        () -> WardConfig.telemetryLogFailures, v -> WardConfig.telemetryLogFailures = v),
                Row.ofBool("Telemetry modifiers", "include quirk/anomaly labels in CSV",
                        () -> WardConfig.telemetryIncludeModifiers, v -> WardConfig.telemetryIncludeModifiers = v))));

        pages.add(new Page("Adaptive wards", List.of(
                Row.ofBool("Skill profile", "tracks timing / logic / memory / composure",
                        () -> WardConfig.adaptiveSkillProfileEnabled, v -> WardConfig.adaptiveSkillProfileEnabled = v),
                Row.ofBool("Deep variants", "mastery/value-gated advanced rules inside familiar minigames",
                        () -> WardConfig.advancedMinigameVariantsEnabled, v -> WardConfig.advancedMinigameVariantsEnabled = v),
                Row.ofFloat("Deep variant rate", "global 0 - 3 multiplier for natural expert variants",
                        () -> WardConfig.advancedMinigameVariantChanceScale, v -> WardConfig.advancedMinigameVariantChanceScale = pFloat(v, WardConfig.advancedMinigameVariantChanceScale)),
                Row.ofBool("Deception layer", "rare late-game false feedback; state stays truthful",
                        () -> WardConfig.deceptionLayerEnabled, v -> WardConfig.deceptionLayerEnabled = v),
                Row.ofInt("Deception unlock", "resolved wards before false feedback can appear",
                        () -> WardConfig.deceptionUnlockAfter, v -> WardConfig.deceptionUnlockAfter = pInt(v, WardConfig.deceptionUnlockAfter)),
                Row.ofPercent("Deception base", "base probability after unlock",
                        () -> WardConfig.deceptionBaseChance, v -> WardConfig.deceptionBaseChance = pFloat(v, WardConfig.deceptionBaseChance)),
                Row.ofPercent("Deception cap", "maximum natural probability",
                        () -> WardConfig.deceptionMaxChance, v -> WardConfig.deceptionMaxChance = pFloat(v, WardConfig.deceptionMaxChance)),
                Row.ofBool("Hybrid rounds", "rare cross-discipline borrowed mechanisms",
                        () -> WardConfig.hybridRoundsEnabled, v -> WardConfig.hybridRoundsEnabled = v),
                Row.ofInt("Hybrid unlock", "resolved wards before hybrid rounds can appear",
                        () -> WardConfig.hybridUnlockAfter, v -> WardConfig.hybridUnlockAfter = pInt(v, WardConfig.hybridUnlockAfter)),
                Row.ofPercent("Hybrid base", "base probability after unlock",
                        () -> WardConfig.hybridBaseChance, v -> WardConfig.hybridBaseChance = pFloat(v, WardConfig.hybridBaseChance)),
                Row.ofPercent("Hybrid cap", "maximum natural probability",
                        () -> WardConfig.hybridMaxChance, v -> WardConfig.hybridMaxChance = pFloat(v, WardConfig.hybridMaxChance)),
                Row.ofFloat("Hybrid loot bonus", "extra multiplier for clearing the interruption",
                        () -> WardConfig.hybridSuccessLootBonus, v -> WardConfig.hybridSuccessLootBonus = pFloat(v, WardConfig.hybridSuccessLootBonus)))));

        pages.add(new Page("Accessibility", List.of(
                Row.ofBool("Reduce motion", "stops idle movement and opening motion",
                        () -> WardConfig.accessibilityReduceMotion, v -> WardConfig.accessibilityReduceMotion = v),
                Row.ofBool("Reduce flashing", "replaces outages with a steady dim",
                        () -> WardConfig.accessibilityReduceFlashing, v -> WardConfig.accessibilityReduceFlashing = v),
                Row.ofBool("High contrast", "dark wells, bright glyphs and text",
                        () -> WardConfig.accessibilityHighContrast, v -> WardConfig.accessibilityHighContrast = v),
                Row.ofBool("Larger markers", "larger pixel/glyph strokes",
                        () -> WardConfig.accessibilityLargeMarkers, v -> WardConfig.accessibilityLargeMarkers = v),
                Row.ofBool("Text feedback", "OK / X feedback does not rely on colour",
                        () -> WardConfig.accessibilityColorIndependentFeedback, v -> WardConfig.accessibilityColorIndependentFeedback = v),
                Row.ofBool("Disable shake", "removes nonessential jolt/shiver effects",
                        () -> WardConfig.accessibilityDisableShake, v -> WardConfig.accessibilityDisableShake = v),
                Row.ofFloat("Animation intensity", "0 - 1 for nonessential GUI motion",
                        () -> WardConfig.accessibilityGuiAnimationIntensity, v -> WardConfig.accessibilityGuiAnimationIntensity = pFloat(v, WardConfig.accessibilityGuiAnimationIntensity)),
                Row.ofFloat("SFX volume", "0 - 1.5 for minigame UI sounds",
                        () -> WardConfig.accessibilitySfxVolume, v -> WardConfig.accessibilitySfxVolume = pFloat(v, WardConfig.accessibilitySfxVolume)),
                Row.ofPercent("Parallax meter reveal", "quality threshold for the meter; up to 95%",
                        () -> WardConfig.parallaxMeterRevealThreshold, v -> WardConfig.parallaxMeterRevealThreshold = pFloat(v, WardConfig.parallaxMeterRevealThreshold)))));

        pages.add(new Page("Dimension", List.of(
                Row.ofText("Dimension id", "edit/rename; editing default creates a new entry",
                        this::selectedDimensionKey, this::renameSelectedDimension),
                Row.ofFloat("Difficulty", "selected dimension",
                        () -> selectedDim().difficulty, v -> selectedDim().difficulty = pFloat(v, selectedDim().difficulty)),
                Row.ofFloat("Loot multiplier", "selected dimension",
                        () -> selectedDim().lootMultiplier, v -> selectedDim().lootMultiplier = pFloat(v, selectedDim().lootMultiplier)),
                Row.ofInt("Lives", "mistakes allowed",
                        () -> selectedDim().lives, v -> selectedDim().lives = pInt(v, selectedDim().lives)),
                Row.ofBool("Random game", "ignore single game when no pool exists",
                        () -> selectedDim().randomGame, v -> selectedDim().randomGame = v),
                Row.ofText("Single game", "minigame id used when pool is empty",
                        () -> selectedDim().minigame.id, WardConfigScreen::setSelectedSingleGame),
                Row.ofText("Pool", "pipe/comma separated minigame ids; empty = none",
                        WardConfigScreen::selectedPoolText, WardConfigScreen::setSelectedPoolText),
                Row.ofText("On failure", "reduce_loot / explode / spawn_guard / lock_forever",
                        () -> selectedDim().onFail.id, WardConfigScreen::setSelectedFailAction))));

        pages.add(new Page("Guardians", List.of(
                Row.ofText("Blacklist", "entity ids that may never spawn",
                        WardConfigScreen::selectedGuardianBlacklistText, WardConfigScreen::setSelectedGuardianBlacklistText),
                Row.ofText("Whitelist / spawn pool", "entity ids separated by commas; empty = stock pool",
                        WardConfigScreen::selectedGuardianPoolText, WardConfigScreen::setSelectedGuardianPoolText))));

        pages.add(new Page("Item overrides", List.of(
                Row.ofText("Item values", "id=value; id=value ... overrides auto scoring",
                        WardConfigScreen::itemOverridesText, WardConfigScreen::setItemOverridesText))));

        pages.add(new Page("Minigame tuning", List.of(
                Row.ofBool("Enabled", "whether this minigame can roll at all",
                        () -> selectedTuning().enabled, v -> selectedTuning().enabled = v),
                Row.ofFloat("Time scale", "1.0 keeps authored time",
                        () -> selectedTuning().timeScale, s -> selectedTuning().timeScale = pFloat(s, selectedTuning().timeScale)),
                Row.ofFloat("Speed scale", "1.0 keeps authored motion",
                        () -> selectedTuning().speedScale, s -> selectedTuning().speedScale = pFloat(s, selectedTuning().speedScale)),
                Row.ofFloat("Difficulty scale", "biases the authored difficulty",
                        () -> selectedTuning().difficultyScale, s -> selectedTuning().difficultyScale = pFloat(s, selectedTuning().difficultyScale)),
                Row.ofFloat("Input window", "wider or narrower timing/target windows",
                        () -> selectedTuning().inputWindowScale, s -> selectedTuning().inputWindowScale = pFloat(s, selectedTuning().inputWindowScale)),
                Row.ofFloat("Reward scale", "reward multiplier for this minigame",
                        () -> selectedTuning().rewardScale, s -> selectedTuning().rewardScale = pFloat(s, selectedTuning().rewardScale)))));
    }

    private MinigameType selectedType() {
        MinigameType[] types = MinigameType.values();
        if (tuningIndex < 0) tuningIndex = 0;
        if (tuningIndex >= types.length) tuningIndex = types.length - 1;
        return types[tuningIndex];
    }

    private WardConfig.GameTuning selectedTuning() {
        return WardConfig.tuning(selectedType());
    }

    private boolean onTuningPage() {
        return page >= 0 && page < pages.size() && "Minigame tuning".equals(pages.get(page).title());
    }

    private static String niceGameName(MinigameType type) {
        String s = type.id.replace('_', ' ');
        if (s.isEmpty()) return type.name();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private List<String> dimensionKeys() {
        List<String> keys = new ArrayList<>(WardConfig.DIMENSIONS.keySet());
        keys.add("default");
        return keys;
    }

    private String selectedDimensionKey() {
        List<String> keys = dimensionKeys();
        if (keys.isEmpty()) return "default";
        dimensionIndex = Math.max(0, Math.min(dimensionIndex, keys.size() - 1));
        return keys.get(dimensionIndex);
    }

    private DimSettings selectedDim() {
        String key = selectedDimensionKey();
        return "default".equals(key) ? WardConfig.fallback : WardConfig.DIMENSIONS.getOrDefault(key, WardConfig.fallback);
    }

    private void renameSelectedDimension(String raw) {
        String next = raw == null ? "" : raw.trim();
        if (next.isEmpty() || "default".equals(next)) return;
        String old = selectedDimensionKey();
        DimSettings value = selectedDim().copy();
        if (!"default".equals(old)) WardConfig.DIMENSIONS.remove(old);
        WardConfig.DIMENSIONS.put(next, value);
        List<String> keys = dimensionKeys();
        dimensionIndex = Math.max(0, keys.indexOf(next));
    }

    private boolean onDimensionPage() {
        String t = pages.get(page).title();
        return "Dimension".equals(t) || "Guardians".equals(t);
    }

    private static void setSelectedSingleGame(String text) {
        WardConfigScreen self = ACTIVE;
        if (self == null) return;
        MinigameType type = MinigameType.byId(text.trim(), self.selectedDim().minigame);
        self.selectedDim().minigame = type;
    }

    private static String selectedPoolText() {
        WardConfigScreen self = ACTIVE;
        if (self == null || self.selectedDim().pool == null) return "";
        StringBuilder b = new StringBuilder();
        for (MinigameType t : self.selectedDim().pool) {
            if (b.length() > 0) b.append('|');
            b.append(t.id);
        }
        return b.toString();
    }

    private static void setSelectedPoolText(String text) {
        WardConfigScreen self = ACTIVE;
        if (self == null) return;
        List<MinigameType> list = new ArrayList<>();
        for (String part : text.split("[|,;\\s]+")) {
            if (part.isBlank()) continue;
            MinigameType t = MinigameType.byId(part.trim(), null);
            if (t != null && !list.contains(t)) list.add(t);
        }
        self.selectedDim().pool = list.isEmpty() ? null : list.toArray(new MinigameType[0]);
        if (!list.isEmpty()) self.selectedDim().minigame = list.get(0);
    }

    private static void setSelectedFailAction(String text) {
        WardConfigScreen self = ACTIVE;
        if (self == null) return;
        self.selectedDim().onFail = DimSettings.FailAction.byId(text.trim(), self.selectedDim().onFail);
    }

    private static String selectedGuardianPoolText() {
        WardConfigScreen self = ACTIVE;
        if (self == null) return "";
        List<String> v = WardConfig.GUARDIAN_POOLS.get(self.selectedDimensionKey());
        return v == null ? "" : String.join(",", v);
    }

    private static void setSelectedGuardianPoolText(String text) {
        WardConfigScreen self = ACTIVE;
        if (self == null) return;
        putIdList(WardConfig.GUARDIAN_POOLS, self.selectedDimensionKey(), text);
    }

    private static String selectedGuardianBlacklistText() {
        WardConfigScreen self = ACTIVE;
        if (self == null) return "";
        List<String> v = WardConfig.GUARDIAN_BLACKLIST.get(self.selectedDimensionKey());
        return v == null ? "" : String.join(",", v);
    }

    private static void setSelectedGuardianBlacklistText(String text) {
        WardConfigScreen self = ACTIVE;
        if (self == null) return;
        putIdList(WardConfig.GUARDIAN_BLACKLIST, self.selectedDimensionKey(), text);
    }

    private static void putIdList(Map<String, List<String>> map, String key, String text) {
        List<String> list = new ArrayList<>();
        for (String raw : text.split("[,;\\s]+")) {
            String id = raw.trim();
            if (!id.isEmpty() && !list.contains(id)) list.add(id);
        }
        if (list.isEmpty()) map.remove(key); else map.put(key, list);
    }

    private static String itemOverridesText() {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, Integer> e : WardConfig.itemValues.entrySet()) {
            if (b.length() > 0) b.append("; ");
            b.append(e.getKey()).append('=').append(e.getValue());
        }
        return b.toString();
    }

    private static void setItemOverridesText(String text) {
        LinkedHashMap<String, Integer> parsed = new LinkedHashMap<>();
        for (String entry : text.split(";")) {
            int at = entry.lastIndexOf('=');
            if (at <= 0) continue;
            String id = entry.substring(0, at).trim();
            try {
                int value = Integer.parseInt(entry.substring(at + 1).trim());
                if (!id.isEmpty()) parsed.put(id, Math.max(0, value));
            } catch (NumberFormatException ignored) {}
        }
        WardConfig.itemValues.clear();
        WardConfig.itemValues.putAll(parsed);
    }

    private static WardConfigScreen ACTIVE;

    // ------------------------------------------------------------------ parsing

    private static int pInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static float pFloat(String s, float fallback) {
        try {
            return Float.parseFloat(s.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // ------------------------------------------------------------------ layout / navigation

    private int panelWidth() {
        return Math.min(MAX_PANEL_W, Math.max(300, width - 24));
    }

    private int panelHeight() {
        return Math.min(MAX_PANEL_H, Math.max(220, height - 24));
    }

    private int panelX() {
        return width / 2 - panelWidth() / 2;
    }

    private int panelY() {
        return height / 2 - panelHeight() / 2;
    }

    private boolean useSingleColumnLayout() {
        String t = pages.get(page).title();
        return panelWidth() < 520 || "Dimension".equals(t) || "Guardians".equals(t) || "Item overrides".equals(t);
    }

    private int rowsPerChunk() {
        int spacing = rowSpacing();
        int reserved = hasContextSelector() ? 194 : 170;
        int perColumn = Math.max(1, Math.min(SINGLE_ROWS_PER_CHUNK, (panelHeight() - reserved) / spacing + 1));
        if (useSingleColumnLayout()) return perColumn;
        return Math.min(NORMAL_ROWS_PER_CHUNK, perColumn * 2);
    }

    private int chunkCount() {
        int n = pages.get(page).rows().size();
        int per = Math.max(1, rowsPerChunk());
        return Math.max(1, (n + per - 1) / per);
    }

    private List<Row> visibleRows() {
        List<Row> all = pages.get(page).rows();
        int per = Math.max(1, rowsPerChunk());
        settingsChunk = Math.max(0, Math.min(settingsChunk, chunkCount() - 1));
        int from = Math.min(all.size(), settingsChunk * per);
        int to = Math.min(all.size(), from + per);
        return all.subList(from, to);
    }

    private int sidebarStep() {
        if (pages.isEmpty()) return 16;
        return Math.max(12, Math.min(16, (panelHeight() - 72) / pages.size()));
    }

    private boolean showSidebar() {
        return panelWidth() >= 600 && sidebarStep() >= 13;
    }

    private int contentLeft() {
        return panelX() + (showSidebar() ? 174 : 18);
    }

    private int contentRight() {
        return panelX() + panelWidth() - 18;
    }

    private int contentCenter() {
        return contentLeft() + (contentRight() - contentLeft()) / 2;
    }

    private int columnGap() {
        return 24;
    }

    private int fieldWidth() {
        if (useSingleColumnLayout()) return contentRight() - contentLeft();
        return (contentRight() - contentLeft() - columnGap()) / 2;
    }

    private int leftX() {
        return contentLeft();
    }

    private int rightX() {
        return contentLeft() + fieldWidth() + columnGap();
    }

    private int rowX(boolean rightColumn) {
        return useSingleColumnLayout() ? leftX() : (rightColumn ? rightX() : leftX());
    }

    private boolean hasContextSelector() {
        return onTuningPage() || onDimensionPage();
    }

    private int navY() {
        return panelY() + 29;
    }

    private int contextY() {
        return panelY() + 53;
    }

    private int chunkY() {
        return panelY() + (hasContextSelector() ? 77 : 53);
    }

    private int gridTop() {
        int top = panelY() + 78;
        if (hasContextSelector()) top += 24;
        if (chunkCount() > 1) top += 24;
        return top;
    }

    private int rowSpacing() {
        return panelHeight() < 360 ? 36 : 42;
    }

    private int rowY(int localIndex, int split) {
        return gridTop() + (localIndex % split) * rowSpacing() + 21;
    }

    private void clearProblemForNavigation() {
        if (!problem.startsWith("Invalid")) problem = "";
    }

    private void goToPage(int next) {
        if (!commitVisibleRows()) return;
        int n = pages.size();
        page = (next % n + n) % n;
        settingsChunk = 0;
        clearProblemForNavigation();
        init();
    }

    private void goToChunk(int next) {
        if (!commitVisibleRows()) return;
        int n = chunkCount();
        settingsChunk = (next % n + n) % n;
        clearProblemForNavigation();
        init();
    }

    private boolean validNumber(Row r, String raw) {
        String s = raw == null ? "" : raw.trim().replace(',', '.');
        if (r.kind == Kind.PERCENT && s.endsWith("%")) s = s.substring(0, s.length() - 1).trim();
        if (s.isEmpty()) return false;
        try {
            if (r.kind == Kind.INT) {
                Integer.parseInt(s);
                return true;
            }
            float value = Float.parseFloat(s);
            if (!Float.isFinite(value)) return false;
            if (r.kind == Kind.PERCENT && (value < 0f || value > 100f)) return false;
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    /**
     * Commits the currently visible controls to the in-memory config only.
     * Disk persistence happens only when Save is pressed. This means a player can
     * edit several categories/chunks and press Save once; Cancel/ESC reloads the
     * last saved JSON and discards all staged changes.
     */
    private boolean commitVisibleRows() {
        for (Row r : visibleRows()) {
            if ((r.kind == Kind.INT || r.kind == Kind.FLOAT || r.kind == Kind.PERCENT) && r.box != null
                    && !validNumber(r, r.box.getValue())) {
                problem = r.kind == Kind.PERCENT
                        ? "Invalid percentage for '" + r.label + "' (use 0 - 100)."
                        : "Invalid value for '" + r.label + "'.";
                return false;
            }
        }

        for (Row r : visibleRows()) {
            if (r.kind == Kind.BOOL) {
                r.writeBool.accept(r.value);
            } else if (r.kind == Kind.TEXT && r.box != null) {
                r.writeText.accept(r.box.getValue());
            } else if (r.box != null) {
                String raw = r.box.getValue().trim().replace(',', '.');
                if (r.kind == Kind.PERCENT) {
                    if (raw.endsWith("%")) raw = raw.substring(0, raw.length() - 1).trim();
                    raw = Float.toString(Float.parseFloat(raw) / 100.0f);
                }
                r.writeNumber.accept(raw);
            }
        }
        WardConfig.clampAll();
        return true;
    }

    @Override
    protected void init() {
        clearWidgets();
        settingsChunk = Math.max(0, Math.min(settingsChunk, chunkCount() - 1));

        int x = panelX(), y = panelY(), w = panelWidth();

        // Category navigation replaces the old 22-tab wall. Long category names
        // no longer truncate and the content area stays at a stable Y position.
        addRenderableWidget(Button.builder(Component.literal("<"), b -> goToPage(page - 1))
                .bounds(contentLeft(), navY(), 26, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> goToPage(page + 1))
                .bounds(contentRight() - 26, navY(), 26, 20).build());

        if (showSidebar()) {
            int sy = y + 39;
            int step = sidebarStep();
            for (int i = 0; i < pages.size(); i++) {
                final int target = i;
                Button categoryButton = Button.builder(Component.literal(pages.get(i).title()), b -> goToPage(target))
                        .bounds(x + 10, sy + i * step, 150, Math.max(12, step - 1)).build();
                categoryButton.active = i != page;
                addRenderableWidget(categoryButton);
            }
        }

        if (onTuningPage()) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                if (!commitVisibleRows()) return;
                tuningIndex = (tuningIndex + MinigameType.values().length - 1) % MinigameType.values().length;
                settingsChunk = 0;
                problem = "";
                init();
            }).bounds(contentLeft(), contextY(), 26, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                if (!commitVisibleRows()) return;
                tuningIndex = (tuningIndex + 1) % MinigameType.values().length;
                settingsChunk = 0;
                problem = "";
                init();
            }).bounds(contentRight() - 26, contextY(), 26, 20).build());
        }

        if (onDimensionPage()) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                if (!commitVisibleRows()) return;
                int n = Math.max(1, dimensionKeys().size());
                dimensionIndex = (dimensionIndex + n - 1) % n;
                settingsChunk = 0;
                problem = "";
                init();
            }).bounds(contentLeft(), contextY(), 26, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                if (!commitVisibleRows()) return;
                int n = Math.max(1, dimensionKeys().size());
                dimensionIndex = (dimensionIndex + 1) % n;
                settingsChunk = 0;
                problem = "";
                init();
            }).bounds(contentRight() - 26, contextY(), 26, 20).build());
        }

        if (chunkCount() > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> goToChunk(settingsChunk - 1))
                    .bounds(contentLeft(), chunkY(), 26, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> goToChunk(settingsChunk + 1))
                    .bounds(contentRight() - 26, chunkY(), 26, 20).build());
        }

        List<Row> rows = visibleRows();
        int split = useSingleColumnLayout() ? rows.size() : Math.max(1, (rows.size() + 1) / 2);
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int xField = rowX(!useSingleColumnLayout() && i >= split);
            int yField = rowY(i, split);

            if (r.kind == Kind.BOOL) {
                r.value = r.readBool.getAsBoolean();
                r.toggle = Button.builder(toggleLabel(r.label, r.value), b -> {
                    r.value = !r.value;
                    b.setMessage(toggleLabel(r.label, r.value));
                }).bounds(xField, yField - 1, fieldWidth(), 20).build();
                addRenderableWidget(r.toggle);
            } else {
                EditBox box = new EditBox(font, xField, yField, fieldWidth(), 18, Component.literal(r.label));
                box.setMaxLength(r.kind == Kind.TEXT ? 2048 : 16);
                box.setValue(r.initial());
                r.box = box;
                addRenderableWidget(box);
            }
        }

        int buttonY = y + panelHeight() - 29;
        int bw = 98;
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> save())
                .bounds(width / 2 - bw - 4, buttonY, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> discardAndClose())
                .bounds(width / 2 + 4, buttonY, bw, 20).build());
    }

    private Component toggleLabel(String name, boolean on) {
        return Component.literal(name + ": " + (on ? "ON" : "OFF"));
    }

    private void save() {
        if (!commitVisibleRows()) return;
        WardConfig.clampAll();
        WardConfig.save();
        problem = "Saved to config/wardbound.json.";
        init();
    }

    private void discardAndClose() {
        WardConfig.load();
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        discardAndClose();
    }

    @Override
    public void removed() {
        if (ACTIVE == this) ACTIVE = null;
        super.removed();
    }

    // ------------------------------------------------------------------ render

    private String fit(String text, int maxWidth) {
        if (text == null) return "";
        if (font.width(text) <= maxWidth) return text;
        String ellipsis = "...";
        int target = Math.max(0, maxWidth - font.width(ellipsis));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            String next = out.toString() + text.charAt(i);
            if (font.width(next) > target) break;
            out.append(text.charAt(i));
        }
        return out + ellipsis;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);

        int x = panelX(), y = panelY(), w = panelWidth(), h = panelHeight();
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, COL_PANEL_EDGE);
        g.fill(x, y, x + w, y + h, COL_BG);
        g.fill(x, y, x + w, y + 22, COL_HEADER);
        g.fill(x, y + 21, x + w, y + 22, COL_BRASS);

        g.drawString(font, "Wardbound Configuration", x + 10, y + 7, COL_TEXT, false);
        String count = (page + 1) + "/" + pages.size();
        g.drawString(font, count, x + w - 10 - font.width(count), y + 7, COL_FAINT, false);

        if (showSidebar()) {
            g.fill(x + 166, y + 24, x + 167, y + h - 35, COL_BRASS);
            g.drawString(font, "Categories", x + 10, y + 27, COL_FAINT, false);
        }

        String category = pages.get(page).title();
        String categoryLine = category + "  ·  category " + (page + 1) + " of " + pages.size();
        String categoryShown = fit(categoryLine, contentRight() - contentLeft() - 70);
        g.drawString(font, categoryShown, contentCenter() - font.width(categoryShown) / 2,
                navY() + 6, COL_BRASS_LIT, false);

        if (onTuningPage()) {
            String label = "Minigame: " + niceGameName(selectedType());
            String shown = fit(label, contentRight() - contentLeft() - 70);
            g.drawString(font, shown, contentCenter() - font.width(shown) / 2, contextY() + 6, COL_BRASS_LIT, false);
        } else if (onDimensionPage()) {
            String label = "Dimension: " + selectedDimensionKey();
            String shown = fit(label, contentRight() - contentLeft() - 70);
            g.drawString(font, shown, contentCenter() - font.width(shown) / 2, contextY() + 6, COL_BRASS_LIT, false);
        }

        if (chunkCount() > 1) {
            int total = pages.get(page).rows().size();
            int per = rowsPerChunk();
            int from = settingsChunk * per + 1;
            int to = Math.min(total, from + per - 1);
            String chunk = "Settings " + from + "-" + to + " of " + total;
            g.drawString(font, chunk, contentCenter() - font.width(chunk) / 2, chunkY() + 6, COL_NOTE, false);
        }

        List<Row> rows = visibleRows();
        int split = useSingleColumnLayout() ? rows.size() : Math.max(1, (rows.size() + 1) / 2);
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int cx = rowX(!useSingleColumnLayout() && i >= split);
            int cy = rowY(i, split);
            if (r.kind != Kind.BOOL) {
                g.drawString(font, fit(r.label, fieldWidth()), cx, cy - 21, COL_TEXT, false);
            }
            if (r.note != null && !r.note.isBlank()) {
                g.drawString(font, fit(r.note, fieldWidth()), cx, cy - 10, COL_NOTE, false);
            }
        }

        String footer = "Changes are staged until Save. Cancel / ESC discards them.";
        g.drawString(font, fit(footer, w - 20), x + 10, y + h - 43, COL_FAINT, false);
        if (!problem.isEmpty()) {
            g.drawString(font, fit(problem, w - 20), x + 10, y + h - 54,
                    problem.startsWith("Saved") ? COL_NOTE : COL_BAD, false);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }
}
