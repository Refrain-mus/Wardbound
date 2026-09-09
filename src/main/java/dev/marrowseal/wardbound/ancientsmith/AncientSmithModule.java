package dev.marrowseal.wardbound.ancientsmith;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.*;
import java.util.*;
/** Call register exactly once from Wardbound's constructor. No old registry edits. */
public final class AncientSmithModule {
 public static final String MODID="wardbound";
 public static ResourceLocation id(String s){return new ResourceLocation(MODID,s);}
 private static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.ENTITY_TYPES,MODID);
 private static final DeferredRegister<SoundEvent> SOUNDS=DeferredRegister.create(ForgeRegistries.SOUND_EVENTS,MODID);
 public static final RegistryObject<EntityType<AncientSmithEntity>> SMITH=ENTITIES.register("ancient_smith",()->EntityType.Builder.of(AncientSmithEntity::new,MobCategory.MISC).sized(3.8f,9f).clientTrackingRange(96).updateInterval(1).fireImmune().build("wardbound:ancient_smith"));
 public static final RegistryObject<EntityType<AncientAnvilEntity>> ANVIL=ENTITIES.register("ancient_forge_anvil",()->EntityType.Builder.<AncientAnvilEntity>of(AncientAnvilEntity::new,MobCategory.MISC).sized(5.5f,3.5f).clientTrackingRange(96).fireImmune().build("wardbound:ancient_forge_anvil"));
 public static final RegistryObject<SoundEvent> IMPACT=sound("ancient_smith.impact"),HEAVY=sound("ancient_smith.heavy"),FINAL=sound("ancient_smith.final"),METAL=sound("ancient_smith.metal"),BREATH=sound("ancient_smith.breath");
 private static RegistryObject<SoundEvent> sound(String s){return SOUNDS.register(s,()->SoundEvent.createVariableRangeEvent(id(s)));}
 public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(id("ancient_smith_scene"),()->"1","1"::equals,"1"::equals);
 public static void register(IEventBus bus){ENTITIES.register(bus);SOUNDS.register(bus);bus.addListener(AncientSmithModule::attributes);bus.addListener(AncientSmithModule::setup);}
 private static void attributes(EntityAttributeCreationEvent e){e.put(SMITH.get(),AncientSmithEntity.attributes().build());}
 private static void setup(FMLCommonSetupEvent e){e.enqueueWork(()->{CHANNEL.registerMessage(0,SmithFxPacket.class,SmithFxPacket::encode,SmithFxPacket::decode,SmithFxPacket::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));});}
 private AncientSmithModule(){}
}
