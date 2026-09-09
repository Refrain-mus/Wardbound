package dev.marrowseal.wardbound.client;

/** Network clocks can skip ticks; accept recent crossings without replaying stale impacts. */
public final class MasterFxTiming {
    private MasterFxTiming() {}
    public static boolean crossed(int before,int now,int beat){
        return before<beat&&now>=beat&&now-beat<=6;
    }
}
