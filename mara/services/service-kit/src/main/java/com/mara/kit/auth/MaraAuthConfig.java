package com.mara.kit.auth;

import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers the two filters in a fixed order (operator token first, so an unauthenticated
 * caller learns nothing about tenants) and the directory they use. Import it next to
 * {@link com.mara.kit.db.MaraDatabaseConfig}.
 *
 * <p>Properties: {@code mara.identity.base-url}, {@code mara.internal.token},
 * {@code mara.admin.token}.
 */
@Configuration
public class MaraAuthConfig {

    @Bean
    public Clock maraClock() {
        return Clock.systemUTC();
    }

    @Bean
    public TerminalDirectory terminalDirectory(
            @Value("${mara.identity.base-url}") String baseUrl,
            @Value("${mara.internal.token}") String internalToken,
            @Value("${mara.identity.cache-seconds:30}") long cacheSeconds,
            Clock maraClock) {
        return new HttpTerminalDirectory(baseUrl, internalToken, Duration.ofSeconds(cacheSeconds), maraClock);
    }

    @Bean
    public FilterRegistrationBean<OperatorTokenFilter> operatorTokenFilter(
            @Value("${mara.admin.token}") String admin, @Value("${mara.internal.token}") String internal) {
        FilterRegistrationBean<OperatorTokenFilter> bean = new FilterRegistrationBean<>(new OperatorTokenFilter(admin, internal));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<TerminalAuthFilter> terminalAuthFilter(TerminalDirectory directory, Clock maraClock) {
        FilterRegistrationBean<TerminalAuthFilter> bean = new FilterRegistrationBean<>(new TerminalAuthFilter(directory, maraClock));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return bean;
    }
}
