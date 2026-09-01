package io.github.miklires.mmotd.analytics;

import java.net.InetAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

public final class PingTracker {
    public enum Audience { ANY, FIRST, RETURNING }

    private final byte[] salt = new byte[32];
    private final ConcurrentHashMap<String, SeenAddress> seen = new ConcurrentHashMap<>();
    private final LongAdder pings = new LongAdder();
    private final LongAdder uniquePings = new LongAdder();
    private final LongAdder joinedAfterPing = new LongAdder();
    private volatile boolean enabled;
    private volatile long ttlMillis = 86_400_000L;
    private volatile int maximumAddresses = 10_000;

    public PingTracker() { new SecureRandom().nextBytes(salt); }

    public void configure(boolean enabled, long ttlMinutes, int maximumAddresses) {
        this.enabled = enabled;
        this.ttlMillis = Math.multiplyExact(Math.max(1, Math.min(10_080, ttlMinutes)), 60_000L);
        this.maximumAddresses = Math.max(100, Math.min(100_000, maximumAddresses));
        if (!enabled) seen.clear();
    }

    public Audience recordPing(InetAddress address, long nowMillis) {
        if (!enabled || address == null) return Audience.ANY;
        pings.increment();
        String key = key(address);
        SeenAddress existing = seen.get(key);
        if (existing != null && nowMillis - existing.lastSeenMillis <= ttlMillis) {
            existing.lastSeenMillis = nowMillis;
            return Audience.RETURNING;
        }
        if (existing == null && seen.size() >= maximumAddresses) return Audience.ANY;
        seen.put(key, new SeenAddress(nowMillis));
        uniquePings.increment();
        return Audience.FIRST;
    }

    public void recordJoin(InetAddress address, long nowMillis) {
        if (!enabled || address == null) return;
        SeenAddress value = seen.get(key(address));
        if (value != null && nowMillis - value.lastSeenMillis <= ttlMillis && value.converted.compareAndSet(false, true)) {
            joinedAfterPing.increment();
        }
    }

    public void cleanup(long nowMillis) {
        if (!enabled) return;
        seen.entrySet().removeIf(entry -> nowMillis - entry.getValue().lastSeenMillis > ttlMillis);
    }

    public Snapshot snapshot() {
        long unique = uniquePings.sum();
        long joined = joinedAfterPing.sum();
        return new Snapshot(enabled, pings.sum(), unique, joined, seen.size(),
                unique == 0 ? 0.0 : joined * 100.0 / unique);
    }

    private String key(InetAddress address) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            return HexFormat.of().formatHex(digest.digest(address.getAddress()));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static final class SeenAddress {
        private volatile long lastSeenMillis;
        private final AtomicBoolean converted = new AtomicBoolean();
        private SeenAddress(long lastSeenMillis) { this.lastSeenMillis = lastSeenMillis; }
    }

    public record Snapshot(boolean enabled, long pings, long uniquePings, long joinedAfterPing,
                           int activeAddresses, double conversionPercent) {}
}
