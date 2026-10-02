package com.mara.sync;

import com.mara.kit.auth.MaraAuthConfig;
import com.mara.kit.db.MaraDatabaseConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
@Import({MaraDatabaseConfig.class, MaraAuthConfig.class})
public class SyncServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SyncServiceApplication.class, args);
    }
}
