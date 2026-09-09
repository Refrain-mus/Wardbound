package dev.marrowseal.wardbound.boss;

import dev.marrowseal.wardbound.Wardbound;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=Wardbound.MODID,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class WardBosses {
    public static final DeferredRegister<EntityType<?>> TYPES=DeferredRegister.create(ForgeRegistries.ENTITY_TYPES,Wardbound.MODID);
    public static final RegistryObject<EntityType<dev.marrowseal.wardbound.champion.DefeatedChampionEntity>> DEFEATED_CHAMPION=TYPES.register("defeated_champion",()->
            EntityType.Builder.of(dev.marrowseal.wardbound.champion.DefeatedChampionEntity::new,MobCategory.CREATURE).sized(.7f,2.05f)
                    .clientTrackingRange(10).updateInterval(2).build("wardbound:defeated_champion"));
    public static final RegistryObject<EntityType<CthulhuHeadEntity>> CTHULHU_HEAD=TYPES.register("cthulhu_head",()->
            EntityType.Builder.of(CthulhuHeadEntity::new,MobCategory.MISC).sized(18f,20f)
                    .clientTrackingRange(32).updateInterval(2).fireImmune().build("wardbound:cthulhu_head"));
    public static final RegistryObject<EntityType<MourningNotaryPreviewEntity>> MOURNING_NOTARY_PREVIEW=TYPES.register("mourning_notary_preview",()->
            EntityType.Builder.of(MourningNotaryPreviewEntity::new,MobCategory.MISC).sized(3.6f,8.4f)
                    .clientTrackingRange(12).updateInterval(2).fireImmune().build("wardbound:mourning_notary_preview"));
    public static final RegistryObject<EntityType<MourningNotaryEntity>> MOURNING_NOTARY=TYPES.register("mourning_notary",()->
            EntityType.Builder.of(MourningNotaryEntity::new,MobCategory.MONSTER).sized(3.8f,8.8f)
                    .clientTrackingRange(24).updateInterval(2).fireImmune().build("wardbound:mourning_notary"));
    public static final RegistryObject<EntityType<AshenCuratorEntity>> ASHEN_CURATOR=TYPES.register("ashen_curator",()->
            EntityType.Builder.of(AshenCuratorEntity::new,MobCategory.MONSTER).sized(3.1f,7.2f)
                    .clientTrackingRange(24).updateInterval(2).fireImmune().build("wardbound:ashen_curator"));
    public static final RegistryObject<EntityType<PaleGamblerEntity>> PALE_GAMBLER=TYPES.register("pale_gambler",()->
            EntityType.Builder.of(PaleGamblerEntity::new,MobCategory.MONSTER).sized(2.1f,5.7f)
                    .clientTrackingRange(20).updateInterval(2).fireImmune().build("wardbound:pale_gambler"));
    public static final RegistryObject<EntityType<MaestroEntity>> MAESTRO=TYPES.register("maestro",()->
            EntityType.Builder.of(MaestroEntity::new,MobCategory.MONSTER).sized(.8f,2.0f)
                    .clientTrackingRange(24).updateInterval(2).fireImmune().build("wardbound:maestro"));
    @SubscribeEvent public static void attributes(EntityAttributeCreationEvent event) {
        event.put(CTHULHU_HEAD.get(),CthulhuHeadEntity.attributes().build());
        event.put(DEFEATED_CHAMPION.get(),dev.marrowseal.wardbound.champion.DefeatedChampionEntity.attributes().build());
        event.put(PALE_GAMBLER.get(),PaleGamblerEntity.attributes().build());
        event.put(ASHEN_CURATOR.get(),AshenCuratorEntity.attributes().build());
        event.put(MOURNING_NOTARY_PREVIEW.get(),MourningNotaryPreviewEntity.attributes().build());
        event.put(MOURNING_NOTARY.get(),MourningNotaryEntity.attributes().build());
        event.put(MAESTRO.get(),MaestroEntity.attributes().build());
    }
}
