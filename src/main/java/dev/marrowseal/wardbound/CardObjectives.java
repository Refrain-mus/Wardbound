package dev.marrowseal.wardbound;

import java.util.UUID;

/** Shared metadata for Contract, Ritual and Covenant objective cards. */
public final class CardObjectives {
    private CardObjectives() {}

    public enum Objective {
        ROTTEN_LEDGER("rotten_ledger", "The Rotten Ledger", ForbiddenBargain.Kind.CONTRACT, 25, "Zombie-family kills", "Swift clause: sustained hunting is noted; exceptional pace doubles the clause payment."),
        BONE_TALLY("bone_tally", "The Bone Tally", ForbiddenBargain.Kind.CONTRACT, 20, "Skeleton-family kills", "Swift clause: sustained hunting is noted; exceptional pace doubles the clause payment."),
        CREEPER_CLAUSE("creeper_clause", "The Creeper Clause", ForbiddenBargain.Kind.CONTRACT, 12, "Creeper-family kills", "Swift clause: sustained hunting is noted; exceptional pace doubles the clause payment."),
        SILK_WARRANT("silk_warrant", "The Silk Warrant", ForbiddenBargain.Kind.CONTRACT, 18, "Spider-family kills", "Swift clause: sustained hunting is noted; exceptional pace doubles the clause payment."),
        ENDER_AUDIT("ender_audit", "The Ender Audit", ForbiddenBargain.Kind.CONTRACT, 8, "Enderman-family kills", "Swift clause: sustained hunting is noted; exceptional pace doubles the clause payment."),
        WITCH_LEDGER("witch_ledger", "The Witch Ledger", ForbiddenBargain.Kind.CONTRACT, 10, "Witch-family kills", "Swift clause: sustained hunting is noted; exceptional pace doubles the clause payment."),
        GRIM_HARVEST("grim_harvest", "The Grim Harvest", ForbiddenBargain.Kind.CONTRACT, 30, "Any hostile kills", "Swift clause: sustained hunting is noted; exceptional pace doubles the clause payment."),

        STONE_COMMUNION("stone_communion", "Stone Communion", ForbiddenBargain.Kind.RITUAL, 128, "Stone worked in low light", "Purity clause: target blocks broken outside the required condition scar the rite; one or two breaches remain legible."),
        VEIN_LITANY("vein_litany", "The Vein Litany", ForbiddenBargain.Kind.RITUAL, 32, "Ore mined at Y 32 or below", "Purity clause: qualifying ore mined too high scars the rite; one or two breaches remain legible."),
        WOODEN_CONFESSION("wooden_confession", "Wooden Confession", ForbiddenBargain.Kind.RITUAL, 96, "Logs cut beneath open sky", "Purity clause: covered log cuts scar the rite; one or two breaches remain legible."),
        EARTHEN_VIGIL("earthen_vigil", "The Earthen Vigil", ForbiddenBargain.Kind.RITUAL, 96, "Earth/mud worked beneath open night", "Purity clause: target earth worked outside open night scars the rite; one or two breaches remain legible."),
        OBSIDIAN_PRAYER("obsidian_prayer", "Obsidian Prayer", ForbiddenBargain.Kind.RITUAL, 12, "Obsidian mined in strong local light", "Purity clause: dim obsidian breaks scar the rite; one or two breaches remain legible."),
        SAND_LITURGY("sand_liturgy", "Sand Liturgy", ForbiddenBargain.Kind.RITUAL, 96, "Sand/glass worked in direct daylight", "Purity clause: target blocks broken outside direct daylight scar the rite."),

        RED_CENSUS("red_census", "The Red Census", ForbiddenBargain.Kind.COVENANT, 20, "Villager-family kills", "Covenant stain deepens with every payment."),
        IRON_SILENCE("iron_silence", "The Iron Silence", ForbiddenBargain.Kind.COVENANT, 5, "Iron-golem-family kills", "Covenant stain deepens with every payment."),
        INNOCENCE_TAX("innocence_tax", "The Innocence Tax", ForbiddenBargain.Kind.COVENANT, 40, "Passive-animal kills", "Covenant stain deepens with every payment."),
        WITCHFIRE_TITHE("witchfire_tithe", "Witchfire Tithe", ForbiddenBargain.Kind.COVENANT, 12, "Witch-family kills", "Covenant stain deepens with every payment."),
        OSSUARY_VOW("ossuary_vow", "The Ossuary Vow", ForbiddenBargain.Kind.COVENANT, 24, "Any undead kills", "Covenant stain deepens with every payment.");

        public final String key;
        public final String title;
        public final ForbiddenBargain.Kind kind;
        public final int goal;
        public final String detail;
        public final String finePrint;

        Objective(String key, String title, ForbiddenBargain.Kind kind, int goal, String detail, String finePrint) {
            this.key = key;
            this.title = title;
            this.kind = kind;
            this.goal = goal;
            this.detail = detail;
            this.finePrint = finePrint;
        }

        public String category() { return kind.name(); }
    }


    public static final long SWIFT_WINDOW_TICKS = 20L * 60L * 10L;
    public static final long EXACTING_WINDOW_TICKS = 20L * 60L * 6L;

    public static int swiftStreakNeeded(Objective objective) {
        return objective == null ? Integer.MAX_VALUE : Math.max(3, objective.goal / 4);
    }

    public static int exactingStreakNeeded(Objective objective) {
        if (objective == null) return Integer.MAX_VALUE;
        return Math.max(swiftStreakNeeded(objective) + 2, objective.goal / 2);
    }

    public static int ritualPurity(int missteps) {
        return Math.max(0, 100 - Math.max(0, missteps) * 8);
    }

    public static String ritualGrade(int purity) {
        if (purity >= 100) return "unscarred";
        if (purity >= 84) return "legible";
        if (purity >= 60) return "scarred";
        return "defaced";
    }

    public static Objective byKey(String key) {
        if (key == null) return null;
        for (Objective objective : Objective.values()) if (objective.key.equals(key)) return objective;
        return null;
    }

    public static int activeCount(LockData data, UUID player) {
        int count = 0;
        for (Objective objective : Objective.values()) if (data.hasUnique(player, "obj_" + objective.key)) count++;
        return count;
    }

    public static int activeCount(LockData data, UUID player, ForbiddenBargain.Kind kind) {
        int count = 0;
        for (Objective objective : Objective.values())
            if (objective.kind == kind && data.hasUnique(player, "obj_" + objective.key)) count++;
        return count;
    }
}
