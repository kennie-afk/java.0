FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline --batch-mode -q
COPY src ./src
RUN mvn package -DskipTests --batch-mode -q

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl
WORKDIR /app
RUN addgroup -S app && adduser -S -G app app
COPY --from=build /app/target/*.jar app.jar
RUN chown -R app:app /app
USER app
EXPOSE 8086
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
