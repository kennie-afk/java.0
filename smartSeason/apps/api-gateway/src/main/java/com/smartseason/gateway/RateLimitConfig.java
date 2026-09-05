package com.smartseason.gateway;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitConfig {

    @Bean
    public KeyResolver authenticatedUserKeyResolver() {
        return exchange -> {
            String subject = exchange.getRequest().getHeaders().getFirst("X-Auth-Subject");
            if (subject != null) {
                return Mono.just(subject);
            }
            return Mono.just(exchange.getRequest().getRemoteAddress() == null
                    ? "anonymous"
                    : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
        };
    }
}
