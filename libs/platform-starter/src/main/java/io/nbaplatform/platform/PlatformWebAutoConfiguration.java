package io.nbaplatform.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nbaplatform.platform.tenant.TenantFilter;
import io.nbaplatform.platform.web.TmfExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import(TmfExceptionHandler.class)
public class PlatformWebAutoConfiguration {

    @Bean
    FilterRegistrationBean<TenantFilter> tenantFilter(ObjectMapper objectMapper) {
        FilterRegistrationBean<TenantFilter> registration = new FilterRegistrationBean<>(new TenantFilter(objectMapper));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
