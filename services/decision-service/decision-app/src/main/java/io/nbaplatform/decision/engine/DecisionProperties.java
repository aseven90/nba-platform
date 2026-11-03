package io.nbaplatform.decision.engine;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param deadline           total time budget for one decision, including feature lookup
 * @param maxConcurrentPerChannel bulkhead size: concurrent decisions allowed per channel
 * @param circuitBreaker     trips to the safe default when the pipeline keeps failing or running slow
 * @param safeDefaults       offering ids returned when a decision cannot be made, per tenant ("default" applies
 *                           to tenants without their own list)
 */
@ConfigurationProperties("nba.decision")
public record DecisionProperties(
        Duration deadline,
        int defaultMaxItems,
        int maxItemsLimit,
        int maxConcurrentPerChannel,
        CircuitBreaker circuitBreaker,
        Map<String, List<String>> safeDefaults) {

    public DecisionProperties {
        deadline = deadline == null ? Duration.ofMillis(80) : deadline;
        defaultMaxItems = defaultMaxItems <= 0 ? 3 : defaultMaxItems;
        maxItemsLimit = maxItemsLimit <= 0 ? 20 : maxItemsLimit;
        maxConcurrentPerChannel = maxConcurrentPerChannel <= 0 ? 256 : maxConcurrentPerChannel;
        circuitBreaker = circuitBreaker == null ? new CircuitBreaker(50, 80, 20, Duration.ofSeconds(10)) : circuitBreaker;
        safeDefaults = safeDefaults == null ? Map.of() : safeDefaults;
    }

    public List<String> safeDefaultsFor(String tenantId) {
        return safeDefaults.getOrDefault(tenantId, safeDefaults.getOrDefault("default", List.of()));
    }

    public record CircuitBreaker(float failureRateThreshold, float slowCallRateThreshold, int minimumNumberOfCalls,
                                 Duration waitInOpenState) {
    }
}
