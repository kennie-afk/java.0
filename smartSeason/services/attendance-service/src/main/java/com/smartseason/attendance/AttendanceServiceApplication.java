package com.smartseason.attendance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
@EnableConfigurationProperties(com.smartseason.attendance.ratelimit.RateLimitProperties.class)
public class AttendanceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AttendanceServiceApplication.class, args);
    }
}
