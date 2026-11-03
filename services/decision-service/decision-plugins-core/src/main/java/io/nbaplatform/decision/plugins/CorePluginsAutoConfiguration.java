package io.nbaplatform.decision.plugins;

import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Registers the core stages. Any jar on the classpath can contribute further {@code DecisionStage} beans the
 * same way; each core stage can be switched off with {@code nba.decision.plugins.<name>.enabled=false}.
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
    @ConditionalOnProperty(name = "nba.decision.plugins.logistic-propensity.enabled", matchIfMissing = true)
    LogisticPropensityStage logisticPropensityStage(
            @Value("${nba.decision.plugins.logistic-propensity.default-propensity:0.05}") double prior) {
        return new LogisticPropensityStage(prior);
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
