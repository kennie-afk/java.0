package com.mara.kit.auth;

import java.time.Clock;
import java.time.Duration;
import com.mara.kit.ratelimit.RateLimiters;
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
 * <p>Properties: {@code mara.identity.base-url}, {@code mara.service.credential}, and optionally {@code mara.ratelimit.terminal-per-minute}
 * (default 1200 per source address) and {@code mara.redis.url} (share that limit across replicas).
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
            @Value("${mara.service.credential}") String serviceCredential,
            @Value("${mara.identity.cache-seconds:30}") long cacheSeconds,
            Clock maraClock) {
        return new HttpTerminalDirectory(baseUrl, serviceCredential, Duration.ofSeconds(cacheSeconds), maraClock);
    }

    /** Back-office and service-to-service credentials are verified by identity-service. */
    @Bean
    public CredentialVerifier credentialVerifier(
            @Value("${mara.identity.base-url}") String baseUrl,
            @Value("${mara.service.credential}") String serviceCredential,
            @Value("${mara.credential.cache-seconds:15}") long cacheSeconds,
            Clock maraClock) {
        return new HttpCredentialVerifier(baseUrl, serviceCredential, Duration.ofSeconds(cacheSeconds), maraClock);
    }

    @Bean
    public FilterRegistrationBean<OperatorAuthFilter> operatorAuthFilter(CredentialVerifier verifier) {
        FilterRegistrationBean<OperatorAuthFilter> bean = new FilterRegistrationBean<>(new OperatorAuthFilter(verifier));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<TerminalRateLimitFilter> terminalRateLimitFilter(
            @Value("${mara.ratelimit.terminal-per-minute:1200}") int perMinute,
            @Value("${mara.redis.url:}") String redisUrl,
            @Value("${mara.ratelimit.trust-forwarded-for:false}") boolean trustForwardedFor, Clock maraClock) {
        FilterRegistrationBean<TerminalRateLimitFilter> bean = new FilterRegistrationBean<>(new TerminalRateLimitFilter(
                RateLimiters.create("terminal", redisUrl, perMinute, Duration.ofMinutes(1)), maraClock, trustForwardedFor));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<TerminalAuthFilter> terminalAuthFilter(TerminalDirectory directory, Clock maraClock) {
        FilterRegistrationBean<TerminalAuthFilter> bean = new FilterRegistrationBean<>(new TerminalAuthFilter(directory, maraClock));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return bean;
    }
}
