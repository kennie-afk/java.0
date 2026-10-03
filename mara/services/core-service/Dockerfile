# Build context is the repository root (../.. from here), not this directory: the service
# depends on the platform module (and service-kit) by Maven coordinate, so all must be
# visible to build from source. docker-compose.yml sets `context: .` for exactly this reason.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src

# Every module's pom is needed even to build one: the aggregator lists them all, and Maven
# refuses a reactor whose child module is missing. Dependency layer first, so a source-only
# change does not re-download the world.
COPY pom.xml pom.xml
COPY platform/pom.xml platform/pom.xml
COPY services/service-kit/pom.xml services/service-kit/pom.xml
COPY services/identity-service/pom.xml services/identity-service/pom.xml
COPY services/sync-service/pom.xml services/sync-service/pom.xml
COPY services/core-service/pom.xml services/core-service/pom.xml
RUN mvn -q -pl platform,services/service-kit,services/core-service -am dependency:go-offline || true

COPY platform/src platform/src
COPY services/service-kit/src services/service-kit/src
COPY services/core-service/src services/core-service/src

# The services keep their own Spring Boot parent (so each stays independently buildable), so
# platform and service-kit must be installed to the local repository before a service can
# resolve them by coordinate.
RUN mvn -q -pl platform,services/service-kit -am install -DskipTests \
 && mvn -q -pl services/core-service -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
# Numeric ids: Kubernetes can only verify "runs as non-root" for a numeric user.
RUN addgroup -S -g 10001 mara && adduser -S -u 10001 -G mara mara
WORKDIR /app
COPY --from=build /src/services/core-service/target/core-service-*.jar app.jar
USER 10001:10001
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
