package com.kenyarealestate.property;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.retry.annotation.EnableRetry;

// PropertyOutboxSweeper is @Scheduled; without this it never runs.
@SpringBootApplication
@EnableScheduling
@EnableRetry
public class PropertyServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PropertyServiceApplication.class, args);
    }
}
