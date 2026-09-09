package dev.marrowseal.wardbound.ancientsmith;
import dev.marrowseal.wardbound.WardConfig;
import java.awt.Color;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.sounds.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import team.lodestar.lodestone.registry.common.particle.LodestoneParticleRegistry;
import team.lodestar.lodestone.systems.particle.builder.WorldParticleBuilder;
import team.lodestar.lodestone.systems.particle.data.GenericParticleData;
import team.lodestar.lodestone.systems.particle.data.color.ColorParticleData;
/** Owned effects can be cut without interrupting unrelated sounds or particles. */
@Mod.EventBusSubscriber(modid="wardbound",value=Dist.CLIENT)
public final class SmithClientFx {
 private static final Color IRON=new Color(111,107,98), ASH=new Color(49,45,42), COPPER=new Color(190,119,62),
         GOLD=new Color(232,184,93), IVORY=new Color(242,225,183), EMBER=new Color(206,79,38), WHITE=new Color(255,246,218);
 private static final class Burst {final List<Particle> particles=new ArrayList<>();final List<SoundInstance> sounds=new ArrayList<>();long muteUntil;}
 private static final Map<Integer,Burst> OWNED=new HashMap<>();private static ClientLevel world;private static int shakeTicks;private static float shake;
 private static void world(){var mc=Minecraft.getInstance();if(world!=mc.level){OWNED.values().forEach(b->{b.particles.forEach(Particle::remove);b.sounds.forEach(mc.getSoundManager()::stop);});OWNED.clear();shakeTicks=0;world=mc.level;}}
 public static void accept(SmithFxPacket p){world();var mc=Minecraft.getInstance();if(world==null||mc.player==null)return;Vec3 pos=new Vec3(p.x(),p.y(),p.z());if(!Double.isFinite(pos.lengthSqr())||pos.distanceToSqr(mc.player.position())>4096||p.event()<1||p.event()>4)return;
  Burst b=OWNED.computeIfAbsent(p.entity(),k->new Burst());
  if(p.event()==SmithVfx.SILENCE){
   // The old implementation removed the FINAL burst and stopped its anvil clang only two ticks
   // after impact. Keep the already-authored impact alive; silence only prevents a second event
   // from stacking immediately on top of the ceremonial final strike.
   b.muteUntil=Math.max(b.muteUntil,world.getGameTime()+12);shakeTicks=0;shake=0;return;
  }
  if(world.getGameTime()<b.muteUntil)return;
  boolean heavy=p.event()!=SmithVfx.STRIKE, fin=p.event()==SmithVfx.FINAL;
  SoundEvent sound=fin?AncientSmithModule.FINAL.get():heavy?AncientSmithModule.HEAVY.get():AncientSmithModule.IMPACT.get();
  play(b,sound,heavy?1.18f:.86f,fin?.56f:heavy?.64f:.82f,pos);
  if(heavy)play(b,AncientSmithModule.METAL.get(),fin?.72f:.46f,fin?.73f:.90f,pos);
  double distance=mc.player.position().distanceTo(pos);
  if(WardConfig.accessibilityDisableShake||WardConfig.accessibilityReduceMotion){shakeTicks=0;shake=0;}
  else{shakeTicks=fin?10:heavy?8:4;shake=(float)(Math.max(0,1-distance/28)*(fin?.62:heavy?.46:.18));}
  int n=mc.options.particles().get()==ParticleStatus.MINIMAL?6:mc.options.particles().get()==ParticleStatus.DECREASED?14:22;
  if(WardConfig.accessibilityReduceMotion)n=Math.max(3,n/2);n=Math.max(0,Math.min(30,n));
  impactBurst(b,pos,p.event(),n);
 }
 private static void play(Burst b,SoundEvent sound,float volume,float pitch,Vec3 pos){
  var mc=Minecraft.getInstance();var instance=new SimpleSoundInstance(sound,SoundSource.BLOCKS,volume,pitch,world.random,pos.x,pos.y,pos.z);mc.getSoundManager().play(instance);b.sounds.add(instance);
 }
 private static void impactBurst(Burst b,Vec3 pos,int event,int n){
  boolean heavy=event!=SmithVfx.STRIKE, fin=event==SmithVfx.FINAL;
  Color hot=fin?WHITE:heavy?GOLD:COPPER, mid=fin?GOLD:heavy?EMBER:IRON;
  // 1) Contact sparks: directional metal fragments leave the hammer face instead of a generic cloud.
  int sparks=fin?Math.max(18,n):heavy?Math.max(12,n*3/4):Math.max(7,n/2);
  for(int i=0;i<sparks;i++){
   double a=world.random.nextDouble()*Math.PI*2, speed=(fin?.22:heavy?.18:.13)+world.random.nextDouble()*(fin?.20:.12);
   double flatten=.72+world.random.nextDouble()*.22;
   spawn(b,pos.add(0,.035,0),new Vec3(Math.cos(a)*speed*flatten,.07+world.random.nextDouble()*(fin?.24:.16),Math.sin(a)*speed*flatten),hot,mid,fin?.095f:heavy?.082f:.066f,fin?18:heavy?15:11,.12f,.90f);
  }
  // 2) A low shock ring makes the exact anvil contact readable from across the forge.
  int ring=fin?28:heavy?22:14;double radius=fin?.62:heavy?.48:.34, outward=fin?.16:heavy?.13:.10;
  for(int i=0;i<ring;i++){
   double a=i*Math.PI*2/ring;Vec3 q=pos.add(Math.cos(a)*radius,.018,Math.sin(a)*radius);
   spawn(b,q,new Vec3(Math.cos(a)*outward,.008,Math.sin(a)*outward),i%3==0?IVORY:hot,ASH,fin?.105f:heavy?.085f:.060f,fin?15:heavy?12:8,0f,fin?.82f:.68f);
  }
  // 3) Four forge spokes read as a deliberate ancient craft mark, not free-floating magic.
  int spokes=fin?8:4;for(int arm=0;arm<spokes;arm++){
   double a=arm*Math.PI*2/spokes;int steps=fin?5:3;
   for(int j=1;j<=steps;j++){
    double rr=.22+j*(fin?.25:.20);Vec3 q=pos.add(Math.cos(a)*rr,.026,Math.sin(a)*rr);
    spawn(b,q,new Vec3(Math.cos(a)*.018,.010,Math.sin(a)*.018),fin?IVORY:GOLD,IRON,fin?.082f:.060f,fin?18:12,0f,.68f);
   }
  }
  // 4) Heavy/final strikes vent a short temper plume; final gets a small crown/helix signature.
  if(heavy){int plume=fin?14:7;for(int i=0;i<plume;i++){
   double a=world.random.nextDouble()*Math.PI*2,rr=world.random.nextDouble()*.34;
   spawn(b,pos.add(Math.cos(a)*rr,.06,Math.sin(a)*rr),new Vec3(Math.cos(a)*.025,.09+world.random.nextDouble()*.11,Math.sin(a)*.025),fin?IVORY:EMBER,ASH,fin?.14f:.11f,fin?23:17,.025f,.62f);
  }}
  if(fin){for(int i=0;i<18;i++){
   double t=i/18.0*Math.PI*4,rr=.52+i*.018;Vec3 q=pos.add(Math.cos(t)*rr,.10+i*.055,Math.sin(t)*rr);
   spawn(b,q,new Vec3(-Math.sin(t)*.018,.035,Math.cos(t)*.018),i%2==0?WHITE:GOLD,EMBER,.085f,22,0f,.82f);
  }}
 }
 private static void spawn(Burst owner,Vec3 p,Vec3 motion,Color a,Color z,float scale,int life,float gravity,float alpha){
  if(p.distanceToSqr(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition())<.45)return;
  WorldParticleBuilder.create(LodestoneParticleRegistry.WISP_PARTICLE).setColorData(ColorParticleData.create(a,z).build()).setTransparencyData(GenericParticleData.create(alpha,0).build()).setScaleData(GenericParticleData.create(scale,0).build()).setMotion(motion).setGravityStrength(gravity).setLifetime(life).addSpawnActor(owner.particles::add).enableNoClip().spawn(world,p.x,p.y,p.z);
 }
 @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END)return;world();var mc=Minecraft.getInstance();if(world==null||mc.isPaused())return;if(shakeTicks>0)shakeTicks--;OWNED.values().forEach(b->{b.particles.removeIf(p->!p.isAlive());b.sounds.removeIf(s->!mc.getSoundManager().isActive(s));});OWNED.entrySet().removeIf(e2->e2.getValue().particles.isEmpty()&&e2.getValue().sounds.isEmpty()&&world.getGameTime()>e2.getValue().muteUntil);}
 @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles e){if(WardConfig.accessibilityDisableShake||WardConfig.accessibilityReduceMotion||shakeTicks==0||Minecraft.getInstance().isPaused())return;double t=shakeTicks-e.getPartialTick();float a=shake*(shakeTicks/10f);e.setPitch(e.getPitch()+(float)Math.sin(t*2.5)*a);e.setRoll(e.getRoll()+(float)Math.cos(t*2)*a*.4f);}
}
