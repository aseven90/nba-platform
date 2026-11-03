package io.nbaplatform.decision.state;

import io.nbaplatform.decision.spi.ContactHistory;
import io.nbaplatform.decision.spi.LearningState;
import io.nbaplatform.events.InteractionRecorded;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.stereotype.Component;

/**
 * Arm statistics and per-party contact history, built from interaction events. Every replica consumes all
 * interactions, so replicas converge on the same state. This is the MVP implementation of the SPI; a shared
 * Redis-compatible store replaces it when party counts outgrow pod memory.
 */
@Component
public class InMemoryLearningStore implements LearningState, ContactHistory {

    private static final Duration WINDOW = Duration.ofHours(24);
    private static final int SEEN_EVENT_IDS = 100_000;

    private record Arm(String tenantId, String campaignId, String offeringId) {
    }

    private record Contact(String tenantId, String partyId, String campaignId) {
    }

    private static final class Counts {
        final LongAdder impressions = new LongAdder();
        final LongAdder accepts = new LongAdder();
    }

    private final Map<Arm, Counts> arms = new ConcurrentHashMap<>();
    private final Map<Contact, Deque<Instant>> contacts = new ConcurrentHashMap<>();
    private final Set<String> seen = Collections.synchronizedSet(Collections.newSetFromMap(
            new LinkedHashMap<>(16, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > SEEN_EVENT_IDS;
                }
            }));

    /** Applies an interaction once; duplicates (same event id) are ignored. */
    public void apply(String eventId, String tenantId, InteractionRecorded event) {
        if (!seen.add(eventId)) {
            return;
        }
        Counts counts = arms.computeIfAbsent(new Arm(tenantId, event.campaignId(), event.offeringId()), a -> new Counts());
        switch (event.outcome()) {
            case IMPRESSION -> {
                counts.impressions.increment();
                Deque<Instant> history = contacts.computeIfAbsent(
                        new Contact(tenantId, event.partyId(), event.campaignId()), c -> new ArrayDeque<>());
                synchronized (history) {
                    history.addLast(event.occurredAt());
                    prune(history, Instant.now());
                }
            }
            case ACCEPTED -> counts.accepts.increment();
            case DECLINED, IGNORED -> {
                // Failures are impressions without an accept; nothing extra to count.
            }
        }
    }

    @Override
    public ArmStats stats(String tenantId, String campaignId, String offeringId) {
        Counts counts = arms.get(new Arm(tenantId, campaignId, offeringId));
        if (counts == null) {
            return ArmStats.EMPTY;
        }
        long accepts = counts.accepts.sum();
        return new ArmStats(Math.max(counts.impressions.sum(), accepts), accepts);
    }

    @Override
    public int impressionsLast24h(String tenantId, String partyId, String campaignId, Instant now) {
        Deque<Instant> history = contacts.get(new Contact(tenantId, partyId, campaignId));
        if (history == null) {
            return 0;
        }
        synchronized (history) {
            prune(history, now);
            return history.size();
        }
    }

    private static void prune(Deque<Instant> history, Instant now) {
        Instant cutoff = now.minus(WINDOW);
        history.removeIf(t -> t.isBefore(cutoff));
    }
}
