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
 * With no API key every advisory call answers 503; there are no built-in rules to fall back on.
 * The key is read from the environment and never logged.
 */
@Configuration
public class AdvisoryConfig {

    private static final Logger log = LoggerFactory.getLogger(AdvisoryConfig.class);

    @Bean
    public AdvisoryModel advisoryModel(
            @Value("${smartseason.ai.api-key:${ANTHROPIC_API_KEY:}}") String apiKey,
            @Value("${smartseason.ai.base-url:https://api.anthropic.com}") String baseUrl,
            @Value("${smartseason.ai.model:claude-fable-5-2}") String model,
            @Value("${smartseason.ai.timeout-seconds:45}") long timeoutSeconds) {

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("No advisory model key configured; AI diagnosis will answer 503 until "
                    + "ANTHROPIC_API_KEY is set.");
            return new UnconfiguredAdvisoryModel();
        }

        // A model call is slow by nature, so the read timeout is generous while
        // the connect timeout stays short: an unreachable endpoint should fail fast.
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
        return new ClaudeAdvisoryModel(client, model);
    }
}
