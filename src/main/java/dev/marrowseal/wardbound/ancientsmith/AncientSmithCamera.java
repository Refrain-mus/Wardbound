package dev.marrowseal.wardbound.ancientsmith;
import net.minecraft.world.phys.Vec3;
/** Optional shot coordinates only: never takes camera control or teleports the player. */
public final class AncientSmithCamera {
 public record Shot(Vec3 position,Vec3 target,float fov){}
 public static Shot shot(AncientSmithEntity s,int tick){
  if(tick<70)return new Shot(s.local(8,3,-13),s.local(0,6,-1),58);
  if(tick<320)return new Shot(s.local(5,5,-9),s.local(0,4.5,-4),48);
  if(tick<390)return new Shot(s.local(-7,2,-10),s.local(0,6,-3),62);
  return new Shot(s.local(6,3,-10),s.offerPoint(),55);
 }
 public static Shot blend(Shot a,Shot b,double t){t=Math.max(0,Math.min(1,t));t=t*t*(3-2*t);return new Shot(a.position.lerp(b.position,t),a.target.lerp(b.target,t),(float)(a.fov+(b.fov-a.fov)*t));}
}
