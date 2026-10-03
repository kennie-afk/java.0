package com.kenyarealestate.pms;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /** One clock so date-dependent logic can be tested with a fixed one. */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
