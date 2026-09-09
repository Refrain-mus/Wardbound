package dev.marrowseal.wardbound.maestroart;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

/** Drop-in registration: no changes to Wardbound's existing constructor or entity registry. */
@Mod.EventBusSubscriber(modid="wardbound",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class MaestroArtModule {
    public static final ResourceLocation ID=new ResourceLocation("wardbound","maestro_art_preview");
    private static EntityType<MaestroArtEntity> type;
    private MaestroArtModule() {}
    public static EntityType<MaestroArtEntity> type(){return Objects.requireNonNull(type,"Maestro art entity requested before registration");}
    @SubscribeEvent public static void register(RegisterEvent event){
        event.register(ForgeRegistries.Keys.ENTITY_TYPES,helper->{
            type=EntityType.Builder.of(MaestroArtEntity::new,MobCategory.MISC)
                    .sized(.8f,2f).clientTrackingRange(16).updateInterval(2).fireImmune().build(ID.toString());
            helper.register(ID,type);
        });
    }
    @SubscribeEvent public static void attributes(EntityAttributeCreationEvent event){event.put(type(),MaestroArtEntity.attributes().build());}
}
