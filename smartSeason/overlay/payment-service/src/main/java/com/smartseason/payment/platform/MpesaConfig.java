package com.smartseason.payment.platform;

import com.smartseason.payment.mpesa.MpesaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MpesaProperties.class)
public class MpesaConfig {
}
