package dev.marrowseal.wardbound.champion;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.marrowseal.wardbound.CardMaster;
import dev.marrowseal.wardbound.LockData;
import dev.marrowseal.wardbound.MasterStory;
import dev.marrowseal.wardbound.OccultChainEvents;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.item.WardItems;
import dev.marrowseal.wardbound.nhalsul.NhalSulModule;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = Wardbound.MODID)
public final class ChampionCommands {
    private ChampionCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        var root = Commands.literal("silas");
        var poses = Commands.literal("pose").requires(s -> s.hasPermission(2));
        for (String pose : new String[]{"sit", "stand", "shift", "look", "sword", "weary"}) {
            poses.then(Commands.literal(pose).executes(c -> {
                var p = c.getSource().getPlayerOrException();
                var list = p.serverLevel().getEntitiesOfClass(DefeatedChampionEntity.class, p.getBoundingBox().inflate(8));
                return !list.isEmpty() && list.get(0).previewRest(pose) ? 1 : 0;
            }));
        }
        root.then(poses);
        root.then(Commands.literal("summon").requires(s -> s.hasPermission(2)).executes(c -> {
            var p = c.getSource().getPlayerOrException();
            var pos = p.blockPosition().relative(p.getDirection(), 5);
            boolean ok = ChampionEncounters.place(p, pos, true);
            c.getSource().sendSuccess(() -> Component.literal(ok
                    ? "Silas Veyr arrived. The same world identity is preserved."
                    : "Cannot arrive: use a clear 3 x 3 Overworld floor, outside combat. Silas Veyr cannot be duplicated."), false);
            return ok ? 1 : 0;
        }));
        root.then(Commands.literal("status").requires(s -> s.hasPermission(2)).executes(c -> status(c.getSource())));
        root.then(progressionBranch());
        e.getDispatcher().register(Commands.literal("wardbound").then(root));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> progressionBranch() {
        var progression = Commands.literal("progression").requires(s -> s.hasPermission(2));
        progression.then(Commands.literal("summary").executes(c -> progressionSummary(c.getSource().getPlayerOrException(), c.getSource())));
        progression.then(Commands.literal("preset")
                .then(Commands.literal("trace").executes(c -> applyPreset(c.getSource().getPlayerOrException(), 0, c.getSource())))
                .then(Commands.literal("wayfarer").executes(c -> applyPreset(c.getSource().getPlayerOrException(), 1, c.getSource())))
                .then(Commands.literal("companions").executes(c -> applyPreset(c.getSource().getPlayerOrException(), 2, c.getSource())))
                .then(Commands.literal("hands").executes(c -> applyPreset(c.getSource().getPlayerOrException(), 3, c.getSource())))
                .then(Commands.literal("unconcluded").executes(c -> applyPreset(c.getSource().getPlayerOrException(), 4, c.getSource())))
                .then(Commands.literal("witness").executes(c -> applyPreset(c.getSource().getPlayerOrException(), 5, c.getSource()))));
        progression.then(Commands.literal("master")
                .then(masterBranch("gambler", CardMaster.PALE_GAMBLER, "gambler_defeated"))
                .then(masterBranch("curator", CardMaster.ASHEN_CURATOR, "curator_defeated"))
                .then(masterBranch("notary", CardMaster.MOURNING_NOTARY, "notary_defeated")));
        progression.then(Commands.literal("chain")
                .then(chainBranch("black_table", OccultChainEvents.ChainEvent.BLACK_TABLE))
                .then(chainBranch("ash_margin", OccultChainEvents.ChainEvent.ASH_MARGIN))
                .then(chainBranch("witness_sequence", OccultChainEvents.ChainEvent.WITNESS_SEQUENCE)));
        progression.then(Commands.literal("objectives")
                .then(Commands.argument("count", IntegerArgumentType.integer(0, 64))
                        .executes(c -> setUniqueInt(c.getSource().getPlayerOrException(), "objectives_completed_total", IntegerArgumentType.getInteger(c, "count"), "Objectives completed", c.getSource()))));
        progression.then(Commands.literal("fragments")
                .then(Commands.argument("count", IntegerArgumentType.integer(0, SilasLore.Fragment.values().length))
                        .executes(c -> setFragments(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "count"), c.getSource()))));
        progression.then(Commands.literal("testimony")
                .then(Commands.argument("assembled", BoolArgumentType.bool())
                        .executes(c -> setUniqueInt(c.getSource().getPlayerOrException(), "silas_testimony_assembled", BoolArgumentType.getBool(c, "assembled") ? 1 : 0, "Unconcluded testimony assembled", c.getSource()))));
        progression.then(Commands.literal("give")
                .then(Commands.literal("ring").executes(c -> giveItem(c.getSource().getPlayerOrException(), new ItemStack(WardItems.WORN_SCABBARD_RING.get()), "Worn Scabbard Ring", c.getSource())))
                .then(Commands.literal("testimony").executes(c -> giveItem(c.getSource().getPlayerOrException(), new ItemStack(WardItems.UNCONCLUDED_TESTIMONY.get()), "The Unconcluded Testimony", c.getSource())))
                .then(Commands.literal("nhal_sul").executes(c -> giveItem(c.getSource().getPlayerOrException(), new ItemStack(NhalSulModule.NHAL_SUL.get()), "Nhal-Sûl", c.getSource()))));
        return progression;
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> masterBranch(String name, CardMaster master, String defeatedKey) {
        return Commands.literal(name)
                .then(Commands.literal("chapter")
                        .then(Commands.argument("value", IntegerArgumentType.integer(0, MasterStory.MAX_CHAPTER))
                                .executes(c -> setUniqueInt(c.getSource().getPlayerOrException(), "dealer_story_" + master.id, IntegerArgumentType.getInteger(c, "value"), master.title + " chapter", c.getSource()))))
                .then(Commands.literal("relation")
                        .then(Commands.argument("value", IntegerArgumentType.integer(-12, 20))
                                .executes(c -> setUniqueInt(c.getSource().getPlayerOrException(), "dealer_relation_" + master.id, IntegerArgumentType.getInteger(c, "value"), master.title + " relation", c.getSource()))))
                .then(Commands.literal("known")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(c -> setKnown(c.getSource().getPlayerOrException(), master, BoolArgumentType.getBool(c, "value"), c.getSource()))))
                .then(Commands.literal("defeated")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(c -> setUniqueInt(c.getSource().getPlayerOrException(), defeatedKey, BoolArgumentType.getBool(c, "value") ? 1 : 0, master.title + " defeated", c.getSource()))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> chainBranch(String literal, OccultChainEvents.ChainEvent event) {
        return Commands.literal(literal)
                .then(Commands.literal("stage")
                        .then(Commands.argument("value", IntegerArgumentType.integer(0, event.maxStage))
                                .executes(c -> setUniqueInt(c.getSource().getPlayerOrException(), "occult_chain_" + event.id, IntegerArgumentType.getInteger(c, "value"), event.title + " stage", c.getSource()))));
    }

    private static int status(CommandSourceStack source) {
        var d = ChampionWorldData.get(source.getServer());
        source.sendSuccess(() -> Component.literal("Silas Veyr: "
                + (d.person == null ? "not encountered" : "at " + d.position.toShortString())
                + " | incarnation " + d.incarnation), false);
        return 1;
    }

    private static int progressionSummary(ServerPlayer player, CommandSourceStack source) {
        LockData data = LockData.get(player.getServer());
        UUID id = player.getUUID();
        String silas = switch (SilasProgression.stage(data, id)) {
            case SilasProgression.TRACE -> "TRACE";
            case SilasProgression.WAYFARER -> "WAYFARER";
            case SilasProgression.COMPANIONS -> "THREE COMPANIONS";
            case SilasProgression.HANDS -> "THE HANDS BEHIND IT";
            case SilasProgression.UNCONCLUDED -> "UNCONCLUDED";
            default -> "WITNESS";
        };
        String line1 = "Silas stage: " + silas + " | fragments " + Integer.bitCount(SilasLore.mask(data, id)) + "/" + SilasLore.Fragment.values().length
                + " | testimony " + (data.uniqueInt(id, "silas_testimony_assembled") > 0 ? "assembled" : "not assembled");
        String line2 = "Curator ch " + MasterStory.chapter(data, id, CardMaster.ASHEN_CURATOR) + " rel " + CardMaster.ASHEN_CURATOR.relation(data, id)
                + " | Notary ch " + MasterStory.chapter(data, id, CardMaster.MOURNING_NOTARY) + " rel " + CardMaster.MOURNING_NOTARY.relation(data, id)
                + " | Gambler ch " + MasterStory.chapter(data, id, CardMaster.PALE_GAMBLER) + " rel " + CardMaster.PALE_GAMBLER.relation(data, id);
        String line3 = "Chains — Ash " + OccultChainEvents.stage(data, id, OccultChainEvents.ChainEvent.ASH_MARGIN)
                + "/5, Witness " + OccultChainEvents.stage(data, id, OccultChainEvents.ChainEvent.WITNESS_SEQUENCE)
                + "/5, Black Table " + OccultChainEvents.stage(data, id, OccultChainEvents.ChainEvent.BLACK_TABLE)
                + "/5 | objectives " + data.uniqueInt(id, "objectives_completed_total");
        String line4 = "Defeated — Curator " + yesNo(data.uniqueInt(id, "curator_defeated") > 0)
                + ", Notary " + yesNo(data.uniqueInt(id, "notary_defeated") > 0)
                + ", Gambler " + yesNo(data.uniqueInt(id, "gambler_defeated") > 0);
        source.sendSuccess(() -> Component.literal(line1), false);
        source.sendSuccess(() -> Component.literal(line2), false);
        source.sendSuccess(() -> Component.literal(line3), false);
        source.sendSuccess(() -> Component.literal(line4), false);
        return 1;
    }

    private static int applyPreset(ServerPlayer player, int stage, CommandSourceStack source) {
        LockData data = LockData.get(player.getServer());
        UUID id = player.getUUID();
        if (stage >= 4) applyPreset(player, stage - 1, source);
        data.setUniqueInt(id, "gambler_defeated", 0);
        data.setUniqueInt(id, "curator_defeated", 0);
        data.setUniqueInt(id, "notary_defeated", 0);
        if (stage <= 3) data.setUniqueInt(id, "silas_testimony_assembled", 0);
        switch (stage) {
            case 0 -> {
                for (CardMaster master : CardMaster.values()) {
                    data.setUnique(id, "dealer_known_" + master.id, false);
                    data.setUniqueInt(id, "dealer_story_" + master.id, 0);
                    data.setUniqueInt(id, "dealer_relation_" + master.id, 0);
                }
                data.setUniqueInt(id, "occult_chain_ash_margin", 0);
                data.setUniqueInt(id, "occult_chain_witness_sequence", 0);
                data.setUniqueInt(id, "occult_chain_black_table", 0);
                data.setUniqueInt(id, "objectives_completed_total", 0);
                data.setUniqueInt(id, "silas_fragment_mask", 0);
            }
            case 1 -> {
                setStory(data, id, CardMaster.ASHEN_CURATOR, 4, 6, true);
                setStory(data, id, CardMaster.MOURNING_NOTARY, 3, 4, true);
                setStory(data, id, CardMaster.PALE_GAMBLER, 2, 3, true);
                data.setUniqueInt(id, "occult_chain_ash_margin", 1);
                data.setUniqueInt(id, "occult_chain_witness_sequence", 0);
                data.setUniqueInt(id, "occult_chain_black_table", 0);
                data.setUniqueInt(id, "objectives_completed_total", 2);
                setFragmentMask(data, id, 2);
            }
            case 2 -> {
                setStory(data, id, CardMaster.ASHEN_CURATOR, 5, 8, true);
                setStory(data, id, CardMaster.MOURNING_NOTARY, 5, 7, true);
                setStory(data, id, CardMaster.PALE_GAMBLER, 5, 7, true);
                data.setUniqueInt(id, "occult_chain_ash_margin", 2);
                data.setUniqueInt(id, "occult_chain_witness_sequence", 1);
                data.setUniqueInt(id, "occult_chain_black_table", 1);
                data.setUniqueInt(id, "objectives_completed_total", 5);
                setFragmentMask(data, id, 5);
            }
            case 3 -> {
                setStory(data, id, CardMaster.ASHEN_CURATOR, 6, 12, true);
                setStory(data, id, CardMaster.MOURNING_NOTARY, 6, 11, true);
                setStory(data, id, CardMaster.PALE_GAMBLER, 6, 11, true);
                data.setUniqueInt(id, "occult_chain_ash_margin", 4);
                data.setUniqueInt(id, "occult_chain_witness_sequence", 3);
                data.setUniqueInt(id, "occult_chain_black_table", 3);
                data.setUniqueInt(id, "objectives_completed_total", 10);
                setFragmentMask(data, id, 9);
            }
            case 4 -> {
                data.setUniqueInt(id, "curator_defeated", 1);
                data.setUniqueInt(id, "silas_testimony_assembled", 1);
                setFragmentMask(data, id, SilasLore.Fragment.values().length);
            }
            default -> {
                data.setUniqueInt(id, "curator_defeated", 1);
                data.setUniqueInt(id, "notary_defeated", 1);
                data.setUniqueInt(id, "silas_testimony_assembled", 1);
                setFragmentMask(data, id, SilasLore.Fragment.values().length);
            }
        }
        source.sendSuccess(() -> Component.literal("Silas progression preset applied: " + silasStageName(stage)), false);
        return progressionSummary(player, source);
    }

    private static void setStory(LockData data, UUID id, CardMaster master, int chapter, int relation, boolean known) {
        data.setUnique(id, "dealer_known_" + master.id, known);
        data.setUniqueInt(id, "dealer_story_" + master.id, chapter);
        data.setUniqueInt(id, "dealer_relation_" + master.id, relation);
    }

    private static int setUniqueInt(ServerPlayer player, String key, int value, String label, CommandSourceStack source) {
        LockData.get(player.getServer()).setUniqueInt(player.getUUID(), key, value);
        source.sendSuccess(() -> Component.literal(label + " set to " + value + "."), false);
        return 1;
    }

    private static int setKnown(ServerPlayer player, CardMaster master, boolean value, CommandSourceStack source) {
        LockData.get(player.getServer()).setUnique(player.getUUID(), "dealer_known_" + master.id, value);
        source.sendSuccess(() -> Component.literal(master.title + " known = " + value), false);
        return 1;
    }

    private static int setFragments(ServerPlayer player, int count, CommandSourceStack source) {
        LockData data = LockData.get(player.getServer());
        setFragmentMask(data, player.getUUID(), count);
        source.sendSuccess(() -> Component.literal("Silas fragments staged to " + count + "/" + SilasLore.Fragment.values().length + "."), false);
        return 1;
    }

    private static void setFragmentMask(LockData data, UUID id, int count) {
        int mask = 0;
        for (int i = 0; i < Math.max(0, Math.min(count, SilasLore.Fragment.values().length)); i++) mask |= 1 << i;
        data.setUniqueInt(id, "silas_fragment_mask", mask);
    }

    private static int giveItem(ServerPlayer player, ItemStack stack, String label, CommandSourceStack source) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        source.sendSuccess(() -> Component.literal(label + " delivered."), false);
        return 1;
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private static String silasStageName(int stage) {
        return switch (stage) {
            case 0 -> "trace";
            case 1 -> "wayfarer";
            case 2 -> "three companions";
            case 3 -> "the hands behind it";
            case 4 -> "unconcluded";
            default -> "witness";
        };
    }
}
