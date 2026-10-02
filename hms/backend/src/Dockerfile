# Build stage: Maven with a dependency cache layer, then a slim runtime that runs as non-root.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
RUN groupadd --system hms && useradd --system --gid hms --no-create-home hms
WORKDIR /app
COPY --from=build /build/target/hms-backend-*.jar app.jar
USER hms
EXPOSE 8100
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
  CMD curl -fs http://127.0.0.1:8100/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
