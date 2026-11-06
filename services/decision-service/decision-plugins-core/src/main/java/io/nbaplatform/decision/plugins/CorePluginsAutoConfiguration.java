package io.nbaplatform.decision.plugins;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.nbaplatform.decision.spi.PropensityScorer;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Registers the core stages and scorers. Any jar on the classpath can contribute further {@code DecisionStage}
 * or {@code PropensityScorer} beans the same way; each core plugin can be switched off with
 * {@code nba.decision.plugins.<name>.enabled=false}. The remote model scorer is off until configured.
 */
@AutoConfiguration
public class CorePluginsAutoConfiguration {

    @Bean
    @ConditionalOnProperty(name = "nba.decision.plugins.campaign-candidates.enabled", matchIfMissing = true)
    CampaignCandidateStage campaignCandidateStage() {
        return new CampaignCandidateStage();
    }

    @Bean
    @ConditionalOnProperty(name = "nba.decision.plugins.consent.enabled", matchIfMissing = true)
    ConsentStage consentStage(@Value("${nba.decision.plugins.consent.require-explicit:false}") boolean explicit) {
        return new ConsentStage(explicit);
    }

    @Bean
    @ConditionalOnProperty(name = "nba.decision.plugins.eligibility-rules.enabled", matchIfMissing = true)
    EligibilityRuleStage eligibilityRuleStage() {
        return new EligibilityRuleStage();
    }

    @Bean
    @ConditionalOnProperty(name = "nba.decision.plugins.contact-policy.enabled", matchIfMissing = true)
    ContactPolicyStage contactPolicyStage() {
        return new ContactPolicyStage();
    }

    @Bean
    @ConditionalOnProperty(name = "nba.decision.plugins.control-group.enabled", matchIfMissing = true)
    ControlGroupStage controlGroupStage() {
        return new ControlGroupStage();
    }

    @Bean
    @ConditionalOnProperty(name = "nba.decision.plugins.logistic.enabled", matchIfMissing = true)
    LogisticScorer logisticScorer() {
        return new LogisticScorer();
    }

    @Bean
    @ConditionalOnProperty(name = "nba.decision.plugins.remote-model.enabled", havingValue = "true")
    RemoteModelScorer remoteModelScorer(
            @Value("${nba.decision.plugins.remote-model.base-url}") URI baseUrl,
            @Value("${nba.decision.plugins.remote-model.timeout:30ms}") Duration timeout,
            @Value("${nba.decision.plugins.remote-model.circuit-breaker.failure-rate-threshold:50}") float failureRate,
            @Value("${nba.decision.plugins.remote-model.circuit-breaker.minimum-number-of-calls:20}") int minCalls,
            @Value("${nba.decision.plugins.remote-model.circuit-breaker.wait-in-open-state:10s}") Duration openWait,
            ObjectProvider<ObjectMapper> mapper) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();
        CircuitBreakerRegistry breakers = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .failureRateThreshold(failureRate)
                .minimumNumberOfCalls(minCalls)
                .waitDurationInOpenState(openWait)
                .build());
        return new RemoteModelScorer(baseUrl, timeout, http, mapper.getIfAvailable(ObjectMapper::new), breakers);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "nba.decision.plugins.propensity-scoring.enabled", matchIfMissing = true)
    PropensityScoringStage propensityScoringStage(
            List<PropensityScorer> scorers,
            @Value("${nba.decision.plugins.propensity-scoring.default-propensity:0.05}") double prior,
            @Value("${nba.decision.plugins.propensity-scoring.timeout:40ms}") Duration timeout,
            @Value("${nba.decision.plugins.propensity-scoring.on-failure:prior}") String onFailure,
            ObjectProvider<MeterRegistry> meters) {
        MeterRegistry registry = meters.getIfAvailable();
        return new PropensityScoringStage(scorers, prior, timeout,
                PropensityScoringStage.OnFailure.valueOf(onFailure.trim().toUpperCase().replace('-', '_')),
                registry == null ? null
                        : (scorer, reason) -> registry.counter("nba.scoring.fallback", "scorer", scorer,
                                "reason", reason).increment());
    }

    @Bean
    @ConditionalOnProperty(name = "nba.decision.plugins.thompson-sampling.enabled", matchIfMissing = true)
    ThompsonSamplingStage thompsonSamplingStage(
            @Value("${nba.decision.plugins.thompson-sampling.prior-strength:20}") double priorStrength) {
        return new ThompsonSamplingStage(priorStrength, ThreadLocalRandom::current);
    }

    @Bean
    @ConditionalOnProperty(name = "nba.decision.plugins.priority-pvl.enabled", matchIfMissing = true)
    PriorityArbitrationStage priorityArbitrationStage() {
        return new PriorityArbitrationStage();
    }

    @Bean
    @ConditionalOnProperty(name = "nba.decision.plugins.top-n.enabled", matchIfMissing = true)
    TopNSelectionStage topNSelectionStage() {
        return new TopNSelectionStage();
    }
}
