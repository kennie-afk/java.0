package com.smartseason.traceability;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
@EnableConfigurationProperties(com.smartseason.traceability.ratelimit.RateLimitProperties.class)
public class TraceabilityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TraceabilityServiceApplication.class, args);
    }
}
