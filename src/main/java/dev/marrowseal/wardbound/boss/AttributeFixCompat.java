package dev.marrowseal.wardbound.boss;

import dev.marrowseal.wardbound.Wardbound;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Runtime sanity checks for the required AttributeFix configuration. */
@Mod.EventBusSubscriber(modid = Wardbound.MODID)
public final class AttributeFixCompat {
    private static boolean warned;
    private AttributeFixCompat() {}

    public static double maxHealthCap() {
        return Attributes.MAX_HEALTH instanceof RangedAttribute ranged ? ranged.getMaxValue() : 1024D;
    }

    public static double requiredCurrentBossHealth() {
        BossConfig.Settings g=BossConfig.gambler(), c=BossConfig.curator(), n=BossConfig.notary();
        // Gambler supports 4 participants; Curator supports 8. Notary normally lives in
        // a private cell but use eight here as a conservative compatibility target.
        double configured=Math.max(g.health()+g.healthPerAlly()*3D,
                Math.max(c.health()+c.healthPerAlly()*7D,n.health()+n.healthPerAlly()*7D));
        return Math.max(14000D,configured);
    }

    public static boolean supports(double requested) {
        return maxHealthCap()+0.001D >= requested;
    }

    public static void warnIfInsufficient(double requested) {
        if (!supports(requested) && !warned) {
            warned=true;
            Wardbound.LOG.error("AttributeFix generic.max_health cap is {} but Wardbound requested {}. " +
                    "Increase minecraft:generic.max_health max in config/attributefix.json. " +
                    "The cinematic ledger will keep the fight functional, but native HUD/mod integrations will be clamped.",
                    maxHealthCap(), requested);
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        double required=requiredCurrentBossHealth();
        if (supports(required)) {
            Wardbound.LOG.info("AttributeFix verified: generic.max_health cap {} supports Wardbound requirement {}.",maxHealthCap(),required);
        } else warnIfInsufficient(required);
    }
}
