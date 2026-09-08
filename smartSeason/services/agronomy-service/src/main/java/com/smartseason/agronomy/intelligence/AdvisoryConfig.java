package com.smartseason.agronomy.intelligence;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Chooses which advisory model the service uses.
 *
 * With no API key the rules model is used, and every response says so. The key
 * is read from the environment and never logged.
 */
@Configuration
public class AdvisoryConfig {

    private static final Logger log = LoggerFactory.getLogger(AdvisoryConfig.class);

    @Bean
    public AdvisoryModel advisoryModel(
            @Value("${smartseason.ai.api-key:${ANTHROPIC_API_KEY:}}") String apiKey,
            @Value("${smartseason.ai.base-url:https://api.anthropic.com}") String baseUrl,
            @Value("${smartseason.ai.model:claude-sonnet-5}") String model,
            @Value("${smartseason.ai.timeout-seconds:45}") long timeoutSeconds) {

        HeuristicAdvisoryModel rules = new HeuristicAdvisoryModel();

        if (apiKey == null || apiKey.isBlank()) {
            log.info("No advisory model key configured; agronomic advice will come from the "
                    + "built-in rules and every response will say so.");
            return rules;
        }

        // A model call is slow by nature, so the read timeout is generous while
        // the connect timeout stays short: an unreachable endpoint should fail
        // fast and drop through to the rules.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

        RestClient client = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(baseUrl)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("content-type", "application/json")
                .build();

        log.info("Advisory model enabled: {}", model);
        return new ClaudeAdvisoryModel(client, model, rules);
    }
}
