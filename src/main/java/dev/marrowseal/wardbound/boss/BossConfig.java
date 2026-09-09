package dev.marrowseal.wardbound.boss;

import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;

/** Shared server-authoritative tuning registry. Future Masters register their own defaults here. */
public final class BossConfig {
    private static final Map<String, Settings> DEFAULTS = new LinkedHashMap<>();
    private static final Map<String, Settings> VALUES = new LinkedHashMap<>();
    public record Settings(boolean enabled, boolean repeatVictories, double health, double healthPerAlly,
                           double damageMultiplier, double attackSpeed, double hitCap, int timeoutSeconds,
                           int storyChapter, int chainStage, int revisedCards, boolean dialogue, int particles,
                           boolean themeEnabled, double themeVolume, int themeFadeInTicks, int themeFadeOutTicks) {}
    static { register("pale_gambler", new Settings(true,false,8000,2000,1,1,180,1800,7,5,3,true,2,true,0.85,24,50));
        register("ashen_curator",new Settings(true,false,8000,1800,1,1,160,1800,7,5,1,true,2,true,.75,45,70));
        register("mourning_notary",new Settings(true,false,8000,1700,1,1,150,1800,7,5,10,true,2,false,.75,36,60)); }
    public static void register(String id, Settings defaults) { DEFAULTS.put(id,defaults); VALUES.putIfAbsent(id,defaults); }
    public static Settings get(String id) { return VALUES.getOrDefault(id,DEFAULTS.get(id)); }
    public static Settings gambler() { return get("pale_gambler"); }
    public static Settings curator() { return get("ashen_curator"); }
    public static Settings notary() { return get("mourning_notary"); }
    public static void reset() { VALUES.clear(); VALUES.putAll(DEFAULTS); }
    private static double num(JsonObject o,String k,double d,double min,double max) {
        try { double v=o.has(k)?o.get(k).getAsDouble():d; return Double.isFinite(v)?Math.max(min,Math.min(max,v)):d; } catch(RuntimeException e) {return d;}
    }
    private static boolean bool(JsonObject o,String k,boolean d) {try{return o.has(k)?o.get(k).getAsBoolean():d;}catch(RuntimeException e){return d;}}
    public static void read(JsonObject root) {
        reset(); if(!root.has("bosses") || !root.get("bosses").isJsonObject())return;
        JsonObject bosses=root.getAsJsonObject("bosses");
        for(var entry:DEFAULTS.entrySet()) {
            String id=entry.getKey();Settings d=entry.getValue();
            if(!bosses.has(id) || !bosses.get(id).isJsonObject())continue;
            JsonObject o=bosses.getAsJsonObject(id);
            double health=num(o,"health",d.health,100,1000000);
            double allyHealth=num(o,"health_per_extra_player",d.healthPerAlly,0,1000000);
            // Migrate only the exact legacy Pale Gambler defaults. Explicit custom tuning is preserved.
            if ("pale_gambler".equals(id) && Math.abs(health-16000.0)<0.001 && Math.abs(allyHealth-7000.0)<0.001) {
                health=d.health;
                allyHealth=d.healthPerAlly;
            }
            VALUES.put(id,new Settings(bool(o,"enabled",d.enabled),bool(o,"allow_repeat_victories",d.repeatVictories),
                health,allyHealth,
                num(o,"damage_multiplier",d.damageMultiplier,.1,10),num(o,"attack_speed",d.attackSpeed,.25,3),
                num(o,"maximum_incoming_hit",d.hitCap,1,100000), (int)num(o,"timeout_seconds",d.timeoutSeconds,60,7200),
                (int)num(o,"required_story_chapter",d.storyChapter,0,7),(int)num(o,"required_chain_stage",d.chainStage,0,5),
                (int)num(o,id.equals("ashen_curator")?"required_curse_evolutions":id.equals("mourning_notary")?"required_objectives":"required_revised_cards",d.revisedCards,0,id.equals("ashen_curator")?3:id.equals("mourning_notary")?64:18),bool(o,"dialogue",d.dialogue),(int)num(o,"particle_detail",d.particles,0,2),
                bool(o,"theme_enabled",d.themeEnabled),num(o,"theme_volume",d.themeVolume,0,2),
                (int)num(o,"theme_fade_in_ticks",d.themeFadeInTicks,1,200),(int)num(o,"theme_fade_out_ticks",d.themeFadeOutTicks,1,240)));
        }
    }
    public static void write(JsonObject root) {
        JsonObject all=new JsonObject();
        all.addProperty("_help","Independent Master settings. Reload with /wardbound gambler reload or /wardbound curator reload. Active encounters snapshot combat values; Curator also synchronizes music tuning. particle_detail: 0 minimal, 1 normal, 2 ornate; danger cues remain visible. Curator required_curse_evolutions counts discovered CurseEvolution corruptions. Theme slots: assets/wardbound/sounds/gambler/boss_theme.ogg and assets/wardbound/sounds/ashen_curator/boss_theme.ogg. Mourning Notary currently uses encounter SFX rather than a dedicated theme slot.");
        for(var e:VALUES.entrySet()) {
            Settings s=e.getValue();JsonObject o=new JsonObject();o.addProperty("enabled",s.enabled);o.addProperty("allow_repeat_victories",s.repeatVictories);
            o.addProperty("health",s.health);o.addProperty("health_per_extra_player",s.healthPerAlly);o.addProperty("damage_multiplier",s.damageMultiplier);
            o.addProperty("attack_speed",s.attackSpeed);o.addProperty("maximum_incoming_hit",s.hitCap);o.addProperty("timeout_seconds",s.timeoutSeconds);
            o.addProperty("required_story_chapter",s.storyChapter);o.addProperty("required_chain_stage",s.chainStage);o.addProperty(e.getKey().equals("ashen_curator")?"required_curse_evolutions":e.getKey().equals("mourning_notary")?"required_objectives":"required_revised_cards",s.revisedCards);
            o.addProperty("dialogue",s.dialogue);o.addProperty("particle_detail",s.particles);
            o.addProperty("theme_enabled",s.themeEnabled);o.addProperty("theme_volume",s.themeVolume);
            o.addProperty("theme_fade_in_ticks",s.themeFadeInTicks);o.addProperty("theme_fade_out_ticks",s.themeFadeOutTicks);all.add(e.getKey(),o);
        }root.add("bosses",all);
    }
}
