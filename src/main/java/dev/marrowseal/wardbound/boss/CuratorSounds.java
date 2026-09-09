package dev.marrowseal.wardbound.boss;
import dev.marrowseal.wardbound.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.RegistryObject;
import java.util.*;
public final class CuratorSounds {
    private static final Map<String,RegistryObject<SoundEvent>> BANK=new LinkedHashMap<>();
    static {for(String name:new String[]{"manifestation","archive_opening","paper_movement","ash_sweep","seal_activation","seal_break","attack_charge","impact","phase_transition","ambient_presence","whispers","hurt","vulnerability","death","victory","boss_theme","shelf_cast","ring_cast","errata_mark","index_burn","open_binding","preserve","fracture"})
        BANK.put(name,WardSounds.REGISTRY.register("curator_"+name,()->SoundEvent.createVariableRangeEvent(new ResourceLocation(Wardbound.MODID,"curator_"+name))));}
    public static void init(){}
    public static SoundEvent get(String name){return BANK.get(name).get();}
}
