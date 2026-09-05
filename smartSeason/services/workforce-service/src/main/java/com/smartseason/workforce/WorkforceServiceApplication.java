package com.smartseason.workforce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class WorkforceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkforceServiceApplication.class, args);
    }
}
