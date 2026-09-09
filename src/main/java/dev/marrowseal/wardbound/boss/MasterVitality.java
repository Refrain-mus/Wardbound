package dev.marrowseal.wardbound.boss;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Cinematic vitality mirror for Wardbound bosses.
 *
 * AttributeFix makes the entity's native generic.max_health/current health usable at
 * Wardbound scale. Ordinary hits now stay on that native pipeline; this ledger mirrors
 * the same value so lethal hits can stop at one native HP while authored death timelines
 * finish cleanly.
 */
public final class MasterVitality {
    /** Attribute suppliers are built before AttributeFix applies its load-complete config. */
    public static final double REGISTRATION_MAX_HEALTH=1024D;
    private double maximum,current;
    public MasterVitality(double max){maximum=Math.max(1,max);current=maximum;}
    public double maximum(){return maximum;}
    public double current(){return current;}
    public float fraction(){return (float)(current/maximum);}
    public boolean damage(double amount){if(Double.isFinite(amount) && amount>0)current=Math.max(0,current-amount);return current<=0;}
    public void heal(double amount){if(Double.isFinite(amount)&&amount>0)current=Math.min(maximum,current+amount);}
    public void restore(double value){current=Double.isFinite(value)?Math.max(0,Math.min(maximum,value)):maximum;}

    /** Bind the authored encounter maximum after AttributeFix has raised the global cap. */
    public void bindMaximum(LivingEntity entity){
        if(entity==null)return;
        AttributeFixCompat.warnIfInsufficient(maximum);
        AttributeInstance attribute=entity.getAttribute(Attributes.MAX_HEALTH);
        if(attribute!=null && Math.abs(attribute.getBaseValue()-maximum)>.0001)attribute.setBaseValue(maximum);
    }

    /** Keep native health readers (HUD mods, commands, integrations) on the real value. */
    public void syncNativeHealth(LivingEntity entity){
        if(entity==null)return;
        bindMaximum(entity);
        if(current<=0){entity.setHealth(1);return;} // death timeline owns the final 1 HP
        float max=entity.getMaxHealth();
        entity.setHealth((float)Math.max(1,Math.min(current,max)));
    }

    /**
     * Adopt legitimate native health changes (healing, commands or compatible external
     * effects) before the next Wardbound damage transaction. Authored maximum health is
     * still retained so unrelated modifiers cannot permanently rewrite encounter tuning.
     */
    public float prepareNativeDamage(LivingEntity entity){
        bindMaximum(entity);
        reconcileFromNative(entity);
        return entity.getHealth();
    }

    public void reconcileFromNative(LivingEntity entity){
        if(entity==null||current<=0)return;
        bindMaximum(entity);
        float nativeHealth=entity.getHealth();
        if(Float.isFinite(nativeHealth)&&nativeHealth>0F&&Math.abs(nativeHealth-current)>.001D)
            current=Math.max(0D,Math.min(maximum,nativeHealth));
    }
}
