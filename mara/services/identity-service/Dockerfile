# Build context is the repository root (../.. from here), not this directory —
# identity-service depends on the platform module by Maven coordinate, not a
# reactor-relative path, so both must be visible to build it from source. See
# docker-compose.yml, which sets `context: .` at the root for exactly this reason.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src

# Dependency layer first: platform's own pom, then a throwaway `go-offline` so
# a source-only change doesn't re-download the world.
COPY pom.xml pom.xml
COPY platform/pom.xml platform/pom.xml
COPY services/identity-service/pom.xml services/identity-service/pom.xml
RUN mvn -q -pl platform,services/identity-service -am dependency:go-offline || true

COPY platform/src platform/src
COPY services/identity-service/src services/identity-service/src

# platform has no reactor relationship to identity-service (separate Spring Boot
# parent, on purpose — see identity-service/pom.xml) so it must be installed to
# the local repo before identity-service can resolve it by coordinate.
RUN mvn -q -pl platform -am install -DskipTests \
 && mvn -q -pl services/identity-service -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S mara && adduser -S mara -G mara
WORKDIR /app
COPY --from=build /src/services/identity-service/target/identity-service-*.jar app.jar
USER mara
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
