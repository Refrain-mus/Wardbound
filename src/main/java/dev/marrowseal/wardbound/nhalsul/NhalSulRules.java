package dev.marrowseal.wardbound.nhalsul;
/** Pure combat rules, shared by the server action and validation tests. */
public final class NhalSulRules {
    /** Right-click is an immediate Unwritten Cut; cooldown prevents spam. */
    public static final int COOLDOWN=240,MAX_TARGETS=8;
    public static final double RANGE=18,WIDTH=1.65;
    public static final float DAMAGE=70;
    public static boolean inCorridor(double along,double sideSquared){return along>=0&&along<=RANGE&&sideSquared<=WIDTH*WIDTH;}
    private NhalSulRules(){}
}
