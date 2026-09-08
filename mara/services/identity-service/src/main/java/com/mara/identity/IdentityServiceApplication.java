package com.mara.identity;

import com.mara.platform.identity.EnrolmentPolicy;
import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class IdentityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityServiceApplication.class, args);
    }

    /** Injected rather than called statically, so expiry behaviour is testable. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public EnrolmentPolicy enrolmentPolicy() {
        return new EnrolmentPolicy();
    }
}
