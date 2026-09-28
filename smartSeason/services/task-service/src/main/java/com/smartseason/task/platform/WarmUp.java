package com.smartseason.task.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

@Component
@Order(100)
public class WarmUp implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(WarmUp.class);

    private final ListableBeanFactory beans;
    private final ObjectMapper objectMapper;

    @Value("${smartseason.warmup.enabled:true}")
    private boolean enabled;

    @Value("${server.port:8080}")
    private int serverPort;

    public WarmUp(ListableBeanFactory beans, ObjectMapper objectMapper) {
        this.beans = beans;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) {
        if (!enabled) {
            log.info("Warm-up disabled; the first request to each path will pay for it.");
            return;
        }
        long started = System.currentTimeMillis();
        int repositories = warmRepositories();
        int serialisers = warmSerialisers();
        boolean transport = warmTransport();
        log.info("Warm-up complete in {}ms: {} repositories, {} serialisers, transport={}",
                System.currentTimeMillis() - started, repositories, serialisers, transport);
    }

    private int warmRepositories() {
        int warmed = 0;
        for (Map.Entry<String, JpaRepository> entry
                : beans.getBeansOfType(JpaRepository.class).entrySet()) {
            try {
                entry.getValue().findAll(PageRequest.of(0, 1));
                warmed++;
            } catch (Exception ex) {
                log.debug("Warm-up skipped repository {}: {}", entry.getKey(), ex.getMessage());
            }
        }
        return warmed;
    }

    private int warmSerialisers() {

        String pkg = getClass().getPackageName();
        String suffix = ".platform";
        String base = pkg.endsWith(suffix) ? pkg.substring(0, pkg.length() - suffix.length()) : pkg;
        int warmed = 0;
        try {
            var scanner = new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AssignableTypeFilter(Object.class));
            for (var candidate : scanner.findCandidateComponents(base + ".web.dto")) {
                try {
                    Class<?> type = Class.forName(candidate.getBeanClassName());
                    objectMapper.getSerializerProviderInstance().findValueSerializer(type);
                    warmed++;
                } catch (Throwable ignored) {

                }
            }
        } catch (Exception ex) {
            log.debug("Serialiser warm-up skipped: {}", ex.getMessage());
        }
        return warmed;
    }

    private boolean warmTransport() {
        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2)).build()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + serverPort + "/actuator/health"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            client.send(request, HttpResponse.BodyHandlers.discarding());
            return true;
        } catch (Exception ex) {
            log.debug("Transport warm-up skipped: {}", ex.getMessage());
            return false;
        }
    }
}
