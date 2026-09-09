package dev.marrowseal.wardbound;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.common.Tags;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

/**
 * Works out how valuable an item is without anybody writing a list by hand.
 *
 * Signals used, roughly in order of how much they matter:
 *   - forge item tags (gems, ingots, raw materials, ores, storage blocks, nuggets, dusts)
 *   - tool tier and armor stats, which cover every modded tool and armour set
 *   - vanilla Rarity, which mods set on their special drops
 *   - stack size of one, fire resistance, durability, enchantability
 *
 * Anything listed in the config item_values map overrides all of this.
 */
public final class ItemValueResolver {

    private static final Map<ResourceLocation, Integer> CACHE = new HashMap<>();

    private ItemValueResolver() {
    }

    public static void clearCache() {
        CACHE.clear();
    }

    /** Score for a concrete stack, enchantments included. */
    public static int score(ItemStack stack) {
        if (stack.isEmpty()) return 0;

        int base = baseScore(stack);

        int enchantments = EnchantmentHelper.getEnchantments(stack).size();
        if (enchantments > 0) {
            base += 6 + Math.min(12, enchantments * 4);
        }
        return Math.max(1, Math.min(60, base));
    }

    /** Score for the item type alone, cached. */
    public static int baseScore(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return 1;

        String key = id.toString();
        Integer manual = WardConfig.itemValues.get(key);
        if (manual != null) return manual;

        if (!WardConfig.autoItemValues) return 1;

        Integer cached = CACHE.get(id);
        if (cached != null) return cached;

        int computed = compute(stack);
        CACHE.put(id, computed);
        return computed;
    }

    private static int compute(ItemStack stack) {
        float s = 1f;

        // ---- material tags. These are the strongest hint mods give us.
        if (stack.is(Tags.Items.GEMS)) s = Math.max(s, 22f);
        else if (stack.is(Tags.Items.INGOTS)) s = Math.max(s, 14f);
        else if (stack.is(Tags.Items.RAW_MATERIALS)) s = Math.max(s, 12f);
        else if (stack.is(Tags.Items.ORES)) s = Math.max(s, 11f);
        else if (stack.is(Tags.Items.DUSTS)) s = Math.max(s, 6f);
        else if (stack.is(Tags.Items.NUGGETS)) s = Math.max(s, 3f);

        if (stack.is(Tags.Items.STORAGE_BLOCKS)) s = Math.max(s, 20f);
        if (stack.is(ItemTags.BEACON_PAYMENT_ITEMS)) s = Math.max(s, 15f);
        if (stack.is(ItemTags.MUSIC_DISCS)) s = Math.max(s, 15f);
        if (stack.is(Items.ENCHANTED_BOOK)) s = Math.max(s, 20f);

        // ---- tools and weapons, tier driven so modded tiers scale on their own
        if (stack.getItem() instanceof TieredItem tiered) {
            Tier tier = tiered.getTier();
            float t = 5f
                    + tier.getLevel() * 3.5f
                    + tier.getEnchantmentValue() * 0.35f
                    + tier.getUses() / 260f
                    + tier.getAttackDamageBonus() * 1.2f;
            s = Math.max(s, t);
        }

        // ---- armour
        if (stack.getItem() instanceof ArmorItem armor) {
            float a = 4f + armor.getDefense() * 1.7f + armor.getToughness() * 3.5f;
            s = Math.max(s, a);
        }

        // ---- generic signals that catch the odd one out
        boolean single = stack.getItem().getMaxStackSize() == 1;
        boolean damageable = stack.getItem().canBeDepleted();

        if (single && !damageable) s += 6f;          // totems, elytra, shells, saddles
        if (stack.getItem().isFireResistant()) s += 8f;  // netherite family
        if (damageable) s += stack.getItem().getMaxDamage() / 220f;
        s += stack.getItem().getEnchantmentValue() * 0.2f;

        // ---- rarity, which is how most mods flag their treasure
        Rarity rarity = stack.getRarity();
        float rarityMult = switch (rarity) {
            case UNCOMMON -> 1.45f;
            case RARE -> 2.1f;
            case EPIC -> 3.0f;
            default -> 1.0f;
        };
        s *= rarityMult;

        s *= WardConfig.autoValueScale;

        return Math.max(1, Math.min(50, Math.round(s)));
    }
}
