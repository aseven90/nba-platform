package io.nbaplatform.decision.engine;

import io.nbaplatform.decision.spi.FeatureProvider;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class EngineConfiguration {

    /** Virtual threads: each decision gets its own, so a slow one cannot starve others of a pool slot. */
    @Bean(destroyMethod = "shutdownNow")
    ExecutorService decisionExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /** No feature store in the MVP: decisions use the context attributes supplied by the caller. */
    @Bean
    @ConditionalOnMissingBean
    FeatureProvider featureProvider() {
        return (tenantId, partyId) -> Map.of();
    }
}
