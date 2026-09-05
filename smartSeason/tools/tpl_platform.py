"""Per-service platform/scaffolding templates (identical shape in every service)."""

POM = '''<?xml version="1.0" encoding="UTF-8"?>
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
  <artifactId>{service}</artifactId>
  <version>1.0.0</version>
  <name>{service}</name>
  <description>{desc}</description>

  <properties>
    <java.version>21</java.version>
    <maven.compiler.source>21</maven.compiler.source>
    <maven.compiler.target>21</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <jjwt.version>0.12.6</jjwt.version>
    <springdoc.version>2.8.3</springdoc.version>
    <resilience4j.version>2.2.0</resilience4j.version>
  </properties>

  <dependencies>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>
    <dependency><groupId>org.springframework.kafka</groupId><artifactId>spring-kafka</artifactId></dependency>

    <dependency><groupId>io.micrometer</groupId><artifactId>micrometer-registry-prometheus</artifactId></dependency>
    <dependency><groupId>io.github.resilience4j</groupId><artifactId>resilience4j-spring-boot3</artifactId><version>${{resilience4j.version}}</version></dependency>

    <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><scope>runtime</scope></dependency>
    <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency>
    <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-database-postgresql</artifactId></dependency>

    <dependency><groupId>org.springdoc</groupId><artifactId>springdoc-openapi-starter-webmvc-ui</artifactId><version>${{springdoc.version}}</version></dependency>

    <dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-api</artifactId><version>${{jjwt.version}}</version></dependency>
    <dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-impl</artifactId><version>${{jjwt.version}}</version><scope>runtime</scope></dependency>
    <dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-jackson</artifactId><version>${{jjwt.version}}</version><scope>runtime</scope></dependency>

    <dependency><groupId>com.fasterxml.jackson.datatype</groupId><artifactId>jackson-datatype-jsr310</artifactId></dependency>
{extra_deps}
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.springframework.security</groupId><artifactId>spring-security-test</artifactId><scope>test</scope></dependency>
    <dependency><groupId>com.h2database</groupId><artifactId>h2</artifactId><scope>test</scope></dependency>
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
'''

DOCKERFILE = '''FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Dependency layer cached independently of source changes.
COPY pom.xml .
RUN mvn dependency:go-offline --batch-mode -q

COPY src ./src
RUN mvn package -DskipTests --batch-mode -q

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl && addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
USER app
EXPOSE {port}
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \\
  CMD curl -fsS http://localhost:{port}/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
'''

APPLICATION_YAML = '''spring:
  application:
    name: {service}
  datasource:
    url: ${{SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/{db}}}
    username: ${{SPRING_DATASOURCE_USERNAME:postgres}}
    password: ${{SPRING_DATASOURCE_PASSWORD:postgres}}
    hikari:
      maximum-pool-size: ${{DB_POOL_MAX:10}}
      minimum-idle: 2
      connection-timeout: 30000
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        jdbc:
          batch_size: 50
        order_inserts: true
  flyway:
    enabled: true
    baseline-on-migrate: true
  kafka:
    bootstrap-servers: ${{KAFKA_BOOTSTRAP_SERVERS:localhost:29092}}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all
      retries: 5
    consumer:
      group-id: {service}
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      auto-offset-reset: earliest
      properties:
        spring.json.trusted.packages: "com.smartseason.*"

server:
  port: {port}
  shutdown: graceful
  error:
    include-message: always
    include-stacktrace: never

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  endpoint:
    health:
      probes:
        enabled: true
      show-details: when-authorized
  metrics:
    tags:
      service: {service}

springdoc:
  swagger-ui:
    path: /swagger-ui.html
  api-docs:
    path: /v3/api-docs

smartseason:
  jwt:
    secret: ${{JWT_SECRET:smartseason-dev-signing-key-change-me-in-production-min-64-chars-long}}
    issuer: ${{JWT_ISSUER:smartseason-identity}}
  events:
    enabled: ${{EVENTS_ENABLED:false}}
    topic-prefix: ss

logging:
  level:
    root: INFO
    com.smartseason: ${{LOG_LEVEL:INFO}}
'''

APPLICATION_TEST_YAML = '''spring:
  application:
    name: {service}
  datasource:
    url: jdbc:h2:mem:{db};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH
    driver-class-name: org.h2.Driver
    username: sa
    password: ""
  jpa:
    hibernate:
      ddl-auto: create-drop
    properties:
      hibernate:
        dialect: org.hibernate.dialect.H2Dialect
  flyway:
    enabled: false
  kafka:
    bootstrap-servers: localhost:59092
    consumer:
      group-id: {service}
      auto-offset-reset: earliest
    listener:
      auto-startup: false

smartseason:
  jwt:
    secret: test-signing-key-that-is-at-least-sixty-four-characters-long-for-hs256!!
    issuer: smartseason-identity
  events:
    enabled: false

logging:
  level:
    root: WARN
'''

MAIN_CLASS = '''package com.smartseason.{pkg};

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * {desc}
 *
 * <p>Owns its database ({db}); no other service reads these tables directly.
 * State leaves this service as domain events, never as shared schema.
 *
 * <p>{{@code UserDetailsServiceAutoConfiguration}} is excluded so Spring Boot does not
 * create a default in-memory user and print a generated password at boot. Authentication
 * is bearer-token only.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class {clazz} {{

    public static void main(String[] args) {{
        SpringApplication.run({clazz}.class, args);
    }}
}}
'''

CONTEXT_TEST = '''package com.smartseason.{pkg};

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest
class {clazz}Test {{

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("the Spring context starts, so bean wiring and configuration are valid")
    void contextLoads() {{
        assertThat(context).isNotNull();
        assertThat(context.getBeanDefinitionCount()).isPositive();
    }}
}}
'''
