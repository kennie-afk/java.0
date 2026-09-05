package com.smartseason.season;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class SeasonServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeasonServiceApplication.class, args);
    }
}
