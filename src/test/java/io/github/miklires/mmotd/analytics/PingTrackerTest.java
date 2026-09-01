package io.github.miklires.mmotd.analytics;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PingTrackerTest {
    @Test void classifiesAddressesAndCountsOneConversion() throws Exception {
        PingTracker tracker = new PingTracker();
        tracker.configure(true, 60, 100);
        InetAddress address = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        assertEquals(PingTracker.Audience.FIRST, tracker.recordPing(address, 1_000));
        assertEquals(PingTracker.Audience.RETURNING, tracker.recordPing(address, 2_000));
        tracker.recordJoin(address, 2_500);
        tracker.recordJoin(address, 3_000);
        var stats = tracker.snapshot();
        assertEquals(2, stats.pings());
        assertEquals(1, stats.uniquePings());
        assertEquals(1, stats.joinedAfterPing());
        assertEquals(100.0, stats.conversionPercent());
    }

    @Test void expiresWithoutPersistingAnAddress() throws Exception {
        PingTracker tracker = new PingTracker();
        tracker.configure(true, 1, 100);
        InetAddress address = InetAddress.getByAddress(new byte[]{10, 0, 0, 1});
        assertEquals(PingTracker.Audience.FIRST, tracker.recordPing(address, 0));
        tracker.cleanup(60_001);
        assertEquals(0, tracker.snapshot().activeAddresses());
        assertEquals(PingTracker.Audience.FIRST, tracker.recordPing(address, 60_002));
    }
}
