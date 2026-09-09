package dev.marrowseal.wardbound.maestroart;

import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

@Mod.EventBusSubscriber(modid="wardbound",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class MaestroParticles {
    public static final SimpleParticleType NOTE=new SimpleParticleType(false);
    public static final SimpleParticleType ARC=new SimpleParticleType(false);
    public static final SimpleParticleType RING=new SimpleParticleType(false);
    public static final SimpleParticleType BURST=new SimpleParticleType(false);
    private MaestroParticles() {}
    @SubscribeEvent public static void register(RegisterEvent event){
        event.register(ForgeRegistries.Keys.PARTICLE_TYPES,helper->{
            helper.register(new ResourceLocation("wardbound","maestro_art_note"),NOTE);
            helper.register(new ResourceLocation("wardbound","maestro_art_arc"),ARC);
            helper.register(new ResourceLocation("wardbound","maestro_art_ring"),RING);
            helper.register(new ResourceLocation("wardbound","maestro_art_burst"),BURST);
        });
    }
}
