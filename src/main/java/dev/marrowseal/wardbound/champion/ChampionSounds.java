package dev.marrowseal.wardbound.champion;

import dev.marrowseal.wardbound.WardSounds;
import dev.marrowseal.wardbound.Wardbound;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.RegistryObject;

public final class ChampionSounds {
    public static final RegistryObject<SoundEvent> PRESENCE = WardSounds.REGISTRY.register("silas_presence",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(Wardbound.MODID, "silas_presence")));
    public static final RegistryObject<SoundEvent> TESTIMONY = WardSounds.REGISTRY.register("silas_testimony",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(Wardbound.MODID, "silas_testimony")));

    public static void init() {}
    private ChampionSounds() {}
}
