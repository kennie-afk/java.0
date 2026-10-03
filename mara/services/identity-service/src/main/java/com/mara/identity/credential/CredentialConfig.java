package com.mara.identity.credential;

import com.mara.kit.auth.CredentialVerifier;
import com.mara.kit.auth.OperatorAuthFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** Puts the shared back-office / service-to-service guard in front of identity-service. */
@Configuration
public class CredentialConfig {

    @Bean
    public FilterRegistrationBean<OperatorAuthFilter> operatorAuthFilter(CredentialVerifier verifier) {
        FilterRegistrationBean<OperatorAuthFilter> bean = new FilterRegistrationBean<>(new OperatorAuthFilter(verifier));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }
}
