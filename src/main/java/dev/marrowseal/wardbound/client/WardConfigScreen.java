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

    private static final int PANEL_W = 440;
    private static final int PANEL_H = 476;
    private static final int COL_W = 182;
    private static final int ROW_H = 33;
    private static final int ROWS = 10;

    private enum Kind { INT, FLOAT, BOOL, TEXT }

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
                case BOOL -> "";
                case TEXT -> readText.get();
            };
        }

        private static String trim(double d) {
            String s = String.format("%.3f", d);
            while (s.contains(".") && (s.endsWith("0") || s.endsWith("."))) {
                s = s.substring(0, s.length() - 1);
            }
            return s;
        }
    }

    private record Page(String title, List<Row> rows) {
    }

    private final List<Page> pages = new ArrayList<>();
    private int page;
    private String problem = "";
    private int tuningIndex = MinigameType.CONSTELLATION.ordinal();
    private int dimensionIndex = 0;
    private EditBox dimensionIdBox;

    public WardConfigScreen(Screen parent) {
        super(Component.literal("Wardbound"));
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
        pages.add(new Page("Locking", List.of(
                Row.ofInt("Lock threshold", "below this uses the low chance",
                        () -> WardConfig.lockThreshold, s -> WardConfig.lockThreshold = pInt(s, WardConfig.lockThreshold)),
                Row.ofFloat("Ward chance, poor", "0 - 1",
                        () -> WardConfig.lockChanceLow, s -> WardConfig.lockChanceLow = pFloat(s, WardConfig.lockChanceLow)),
                Row.ofFloat("Ward chance, rich", "0 - 1",
                        () -> WardConfig.lockChanceHigh, s -> WardConfig.lockChanceHigh = pFloat(s, WardConfig.lockChanceHigh)),
                Row.ofInt("Min value to ward", "junk chests below this stay open",
                        () -> WardConfig.minValueToLock, s -> WardConfig.minValueToLock = pInt(s, WardConfig.minValueToLock)),
                Row.ofFloat("Loot kept on a loss", "0.4 = lose 60% of worth",
                        () -> WardConfig.failLootMultiplier, s -> WardConfig.failLootMultiplier = pFloat(s, WardConfig.failLootMultiplier)),
                Row.ofFloat("Global roll multiplier", "applied to every win",
                        () -> WardConfig.globalRollMultiplier, s -> WardConfig.globalRollMultiplier = pFloat(s, WardConfig.globalRollMultiplier)),
                Row.ofBool("Confirm click", "right click once to read, again to begin",
                        () -> WardConfig.requireConfirmClick, v -> WardConfig.requireConfirmClick = v),
                Row.ofBool("Auto item values", "score modded items on their own",
                        () -> WardConfig.autoItemValues, v -> WardConfig.autoItemValues = v))));

        pages.add(new Page("Feel", List.of(
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
                Row.ofFloat("Streak difficulty step", "per lock in a run",
                        () -> WardConfig.streakDifficultyPerLock, s -> WardConfig.streakDifficultyPerLock = pFloat(s, WardConfig.streakDifficultyPerLock)),
                Row.ofFloat("Streak difficulty cap", "ceiling on the above",
                        () -> WardConfig.streakDifficultyCap, s -> WardConfig.streakDifficultyCap = pFloat(s, WardConfig.streakDifficultyCap)))));

        pages.add(new Page("Rewards", List.of(
                Row.ofFloat("Streak bonus per lock", "loot, per win in a run",
                        () -> WardConfig.streakBonusPerLock, s -> WardConfig.streakBonusPerLock = pFloat(s, WardConfig.streakBonusPerLock)),
                Row.ofFloat("Streak bonus cap", "ceiling on the above",
                        () -> WardConfig.streakBonusCap, s -> WardConfig.streakBonusCap = pFloat(s, WardConfig.streakBonusCap)),
                Row.ofFloat("Pulse intensity", "how hard the beating lock throbs",
                        () -> WardConfig.pulseIntensity, s -> WardConfig.pulseIntensity = pFloat(s, WardConfig.pulseIntensity)),
                Row.ofFloat("Eye drop chance", "once the threshold is beaten",
                        () -> WardConfig.eyeDropChance, s -> WardConfig.eyeDropChance = pFloat(s, WardConfig.eyeDropChance)))));

        pages.add(new Page("Charms", List.of(
                Row.ofInt("Key: extra lives", "durability 4",
                        () -> WardConfig.keyBonusLives, s -> WardConfig.keyBonusLives = pInt(s, WardConfig.keyBonusLives)),
                Row.ofInt("Heart: extra lives", "durability 2",
                        () -> WardConfig.heartBonusLives, s -> WardConfig.heartBonusLives = pInt(s, WardConfig.heartBonusLives)),
                Row.ofFloat("Heart: loot bonus", "1.25 = +25%",
                        () -> WardConfig.heartLootBonus, s -> WardConfig.heartLootBonus = pFloat(s, WardConfig.heartLootBonus)),
                Row.ofFloat("Key drop chance", "per lock beaten",
                        () -> WardConfig.keyDropChance, s -> WardConfig.keyDropChance = pFloat(s, WardConfig.keyDropChance)),
                Row.ofFloat("Heart drop chance", "scaled by chest worth",
                        () -> WardConfig.heartDropChance, s -> WardConfig.heartDropChance = pFloat(s, WardConfig.heartDropChance)),
                Row.ofFloat("Relic drop chance", "shim and cyclopean lens",
                        () -> WardConfig.relicDropChance, s -> WardConfig.relicDropChance = pFloat(s, WardConfig.relicDropChance)),
                Row.ofInt("Eye cooldown, ticks", "3600 = three minutes",
                        () -> WardConfig.eyeCooldownTicks, s -> WardConfig.eyeCooldownTicks = pInt(s, WardConfig.eyeCooldownTicks)),
                Row.ofInt("Ember seconds", "added to the ward clock",
                        () -> WardConfig.emberSeconds, s -> WardConfig.emberSeconds = pInt(s, WardConfig.emberSeconds)),
                Row.ofFloat("Ember threshold", "Nether chests must beat this",
                        () -> WardConfig.emberLootThreshold, s -> WardConfig.emberLootThreshold = pFloat(s, WardConfig.emberLootThreshold)),
                Row.ofInt("Ember after wins", "resolved ward gate for Slow Ember eligibility",
                        () -> WardConfig.emberAfterBeaten, s -> WardConfig.emberAfterBeaten = pInt(s, WardConfig.emberAfterBeaten)),
                Row.ofFloat("Eye loot threshold", "End chests must beat this",
                        () -> WardConfig.eyeLootThreshold, s -> WardConfig.eyeLootThreshold = pFloat(s, WardConfig.eyeLootThreshold)),
                Row.ofInt("Savant after wins", "resolved ward gate for Savant eligibility",
                        () -> WardConfig.eyeAfterBeaten, s -> WardConfig.eyeAfterBeaten = pInt(s, WardConfig.eyeAfterBeaten)))));

        pages.add(new Page("The offer", List.of(
                Row.ofBool("Offer enabled", "re-seal a beaten container",
                        () -> WardConfig.temptEnabled, v -> WardConfig.temptEnabled = v),
                Row.ofFloat("Offer chance", "per lock beaten",
                        () -> WardConfig.temptChance, s -> WardConfig.temptChance = pFloat(s, WardConfig.temptChance)),
                Row.ofFloat("Offer multiplier", "compounding, per re-seal",
                        () -> WardConfig.temptMultiplier, s -> WardConfig.temptMultiplier = pFloat(s, WardConfig.temptMultiplier)),
                Row.ofInt("Offer max depth", "how often one chest may be wound",
                        () -> WardConfig.temptMaxDepth, s -> WardConfig.temptMaxDepth = pInt(s, WardConfig.temptMaxDepth)),
                Row.ofFloat("Offer difficulty step", "per re-seal",
                        () -> WardConfig.temptDifficultyPerDepth, s -> WardConfig.temptDifficultyPerDepth = pFloat(s, WardConfig.temptDifficultyPerDepth)),
                Row.ofFloat("Grudge difficulty", "per loss on one container",
                        () -> WardConfig.spiteDifficulty, s -> WardConfig.spiteDifficulty = pFloat(s, WardConfig.spiteDifficulty)),
                Row.ofFloat("Grudge loot", "per loss on one container",
                        () -> WardConfig.spiteLoot, s -> WardConfig.spiteLoot = pFloat(s, WardConfig.spiteLoot)),
                Row.ofInt("Grudge max", "how far one container escalates",
                        () -> WardConfig.spiteMax, s -> WardConfig.spiteMax = pInt(s, WardConfig.spiteMax)))));

        pages.add(new Page("Consequences", List.of(
                Row.ofFloat("Guardian chance", "on a lost lock",
                        () -> WardConfig.guardianChance, s -> WardConfig.guardianChance = pFloat(s, WardConfig.guardianChance)),
                Row.ofInt("Guardian max count", "scaled down by chest worth",
                        () -> WardConfig.guardianMaxCount, s -> WardConfig.guardianMaxCount = pInt(s, WardConfig.guardianMaxCount)),
                Row.ofInt("Sulk, seconds", "how long a beaten chest stays shut",
                        () -> WardConfig.guardianSealSeconds, s -> WardConfig.guardianSealSeconds = pInt(s, WardConfig.guardianSealSeconds)),
                Row.ofBool("Guardian dimension lock", "Nether/End mobs stay in their native dimension",
                        () -> WardConfig.guardianDimensionLock, v -> WardConfig.guardianDimensionLock = v),
                Row.ofBool("Guardian progression lock", "requires visiting Nether/End before those tiers unlock",
                        () -> WardConfig.guardianProgressionLock, v -> WardConfig.guardianProgressionLock = v),
                Row.ofFloat("Wild pool chance", "ignore curated list, but still obey guardian locks",
                        () -> WardConfig.guardianWildChance, s -> WardConfig.guardianWildChance = pFloat(s, WardConfig.guardianWildChance)),
                Row.ofInt("Horde size", "when it sends a crowd",
                        () -> WardConfig.hordeSize, s -> WardConfig.hordeSize = pInt(s, WardConfig.hordeSize)),
                Row.ofFloat("Burn chance", "a lost ward takes the contents",
                        () -> WardConfig.failBurnChance, s -> WardConfig.failBurnChance = pFloat(s, WardConfig.failBurnChance)),
                Row.ofFloat("Burn floor", "least that can survive",
                        () -> WardConfig.failBurnFloor, s -> WardConfig.failBurnFloor = pFloat(s, WardConfig.failBurnFloor)),
                Row.ofFloat("Burn ceiling", "most that can survive",
                        () -> WardConfig.failBurnCeiling, s -> WardConfig.failBurnCeiling = pFloat(s, WardConfig.failBurnCeiling)))));

        pages.add(new Page("The long game", List.of(
                Row.ofInt("Tier step", "seals per tier, 0 disables",
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
                Row.ofFloat("Watcher max chance", "never certain",
                        () -> WardConfig.watcherMaxChance, s -> WardConfig.watcherMaxChance = pFloat(s, WardConfig.watcherMaxChance)))));

        pages.add(new Page("Endgame", List.of(
                Row.ofInt("Verdict after", "its seals before it judges you",
                        () -> WardConfig.verdictAfter, s -> WardConfig.verdictAfter = pInt(s, WardConfig.verdictAfter)),
                Row.ofInt("Verdict threshold", "regard needed for favour",
                        () -> WardConfig.verdictThreshold, s -> WardConfig.verdictThreshold = pInt(s, WardConfig.verdictThreshold)),
                Row.ofFloat("Contempt difficulty", "on that house's seals",
                        () -> WardConfig.contemptDifficulty, s -> WardConfig.contemptDifficulty = pFloat(s, WardConfig.contemptDifficulty)),
                Row.ofFloat("Contempt loot", "and what it pays",
                        () -> WardConfig.contemptLoot, s -> WardConfig.contemptLoot = pFloat(s, WardConfig.contemptLoot)),
                Row.ofFloat("Unsigned chance", "a seal with no maker",
                        () -> WardConfig.unsignedChance, s -> WardConfig.unsignedChance = pFloat(s, WardConfig.unsignedChance)),
                Row.ofFloat("Unsigned, End factor", "how much likelier there",
                        () -> WardConfig.unsignedEndFactor, s -> WardConfig.unsignedEndFactor = pFloat(s, WardConfig.unsignedEndFactor)),
                Row.ofFloat("Unsigned loot", "what one is worth",
                        () -> WardConfig.unsignedLoot, s -> WardConfig.unsignedLoot = pFloat(s, WardConfig.unsignedLoot)),
                Row.ofInt("Gauntlet after", "unsigned seals before threes",
                        () -> WardConfig.gauntletAfter, s -> WardConfig.gauntletAfter = pInt(s, WardConfig.gauntletAfter)),
                Row.ofBool("Cthulhu ward", "ultra-rare singular ward",
                        () -> WardConfig.cthulhuWardsEnabled, v -> WardConfig.cthulhuWardsEnabled = v),
                Row.ofFloat("Cthulhu chance", "singular ward rarity",
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
                Row.ofFloat("Eldritch chance", "chance for an elite ward",
                        () -> WardConfig.eldritchWardChance, s -> WardConfig.eldritchWardChance = pFloat(s, WardConfig.eldritchWardChance)),
                Row.ofInt("Eldritch stages", "locks in the elite chain",
                        () -> WardConfig.eldritchWardStages, s -> WardConfig.eldritchWardStages = pInt(s, WardConfig.eldritchWardStages)),
                Row.ofFloat("Eldritch loot", "final payout multiplier",
                        () -> WardConfig.eldritchWardLoot, s -> WardConfig.eldritchWardLoot = pFloat(s, WardConfig.eldritchWardLoot)),
                Row.ofBool("Possessed wards", "rare single corrupted lock",
                        () -> WardConfig.possessedWardsEnabled, v -> WardConfig.possessedWardsEnabled = v),
                Row.ofFloat("Possessed chance", "very rare one-lock mutation",
                        () -> WardConfig.possessedWardChance, s -> WardConfig.possessedWardChance = pFloat(s, WardConfig.possessedWardChance)),
                Row.ofFloat("Possessed loot", "reward multiplier",
                        () -> WardConfig.possessedWardLoot, s -> WardConfig.possessedWardLoot = pFloat(s, WardConfig.possessedWardLoot)),
                Row.ofBool("Ward afflictions", "persistent named ward afflictions",
                        () -> WardConfig.wardAfflictionsEnabled, v -> WardConfig.wardAfflictionsEnabled = v),
                Row.ofFloat("Affliction chance", "chance for a new ward affliction",
                        () -> WardConfig.wardAfflictionChance, s -> WardConfig.wardAfflictionChance = pFloat(s, WardConfig.wardAfflictionChance)),
                Row.ofBool("Living wards", "old mutated wards may awaken",
                        () -> WardConfig.livingWardsEnabled, v -> WardConfig.livingWardsEnabled = v),
                Row.ofFloat("Living chance", "chance once mutation threshold is met",
                        () -> WardConfig.livingWardChance, s -> WardConfig.livingWardChance = pFloat(s, WardConfig.livingWardChance)),
                Row.ofFloat("Living loot", "reward multiplier for living wards",
                        () -> WardConfig.livingWardLoot, s -> WardConfig.livingWardLoot = pFloat(s, WardConfig.livingWardLoot)),
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
                Row.ofInt("Normal cards after", "ward wins before ordinary post-chest card hands can appear",
                        () -> WardConfig.normalCardsAfterBeaten, s -> WardConfig.normalCardsAfterBeaten = pInt(s, WardConfig.normalCardsAfterBeaten)),
                Row.ofInt("Field cards after", "ward wins before hostile mobs can drop sealed cards",
                        () -> WardConfig.fieldCardAfterBeaten, s -> WardConfig.fieldCardAfterBeaten = pInt(s, WardConfig.fieldCardAfterBeaten)),
                Row.ofFloat("Field card drop", "eligible hostile kill chance; 0.0015 = 0.15%",
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
                        () -> WardConfig.deathCardsAfterBeaten, s -> WardConfig.deathCardsAfterBeaten = pInt(s, WardConfig.deathCardsAfterBeaten)),
                Row.ofInt("Mercy after", "losses before mercy can appear",
                        () -> WardConfig.mercyAfterLosses, s -> WardConfig.mercyAfterLosses = pInt(s, WardConfig.mercyAfterLosses)),
                Row.ofFloat("Mercy chance", "chance on an eligible retry",
                        () -> WardConfig.mercyChance, s -> WardConfig.mercyChance = pFloat(s, WardConfig.mercyChance)),
                Row.ofFloat("Mercy loot", "reward multiplier during mercy",
                        () -> WardConfig.mercyLoot, s -> WardConfig.mercyLoot = pFloat(s, WardConfig.mercyLoot)),
                Row.ofFloat("Perfect relic", "Ward Splinter chance on Perfect",
                        () -> WardConfig.perfectRelicChance, s -> WardConfig.perfectRelicChance = pFloat(s, WardConfig.perfectRelicChance)),
                Row.ofFloat("Eldritch relic", "Eldritch Shard chance on Perfect finale",
                        () -> WardConfig.eldritchRelicChance, s -> WardConfig.eldritchRelicChance = pFloat(s, WardConfig.eldritchRelicChance)),
                Row.ofBool("Master rivalries", "houses react to rival mastery",
                        () -> WardConfig.masterRivalriesEnabled, v -> WardConfig.masterRivalriesEnabled = v),
                Row.ofInt("Rivalry after", "rival familiarity threshold",
                        () -> WardConfig.masterRivalryAfter, s -> WardConfig.masterRivalryAfter = pInt(s, WardConfig.masterRivalryAfter)),
                Row.ofFloat("Rivalry difficulty", "reactive difficulty multiplier",
                        () -> WardConfig.masterRivalryDifficulty, s -> WardConfig.masterRivalryDifficulty = pFloat(s, WardConfig.masterRivalryDifficulty)),
                Row.ofFloat("Rivalry loot", "reactive reward multiplier",
                        () -> WardConfig.masterRivalryLoot, s -> WardConfig.masterRivalryLoot = pFloat(s, WardConfig.masterRivalryLoot)),
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
                Row.ofFloat("Chain chance", "per ward beaten",
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

        pages.add(new Page("The attention", List.of(
                Row.ofInt("Gauntlet stages", "locks in a row",
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
                Row.ofFloat("Re-seal chance", "emptied chests close again",
                        () -> WardConfig.attentionResealChance, s -> WardConfig.attentionResealChance = pFloat(s, WardConfig.attentionResealChance)),
                Row.ofFloat("Horde on a win", "winning is not a reprieve",
                        () -> WardConfig.attentionHordeChance, s -> WardConfig.attentionHordeChance = pFloat(s, WardConfig.attentionHordeChance)))));


        pages.add(new Page("Hands & debts", List.of(
                Row.ofBool("Master signatures", "bias one existing quirk slot",
                        () -> WardConfig.masterSignaturesEnabled, v -> WardConfig.masterSignaturesEnabled = v),
                Row.ofFloat("Signature bias", "0 - 1, never adds a quirk",
                        () -> WardConfig.masterSignatureBiasChance, s -> WardConfig.masterSignatureBiasChance = pFloat(s, WardConfig.masterSignatureBiasChance)),
                Row.ofInt("Reveal hand after", "familiarity threshold",
                        () -> WardConfig.masterSignatureRevealAfter, s -> WardConfig.masterSignatureRevealAfter = pInt(s, WardConfig.masterSignatureRevealAfter)),
                Row.ofInt("Know hand after", "second familiarity threshold",
                        () -> WardConfig.masterSignatureKnowAfter, s -> WardConfig.masterSignatureKnowAfter = pInt(s, WardConfig.masterSignatureKnowAfter)),
                Row.ofBool("Forbidden bargains", "rare post-ward card choice",
                        () -> WardConfig.forbiddenBargainsEnabled, v -> WardConfig.forbiddenBargainsEnabled = v),
                Row.ofFloat("Bargain chance", "per eligible ward",
                        () -> WardConfig.forbiddenBargainChance, s -> WardConfig.forbiddenBargainChance = pFloat(s, WardConfig.forbiddenBargainChance)),
                Row.ofInt("Bargain cards", "ordinary hands may deal 2 - 4",
                        () -> WardConfig.forbiddenBargainMaxOffers, s -> WardConfig.forbiddenBargainMaxOffers = pInt(s, WardConfig.forbiddenBargainMaxOffers)),
                Row.ofFloat("Borrowed Breath", "flat loot addition",
                        () -> WardConfig.bargainBorrowedBreathReward, s -> WardConfig.bargainBorrowedBreathReward = pFloat(s, WardConfig.bargainBorrowedBreathReward)),
                Row.ofFloat("Debt of Iron", "flat loot addition",
                        () -> WardConfig.bargainIronDebtReward, s -> WardConfig.bargainIronDebtReward = pFloat(s, WardConfig.bargainIronDebtReward)),
                Row.ofFloat("Watching Mark", "flat loot addition",
                        () -> WardConfig.bargainWatchingMarkReward, s -> WardConfig.bargainWatchingMarkReward = pFloat(s, WardConfig.bargainWatchingMarkReward)))));

        pages.add(new Page("Advanced", List.of(
                Row.ofFloat("Ward clock, base", "seconds before value scaling",
                        () -> WardConfig.wardClockBaseSeconds, s -> WardConfig.wardClockBaseSeconds = pFloat(s, WardConfig.wardClockBaseSeconds)),
                Row.ofFloat("Ward clock, value", "seconds added per chest worth",
                        () -> WardConfig.wardClockValueSeconds, s -> WardConfig.wardClockValueSeconds = pFloat(s, WardConfig.wardClockValueSeconds)),
                Row.ofFloat("Clock difficulty", "time returned to harder locks",
                        () -> WardConfig.wardClockDifficultyCompensation, s -> WardConfig.wardClockDifficultyCompensation = pFloat(s, WardConfig.wardClockDifficultyCompensation)),
                Row.ofFloat("Clock minimum", "hard floor in seconds",
                        () -> WardConfig.wardClockMinimumSeconds, s -> WardConfig.wardClockMinimumSeconds = pFloat(s, WardConfig.wardClockMinimumSeconds)),
                Row.ofFloat("Heart ward seconds", "extra time if the heart intervenes",
                        () -> WardConfig.heartWardSeconds, s -> WardConfig.heartWardSeconds = pFloat(s, WardConfig.heartWardSeconds)),
                Row.ofFloat("Hurried time scale", "global hurried lock time multiplier",
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
                Row.ofFloat("Low-value quirk chance", "0 - 1",
                        () -> WardConfig.quirkLowValueChance, s -> WardConfig.quirkLowValueChance = pFloat(s, WardConfig.quirkLowValueChance)),
                Row.ofInt("Two anomalies after", "value threshold for a second anomaly",
                        () -> WardConfig.anomalyTwoThreshold, s -> WardConfig.anomalyTwoThreshold = pInt(s, WardConfig.anomalyTwoThreshold)),
                Row.ofFloat("Hurried chance", "0 - 1",
                        () -> WardConfig.anomalyHurriedChance, s -> WardConfig.anomalyHurriedChance = pFloat(s, WardConfig.anomalyHurriedChance)),
                Row.ofFloat("Shrouded chance", "0 - 1",
                        () -> WardConfig.anomalyShroudedChance, s -> WardConfig.anomalyShroudedChance = pFloat(s, WardConfig.anomalyShroudedChance)),
                Row.ofFloat("Guttering chance", "0 - 1",
                        () -> WardConfig.anomalyGutteringChance, s -> WardConfig.anomalyGutteringChance = pFloat(s, WardConfig.anomalyGutteringChance)),
                Row.ofFloat("Unlit chance", "0 - 1",
                        () -> WardConfig.anomalyUnlitChance, s -> WardConfig.anomalyUnlitChance = pFloat(s, WardConfig.anomalyUnlitChance)),
                Row.ofFloat("Spent chance", "starts one stage down but costs a life",
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
                Row.ofFloat("Lens true-name chance", "0 - 1",
                        () -> WardConfig.lensTrueNameChance, s -> WardConfig.lensTrueNameChance = pFloat(s, WardConfig.lensTrueNameChance)),
                Row.ofInt("Slow ember seconds", "extra clock seconds added",
                        () -> WardConfig.emberSeconds, s -> WardConfig.emberSeconds = pInt(s, WardConfig.emberSeconds)),
                Row.ofFloat("Ember loot threshold", "Nether chest value needed to appear",
                        () -> WardConfig.emberLootThreshold, s -> WardConfig.emberLootThreshold = pFloat(s, WardConfig.emberLootThreshold)),
                Row.ofInt("Ember after wins", "resolved ward gate before Nether chests may drop it",
                        () -> WardConfig.emberAfterBeaten, s -> WardConfig.emberAfterBeaten = pInt(s, WardConfig.emberAfterBeaten)),
                Row.ofFloat("Ember drop chance", "0 - 1",
                        () -> WardConfig.emberDropChance, s -> WardConfig.emberDropChance = pFloat(s, WardConfig.emberDropChance)),
                Row.ofFloat("Guardian wild chance", "hordes may pick eligible hostile mobs",
                        () -> WardConfig.guardianWildChance, s -> WardConfig.guardianWildChance = pFloat(s, WardConfig.guardianWildChance)),
                Row.ofInt("Horde size", "mobs in a sent horde",
                        () -> WardConfig.hordeSize, s -> WardConfig.hordeSize = pInt(s, WardConfig.hordeSize)),
                Row.ofFloat("Card hover lift", "visual lift in pixels",
                        () -> WardConfig.bargainCardHoverLift, s -> WardConfig.bargainCardHoverLift = pFloat(s, WardConfig.bargainCardHoverLift)),
                Row.ofFloat("Card anim speed", "hover/settle animation speed",
                        () -> WardConfig.bargainCardAnimSpeed, s -> WardConfig.bargainCardAnimSpeed = pFloat(s, WardConfig.bargainCardAnimSpeed)),
                Row.ofInt("Ambience period", "ticks between nearby hum checks",
                        () -> WardConfig.ambiencePeriodTicks, s -> WardConfig.ambiencePeriodTicks = pInt(s, WardConfig.ambiencePeriodTicks)),
                Row.ofFloat("Ambience range", "blocks",
                        () -> WardConfig.ambienceRange, s -> WardConfig.ambienceRange = pFloat(s, WardConfig.ambienceRange)))));

        pages.add(new Page("Director", List.of(
                Row.ofBool("Anti-repetition", "de-weight recently played locks",
                        () -> WardConfig.antiRepetitionEnabled, v -> WardConfig.antiRepetitionEnabled = v),
                Row.ofInt("Recent window", "1 - 8 resolved locks remembered",
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
                Row.ofFloat("Clean performance", "minimum 0 - 1",
                        () -> WardConfig.cleanWinPerformanceThreshold, v -> WardConfig.cleanWinPerformanceThreshold = pFloat(v, WardConfig.cleanWinPerformanceThreshold)),
                Row.ofFloat("Perfect performance", "minimum 0 - 1",
                        () -> WardConfig.perfectWinPerformanceThreshold, v -> WardConfig.perfectWinPerformanceThreshold = pFloat(v, WardConfig.perfectWinPerformanceThreshold)),
                Row.ofFloat("Clean time left", "fraction of ward clock",
                        () -> WardConfig.cleanWinTimeRemaining, v -> WardConfig.cleanWinTimeRemaining = pFloat(v, WardConfig.cleanWinTimeRemaining)),
                Row.ofFloat("Perfect time left", "fraction of ward clock",
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
                Row.ofFloat("Deception base", "base probability after unlock",
                        () -> WardConfig.deceptionBaseChance, v -> WardConfig.deceptionBaseChance = pFloat(v, WardConfig.deceptionBaseChance)),
                Row.ofFloat("Deception cap", "maximum natural probability",
                        () -> WardConfig.deceptionMaxChance, v -> WardConfig.deceptionMaxChance = pFloat(v, WardConfig.deceptionMaxChance)),
                Row.ofBool("Hybrid rounds", "rare cross-discipline borrowed mechanisms",
                        () -> WardConfig.hybridRoundsEnabled, v -> WardConfig.hybridRoundsEnabled = v),
                Row.ofInt("Hybrid unlock", "resolved wards before hybrid rounds can appear",
                        () -> WardConfig.hybridUnlockAfter, v -> WardConfig.hybridUnlockAfter = pInt(v, WardConfig.hybridUnlockAfter)),
                Row.ofFloat("Hybrid base", "base probability after unlock",
                        () -> WardConfig.hybridBaseChance, v -> WardConfig.hybridBaseChance = pFloat(v, WardConfig.hybridBaseChance)),
                Row.ofFloat("Hybrid cap", "maximum natural probability",
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
                Row.ofFloat("Parallax meter reveal", "meter appears only after this quality",
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
                Row.ofFloat("Reward scale", "reward multiplier for this lock",
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

    // ------------------------------------------------------------------ layout

    private int panelX() {
        return width / 2 - PANEL_W / 2;
    }

    private int panelY() {
        return height / 2 - PANEL_H / 2;
    }

    private int leftX() {
        return width / 2 - 16 - COL_W;
    }

    private int rightX() {
        return width / 2 + 16;
    }

    private boolean useSingleColumnLayout() {
        String t = pages.get(page).title();
        return "Dimension".equals(t) || "Guardians".equals(t) || "Item overrides".equals(t);
    }

    private int fieldWidth() {
        return useSingleColumnLayout() ? PANEL_W - 40 : COL_W;
    }

    private int rowX(boolean rightColumn) {
        return useSingleColumnLayout() ? panelX() + 20 : (rightColumn ? rightX() : leftX());
    }

    private int pageFieldLift() {
        return onDimensionPage() ? 18 : 0;
    }

    private int gridTop() {
        // Three rows of tabs now that there are ten of them. The grid used to
        // start at a fixed 76 and the third row of tabs was drawn straight over
        // the first row of settings.
        return panelY() + 32 + tabRows() * 22 + 10;
    }

    /** How many rows the tab strip needs. */
    private int tabRows() {
        return (pages.size() + 3) / 4;
    }

    private int rowY(int i) {
        return gridTop() + pageFieldLift() + i * ROW_H + 13;
    }

    @Override
    protected void init() {
        clearWidgets();
        problem = "";

        // tabs across the top, wrapping onto a second line when there are enough
        int tabW = (PANEL_W - 16) / 4;
        for (int i = 0; i < pages.size(); i++) {
            final int which = i;
            int tx = panelX() + 8 + (i % 4) * tabW;
            int ty = panelY() + 26 + (i / 4) * 22;
            Button tab = Button.builder(Component.literal(pages.get(i).title()), b -> {
                page = which;
                init();
            }).bounds(tx, ty, tabW - 4, 20).build();
            tab.active = i != page;
            addRenderableWidget(tab);
        }

        if (onTuningPage()) {
            int ty = gridTop() - 23;
            addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                tuningIndex = (tuningIndex + MinigameType.values().length - 1) % MinigameType.values().length;
                init();
            }).bounds(panelX() + 18, ty, 22, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                tuningIndex = (tuningIndex + 1) % MinigameType.values().length;
                init();
            }).bounds(panelX() + PANEL_W - 40, ty, 22, 20).build());
        }

        if (onDimensionPage()) {
            int ty = gridTop() - 23;
            addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                int n = Math.max(1, dimensionKeys().size());
                dimensionIndex = (dimensionIndex + n - 1) % n;
                init();
            }).bounds(panelX() + 18, ty, 22, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                int n = Math.max(1, dimensionKeys().size());
                dimensionIndex = (dimensionIndex + 1) % n;
                init();
            }).bounds(panelX() + PANEL_W - 40, ty, 22, 20).build());
        }

        List<Row> rows = pages.get(page).rows();
        int split = useSingleColumnLayout() ? rows.size() : Math.max(1, (rows.size() + 1) / 2);
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int x = rowX(i >= split);
            int y = rowY(i % split);

            if (r.kind == Kind.BOOL) {
                r.value = r.readBool.getAsBoolean();
                r.toggle = Button.builder(toggleLabel(r.label, r.value), b -> {
                    r.value = !r.value;
                    b.setMessage(toggleLabel(r.label, r.value));
                }).bounds(x, y - 1, fieldWidth(), 20).build();
                addRenderableWidget(r.toggle);
            } else {
                EditBox box = new EditBox(font, x, y, fieldWidth(), 18, Component.literal(""));
                box.setMaxLength(r.kind == Kind.TEXT ? 2048 : 12);
                box.setValue(r.initial());
                r.box = box;
                addRenderableWidget(box);
            }
        }

        int buttonY = panelY() + PANEL_H - 30;
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> save())
                .bounds(width / 2 - 108, buttonY, 104, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(width / 2 + 4, buttonY, 104, 20).build());
    }

    private Component toggleLabel(String name, boolean on) {
        return Component.literal(name + ": " + (on ? "on" : "off"));
    }

    /**
     * Writes back only the page in front of you.
     *
     * <p>The other pages have no live widgets - they are rebuilt on every tab
     * change - so writing them would write the values they were constructed
     * with, which is what they already hold. Saving one page at a time is
     * therefore both correct and the only thing that can be correct here.
     */
    private void save() {
        for (Row r : pages.get(page).rows()) {
            if (r.kind == Kind.BOOL) {
                r.writeBool.accept(r.value);
            } else if (r.kind == Kind.TEXT && r.box != null) {
                r.writeText.accept(r.box.getValue());
            } else if (r.box != null) {
                r.writeNumber.accept(r.box.getValue());
            }
        }
        WardConfig.clampAll();
        WardConfig.save();
        problem = "Saved. Values outside their limits were clamped.";
        init();
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);

        int x = panelX(), y = panelY();
        g.fill(x - 2, y - 2, x + PANEL_W + 2, y + PANEL_H + 2, COL_PANEL_EDGE);
        g.fill(x, y, x + PANEL_W, y + PANEL_H, COL_BG);
        g.fill(x, y, x + PANEL_W, y + 22, COL_HEADER);
        g.fill(x, y + 21, x + PANEL_W, y + 22, COL_BRASS);

        g.drawString(font, "Wardbound", x + 10, y + 7, COL_TEXT, false);
        g.drawString(font, pages.get(page).title(), x + PANEL_W - 10
                - font.width(pages.get(page).title()), y + 7, COL_BRASS_LIT, false);

        if (onTuningPage()) {
            String tuningLabel = "Tuning: " + niceGameName(selectedType());
            g.drawString(font, tuningLabel, width / 2 - font.width(tuningLabel) / 2, gridTop() - 18, COL_BRASS_LIT, false);
        }

        if (onDimensionPage()) {
            String dimLabel = "Dimension: " + selectedDimensionKey();
            g.drawString(font, dimLabel, width / 2 - font.width(dimLabel) / 2, gridTop() - 18, COL_BRASS_LIT, false);
        }

        List<Row> rows = pages.get(page).rows();
        int split = useSingleColumnLayout() ? rows.size() : Math.max(1, (rows.size() + 1) / 2);
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int cx = rowX(i >= split);
            int cy = rowY(i % split);
            if (r.kind != Kind.BOOL) {
                g.drawString(font, r.label, cx, cy - 22, COL_TEXT, false);
            }
            if (r.note != null) {
                g.drawString(font, r.note, cx, cy - 11, COL_NOTE, false);
            }
        }

        g.drawString(font, "Everything here also lives in config/wardbound.json, with notes.",
                x + 10, y + PANEL_H - 44, COL_FAINT, false);
        if (!problem.isEmpty()) {
            g.drawString(font, problem, x + 10, y + PANEL_H - 56,
                    problem.startsWith("Saved") ? COL_NOTE : COL_BAD, false);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }
}
