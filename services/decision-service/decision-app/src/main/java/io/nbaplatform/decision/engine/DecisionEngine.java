package io.nbaplatform.decision.engine;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedBulkheadMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.nbaplatform.decision.spi.Candidate;
import io.nbaplatform.decision.spi.ContactHistory;
import io.nbaplatform.decision.spi.DecisionContext;
import io.nbaplatform.decision.spi.DecisionRequest;
import io.nbaplatform.decision.spi.DecisionSnapshot;
import io.nbaplatform.decision.spi.FeatureProvider;
import io.nbaplatform.decision.spi.LearningState;
import io.nbaplatform.events.OfferingChanged;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runs the decision pipeline under the resilience quartet: a per-request deadline (timeout), a per-channel
 * bulkhead, and a circuit breaker. No retries on this path: the deadline is too short for them to help.
 * Whenever a decision cannot be made, the configured safe default answer is returned instead of an error.
 */
@Service
public class DecisionEngine {

    private static final Logger log = LoggerFactory.getLogger(DecisionEngine.class);

    private final DecisionPipeline pipeline;
    private final DecisionSnapshot snapshot;
    private final LearningState learning;
    private final ContactHistory contactHistory;
    private final FeatureProvider features;
    private final ExecutorService executor;
    private final DecisionProperties properties;
    private final BulkheadRegistry bulkheads;
    private final CircuitBreaker circuitBreaker;
    private final MeterRegistry meters;

    public DecisionEngine(DecisionPipeline pipeline, DecisionSnapshot snapshot, LearningState learning,
                          ContactHistory contactHistory, FeatureProvider features, ExecutorService decisionExecutor,
                          DecisionProperties properties, MeterRegistry meters) {
        this.pipeline = pipeline;
        this.snapshot = snapshot;
        this.learning = learning;
        this.contactHistory = contactHistory;
        this.features = features;
        this.executor = decisionExecutor;
        this.properties = properties;
        this.meters = meters;
        this.bulkheads = BulkheadRegistry.of(BulkheadConfig.custom()
                .maxConcurrentCalls(properties.maxConcurrentPerChannel())
                .maxWaitDuration(Duration.ZERO)
                .build());
        DecisionProperties.CircuitBreaker cb = properties.circuitBreaker();
        CircuitBreakerRegistry breakers = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                        .failureRateThreshold(cb.failureRateThreshold())
                        .slowCallRateThreshold(cb.slowCallRateThreshold())
                        .slowCallDurationThreshold(properties.deadline())
                        .minimumNumberOfCalls(cb.minimumNumberOfCalls())
                        .waitDurationInOpenState(cb.waitInOpenState())
                        .build());
        this.circuitBreaker = breakers.circuitBreaker("decision-pipeline");
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(breakers).bindTo(meters);
        TaggedBulkheadMetrics.ofBulkheadRegistry(bulkheads).bindTo(meters);
    }

    public DecisionOutcome decide(DecisionRequest request) {
        String decisionId = UUID.randomUUID().toString();
        long start = System.nanoTime();
        DecisionOutcome outcome = guarded(decisionId, request, start);
        Timer.builder("nba.decision.latency")
                .tag("channel", request.channel())
                .tag("fallback", String.valueOf(outcome.fallback()))
                .publishPercentileHistogram()
                .register(meters)
                .record(Duration.ofNanos(System.nanoTime() - start));
        return outcome;
    }

    private DecisionOutcome guarded(String decisionId, DecisionRequest request, long start) {
        Bulkhead bulkhead = bulkheads.bulkhead("decision-" + request.channel());
        if (!bulkhead.tryAcquirePermission()) {
            return fallback(decisionId, request, "BULKHEAD_FULL", start);
        }
        try {
            if (!circuitBreaker.tryAcquirePermission()) {
                return fallback(decisionId, request, "CIRCUIT_OPEN", start);
            }
            Future<DecisionContext> future = executor.submit(() -> run(decisionId, request));
            try {
                DecisionContext context = future.get(properties.deadline().toNanos(), TimeUnit.NANOSECONDS);
                circuitBreaker.onSuccess(System.nanoTime() - start, TimeUnit.NANOSECONDS);
                return new DecisionOutcome(decisionId, context.request(), List.copyOf(context.candidates()),
                        context.suppressed(), null, micros(start));
            } catch (TimeoutException e) {
                future.cancel(true);
                circuitBreaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, e);
                return fallback(decisionId, request, "DEADLINE_EXCEEDED", start);
            } catch (ExecutionException e) {
                circuitBreaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, e.getCause());
                log.warn("Decision pipeline failed for tenant {}: {}", request.tenantId(), e.getCause().toString());
                return fallback(decisionId, request, "PIPELINE_ERROR", start);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.cancel(true);
                circuitBreaker.releasePermission();
                return fallback(decisionId, request, "INTERRUPTED", start);
            }
        } finally {
            bulkhead.onComplete();
        }
    }

    private DecisionContext run(String decisionId, DecisionRequest request) {
        Map<String, Object> attributes = new HashMap<>(features.features(request.tenantId(), request.partyId()));
        attributes.putAll(request.attributes()); // caller context is fresher than stored features
        DecisionRequest enriched = new DecisionRequest(request.tenantId(), request.partyId(), request.channel(),
                attributes, request.maxItems(), request.now());
        DecisionContext context = new DecisionContext(decisionId, enriched, snapshot, learning, contactHistory);
        pipeline.run(context);
        return context;
    }

    private DecisionOutcome fallback(String decisionId, DecisionRequest request, String reason, long start) {
        meters.counter("nba.decision.fallback", "reason", reason).increment();
        List<Candidate> items = properties.safeDefaultsFor(request.tenantId()).stream()
                .limit(request.maxItems())
                .map(id -> {
                    OfferingChanged offering = snapshot.offering(request.tenantId(), id)
                            .orElse(new OfferingChanged(id, null, null, null, null, List.of(), 0, null, null, false, 0));
                    Candidate c = new Candidate(offering, null, 1.0);
                    c.reason("SAFE_DEFAULT");
                    c.reason(reason);
                    return c;
                })
                .toList();
        return new DecisionOutcome(decisionId, request, items, List.of(), reason, micros(start));
    }

    private static long micros(long start) {
        return (System.nanoTime() - start) / 1_000;
    }

    public String strategy() {
        return pipeline.describe();
    }
}
