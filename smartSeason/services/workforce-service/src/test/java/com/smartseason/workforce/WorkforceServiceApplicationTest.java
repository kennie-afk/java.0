package com.smartseason.workforce;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest
class WorkforceServiceApplicationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("the Spring context starts, so bean wiring and configuration are valid")
    void contextLoads() {
        assertThat(context).isNotNull();
        assertThat(context.getBeanDefinitionCount()).isPositive();
    }
}
