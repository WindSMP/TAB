package me.neznamy.tab.shared.metrics;

import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/**
 * Low-overhead, opt-in counters for TAB's own outbound work. These counters are
 * deliberately incremented before the platform packet implementation is called,
 * so forwarding an unrelated packet through TAB's Netty handler is not charged
 * to TAB as a generated packet.
 */
public final class OutboundPacketMetrics {

    private static final LongAdder teamPackets = new LongAdder();
    private static final LongAdder tablistPackets = new LongAdder();
    private static final LongAdder headerFooterPackets = new LongAdder();
    private static final LongAdder scoreboardPackets = new LongAdder();
    private static final LongAdder suppressedUpdates = new LongAdder();
    private static final LongAdder placeholderEvaluations = new LongAdder();
    private static final LongAdder fullResyncs = new LongAdder();

    private static volatile boolean enabled;
    private static long lastSnapshotNanos = System.nanoTime();
    private static long lastTeamPackets;
    private static long lastTablistPackets;
    private static long lastHeaderFooterPackets;
    private static long lastScoreboardPackets;
    private static long lastSuppressedUpdates;
    private static long lastPlaceholderEvaluations;
    private static long lastFullResyncs;

    private OutboundPacketMetrics() {
    }

    public static void setEnabled(boolean enabled) {
        OutboundPacketMetrics.enabled = enabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void teamPacket() {
        if (enabled) teamPackets.increment();
    }

    public static void tablistPacket() {
        if (enabled) tablistPackets.increment();
    }

    public static void headerFooterPacket() {
        if (enabled) headerFooterPackets.increment();
    }

    public static void scoreboardPacket() {
        if (enabled) scoreboardPackets.increment();
    }

    public static void suppressedUpdate() {
        if (enabled) suppressedUpdates.increment();
    }

    public static void placeholderEvaluations(long count) {
        if (enabled) placeholderEvaluations.add(count);
    }

    public static void fullResync() {
        if (enabled) fullResyncs.increment();
    }

    /**
     * Returns cumulative counters and rates since the preceding snapshot. This is
     * called only by the dump command and therefore adds no periodic task.
     */
    @NotNull
    public static synchronized Map<String, Object> snapshot() {
        long now = System.nanoTime();
        double seconds = Math.max((now - lastSnapshotNanos) / 1_000_000_000D, 0.001D);
        long teams = teamPackets.sum();
        long tablist = tablistPackets.sum();
        long headerFooter = headerFooterPackets.sum();
        long scoreboard = scoreboardPackets.sum();
        long suppressed = suppressedUpdates.sum();
        long placeholders = placeholderEvaluations.sum();
        long resyncs = fullResyncs.sum();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", enabled);
        if (enabled) {
            result.put("sample-seconds", seconds);
            result.put("team-packets-total", teams);
            result.put("team-packets-per-second", (teams - lastTeamPackets) / seconds);
            result.put("tablist-packets-total", tablist);
            result.put("tablist-packets-per-second", (tablist - lastTablistPackets) / seconds);
            result.put("header-footer-packets-total", headerFooter);
            result.put("header-footer-packets-per-second", (headerFooter - lastHeaderFooterPackets) / seconds);
            result.put("scoreboard-packets-total", scoreboard);
            result.put("scoreboard-packets-per-second", (scoreboard - lastScoreboardPackets) / seconds);
            result.put("suppressed-identical-updates-total", suppressed);
            result.put("suppressed-identical-updates-per-second", (suppressed - lastSuppressedUpdates) / seconds);
            result.put("placeholder-evaluations-total", placeholders);
            result.put("placeholder-evaluations-per-second", (placeholders - lastPlaceholderEvaluations) / seconds);
            result.put("full-resyncs-total", resyncs);
            result.put("full-resyncs-per-second", (resyncs - lastFullResyncs) / seconds);
        }

        lastSnapshotNanos = now;
        lastTeamPackets = teams;
        lastTablistPackets = tablist;
        lastHeaderFooterPackets = headerFooter;
        lastScoreboardPackets = scoreboard;
        lastSuppressedUpdates = suppressed;
        lastPlaceholderEvaluations = placeholders;
        lastFullResyncs = resyncs;
        return result;
    }
}
