package com.kenyarealestate.verification.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

/**
 * Pays the first-request cost at start-up instead of charging it to a user.
 *
 * <p>Measured here on 2026-09-11: the first call to an endpoint cost 20 to 200 times the
 * second, worst case 3.53s against a settled 17ms. None of it is query time. It is class
 * loading, JIT, Hibernate building its metamodel, and Jackson constructing a serialiser
 * per type it has not seen.
 *
 * <p>An earlier version of this class primed only the repositories and cut the worst case
 * to about a third. That was not enough, and the reason is worth keeping: a request does
 * not only touch the data layer. It crosses the servlet container, the security filter
 * chain, the controller and the JSON writer, and none of those were being warmed. So this
 * warms all three layers deliberately:
 *
 * <ol>
 *   <li><b>Data</b> - one row from every repository, which builds Hibernate's metamodel,
 *       opens a pooled connection and materialises an entity.
 *   <li><b>Serialisation</b> - a Jackson serialiser for every DTO this service declares.
 *       Built directly from the type, so it does not depend on a row existing: priming by
 *       serialising query results silently skips every DTO whose table is empty, which is
 *       most of them on a fresh deployment.
 *   <li><b>Transport</b> - one real loopback request over HTTP, which starts the servlet
 *       container's request path and runs the whole filter chain including security. It
 *       is unauthenticated and expected to be refused; a 401 has travelled the same code
 *       as a 200 up to the controller, which is the part that is expensive once.
 * </ol>
 *
 * <p>Deliberately a {@link CommandLineRunner}: runners finish before the readiness probe
 * reports up, so Kubernetes will not route to a pod that has not warmed. That matters
 * most under an autoscaler, where a cold pod joins the set exactly when load is highest
 * and would hand new arrivals the worst latency in the system.
 *
 * <p>Every failure is swallowed. A warm-up that prevented start-up would be a far worse
 * bug than the latency it removes.
 */
@Slf4j
@Component
@Order(100)
public class WarmUp implements CommandLineRunner {

    private final ListableBeanFactory beans;
    private final ObjectMapper objectMapper;

    @Value("${smartre.warmup.enabled:true}")
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
            log.info("Warm-up disabled; the first request to each path will pay for it instead.");
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
        for (Map.Entry<String, JpaRepository> entry : beans.getBeansOfType(JpaRepository.class).entrySet()) {
            try {
                // One row, not a count: a count is answered from an index without building
                // an entity, and building the entity for the first time is the costly part.
                entry.getValue().findAll(PageRequest.of(0, 1));
                warmed++;
            } catch (Exception ex) {
                log.debug("Warm-up skipped repository {}: {}", entry.getKey(), ex.getMessage());
            }
        }
        return warmed;
    }

    /**
     * Builds a Jackson serialiser for every DTO this service declares.
     *
     * <p>From the type rather than from an instance, so an empty table does not mean an
     * unwarmed response. This is the step the first version was missing.
     */
    private int warmSerialisers() {
        String base = getClass().getPackageName().replaceAll("\\.config$", "");
        int warmed = 0;
        try {
            var scanner = new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AssignableTypeFilter(Object.class));
            for (var candidate : scanner.findCandidateComponents(base + ".dto")) {
                try {
                    Class<?> type = Class.forName(candidate.getBeanClassName());
                    objectMapper.getSerializerProviderInstance().findValueSerializer(type);
                    warmed++;
                } catch (Throwable ignored) {
                    // A DTO that cannot be serialised in isolation is not a start-up problem.
                }
            }
        } catch (Exception ex) {
            log.debug("Serialiser warm-up skipped: {}", ex.getMessage());
        }
        return warmed;
    }

    /**
     * One loopback request, to start the servlet container and the filter chain.
     *
     * <p>Runners execute after the web server is listening, so this reaches a live port.
     * The response is irrelevant - being refused still means the request crossed every
     * filter, which is what needed compiling.
     */
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
