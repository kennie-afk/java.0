package com.smartseason.season;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
@EnableConfigurationProperties(com.smartseason.season.ratelimit.RateLimitProperties.class)
public class SeasonServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeasonServiceApplication.class, args);
    }
}
