package com.hms.platform.tenancy;

import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class TenancyConfig {

    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new TenantTransactionManager(dataSource);
    }
}
