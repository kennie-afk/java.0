"""Security, web-config and event-publishing templates."""

JWT_FILTER = '''package com.smartseason.{pkg}.platform;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import io.jsonwebtoken.security.Keys;

/**
 * Verifies the bearer token minted by identity-service and binds the caller's
 * tenant and roles to the request.
 *
 * <p>A token that fails verification is not rejected here — the filter simply leaves the
 * context unauthenticated and lets Spring Security's authorization rules decide, so that
 * public endpoints (health, docs) stay reachable without a token.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {{

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER = "Bearer ";

    private final SecretKey key;
    private final String issuer;

    public JwtAuthenticationFilter(
            @Value("${{smartseason.jwt.secret}}") String secret,
            @Value("${{smartseason.jwt.issuer}}") String issuer) {{
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
    }}

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {{
        try {{
            String header = request.getHeader("Authorization");
            if (StringUtils.hasText(header) && header.startsWith(BEARER)) {{
                authenticate(header.substring(BEARER.length()).trim());
            }}
            chain.doFilter(request, response);
        }} finally {{
            // The thread returns to the container's pool; a leaked tenant here would
            // leak across unrelated requests.
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }}
    }}

    private void authenticate(String token) {{
        try {{
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String subject = claims.getSubject();
            String tenant = claims.get("tid", String.class);
            if (tenant == null) {{
                log.debug("Token for subject {{}} carries no tenant claim; ignoring", subject);
                return;
            }}
            TenantContext.set(UUID.fromString(tenant));

            List<SimpleGrantedAuthority> authorities = roles(claims).stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(subject, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }} catch (JwtException | IllegalArgumentException ex) {{
            log.debug("Rejected bearer token: {{}}", ex.getMessage());
        }}
    }}

    private List<String> roles(Claims claims) {{
        Object raw = claims.get("roles");
        if (raw instanceof String csv) {{
            return Arrays.stream(csv.split(",")).map(String::trim).filter(StringUtils::hasText).toList();
        }}
        if (raw instanceof List<?> list) {{
            return list.stream().map(String::valueOf).toList();
        }}
        return List.of();
    }}
}}
'''

SECURITY_CONFIG = '''package com.smartseason.{pkg}.platform;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.beans.factory.annotation.Value;

/**
 * Stateless JWT security for {service}.
 *
 * <p>CSRF is disabled because there is no cookie-borne session to forge against —
 * every mutating call must carry an explicit bearer token.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {{

    private final JwtAuthenticationFilter jwtFilter;
    private final String allowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter,
                          @Value("${{CORS_ALLOWED_ORIGINS:http://localhost:3000,http://localhost:5173}}") String allowedOrigins) {{
        this.jwtFilter = jwtFilter;
        this.allowedOrigins = allowedOrigins;
    }}

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {{
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers
                .frameOptions(frame -> frame.deny())
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'")))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
{public_matchers}                .anyRequest().authenticated())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }}

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {{
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Request-Id"));
        config.setExposedHeaders(List.of("X-Request-Id", "X-Total-Count"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }}
}}
'''

OPENAPI_CONFIG = '''package com.smartseason.{pkg}.platform;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI descriptor for {service}, served at {{@code /swagger-ui.html}}. */
@Configuration
public class OpenApiConfig {{

    private static final String SCHEME = "bearerAuth";

    @Bean
    public OpenAPI openApi() {{
        return new OpenAPI()
                .info(new Info()
                        .title("SmartSeason — {service}")
                        .description("{desc}")
                        .version("1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME))
                .components(new Components().addSecuritySchemes(SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }}
}}
'''

PAGE_RESPONSE = '''package com.smartseason.{pkg}.platform;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Transport shape for a page of results.
 *
 * <p>Spring's {{@code Page}} is deliberately not serialised directly — its JSON layout is
 * an implementation detail that would otherwise become part of the public API contract.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {{

    public static <T> PageResponse<T> from(Page<T> page) {{
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }}
}}
'''

DOMAIN_EVENT = '''package com.smartseason.{pkg}.platform;

import java.time.Instant;
import java.util.UUID;

/**
 * Envelope every event this service publishes is wrapped in.
 *
 * <p>{{@code eventId}} lets consumers deduplicate, {{@code tenantId}} lets them shard, and
 * {{@code version}} lets the schema evolve without breaking existing subscribers.
 */
public record DomainEvent<T>(
        UUID eventId,
        String eventType,
        int version,
        UUID tenantId,
        UUID aggregateId,
        Instant occurredAt,
        String producer,
        T payload) {{

    public static <T> DomainEvent<T> of(String eventType, UUID aggregateId, String producer, T payload) {{
        return new DomainEvent<>(
                UUID.randomUUID(),
                eventType,
                1,
                TenantContext.tenantId().orElse(null),
                aggregateId,
                Instant.now(),
                producer,
                payload);
    }}
}}
'''

EVENT_PUBLISHER = '''package com.smartseason.{pkg}.platform;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EventPublisher {{

    private final OutboxRepository outbox;
    private final ObjectMapper objectMapper;
    private final String topicPrefix;
    private final String producer;

    public EventPublisher(OutboxRepository outbox,
                          ObjectMapper objectMapper,
                          @Value("${{smartseason.events.topic-prefix:ss}}") String topicPrefix,
                          @Value("${{spring.application.name}}") String producer) {{
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.topicPrefix = topicPrefix;
        this.producer = producer;
    }}

    public void publish(String domain, String type, UUID aggregateId, Object payload) {{
        DomainEvent<Object> event = DomainEvent.of(type, aggregateId, producer, payload);
        String topic = "%s.%s.%s.v1".formatted(topicPrefix, domain, camelToKebab(type));

        OutboxEntry entry = new OutboxEntry();
        entry.setId(UUID.randomUUID());
        entry.setTenantId(TenantContext.tenantId().orElse(null));
        entry.setTopic(topic);
        entry.setMessageKey(aggregateId == null ? null : aggregateId.toString());
        entry.setPayload(serialise(event));
        entry.setEventType(type);
        entry.setStatus(OutboxEntry.Status.PENDING);
        entry.setAttempts(0);
        entry.setCreatedAt(Instant.now());

        outbox.save(entry);
    }}

    private String serialise(DomainEvent<Object> event) {{
        try {{
            return objectMapper.writeValueAsString(event);
        }} catch (JsonProcessingException ex) {{
            throw new IllegalStateException("Could not serialise event " + event.eventType(), ex);
        }}
    }}

    private static String camelToKebab(String value) {{
        return value.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase();
    }}
}}
'''
