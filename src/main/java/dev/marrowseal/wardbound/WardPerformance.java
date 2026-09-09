package dev.marrowseal.wardbound;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tiny bounded caches/counters used by the long-form systems. No scheduled work:
 * every cache is populated only by an event that already needed the result.
 */
public final class WardPerformance {
    private WardPerformance() {}

    private static final int MAX_CACHE = 256;
    private static long guardianHits;
    private static long guardianMisses;
    private static long guardianPuts;

    private static final Map<String, Boolean> GUARDIAN_ELIGIBILITY = new LinkedHashMap<>(MAX_CACHE, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) { return size() > MAX_CACHE; }
    };

    public static synchronized Boolean guardianEligibility(String key) {
        Boolean value = GUARDIAN_ELIGIBILITY.get(key);
        if (value == null) guardianMisses++; else guardianHits++;
        return value;
    }

    public static synchronized void putGuardianEligibility(String key, boolean value) {
        GUARDIAN_ELIGIBILITY.put(key, value);
        guardianPuts++;
    }

    public static synchronized void clear() {
        GUARDIAN_ELIGIBILITY.clear();
        guardianHits = 0;
        guardianMisses = 0;
        guardianPuts = 0;
    }

    public static synchronized int guardianCacheSize() { return GUARDIAN_ELIGIBILITY.size(); }
    public static synchronized long guardianCacheHits() { return guardianHits; }
    public static synchronized long guardianCacheMisses() { return guardianMisses; }
    public static synchronized long guardianCachePuts() { return guardianPuts; }

    public static synchronized String guardianCacheStats() {
        long total = guardianHits + guardianMisses;
        double hitRate = total <= 0 ? 0.0 : guardianHits * 100.0 / total;
        return String.format(java.util.Locale.ROOT, "%d/%d entries · %.1f%% hit · %d misses · %d writes",
                GUARDIAN_ELIGIBILITY.size(), MAX_CACHE, hitRate, guardianMisses, guardianPuts);
    }
}
