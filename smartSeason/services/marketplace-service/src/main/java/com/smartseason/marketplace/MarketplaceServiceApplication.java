package com.smartseason.marketplace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
@EnableConfigurationProperties(com.smartseason.marketplace.ratelimit.RateLimitProperties.class)
public class MarketplaceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketplaceServiceApplication.class, args);
    }
}
