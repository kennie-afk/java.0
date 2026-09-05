package com.smartseason.farm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class FarmServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FarmServiceApplication.class, args);
    }
}
