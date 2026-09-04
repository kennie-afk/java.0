package com.kenyarealestate.pms;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication
@EnableScheduling
public class PropertyManagementServiceApplication {
    public static void main(String[] a) { SpringApplication.run(PropertyManagementServiceApplication.class, a); }
}
