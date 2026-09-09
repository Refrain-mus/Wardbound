package dev.marrowseal.wardbound.nhalsul;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.*;
import java.util.Optional;

/** Call register(modBus) once from Wardbound's mod constructor. No other project edits required. */
public final class NhalSulModule {
    public static final String MODID="wardbound";
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,MODID);
    public static final RegistryObject<NhalSulItem> NHAL_SUL=ITEMS.register("nhal_sul",()->new NhalSulItem(new Item.Properties().fireResistant().rarity(Rarity.EPIC)));
    private static final String PROTOCOL="1";
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(MODID,"nhal_sul_fx"),()->PROTOCOL,PROTOCOL::equals,PROTOCOL::equals);
    public static void register(IEventBus bus){ITEMS.register(bus);bus.addListener(NhalSulModule::setup);bus.addListener(NhalSulModule::creative);}
    private static void setup(FMLCommonSetupEvent e){e.enqueueWork(()->{CHANNEL.registerMessage(0,NhalSulFxPacket.class,NhalSulFxPacket::encode,NhalSulFxPacket::decode,NhalSulFxPacket::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));});}
    private static void creative(BuildCreativeModeTabContentsEvent e){if(e.getTabKey()==CreativeModeTabs.COMBAT)e.accept(NHAL_SUL.get());}
    private NhalSulModule(){}
}
