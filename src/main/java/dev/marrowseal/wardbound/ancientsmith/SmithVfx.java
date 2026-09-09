package dev.marrowseal.wardbound.ancientsmith;
import net.minecraftforge.network.PacketDistributor;
/** Stable wire IDs: 1 metal impact, 2 heavy, 3 final, 4 silence. No server particle loop. */
public final class SmithVfx {
 public static final int STRIKE=1,HEAVY=2,FINAL=3,SILENCE=4;
 public static void emit(AncientSmithEntity smith,int id){if(smith.level().isClientSide)return;var p=smith.strikePoint();AncientSmithModule.CHANNEL.send(PacketDistributor.NEAR.with(()->new PacketDistributor.TargetPoint(p.x,p.y,p.z,64,smith.level().dimension())),new SmithFxPacket(smith.getId(),id,p.x,p.y,p.z));}
}
