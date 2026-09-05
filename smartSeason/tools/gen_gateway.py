#!/usr/bin/env python3
"""Emits apps/api-gateway with one route per catalogued service."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from catalogue import SERVICES
from gen_support import kebab_to_camel
import strip_comments as sc

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASE = os.path.join(ROOT, "apps/api-gateway")
PORT = 8080

POM = """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.16</version>
    <relativePath/>
  </parent>

  <groupId>com.smartseason</groupId>
  <artifactId>api-gateway</artifactId>
  <version>1.0.0</version>
  <name>api-gateway</name>
  <description>Edge router for the SmartSeason platform</description>

  <properties>
    <java.version>21</java.version>
    <maven.compiler.source>21</maven.compiler.source>
    <maven.compiler.target>21</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <spring-cloud.version>2025.0.3</spring-cloud.version>
    <jjwt.version>0.12.6</jjwt.version>
  </properties>

  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-dependencies</artifactId>
        <version>${spring-cloud.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>

  <dependencies>
    <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-gateway</artifactId></dependency>
    <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-circuitbreaker-reactor-resilience4j</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-redis-reactive</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>
    <dependency><groupId>io.micrometer</groupId><artifactId>micrometer-registry-prometheus</artifactId></dependency>

    <dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-api</artifactId><version>${jjwt.version}</version></dependency>
    <dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-impl</artifactId><version>${jjwt.version}</version><scope>runtime</scope></dependency>
    <dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-jackson</artifactId><version>${jjwt.version}</version><scope>runtime</scope></dependency>

    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
    <dependency><groupId>io.projectreactor</groupId><artifactId>reactor-test</artifactId><scope>test</scope></dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
"""

APP = """package com.smartseason.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
"""

JWT_FILTER = """package com.smartseason.gateway;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtPreCheckFilter implements GlobalFilter, Ordered {

    private static final String BEARER = "Bearer ";

    private static final List<String> OPEN_PATHS = List.of(
            "/api/identity/v1/auth/",
            "/api/identity/v1/.well-known/",
            "/actuator/health",
            "/actuator/prometheus",
            "/fallback/");

    private final SecretKey key;
    private final String issuer;

    public JwtPreCheckFilter(@Value("${smartseason.jwt.secret}") String secret,
                             @Value("${smartseason.jwt.issuer}") String issuer) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (OPEN_PATHS.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER)) {
            return reject(exchange);
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(header.substring(BEARER.length()).trim())
                    .getPayload();

            ServerHttpRequest request = exchange.getRequest().mutate()
                    .header("X-Auth-Subject", String.valueOf(claims.getSubject()))
                    .header("X-Auth-Tenant", String.valueOf(claims.get("tid")))
                    .build();

            return chain.filter(exchange.mutate().request(request).build());
        } catch (JwtException | IllegalArgumentException ex) {
            return reject(exchange);
        }
    }

    private Mono<Void> reject(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
"""

RATE_LIMIT = """package com.smartseason.gateway;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitConfig {

    @Bean
    public KeyResolver authenticatedUserKeyResolver() {
        return exchange -> {
            String subject = exchange.getRequest().getHeaders().getFirst("X-Auth-Subject");
            if (subject != null) {
                return Mono.just(subject);
            }
            return Mono.just(exchange.getRequest().getRemoteAddress() == null
                    ? "anonymous"
                    : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
        };
    }
}
"""

FALLBACK = """package com.smartseason.gateway;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping("/{service}")
    public ResponseEntity<Map<String, Object>> fallback(@PathVariable String service) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(Map.of(
                        "type", "https://docs.smartseason.io/errors/upstream-unavailable",
                        "title", "upstream-unavailable",
                        "status", 503,
                        "detail", service + " is not responding; the request was not applied",
                        "code", "upstream-unavailable",
                        "timestamp", Instant.now().toString()));
    }
}
"""

DOCKERFILE = """FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline --batch-mode -q
COPY src ./src
RUN mvn package -DskipTests --batch-mode -q

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl && addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \\
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
"""


def routes_yaml():
    lines = []
    for spec in SERVICES:
        service = spec["name"]
        slug = service[:-len("-service")] if service.endswith("-service") else service
        cb = kebab_to_camel(service)
        lines.append(f"        - id: {service}")
        lines.append(f"          uri: ${{{service.upper().replace('-', '_')}_URI:http://{service}:{spec['port']}}}")
        lines.append(f"          predicates:")
        lines.append(f"            - Path=/api/{slug}/**")
        lines.append(f"          filters:")
        lines.append(f"            - name: CircuitBreaker")
        lines.append(f"              args:")
        lines.append(f"                name: {cb}")
        lines.append(f"                fallbackUri: forward:/fallback/{slug}")
        lines.append(f"            - name: RequestRateLimiter")
        lines.append(f"              args:")
        lines.append(f"                key-resolver: \"#{{@authenticatedUserKeyResolver}}\"")
        lines.append(f"                redis-rate-limiter.replenishRate: 50")
        lines.append(f"                redis-rate-limiter.burstCapacity: 100")
    return "\n".join(lines)


APPLICATION_YAML = """spring:
  application:
    name: api-gateway
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
  cloud:
    gateway:
      default-filters:
        - DedupeResponseHeader=Access-Control-Allow-Origin Access-Control-Allow-Credentials
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOriginPatterns: ${CORS_ALLOWED_ORIGINS:http://localhost:3000}
            allowedMethods: [GET, POST, PUT, PATCH, DELETE, OPTIONS]
            allowedHeaders: ["*"]
            allowCredentials: true
      httpclient:
        connect-timeout: 3000
        response-timeout: 15s
      routes:
{routes}

resilience4j:
  circuitbreaker:
    configs:
      default:
        slidingWindowSize: 20
        failureRateThreshold: 50
        waitDurationInOpenState: 20s
        permittedNumberOfCallsInHalfOpenState: 5
  timelimiter:
    configs:
      default:
        timeoutDuration: 15s

server:
  port: 8080
  shutdown: graceful

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,gateway
  endpoint:
    health:
      probes:
        enabled: true

smartseason:
  jwt:
    secret: ${JWT_SECRET:smartseason-dev-signing-key-change-me-in-production-min-64-chars-long}
    issuer: ${JWT_ISSUER:smartseason-identity}

logging:
  level:
    root: INFO
    com.smartseason: ${LOG_LEVEL:INFO}
"""


def write(path, content):
    if path.endswith(".java"):
        content = sc.strip_java(content)
    elif os.path.basename(path) == "Dockerfile":
        content = sc.strip_hash(content)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as handle:
        handle.write(content)


def main():
    src = os.path.join(BASE, "src/main/java/com/smartseason/gateway")
    write(os.path.join(BASE, "pom.xml"), POM)
    write(os.path.join(BASE, "Dockerfile"), DOCKERFILE)
    write(os.path.join(src, "ApiGatewayApplication.java"), APP)
    write(os.path.join(src, "JwtPreCheckFilter.java"), JWT_FILTER)
    write(os.path.join(src, "RateLimitConfig.java"), RATE_LIMIT)
    write(os.path.join(src, "FallbackController.java"), FALLBACK)
    write(os.path.join(BASE, "src/main/resources/application.yaml"),
          APPLICATION_YAML.replace("{routes}", routes_yaml()))
    print(f"api-gateway written with {len(SERVICES)} routes")


if __name__ == "__main__":
    main()
