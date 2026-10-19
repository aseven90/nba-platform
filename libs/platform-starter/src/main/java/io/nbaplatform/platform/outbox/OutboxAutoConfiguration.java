package io.nbaplatform.platform.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Transactional outbox for services that persist with JPA. {@link AutoConfigurationPackage} adds this package to
 * the application's entity and repository scanning, so services need no extra configuration.
 */
@AutoConfiguration(after = KafkaAutoConfiguration.class,
        before = {HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class})
@ConditionalOnClass(name = "jakarta.persistence.EntityManager")
@AutoConfigurationPackage
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxAutoConfiguration {

    @Bean
    OutboxWriter outboxWriter(OutboxRepository repository, ObjectMapper objectMapper,
                              @Value("${spring.application.name:nba-service}") String applicationName) {
        return new OutboxWriter(repository, objectMapper, "/nba/" + applicationName);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnBean(KafkaTemplate.class)
    @ConditionalOnProperty(name = "nba.outbox.relay-enabled", matchIfMissing = true)
    static class RelayConfiguration {

        @Bean
        OutboxRelay outboxRelay(OutboxRepository repository, KafkaTemplate<String, String> kafka,
                                PlatformTransactionManager transactionManager, OutboxProperties properties) {
            return new OutboxRelay(repository, kafka, new TransactionTemplate(transactionManager), properties);
        }
    }
}
