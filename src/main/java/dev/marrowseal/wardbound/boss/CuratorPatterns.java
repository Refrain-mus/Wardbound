package dev.marrowseal.wardbound.boss;

/** Shared by server collision, client drawings and the standalone geometry checks. */
public final class CuratorPatterns {
    private CuratorPatterns() {}
    public static final int RECT=0, RING=1, PAGE=2;
    public static final double RADIUS=16, SEAL_RADIUS=3.5;
    public static final double[][] SEALS={{8,8},{-8,8},{-8,-8},{8,-8}};
    public static boolean inside(int shape,double x,double z,double a,double b) {
        return shape==RING ? Math.abs(Math.hypot(x,z)-a)<=b
                : shape==PAGE ? x*x+z*z<=a*a : Math.abs(x)<=a && Math.abs(z)<=b;
    }
    public static boolean refuge(double x,double z,int mask) {
        for(int i=0;i<4;i++) if((mask&(1<<i))!=0 && inside(RECT,x-SEALS[i][0],z-SEALS[i][1],SEAL_RADIUS,SEAL_RADIUS))return true;
        return false;
    }
    public static boolean active(long age,int warning,int duration){return age>=warning && age<warning+duration;}
    public static int chapter(float health){return health<=.60f?2:1;}
    public static int warning(double speed,int ticks){return Math.max(36,(int)Math.round(ticks/speed));}
}
