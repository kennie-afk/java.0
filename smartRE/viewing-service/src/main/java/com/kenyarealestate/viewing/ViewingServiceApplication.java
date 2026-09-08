package com.kenyarealestate.viewing;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
// ViewingOutboxSweeper is @Scheduled; without this it is a bean that never runs.
@SpringBootApplication
@EnableScheduling
public class ViewingServiceApplication { public static void main(String[] a) { SpringApplication.run(ViewingServiceApplication.class,a); } }
