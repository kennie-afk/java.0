package com.smartseason.agronomy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class AgronomyServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgronomyServiceApplication.class, args);
    }
}
