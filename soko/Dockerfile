FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml ./
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
RUN useradd --create-home --uid 10001 soko
COPY --from=build /app/target/soko-1.0.0.jar app.jar
USER soko
EXPOSE 8090
HEALTHCHECK --interval=30s --timeout=3s --start-period=90s --retries=3 \
    CMD ["sh", "-c", "wget -qO- http://127.0.0.1:8090/actuator/health || exit 1"]
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
