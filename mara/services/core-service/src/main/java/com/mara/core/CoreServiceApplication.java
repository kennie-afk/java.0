package com.mara.core;

import com.mara.kit.auth.MaraAuthConfig;
import com.mara.kit.db.MaraDatabaseConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
@Import({MaraDatabaseConfig.class, MaraAuthConfig.class})
@EnableScheduling
public class CoreServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreServiceApplication.class, args);
    }
}
